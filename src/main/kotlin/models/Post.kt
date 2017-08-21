package models

import events.*
import org.funktionale.option.Option
import org.funktionale.option.toOption

data class Post(val id: PostId, val content: PostContent)

data class Posts(val byId: Map<PostId, Post> = emptyMap(), private val orderedByTimeAdded: List<PostId> = emptyList()) {
    fun get(id: PostId): Option<Post> = byId[id].toOption()
    fun mostRecent(n: Int): List<Post?> = orderedByTimeAdded.take(n).mapNotNull { byId[it] }

    fun apply(event: PostEvent): Posts = when (event) {
        is PostAdded -> copy(byId = byId + Pair(event.postId, Post(event.postId, event.content)), orderedByTimeAdded = orderedByTimeAdded + event.postId)
        is PostEdited -> copy(byId = byId + Pair(event.postId, Post(event.postId, event.content)))
        is PostDeleted -> copy(byId = byId - event.postId)
    }
}

fun fromHistory(vararg events: PostEvent): Posts = events.fold(Posts(), { acc, event -> acc.apply(event) })
