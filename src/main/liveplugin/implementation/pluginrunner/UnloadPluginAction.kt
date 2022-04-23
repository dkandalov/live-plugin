package liveplugin.implementation.pluginrunner

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import liveplugin.implementation.common.FilePath
import liveplugin.implementation.common.Icons
import liveplugin.implementation.LivePluginAppComponent.Companion.findParentPluginFolder
import liveplugin.implementation.common.selectedFiles
import liveplugin.pluginrunner.Binding
import liveplugin.pluginrunner.LivePlugin

class UnloadPluginAction: AnAction("Unload Plugin", "Unload selected plugins", Icons.unloadPluginIcon), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        unloadPlugins(event.selectedFiles())
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.hasPluginsToUnload()
    }

    companion object {
        @JvmStatic fun unloadPlugins(pluginFilePaths: List<FilePath>) {
            pluginFilePaths.forEach { it.toBinding()?.dispose() }
        }
    }
}

fun AnActionEvent.hasPluginsToUnload() =
    selectedFiles().any { it.toBinding() != null }

private fun FilePath.toBinding(): Binding? {
    val pluginFolder = findParentPluginFolder() ?: return null
    return Binding.lookup(LivePlugin(pluginFolder))
}
