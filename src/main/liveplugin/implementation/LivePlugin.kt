package liveplugin.implementation

import com.intellij.openapi.actionSystem.AnActionEvent
import liveplugin.implementation.LivePluginAppComponent.Companion.findParentPluginFolder
import liveplugin.implementation.common.FilePath
import liveplugin.implementation.common.selectedFiles
import liveplugin.implementation.pluginrunner.PluginRunner

data class LivePlugin(val path: FilePath) {
    val id: String = path.toFile().name
}

fun AnActionEvent.livePlugins(): List<LivePlugin> =
    selectedFiles().toLivePlugins()

fun List<FilePath>.toLivePlugins() =
    mapNotNull { it.findParentPluginFolder() }.distinct().map { LivePlugin(it) }

fun List<LivePlugin>.canBeHandledBy(pluginRunners: List<PluginRunner>): Boolean =
    any { livePlugin ->
        pluginRunners.any { runner ->
            livePlugin.path.allFiles().any { it.name == runner.scriptName }
        }
    }