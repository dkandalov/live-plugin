package ssh

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.unscramble.AnalyzeStacktraceUtil
import com.intellij.unscramble.UnscrambleDialog

class Utils {
  static def sayHi() {
    project().toString()
  }

  static registerInMetaClasses(AnActionEvent anActionEvent) {
    [Object.metaClass, Class.metaClass].each {
      it.actionEvent = { anActionEvent }
      it.project = { actionEvent().getData(PlatformDataKeys.PROJECT) }
      it.editor = { actionEvent().getData(PlatformDataKeys.EDITOR) }
      it.fileText = { actionEvent().getData(PlatformDataKeys.FILE_TEXT) }
    }
  }

  static showPopup(String htmlBody, String toolWindowId = ToolWindowId.RUN, MessageType messageType = MessageType.INFO) {
    ToolWindowManager.getInstance(project()).notifyByBalloon(toolWindowId, messageType, htmlBody)
  }

  static showInUnscrambleDialog(Exception e) {
    def writer = new StringWriter()
    e.printStackTrace(new PrintWriter(writer))
    def s = UnscrambleDialog.normalizeText(writer.buffer.toString())
    def console = UnscrambleDialog.addConsole(project(), [])
    AnalyzeStacktraceUtil.printStacktrace(console, s)
  }

  static catchingAll(Closure closure) {
    try {
      closure.call()
    } catch (Exception e) {
      showInUnscrambleDialog(e)
      showPopup("Caught exception", ToolWindowId.RUN, MessageType.ERROR)
    }
  }

  static aaa(String groupDisplayId, String title, String content, NotificationType type = NotificationType.INFORMATION) {
    com.intellij.notification.Notifications.Bus.notify(new Notification(groupDisplayId, title, content, type))
  }
}
