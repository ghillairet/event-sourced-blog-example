package eventstore

import io.reactivex.Flowable
import org.funktionale.either.Either

data class StoreRevision(val value: Long) {
    companion object {
        val Initial = StoreRevision(0)
        val Maximum = StoreRevision(Long.MAX_VALUE)
    }

    fun previous() = StoreRevision(value - 1)
    fun next() = StoreRevision(value + 1)

    operator infix fun plus(that: Long): StoreRevision = StoreRevision(value + that)
    operator infix fun minus(that: Long): StoreRevision = StoreRevision(value - that)
    operator infix fun minus(that: StoreRevision): Long = value - that.value
    operator infix fun compareTo(that: StoreRevision): Int = value.compareTo(that.value)
}

data class StreamRevision(val value: Long) {
    companion object {
        val Initial = StreamRevision(0)
        val Maximum = StreamRevision(Long.MAX_VALUE)
    }

    fun previous() = StreamRevision(value - 1)
    fun next() = StreamRevision(value + 1)

    operator infix fun plus(that: Long): StreamRevision = StreamRevision(value + that)
    operator infix fun minus(that: Long): StreamRevision = StreamRevision(value - that)
    operator infix fun minus(that: StreamRevision): Long = value - that.value
    operator infix fun compareTo(that: StreamRevision): Int = value.compareTo(that.value)
}

data class Commit<out Event>(val storeRevision: StoreRevision,
                             val timestamp: Long,
                             val streamId: String,
                             val streamRevision: StreamRevision,
                             val events: List<Event>) {

    fun eventsWithRevision(): List<Pair<Event, StreamRevision>> = events.map { event -> Pair(event, streamRevision) }
}

data class Conflict<out Event>(val streamId: String,
                               val actual: StreamRevision,
                               val expected: StreamRevision,
                               val conflicting: List<Commit<Event>>)

typealias CommitResult<Event> = Either<Conflict<Event>, Commit<Event>>

interface CommitReader<Event> {
    fun storeRevision(): StoreRevision
    fun readCommits(since: StoreRevision, to: StoreRevision): Flowable<Commit<Event>>
    fun streamRevision(streamId: String): StreamRevision
    fun readStream(streamId: String, since: StreamRevision = StreamRevision.Initial, to: StreamRevision = StreamRevision.Maximum): Flowable<Commit<Event>>
}

interface EventCommitter<Event> {
    fun tryCommit(streamId: String, expected: StreamRevision, event: Event): CommitResult<Event>
}

interface EventStore<Event> {
    val reader: CommitReader<Event>
    val committer: EventCommitter<Event>
    fun commits(): Flowable<Commit<Event>>
    fun close()
}