package liveplugin.pluginrunner

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import liveplugin.FilePath
import liveplugin.Icons
import liveplugin.LivePluginAppComponent.Companion.findPluginFolder

class UnloadPluginAction: AnAction("Unload Plugin", "Unload selected plugins", Icons.unloadPluginIcon), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        unloadPlugins(event.selectedFilePaths())
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.selectedFilePaths().any { it.toBinding() != null }
    }

    companion object {
        @JvmStatic fun unloadPlugins(pluginFilePaths: List<FilePath>) {
            pluginFilePaths.forEach { it.toBinding()?.dispose() }
        }

        private fun FilePath.toBinding(): Binding? {
            val pluginFolder = findPluginFolder() ?: return null
            return Binding.lookup(LivePlugin(pluginFolder))
        }
    }
}