package com.example.lanscreendupe

import android.content.Context
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*

object HttpServer {

    private var server: EmbeddedServer<*, *>? = null

    fun startServer(context: Context) {
        if (server != null) return

        val clientHtml = try {
            context.assets.open("index.html").bufferedReader().use { it.readText() }
        } catch (e: Exception){
            "Error loading index.html: ${e.message}"
        }

        val viewOnlyHtml = try { context.assets.open("index.html").bufferedReader().useLines { lines ->
                lines.filterIndexed { index, _ -> index !in 56..102 }.joinToString("\n")
            }
        } catch (e: Exception) {
            "Error loading index.html: ${e.message}"
        }

        CoroutineScope(Dispatchers.IO).launch { //a coroutine scope is better for big ktor servers. no significant benefit in this app
            server = embeddedServer(CIO, port = 8080, host = "0.0.0.0") {
                routing {
                    get("/") {
                        if (ScreenCaptureService.rcsOn) { //probably bloating with conditions; only using clientHtml might be a better UX too
                            call.respondText(clientHtml, ContentType.Text.Html)
                        } else {
                            call.respondText(viewOnlyHtml, ContentType.Text.Html)
                        }
                    }
                    get("/offer") {
                        try {
                            call.respondText(ScreenCaptureService.getOffer())
                        } catch (e: Exception) {
                            val errorLog = "Offer error: ${e.message}\n${e.stackTraceToString()}"
                            call.respondText(errorLog, status = HttpStatusCode.InternalServerError)
                        }
                    }
                    post("/answer") {
                        ScreenCaptureService.setAnswer(call.receiveText())
                        call.respondText("OK")
                    }
                    get("/info") {
                        call.respondText(context.assets.open("info.html").bufferedReader().use { it.readText() }, ContentType.Text.Html)
                    }
                }
            }.start(wait = false)
        }
    }

    fun stopServer() {
        server?.stop(100, 500)
        server = null
    }
}
