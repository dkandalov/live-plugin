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
package intellijeval

import com.intellij.execution.ExecutionManager
import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.actions.CloseAction
import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerAdapter
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.ui.content.ContentFactory
import com.intellij.unscramble.UnscrambleDialog
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.junit.Test

import javax.swing.*
import java.awt.*
import java.util.List
import java.util.concurrent.atomic.AtomicReference

import static com.intellij.notification.NotificationType.*
import static com.intellij.openapi.progress.PerformInBackgroundOption.ALWAYS_BACKGROUND
import static com.intellij.openapi.wm.ToolWindowAnchor.RIGHT

/**
 *
 */
@SuppressWarnings("GroovyUnusedDeclaration")
class PluginUtil {
	/**
	 * Writes a message to "idea.log" file.
	 * (Its location can be found using {@link com.intellij.openapi.application.PathManager#getLogPath()}.)
	 *
	 * @param message message or {@link Throwable} to log
	 * @param notificationType (optional) see https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/notification/NotificationType.java
	 * (Note that NotificationType.ERROR will not just log message but will also show it in "IDE internal errors" toolbar.)
	 */
	@CanCallFromAnyThread
	static void log(@Nullable message, NotificationType notificationType = INFORMATION) {
		if (!(message instanceof Throwable)) {
			message = asString(message)
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
	@CanCallFromAnyThread
	static show(@Nullable message, @Nullable title = "",
	            NotificationType notificationType = INFORMATION, String groupDisplayId = "") {
		SwingUtilities.invokeLater({
			message = asString(message)
			// this is because Notification doesn't accept empty messages
			if (message.trim().empty) message = "[empty message]"

			def notification = new Notification(groupDisplayId, asString(title), message, notificationType)
			ApplicationManager.application.messageBus.syncPublisher(Notifications.TOPIC).notify(notification)
		} as Runnable)
	}

	/**
	 * Opens new "Run" console tool window with {@code text} in it.
	 *
	 * @param message text or exception to show
	 * @param consoleTitle (optional)
	 * @param project console will be displayed in the window of this project
	 * @param contentType (optional) see https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/execution/ui/ConsoleViewContentType.java
	 */
	@CanCallFromAnyThread
	static ConsoleView showInNewConsole(@Nullable message, String consoleTitle = "", @NotNull Project project,
	                                    ConsoleViewContentType contentType = guessContentTypeOf(message)) {
		AtomicReference<ConsoleView> result = new AtomicReference(null)
		// Use reference for consoleTitle because get groovy Reference class like in this bug http://jira.codehaus.org/browse/GROOVY-5101
		AtomicReference<String> titleRef = new AtomicReference(consoleTitle)

		UIUtil.invokeAndWaitIfNeeded {
			if (message instanceof Throwable) {
				def writer = new StringWriter()
				message.printStackTrace(new PrintWriter(writer))
				message = UnscrambleDialog.normalizeText(writer.buffer.toString())

				result.set(showInNewConsole(message, titleRef.get(), project, contentType))
			} else {
				ConsoleView console = TextConsoleBuilderFactory.instance.createBuilder(project).console
				console.print(asString(message), contentType)

				DefaultActionGroup toolbarActions = new DefaultActionGroup()
				def consoleComponent = new MyConsolePanel(console, toolbarActions)
				RunContentDescriptor descriptor = new RunContentDescriptor(console, null, consoleComponent, titleRef.get()) {
					@Override boolean isContentReuseProhibited() { true }
					@Override Icon getIcon() { AllIcons.Nodes.Plugin }
				}
				Executor executor = DefaultRunExecutor.runExecutorInstance

				toolbarActions.add(new ConsoleCloseAction(console, executor, descriptor, project))
				console.createConsoleActions().each{ toolbarActions.add(it) }

				ExecutionManager.getInstance(project).contentManager.showRunContent(executor, descriptor)
				result.set(console)
			}
		}
		result.get()
	}

	/**
	 * Opens "Run" console tool window with {@code text} in it.
	 * If console with the same {@code consoleTitle} already exists, the text is appended to it.
	 *
	 * (The only reason to use "Run" console is because it's convenient for showing multi-line text.)
	 *
	 * @param message text or exception to show
	 * @param consoleTitle (optional)
	 * @param project console will be displayed in the window of this project
	 * @param contentType (optional) see https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/execution/ui/ConsoleViewContentType.java
	 */
	@CanCallFromAnyThread
	static ConsoleView showInConsole(@Nullable message, String consoleTitle = "", @NotNull Project project,
	                                 ConsoleViewContentType contentType = guessContentTypeOf(message)) {
		AtomicReference<ConsoleView> result = new AtomicReference(null)
		UIUtil.invokeAndWaitIfNeeded {
			ConsoleView console = consoleToConsoleTitle.find{ it.value == consoleTitle }?.key
			if (console == null) {
				console = showInNewConsole(message, consoleTitle, project, contentType)
				consoleToConsoleTitle[console] = consoleTitle
			} else {
				console.print("\n" + asString(message), contentType)
			}
			result.set(console)
		}
		result.get()
	}

	/**
	 * Registers action in IDE.
	 * If there is already an action with {@code actionId}, it will be replaced.
	 * (The main reason to replace action is to be able to incrementally add code to callback without restarting IDE.)
	 *
	 * @param actionId unique identifier for action
	 * @param keyStroke (optional) e.g. "ctrl alt shift H" or "alt C, alt H" for double key stroke.
	 *        Note that letters must be uppercase, modification keys lowercase.
	 *        See {@link javax.swing.KeyStroke#getKeyStroke(String)}
	 * @param actionGroupId (optional) can be used to add actions to existing menus, etc.
	 *                      (e.g. "ToolsMenu" corresponds to main menu "Tools")
	 *                      The best way to find existing actionGroupIds is probably to search IntelliJ source code for "group id=".
	 * @param displayText (optional) if action is added to menu, this text will be shown
	 * @param callback code to run when action is invoked. {@link AnActionEvent} will be passed as a parameter.
	 *
	 * @return instance of created action
	 */
	static AnAction registerAction(String actionId, String keyStroke = "", // TODO check that keyStroke is correct
	                               String actionGroupId = null, String displayText = actionId, Closure callback) {
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
	@CanOnlyCallFromEDT
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
	@CanOnlyCallFromEDT
	@Nullable static String showInputDialog(String message, String title, @Nullable Icon icon = null) {
		Messages.showInputDialog(message, title, icon)
	}

	/**
	 * @return currently open editor; null if there are no open files
	 */
	@CanOnlyCallFromEDT
	@Nullable static Editor currentEditorIn(@NotNull Project project) {
		((FileEditorManagerEx) FileEditorManagerEx.getInstance(project)).selectedTextEditor
	}

	/**
	 * @return {@PsiFile} for opened editor tab; null if there are no open files
	 */
	@CanOnlyCallFromEDT
	@Nullable static PsiFile currentPsiFileIn(@NotNull Project project) {
		PsiManager.getInstance(project).findFile(currentFileIn(project))
	}

	/**
	 * @return {@link Document} for opened editor tab; null if there are no open files
	 */
	@CanOnlyCallFromEDT
	@Nullable static Document currentDocumentIn(@NotNull Project project) {
		def file = ((FileEditorManagerEx) FileEditorManagerEx.getInstance(project)).currentFile
		if (file == null) return null
		FileDocumentManager.instance.getDocument(file)
	}

	/**
	 * @return {@link VirtualFile} for opened editor tab; null if there are no open files
	 */
	@CanOnlyCallFromEDT
	@Nullable static VirtualFile currentFileIn(@NotNull Project project) {
		((FileEditorManagerEx) FileEditorManagerEx.getInstance(project)).currentFile
	}

	/**
	 * @return lazy iterator for all {@link VirtualFile}s in project (in breadth-first order)
	 */
	static Iterator<VirtualFile> allFilesIn(@NotNull Project project) {
		def sourceRoots = ProjectRootManager.getInstance(project).contentSourceRoots
		def queue = new LinkedList<VirtualFile>(sourceRoots.toList())

		new Iterator<VirtualFile>() {
			@Override boolean hasNext() { !queue.empty }

			@Override VirtualFile next() {
				if (queue.first.isDirectory())
					queue.addAll(queue.first.children)
				queue.removeFirst()
			}

			@Override void remove() { throw new UnsupportedOperationException() }
		}
	}

	/**
	 * @return lazy iterator for all {@link Document}s in project (in breadth-first order).
	 *         Note that iterator can return null elements.
	 */
	static Iterator<Document> allDocumentsIn(@NotNull Project project) {
		def fileIterator = allFilesIn(project)
		def documentManager = FileDocumentManager.instance

		new Iterator<Document>() {
			@Override boolean hasNext() { fileIterator.hasNext() }
			@Override Document next() { documentManager.getDocument(fileIterator.next()) }
			@Override void remove() { throw new UnsupportedOperationException() }
		}
	}

	/**
	 * @return lazy iterator for all {@link PsiFileSystemItem}s in project (in breadth-first order).
	 *         Note that iterator can return null elements.
	 */
	static Iterator<PsiFileSystemItem> allPsiItemsIn(@NotNull Project project) {
		def fileIterator = allFilesIn(project)
		def psiManager = PsiManager.getInstance(project)

		new Iterator<PsiFileSystemItem>() {
			@Override boolean hasNext() { fileIterator.hasNext() }
			@Override PsiFileSystemItem next() {
				def file = fileIterator.next()
				def psiItem = file.isDirectory() ? psiManager.findDirectory(file) : psiManager.findFile(file)
				psiItem
			}
			@Override void remove() { throw new UnsupportedOperationException() }
		}
	}

	/**
	 * Iterates over all files in project (in breadth-first order).
	 *
	 * @param callback code to be executed for each file.
	 *        Passes in as parameters {@link VirtualFile}, {@link Document}, {@link PsiFileSystemItem}.
	 */
	static forAllFilesIn(@NotNull Project project, Closure callback) {
		def documentManager = FileDocumentManager.instance
		def psiManager = PsiManager.getInstance(project)

		def filesIterator = allFilesIn(project)
		for (VirtualFile file in filesIterator) {
			def document = documentManager.getDocument(file)
			def psiItem = (file.isDirectory() ? psiManager.findDirectory(file) : psiManager.findFile(file))
			callback.call(file, document, psiItem)
		}
	}

	/**
	 * Executes callback as write action ensuring that it's runs in Swing event-dispatch thread.
	 * For details see javadoc {@link com.intellij.openapi.application.Application}
	 * (https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/application/Application.java)
	 *
	 * @param callback code to execute
	 * @return result of callback
	 */
	@CanCallFromAnyThread
	static runWriteAction(Closure callback) {
		if (SwingUtilities.isEventDispatchThread()) {
			ApplicationManager.application.runWriteAction(callback as Computable)
		} else {
			SwingUtilities.invokeAndWait {
				ApplicationManager.application.runWriteAction(callback as Computable)
			}
		}
	}

	/**
	 * Executes callback as read action.
	 * This is only required if IntelliJ data structures are accessed NOT from Swing event-dispatch thread.
	 * For details see javadoc {@link com.intellij.openapi.application.Application}
	 * (https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/application/Application.java)
	 *
	 * @param callback code to execute
	 * @return result of callback
	 */
	@CanCallFromAnyThread
	static runReadAction(Closure callback) {
		ApplicationManager.application.runReadAction(callback as Computable)
	}

	/**
	 * TODO
	 *
	 * @param document
	 * @param callback
	 */
	@CanCallFromAnyThread
	static runDocumentWriteAction(@NotNull Project project, Document document = currentDocumentIn(project), Closure callback) {
		def name = "runDocumentWriteAction"
		def groupId = "PluginUtil"
		runWriteAction {
			CommandProcessor.instance.executeCommand(project, {
				callback.call(document)
			}, name, groupId, UndoConfirmationPolicy.DEFAULT, document)
		}
	}

	/**
	 * TODO
	 *
	 * @param project
	 * @param transformer
	 */
	@CanCallFromAnyThread
	static transformSelectedText(@NotNull Project project, Closure transformer) {
		def editor = currentEditorIn(project)
		runDocumentWriteAction(project) {
			transformSelectionIn(editor, transformer)
		}
	}

	/**
	 * Allows to store value on application level sharing it between plugin reloads.
	 *
	 * Note that static fields will NOT keep data because new classloader is created on each plugin reload.
	 * {@link com.intellij.openapi.util.UserDataHolder} won't work as well because {@link com.intellij.openapi.util.Key}
	 * implementation uses incremental numbers as hashCode() (each "new Key()" is different from previous one).
	 *
	 * @param varName
	 * @param initialValue
	 * @param callback should calculate new value given previous one
	 * @return new value
	 */
	@Nullable static <T> T changeGlobalVar(String varName, @Nullable initialValue = null, Closure callback) {
		def actionManager = ActionManager.instance
		def action = actionManager.getAction(asActionId(varName))

		def prevValue = (action == null ? initialValue : action.value)
		T newValue = (T) callback.call(prevValue)

		// unregister action only after callback has been invoked in case it crashes
		if (action != null) actionManager.unregisterAction(asActionId(varName))

		// anonymous class below will keep reference to outer object but that should be ok
		// because its class is not a part of reloadable plugin
		actionManager.registerAction(asActionId(varName), new AnAction() {
			final def value = newValue
			@Override void actionPerformed(AnActionEvent e) {}
		})

		newValue
	}

	@Nullable static <T> T setGlobalVar(String varName, @Nullable varValue) {
		changeGlobalVar(varName){ varValue }
	}

	@Nullable static <T> T getGlobalVar(String varName) {
		def action = ActionManager.instance.getAction(asActionId(varName))
		action == null ? null : action.value
	}

	@Nullable static <T> T removeGlobalVar(String varName) {
		def action = ActionManager.instance.getAction(asActionId(varName))
		if (action == null) return null
		ActionManager.instance.unregisterAction(asActionId(varName))
		action.value
	}

	/**
	 * TODO
	 *
	 */
	static doInBackground(String taskDescription = "", boolean canBeCancelled = true,
	                      PerformInBackgroundOption backgroundOption = ALWAYS_BACKGROUND,
	                      Closure task, Closure whenCancelled = {}, Closure whenDone) {
		AtomicReference result = new AtomicReference(null)
		new Task.Backgroundable(null, taskDescription, canBeCancelled, backgroundOption) {
			@Override void run(ProgressIndicator indicator) { result.set(task.call(indicator)) }
			@Override void onSuccess() { whenDone.call(result.get()) }
			@Override void onCancel() { whenCancelled.call() }

		}.queue()
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

	static registerInMetaClassesContextOf(AnActionEvent actionEvent, List metaClasses = [Object.metaClass],
	                                      Map contextKeys = ["project": PlatformDataKeys.PROJECT]) {
		metaClasses.each { aMetaClass ->
			contextKeys.each { entry ->
				aMetaClass."${entry.key}" = actionEvent.getData(entry.value as DataKey)
			}
		}
	}

	static catchingAll(Closure closure) {
		try {

			closure.call()

		} catch (Exception e) {
			ProjectManager.instance.openProjects.each { Project project ->
				showInConsole(e, e.class.simpleName, project)
			}
		}
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

	private static ToolWindow registerToolWindowIn(Project project, String id, JComponent component, ToolWindowAnchor location = RIGHT) {
		def manager = ToolWindowManager.getInstance(project)

		if (manager.getToolWindow(id) != null) {
			manager.unregisterToolWindow(id)
		}

		def toolWindow = manager.registerToolWindow(id, false, location)
		def content = ContentFactory.SERVICE.instance.createContent(component, "", false)
		toolWindow.contentManager.addContent(content)
		toolWindow
	}

	private static unregisterToolWindowIn(Project project, String id) {
		ToolWindowManager.getInstance(project).unregisterToolWindow(id)
	}

	private static ConsoleViewContentType guessContentTypeOf(text) {
		text instanceof Throwable ? ConsoleViewContentType.ERROR_OUTPUT : ConsoleViewContentType.NORMAL_OUTPUT
	}

	private static asActionId(String globalVarKey) {
		"IntelliJEval-" + globalVarKey
	}

	static String asString(@Nullable message) {
		if (message?.getClass()?.isArray()) return Arrays.toString(message)
		if (message instanceof MapWithDefault) return "{" + message.entrySet().join(", ") + "}"
		String.valueOf(message)
	}
	
	/**
	 * Original version borrowed from here
	 * http://code.google.com/p/idea-string-manip/source/browse/trunk/src/main/java/osmedile/intellij/stringmanip/AbstractStringManipAction.java
	 *
	 * @author Olivier Smedile
	 */
	private static transformSelectionIn(Editor editor, Closure transformer) {
		SelectionModel selectionModel = editor.selectionModel
		String selectedText = selectionModel.selectedText

		boolean allLineSelected = false
		if (selectedText == null) {
			selectionModel.selectLineAtCaret()
			selectedText = selectionModel.selectedText
			allLineSelected = true

			if (selectedText == null) return
		}
		String[] textParts = selectedText.split("\n")

		if (editor.columnMode) { // TODO doesn't work properly
			int[] blockStarts = selectionModel.blockSelectionStarts
			int[] blockEnds = selectionModel.blockSelectionEnds

			int plusOffset = 0

			for (int i = 0; i < textParts.length; i++) {
				String newTextPart = transformer(textParts[i])
				if (allLineSelected) {
					newTextPart += "\n"
				}

				editor.document.replaceString(blockStarts[i] + plusOffset, blockEnds[i] + plusOffset, newTextPart)
				plusOffset += newTextPart.length() - textParts[i].length()
			}
		} else {
			String transformedText = textParts.collect{ transformer(it) }.join("\n")
			editor.document.replaceString(selectionModel.selectionStart, selectionModel.selectionEnd, transformedText)
			if (allLineSelected) {
				editor.document.insertString(selectionModel.selectionEnd, "\n")
			}
		}
	}

	private static class MyConsolePanel extends JPanel {
		MyConsolePanel(ExecutionConsole consoleView, ActionGroup toolbarActions) {
			super(new BorderLayout())
			def toolbarPanel = new JPanel(new BorderLayout())
			toolbarPanel.add(ActionManager.instance.createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions, false).component)
			add(toolbarPanel, BorderLayout.WEST)
			add(consoleView.component, BorderLayout.CENTER)
		}
	}

	// Can't use anonymous class because of ClassCastException like in this bug http://jira.codehaus.org/browse/GROOVY-5101
	private static class ConsoleCloseAction extends CloseAction {
		private final ConsoleView consoleView

		ConsoleCloseAction(ConsoleView consoleView, Executor executor, RunContentDescriptor contentDescriptor, Project project) {
			super(executor, contentDescriptor, project)
			this.consoleView = consoleView
		}

		@Override void actionPerformed(AnActionEvent event) {
			super.actionPerformed(event)
			consoleToConsoleTitle.remove(consoleView)
		}
	}


	private static final Logger LOG = Logger.getInstance("IntelliJEval")

	// Using WeakHashMap to make unregistering tool window optional
	private static final Map<ProjectManagerListener, String> pmListenerToToolWindowId = new WeakHashMap()
	// thread-confined to EDT
	private static final Map<ConsoleView, String> consoleToConsoleTitle = new HashMap()
	
	@Test void "asString() should convert to string values of any type"() {
		assert asString(null) == "null"

		assert asString(1) == "1"

		assert asString([] as Integer[]) == "[]"
		assert asString([1] as Integer[]) == "[1]"

		assert asString([]) == "[]"
		assert asString([1, 2, 3]) == "[1, 2, 3]"

		assert asString([:]) == "{}"
		assert asString([a: 1]) == "{a=1}"
		assert asString([:].withDefault { 0 }) == "{}"
		assert asString([a: 1].withDefault { 0 }) == "{a=1}"
	}
}

@interface CanCallFromAnyThread {}
@interface CanOnlyCallFromEDT {}