import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex

static show(String htmlBody, String title = "", notificationType = NotificationType.INFORMATION) {
	def notification = new Notification("", title, htmlBody, notificationType)
	((Notifications) NotificationsManager.notificationsManager).notify(notification)
}

def fileStats = FileTypeManager.instance.registeredFileTypes.inject([:]) { stats, fileType ->
	def scope = GlobalSearchScope.projectScope(event.project)
	int fileCount = FileBasedIndex.instance.getContainingFiles(FileTypeIndex.NAME, fileType, scope).size()
	if (fileCount > 0) stats.put("'$fileType.defaultExtension'", fileCount)
	stats
}.sort{ -it.value }

show("File count by type:<br/>" + fileStats.entrySet().join("<br/>"))

