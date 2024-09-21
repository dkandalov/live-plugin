package liveplugin.implementation.actions.gist

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.util.net.JdkProxyProvider
import git4idea.checkout.GitCheckoutProvider
import git4idea.commands.Git
import liveplugin.implementation.LivePluginPaths.livePluginsPath
import liveplugin.implementation.actions.gist.GistApi.FailedRequest
import liveplugin.implementation.actions.gist.GistApi.Gist
import liveplugin.implementation.actions.git.isGitHubPluginAvailable
import liveplugin.implementation.actions.isNewPluginNameValidator
import liveplugin.implementation.common.IdeUtil.runLaterOnEdt
import liveplugin.implementation.common.IdeUtil.showError
import liveplugin.implementation.common.IdeUtil.showInputDialog
import liveplugin.implementation.common.createFile
import liveplugin.implementation.common.inputValidator
import java.io.File
import java.io.IOException
import java.net.URI

class AddPluginFromGistOrGitAction : AnAction("Copy from Gist/Git", "Copy from Gist/Git", AllIcons.Vcs.Vendors.Github), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val url = project.showInputDialog(
            message = "Enter Gist/Git URL:",
            title = dialogTitle,
            inputValidator = inputValidator { if (extractIdFrom(it) == null) "Invalid Gist/Git URL" else null }
        ) ?: return

        if (url.lowercase().startsWith("https://gist.")) {
            fetchGist(
                url,
                project,
                onSuccess = { gist ->
                    val newPluginId = project.showInputDialog(
                        message = "Enter new plugin name:",
                        title = dialogTitle,
                        inputValidator = isNewPluginNameValidator,
                        initialValue = gist.description
                    )
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
        } else if (isGitHubPluginAvailable) {
            val newPluginId = project.showInputDialog(
                message = "Enter new plugin name:",
                title = dialogTitle,
                inputValidator = isNewPluginNameValidator,
                initialValue = url.takeLastWhile { it != '/' }
            ) ?: return

            val gitService = service<Git>()
            val destinationFolder = livePluginsPath.toVirtualFile() ?: return
            GitCheckoutProvider.clone(
                /* project = */ project,
                /* git = */ gitService,
                /* listener = */ GitCheckoutListener(destinationFolder, newPluginId),
                /* destinationParent = */ destinationFolder,
                /* sourceRepositoryURL = */ url,
                /* directoryName = */ newPluginId,
                /* parentDirectory = */ destinationFolder.path
            )
        } else {
            project.showError("Cannot clone project without GitHub plugin.")
        }
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
                    val gist = GistApiHttp(proxy).getGist(extractIdFrom(gistUrl)!!)
                    runLaterOnEdt { onSuccess(gist) }
                } catch (e: FailedRequest) {
                    runLaterOnEdt { onFailure(e) }
                }
            }
        }.queue()
    }

    private class GitCheckoutListener(
        private val destinationFolder: VirtualFile,
        private val pluginName: String
    ): CheckoutProvider.Listener {
        private val logger = Logger.getInstance(GitCheckoutListener::class.java)

        override fun directoryCheckedOut(directory: File, vcs: VcsKey) {}

        override fun checkoutCompleted() {
            val finishRunnable = Runnable {
                val clonedFolder = destinationFolder.findChild(pluginName)
                if (clonedFolder == null) logger.error("Couldn't find virtual file for checked out plugin: $pluginName")
            }
            val pluginsRoot = livePluginsPath.toVirtualFile() ?: return
            RefreshQueue.getInstance().refresh(false, true, finishRunnable, pluginsRoot)
        }
    }

    private companion object {
        private const val dialogTitle = "Copy Plugin From Gist/Git"

        private fun extractIdFrom(gistOrGitUrl: String): String? {
            val i = gistOrGitUrl.lastIndexOf('/')
            return if (i == -1) null else gistOrGitUrl.substring(i + 1)
        }
    }
}
