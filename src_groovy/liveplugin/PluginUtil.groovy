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
package liveplugin

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.codeInspection.InspectionProfileEntry
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
import com.intellij.internal.psiView.PsiViewerDialog
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.compiler.CompilationStatusAdapter
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerAdapter
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.ProjectScope
import com.intellij.testFramework.MapDataContext
import com.intellij.ui.content.ContentFactory
import com.intellij.unscramble.UnscrambleDialog
import com.intellij.util.IncorrectOperationException
import liveplugin.implementation.*
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import javax.swing.*
import java.awt.*
import java.util.List
import java.util.concurrent.atomic.AtomicReference

import static com.intellij.notification.NotificationType.*
import static com.intellij.openapi.progress.PerformInBackgroundOption.ALWAYS_BACKGROUND
import static com.intellij.openapi.wm.ToolWindowAnchor.RIGHT

/**
 * Contains a bunch of utility methods on top of IntelliJ API.
 * Some of them might be very simple and exist only for reference.
 *
 * See also http://confluence.jetbrains.com/display/IDEADEV/IntelliJ+IDEA+Architectural+Overview
 */
@SuppressWarnings(["GroovyUnusedDeclaration", "UnnecessaryQualifiedReference"])
class PluginUtil {

	@CanCallFromAnyThread
	static <T> T invokeOnEDT(Closure closure) {
		def result = null
		ApplicationManager.application.invokeAndWait(new Runnable() {
			@Override void run() {
				//noinspection GrReassignedInClosureLocalVar
				result = closure()
			}
		}, ModalityState.any())
		(T) result
	}

	@CanCallFromAnyThread
	static void invokeLaterOnEDT(Closure closure) {
		ApplicationManager.application.invokeLater(new Runnable() {
			@Override void run() {
				closure()
			}
		})
	}

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
	static show(@Nullable message, @Nullable String title = "",
	            NotificationType notificationType = INFORMATION, String groupDisplayId = "") {
		invokeLaterOnEDT {
			message = asString(message)
			// this is because Notification doesn't accept empty messages
			if (message.trim().empty) message = "[empty message]"

			def notification = new Notification(groupDisplayId, title, message, notificationType)
			ApplicationManager.application.messageBus.syncPublisher(Notifications.TOPIC).notify(notification)
		}
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
	static ConsoleView showInConsole(@Nullable message, String consoleTitle = "", @NotNull Project project,
	                                 ConsoleViewContentType contentType = guessContentTypeOf(message)) {
		AtomicReference<ConsoleView> result = new AtomicReference(null)
		// Use reference for consoleTitle because get groovy Reference class like in this bug http://jira.codehaus.org/browse/GROOVY-5101
		AtomicReference<String> titleRef = new AtomicReference(consoleTitle)

		invokeOnEDT {
			ConsoleView console = TextConsoleBuilderFactory.instance.createBuilder(project).console
			console.print(asString(message), contentType)

			DefaultActionGroup toolbarActions = new DefaultActionGroup()
			def consoleComponent = new MyConsolePanel(console, toolbarActions)
			RunContentDescriptor descriptor = new RunContentDescriptor(console, null, consoleComponent, titleRef.get()) {
				@Override boolean isContentReuseProhibited() { true }
				@Override Icon getIcon() { AllIcons.Nodes.Plugin }
			}
			Executor executor = DefaultRunExecutor.runExecutorInstance

			toolbarActions.add(new CloseAction(executor, descriptor, project))
			console.createConsoleActions().each{ toolbarActions.add(it) }

			ExecutionManager.getInstance(project).contentManager.showRunContent(executor, descriptor)
			result.set(console)
		}
		result.get()
	}

	/**
	 * Action group id for Main Menu -> Tools.
	 * Can be used in {@link #registerAction(java.lang.String, com.intellij.openapi.actionSystem.AnAction)}.
	 *
	 * The only reason to have it here is that there is no constant for it in IntelliJ source code.
	 */
	static String TOOLS_MENU = "ToolsMenu"

	/**
	 * Registers action in IDE.
	 * If there is already an action with {@code actionId}, it will be replaced.
	 * (The main reason to replace action is to be able to incrementally add code to it without restarting IDE.)
	 *
	 * @param actionId unique identifier for action
	 * @param keyStroke (optional) e.g. "ctrl alt shift H" or "alt C, alt H" for double key stroke;
	 *        on OSX "meta" means "command" button. Note that letters must be uppercase, modification keys lowercase.
	 *        See {@link javax.swing.KeyStroke#getKeyStroke(String)}
	 * @param actionGroupId (optional) can be used to add actions to existing menus, etc.
	 *                      (e.g. "ToolsMenu" corresponds to main menu "Tools")
	 *                      The best way to find existing actionGroupIds is probably to search IntelliJ source code for "group id=".
	 * @param displayText (optional) if action is added to menu, this text will be shown
	 * @param callback code to run when action is invoked. {@link AnActionEvent} will be passed as a parameter.
	 *
	 * @return instance of created action
	 */
	@CanCallFromAnyThread
	static AnAction registerAction(String actionId, String keyStroke = "",
	                               String actionGroupId = null, String displayText = actionId, Closure callback) {
		registerAction(actionId, keyStroke, actionGroupId, displayText, new AnAction() {
			@Override void actionPerformed(AnActionEvent event) { callback(event) }
		})
	}

	@CanCallFromAnyThread
	static AnAction registerAction(String actionId, String keyStroke = "",
	                               String actionGroupId = null, String displayText = actionId, AnAction action) {
		assertNoNeedForEdtOrWriteActionWhenUsingActionManager()

		def actionManager = ActionManager.instance
		def actionGroup = findActionGroup(actionGroupId)

		def alreadyRegistered = (actionManager.getAction(actionId) != null)
		if (alreadyRegistered) {
			actionGroup?.remove(actionManager.getAction(actionId))
			actionManager.unregisterAction(actionId)
		}

		assignKeyStrokeTo(actionId, keyStroke)
		actionManager.registerAction(actionId, action)
		actionGroup?.add(action)
		action.templatePresentation.setText(displayText, true)

		log("Action '${actionId}' registered")

		action
	}

	@CanCallFromAnyThread
	static IntentionAction registerIntention(String intentionId, String text = intentionId,
	                                         String familyName = text, Closure callback) {
		def intention = new IntentionAction() {
			@Override void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
				callback.call([checkAvailability: false, project: project, editor: editor, file: file])
			}

			@Override boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
				callback.call([checkAvailability: true, project: project, editor: editor, file: file])
			}

			@Override boolean startInWriteAction() { false }
			@Override String getFamilyName() { familyName }
			@Override String getText() { text }
		}
		registerIntention(intentionId, intention)
	}

	@CanCallFromAnyThread
	static IntentionAction registerIntention(String intentionId, IntentionAction intention) {
		runWriteAction {
			changeGlobalVar(intentionId) { IntentionAction oldIntention ->
				if (oldIntention != null) {
					IntentionManager.instance.unregisterIntention(oldIntention)
				}
				IntentionManager.instance.addAction(intention)
				intention
			}
		}
	}

	@CanCallFromAnyThread
	static IntentionAction unregisterIntention(String intentionId) {
		runWriteAction {
			changeGlobalVar(intentionId) { IntentionAction oldIntention ->
				if (oldIntention != null) {
					IntentionManager.instance.unregisterIntention(oldIntention)
				}
			}
		}
	}

	@CanCallFromAnyThread
	static registerInspection(Project project, InspectionProfileEntry inspection) {
		runWriteAction {
			Inspections.registerInspection(project, inspection)
		}
	}

	@CanCallFromAnyThread
	static unregisterInspection(Project project, InspectionProfileEntry inspection) {
		runWriteAction {
			Inspections.unregisterInspection(project, inspection)
		}
	}

	/**
	 * @param searchString string which is contained in action id, text or class name
	 * @return collection of tuples (arrays) in the form of [id, action instance]
	 */
	@CanCallFromAnyThread
	static Collection findAllActions(String searchString) {
		ActionSearch.findAllActions(searchString)
	}

	/**
	 * Wraps action if it's not already wrapped.
	 *
	 * @param actionId id of action to wrap
	 * @param actionGroupIds (optional) action groups ids in which action is registered;
	 *        can be used to update actions in menus, etc. (this is needed because action groups reference actions directly)
	 * @param callback will be invoked instead of wrapped action;
	 *        {@link ActionWrapper.Context} is passed as parameter
	 * @return wrapped action or null if there are no actions for {@code actionId}
	 */
	@CanCallFromAnyThread
	static AnAction wrapAction(String actionId, List<String> actionGroupIds = [], Closure callback) {
		assertNoNeedForEdtOrWriteActionWhenUsingActionManager()
		ActionWrapper.wrapAction(actionId, actionGroupIds, callback)
	}

	/**
	 * Wraps action even it's already wrapped.
	 *
	 * @see #wrapAction(java.lang.String, java.util.List, groovy.lang.Closure)
	 */
	@CanCallFromAnyThread
	static AnAction doWrapAction(String actionId, List<String> actionGroupIds = [], Closure callback) {
		assertNoNeedForEdtOrWriteActionWhenUsingActionManager()
		ActionWrapper.doWrapAction(actionId, actionGroupIds, callback)
	}

	/**
	 * Removes one wrapper around action.
	 *
	 * @param actionId id of action to unwrap
	 * @param actionGroupIds (optional) action groups ids in which action is registered;
	 *        can be used to update actions in menus, etc. (this is needed because action groups reference actions directly)
	 * @return unwrapped action or null if there are no actions for {@code actionId}
	 */
	@CanCallFromAnyThread
	static AnAction unwrapAction(String actionId, List<String> actionGroupIds = []) {
		assertNoNeedForEdtOrWriteActionWhenUsingActionManager()
		ActionWrapper.unwrapAction(actionId, actionGroupIds)
	}

	/**
	 * Removes all wrappers around action.
	 *
	 * @see #unwrapAction(java.lang.String, java.util.List)
	 */
	@CanCallFromAnyThread
	static AnAction doUnwrapAction(String actionId, List<String> actionGroupIds = []) {
		assertNoNeedForEdtOrWriteActionWhenUsingActionManager()
		ActionWrapper.doUnwrapAction(actionId, actionGroupIds)
	}

	/**
	 * Registers project manager listener which will be replaced between plugin reloads.
	 *
	 * @param listenerId unique id of this project manager listener
	 * @param listener see https://github.com/JetBrains/intellij-community/blob/master/platform/projectModel-api/src/com/intellij/openapi/project/ProjectManagerListener.java
	 */
	@CanCallWithinRunReadActionOrFromEDT
	static def registerProjectListener(String listenerId, ProjectManagerListener listener) {
		Disposable disposable = (Disposable) changeGlobalVar(listenerId) { Disposable previousDisposable ->
			if (previousDisposable != null) Disposer.dispose(previousDisposable)
			new Disposable() {
				@Override void dispose() {}
			}
		}
		ProjectManager.instance.addProjectManagerListener(listener, disposable)
	}

	/**
	 * Registers a tool window in all open IDE windows.
	 * If there is already a tool window with {@code toolWindowId}, it will be replaced.
	 *
	 * @param toolWindowId unique identifier for tool window
	 * @param location (optional) see https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/openapi/wm/ToolWindowAnchor.java
	 * @param createComponent closure that creates tool window content (will be called for each open project)
	 */
	@CanCallFromAnyThread
	static registerToolWindow(String toolWindowId, ToolWindowAnchor location = RIGHT, Closure<JComponent> createComponent) {
		invokeOnEDT {
			runWriteAction {
				def previousListener = pmListenerToToolWindowId.find{ it == toolWindowId }?.key
				if (previousListener != null) {
					ProjectManager.instance.removeProjectManagerListener(previousListener)
					pmListenerToToolWindowId.remove(previousListener)
				}

				def listener = new ProjectManagerAdapter() {
					@Override void projectOpened(Project project) { registerToolWindowIn(project, toolWindowId, createComponent(), location) }
					@Override void projectClosed(Project project) { unregisterToolWindowIn(project, toolWindowId) }
				}
				pmListenerToToolWindowId[listener] = toolWindowId
				ProjectManager.instance.addProjectManagerListener(listener)

				ProjectManager.instance.openProjects.each { project -> registerToolWindowIn(project, toolWindowId, createComponent(), location) }

				log("Toolwindow '${toolWindowId}' registered")
			}
		}
	}

	/**
	 * Unregisters a tool window from all open IDE windows.
	 */
  @CanCallFromAnyThread
	static unregisterToolWindow(String toolWindowId) {
	  invokeOnEDT {
		  runWriteAction {
				def previousListener = pmListenerToToolWindowId.find{ it == toolWindowId }?.key
				if (previousListener != null) {
					ProjectManager.instance.removeProjectManagerListener(previousListener)
					pmListenerToToolWindowId.remove(previousListener)
				}

				ProjectManager.instance.openProjects.each { project -> unregisterToolWindowIn(project, toolWindowId) }
		  }
	  }
	}

	static ToolWindow registerToolWindowIn(Project project, String toolWindowId, JComponent component, ToolWindowAnchor location = RIGHT) {
		def manager = ToolWindowManager.getInstance(project)

		if (manager.getToolWindow(toolWindowId) != null) {
			manager.unregisterToolWindow(toolWindowId)
		}

		def toolWindow = manager.registerToolWindow(toolWindowId, false, location)
		def content = ContentFactory.SERVICE.instance.createContent(component, "", false)
		toolWindow.contentManager.addContent(content)
		toolWindow
	}

	static unregisterToolWindowIn(Project project, String toolWindowId) {
		ToolWindowManager.getInstance(project).unregisterToolWindow(toolWindowId)
	}

	@CanCallFromAnyThread
	static void inspect(Object object) {
		invokeOnEDT {
			ObjectInspector.inspect(object)
		}
	}

	@CanCallFromAnyThread
	static showPsiDialog(@NotNull Project project) {
		invokeOnEDT {
			new PsiViewerDialog(project, false, null, null).show()
		}
	}

	/**
	 * This method exists for reference only.
	 * For more dialogs see https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/openapi/ui/Messages.java
	 */
	@CanCallWithinRunReadActionOrFromEDT
	@Nullable static String showInputDialog(String message, String title, @Nullable Icon icon = null) {
		Messages.showInputDialog(message, title, icon)
	}

	/**
	 * @return currently open editor; null if there are no open editors
	 */
	@CanCallWithinRunReadActionOrFromEDT
	@Nullable static Editor currentEditorIn(@NotNull Project project) {
		((FileEditorManagerEx) FileEditorManagerEx.getInstance(project)).selectedTextEditor
	}

	/**
	 * @return editor which is visible but not active
	 * (this is the case when windows is split or editor is dragged out of project frame)
	 *
	 * It is intended to be used while writing plugin code which modifies content of another open editor.
	 */
	@CanCallWithinRunReadActionOrFromEDT
	@NotNull static Editor anotherOpenEditorIn(@NotNull Project project) {
		((FileEditorManagerEx) FileEditorManagerEx.getInstance(project)).with {
			if (selectedTextEditor == null) throw new IllegalStateException("There are no open editors in " + project.name)
			def editors = selectedEditors
					.findAll{it instanceof TextEditor}
					.collect{(Editor) it.editor}
					.findAll{it != selectedTextEditor}
			if (editors.size() == 0) throw new IllegalStateException("There is only one open editor in " + project.name)
			if (editors.size() > 1) throw new IllegalStateException("There are more than 2 open editors in " + project.name)
			editors.first()
		}
	}

	/**
	 * @return {@PsiFile} for opened editor tab; null if there are no open files
	 */
	@CanCallWithinRunReadActionOrFromEDT
	@Nullable static PsiFile currentPsiFileIn(@NotNull Project project) {
		psiFile(currentFileIn(project), project)
	}

	/**
	 * @return {@link Document} for opened editor tab; null if there are no open files
	 */
	@CanCallWithinRunReadActionOrFromEDT
	@Nullable static Document currentDocumentIn(@NotNull Project project) {
		document(currentFileIn(project))
	}

	/**
	 * @return {@link VirtualFile} for opened editor tab; null if there are no open files
	 */
	@CanCallWithinRunReadActionOrFromEDT
	@Nullable static VirtualFile currentFileIn(@NotNull Project project) {
		((FileEditorManagerEx) FileEditorManagerEx.getInstance(project)).currentFile
	}

	// TODO use category?
	@Nullable static Document document(@Nullable PsiFile psiFile) {
		document(psiFile?.virtualFile)
	}

	@Nullable static PsiFile psiFile(@Nullable VirtualFile file, @NotNull Project project) {
		file == null ? null : PsiManager.getInstance(project).findFile(file)
	}

	@Nullable static Document document(@Nullable VirtualFile file) {
		file == null ? null : FileDocumentManager.instance.getDocument(file)
	}

	@Nullable static PsiFile psiFile(@Nullable Editor editor, @NotNull Project project) {
		psiFile(virtualFile(editor), project)
	}

	@Nullable static VirtualFile virtualFile(@Nullable Editor editor) {
		if (editor == null || !(editor instanceof EditorEx)) null
		else ((EditorEx) editor).virtualFile
	}

	/**
	 * @return all {@link VirtualFile}s in project
	 */
	static Collection<VirtualFile> allFilesIn(@NotNull Project project) {
		def result = []
		def projectScope = ProjectScope.getAllScope(project)
		ProjectRootManager.getInstance(project).fileIndex.iterateContent(new ContentIterator() {
			@Override boolean processFile(VirtualFile fileOrDir) {
				if (projectScope.contains(fileOrDir)) result.add(fileOrDir)
				true
			}
		})
		result
	}

	/**
	 * @return all {@link Document}s in project.
	 *         Note that some {@link VirtualFile}s might not have {@link Document},
	 *         so result of this method might have fewer elements than {@link #allFilesIn}.
	 */
	static Collection<Document> allDocumentsIn(@NotNull Project project) {
		def documentManager = FileDocumentManager.instance
		allFilesIn(project).findResults { VirtualFile file ->
			documentManager.getDocument(file)
		}
	}

	/**
	 * @return all {@link PsiFileSystemItem}s in project.
	 *         Note that some {@link VirtualFile}s might not have {@link PsiFileSystemItem},
	 *         so result of this method might have fewer elements than {@link #allFilesIn}.
	 */
	static Collection<PsiFileSystemItem> allPsiItemsIn(@NotNull Project project) {
		def psiManager = PsiManager.getInstance(project)
		allFilesIn(project).findResults { VirtualFile file ->
			file.isDirectory() ? psiManager.findDirectory(file) : psiManager.findFile(file)
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

		for (VirtualFile file in allFilesIn(project)) {
			def document = documentManager.getDocument(file)
			def psiItem = (file.isDirectory() ? psiManager.findDirectory(file) : psiManager.findFile(file))
			callback.call(file, document, psiItem)
		}
	}


	static List<PsiFileSystemItem> filesByName(String name, @NotNull Project project, boolean searchInLibraries = false) {
		def scope = searchInLibraries? ProjectScope.getAllScope(project) : ProjectScope.getProjectScope(project)
		FilenameIndex.getFilesByName(project, name, scope).toList()
	}

	static List<VirtualFile> sourceRootsIn(Project project) {
		ProjectRootManager.getInstance(project).contentSourceRoots.toList()
	}

	static List<VcsRoot> vcsRootsIn(Project project) {
		ProjectRootManager.getInstance(project).contentSourceRoots
				.collect{ ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(it) }
				.findAll{ it.path != null }.unique()
	}

	static void addCompilationListener(String id, Project project, CompilationStatusAdapter listener) {
		Compilation.addCompilationListener(id, project, listener)
	}

	/**
	 * Executes callback as write action ensuring that it's run in Swing event-dispatch thread.
	 * For details see javadoc {@link com.intellij.openapi.application.Application}
	 * (https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/application/Application.java)
	 *
	 * @param callback code to execute
	 * @return result of callback
	 */
	@CanCallFromAnyThread
	static <T> T runWriteAction(Closure callback) {
		invokeOnEDT {
			ApplicationManager.application.runWriteAction(callback as Computable)
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
	static <T> T runReadAction(Closure callback) {
		ApplicationManager.application.runReadAction(callback as Computable)
	}


	/**
	 * Changes document so that modification is added to "Main menu - Edit - Undo/Redo".
	 * @see #runWriteAction(groovy.lang.Closure)
	 *
	 * @param callback takes {@link Document}
	 */
	@CanCallFromAnyThread
	static runDocumentWriteAction(@NotNull Project project, Document document = currentDocumentIn(project),
	                              String modificationName = "runDocumentWriteAction", String modificationGroup = "LivePlugin",
	                              Closure callback) {
		runWriteAction {
			CommandProcessor.instance.executeCommand(project, {
				callback.call(document)
			}, modificationName, modificationGroup, UndoConfirmationPolicy.DEFAULT, document)
		}
	}

	@CanCallWithinRunReadActionOrFromEDT
	static VirtualFile file(Document document) {
		FileDocumentManager.instance.getFile(document)
	}

	@CanCallWithinRunReadActionOrFromEDT
	static PsiFile psiFile(Document document, Project project) {
		PsiDocumentManager.getInstance(project).getPsiFile(document)
	}

	/**
	 * Transforms selected text in editor or current line if there is no selection.
	 *
	 * @param transformer takes selected text as {@link String}; should output new text that will replace selection
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
	 * @param callback receives single parameter with old value, should return new value
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

	@Nullable static <T> T getGlobalVar(String varName, @Nullable initialValue = null) {
		changeGlobalVar(varName, initialValue, {it})
	}

	@Nullable static <T> T removeGlobalVar(String varName) {
		def action = ActionManager.instance.getAction(asActionId(varName))
		if (action == null) return null
		ActionManager.instance.unregisterAction(asActionId(varName))
		action.value
	}

	@CanCallFromAnyThread
	static doInBackground(String taskDescription = "A task", boolean canBeCancelledByUser = true,
	                      PerformInBackgroundOption backgroundOption = ALWAYS_BACKGROUND,
	                      Closure task, Closure whenCancelled = {}, Closure whenDone = {}) {
		AtomicReference result = new AtomicReference(null)
		new Task.Backgroundable(null, taskDescription, canBeCancelledByUser, backgroundOption) {
			@Override void run(ProgressIndicator indicator) { result.set(task.call(indicator)) }
			@Override void onSuccess() { whenDone.call(result.get()) }
			@Override void onCancel() { whenCancelled.call() }
		}.queue()
	}

	@CanCallFromAnyThread
	static doInModalMode(String taskDescription = "A task", boolean canBeCancelledByUser = true, Closure task) {
		new Task.Modal(null, taskDescription, canBeCancelledByUser) {
			@Override void run(ProgressIndicator indicator) { task.call(indicator) }
		}.queue()
	}

	/**
	 * @param description map that represents a tree of actions.
     *                   Entry keys will be used as text presentation of items.
     *                   Entry values can be map, closure or {@link AnAction}
	 *                   Map is interpreter as description.
	 *                   Close is a callback which takes one parameter with "key" and "event" attributes.
	 * @param actionGroup (optional) action group to which actions will be added
	 * @return actionGroup with actions
	 */
	static ActionGroup createNestedActionGroup(Map description, actionGroup = new DefaultActionGroup()) {
		description.each { entry ->
			if (entry.value instanceof Closure) {
				actionGroup.add(new AnAction(entry.key.toString()) {
					@Override void actionPerformed(AnActionEvent event) {
						entry.value.call([key: entry.key, event: event])
					}
				})
			} else if (entry.value instanceof Map) {
				Map subMenuDescription = entry.value as Map
				def actionGroupName = entry.key.toString()
				def isPopup = true
				actionGroup.add(createNestedActionGroup(subMenuDescription, new DefaultActionGroup(actionGroupName, isPopup)))
			} else if (entry.value instanceof AnAction) {
				actionGroup.add(entry.value)
			}
		}
		actionGroup
	}

	/**
	 * Shows popup menu in which each leaf item is associated with action.
	 *
	 * @param menuDescription see javadoc for {@link #createNestedActionGroup(java.util.Map)}
	 * @param popupTitle (optional)
	 */
	static showPopupMenu(Map menuDescription, String popupTitle = "") {
		def dummyDataContext = new MapDataContext()
        def dummyComponent = new JPanel()
        dummyDataContext.put(PlatformDataKeys.CONTEXT_COMPONENT, dummyComponent)
        JBPopupFactory.instance.createActionGroupPopup(
				popupTitle,
				createNestedActionGroup(menuDescription),
				dummyDataContext,
				JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
				true
		).showInFocusCenter()
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

	static Map execute(String fullCommand) {
		fullCommand.split(" ").toList().with { execute(it.head(), it.tail()) }
	}

	static Map execute(String command, String parameters) {
		execute(command, parameters.split(" ").toList())
	}

	static Map execute(String command, Collection<String> parameters) {
		def ant = new AntBuilder()
		ant.exec(outputproperty:"cmdOut",
				errorproperty: "cmdErr",
				resultproperty:"cmdExit",
				failonerror: "false",
				executable: command) {
			arg(line: parameters.join(" "))
		}

		[exitCode: Integer.parseInt(ant.project.properties.cmdExit),
		 stderr: ant.project.properties.cmdErr,
		 stdout: ant.project.properties.cmdOut]
	}

	@Nullable static <T> T catchingAll(Closure<T> closure) {
		try {

			closure.call()

		} catch (Exception e) {
			ProjectManager.instance.openProjects.each { Project project ->
				showInConsole(e, e.class.simpleName, project)
			}
			null
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

	private static void assignKeyStrokeTo(String actionId, String keyStroke) {
		def keymap = KeymapManager.instance.activeKeymap
		keymap.removeAllActionShortcuts(actionId)
		def shortcut = asKeyboardShortcut(keyStroke)
		if (shortcut != null) {
			keymap.addShortcut(actionId, shortcut)
		}
	}

	static KeyboardShortcut asKeyboardShortcut(String keyStroke) {
		if (keyStroke.trim().empty) return null

		def firstKeystroke
		def secondsKeystroke = null
		if (keyStroke.contains(",")) {
			firstKeystroke = KeyStroke.getKeyStroke(keyStroke[0..<keyStroke.indexOf(",")].trim())
			secondsKeystroke = KeyStroke.getKeyStroke(keyStroke[(keyStroke.indexOf(",") + 1)..-1].trim())
		} else {
			firstKeystroke = KeyStroke.getKeyStroke(keyStroke)
		}
		if (firstKeystroke == null) throw new IllegalStateException("Invalid keystroke '$keyStroke'")
		new KeyboardShortcut(firstKeystroke, secondsKeystroke)
	}

	private static ConsoleViewContentType guessContentTypeOf(text) {
		text instanceof Throwable ? ConsoleViewContentType.ERROR_OUTPUT : ConsoleViewContentType.NORMAL_OUTPUT
	}

	private static String asActionId(String globalVarKey) {
		"LivePlugin-" + globalVarKey
	}


	static String asString(@Nullable message) {
		if (message?.getClass()?.isArray()) Arrays.toString(message)
		else if (message instanceof MapWithDefault) "{" + message.entrySet().join(", ") + "}"
		else if (message instanceof Throwable) {
			def writer = new StringWriter()
			message.printStackTrace(new PrintWriter(writer))
			UnscrambleDialog.normalizeText(writer.buffer.toString())
		} else {
			String.valueOf(message)
		}
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

		if (selectionModel.hasBlockSelection()) { 
			int[] blockStarts = selectionModel.blockSelectionStarts
			int[] blockEnds = selectionModel.blockSelectionEnds

			int plusOffset = 0

			for (int i = 0; i < textParts.length; i++) {
				String newTextPart = transformer(textParts[i])
				if (allLineSelected) {
					newTextPart += "\n"
				}

				editor.document.replaceString(blockStarts[i] + plusOffset, blockEnds[i] + plusOffset, newTextPart)

				def realTextLength = blockEnds[i] - blockStarts[i]
				plusOffset += newTextPart.length() - realTextLength
			}
		} else {
			String transformedText = textParts.collect{ transformer(it) }.join("\n")
			editor.document.replaceString(selectionModel.selectionStart, selectionModel.selectionEnd, transformedText)
			if (allLineSelected) {
				editor.document.insertString(selectionModel.selectionEnd, "\n")
			}
		}
	}

	private static void assertNoNeedForEdtOrWriteActionWhenUsingActionManager() {
		// Dummy method to document that there is no need for EDT or writeAction since ActionManager uses its own internal lock.
		// Obviously, to be properly "thread-safe" group of operations on ActionManager should be atomic,
		// this is not the case. The assumption is that normally mini-plugins don't change the same actions and
		// using writeAction on IDE startup can cause deadlock, e.g. when running at the same time as ServiceManager:
		//    at com.intellij.openapi.application.impl.ApplicationImpl.getStateStore(ApplicationImpl.java:197)
		//    at com.intellij.openapi.application.impl.ApplicationImpl.initializeComponent(ApplicationImpl.java:205)
		//    at com.intellij.openapi.components.impl.ServiceManagerImpl$MyComponentAdapter.initializeInstance(ServiceManagerImpl.java:164)
		//    at com.intellij.openapi.components.impl.ServiceManagerImpl$MyComponentAdapter$1.compute(ServiceManagerImpl.java:147)
		//    at com.intellij.openapi.application.impl.ApplicationImpl.runReadAction(ApplicationImpl.java:932)
		//    at com.intellij.openapi.components.impl.ServiceManagerImpl$MyComponentAdapter.getComponentInstance(ServiceManagerImpl.java:139)
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

	static final Logger LOG = Logger.getInstance("LivePlugin")

	// thread-confined to EDT
	private static final Map<ProjectManagerListener, String> pmListenerToToolWindowId = new HashMap()
}

@interface CanCallFromAnyThread {}
@interface CanCallWithinRunReadActionOrFromEDT {}