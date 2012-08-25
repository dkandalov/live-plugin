package ru.intellijeval
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerAdapter
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import com.intellij.unscramble.UnscrambleDialog

import javax.swing.*

import static com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT
/**
 * User: dima
 * Date: 11/08/2012
 */
class PluginUtil {

	static log(String htmlBody, String title = "", notificationType = NotificationType.INFORMATION) {
		def notification = new Notification("", title, htmlBody, notificationType)
		ApplicationManager.application.messageBus.syncPublisher(Notifications.TOPIC).notify(notification)
	}

	static show(String htmlBody, String title = "", notificationType = NotificationType.INFORMATION) {
		def notification = new Notification("", title, htmlBody, notificationType)
		((Notifications) NotificationsManager.notificationsManager).notify(notification)
	}

	static showExceptionInConsole(Exception e, String header = "", Project project, ConsoleViewContentType contentType = NORMAL_OUTPUT) {
		def writer = new StringWriter()
		e.printStackTrace(new PrintWriter(writer))
		String text = UnscrambleDialog.normalizeText(writer.buffer.toString())

		showInConsole(text, header, project, contentType)
	}

	static showInConsole(String text, String header = "", Project project, ConsoleViewContentType contentType = NORMAL_OUTPUT) {
		Util.displayInConsole(header, text, contentType, project)
	}

	static registerAction(String actionId, String keyStroke = "", Closure closure) {
		def actionManager = ActionManager.instance
		def keymap = KeymapManager.instance.activeKeymap

		def alreadyRegistered = (actionManager.getAction(actionId) != null)
		if (alreadyRegistered) {
			keymap.removeAllActionShortcuts(actionId)
			actionManager.unregisterAction(actionId)
		}

		if (!keyStroke.empty) keymap.addShortcut(actionId, new KeyboardShortcut(KeyStroke.getKeyStroke(keyStroke), null))
		actionManager.registerAction(actionId, new AnAction() {
			@Override void actionPerformed(AnActionEvent e) {
				closure.call(e)
			}
		})

		log("Action '${actionId}' loaded")
	}

	static registerToolWindow(String id, JComponent component) {
		ProjectManager.instance.addProjectManagerListener(new ProjectManagerAdapter() {
			@Override void projectOpened(Project project) {
				registerToolWindowIn(project, id, component)
			}

			@Override void projectClosed(Project project) {
				unregisterToolWindowIn(project, id)
			}
		})

		ProjectManager.instance.openProjects.each { project ->
			registerToolWindowIn(project, id, component)
		}
	}

	private static ToolWindow registerToolWindowIn(Project project, String id, JComponent component) {
		def manager = ToolWindowManager.getInstance(project)

		if (manager.getToolWindow(id) != null) {
			manager.unregisterToolWindow(id)
		}

		def toolWindow = manager.registerToolWindow(id, false, ToolWindowAnchor.RIGHT)
		def content = ContentFactory.SERVICE.instance.createContent(component, "", false)
		toolWindow.contentManager.addContent(content)
		toolWindow
	}

	private static unregisterToolWindowIn(Project project, String id) {
		ToolWindowManager.getInstance(project).unregisterToolWindow(id)
	}

	static doCatchingAll(Closure closure) {
		try {

			closure.call()

		} catch (Exception e) {
			ProjectManager.instance.openProjects.each { Project project ->
				showExceptionInConsole(e, e.class.simpleName, project, ConsoleViewContentType.ERROR_OUTPUT)
			}
		}
	}

	static accessField(Object o, String fieldName, Closure callback) {
		doCatchingAll {
			for (field in o.class.declaredFields) {
				if (field.name == fieldName) {
					field.setAccessible(true)
					callback(field.get(o))
					return
				}
			}
		}
	}

	static registerInMetaClasses(AnActionEvent anActionEvent) { // TODO
		[Object.metaClass, Class.metaClass].each {
			it.actionEvent = { anActionEvent }
			it.project = { actionEvent().getData(PlatformDataKeys.PROJECT) }
			it.editor = { actionEvent().getData(PlatformDataKeys.EDITOR) }
			it.fileText = { actionEvent().getData(PlatformDataKeys.FILE_TEXT) }
		}
	}
}
