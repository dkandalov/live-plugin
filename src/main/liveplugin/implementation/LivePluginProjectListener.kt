package liveplugin.implementation

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import kotlinx.coroutines.runBlocking
import liveplugin.implementation.pluginrunner.PluginRunner.Companion.unloadPlugins

class LivePluginProjectListener : ProjectManagerListener {
    override fun projectClosing(project: Project) = runBlocking {
        unloadPlugins(readLivePlugins(project))
    }
}
