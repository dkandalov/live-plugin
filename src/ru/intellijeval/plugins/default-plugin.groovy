import com.intellij.notification.*

static showPopup(String htmlBody, String title = "", notificationType = NotificationType.INFORMATION) {
	((Notifications) NotificationsManager.notificationsManager).notify(new Notification("", title, htmlBody, notificationType))
}

showPopup("Hello IntelliJ")
