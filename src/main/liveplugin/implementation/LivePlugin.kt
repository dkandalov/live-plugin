package liveplugin.implementation

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import liveplugin.implementation.LivePluginPaths.livePluginsPath
import liveplugin.implementation.LivePluginPaths.livePluginsProjectDirName
import liveplugin.implementation.common.FilePath
import liveplugin.implementation.common.selectedFiles
import liveplugin.implementation.common.toFilePath

data class LivePlugin(val path: FilePath) {
    val id: String = path.name

    companion object {
        @JvmStatic fun livePluginsById(): Map<String, LivePlugin> =
            livePluginsPath.listFiles { it.isDirectory && it.name != Project.DIRECTORY_STORE_FOLDER }
                .map { LivePlugin(it) }
                .associateBy { it.id }
    }
}

fun AnActionEvent.livePlugins(): List<LivePlugin> =
    selectedFiles().toLivePlugins()

fun List<FilePath>.toLivePlugins() =
    mapNotNullTo(LinkedHashSet()) { it.findParentPluginFolder() }.map { LivePlugin(it) }

private tailrec fun FilePath.findParentPluginFolder(): FilePath? =
    if (isPluginFolder()) this else parent?.findParentPluginFolder()

fun FilePath.isPartOfPlugin(): Boolean =
    findParentPluginFolder() != null

fun FilePath.isPluginFolder(): Boolean {
    if (exists() && !isDirectory) return false
    val parent = parent ?: return false
    return parent == livePluginsPath ||
        parent.name == livePluginsProjectDirName ||
        name == livePluginsProjectDirName
}

// Same implementation as above but for VirtualFile to have better performance
// (See https://github.com/dkandalov/live-plugin/issues/184)
fun VirtualFile.isPluginFolder(): Boolean {
    if (!exists() && !isDirectory) return false
    val parent = parent ?: return false
    return parent.path == livePluginsPath.value ||
        parent.name == livePluginsProjectDirName ||
        name == livePluginsProjectDirName
}

object LivePluginPaths {
    val ideJarsPath = PathManager.getHomePath().toFilePath() + "lib"

    val livePluginPath = PathManager.getPluginsPath().toFilePath() + "LivePlugin"
    val livePluginLibPath = PathManager.getPluginsPath().toFilePath() + "LivePlugin/lib"

    // Use scratches location because it's more standard for keeping scripts, e.g. from IDE console.
    val livePluginsCompiledPath = PathManager.getScratchPath().toFilePath() + "live-plugins-compiled"
    @JvmField val livePluginsPath = PathManager.getScratchPath().toFilePath() + "live-plugins"
    val oldLivePluginsCompiledPath = PathManager.getPluginsPath().toFilePath() + "live-plugins-compiled"
    val oldLivePluginsPath = PathManager.getPluginsPath().toFilePath() + "live-plugins"
    val livePluginsProjectDirName = ".live-plugins"

    const val groovyExamplesPath = "groovy/"
    const val kotlinExamplesPath = "kotlin/"
}