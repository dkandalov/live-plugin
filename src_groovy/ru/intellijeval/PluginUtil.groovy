/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.openapi.project.ProjectManagerListener
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
	private static final WeakHashMap<ProjectManagerListener, String> pmListenerToId = new WeakHashMap()

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

		log("Action '${actionId}' registered")
	}

	static unregisterAction(String actionId) {
		ActionManager.instance.unregisterAction(actionId)
		log("Action '${actionId}' unregistered")
	}

	static registerToolWindow(String id, JComponent component) {
		def listener = new ProjectManagerAdapter() {
			@Override void projectOpened(Project project) {
				registerToolWindowIn(project, id, component)
			}

			@Override void projectClosed(Project project) {
				unregisterToolWindowIn(project, id)
			}
		}
		pmListenerToId[listener] = id
		ProjectManager.instance.addProjectManagerListener(listener)

		ProjectManager.instance.openProjects.each { project ->
			registerToolWindowIn(project, id, component)
		}

		log("Toolwindow '${id}' registered")
	}

	static unregisterToolWindow(String id) {
		def entry = pmListenerToId.find {it.value == id}
		if (entry != null) ProjectManager.instance.removeProjectManagerListener(entry.key)

		ProjectManager.instance.openProjects.each { project ->
			unregisterToolWindowIn(project, id)
		}

		log("Toolwindow '${id}' unregistered")
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

	static catchingAll(Closure closure) {
		try {

			closure.call()

		} catch (Exception e) {
			ProjectManager.instance.openProjects.each { Project project ->
				showExceptionInConsole(e, e.class.simpleName, project, ConsoleViewContentType.ERROR_OUTPUT)
			}
		}
	}

	static accessField(Object o, String fieldName, Closure callback) {
		catchingAll {
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
