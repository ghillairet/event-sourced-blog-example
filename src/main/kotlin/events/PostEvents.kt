package events

import com.fasterxml.jackson.annotation.JsonValue
import org.funktionale.option.Option
import java.util.*

data class PostId(private val uuid: UUID) {
    @JsonValue
    override fun toString(): String = uuid.toString()

    companion object {
        fun generate(): PostId = PostId(UUID.randomUUID())
        fun fromString(id: String?): Option<PostId> =
                try {
                    Option.Some(PostId(UUID.fromString(id)))
                } catch (e: Exception) {
                    Option.None
                }
    }
}

data class PostContent(val author: String, val title: String, val body: String)

sealed class PostEvent(open val postId: PostId)
data class PostAdded(override val postId: PostId, val content: PostContent) : PostEvent(postId)
data class PostEdited(override val postId: PostId, val content: PostContent) : PostEvent(postId)
data class PostDeleted(override val postId: PostId) : PostEvent(postId)
