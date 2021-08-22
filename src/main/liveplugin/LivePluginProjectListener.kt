package liveplugin

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import liveplugin.pluginrunner.RunPluginAction
import liveplugin.pluginrunner.UnloadPluginAction

class LivePluginProjectListener : ProjectManagerListener {
    override fun projectOpened(project: Project) {
        if (!Settings.instance.runProjectSpecificPlugins) return

        val projectPath = project.basePath?.toFilePath() ?: return
        val pluginsPath = projectPath + LivePluginPaths.livePluginsProjectDirName
        if (!pluginsPath.exists()) return

        val dataContext = MapDataContext(mapOf(CommonDataKeys.PROJECT.name to project))
        val dummyEvent = AnActionEvent(null, dataContext, "", Presentation(), ActionManager.getInstance(), 0)

        RunPluginAction.runPlugins(pluginsPath.listFiles(), dummyEvent)
    }

    override fun projectClosing(project: Project) {
        val projectPath = project.basePath?.toFilePath() ?: return
        val pluginsPath = projectPath + LivePluginPaths.livePluginsProjectDirName
        if (!pluginsPath.exists()) return

        UnloadPluginAction.unloadPlugins(pluginsPath.listFiles())
    }
}