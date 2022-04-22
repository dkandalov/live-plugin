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
import liveplugin.common.Icons
import liveplugin.common.IdeUtil
import liveplugin.LivePluginAppComponent.Companion.pluginFolder
import liveplugin.LivePluginPaths
import liveplugin.pluginrunner.LivePlugin
import liveplugin.pluginrunner.UnloadPluginAction.Companion.unloadPlugins
import liveplugin.pluginrunner.selectedFilePaths
import liveplugin.pluginrunner.toLivePlugins
import liveplugin.toolwindow.util.delete
import java.io.IOException


class DeletePluginAction: AnAction("Delete Plugin", "Delete plugin", Icons.deletePluginIcon), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val livePlugins = event.selectedFilePaths().toLivePlugins().ifEmpty { return }
        if (userDoesNotWantToRemovePlugins(livePlugins, event.project)) return

        unloadPlugins(livePlugins.map { it.path })

        livePlugins.forEach { plugin ->
            try {
                plugin.path.toVirtualFile()?.delete()
                (LivePluginPaths.livePluginsCompiledPath + plugin.id).toVirtualFile()?.delete()
            } catch (e: IOException) {
                val project = event.project
                if (project != null) {
                    IdeUtil.showErrorDialog(project, "Error deleting plugin \"" + plugin.path, "Delete Plugin")
                }
                logger.error(e)
            }
        }

        RefreshPluginsPanelAction.refreshPluginTree()
    }

    override fun update(event: AnActionEvent) {
        val files = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(event.dataContext)
        event.presentation.isEnabled = files != null && files.any { it.pluginFolder() != null }
    }

    companion object {
        private val logger = Logger.getInstance(DeletePluginAction::class.java)

        private fun userDoesNotWantToRemovePlugins(plugins: List<LivePlugin>, project: Project?): Boolean {
            val pluginIds = plugins.map { it.id }
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
