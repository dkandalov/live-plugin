package ru.intellijeval

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.unscramble.AnalyzeStacktraceUtil
import com.intellij.unscramble.UnscrambleDialog

import javax.swing.KeyStroke

/**
 * User: dima
 * Date: 11/08/2012
 */
class PluginUtil {

	static registerInMetaClasses(AnActionEvent anActionEvent) {
		[Object.metaClass, Class.metaClass].each {
			it.actionEvent = { anActionEvent }
			it.project = { actionEvent().getData(PlatformDataKeys.PROJECT) }
			it.editor = { actionEvent().getData(PlatformDataKeys.EDITOR) }
			it.fileText = { actionEvent().getData(PlatformDataKeys.FILE_TEXT) }
		}
	}

	static showPopup(String htmlBody, String title = "", NotificationType notificationType = NotificationType.INFORMATION) {
		((Notifications) NotificationsManager.notificationsManager).notify(new Notification("", title, htmlBody, notificationType))
	}

	static showBalloonPopup(String htmlBody, String toolWindowId = ToolWindowId.RUN, MessageType messageType = MessageType.INFO, Project project) {
		ToolWindowManager.getInstance(project).notifyByBalloon(toolWindowId, messageType, htmlBody)
	}

	static showInUnscrambleDialog(Exception e, Project project) {
		def writer = new StringWriter()
		e.printStackTrace(new PrintWriter(writer))
		def s = UnscrambleDialog.normalizeText(writer.buffer.toString())
		def console = UnscrambleDialog.addConsole(project, [])
		AnalyzeStacktraceUtil.printStacktrace(console, s)
	}

	static catchingAll(Closure closure) {
		try {
			closure.call()
		} catch (Exception e) {
			showInUnscrambleDialog(e, project())
			showPopup("Caught exception")
		}
	}

	def registerAction(String actionId, String keyStroke = "", Closure closure) {
		catchingAll {
			def actionManager = ActionManager.instance
			if (actionManager.getActionIds("").toList().contains(actionId)) {
				actionManager.unregisterAction(actionId)
			}

			def action = new AnAction() {
				@Override void actionPerformed(AnActionEvent e) {
					closure.call(e)
				}
			}
			KeymapManager.instance.activeKeymap.addShortcut(actionId, new KeyboardShortcut(KeyStroke.getKeyStroke(keyStroke), null))
			actionManager.registerAction(actionId, action)
		}
	}

	static accessField(Object o, String fieldName, Closure callback) {
		try {
			for (field in o.class.declaredFields) {
				if (field.name == fieldName) {
					field.setAccessible(true)
					callback(field.get(o))
					return
				}
			}
		}
		catch (Exception e) {
			showPopup(e.toString())
		}
	}

}
