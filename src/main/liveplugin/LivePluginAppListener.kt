package liveplugin

import com.intellij.ide.AppLifecycleListener
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationType.ERROR
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.OK
import com.intellij.util.download.DownloadableFileService
import liveplugin.GroovyDownloader.downloadGroovyJar
import liveplugin.GroovyDownloader.isGroovyOnClasspath
import liveplugin.IdeUtil.invokeLaterOnEDT
import liveplugin.LivePluginAppComponent.Companion.livePluginNotificationGroup
import liveplugin.LivePluginPaths.livePluginLibPath
import liveplugin.toolwindow.util.GroovyExamples
import liveplugin.toolwindow.util.installPlugin
import java.util.concurrent.CompletableFuture

class LivePluginAppListener: AppLifecycleListener {
    @Suppress("UnstableApiUsage")
    override fun appStarted() {
        // Download Groovy in non-java IDEs because they don't have bundled Groovy libs.
        if (!isGroovyOnClasspath()) downloadGroovyJar()

        val settings = Settings.instance
        if (settings.justInstalled) {
            installHelloWorldPlugins()
            settings.justInstalled = false
        }
        if (settings.runAllPluginsOnIDEStartup) {
            LivePluginAppComponent.runAllPlugins()
        }
    }

    private fun installHelloWorldPlugins() {
        invokeLaterOnEDT {
            listOf(GroovyExamples.helloWorld, GroovyExamples.ideActions, GroovyExamples.modifyDocument, GroovyExamples.popupMenu).forEach {
                it.installPlugin(handleError = { e: Exception, pluginPath: String ->
                    LivePluginAppComponent.logger.warn("Failed to install plugin: $pluginPath", e)
                })
            }
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
            content = "Without it plugins won't work. <a href=\"\">Download Groovy libraries</a> (~5Mb)",
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
                        val answer = Messages.showOkCancelDialog(
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
                            .createNotification("Failed to download Groovy libraries", NotificationType.WARNING)
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
