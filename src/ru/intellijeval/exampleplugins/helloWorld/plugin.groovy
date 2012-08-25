import com.intellij.notification.*


static show(String htmlBody, String title = "", notificationType = NotificationType.INFORMATION) {
	((Notifications) NotificationsManager.notificationsManager).notify(new Notification("", title, htmlBody, notificationType))
}

show("Hello IntelliJ")

// You can find more examples in "Add Plugin - Example" menu
