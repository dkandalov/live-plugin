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
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import javax.swing.*

import static com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT

/**
 * User: dima
 * Date: 11/08/2012
 */
class PluginUtil {
	// TODO add javadocs?

	private static final WeakHashMap<ProjectManagerListener, String> pmListenerToId = new WeakHashMap()

	// TODO use actual intellij logger
	static log(String htmlBody, String title = "", NotificationType notificationType = NotificationType.INFORMATION, String groupDisplayId = "") {
		def notification = new Notification(groupDisplayId, title, htmlBody, notificationType)
		ApplicationManager.application.messageBus.syncPublisher(Notifications.TOPIC).notify(notification)
	}

	/**
	 * Shows popup balloon notification.
	 * (It actually sends IDE notification event which by default shows "balloon".
	 * This also means that message will be added to "Event Log" console.)
	 *
	 * See "IDE Settings - Notifications".
	 *
	 * @param message message to display (can have html tags in it)
	 * @param title (optional) popup title
	 * @param notificationType (optional) see https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/notification/NotificationType.java
	 * @param groupDisplayId (optional) an id to group notifications by (can be configured in "IDE Settings - Notifications")
	 */
	static show(@Nullable Object message, @Nullable Object title = "", NotificationType notificationType = NotificationType.INFORMATION, String groupDisplayId = "") {
		SwingUtilities.invokeLater({
			def notification = new Notification(groupDisplayId, String.valueOf(title), String.valueOf(message), notificationType)
			ApplicationManager.application.messageBus.syncPublisher(Notifications.TOPIC).notify(notification)
		} as Runnable)
	}

	/**
	 * @param e exception to show
	 * @param consoleTitle (might be useful to have different titles if there are several open consoles)
	 * @param project console will be displayed in the window of this project
	 */
	static showExceptionInConsole(Exception e, Object consoleTitle = "", @NotNull Project project) {
		def writer = new StringWriter()
		e.printStackTrace(new PrintWriter(writer))
		String text = UnscrambleDialog.normalizeText(writer.buffer.toString())

		showInConsole(text, String.valueOf(consoleTitle), project, ConsoleViewContentType.ERROR_OUTPUT)
	}

	/**
	 *
	 * @param text
	 * @param header
	 * @param project
	 * @param contentType see https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/execution/ui/ConsoleViewContentType.java
	 */
	// TODO show reuse the same console and append to output
	static showInConsole(String text, String header = "", Project project, ConsoleViewContentType contentType = NORMAL_OUTPUT) {
		Util.displayInConsole(header, text, contentType, project)
	}

	/**
	 *
	 * @param actionId
	 * @param keyStroke
	 * @param closure
	 */
	static registerAction(String actionId, String keyStroke = "", Closure closure) {
		def actionManager = ActionManager.instance
		def keymap = KeymapManager.instance.activeKeymap

		def alreadyRegistered = (actionManager.getAction(actionId) != null)
		if (alreadyRegistered) {
			keymap.removeAllActionShortcuts(actionId)
			actionManager.unregisterAction(actionId)
		}

		def firstKeyStroke = { keyStroke[0..<keyStroke.indexOf(",")].trim() }
		def secondKeyStroke = { keyStroke[(keyStroke.indexOf(",") + 1)..-1].trim() }
		if (!keyStroke.empty) keymap.addShortcut(actionId,
				new KeyboardShortcut(KeyStroke.getKeyStroke(firstKeyStroke()), KeyStroke.getKeyStroke(secondKeyStroke())))

		actionManager.registerAction(actionId, new AnAction() {
			@Override void actionPerformed(AnActionEvent event) {
				closure.call(event)
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
