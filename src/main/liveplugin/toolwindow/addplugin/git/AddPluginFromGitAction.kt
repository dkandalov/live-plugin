package liveplugin.toolwindow.addplugin.git

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import git4idea.checkout.GitCheckoutProvider
import git4idea.commands.Git
import liveplugin.LivePluginPaths
import liveplugin.toolwindow.RefreshPluginsPanelAction
import java.io.File


/**
 * Partially copied from org.jetbrains.plugins.github.GithubCheckoutProvider (became com.intellij.dvcs.ui.CloneDvcsDialog in IJ13)
 */
class AddPluginFromGitAction: AnAction("Clone from Git", "Clone from Git", AllIcons.Vcs.Vendors.Github), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return

        val dialog = GitCloneDialog(project)
        dialog.show()
        if (!dialog.isOK) return
        dialog.rememberSettings()

        val destinationFolder = dialog.parentDirectory.refreshAndFindFileByUrl() ?: return
        val gitService = service<Git>()

        GitCheckoutProvider.clone(
            project,
            gitService,
            MyCheckoutListener(destinationFolder, dialog.directoryName),
            destinationFolder,
            dialog.sourceRepositoryURL,
            dialog.directoryName,
            dialog.parentDirectory
        )
    }

    private fun String.refreshAndFindFileByUrl(): VirtualFile? =
        VirtualFileManager.getInstance().refreshAndFindFileByUrl("file:///$this")

    private class MyCheckoutListener(
        private val destinationFolder: VirtualFile,
        private val pluginName: String
    ): CheckoutProvider.Listener {

        /**
         * Copied from [com.intellij.openapi.vcs.checkout.CompositeCheckoutListener]
         */
        private fun refreshVFS(directory: File): VirtualFile {
            val result = Ref<VirtualFile>()
            runWriteAction {
                val lfs = LocalFileSystem.getInstance()
                val vDir = lfs.refreshAndFindFileByIoFile(directory)
                result.set(vDir)
                if (vDir != null) {
                    val watchRequest = lfs.addRootToWatch(vDir.path, true)
                    (vDir as NewVirtualFile).markDirtyRecursively()
                    vDir.refresh(false, true)
                    if (watchRequest != null) {
                        lfs.removeWatchedRoot(watchRequest)
                    }
                }
            }
            return result.get()
        }

        override fun directoryCheckedOut(directory: File, vcs: VcsKey) {
            refreshVFS(directory)
        }

        override fun checkoutCompleted() {
            val finishRunnable = Runnable {
                val clonedFolder = destinationFolder.findChild(pluginName)
                if (clonedFolder == null) {
                    logger.error("Couldn't find virtual file for checked out plugin: $pluginName")
                    return@Runnable
                }

                RefreshPluginsPanelAction.refreshPluginTree()
            }
            val pluginsRoot = LivePluginPaths.livePluginsPath.toVirtualFile() ?: return
            RefreshQueue.getInstance().refresh(false, true, finishRunnable, pluginsRoot)
        }
    }

    companion object {
        private val logger = Logger.getInstance(AddPluginFromGitAction::class.java)
    }
}
