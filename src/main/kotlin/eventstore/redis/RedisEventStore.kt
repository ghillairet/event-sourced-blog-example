package eventstore.redis

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import eventstore.*
import json.Json
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPubSub
import redis.clients.jedis.Protocol
import java.util.*
import java.util.concurrent.Executors

abstract class RedisEventStore<Event>(
        val name: String,
        val host: String,
        val port: Int = RedisEventStore.DEFAULT_PORT,
        val json: Json<Event>,
        val config: GenericObjectPoolConfig = GenericObjectPoolConfig()) : EventStore<Event> {

    companion object {
        val DEFAULT_PORT = Protocol.DEFAULT_PORT

        private val LOGGER = LoggerFactory.getLogger(RedisEventStore::class.java)
    }

    private val executor = Executors.newCachedThreadPool()

    // Each event store has its own namespace in Redis, based on the event store name.
    private val KeyPrefix = name + ":"

    // Redis key for the commits hash. Also used as commits publication channel for subscribers.
    protected val CommitsKey: String = KeyPrefix + "commits"

    private val StreamKeyPrefix: String = KeyPrefix + "stream:"

    // Redis key for the list of commit ids per event stream.
    protected fun keyForStream(streamId: String): String = StreamKeyPrefix + streamId

    // Subscriber control channel for handling event store closing and subscription cancellation.
    private val ControlChannel: String = KeyPrefix + "control"

    // Control message used to notify subscribers the event store is closing.
    private val CloseToken = UUID.randomUUID().toString()

    private val jedisPool = JedisPool(config, host, port)

    private val mapper = jacksonObjectMapper()

    private var closed = false

    protected fun <A> withJedis(f: (Jedis) -> A): A {
        val jedis = jedisPool.resource
        return jedis.use { f(it) }
    }

    private val ChunkSize = 10000

    private fun doReadCommits(commitIds: List<String>): Sequence<Commit<Event>> {
        val chunks = commitIds.take(ChunkSize)

        return chunks.flatMap { chunk ->
            withJedis {
                it.hmget(CommitsKey, chunk)
            }.map {
                deserializeCommit(it)
            }
        }.asSequence()
    }

    protected fun deserializeCommit(serialized: String): Commit<Event> = json.deserializeCommit(mapper.readTree(serialized)!!)
    protected fun serializeCommit(commit: Commit<Event>): String = mapper.writeValueAsString(json.serializeCommit(commit))

    override val reader: CommitReader<Event> = object : CommitReader<Event> {

        override fun storeRevision(): StoreRevision = withJedis { jedis -> StoreRevision(jedis.hlen(CommitsKey)) }

        override fun readCommits(since: StoreRevision, to: StoreRevision): Sequence<Commit<Event>> {
            val current = storeRevision()
            return if (since >= current) emptySequence() else {
                val revisionRange = (since.value + 1).rangeTo(Math.min(to.value, current.value))
                doReadCommits(revisionRange.map { it.toString() })
            }
        }

        override fun streamRevision(streamId: String): StreamRevision = withJedis { jedis ->
            StreamRevision(jedis.llen(keyForStream(streamId)))
        }

        override fun readStream(streamId: String, since: StreamRevision, to: StreamRevision): Sequence<Commit<Event>> {
            val commitIds = withJedis { it.lrange(keyForStream(streamId), since.value, to.value) }

            return doReadCommits(commitIds)
        }
    }

    override val publisher: CommitPublisher<Event> = object : CommitPublisher<Event> {

        override fun subscribe(since: StoreRevision, listener: CommitListener<Event>): Subscription {
            var cancelled = false
            var last = StoreRevision.Initial
            val unsubscribeToken = UUID.randomUUID().toString()

            fun replayCommitsTo(to: StoreRevision) {
                if (last < to) {
                    reader.readCommits(last, to)
                            .takeWhile { _ -> !closed && !cancelled }
                            .forEach {
                                LOGGER.info("Commit " + it.javaClass)
                                listener(it)
                            }
                    last = to
                }
            }

            val subscriber = object : JedisPubSub() {
                override fun onSubscribe(channel: String?, subscribedChannels: Int) {
                    when (channel) {
                        ControlChannel -> if (closed || cancelled) unsubscribe()
                        CommitsKey -> replayCommitsTo(reader.storeRevision())
                    }
                }

                override fun onMessage(channel: String?, message: String?) {
                    when (channel) {
                        ControlChannel -> if (message == CloseToken || message == unsubscribeToken) unsubscribe()
                        CommitsKey -> {
                            val commit = deserializeCommit(message.orEmpty())
                            when {
                                last.next() < commit.storeRevision -> replayCommitsTo(commit.storeRevision)
                                last.next() == commit.storeRevision -> {
                                    LOGGER.info("Receive commit " + commit)
                                    listener(commit)
                                    last = commit.storeRevision
                                }
                                else -> {
                                    // warn
                                }
                            }
                        }
                    }
                }
            }

            executor.execute {
                val currentRevision = reader.storeRevision()
                if (last > currentRevision) {
                    LOGGER.warn("Last $last is in the future, resetting it to current $currentRevision")
                    last = currentRevision
                } else {
                    replayCommitsTo(currentRevision)
                }

                val jedis = Jedis(host, port)
                try {
                    jedis.subscribe(subscriber, ControlChannel, CommitsKey)
                } finally {
                    jedis.disconnect()
                }
            }

            return object : Subscription {
                override fun cancel() {
                    cancelled = true
                    withJedis { it.publish(ControlChannel, unsubscribeToken) }
                }
            }
        }
    }

    override fun close() {
        closed = true
        withJedis { it.publish(ControlChannel, CloseToken) }
        executor.shutdown()
        jedisPool.destroy()
    }
}