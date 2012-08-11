package util

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager

static showPopup(String htmlBody, String toolWindowId = ToolWindowId.RUN, MessageType messageType = MessageType.INFO, event) {
	ToolWindowManager.getInstance(event.project()).notifyByBalloon(toolWindowId, messageType, htmlBody)
}

static showPopup2(String htmlBody, String title = "", NotificationType notificationType = NotificationType.INFORMATION) {
	((Notifications) NotificationsManager.notificationsManager).notify(new Notification("", title, htmlBody, notificationType))
}

