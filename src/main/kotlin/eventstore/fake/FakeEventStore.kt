package eventstore.fake

import com.google.common.util.concurrent.Atomics
import eventstore.*
import org.funktionale.either.Either.Left
import org.funktionale.either.Either.Right
import org.funktionale.option.Option
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class FakeEventStore<Event> : EventStore<Event> {

    internal val lock = Object()

    private val executor = Executors.newCachedThreadPool()
    private val closed = AtomicBoolean(false)
    private val commits = Atomics.newReference(emptyList<Commit<Event>>())
    private val streams = Atomics.newReference(emptyMap<String, List<Commit<Event>>>())

    companion object {
        private val LOGGER = LoggerFactory.getLogger(FakeEventStore::class.java)

        fun <Event> fromHistory(events: List<Pair<String, Event>>): FakeEventStore<Event> {
            val result = FakeEventStore<Event>()
            for ((streamId, event) in events) {
                val expected = result.reader.streamRevision(streamId)
                result.committer.tryCommit(streamId, expected, event)
            }
            return result
        }
    }

    override val publisher = object : CommitPublisher<Event> {
        private val lock = Object()

        override fun subscribe(since: StoreRevision, listener: CommitListener<Event>): Subscription {

            val cancelled = AtomicBoolean(false)
            val last = Atomics.newReference(since)

            executor.execute(object : Runnable {
                override tailrec fun run() {
                    val pending = synchronized(lock) {
                        if (closed.get() || cancelled.get())
                            Option.None
                        else Option.Some(commits.get().drop(last.get().value.toInt()))
                    }

                    when (pending) {
                        is Option.None -> return
                        is Option.Some -> {
                            pending.get().forEach { commit ->
                                LOGGER.info("Publishing $commit")
                                listener(commit)
                                last.set(commit.storeRevision)
                            }
                            run()
                        }
                    }
                }
            })

            return object : Subscription {
                override fun cancel() = cancelled.set(true)
                override fun toString(): String =
                        "Subscription(${last.get()}, ${cancelled.get()}, ${this@FakeEventStore})"
            }
        }
    }

    override val reader = object : CommitReader<Event> {

        override fun storeRevision(): StoreRevision = StoreRevision(commits.get().size.toLong())

        override fun readCommits(since: StoreRevision, to: StoreRevision): Sequence<Commit<Event>> = commits.get()
                .slice(IntRange(since.value.toInt(), to.value.toInt()))
                .asSequence()

        override fun streamRevision(streamId: String): StreamRevision =
                StreamRevision(streams.get()[streamId].orEmpty().size.toLong())

        override fun readStream(streamId: String, since: StreamRevision, to: StreamRevision): Sequence<Commit<Event>> = streams.get()[streamId]
                .orEmpty()
                .slice(IntRange(since.value.toInt(), to.value.toInt()))
                .asSequence()
    }

    override val committer: EventCommitter<Event> = object : EventCommitter<Event> {

        override fun tryCommit(streamId: String, expected: StreamRevision, event: Event): CommitResult<Event> {
            val actual = reader.streamRevision(streamId)

            synchronized(lock) {
                return when {
                    expected < actual -> {
                        val conflicting = reader.readStream(streamId, since = expected)

                        LOGGER.info("Conflict detected on stream $streamId")
                        Left(Conflict(streamId, actual, expected, conflicting))
                    }
                    expected > actual -> {
                        throw IllegalArgumentException("expected revision %d greater than actual revision %d".format(expected.value, actual.value))
                    }
                    else -> {
                        val commit = Commit(reader.storeRevision().next(), System.currentTimeMillis(), streamId, actual.next(), listOf(event))
                        commits.set(commits.get() + commit)
                        streams.set(streams.get() + Pair(streamId, streams.get()[streamId].orEmpty() + commit))

                        LOGGER.info("Successful commit on stream $streamId with revision ${commit.streamRevision}")
                        Right(commit)
                    }
                }
            }
        }
    }

    override fun close() {
        closed.set(true)
        executor.shutdown()
    }

}