package liveplugin.toolwindow

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import liveplugin.Icons
import liveplugin.LivePluginPaths

class RefreshPluginsPanelAction: AnAction(
    "Refresh Plugins Panel",
    "Refresh plugins panel",
    Icons.refreshPluginsPanelIcon
), DumbAware {
    override fun actionPerformed(e: AnActionEvent) = refreshPluginTree()

    companion object {
        fun refreshPluginTree() {
            val pluginsRoot = LivePluginPaths.livePluginsPath.toVirtualFile() ?: return
            ApplicationManager.getApplication().runWriteAction {
                val finishRunnable = { LivePluginToolWindowFactory.reloadPluginTreesInAllProjects() }
                RefreshQueue.getInstance().refresh(false, true, finishRunnable, pluginsRoot)
            }
        }
    }
}
