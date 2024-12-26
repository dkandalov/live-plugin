package liveplugin.implementation

import com.intellij.ide.impl.isTrusted
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.project.modules
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.findFileOrDirectory
import kotlinx.coroutines.runBlocking
import liveplugin.implementation.LivePluginPaths.livePluginsProjectDirName
import liveplugin.implementation.common.MapDataContext
import liveplugin.implementation.common.livePluginNotificationGroup
import liveplugin.implementation.common.toFilePath
import liveplugin.implementation.pluginrunner.PluginRunner.Companion.runPlugins
import liveplugin.implementation.pluginrunner.PluginRunner.Companion.unloadPlugins

class LivePluginProjectPostStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (!Settings.instance.runProjectSpecificPlugins) return
        @Suppress("UnstableApiUsage")
        if (!project.isTrusted()) {
            val message = "Skipped execution of project specific plugins because the project is not trusted."
            livePluginNotificationGroup.createNotification(title = "Live plugin", message, INFORMATION).notify(project)
            return
        }

        val dataContext = MapDataContext(mapOf(CommonDataKeys.PROJECT.name to project))
        val dummyEvent = AnActionEvent.createEvent(dataContext, null, "", ActionUiKind.NONE, null)
        runPlugins(findLivePluginsIn(project), dummyEvent)
    }
}

class LivePluginProjectListener : ProjectManagerListener {
    override fun projectClosing(project: Project) = runBlocking {
        unloadPlugins(findLivePluginsIn(project))
    }
}

private fun findLivePluginsIn(project: Project) =
    project.modules
        .flatMap { it.rootManager.contentRoots.toList() }
        .flatMap { root ->
            ApplicationManager.getApplication().runReadAction(Computable {
                root.findFileOrDirectory(livePluginsProjectDirName)
                    ?.toFilePath()?.listFiles()?.toLivePlugins() ?: emptyList()
            })
        }
