import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex

import javax.swing.SwingUtilities

static show(String htmlBody, String title = "", NotificationType notificationType = NotificationType.INFORMATION) {
    SwingUtilities.invokeLater({
        def notification = new Notification("", title, htmlBody, notificationType)
        ApplicationManager.application.messageBus.syncPublisher(Notifications.TOPIC).notify(notification)
    } as Runnable)
}

def fileStats = FileTypeManager.instance.registeredFileTypes.inject([:]) { stats, fileType ->
	def scope = GlobalSearchScope.projectScope(event.project)
	int fileCount = FileBasedIndex.instance.getContainingFiles(FileTypeIndex.NAME, fileType, scope).size()
	if (fileCount > 0) stats.put("'$fileType.defaultExtension'", fileCount)
	stats
}.sort{ -it.value }

show("File count by type:<br/>" + fileStats.entrySet().join("<br/>"))

