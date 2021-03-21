package liveplugin.pluginrunner

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import liveplugin.FilePath
import liveplugin.Icons

class UnloadPluginAction: AnAction("Unload Plugin", "Unload selected plugins", Icons.unloadPluginIcon), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        event.selectedFilePaths()
            .forEach { it.toBinding()?.dispose() }
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.selectedFilePaths().any { it.toBinding() != null }
    }

    private fun FilePath.toBinding(): Binding? {
        val filePath = findPluginFolder(this) ?: return null
        return Binding.lookup(LivePlugin(filePath))
    }
}