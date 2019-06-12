package liveplugin.toolwindow

import com.intellij.CommonBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import liveplugin.IdeUtil
import liveplugin.Icons
import liveplugin.toolwindow.PluginToolWindowManager.Companion.findPluginRootsFor
import liveplugin.toolwindow.util.delete
import java.io.IOException


internal class DeletePluginAction: AnAction("Delete Plugin", "Delete Plugin", Icons.deletePluginIcon), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val files = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(event.dataContext)
        if (files == null || files.isEmpty()) return

        val pluginRoots = findPluginRootsFor(files)
        if (userDoesNotWantToRemovePlugins(pluginRoots, event.project)) return

        pluginRoots.forEach { pluginRoot ->
            try {
                delete(pluginRoot.path)
            } catch (e: IOException) {
                val project = event.project
                if (project != null) {
                    IdeUtil.showErrorDialog(project, "Error deleting plugin \"" + pluginRoot.path, "Delete Plugin")
                }
                logger.error(e)
            }
        }

        RefreshPluginsPanelAction.refreshPluginTree()
    }

    override fun update(event: AnActionEvent) {
        val files = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(event.dataContext)

        val enabled =
            if (files == null || files.isEmpty()) false
            else findPluginRootsFor(files).isNotEmpty()

        event.presentation.isEnabled = enabled
    }

    companion object {
        private val logger = Logger.getInstance(DeletePluginAction::class.java)

        private fun userDoesNotWantToRemovePlugins(pluginRoots: Collection<VirtualFile>, project: Project?): Boolean {
            val pluginIds = pluginRoots.map { it.name }

            val message = when {
                pluginIds.size == 1 -> "Do you want to delete plugin \"" + pluginIds[0] + "\"?"
                pluginIds.size == 2 -> "Do you want to delete plugin \"" + pluginIds[0] + "\" and \"" + pluginIds[1] + "\"?"
                else                -> "Do you want to delete plugins \"" + StringUtil.join(pluginIds, ", ") + "\"?"
            }
            val answer = Messages.showOkCancelDialog(
                project,
                message,
                "Delete",
                ApplicationBundle.message("button.delete"),
                CommonBundle.getCancelButtonText(),
                Messages.getQuestionIcon()
            )
            return answer != Messages.OK
        }
    }
}
