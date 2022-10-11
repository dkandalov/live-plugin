package liveplugin.implementation.actions.addplugin.git

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.introspect.VisibilityChecker
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import liveplugin.implementation.actions.addplugin.git.GistApi.*
import okhttp3.OkHttpClient
import org.http4k.client.OkHttp
import org.http4k.core.*
import org.http4k.core.Method.*
import org.http4k.core.Status.Companion.CREATED
import org.http4k.core.Status.Companion.NO_CONTENT
import org.http4k.core.Status.Companion.OK
import org.http4k.filter.ClientFilters.SetBaseUriFrom
import java.net.Proxy
import java.text.SimpleDateFormat
import java.util.*

interface GistApi {
    // https://docs.github.com/en/rest/gists/gists#create-a-gist
    fun create(gist: Gist, authToken: String): Gist

    // https://docs.github.com/en/rest/gists/gists#update-a-gist
    fun update(gist: Gist, authToken: String): Gist

    // https://docs.github.com/en/rest/gists/gists#delete-a-gist
    fun delete(gistId: String, authToken: String)

    // https://docs.github.com/en/rest/gists/gists#get-a-gist
    fun getGist(gistId: String): Gist

    // https://docs.github.com/en/rest/gists/gists#list-gist-commits
    fun listCommits(gistId: String): List<GistCommit>

    // https://docs.github.com/en/rest/gists/gists#get-a-gist-revision
    fun getGistRevision(gistId: String, sha: String): Gist

    data class Gist(
        val id: String = "",
        val description: String? = "",
        val files: Map<String, GistFile>,
        val public: Boolean = true,
        val htmlUrl: String = ""
    )

    data class GistFile(val content: String)
    data class GistCommit(val version: String)

    class FailedRequest(message: String) : Exception(message)
}

class GistApiHttp(httpHandler: HttpHandler = defaultHandler()) : GistApi {
    constructor(proxy: Proxy?) : this(defaultHandler(proxy))

    private val client = httpHandler.with(AcceptGithubJsonHeader())

    override fun create(gist: Gist, authToken: String): Gist {
        val request = Request(POST, "")
            .header("Authorization", "Bearer $authToken")
            .body(objectMapper.writeValueAsString(gist))
        return client(request).expectStatus(CREATED).parse()
    }

    override fun update(gist: Gist, authToken: String): Gist {
        val request = Request(PATCH, gist.id)
            .header("Authorization", "Bearer $authToken")
            .body(objectMapper.writeValueAsString(gist))
        return client(request).expectStatus(OK).parse()
    }

    override fun delete(gistId: String, authToken: String) {
        val request = Request(DELETE, gistId)
            .header("Authorization", "Bearer $authToken")
        client(request).expectStatus(NO_CONTENT)
    }

    override fun getGist(gistId: String): Gist =
        client(Request(GET, gistId)).expectStatus(OK).parse()

    override fun listCommits(gistId: String): List<GistCommit> =
        client(Request(GET, "$gistId/commits")).expectStatus(OK).parseList()

    override fun getGistRevision(gistId: String, sha: String): Gist =
        client(Request(GET, "$gistId/$sha")).expectStatus(OK).parse()

    private fun Response.expectStatus(status: Status): Response {
        if (this.status == status) return this
        else throw FailedRequest("Expected status ${status.code} but was ${this.status.code}")
    }

    private inline fun <reified T> Response.parseList(): List<T> =
        objectMapper.readValue(
            body.stream,
            objectMapper.typeFactory.constructCollectionType(List::class.java, T::class.java)
        )

    private inline fun <reified T> Response.parse(): T =
        objectMapper.readValue(
            body.stream,
            objectMapper.typeFactory.constructType(T::class.java)
        )

    companion object {
        fun defaultHandler(proxy: Proxy? = null): HttpHandler {
            val okHttpClient = OkHttpClient.Builder()
                .proxy(proxy)
                .followRedirects(true)
                .build()
            return OkHttp(okHttpClient).with(SetBaseUriFrom(Uri.of("https://api.github.com/gists")))
        }

        private class AcceptGithubJsonHeader : Filter {
            override fun invoke(handler: HttpHandler) = { request: Request ->
                handler(request.header("Accept", "application/vnd.github.v3+json"))
            }
        }

        private val objectMapper = jacksonMapperBuilder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .build()
            .setDateFormat(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX"))
            .setTimeZone(TimeZone.getDefault())
            .setSerializationInclusion(NON_EMPTY)
            .setPropertyNamingStrategy(SNAKE_CASE)
            .setVisibility(
                VisibilityChecker.Std(
                    /* getter = */ Visibility.NONE,
                    /* isGetter = */ Visibility.NONE,
                    /* setter = */ Visibility.NONE,
                    /* creator = */ Visibility.NONE,
                    /* field = */ Visibility.ANY
                )
            )

        private fun HttpHandler.with(filter: Filter) = filter.then(this)
    }
}
