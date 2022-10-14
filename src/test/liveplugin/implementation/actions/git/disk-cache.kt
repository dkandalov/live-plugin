package liveplugin.implementation.actions.git

import org.http4k.core.HttpMessage
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.parse
import org.http4k.traffic.ReadWriteCache
import org.http4k.traffic.Sink
import org.http4k.traffic.Source
import java.io.File
import java.security.MessageDigest
import java.util.*

fun diskReadWriteCache(baseDir: String = ".", shouldStore: (HttpMessage) -> Boolean = { true }): ReadWriteCache =
    object : ReadWriteCache, Source by diskSource(baseDir), Sink by diskSink(baseDir, shouldStore) {}

private fun diskSink(baseDir: String, shouldStore: (HttpMessage) -> Boolean = { true }) =
    Sink { request, response ->
        val requestFolder = request.toFolder(File(baseDir))
        if (shouldStore(request)) request.writeTo(requestFolder)
        if (shouldStore(response)) response.writeTo(requestFolder)
    }

private fun diskSource(baseDir: String) = Source { request ->
    val file = File(request.toFolder(File(baseDir)), "response.txt")
    if (!file.exists()) null else {
        val responseString = String(file.readBytes()).replace("\r\n", "\n")
        Response.parse(responseString, lineBreak = "\n")
    }
}

private fun Request.toFolder(baseDir: File): File {
    val parentFile = File(baseDir, uri.path)
    val digest = String(Base64.getEncoder().encode(MessageDigest.getInstance("SHA1").digest(toString().toByteArray())))
    val child = (method.name.lowercase() + "-" + digest).replace(File.separatorChar, '_')
    return File(parentFile, child)
}

private fun HttpMessage.writeTo(folder: File) {
    folder.mkdirs()
    val file = toFile(folder)
    file.createNewFile()
    file.writeBytes(toString().toByteArray())
}

private fun HttpMessage.toFile(folder: File) =
    File(folder, if (this is Request) "request.txt" else "response.txt")
