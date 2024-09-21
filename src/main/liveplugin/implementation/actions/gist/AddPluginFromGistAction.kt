package liveplugin.implementation.actions.gist

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.util.net.JdkProxyProvider
import liveplugin.implementation.LivePluginPaths.livePluginsPath
import liveplugin.implementation.actions.gist.GistApi.FailedRequest
import liveplugin.implementation.actions.gist.GistApi.Gist
import liveplugin.implementation.actions.isNewPluginNameValidator
import liveplugin.implementation.common.IdeUtil.runLaterOnEdt
import liveplugin.implementation.common.IdeUtil.showError
import liveplugin.implementation.common.IdeUtil.showInputDialog
import liveplugin.implementation.common.createFile
import liveplugin.implementation.common.inputValidator
import java.io.IOException
import java.net.URI

class AddPluginFromGistAction : AnAction("Copy from Gist", "Copy from Gist", AllIcons.Vcs.Vendors.Github), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        val gistUrl = project.showInputDialog(
            message = "Enter Gist URL:",
            dialogTitle,
            inputValidator { if (extractGistIdFrom(it) == null) "Couldn't parse Gist URL" else null }
        ) ?: return

        fetchGist(
            gistUrl,
            project,
            onSuccess = { gist ->
                val newPluginId = project.showInputDialog(message = "Enter new plugin name:", dialogTitle, isNewPluginNameValidator, gist.description)
                if (newPluginId != null) {
                    try {
                        gist.files.forEach { (filename, file) ->
                            createFile("$livePluginsPath/$newPluginId", filename, file.content)
                        }
                    } catch (e: IOException) {
                        project.showError("Error adding plugin \"$newPluginId\": ${e.message}", e)
                    }
                }
            },
            onFailure = { e ->
                project.showError("Failed to fetch Gist: ${e.message}", e)
            }
        )
    }

    private fun fetchGist(
        gistUrl: String,
        project: Project?,
        onSuccess: (Gist) -> Unit,
        onFailure: (FailedRequest) -> Unit
    ) {
        object : Task.Backgroundable(project, "Fetching gist…", true, ALWAYS_BACKGROUND) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val proxy = JdkProxyProvider.getInstance().proxySelector.select(URI("https://api.github.com/gists")).firstOrNull()
                    val gist = GistApiHttp(proxy).getGist(extractGistIdFrom(gistUrl)!!)
                    runLaterOnEdt { onSuccess(gist) }
                } catch (e: FailedRequest) {
                    runLaterOnEdt { onFailure(e) }
                }
            }
        }.queue()
    }

    private companion object {
        private const val dialogTitle = "Copy Plugin From Gist"

        private fun extractGistIdFrom(gistUrl: String): String? {
            val i = gistUrl.lastIndexOf('/')
            return if (i == -1) null else gistUrl.substring(i + 1)
        }
    }
}
