package liveplugin

import com.intellij.openapi.application.PathManager
import liveplugin.common.toFilePath

object LivePluginPaths {
    val ideJarsPath = PathManager.getHomePath().toFilePath() + "lib"

    val livePluginPath = PathManager.getPluginsPath().toFilePath() + "LivePlugin"
    val livePluginLibPath = PathManager.getPluginsPath().toFilePath() + "LivePlugin/lib"
    val livePluginsCompiledPath = PathManager.getPluginsPath().toFilePath() + "live-plugins-compiled"
    @JvmField val livePluginsPath = PathManager.getPluginsPath().toFilePath() + "live-plugins"
    val livePluginsProjectDirName = ".live-plugins"

    const val groovyExamplesPath = "groovy/"
    const val kotlinExamplesPath = "kotlin/"
}