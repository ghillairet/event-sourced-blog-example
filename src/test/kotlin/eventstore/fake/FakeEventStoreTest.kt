package eventstore.fake

import events.*
import eventstore.Commit
import eventstore.StreamRevision
import io.reactivex.subscribers.TestSubscriber
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class FakeEventStoreTest {

    @Test
    fun test() {
        val store = FakeEventStore<PostEvent>()
        val testSubscriber = TestSubscriber.create<Commit<PostEvent>>()
        val testSubscriberStreams = TestSubscriber.create<Commit<PostEvent>>()

        store.commits().subscribe(testSubscriber)

        val e1 = PostAdded(PostId.generate(), PostContent("Paul", "Hello", "World"))
        val r1 = store.committer.tryCommit(e1.postId.toString(), StreamRevision.Initial, e1)

        assertThat(r1.isRight()).isTrue()
        assertThat(r1.right().get().events).contains(e1)

        val e2 = PostAdded(PostId.generate(), PostContent("Paul", "Hello", "Again"))
        val r2 = store.committer.tryCommit(e2.postId.toString(), StreamRevision.Initial, e2)

        assertThat(r2.isRight()).isTrue()
        assertThat(r2.right().get().events).contains(e2)

        val e3 = PostEdited(e1.postId, PostContent("Paul", "Hello", "Edited"))
        val r3 = store.committer.tryCommit(e3.postId.toString(), r1.right().get().streamRevision, e3)

        assertThat(r3.isRight()).isTrue()
        assertThat(r3.right().get().events).contains(e3)

        store.reader.readStream(e1.postId.toString()).subscribe(testSubscriberStreams)

        testSubscriberStreams.assertValueSet(listOf(r1.right().get(), r3.right().get()))
        testSubscriber.assertValueSet(listOf(r1.right().get(), r2.right().get(), r3.right().get()))
    }

}