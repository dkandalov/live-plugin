package liveplugin.toolwindow.settingsmenu.languages

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import liveplugin.IDEUtil.askIfUserWantsToRestartIde
import liveplugin.IDEUtil.downloadFiles
import liveplugin.LivePluginAppComponent.Companion.clojureIsOnClassPath
import liveplugin.LivePluginAppComponent.Companion.livePluginNotificationGroup
import liveplugin.LivePluginAppComponent.Companion.livepluginLibsPath
import liveplugin.MyFileUtil.fileNamesMatching
import java.io.File
import java.util.Arrays.asList

class DownloadClojureLibs: AnAction(), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        if (clojureIsOnClassPath()) {
            val answer = Messages.showYesNoDialog(
                event.project,
                "Do you want to remove Clojure libraries from LivePlugin classpath? This action cannot be undone.", "Live Plugin", null)
            if (answer == Messages.YES) {
                for (fileName in fileNamesMatching(libFilesPattern, livepluginLibsPath)) {
                    FileUtil.delete(File(livepluginLibsPath + fileName))
                }
                askIfUserWantsToRestartIde("For Clojure libraries to be unloaded IDE restart is required. Restart now?")
            }
        } else {
            val answer = Messages.showOkCancelDialog(
                event.project,
                "Clojure libraries " + approximateSize + " will be downloaded to '" + livepluginLibsPath + "'." +
                    "\n(If you already have clojure >= 1.7.0, you can copy it manually and restart IDE.)", "Live Plugin", null)
            if (answer != Messages.OK) return

            val downloaded = downloadFiles(asList(
                Pair.create("http://repo1.maven.org/maven2/org/clojure/clojure/1.7.0/", "clojure-1.7.0.jar"),
                Pair.create("http://repo1.maven.org/maven2/org/clojure/clojure-contrib/1.2.0/", "clojure-contrib-1.2.0.jar")
            ), livepluginLibsPath)
            if (downloaded) {
                askIfUserWantsToRestartIde("For Clojure libraries to be loaded IDE restart is required. Restart now?")
            } else {
                livePluginNotificationGroup
                    .createNotification("Failed to download Clojure libraries", NotificationType.WARNING)
            }
        }
    }

    override fun update(event: AnActionEvent) {
        if (clojureIsOnClassPath()) {
            event.presentation.text = "Remove Clojure from LivePlugin Classpath"
            event.presentation.description = "Remove Clojure from LivePlugin Classpath"
        } else {
            event.presentation.text = "Download Clojure to LivePlugin Classpath"
            event.presentation.description = "Download Clojure libraries to LivePlugin classpath to enable clojure plugins support " + approximateSize
        }
    }

    companion object {
        val libFilesPattern = "clojure-.*jar"
        private val approximateSize = "(~5Mb)"
    }
}
