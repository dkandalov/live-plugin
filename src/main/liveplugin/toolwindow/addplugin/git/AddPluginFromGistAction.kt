package liveplugin.toolwindow.addplugin.git


import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import icons.GithubIcons.Github_icon
import liveplugin.IdeUtil.showErrorDialog
import liveplugin.LivePluginAppComponent
import liveplugin.LivePluginAppComponent.Companion.livePluginsPath
import liveplugin.toolwindow.RefreshPluginsPanelAction
import liveplugin.toolwindow.addplugin.PluginIdValidator
import liveplugin.toolwindow.util.createFile
import org.jetbrains.plugins.github.api.GithubApiUtil
import org.jetbrains.plugins.github.api.GithubConnection
import org.jetbrains.plugins.github.api.data.GithubGist
import org.jetbrains.plugins.github.util.GithubAuthData
import java.io.IOException
import javax.swing.Icon

class AddPluginFromGistAction: AnAction("Clone from Gist", "Clone from Gist", Github_icon), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        val gistUrl = askUserForGistUrl(event) ?: return

        fetchGistFrom(gistUrl, event, object: FetchGistCallback {
            override fun onSuccess(gist: GithubGist) {
                val newPluginId = askUserForPluginId(project) ?: return
                try {
                    createPluginFrom(gist, newPluginId)
                } catch (e: IOException) {
                    showMessageThatCreatingPluginFailed(e, newPluginId, project)
                }
                RefreshPluginsPanelAction.refreshPluginTree()
            }

            override fun onFailure(e: IOException?) {
                showMessageThatFetchingGistFailed(e, project)
            }
        })
    }

    private fun showMessageThatCreatingPluginFailed(e: IOException, newPluginId: String?, project: Project?) {
        showErrorDialog(project, "Error adding plugin \"$newPluginId\" to $livePluginsPath", dialogTitle)
        log.info(e)
    }


    private interface FetchGistCallback {
        fun onSuccess(gist: GithubGist)
        fun onFailure(e: IOException?)
    }


    private class GistUrlValidator: InputValidatorEx {
        private var errorText: String? = null

        override fun checkInput(inputString: String): Boolean {
            val isValid = inputString.lastIndexOf('/') != -1
            errorText = if (isValid) null else "Gist URL should have at least one '/' symbol"
            return isValid
        }

        override fun getErrorText(inputString: String) = errorText

        override fun canClose(inputString: String) = true

        companion object {
            fun extractGistIdFrom(gistUrl: String): String {
                val i = gistUrl.lastIndexOf('/')
                return gistUrl.substring(i + 1)
            }
        }
    }

    companion object {
        private val log = Logger.getInstance(AddPluginFromGistAction::class.java)
        private val dialogTitle = "Clone Plugin From Gist"
        private val defaultIcon: Icon? = null

        private fun askUserForGistUrl(event: AnActionEvent): String? {
            return Messages.showInputDialog(
                event.project,
                "Enter gist URL:",
                dialogTitle,
                defaultIcon, "", GistUrlValidator()
            )
        }

        private fun fetchGistFrom(gistUrl: String, event: AnActionEvent, callback: FetchGistCallback) {
            object: Task.Backgroundable(event.project, "Fetching Gist", false, ALWAYS_BACKGROUND) {
                private var gist: GithubGist? = null
                private var exception: IOException? = null

                override fun run(indicator: ProgressIndicator) {
                    try {
                        val connection = GithubConnection(GithubAuthData.createAnonymous(), false)
                        gist = GithubApiUtil.getGist(connection, GistUrlValidator.extractGistIdFrom(gistUrl))
                    } catch (e: IOException) {
                        exception = e
                    }
                }

                override fun onSuccess() {
                    if (exception != null) callback.onFailure(exception)
                    else callback.onSuccess(gist!!)
                }
            }.queue()
        }

        private fun askUserForPluginId(project: Project?): String? {
            return Messages.showInputDialog(
                project,
                "Enter new plugin name:",
                dialogTitle,
                defaultIcon, "", PluginIdValidator()
            )
        }

        private fun createPluginFrom(gist: GithubGist, pluginId: String?) {
            gist.files.forEach { gistFile ->
                createFile(LivePluginAppComponent.livePluginsPath + "/" + pluginId, gistFile.filename, gistFile.content)
            }
        }

        private fun showMessageThatFetchingGistFailed(e: IOException?, project: Project?) {
            showErrorDialog(project, "Failed to fetch gist", dialogTitle)
            log.info(e!!)
        }
    }
}
