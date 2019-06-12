package liveplugin.toolwindow

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import liveplugin.Icons
import liveplugin.LivePluginAppComponent.Companion.livePluginsPath
import liveplugin.findFileByUrl

class RefreshPluginsPanelAction: AnAction(
    "Refresh Plugins Panel",
    "Refresh Plugins Panel",
    Icons.refreshPluginsPanelIcon
), DumbAware {
    override fun actionPerformed(e: AnActionEvent) = refreshPluginTree()

    companion object {
        fun refreshPluginTree() {
            val pluginsRoot = livePluginsPath.findFileByUrl() ?: return
            ApplicationManager.getApplication().runWriteAction {
                RefreshQueue.getInstance().refresh(
                    false,
                    true,
                    Runnable { PluginToolWindowManager.reloadPluginTreesInAllProjects() },
                    pluginsRoot
                )
            }
        }
    }
}
