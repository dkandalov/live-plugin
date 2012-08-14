package util

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager

class Util {
    static void ooo() {
        println("hey logs!2222")
    }

    static showPopup(String htmlBody, String toolWindowId = ToolWindowId.RUN, MessageType messageType = MessageType.INFO, event) {
//	ToolWindowManager.getInstance(event.project).notifyByBalloon(toolWindowId, messageType, htmlBody)
        println("hey logs!2222")
    }

    static showPopup2(String htmlBody, String title = "", NotificationType notificationType = NotificationType.INFORMATION) {
//	((Notifications) NotificationsManager.notificationsManager).notify(new Notification("", title, htmlBody, notificationType))
        println("hey logs!2222")
    }

}
