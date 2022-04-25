package liveplugin.implementation.pluginrunner

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import liveplugin.implementation.common.FilePath
import liveplugin.implementation.common.Icons
import liveplugin.implementation.common.selectedFiles
import liveplugin.implementation.livePlugins
import liveplugin.implementation.toLivePlugins

class UnloadPluginAction: AnAction("Unload Plugin", "Unload selected plugins", Icons.unloadPluginIcon), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        unloadPlugins(event.selectedFiles())
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.hasPluginsToUnload()
    }

    companion object {
        @JvmStatic fun unloadPlugins(pluginFilePaths: List<FilePath>) {
            pluginFilePaths.toLivePlugins().forEach { Binding.lookup(it)?.dispose() }
        }
    }
}

fun AnActionEvent.hasPluginsToUnload() =
    livePlugins().any { Binding.lookup(it) != null }
