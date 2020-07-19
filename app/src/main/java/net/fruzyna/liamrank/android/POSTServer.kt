package net.fruzyna.liamrank.android

import android.util.Base64
import fi.iki.elonen.NanoHTTPD
import java.io.*
import kotlin.collections.HashMap

class POSTServer(directory: String, apiKey: String) : NanoHTTPD(8080) {
    private var directory = ""
    private var apiKey = ""

    init {
        this.directory = directory
        this.apiKey = apiKey
        start(SOCKET_READ_TIMEOUT, false)
    }

    override fun serve(session: IHTTPSession): Response? {
        var request = session.uri
        if (request == "/") {
            request = "/index.html"
        }
        println("Request: $request")

        // get upload directory
        val uploadDir = File(directory, "uploads")
        uploadDir.mkdirs()

        // save posted files to /uploads
        if (session.method == Method.POST) {
            val post = HashMap<String, String>()
            session.parseBody(post)
            if (post.containsKey("postData")) {
                val upload = post["postData"]!!.split("|||")
                if (upload.size > 1) {
                    var file = upload[0]
                    var content = upload[1]

                    if (content.contains("data:image/png;base64")) {
                        file += ".png"
                        content = content.replace("data:image/png;base64", "")

                        // write PNG to file
                        val writer = DataInputStream(FileInputStream(File(uploadDir, file)))
                        writer.readFully(Base64.decode(content, Base64.DEFAULT))
                        writer.close()

                        println("Received PNG image $file")
                        return newFixedLengthResponse(Response.Status.OK, "text/plain", "Received PNG image")
                    }
                    else if (!file.contains(".")) {
                        file += ".json"
                    }

                    // write JSON to file
                    val writer = FileWriter(File(uploadDir, file))
                    writer.write(content)
                    writer.close()

                    println("Received JSON file $file")
                    return newFixedLengthResponse(Response.Status.OK, "text/plain", "Received JSON file")
                }
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "No file name found")
            }
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "No data attached")
        }

        var uploads = uploadDir.listFiles()
        if (request == "/getPitResultNames") {
            uploads = uploadDir.listFiles { file ->
                file.exists() && file.name.startsWith("pit-") && file.name.endsWith(".json")
            }
        }
        else if (request == "/getImageNames") {
            uploads = uploadDir.listFiles { file ->
                file.exists() && file.name.startsWith("image-") && file.name.endsWith(".png")
            }
        }
        else if (request == "/getMatchResultNames") {
            uploads = uploadDir.listFiles { file ->
                file.exists() && file.name.startsWith("match-") && file.name.endsWith(".json")
            }
        }
        else if (request == "/getNoteNames") {
            uploads = uploadDir.listFiles { file ->
                file.exists() && file.name.startsWith("note-") && file.name.endsWith(".json")
            }
        }
        else if (request == "/about") {
            return newFixedLengthResponse(Response.Status.OK, "text/html", "<!DOCTYPE html><html lang=\"en\"><html><head><meta charset=\"utf-8\"/><title>LiamRank Android</title></head><body><h1>Liam Rank</h1>POSTServer.kt Kotlin POST server<br>2020 Liam Fruzyna<br><a href=\"https://github.com/mail929/LiamRank-Android\">MPL Licensed on GitHub</a></body></html>")
        }
        else {
            // determine mime type
            val file = File(directory, request)
            var mime = when (file.extension) {
                "html" -> "text/html"
                "css" -> "text/css"
                "js" -> "text/javascript"
                "json" -> "text/plain"
                "ico" -> "image/x-icon"
                "png" -> "image/png"
                "svg" -> "image/svg+xml"
                else -> return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/plain", "File not allowed")
            }

            // return API key (not contained in repo)
            return if (request == "/scripts/keys.js") {
                println("Returning API key")
                newFixedLengthResponse(Response.Status.OK, mime, "API_KEY=\"$apiKey\"")
            }
            // return file if it exists
            else if (file.exists()) {
                newChunkedResponse(Response.Status.OK, mime, FileInputStream(file))
            }
            // return 404
            else {
                println("$request does not exist")
                newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found")
            }
        }

        return newFixedLengthResponse(Response.Status.OK, "text/plain", uploads.joinToString(","))
    }
}