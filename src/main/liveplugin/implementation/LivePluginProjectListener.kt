package liveplugin.implementation

import com.intellij.ide.impl.isTrusted
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Key
import liveplugin.implementation.LivePluginPaths.livePluginsProjectDirName
import liveplugin.implementation.actions.RunPluginAction
import liveplugin.implementation.actions.UnloadPluginAction
import liveplugin.implementation.command.LiveCommandService
import liveplugin.implementation.command.impl.LiveCommandServiceImpl
import liveplugin.implementation.common.MapDataContext
import liveplugin.implementation.common.livePluginNotificationGroup
import liveplugin.implementation.common.toFilePath
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarFile

class LivePluginProjectListener : ProjectManagerListener {

    companion object {
        val SPP_COMMANDS_LOCATION = Key.create<File>("SPP_COMMANDS_LOCATION")
    }

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

        project.putUserData(LiveCommandService.KEY, LiveCommandServiceImpl(project))
        project.putUserData(LiveCommandService.LIVE_COMMAND_LOADER) {
            val sppCommandsLocation = extractSppCommands()
            RunPluginAction.runPlugins(sppCommandsLocation.toFilePath().listFiles().toLivePlugins(), dummyEvent)
            project.putUserData(SPP_COMMANDS_LOCATION, sppCommandsLocation)

            val projectPath = project.basePath?.toFilePath() ?: return@putUserData
            val pluginsPath = projectPath + livePluginsProjectDirName
            if (!pluginsPath.exists()) return@putUserData

            RunPluginAction.runPlugins(pluginsPath.listFiles().toLivePlugins(), dummyEvent)
        }
    }

    override fun projectClosing(project: Project) {
        project.getUserData(SPP_COMMANDS_LOCATION)?.let {
            UnloadPluginAction.unloadPlugins(it.toFilePath().listFiles().toLivePlugins())
            it.deleteRecursively()
        }

        val projectPath = project.basePath?.toFilePath() ?: return
        val pluginsPath = projectPath + livePluginsProjectDirName
        if (!pluginsPath.exists()) return

        UnloadPluginAction.unloadPlugins(pluginsPath.listFiles().toLivePlugins())
    }

    private fun extractSppCommands(): File {
        val tmpDir = Files.createTempDirectory("spp-commands").toFile()
        tmpDir.deleteOnExit()
        val destDir = tmpDir.absolutePath

        val jar = JarFile(File(PathManager.getJarPathForClass(LivePluginProjectListener::class.java)))
        val enumEntries: Enumeration<*> = jar.entries()
        while (enumEntries.hasMoreElements()) {
            val file = enumEntries.nextElement() as JarEntry
            if (!file.name.startsWith(".spp")) continue

            val f = File(destDir + File.separator + file.name)
            if (file.isDirectory) {
                f.mkdir()
                continue
            }

            val inputStream = jar.getInputStream(file)
            val fos = FileOutputStream(f)
            while (inputStream.available() > 0) {
                fos.write(inputStream.read())
            }
            fos.close()
            inputStream.close()
        }
        jar.close()

        return File(destDir, ".spp/commands")
    }
}
