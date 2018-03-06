package models

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import controllers.Global
import controllers.PostsController
import events.PostAdded
import events.PostContent
import events.PostEvent
import events.PostId
import eventstore.Commit
import eventstore.StoreRevision
import eventstore.StreamRevision
import org.assertj.core.api.Assertions.assertThat
import org.funktionale.either.Either
import org.junit.Test
import java.util.*

class PostsTest {

    @Test
    fun test() {
        val controller = PostsController.get(Global.fake)

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

    @Test
    fun testSerialize() {
        val post = PostAdded(PostId.generate(), PostContent("me", "Hello", ""))
        val commit = Commit(StoreRevision.Initial, Date().time, post.postId.toString(), StreamRevision.Initial, listOf(post))
        val mapper = jacksonObjectMapper()
        println(mapper.valueToTree<JsonNode>(commit))
        println(mapper.valueToTree<JsonNode>(post))
    }

    @Test
    fun testDeserialize() {
        val json = "{\"postId\":\"f7baf8b9-d52b-41c5-a266-ab8c6fcdc9af\",\"content\":{\"author\":\"me\",\"title\":\"Hello\",\"body\":\"\"}}"

        val mapper = jacksonObjectMapper()
        val value = mapper.readValue<PostEvent>(json, jacksonTypeRef<PostEvent>())

        println(value)

        assertThat(value.postId).isEqualTo(PostId.fromString("f7baf8b9-d52b-41c5-a266-ab8c6fcdc9af").get())
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
