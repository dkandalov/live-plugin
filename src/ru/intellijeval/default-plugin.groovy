import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationsManager

static showPopup(String htmlBody, String title = "", notificationType = NotificationType.INFORMATION) {
	((Notifications) NotificationsManager.notificationsManager).notify(new Notification("", title, htmlBody, notificationType))
}

showPopup("Hello IntelliJ")
