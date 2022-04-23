package liveplugin.toolwindow.addplugin.git

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT
import com.intellij.openapi.actionSystem.PlatformDataKeys.VIRTUAL_FILE_ARRAY
import com.intellij.openapi.project.DumbAware
import liveplugin.LivePluginAppComponent.Companion.findPluginFolder
import liveplugin.common.MapDataContext
import liveplugin.common.toFilePath

class SharePluginAsGistAction: AnAction("Share as Gist", "Share as plugin files as ag Gist", AllIcons.Vcs.Vendors.Github), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        // Lookup action at runtime because org.jetbrains.plugins.github.GithubCreateGistAction.GithubCreateGistAction() is protected.
        val delegate = ActionManager.getInstance().getAction("Github.Create.Gist")
        val files = VIRTUAL_FILE_ARRAY.getData(event.dataContext) ?: return
        val project = event.project
        files.mapNotNullTo(HashSet()) { it.findPluginFolder() }
            .forEach { pluginFolder ->
                val allFiles = pluginFolder.toFilePath().allFiles().map { it.toVirtualFile() }.toList().toTypedArray()
                delegate.actionPerformed(event.withDataContext(
                    MapDataContext(mapOf(
                    VIRTUAL_FILE_ARRAY.name to allFiles,
                    PROJECT.name to project,
                ))
                ))
            }
    }

    override fun update(event: AnActionEvent) {
        val files = VIRTUAL_FILE_ARRAY.getData(event.dataContext)
        event.presentation.isEnabled = files != null && files.any { it.findPluginFolder() != null }
    }
}