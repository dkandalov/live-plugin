package liveplugin.implementation

import com.intellij.openapi.application.PathManager
import liveplugin.implementation.common.toFilePath

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