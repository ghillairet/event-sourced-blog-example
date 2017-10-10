package eventstore.fake

import com.google.common.util.concurrent.Atomics
import eventstore.*
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.flowables.GroupedFlowable
import io.reactivex.subjects.PublishSubject
import org.funktionale.either.Either.Left
import org.funktionale.either.Either.Right
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

class FakeEventStore<Event> : EventStore<Event> {

    private val LOGGER = LoggerFactory.getLogger(FakeEventStore::class.java)

    internal val lock = Object()

    private val closed = AtomicBoolean(false)
    private val commits = Atomics.newReference(emptyList<Commit<Event>>())
    private val streams = Atomics.newReference(emptyMap<String, List<Commit<Event>>>())

    private val subject = PublishSubject.create<Commit<Event>>()


    override fun commits(): Flowable<Commit<Event>> =
            subject.toFlowable(BackpressureStrategy.BUFFER)

    fun streams(): Flowable<GroupedFlowable<String, Commit<Event>>> =
            commits().groupBy { t: Commit<Event> -> t.streamId }

    companion object {
        fun <Event> fromHistory(events: List<Pair<String, Event>>): FakeEventStore<Event> {
            val result = FakeEventStore<Event>()
            for ((streamId, event) in events) {
                val expected = result.reader.streamRevision(streamId)
                result.committer.tryCommit(streamId, expected, event)
            }
            return result
        }
    }

    override val reader: CommitReader<Event> = object : CommitReader<Event> {
        override fun storeRevision(): StoreRevision = StoreRevision(commits.get().size.toLong())

        override fun readCommits(since: StoreRevision, to: StoreRevision): Flowable<Commit<Event>> =
                Flowable.defer { commits().take(to.value).skip(since.value) }

        override fun streamRevision(streamId: String): StreamRevision =
                StreamRevision(streams.get()[streamId].orEmpty().size.toLong())

        override fun readStream(streamId: String, since: StreamRevision, to: StreamRevision): Flowable<Commit<Event>> =
                streams().filter { group -> group.key == streamId }
                        .take(to.value)
                        .skip(since.value)
                        .flatMap { it }
    }

    override val committer: EventCommitter<Event> = object : EventCommitter<Event> {
        override fun tryCommit(streamId: String, expected: StreamRevision, event: Event): CommitResult<Event> {
            val actual = reader.streamRevision(streamId)

            synchronized(lock) {
                return when {
                    expected < actual -> {
                        val conflicting = reader.readStream(streamId, since = expected)

                        LOGGER.info("Conflict detected on stream $streamId")
                        Left(Conflict(streamId, actual, expected, conflicting.toObservable().toList().blockingGet()))
                    }
                    expected > actual -> {
                        throw IllegalArgumentException("expected revision %d greater than actual revision %d".format(expected.value, actual.value))
                    }
                    else -> {
                        val commit = Commit(reader.storeRevision().next(), System.currentTimeMillis(), streamId, actual.next(), listOf(event))
                        commits.set(commits.get() + commit)
                        streams.set(streams.get() + Pair(streamId, streams.get()[streamId].orEmpty() + commit))
                        subject.onNext(commit)

                        LOGGER.info("Successful commit on stream $streamId with revision ${commit.streamRevision}")
                        Right(commit)
                    }
                }
            }
        }
    }

    override fun close() {
        closed.set(true)
    }

}