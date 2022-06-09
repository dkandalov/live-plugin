package liveplugin.implementation.actions.addplugin.git

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
import liveplugin.implementation.LivePluginPaths.livePluginsPath
import liveplugin.implementation.actions.addplugin.PluginIdValidator
import liveplugin.implementation.actions.addplugin.git.GistApi.FailedRequest
import liveplugin.implementation.actions.addplugin.git.GistApi.Gist
import liveplugin.implementation.common.IdeUtil.runLaterOnEdt
import liveplugin.implementation.common.IdeUtil.showErrorDialog
import liveplugin.implementation.common.createFile
import java.io.IOException
import javax.swing.Icon

class AddPluginFromGistAction : AnAction("Copy from Gist", "Copy from Gist", AllIcons.Vcs.Vendors.Github), DumbAware {

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

    private fun askUserForNewPluginName(project: Project, gist: Gist): String? =
        Messages.showInputDialog(
            project,
            "Enter new plugin name:",
            dialogTitle,
            defaultIcon,
            gist.description,
            PluginIdValidator()
        )

    private fun createPluginFrom(gist: Gist, pluginId: String) =
        gist.files.forEach { (filename, file) ->
            createFile("$livePluginsPath/$pluginId", filename, file.content)
        }

    private fun showMessageThatFetchingGistFailed(e: FailedRequest?, project: Project) {
        project.showErrorDialog("Failed to fetch gist", dialogTitle)
        if (e != null) log.info(e)
    }

    private fun showMessageThatCreatingPluginFailed(e: IOException, newPluginId: String?, project: Project) {
        project.showErrorDialog("Error adding plugin \"$newPluginId\"", dialogTitle)
        log.info(e)
    }

    private fun fetchGist(
        gistUrl: String,
        project: Project,
        onSuccess: (Gist) -> Unit,
        onFailure: (FailedRequest?) -> Unit
    ) {
        object : Task.Backgroundable(project, "Fetching gist…", true, ALWAYS_BACKGROUND) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val gist = GistApi().getGist(extractGistIdFrom(gistUrl)!!)
                    runLaterOnEdt { onSuccess(gist) }
                } catch (e: FailedRequest) {
                    runLaterOnEdt { onFailure(e) }
                }
            }
        }.queue()
    }

    private class GistUrlValidator : InputValidatorEx {
        private var isValid = true

        override fun checkInput(inputString: String): Boolean {
            isValid = extractGistIdFrom(inputString) != null
            return isValid
        }

        override fun getErrorText(inputString: String) =
            if (isValid) null else "Couldn't parse gist URL"

        override fun canClose(inputString: String) = true
    }

    private companion object {
        private val log = Logger.getInstance(AddPluginFromGistAction::class.java)
        private const val dialogTitle = "Copy Plugin From Gist"
        private val defaultIcon: Icon? = null

        private fun extractGistIdFrom(gistUrl: String): String? {
            val i = gistUrl.lastIndexOf('/')
            return if (i == -1) null else gistUrl.substring(i + 1)
        }
    }
}
