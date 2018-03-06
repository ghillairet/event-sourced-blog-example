package models

import events.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class PostsTest {

    @Test
    fun testApply() {
        val id = PostId.generate()
        val event = PostAdded(id,
                PostContent("Mark", "The power of feedback in Scrum", "Searching the web for ..."))
        val posts = Posts().apply(event)

        assertThat(posts.byId)
                .containsOnlyKeys(event.postId)

        val secondState = posts.apply(PostEdited(id,
                PostContent("Mark", "The power of Scrum", "Searching the web for ...")))

        assertThat(secondState.byId)
                .containsOnlyKeys(id)

        assertThat(secondState.get(id).get().content)
                .isEqualTo(PostContent("Mark", "The power of Scrum", "Searching the web for ..."))

        val thirdState = posts.apply(PostDeleted(id))

        assertThat(thirdState.byId).isEmpty()

    }
}