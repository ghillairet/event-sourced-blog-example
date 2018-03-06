package main

import controllers.Global
import controllers.PostsController
import events.PostContent
import events.PostId
import events.PostId.Companion.fromString
import eventstore.StreamRevision
import org.funktionale.either.Either
import org.funktionale.option.Option
import spark.Redirect
import spark.Request
import spark.Response
import spark.Spark.get
import spark.Spark.post


fun Response.invalid() = {
    status(401)
    redirect("/")
}

typealias PostContentForm = Either<Exception, PostContent>

fun Request.postContentForm(): PostContentForm =
        try {
            val content = PostContent(
                    queryParams("author"),
                    queryParams("title"),
                    queryParams("body"))
            Either.Right(content)
        } catch (e: Exception) {
            Either.Left(e)
        }

fun main(args: Array<String>) {

    val controller = PostsController.get(Global.redis)

    fun Request.postId(): Option<PostId> = fromString(this.params(":id"))
    fun Request.streamRevision(): Option<StreamRevision> = try {
        Option.Some(StreamRevision(this.queryParams("r").toLong()))
    } catch (e: Exception) {
        Option.None
    }

    fun Request.idAndRevision(): Option<Pair<PostId, StreamRevision>> {
        val id = this.postId()
        return id.fold({ Option.None }, {
            this.streamRevision().fold({ Option.None }, { Option.Some(Pair(id.get(), it)) })
        })
    }

    get("/", { _, _ ->
        controller.index()
    })
    get("/posts/new", { _, _ ->
        controller.add()
    })
    get("/posts/:id", { req, resp ->
        req.postId().fold({ resp.invalid() }, { id -> controller.show(id) })
    })
    get("/posts/:id/edit", { req, resp ->
        req.postId().fold({ resp.invalid() }, { id -> controller.edit(id) })
    })

    post("/posts/:id/delete", { req, resp ->
        req.idAndRevision().fold({ resp.invalid() }, { (id, revision) ->
            controller.delete(id, revision, { result ->
                result.fold({
                    resp.redirect("/", Redirect.Status.NOT_MODIFIED.intValue())
                }, {
                    resp.redirect("/", Redirect.Status.FOUND.intValue())
                })
            })
        })
    })
    post("/posts/:id/add", { req, resp ->
        req.postId().fold({ resp.invalid() }, { id ->
            controller.add(id, req.postContentForm(), { result ->
                result.fold({
                    resp.redirect("/", Redirect.Status.NOT_MODIFIED.intValue())
                }, {
                    resp.redirect("/", Redirect.Status.FOUND.intValue())
                })
            })
        })
    })
    post("/posts/:id/edit", { req, resp ->
        req.idAndRevision().fold(
                { resp.invalid() },
                { (id, revision) ->
                    controller.edit(id, revision, req.postContentForm(), {
                        it.fold(
                                { resp.redirect("/", Redirect.Status.NOT_MODIFIED.intValue()) },
                                { resp.redirect("/", Redirect.Status.FOUND.intValue()) })
                    })
                })
    })
}
