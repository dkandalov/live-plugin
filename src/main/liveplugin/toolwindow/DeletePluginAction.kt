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
import com.intellij.openapi.vfs.VirtualFile
import liveplugin.Icons
import liveplugin.IdeUtil
import liveplugin.LivePluginAppComponent.Companion.pluginFolder
import liveplugin.pluginrunner.UnloadPluginAction.Companion.unloadPlugins
import liveplugin.toFilePath
import liveplugin.toolwindow.util.delete
import java.io.IOException


internal class DeletePluginAction: AnAction("Delete Plugin", "Delete plugin", Icons.deletePluginIcon), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val files = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(event.dataContext)
        if (files.isNullOrEmpty()) return

        val pluginRoots = files.mapNotNull { it.pluginFolder() }.distinct()
        if (userDoesNotWantToRemovePlugins(pluginRoots, event.project)) return

        unloadPlugins(pluginRoots.map { it.toFilePath() })

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
        event.presentation.isEnabled =
            files != null && files.any { it.pluginFolder() != null }
    }

    companion object {
        private val logger = Logger.getInstance(DeletePluginAction::class.java)

        private fun userDoesNotWantToRemovePlugins(pluginRoots: Collection<VirtualFile>, project: Project?): Boolean {
            val pluginIds = pluginRoots.map { it.name }
            val message = when (pluginIds.size) {
                1    -> "Do you want to delete plugin ${pluginIds.first()}?"
                else -> "Do you want to delete plugins ${pluginIds.joinToString(", ")}?"
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
