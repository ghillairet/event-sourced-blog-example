package eventstore

import com.google.common.util.concurrent.Atomics
import org.slf4j.LoggerFactory

class MemoryImage<out State, Event>(private val eventStore: EventStore<Event>, private val initialState: State, private val update: (State, Commit<Event>) -> State) : EventCommitter<Event> {

    private val lock = Object()
    private val revision = Atomics.newReference(StoreRevision.Initial)
    private val state = Atomics.newReference(this.initialState)

    companion object {
        private val LOGGER = LoggerFactory.getLogger(MemoryImage::class.java)

        fun <State, Event> apply(eventStore: EventStore<Event>, initialState: State, update: (State, Commit<Event>) -> State) =
                MemoryImage(eventStore, initialState, update)
    }

    init {
        eventStore.publisher.subscribe(StoreRevision.Initial, { commit ->
            synchronized(lock) {
                state.set(update(state.get(), commit))
                LOGGER.info("Updating state after commit on stream ${commit.streamId} with revision ${commit.streamRevision}")
                revision.set(commit.storeRevision)
            }
        })
    }

    fun get(): State = state.get()

    override fun tryCommit(streamId: String, expected: StreamRevision, event: Event): CommitResult<Event> =
            eventStore.committer.tryCommit(streamId, expected, event)
}
