package eventstore

import com.google.common.util.concurrent.Atomics
import io.reactivex.rxkotlin.subscribeBy
import org.slf4j.LoggerFactory

class MemoryImage<out State, Event>(private val eventStore: EventStore<Event>, private val initialState: State, private val update: (State, Commit<Event>) -> State) : EventCommitter<Event> {

    private val LOGGER = LoggerFactory.getLogger(MemoryImage::class.java)

    private val lock = Object()
    private var revision: StoreRevision = StoreRevision.Initial
    private var state = Atomics.newReference(this.initialState)

    companion object {
        fun <State, Event> apply(eventStore: EventStore<Event>, initialState: State, update: (State, Commit<Event>) -> State) =
                MemoryImage(eventStore, initialState, update)
    }

    init {
        eventStore.commits().subscribeBy(
                onNext = { commit ->
                    synchronized(lock) {
                        state.set(update(state.get(), commit))
                        LOGGER.info("Updating state after commit on stream ${commit.streamId} with revision ${commit.streamRevision}")
                        revision = commit.storeRevision
                    }
                }
        )
    }

    fun get(): State = state.get()

    override fun tryCommit(streamId: String, expected: StreamRevision, event: Event): CommitResult<Event> =
            eventStore.committer.tryCommit(streamId, expected, event)
}
