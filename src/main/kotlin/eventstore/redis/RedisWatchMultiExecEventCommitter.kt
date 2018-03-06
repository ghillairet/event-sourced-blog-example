package eventstore.redis

import eventstore.*
import json.Json
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.funktionale.either.Either
import redis.clients.jedis.Jedis
import java.util.*

class RedisWatchMultiExecEventCommitter<Event>(name: String, host: String, port: Int, json: Json<Event>, config: GenericObjectPoolConfig) : RedisEventStore<Event>(name, host, port, json, config) {

    override val committer: EventCommitter<Event> = object : EventCommitter<Event> {
        private val lock = Object()

        override fun tryCommit(streamId: String, expected: StreamRevision, event: Event): CommitResult<Event> {
            val backoff = Backoff()
            val streamKey = keyForStream(streamId)

            val result = withJedis { jedis ->
                fun tryCommitWithRetry(): Either<StreamRevision, Commit<Event>> {
                    val (storeRevision, actual) = prepareCommit(streamKey, jedis)

                    return if (expected != actual) {
                        abortCommit(streamId, actual, expected, jedis)
                    } else {
                        val commit = Commit(storeRevision.next(), System.currentTimeMillis(), streamId, actual.next(), listOf(event))
                        if (doCommit(streamKey, commit, jedis)) {
                            Either.Right(commit)
                        } else {
                            backoff.once()
                            tryCommitWithRetry()
                        }
                    }
                }

                synchronized(lock) {
                    tryCommitWithRetry()
                }
            }

            return result.left().map { actual ->
                val conflicting = reader.readStream(streamId, since = expected)
                Conflict(streamId, actual, expected, conflicting)
            }
        }

        private fun prepareCommit(streamKey: String, jedis: Jedis): Pair<StoreRevision, StreamRevision> {
            val pipeline = jedis.pipelined()
            pipeline.watch(CommitsKey, streamKey)
            val storeRevision = pipeline.hlen(CommitsKey)
            val streamRevision = pipeline.llen(streamKey)
            pipeline.sync()

            return StoreRevision(storeRevision.get()) to StreamRevision(streamRevision.get())
        }

        private fun abortCommit(streamId: String, actual: StreamRevision, expected: StreamRevision, jedis: Jedis): Either<StreamRevision, Commit<Event>> {
            jedis.unwatch()
            return if (expected < actual) Either.Left(actual)
            else throw IllegalArgumentException("expected revision $expected.value is greater than actual revision $actual.value for $streamId")
        }

        private fun doCommit(streamKey: String, commit: Commit<Event>, jedis: Jedis): Boolean {
            val commitId = commit.storeRevision.value.toString()
            val commitData = serializeCommit(commit)

            val pipeline = jedis.pipelined()
            pipeline.multi()
            pipeline.hset(CommitsKey, commitId, commitData)
            pipeline.rpush(streamKey, commitId)
            pipeline.publish(CommitsKey, commitData)
            val exec = pipeline.exec()
            pipeline.sync()

            return exec.get() != null
        }
    }

    private class Backoff {
        var tries: Int = 0

        fun once() {
            Thread.sleep(tries * (Random().nextInt(10) + 1).toLong())
            tries += 1
        }
    }
}