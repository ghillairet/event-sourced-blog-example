package controllers

import events.*
import eventstore.CommitResult
import eventstore.MemoryImage
import eventstore.StreamRevision
import eventstore.fake.FakeEventStore
import main.PostContentForm
import models.Post
import models.Posts
import org.funktionale.option.Option
import spark.ModelAndView
import spark.template.handlebars.HandlebarsTemplateEngine

class PostsController(private val memoryImage: MemoryImage<Posts, PostEvent>) {

    companion object {
        fun get(image: MemoryImage<Posts, PostEvent>) = PostsController(image)
    }

    fun posts(): Posts = memoryImage.get()

    private fun render(model: Map<String, Any>, view: String) =
            HandlebarsTemplateEngine().render(ModelAndView(model, view))

    private fun render(extractor: (Post) -> Map<String, Any>): (PostId, String) -> String = { id, view ->
        posts().get(id).fold(
                { render(emptyMap(), "error.hbs") },
                { model -> render(extractor(model), view) })
    }

    private val renderPost = render({ post -> mapOf("post" to post) })

    private fun commit(expected: StreamRevision, event: PostEvent) =
            memoryImage.tryCommit(event.postId.toString(), expected, event)

    fun index() = render(mapOf("posts" to posts().mostRecent(20)), "all.hbs")

    fun add() = render(mapOf("id" to PostId.generate()), "add.hbs")

    fun add(id: PostId, form: PostContentForm, action: (Option<CommitResult<PostEvent>>) -> Unit) = action(form.fold(
            { Option.None },
            { content -> Option.Some(commit(StreamRevision.Initial, PostAdded(id, content))) }))

    fun show(id: PostId) = renderPost(id, "show.hbs")

    fun edit(id: PostId) = renderPost(id, "edit.hbs")

    fun edit(id: PostId, expected: StreamRevision, form: PostContentForm, action: (Option<CommitResult<PostEvent>>) -> Unit) = action(form.fold(
            { Option.None },
            { content -> Option.Some(commit(expected, PostEdited(id, content))) }))

    fun delete(id: PostId, expected: StreamRevision, action: (Option<CommitResult<PostEvent>>) -> Unit) =
            action(Option.Some(commit(expected, PostDeleted(id))))
}
