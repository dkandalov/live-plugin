import com.intellij.notification.*


static show(String htmlBody, String title = "", notificationType = NotificationType.INFORMATION) {
	def notification = new Notification("", title, htmlBody, notificationType)
	((Notifications) NotificationsManager.notificationsManager).notify(notification)
}

show("Hello IntelliJ")

// You can find more examples in "Add Plugin - Example" menu
