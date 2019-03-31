package liveplugin.toolwindow.addplugin.git

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
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
import icons.GithubIcons
import liveplugin.IdeUtil
import liveplugin.LivePluginAppComponent.Companion.isInvalidPluginFolder
import liveplugin.LivePluginAppComponent.Companion.livePluginsPath
import liveplugin.toolwindow.RefreshPluginsPanelAction
import liveplugin.toolwindow.util.delete
import java.io.File


/**
 * Partially copied from org.jetbrains.plugins.github.GithubCheckoutProvider (became com.intellij.dvcs.ui.CloneDvcsDialog in IJ13)
 */
class AddPluginFromGitAction: AnAction("Clone from Git", "Clone from Git", GithubIcons.Github_icon), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return

        val dialog = GitCloneDialog(project)
        dialog.show()
        if (!dialog.isOK) return
        dialog.rememberSettings()

        val destinationFolder = LocalFileSystem.getInstance().findFileByIoFile(File(dialog.parentDirectory)) ?: return

        GitCheckoutProvider.clone(
            project,
            ServiceManager.getService(Git::class.java),
            MyCheckoutListener(project, destinationFolder, dialog.directoryName),
            destinationFolder,
            dialog.sourceRepositoryURL,
            dialog.directoryName,
            dialog.parentDirectory
        )
    }

    private class MyCheckoutListener(
        private val project: Project?,
        private val destinationFolder: VirtualFile,
        private val pluginName: String
    ): CheckoutProvider.Listener {

        /**
         * Copied from [com.intellij.openapi.vcs.checkout.CompositeCheckoutListener]
         */
        private fun refreshVFS(directory: File): VirtualFile {
            val result = Ref<VirtualFile>()
            ApplicationManager.getApplication().runWriteAction {
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
            val pluginsRoot = VirtualFileManager.getInstance().findFileByUrl("file://" + livePluginsPath) ?: return

            val finishRunnable = Runnable {
                val clonedFolder = destinationFolder.findChild(pluginName)
                if (clonedFolder == null) {
                    logger.error("Couldn't find virtual file for checked out plugin: " + pluginName)
                    return@Runnable
                }

                try {
                    if (isInvalidPluginFolder(clonedFolder) && userDoesNotWantToKeepIt()) {
                        delete(clonedFolder.path)
                    }
                } catch (e: Exception) {
                    if (project != null) {
                        IdeUtil.showErrorDialog(project, "Error deleting plugin \"${clonedFolder.path}\"", "Delete Plugin")
                    }
                    logger.error(e)
                }

                RefreshPluginsPanelAction.refreshPluginTree()
            }
            RefreshQueue.getInstance().refresh(false, true, finishRunnable, pluginsRoot)
        }

        private fun userDoesNotWantToKeepIt(): Boolean {
            val answer = Messages.showYesNoDialog(
                project,
                "It looks like \"$pluginName\" is not a valid plugin because it does not contain plugin scripts.\n\nDo you want to add it anyway?",
                dialogTitle,
                Messages.getQuestionIcon()
            )
            return answer != Messages.YES
        }
    }

    companion object {
        private val logger = Logger.getInstance(AddPluginFromGitAction::class.java)
        private val dialogTitle = "Clone Plugin From Git"
    }
}
