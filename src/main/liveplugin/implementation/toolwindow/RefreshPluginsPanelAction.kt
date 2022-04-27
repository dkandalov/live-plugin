package liveplugin.implementation.toolwindow

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import liveplugin.implementation.LivePluginPaths.livePluginsPath
import liveplugin.implementation.common.Icons.refreshPluginsPanelIcon

class RefreshPluginsPanelAction: AnAction(
    "Refresh Plugins Panel",
    "Refresh plugins panel",
    refreshPluginsPanelIcon
), DumbAware {
    override fun actionPerformed(e: AnActionEvent) = refreshPluginTree()

    companion object {
        fun refreshPluginTree() {
            val pluginsRoot = livePluginsPath.toVirtualFile() ?: return
            runWriteAction {
                val finishRunnable = { LivePluginToolWindowFactory.reloadPluginTreesInAllProjects() }
                RefreshQueue.getInstance().refresh(false, true, finishRunnable, pluginsRoot)
            }
        }
    }
}
