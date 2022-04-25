package liveplugin.implementation.toolwindow.addplugin.git


import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import liveplugin.implementation.common.IdeUtil.runLaterOnEDT
import liveplugin.implementation.common.IdeUtil.showErrorDialog
import liveplugin.implementation.LivePluginPaths
import liveplugin.implementation.toolwindow.RefreshPluginsPanelAction
import liveplugin.implementation.toolwindow.addplugin.PluginIdValidator
import liveplugin.implementation.toolwindow.addplugin.git.AddPluginFromGistAction.GistUrlValidator.Companion.extractGistIdFrom
import liveplugin.implementation.toolwindow.util.createFile
import org.jetbrains.plugins.github.api.GithubApiRequest
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GithubGist
import org.jetbrains.plugins.github.util.GithubSettings
import java.io.IOException
import javax.swing.Icon

class AddPluginFromGistAction: AnAction("Copy from Gist", "Copy from Gist", AllIcons.Vcs.Vendors.Github), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val gistUrl = askUserForGistUrl(project) ?: return

        fetchGist(
            gistUrl,
            project,
            onSuccess = { gist ->
                val newPluginId = askUserForNewPluginName(project, gist)
                if (newPluginId != null) {
                    try {
                        createPluginFrom(gist, newPluginId)
                    } catch (e: IOException) {
                        showMessageThatCreatingPluginFailed(e, newPluginId, project)
                    }
                    RefreshPluginsPanelAction.refreshPluginTree()
                }
            },
            onFailure = {
                showMessageThatFetchingGistFailed(it, project)
            }
        )
    }

    private fun askUserForGistUrl(project: Project): String? =
        Messages.showInputDialog(
            project,
            "Enter gist URL:",
            dialogTitle,
            defaultIcon,
            "",
            GistUrlValidator()
        )

    private fun askUserForNewPluginName(project: Project, gist: GithubGist): String? =
        Messages.showInputDialog(
            project,
            "Enter new plugin name:",
            dialogTitle,
            defaultIcon,
            gist.description,
            PluginIdValidator()
        )

    private fun createPluginFrom(gist: GithubGist, pluginId: String) =
        gist.files.forEach { gistFile ->
            createFile("${LivePluginPaths.livePluginsPath}/$pluginId", gistFile.filename, gistFile.content)
        }

    private fun showMessageThatFetchingGistFailed(e: IOException?, project: Project) {
        showErrorDialog(project, "Failed to fetch gist", dialogTitle)
        if (e != null) log.info(e)
    }

    private fun showMessageThatCreatingPluginFailed(e: IOException, newPluginId: String?, project: Project) {
        showErrorDialog(project, "Error adding plugin \"$newPluginId\" to ${LivePluginPaths.livePluginsPath}", dialogTitle)
        log.info(e)
    }

    private fun fetchGist(
        gistUrl: String,
        project: Project,
        onSuccess: (GithubGist) -> Unit,
        onFailure: (IOException?) -> Unit
    ) {
        object: Task.Backgroundable(project, @Suppress("DialogTitleCapitalization") "Fetching Gist", true, ALWAYS_BACKGROUND) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val gistId = extractGistIdFrom(gistUrl)!!
                    val request = GithubApiRequests.Gists.get(GithubServerPath.DEFAULT_SERVER, gistId)
                    val gist = SimpleExecutor().execute(request)
                    runLaterOnEDT {
                        if (gist == null) onFailure(null) else onSuccess(gist)
                    }
                } catch (e: IOException) {
                    runLaterOnEDT { onFailure(e) }
                }
            }
        }.queue()
    }

    private class SimpleExecutor: GithubApiRequestExecutor.Base(GithubSettings.getInstance()) {
        override fun <T> execute(indicator: ProgressIndicator, request: GithubApiRequest<T>): T =
            createRequestBuilder(request).execute(request, indicator)
    }

    private class GistUrlValidator: InputValidatorEx {
        private var isValid = true

        override fun checkInput(inputString: String): Boolean {
            isValid = extractGistIdFrom(inputString) != null
            return isValid
        }

        override fun getErrorText(inputString: String) =
            if (isValid) null else "Couldn't parse gist URL"

        override fun canClose(inputString: String) = true

        companion object {
            fun extractGistIdFrom(gistUrl: String): String? {
                val i = gistUrl.lastIndexOf('/')
                return if (i == -1) null
                else gistUrl.substring(i + 1)
            }
        }
    }

    private companion object {
        private val log = Logger.getInstance(AddPluginFromGistAction::class.java)
        private const val dialogTitle = "Copy Plugin From Gist"
        private val defaultIcon: Icon? = null
    }
}
