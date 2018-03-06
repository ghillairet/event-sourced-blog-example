package json

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import events.*
import eventstore.Commit
import eventstore.StoreRevision
import eventstore.StreamRevision

abstract class Json<Event> {
    abstract fun serializeCommit(commit: Commit<Event>): JsonNode
    abstract fun serializeEvent(event: Event): JsonNode
    abstract fun deserializeCommit(node: JsonNode): Commit<Event>
    abstract fun deserializeEvent(node: JsonNode): Event
}

class JsonPostEvent : Json<PostEvent>() {

    override fun serializeEvent(event: PostEvent): JsonNode {
        val node = mapper.createObjectNode()
        node.put("postId", event.postId.toString())

        when (event) {
            is PostAdded -> {
                node.put("@type", "events.PostAdded")
                node.putPOJO("content", event.content)
            }
            is PostEdited -> {
                node.put("@type", "events.PostEdited")
                node.putPOJO("content", event.content)
            }
            is PostDeleted -> {
                node.put("@type", "events.PostDeleted")
            }
        }
        return node
    }

    private val mapper = ObjectMapper()

    override fun serializeCommit(commit: Commit<PostEvent>): JsonNode {
        val node = mapper.createObjectNode()
        node.put("storeRevision", commit.storeRevision.value)
        node.put("timestamp", commit.timestamp)
        node.put("streamRevision", commit.streamRevision.value)
        node.put("streamId", commit.streamId)

        val array = node.putArray("events")
        commit.events.forEach { array.add(serializeEvent(it)) }

        return node
    }

    override fun deserializeCommit(node: JsonNode): Commit<PostEvent> {
        val storeRevision = StoreRevision(node.get("storeRevision").asLong())
        val streamRevision = StreamRevision(node.get("streamRevision").asLong())
        val timestamp = node.get("timestamp").asLong()
        val streamId = node.get("streamId").asText()

        val array = node.get("events") as ArrayNode
        val events = array.map { deserializeEvent(it) }

        return Commit(storeRevision, timestamp, streamId, streamRevision, events)
    }

    override fun deserializeEvent(node: JsonNode): PostEvent = when (node.get("@type").asText()) {
        "events.PostAdded" -> PostAdded(
                PostId.fromString(node.get("postId").asText()).get(),
                PostContent(
                        node.get("content")?.get("author")!!.asText(),
                        node.get("content")?.get("title")!!.asText(),
                        node.get("content")?.get("body")!!.asText("")))
        "events.PostEdited" -> PostEdited(
                PostId.fromString(node.get("postId").asText()).get(),
                PostContent(
                        node.get("content")?.get("author")!!.asText(),
                        node.get("content")?.get("title")!!.asText(),
                        node.get("content")?.get("body")!!.asText("")))
        else -> PostDeleted(PostId.fromString(node.get("postId")!!.asText()).get())
    }

}

