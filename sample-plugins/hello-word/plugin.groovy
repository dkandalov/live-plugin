import com.intellij.openapi.project.Project
import util.Util
import org.mockito.Mockito
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager

//-- classpath: /Users/dima/.m2/repository/org/mockito/mockito-all/1.8.4/mockito-all-1.8.4.jar

//static showPopup(String htmlBody, String toolWindowId = ToolWindowId.RUN, MessageType messageType = MessageType.INFO, event) {
//    ToolWindowManager.getInstance(event.project).notifyByBalloon(toolWindowId, messageType, htmlBody)
//}
//static showPopup2(String htmlBody, String title = "", NotificationType notificationType = NotificationType.INFORMATION) {
//    ((Notifications) NotificationsManager.notificationsManager).notify(new Notification("", title, htmlBody, notificationType))
//}

//showPopup("hello world!", event)
//showPopup2("hello world 2")
//Mockito.mock(Project.class)

//Util.ooo()

//showPopup(Util.class.declaredMethods.collect{it.name}.toString())
println(Util.class.declaredMethods.collect{it.name})