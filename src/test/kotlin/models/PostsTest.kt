package models

import controllers.PostsController
import events.PostAdded
import events.PostContent
import events.PostId
import eventstore.StreamRevision
import org.assertj.core.api.Assertions.assertThat
import org.funktionale.either.Either
import org.junit.Test

class PostsTest {

    @Test
    fun test() {
        val controller = PostsController.get()

        controller.add(PostId.generate(), Either.Right(PostContent("me", "hello", "")), { result ->
            println(result)
        })
    }

    @Test
    fun testPostsUpdateMany() {
        val posts = Posts()
        val update = posts.updateMany(listOf(Pair(PostAdded(PostId.generate(), PostContent("me", "Hello", "")), StreamRevision.Initial)))

        assertThat(update.byId).hasSize(1)

        val update2 = update.updateMany(listOf(Pair(PostAdded(PostId.generate(), PostContent("me", "World", "")), StreamRevision.Initial)))

        assertThat(update2.byId).hasSize(2)
    }
}

//    describe("apply") {
//        val id = PostId.generate()
//        val event = PostAdded(id,
//                PostContent("Mark", "The power of feedback in Scrum", "Searching the web for ..."))
//        val posts = Posts().apply(event)
//
//        it("should contain one event") {
//            assertThat(posts.byId)
//                    .containsOnlyKeys(event.postId)
//        }
//
//        it("should update one event") {
//            val state = posts.apply(PostEdited(id,
//                    PostContent("Mark", "The power of Scrum", "Searching the web for ...")))
//
//            assertThat(state.byId)
//                    .containsOnlyKeys(id)
//
//            assertThat(state.get(id).get().content)
//                    .isEqualTo(PostContent("Mark", "The power of Scrum", "Searching the web for ..."))
//        }
//
//        it("should delete one event") {
//            val state = posts.apply(PostDeleted(id))
//
//            assertThat(state.byId).isEmpty()
//        }
//    }