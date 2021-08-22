package liveplugin

import com.intellij.ide.AppLifecycleListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.ui.Messages
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.download.DownloadableFileService
import liveplugin.toolwindow.util.GroovyExamples
import liveplugin.toolwindow.util.installPlugin

class LivePluginAppListener: AppLifecycleListener {
    @Suppress("UnstableApiUsage")
    override fun appStarted() {
        checkThatGroovyIsOnClasspath()

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
        IdeUtil.invokeLaterOnEDT {
            listOf(GroovyExamples.helloWorld, GroovyExamples.ideActions, GroovyExamples.modifyDocument, GroovyExamples.popupMenu).forEach {
                it.installPlugin(handleError = { e: Exception, pluginPath: String ->
                    LivePluginAppComponent.logger.warn("Failed to install plugin: $pluginPath", e)
                })
            }
        }
    }

    private fun checkThatGroovyIsOnClasspath(): Boolean {
        val isGroovyOnClasspath = isOnClasspath("org.codehaus.groovy.runtime.DefaultGroovyMethods")
        if (isGroovyOnClasspath) return true

        // This can be useful for non-java IDEs because they don't have bundled groovy libs.
        LivePluginAppComponent.livePluginNotificationGroup.createNotification(
            title = "LivePlugin didn't find Groovy libraries on classpath",
            content = "Without it plugins won't work. <a href=\"\">Download Groovy libraries</a> (~7Mb)",
            type = NotificationType.ERROR
        ).setListener { notification, _ ->
            val groovyVersion = "2.5.11" // Version of groovy used by latest IJ.
            val downloaded = downloadFile(
                "https://repo1.maven.org/maven2/org/codehaus/groovy/groovy-all/$groovyVersion/",
                "groovy-all-$groovyVersion.jar",
                LivePluginPaths.livePluginLibPath
            )
            if (downloaded) {
                notification.expire()
                askIfUserWantsToRestartIde("For Groovy libraries to be loaded IDE restart is required. Restart now?")
            } else {
                LivePluginAppComponent.livePluginNotificationGroup
                    .createNotification("Failed to download Groovy libraries", NotificationType.WARNING)
            }
        }.notify(null)

        return false
    }

    private fun downloadFile(downloadUrl: String, fileName: String, targetPath: FilePath): Boolean =
        downloadFiles(listOf(Pair(downloadUrl, fileName)), targetPath)

    // TODO make download non-modal
    private fun downloadFiles(urlAndFileNames: List<Pair<String, String>>, targetPath: FilePath): Boolean {
        val service = DownloadableFileService.getInstance()
        val descriptions = ContainerUtil.map(urlAndFileNames) { service.createFileDescription(it.first + it.second, it.second) }
        val files = service.createDownloader(descriptions, "").downloadFilesWithProgress(targetPath.value, null, null)
        return files != null && files.size == urlAndFileNames.size
    }

    private fun askIfUserWantsToRestartIde(message: String) {
        val answer = Messages.showOkCancelDialog(message, "Restart Is Required", "Restart", "Postpone", Messages.getQuestionIcon())
        if (answer == Messages.OK) {
            ApplicationManagerEx.getApplicationEx().restart(true)
        }
    }

    private fun isOnClasspath(className: String) =
        LivePluginAppListener::class.java.classLoader.getResource(className.replace(".", "/") + ".class") != null
}