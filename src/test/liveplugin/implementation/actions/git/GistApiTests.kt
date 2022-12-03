package liveplugin.implementation.actions.git

import liveplugin.implementation.actions.gist.GistApi.*
import liveplugin.implementation.actions.gist.GistApiHttp
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual.equalTo
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.then
import org.http4k.filter.TrafficFilters.RecordTo
import org.http4k.filter.TrafficFilters.ServeCachedFrom
import org.http4k.traffic.ReadWriteCache
import org.junit.Ignore
import org.junit.Test
import org.junit.jupiter.api.assertThrows

@Ignore
class GistApiExternalServerTests : GistApiTests(
    httpClient = RecordTo(storage).then(GistApiHttp.defaultHandler()),
    authToken = System.getenv("GIST_API_TOKEN") ?: ""
)

class GistApiReplayingServerTests : GistApiTests(
    httpClient = ServeCachedFrom(storage).then { request -> error("no recorded response for request:\n$request") },
    authToken = "dummy-token"
)

private val storage = diskReadWriteCache("src/test/liveplugin/implementation/actions/git/recorded_traffic").sanitized()

abstract class GistApiTests(
    httpClient: HttpHandler,
    private val authToken: String
) {
    private val gistApi = GistApiHttp(httpClient)
    private val someGist = Gist(description = "test", files = mapOf("test.txt" to GistFile("some file content")), public = false)

    @Test fun `create and delete gist`() {
        val createGist = gistApi.create(someGist, authToken)
        assertThat(someGist.copy(id = createGist.id, htmlUrl = createGist.htmlUrl), equalTo(createGist))

        gistApi.delete(createGist.id, authToken)
    }

    @Test fun `create and delete public gist without description`() {
        val gist = someGist.copy(description = "", public = true)
        val createGist = gistApi.create(gist, authToken)
        assertThat(createGist.copy(id = createGist.id, htmlUrl = createGist.htmlUrl), equalTo(createGist))

        gistApi.delete(createGist.id, authToken)
    }

    @Test fun `update gist and list its revisions`() {
        val gist = gistApi.create(someGist, authToken)
        try {
            val updatedGist = gistApi.update(gist.copy(files = mapOf("test.txt" to GistFile("updated file content"))), authToken)

            val gistCommits = gistApi.listCommits(gist.id)
            assertThat(gistCommits.size, equalTo(2))

            val gists = gistCommits.map { gistApi.getGistRevision(gist.id, sha = it.version) }
            assertThat(gists[0], equalTo(updatedGist))
            assertThat(gists[1], equalTo(gist))
        } finally {
            gistApi.delete(gist.id, authToken)
        }
    }

    @Test fun `get public gist without authentication`() {
        assertThat(
            gistApi.getGist(gistId = "d51de2311dfd92dfa56feb3e3f9f96a6"),
            equalTo(
                Gist(
                    id = "d51de2311dfd92dfa56feb3e3f9f96a6",
                    description = "Workaround for https://youtrack.jetbrains.com/issue/IDEA-171273 (use https://github.com/dkandalov/live-plugin to run the code snippet)",
                    files = mapOf(
                        "plugin.kts" to GistFile(
                            """
                                import com.intellij.openapi.actionSystem.*
            
                                val newElementActionGroup = ActionManager.getInstance().getAction(IdeActions.GROUP_NEW) as DefaultActionGroup
                                val newKotlinFileAction = ActionManager.getInstance().getAction("Kotlin.NewFile") as AnAction
            
                                newElementActionGroup.remove(newKotlinFileAction)
                                newElementActionGroup.add(newKotlinFileAction, Constraints.FIRST)
                                
                            """.trimIndent()
                        )
                    ),
                    htmlUrl = "https://gist.github.com/d51de2311dfd92dfa56feb3e3f9f96a6"
                )
            )
        )
    }

    @Test fun `list commits of public gist without authentication`() {
        assertThat(
            gistApi.listCommits(gistId = "d51de2311dfd92dfa56feb3e3f9f96a6"),
            equalTo(
                listOf(
                    GistCommit(version = "a6e4e0c2fe4664ce76c2cb5b5d22c60b3b484e70"),
                    GistCommit(version = "d4375bcd40b8a241ba656c8162de1cfe478f7db0")
                )
            )
        )
    }

    @Test fun `fail to create invalid gist with no files`() {
        assertThrows<FailedRequest> {
            gistApi.create(someGist.copy(files = emptyMap()), authToken)
        }
    }

    @Test fun `fail to access non-existing gist`() {
        assertThrows<FailedRequest> { gistApi.update(Gist(id = "non-existing-id", files = emptyMap()), authToken) }
        assertThrows<FailedRequest> { gistApi.delete(gistId = "non-existing-id", authToken) }
        assertThrows<FailedRequest> { gistApi.getGist(gistId = "non-existing-id") }
        assertThrows<FailedRequest> { gistApi.listCommits(gistId = "non-existing-id") }
        assertThrows<FailedRequest> { gistApi.getGistRevision(gistId = "non-existing-id", sha = "some-sha") }
    }
}

private fun ReadWriteCache.sanitized() = object : ReadWriteCache {
    override fun set(request: Request, response: Response) {
        this@sanitized[request.sanitized()] = response.sanitised()
    }

    override fun get(request: Request) = this@sanitized[request.sanitized()]

    private fun Response.sanitised() = removeHeader("date")
        .removeHeader("etag")
        .removeHeader("last-modified")
        .removeHeaders("github-")
        .removeHeaders("x-")

    private fun Request.sanitized() = replaceHeaderIfExists("Authorization", "Bearer dummy-token")

    private fun Request.replaceHeaderIfExists(name: String, value: String?) =
        if (header(name) == null) this
        else removeHeader(name).header(name, value)
}
