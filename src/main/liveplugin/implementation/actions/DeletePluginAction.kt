package liveplugin.implementation.actions

import com.intellij.CommonBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import liveplugin.implementation.LivePlugin
import liveplugin.implementation.common.Icons.deletePluginIcon
import liveplugin.implementation.common.IdeUtil.showError
import liveplugin.implementation.common.delete
import liveplugin.implementation.livePlugins
import java.io.IOException


class DeletePluginAction: AnAction("Delete Plugin", "Delete plugin", deletePluginIcon), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        val livePlugins = event.livePlugins().ifEmpty { return }
        if (userDoesNotWantToRemovePlugins(livePlugins, event.project)) return

        livePlugins.forEach { plugin ->
            try {
                plugin.path.toVirtualFile()?.delete()
            } catch (e: IOException) {
                event.project.showError("Error deleting plugin \"${plugin.path}\": ${e.message}", e)
            }
        }
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.livePlugins().isNotEmpty()
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    companion object {
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
