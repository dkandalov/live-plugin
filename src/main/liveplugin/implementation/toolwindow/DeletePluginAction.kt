package liveplugin.implementation.toolwindow

import com.intellij.CommonBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import liveplugin.implementation.LivePlugin
import liveplugin.implementation.common.Icons.deletePluginIcon
import liveplugin.implementation.common.IdeUtil
import liveplugin.implementation.livePlugins
import liveplugin.implementation.toolwindow.util.delete
import java.io.IOException


class DeletePluginAction: AnAction("Delete Plugin", "Delete plugin", deletePluginIcon), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val livePlugins = event.livePlugins().ifEmpty { return }
        if (userDoesNotWantToRemovePlugins(livePlugins, event.project)) return

        livePlugins.forEach { plugin ->
            try {
                plugin.path.toVirtualFile()?.delete()
            } catch (e: IOException) {
                val project = event.project
                if (project != null) {
                    IdeUtil.showErrorDialog(project, "Error deleting plugin \"${plugin.path}\"", "Delete Plugin")
                }
                logger.error(e)
            }
        }

        RefreshPluginsPanelAction.refreshPluginTree()
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.livePlugins().isNotEmpty()
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
