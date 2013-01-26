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
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerAdapter
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import com.intellij.unscramble.UnscrambleDialog
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import javax.swing.*

import static com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import static com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT
import static com.intellij.notification.NotificationType.*
import static com.intellij.openapi.wm.ToolWindowAnchor.RIGHT
/**
 * User: dima
 * Date: 11/08/2012
 */
class PluginUtil {
	private static final Logger LOG = Logger.getInstance("IntelliJEval")
	// Using WeakHashMap to make unregistering tool window optional
	private static final Map<ProjectManagerListener, String> pmListenerToToolWindowId = new WeakHashMap()
	private static final Map<ConsoleView, String> consoleToConsoleTitle = new WeakHashMap()

	/**
	 * Writes a message to "idea.log" file.
	 * (Its location can be found using {@link com.intellij.openapi.application.PathManager#getLogPath()}.)
	 *
	 * @param message message or {@link Throwable} to log
	 * @param notificationType (optional) see https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/notification/NotificationType.java
	 * (Note that NotificationType.ERROR will not just log message but will also show it in "IDE internal errors" toolbar.)
	 */
	static void log(@Nullable message, NotificationType notificationType = INFORMATION) {
		if (!(message instanceof Throwable)) {
			message = String.valueOf(message)
		}
		if (notificationType == INFORMATION) LOG.info(message)
		else if (notificationType == WARNING) LOG.warn(message)
		else if (notificationType == ERROR) LOG.error(message)
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
	static show(@Nullable message, @Nullable title = "",
	            NotificationType notificationType = INFORMATION, String groupDisplayId = "") {
		SwingUtilities.invokeLater({
			def notification = new Notification(groupDisplayId, String.valueOf(title), String.valueOf(message), notificationType)
			ApplicationManager.application.messageBus.syncPublisher(Notifications.TOPIC).notify(notification)
		} as Runnable)
	}

	/**
	 * @param throwable exception to show
	 * @param consoleTitle (optional) might be useful to have different titles if there are several open consoles
	 * @param project console will be displayed in the window of this project
	 */
	static showExceptionInConsole(Throwable throwable, consoleTitle = "", @NotNull Project project) {
		def writer = new StringWriter()
		throwable.printStackTrace(new PrintWriter(writer))
		String text = UnscrambleDialog.normalizeText(writer.buffer.toString())

		showInConsole(text, String.valueOf(consoleTitle), project, ERROR_OUTPUT)
	}

	/**
	 * Opens new "Run" console tool window with {@code text} in it.
	 *
	 * @param text text to show
	 * @param consoleTitle (optional)
	 * @param project console will be displayed in the window of this project
	 * @param contentType (optional) see https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/execution/ui/ConsoleViewContentType.java
	 */
	static ConsoleView showInNewConsole(@Nullable text, String consoleTitle = "", @NotNull Project project, ConsoleViewContentType contentType = NORMAL_OUTPUT) {
		if (text instanceof Throwable) {
			showExceptionInConsole(text, consoleTitle, project)
		} else {
			Util.displayInConsole(consoleTitle, String.valueOf(text), contentType, project)
		}
	}

	/**
	 * Opens "Run" console tool window with {@code text} in it.
	 * If console with the same {@code consoleTitle} already exists, the text is appended to it.
	 *
	 * (The only reason to use "Run" console is because it's convenient for showing multi-line text.)
	 *
	 * @param text
	 * @param consoleTitle
	 * @param project
	 * @param contentType
	 */
	static ConsoleView showInConsole(@Nullable text, String consoleTitle = "", @NotNull Project project, ConsoleViewContentType contentType = NORMAL_OUTPUT) {
		ConsoleView console = consoleToConsoleTitle.find{ it.value == consoleTitle }?.key
		if (console == null) {
			console = showInNewConsole(text, consoleTitle, project, contentType)
			consoleToConsoleTitle[console] = consoleTitle
			console
		} else {
			console.print(String.valueOf(text), contentType)
			console
		}
	}

	/**
	 * Registers action in IDE.
	 * If there is already an action with {@code actionId}, it will be replaced.
	 * (The main reason to replace action is to be able to incrementally add code to callback without restarting IDE.)
	 *
	 * @param actionId unique identifier for action
	 * @param keyStroke (optional) e.g. "ctrl alt shift H" or "alt C, alt H" for double key stroke.
	 *        Note that letters must be uppercase, modification keys lowercase.
	 * @param actionGroupId (optional) can be used to add actions to existing menus, etc.
	 *                      (e.g. "ToolsMenu" corresponds to main menu "Tools")
	 *                      The best way to find existing actionGroupIds is probably to search IntelliJ source code for "group id=".
	 * @param displayText (optional) if action is added to menu, this text will be shown
	 * @param callback code to run when action is invoked
	 * @return instance of created action
	 */
	static AnAction registerAction(String actionId, String keyStroke = "",
	                               String actionGroupId = null, String displayText = "", Closure callback) {
		def action = new AnAction(displayText) {
			@Override void actionPerformed(AnActionEvent event) { callback.call(event) }
		}

		def actionManager = ActionManager.instance
		def actionGroup = findActionGroup(actionGroupId)

		def alreadyRegistered = (actionManager.getAction(actionId) != null)
		if (alreadyRegistered) {
			actionGroup?.remove(actionManager.getAction(actionId))
			actionManager.unregisterAction(actionId)
		}

		registerKeyStroke(actionId, keyStroke)
		actionManager.registerAction(actionId, action)
		actionGroup?.add(action)

		log("Action '${actionId}' registered")

		action
	}

	/**
	 * Registers a tool window in IDE.
	 * If there is already a tool window with {@code toolWindowId}, it will be replaced.
	 *
	 * @param toolWindowId unique identifier for tool window
	 * @param component content of the tool window
	 * @param location (optional) see https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/openapi/wm/ToolWindowAnchor.java
	 */
	static registerToolWindow(String toolWindowId, JComponent component, ToolWindowAnchor location = RIGHT) {
		def previousListener = pmListenerToToolWindowId.find{ it == toolWindowId }?.key
		if (previousListener != null) {
			ProjectManager.instance.removeProjectManagerListener(previousListener)
			pmListenerToToolWindowId.remove(previousListener)
		}

		def listener = new ProjectManagerAdapter() {
			@Override void projectOpened(Project project) {
				registerToolWindowIn(project, toolWindowId, component)
			}

			@Override void projectClosed(Project project) {
				unregisterToolWindowIn(project, toolWindowId)
			}
		}
		pmListenerToToolWindowId[listener] = toolWindowId
		ProjectManager.instance.addProjectManagerListener(listener)

		ProjectManager.instance.openProjects.each { project ->
			registerToolWindowIn(project, toolWindowId, component)
		}

		log("Toolwindow '${toolWindowId}' registered")
	}

	/**
	 * This method exists for reference only.
	 * For more dialogs see https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/openapi/ui/Messages.java
	 */
	@Nullable static String showInputDialog(String message, String title, @Nullable Icon icon) {
		Messages.showInputDialog(message, title, icon)
	}


	private static DefaultActionGroup findActionGroup(String actionGroupId) {
		if (actionGroupId != null && actionGroupId) {
			def action = ActionManager.instance.getAction(actionGroupId)
			action instanceof DefaultActionGroup ? action : null
		} else {
			null
		}
	}

	private static void registerKeyStroke(String actionId, String keyStroke) {
		def keymap = KeymapManager.instance.activeKeymap
		keymap.removeAllActionShortcuts(actionId)
		if (!keyStroke.empty) {
			if (keyStroke.contains(",")) {
				def firstKeyStroke = { keyStroke[0..<keyStroke.indexOf(",")].trim() }
				def secondKeyStroke = { keyStroke[(keyStroke.indexOf(",") + 1)..-1].trim() }
				keymap.addShortcut(actionId,
						new KeyboardShortcut(
								KeyStroke.getKeyStroke(firstKeyStroke()),
								KeyStroke.getKeyStroke(secondKeyStroke())))
			} else {
				keymap.addShortcut(actionId,
						new KeyboardShortcut(
								KeyStroke.getKeyStroke(keyStroke), null))
			}
		}
	}

	private static ToolWindow registerToolWindowIn(Project project, String id, JComponent component) {
		def manager = ToolWindowManager.getInstance(project)

		if (manager.getToolWindow(id) != null) {
			manager.unregisterToolWindow(id)
		}

		def toolWindow = manager.registerToolWindow(id, false, RIGHT)
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
				showExceptionInConsole(e, e.class.simpleName, project)
			}
		}
	}

	/**
	 * @return {@link VirtualFile} for opened editor tab; null if there are no open files
	 */
	@Nullable static VirtualFile currentFileIn(@NotNull Project project) {
		((FileEditorManagerEx) FileEditorManagerEx.getInstance(project)).currentFile
	}

	// TODO method to edit content of a file (read-write action wrapper)
	// TODO method to iterate over all virtual files in project
	// TODO method to iterate over PSI files in project


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
