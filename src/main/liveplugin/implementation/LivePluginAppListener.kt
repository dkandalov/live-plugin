package liveplugin.implementation

import com.intellij.ide.AppLifecycleListener
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType.ERROR
import com.intellij.notification.NotificationType.WARNING
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.OK
import com.intellij.openapi.ui.Messages.showOkCancelDialog
import com.intellij.openapi.util.io.FileUtil.moveDirWithContent
import com.intellij.util.download.DownloadableFileService
import liveplugin.implementation.GroovyDownloader.downloadGroovyJar
import liveplugin.implementation.GroovyDownloader.isGroovyOnClasspath
import liveplugin.implementation.LivePluginPaths.livePluginLibPath
import liveplugin.implementation.LivePluginPaths.livePluginsCompiledPath
import liveplugin.implementation.LivePluginPaths.livePluginsPath
import liveplugin.implementation.LivePluginPaths.oldLivePluginsCompiledPath
import liveplugin.implementation.LivePluginPaths.oldLivePluginsPath
import liveplugin.implementation.common.FilePath
import liveplugin.implementation.common.IdeUtil.ideStartupActionPlace
import liveplugin.implementation.common.IdeUtil.logger
import liveplugin.implementation.common.IdeUtil.runLaterOnEdt
import liveplugin.implementation.common.livePluginNotificationGroup
import liveplugin.implementation.pluginrunner.PluginRunner.Companion.runPlugins
import java.util.concurrent.CompletableFuture

class LivePluginAppListener : AppLifecycleListener {
    @Suppress("UnstableApiUsage")
    override fun appStarted() {
        // Download Groovy in non-java IDEs because they don't have bundled Groovy libs.
        if (!isGroovyOnClasspath()) downloadGroovyJar()

        val settings = Settings.instance
        if (!settings.migratedLivePluginsToScratchesPath) {
            val migrated = migrateLivePluginsToScratchesPath()
            if (migrated) settings.migratedLivePluginsToScratchesPath = true
        }
        if (settings.justInstalled) {
            installLivepluginTutorialExamples()
            settings.justInstalled = false
        }
        if (settings.runAllPluginsOnIDEStartup) {
            runAllPlugins()
        }
    }

    private fun migrateLivePluginsToScratchesPath() =
        try {
            val wasMoved = moveDirWithContent(oldLivePluginsPath.toFile(), livePluginsPath.toFile())

            livePluginsCompiledPath.toFile().mkdirs()
            // Delete because class files because liveplugin classes were moved to "implementation" package.
            oldLivePluginsCompiledPath.toFile().deleteRecursively()
            wasMoved
        } catch (e: Exception) {
            logger.warn("Failed to migrate plugins", e)
            livePluginNotificationGroup.createNotification(
                title = "Live plugin",
                "Failed to migrate plugins from '${oldLivePluginsPath.toFile().absolutePath} 'to '${livePluginsPath.toFile().absolutePath}'",
                WARNING
            ).notify(null)
            false
        }

    private fun runAllPlugins() {
        runLaterOnEdt {
            val event = AnActionEvent.createEvent(EMPTY_CONTEXT, Presentation(), ideStartupActionPlace, ActionUiKind.NONE, null)
            runPlugins(LivePlugin.livePluginsById().values, event)
        }
    }
}

private object GroovyDownloader {
    fun isGroovyOnClasspath() =
        LivePluginAppListener::class.java.classLoader.getResource(
            "org.codehaus.groovy.runtime.DefaultGroovyMethods".replace(".", "/") + ".class"
        ) != null

    fun downloadGroovyJar() {
        livePluginNotificationGroup.createNotification(
            title = "LivePlugin didn't find Groovy libraries on classpath",
            content = "Without it plugins won't work. <a href=\"\">Download Groovy libraries</a> (~8Mb)",
            type = ERROR
        ).addAction(object : NotificationAction("Download Groovy libraries") {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                val groovyVersion = "3.0.9" // Version of Groovy jar in the latest IJ.

                downloadFiles(
                    url = "https://repo1.maven.org/maven2/org/codehaus/groovy/groovy/$groovyVersion/",
                    fileName = "groovy-$groovyVersion.jar",
                    targetPath = livePluginLibPath
                ).whenComplete { downloaded, _ ->
                    if (downloaded) {
                        notification.expire()
                        val answer = showOkCancelDialog(
                            "LivePlugin needs to restart IDE to load Groovy libraries. Restart now?",
                            "IDE Restart",
                            "Restart",
                            "Postpone",
                            Messages.getQuestionIcon()
                        )
                        if (answer == OK) {
                            ApplicationManagerEx.getApplicationEx().restart(true)
                        }
                    } else {
                        livePluginNotificationGroup
                            .createNotification("Failed to download Groovy libraries", WARNING)
                    }
                }
            }
        }).notify(null)
    }

    private fun downloadFiles(url: String, fileName: String, targetPath: FilePath): CompletableFuture<Boolean> {
        val service = DownloadableFileService.getInstance()
        val description = service.createFileDescription(url + fileName, fileName)
        return service.createDownloader(listOf(description), "")
            .downloadWithBackgroundProgress(targetPath.value, null)
            .thenApply { it?.size == 1 }
    }
}
