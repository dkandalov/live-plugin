package liveplugin.implementation

import com.intellij.openapi.actionSystem.AnActionEvent
import liveplugin.implementation.LivePluginAppComponent.Companion.isPluginFolder
import liveplugin.implementation.common.FilePath
import liveplugin.implementation.common.selectedFiles

data class LivePlugin(val path: FilePath) {
    val id: String = path.toFile().name
}

fun AnActionEvent.livePlugins(): List<LivePlugin> =
    selectedFiles().toLivePlugins()

fun List<FilePath>.toLivePlugins() =
    mapNotNullTo(LinkedHashSet()) { it.findParentPluginFolder() }.map { LivePlugin(it) }

private tailrec fun FilePath.findParentPluginFolder(): FilePath? =
    if (isPluginFolder()) this else parent?.findParentPluginFolder()
