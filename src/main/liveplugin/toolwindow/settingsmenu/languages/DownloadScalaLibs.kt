package liveplugin.toolwindow.settingsmenu.languages

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.containers.ContainerUtil.map
import liveplugin.IDEUtil.askIfUserWantsToRestartIde
import liveplugin.IDEUtil.downloadFiles
import liveplugin.LivePluginAppComponent.Companion.livePluginNotificationGroup
import liveplugin.LivePluginAppComponent.Companion.livepluginLibsPath
import liveplugin.LivePluginAppComponent.Companion.scalaIsOnClassPath
import liveplugin.MyFileUtil.fileNamesMatching
import java.io.File

class DownloadScalaLibs: AnAction(), DumbAware {

    override fun update(event: AnActionEvent) {
        if (scalaIsOnClassPath()) {
            event.presentation.text = "Remove Scala from LivePlugin Classpath"
            event.presentation.description = "Remove Scala from LivePlugin Classpath"
        } else {
            event.presentation.text = "Download Scala to LivePlugin Classpath"
            event.presentation.description = "Download Scala libraries to LivePlugin classpath to enable Scala plugins support " + approximateSize
        }
    }

    override fun actionPerformed(event: AnActionEvent) {
        if (scalaIsOnClassPath()) {
            val answer = Messages.showYesNoDialog(event.project,
                                                  "Do you want to remove Scala libraries from LivePlugin classpath? This action cannot be undone.", "Live Plugin", null)
            if (answer == Messages.YES) {
                for (fileName in fileNamesMatching(libFilesPattern, livepluginLibsPath)) {
                    FileUtil.delete(File(livepluginLibsPath + fileName))
                }
                askIfUserWantsToRestartIde("For Scala libraries to be unloaded IDE restart is required. Restart now?")
            }
        } else {
            val answer = Messages.showOkCancelDialog(event.project,
                                                     "Scala libraries " + approximateSize + " will be downloaded to '" + livepluginLibsPath + "'." +
                                                         "\n(If you already have scala >= 2.11, you can copy it manually and restart IDE.)", "Live Plugin", null)
            if (answer != Messages.OK) return

            val scalaLibs = listOf("scala-library", "scala-compiler", "scala-reflect", "scalap")
            val urlAndFileNamePairs = map(scalaLibs) { it ->
                // Using alternative maven repo instead of "repo1.maven.org" because standard repo for some reason
                // returns 403 when requested scala libs from IntelliJ downloader (even though the same code works for clojure libs)
                // (using this particular repo because it seems to be the fastest mirror http://docs.codehaus.org/display/MAVENUSER/Mirrors+Repositories)
                Pair.create("http://maven.antelink.com/content/repositories/central/org/scala-lang/$it/2.11.7/", it + "-2.11.7.jar")
            }

            val downloaded = downloadFiles(urlAndFileNamePairs, livepluginLibsPath)
            if (downloaded) {
                askIfUserWantsToRestartIde("For Scala libraries to be loaded IDE restart is required. Restart now?")
            } else {
                livePluginNotificationGroup
                    .createNotification("Failed to download Scala libraries", NotificationType.WARNING)
            }
        }
    }

    companion object {
        val libFilesPattern = "(scala-|scalap).*jar"
        private val approximateSize = "(~25Mb)"
    }
}
