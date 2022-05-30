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
import liveplugin.implementation.pluginrunner.PluginRunner.Companion.runPlugins
import liveplugin.implementation.pluginrunner.PluginRunner.Companion.unloadPlugins

class LivePluginProjectListener : ProjectManagerListener {
    override fun projectOpened(project: Project) {
        if (!Settings.instance.runProjectSpecificPlugins) return
        @Suppress("UnstableApiUsage")
        if (!project.isTrusted()) {
            val message = "Skipped execution of project specific plugins because the project is not trusted."
            livePluginNotificationGroup.createNotification(title = "Live plugin", message, INFORMATION).notify(project)
            return
        }

        val dataContext = MapDataContext(mapOf(CommonDataKeys.PROJECT.name to project))
        val dummyEvent = AnActionEvent(null, dataContext, "", Presentation(), ActionManager.getInstance(), 0)
        runPlugins(livePluginsIn(project), dummyEvent)
    }

    override fun projectClosing(project: Project) {
        unloadPlugins(livePluginsIn(project))
    }

    private fun livePluginsIn(project: Project): List<LivePlugin> {
        val projectPath = project.basePath?.toFilePath() ?: return emptyList()
        return (projectPath + livePluginsProjectDirName).listFiles().toLivePlugins()
    }
}