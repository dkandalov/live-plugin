package liveplugin.implementation

import com.intellij.openapi.project.Project
import liveplugin.implementation.common.toFilePath

fun readLivePlugins(project: Project): List<LivePlugin> {
    val projectPath = project.basePath?.toFilePath() ?: return emptyList()
    return (projectPath + LivePluginPaths.livePluginsProjectDirName).listFiles().toLivePlugins()
}
