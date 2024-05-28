package liveplugin.implementation

import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.vfs.findFileOrDirectory
import liveplugin.implementation.LivePluginPaths.livePluginsProjectDirName
import liveplugin.implementation.common.toFilePath

suspend fun readLivePlugins(project: Project): List<LivePlugin> {
    return project.modules.flatMap { module ->
        readLivePlugins(module)
    }
}

suspend fun readLivePlugins(module: Module): List<LivePlugin> {
    return module.rootManager.contentRoots
        .mapNotNull { root ->
            readAction {
                root.findFileOrDirectory(livePluginsProjectDirName)?.takeIf { it.isDirectory }
            }
        }
        .flatMap {
            it.toFilePath().listFiles().toLivePlugins()
        }
}
