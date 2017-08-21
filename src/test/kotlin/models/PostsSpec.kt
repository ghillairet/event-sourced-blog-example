package models

import events.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it

object PostsSpec : Spek({

    describe("apply") {
        val id = PostId.generate()
        val event = PostAdded(id,
                PostContent("Mark", "The power of feedback in Scrum", "Searching the web for ..."))
        val posts = Posts().apply(event)

        it("should contain one event") {
            assertThat(posts.byId)
                    .containsOnlyKeys(event.postId)
        }

        it("should update one event") {
            val state = posts.apply(PostEdited(id,
                    PostContent("Mark", "The power of Scrum", "Searching the web for ...")))

            assertThat(state.byId)
                    .containsOnlyKeys(id)

            assertThat(state.get(id).get().content)
                    .isEqualTo(PostContent("Mark", "The power of Scrum", "Searching the web for ..."))
        }

        it("should delete one event") {
            val state = posts.apply(PostDeleted(id))

            assertThat(state.byId).isEmpty()
        }
    }
})