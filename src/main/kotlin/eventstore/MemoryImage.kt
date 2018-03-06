package eventstore

import com.google.common.util.concurrent.Atomics
import org.slf4j.LoggerFactory

class MemoryImage<out State, Event>(private val eventStore: EventStore<Event>, private val initialState: State, private val update: (State, Commit<Event>) -> State) : EventCommitter<Event> {

    companion object {
        private val LOGGER = LoggerFactory.getLogger(MemoryImage::class.java)

        fun <State, Event> apply(eventStore: EventStore<Event>, initialState: State, update: (State, Commit<Event>) -> State) =
                MemoryImage(eventStore, initialState, update)
    }

    private val lock = Object()
    private var revision = Atomics.newReference(StoreRevision.Initial)
    private var state = Atomics.newReference(this.initialState)

    init {
        eventStore.publisher.subscribe(StoreRevision.Initial, { commit ->
            synchronized(lock) {
                state.set(update(state.get(), commit))
                revision.set(commit.storeRevision)
                LOGGER.info("Updating state after commit on stream ${commit.streamId} with revision ${commit.streamRevision}")
            }
        })
    }

    fun get(): State = state.get()

    override fun tryCommit(streamId: String, expected: StreamRevision, event: Event): CommitResult<Event> =
            eventStore.committer.tryCommit(streamId, expected, event)
}
