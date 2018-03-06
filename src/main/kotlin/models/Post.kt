package models

import events.*
import eventstore.StreamRevision
import org.funktionale.option.Option
import org.funktionale.option.toOption

data class Post(val id: PostId, val content: PostContent, val revision: StreamRevision)

data class Posts(val byId: Map<PostId, Post> = emptyMap(), private val orderedByTimeAdded: List<PostId> = emptyList()) {
    fun get(id: PostId): Option<Post> = byId[id].toOption()
    fun mostRecent(n: Int): List<Post?> = orderedByTimeAdded.take(n).mapNotNull { byId[it] }

    fun update(event: PostEvent, revision: StreamRevision): Posts = when (event) {
        is PostAdded ->
            copy(byId = byId + Pair(event.postId, Post(event.postId, event.content, revision)), orderedByTimeAdded = orderedByTimeAdded + event.postId)
        is PostEdited ->
            copy(byId = byId + Pair(event.postId, Post(event.postId, event.content, revision)))
        is PostDeleted ->
            copy(byId = byId - event.postId, orderedByTimeAdded = orderedByTimeAdded.filterNot { it == event.postId })
    }

    fun updateMany(events: List<Pair<PostEvent, StreamRevision>>): Posts =
            events.fold(this, { posts, e -> posts.update(e.first, e.second) })

    companion object {
        fun fromHistory(events: List<Pair<PostEvent, StreamRevision>>): Posts
                = Posts().updateMany(events)
    }
}
