package liveplugin.implementation.toolwindow.addplugin.git

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT
import com.intellij.openapi.actionSystem.PlatformDataKeys.VIRTUAL_FILE_ARRAY
import com.intellij.openapi.project.DumbAware
import liveplugin.implementation.LivePluginAppComponent.Companion.findParentPluginFolder
import liveplugin.implementation.common.MapDataContext
import liveplugin.implementation.common.selectedFiles

class SharePluginAsGistAction: AnAction("Share as Gist", "Share as plugin files as ag Gist", AllIcons.Vcs.Vendors.Github), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        // Lookup action at runtime because org.jetbrains.plugins.github.GithubCreateGistAction.GithubCreateGistAction() is protected.
        val delegate = ActionManager.getInstance().getAction("Github.Create.Gist")
        event.selectedFiles()
            .mapNotNullTo(LinkedHashSet()) { it.findParentPluginFolder() }
            .forEach { pluginFolder ->
                val allFiles = pluginFolder.allFiles().map { it.toVirtualFile() }.toList().toTypedArray()
                val dataContext = MapDataContext(mapOf(VIRTUAL_FILE_ARRAY.name to allFiles, PROJECT.name to event.project))
                delegate.actionPerformed(event.withDataContext(dataContext))
            }
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.selectedFiles().any { it.findParentPluginFolder() != null }
    }
}