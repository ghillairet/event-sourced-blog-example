package controllers

import events.PostEvent
import eventstore.MemoryImage
import eventstore.fake.FakeEventStore
import eventstore.redis.RedisWatchMultiExecEventCommitter
import json.JsonPostEvent
import models.Posts
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.slf4j.LoggerFactory

object Global {

    private val LOGGER = LoggerFactory.getLogger(Global::class.java)

    val redis = memoryImage("redis")
    val fake = memoryImage("fake")

    private fun eventStore(type: String) = when (type) {
        "redis" -> {
            LOGGER.info("Starting redis event store...")
            val jedisConfig = GenericObjectPoolConfig()
            jedisConfig.minIdle = 8
            jedisConfig.maxIdle = jedisConfig.minIdle
            RedisWatchMultiExecEventCommitter("blog", "localhost", 6379, JsonPostEvent(), jedisConfig)
        }
        else -> {
            LOGGER.info("Starting fake event store...")
            FakeEventStore<PostEvent>()
        }
    }

    private fun memoryImage(type: String): MemoryImage<Posts, PostEvent> {
        return MemoryImage(eventStore(type), Posts(), { posts, commit ->
            try {
                posts.updateMany(commit.eventsWithRevision())
            } catch (e: Exception) {
                LOGGER.error(e.message, e)
                throw e
            }
        })
    }

}