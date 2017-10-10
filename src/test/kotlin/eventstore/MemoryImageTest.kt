package eventstore

import events.*
import eventstore.fake.FakeEventStore
import models.Post
import models.Posts
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.Before
import org.junit.Test

class MemoryImageTest {

    private lateinit var image: MemoryImage<Posts, PostEvent>

    @Before
    fun setUp() {
        image = MemoryImage(FakeEventStore(), Posts(), { posts, commit ->
            posts.updateMany(commit.eventsWithRevision())
        })
    }

    @Test
    fun testStateAfterOnePostAdded() {
        val content = PostContent("Paul", "Hello", "World")
        val event = PostAdded(PostId.generate(), content)
        val result = image.tryCommit(event.postId.toString(), StreamRevision.Initial, event)

        assertThat(result.isRight()).isTrue()
        assertThat(image.get().byId)
                .containsExactly(entry(event.postId, Post(event.postId, content, StreamRevision(1))))
    }

    @Test
    fun testStateAfterSeveralPostAdded() {
        val c1 = PostContent("Paul", "Hello", "World")
        val e1 = PostAdded(PostId.generate(), c1)

        val c2 = PostContent("Peter", "Hello", "World")
        val e2 = PostAdded(PostId.generate(), c2)

        val c3 = PostContent("Sofie", "Hello", "World")
        val e3 = PostAdded(PostId.generate(), c3)

        val c4 = PostContent("Kate", "Hello", "World")
        val e4 = PostAdded(PostId.generate(), c4)

        image.tryCommit(e1.postId.toString(), StreamRevision.Initial, e1)
        image.tryCommit(e2.postId.toString(), StreamRevision.Initial, e2)
        image.tryCommit(e3.postId.toString(), StreamRevision.Initial, e3)
        image.tryCommit(e4.postId.toString(), StreamRevision.Initial, e4)

        assertThat(image.get().byId).containsExactly(
                entry(e1.postId, Post(e1.postId, c1, StreamRevision(1))),
                entry(e2.postId, Post(e2.postId, c2, StreamRevision(1))),
                entry(e3.postId, Post(e3.postId, c3, StreamRevision(1))),
                entry(e4.postId, Post(e4.postId, c4, StreamRevision(1)))
        )
    }

    @Test
    fun testStateAfterSeveralPostAddedAndEdited() {
        val e1 = PostAdded(PostId.generate(), PostContent("Paul", "Hello", "World"))
        val e2 = PostEdited(e1.postId, PostContent("Paul", "Hello", "World World"))

        image.tryCommit(e1.postId.toString(), StreamRevision.Initial, e1)
        image.tryCommit(e2.postId.toString(), StreamRevision(1), e2)

        assertThat(image.get().byId).containsExactly(
                entry(e1.postId, Post(e2.postId, e2.content, StreamRevision(2))))
    }

    @Test
    fun testStateAfterPostAddedAndDeleted() {
        val e1 = PostAdded(PostId.generate(), PostContent("Paul", "Hello", "World"))
        val e2 = PostDeleted(e1.postId)

        image.tryCommit(e1.postId.toString(), StreamRevision.Initial, e1)
        image.tryCommit(e2.postId.toString(), StreamRevision(1), e2)

        assertThat(image.get().byId).isEmpty()
    }

}