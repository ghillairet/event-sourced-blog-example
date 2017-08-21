package main

import events.*
import events.PostId.Companion.fromString
import models.Post
import models.fromHistory
import org.funktionale.option.Option
import org.funktionale.tries.Try
import org.funktionale.tries.Try.Failure
import org.funktionale.tries.Try.Success
import spark.ModelAndView
import spark.Request
import spark.Response
import spark.Spark.get
import spark.Spark.post
import spark.template.handlebars.HandlebarsTemplateEngine

fun parsePost(builder: (id: PostId) -> PostEvent): (Option<PostId>) -> Try<Option<PostEvent>> = {
    try {
        Success(it.map { builder(it) })
    } catch (e: Exception) {
        Failure(e)
    }
}

fun Request.parsePostForm(builder: (id: PostId, content: PostContent) -> PostEvent): (Option<PostId>) -> Try<Option<PostEvent>> = {
    try {
        Success(it.map { builder(it, fromRequest(this)) })
    } catch (e: Exception) {
        Failure(e)
    }
}

fun added(): (PostId, PostContent) -> PostAdded = { id, content -> PostAdded(id, content) }
fun edited(): (PostId, PostContent) -> PostEdited = { id, content -> PostEdited(id, content) }
fun deleted(): (PostId) -> PostDeleted = { PostDeleted(it) }

fun fromRequest(req: Request): PostContent = PostContent(
        req.queryParams("author"),
        req.queryParams("title"),
        req.queryParams("body"))


fun main(args: Array<String>) {

    val lock = Object()

    var posts = fromHistory(
            PostAdded(PostId.generate(), PostContent("Mark", "The power of feedback in Scrum", "Searching the web for ...")),
            PostAdded(PostId.generate(), PostContent("Erik", "Picking the right abstraction", "Recently I had to ...")),
            PostAdded(PostId.generate(), PostContent("Michael", "Architect in Scrum", "Last friday I gave ...")))

    fun Request.postId(): Option<PostId> = fromString(this.params(":id"))

    fun Response.success(event: PostEvent) {
        synchronized(lock) {
            posts = posts.apply(event)
        }

        this.status(201)
        this.redirect("/")
    }

    fun Response.fail() {
        this.status(400)
        this.redirect("/")
    }

    fun render(extractor: (Post) -> Map<String, Any>): (Option<PostId>, String) -> ModelAndView = { id, view ->
        val post = id.flatMap { posts.get(id.get()) }
        when (post) {
            is Option.Some<Post> -> ModelAndView(extractor(post.get()), view)
            else -> ModelAndView(emptyMap<String, Any>(), "error.hbs")
        }
    }

    val renderPost = render({ post -> mapOf("post" to post) })

    get("/", { _, _ ->
        ModelAndView(mapOf("posts" to posts.mostRecent(20)), "all.hbs")
    }, HandlebarsTemplateEngine())

    get("/posts/new", { _, _ ->
        ModelAndView(mapOf("id" to PostId.generate()), "add.hbs")
    }, HandlebarsTemplateEngine())

    get("/posts/:id", { req, _ ->
        renderPost(req.postId(), "show.hbs")
    }, HandlebarsTemplateEngine())

    get("/posts/:id/edit", { req, _ ->
        renderPost(req.postId(), "edit.hbs")
    }, HandlebarsTemplateEngine())

    post("/posts/:id/delete", { req, resp ->
        parsePost(deleted())(req.postId())
                .onSuccess { it.forEach { resp.success(it) } }
                .onFailure { resp.fail() }
    })

    post("/posts/:id/add", { req, resp ->
        req.parsePostForm(added())(req.postId())
                .onSuccess { it.forEach { resp.success(it) } }
                .onFailure { resp.fail() }
    })

    post("/posts/:id/edit", { req, resp ->
        req.parsePostForm(edited())(req.postId())
                .onSuccess { it.forEach { resp.success(it) } }
                .onFailure { resp.fail() }
    })
}
