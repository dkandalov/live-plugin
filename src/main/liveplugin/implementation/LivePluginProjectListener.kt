package liveplugin.implementation

import com.intellij.ide.impl.isTrusted
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import liveplugin.implementation.LivePluginPaths.livePluginsProjectDirName
import liveplugin.implementation.common.MapDataContext
import liveplugin.implementation.common.livePluginNotificationGroup
import liveplugin.implementation.common.toFilePath
import liveplugin.implementation.pluginrunner.RunPluginAction
import liveplugin.implementation.pluginrunner.UnloadPluginAction

class LivePluginProjectListener : ProjectManagerListener {
    override fun projectOpened(project: Project) {
        if (!Settings.instance.runProjectSpecificPlugins) return
        @Suppress("UnstableApiUsage")
        if (!project.isTrusted()) {
            val message = "Skipped execution of project specific plugins because the project is not trusted."
            livePluginNotificationGroup.createNotification(title = "Live plugin", message, INFORMATION).notify(project)
            return
        }

        val projectPath = project.basePath?.toFilePath() ?: return
        val pluginsPath = projectPath + livePluginsProjectDirName
        if (!pluginsPath.exists()) return

        val dataContext = MapDataContext(mapOf(CommonDataKeys.PROJECT.name to project))
        val dummyEvent = AnActionEvent(null, dataContext, "", Presentation(), ActionManager.getInstance(), 0)

        RunPluginAction.runPlugins(pluginsPath.listFiles().toLivePlugins(), dummyEvent)
    }

    override fun projectClosing(project: Project) {
        val projectPath = project.basePath?.toFilePath() ?: return
        val pluginsPath = projectPath + livePluginsProjectDirName
        if (!pluginsPath.exists()) return

        UnloadPluginAction.unloadPlugins(pluginsPath.listFiles().toLivePlugins())
    }
}