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
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.ide.BrowserUtil
import com.intellij.internal.psiView.PsiViewerDialog
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.search.ProjectScope
import liveplugin.implementation.*
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import javax.swing.*
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

import static com.intellij.notification.NotificationType.*
import static com.intellij.openapi.progress.PerformInBackgroundOption.ALWAYS_BACKGROUND
import static com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid.SPEEDSEARCH
import static com.intellij.openapi.wm.ToolWindowAnchor.RIGHT
import static liveplugin.implementation.Misc.registerDisposable
import static liveplugin.implementation.Misc.unregisterDisposable
/**
 * This class contains a bunch of utility methods on top of IntelliJ API.
 * Some of them are very simple and were added only for reference.
 *
 * API of this class should be backward-compatible between major releases.
 *
 * If you are new to IntelliJ API,
 * see also http://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview.html
 */
@SuppressWarnings(["GroovyUnusedDeclaration", "UnnecessaryQualifiedReference"])
class PluginUtil {
	static final Logger LOG = Logger.getInstance("LivePlugin")

	/**
	 * Action group id for Main Menu -> Tools.
	 * Can be used in {@link #registerAction(java.lang.String, com.intellij.openapi.actionSystem.AnAction)}.
	 *
	 * The only reason to have it here is that there is no constant for it in IntelliJ source code.
	 */
	static final String TOOLS_MENU = "ToolsMenu"


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
			message = Misc.asString(message)
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
	static show(@Nullable message, @Nullable String title = "", NotificationType notificationType = INFORMATION,
	            String groupDisplayId = "", @Nullable NotificationListener notificationListener = null) {
		invokeLaterOnEDT {
			message = Misc.asString(message)
			// this is because Notification doesn't accept empty messages
			if (message.trim().empty) message = "[empty message]"

			def notification = new Notification(groupDisplayId, title, message, notificationType, notificationListener)
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
	                                 ConsoleViewContentType contentType = Console.guessContentTypeOf(message)) {
		Console.showInConsole(message, consoleTitle, project, contentType)
	}

	static registerConsoleListener(Disposable disposable, Closure callback) {
		Console.registerConsoleListener(disposable, callback)
	}

	static registerConsoleListener(String id, Closure callback) {
		registerConsoleListener(registerDisposable(id), callback)
	}

	static unregisterConsoleListener(String id) {
		unregisterDisposable(id)
	}


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
	static AnAction registerAction(String actionId, String keyStroke = "", String actionGroupId = null,
	                               String displayText = actionId, Disposable disposable = null, Closure callback) {
		assertNoNeedForEdtOrWriteActionWhenUsingActionManager()
		Actions.registerAction(actionId, keyStroke, actionGroupId, displayText, disposable, callback)
	}

	@CanCallFromAnyThread
	static AnAction registerAction(String actionId, String keyStroke = "", String actionGroupId = null,
	                               String displayText = actionId, Disposable disposable = null, AnAction action) {
		assertNoNeedForEdtOrWriteActionWhenUsingActionManager()
		Actions.registerAction(actionId, keyStroke, actionGroupId, displayText, disposable, action)
	}

	@CanCallFromAnyThread
	static assignKeyStroke(String actionId, String keyStroke, String macKeyStroke = keyStroke) {
		invokeOnEDT {
			Actions.assignKeyStroke(actionId, keyStroke, macKeyStroke)
		}
	}

	@CanCallFromAnyThread
	static unregisterAction(String actionId) {
		Actions.unregisterAction(actionId)
	}

	/**
	 * Can be used to invoke actions in code which doesn't have access to any valid {@code AnActionEvent}s.
	 * E.g. from between two different callbacks on EDT or from background thread.
	 */
	@CanCallFromAnyThread
	@NotNull static AnActionEvent anActionEvent(DataContext dataContext = Actions.dataContextFromFocus(),
	                              Presentation templatePresentation = new Presentation()) {
		Actions.anActionEvent(dataContext, templatePresentation)
	}

	@CanCallFromAnyThread
	@NotNull static MapDataContext newDataContext(Map map = [:]) {
		new MapDataContext(map)
	}

	@CanCallFromAnyThread
	static Collection<AnAction> allActions() {
		ActionSearch.allActions()
	}

	/**
	 * @param searchString string which is contained in action id, text or class name
	 * @return collection of matching actions
	 */
	@CanCallFromAnyThread
	static Collection<AnAction> findAllActions(String searchString) {
		ActionSearch.findAllActions(searchString)
	}

	@CanCallFromAnyThread
	@Nullable static AnAction actionById(String actionId) {
		ActionSearch.actionById(actionId)
	}

	/**
	 * Executes first "Run configuration" which matches {@code configurationName}.
	 */
	static executeRunConfiguration(@NotNull String configurationName, @NotNull Project project) {
		Actions.executeRunConfiguration(configurationName, project)
	}

	static runLivePlugin(@NotNull String pluginId, @NotNull Project project = currentProjectInFrame()) {
		Actions.runLivePlugin(pluginId, project)
	}

	static testLivePlugin(@NotNull String pluginId, @NotNull Project project = currentProjectInFrame()) {
		Actions.testLivePlugin(pluginId, project)
	}

	@CanCallFromAnyThread
	static IntentionAction registerIntention(Disposable disposable, String text = "",
	                                         String familyName = text, Closure callback) {
		runWriteAction {
			Intentions.registerIntention(disposable, text, familyName, callback)
		}
	}

	@CanCallFromAnyThread
	static IntentionAction registerIntention(Disposable disposable, IntentionAction intention) {
		runWriteAction {
			Intentions.registerIntention(disposable, intention)
		}
	}
	@CanCallFromAnyThread
	static IntentionAction registerIntention(String intentionId, String text = intentionId,
	                                         String familyName = text, Closure callback) {
		runWriteAction {
			Intentions.registerIntention(registerDisposable(intentionId), text, familyName, callback)
		}
	}

	@CanCallFromAnyThread
	static IntentionAction registerIntention(String intentionId, IntentionAction intention) {
		runWriteAction {
			Intentions.registerIntention(registerDisposable(intentionId), intention)
		}
	}

	@CanCallFromAnyThread
	static IntentionAction unregisterIntention(String intentionId) {
		runWriteAction {
			unregisterDisposable(intentionId)
		}
	}

	@CanCallFromAnyThread
	static registerInspection(@NotNull Disposable disposable, InspectionProfileEntry inspection) {
		runWriteAction {
			Inspections.registerInspection(disposable, inspection)
		}
	}

	@CanCallFromAnyThread
	static registerInspection(@NotNull Project project, InspectionProfileEntry inspection) {
		runWriteAction {
			Inspections.registerInspection(project, inspection)
		}
	}

	@CanCallFromAnyThread
	static unregisterInspection(@NotNull Project project, InspectionProfileEntry inspection) {
		runWriteAction {
			Inspections.unregisterInspection(project, inspection)
		}
	}

	@CanCallFromAnyThread
	static unregisterInspection(@NotNull Project project, String inspectionName) {
		runWriteAction {
			Inspections.unregisterInspection(project, inspectionName)
		}
	}

	/**
	 * Wraps action if it's not already wrapped.
	 *
	 * @param actionId id of action to wrap.
	 *        To find id of existing action you can try {@link liveplugin.PluginUtil#findAllActions(java.lang.String)}.
	 *        For some of built-in editor actions see https://github.com/JetBrains/intellij-community/blob/master/platform/platform-resources/src/idea/PlatformActions.xml#L50
	 *        And obviously, you can look at IntelliJ source code for implementations of {@link AnAction}.
	 * @param actionGroupIds (optional) action groups ids in which action is registered;
	 *        can be used to update actions in menus, etc. (this is needed because action groups reference actions directly)
	 * @param callback will be invoked instead of wrapped action,
	 *        it takes as arguments {@link AnActionEvent} representing current event
	 *        and {@link Closure} which will delegate to original action
	 *        (it can optionally take another {@link AnActionEvent} if the original one need to be substituted).
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
	 * @param parentDisposable disposable for this listener (can be "pluginDisposable" to remove listener on plugin reload)
	 */
	@CanCallFromAnyThread
	static AnActionListener registerActionListener(Disposable parentDisposable, AnActionListener actionListener) {
		Actions.registerActionListener(parentDisposable, actionListener)
	}

	@CanCallFromAnyThread
	static AnActionListener registerActionListener(String listenerId, AnActionListener actionListener) {
		Actions.registerActionListener(registerDisposable(listenerId), actionListener)
	}

	@CanCallFromAnyThread
	static void unregisterActionListener(String listenerId) {
		unregisterDisposable(listenerId)
	}


	/**
	 * @param disposable disposable to automatically unregister listener
	 *                   (e.g. "pluginDisposable" to remove listener on plugin reload)
	 * @param listener invoked for all open projects and on project open events
	 *                 (cleanup on project closed is supposed to be done through {@code disposable}
	 */
	@CanCallWithinRunReadActionOrFromEDT
	static def registerProjectListener(Disposable disposable, Closure listener) {
		Projects.registerProjectListener(disposable, listener)
	}

	/**
	 * Registers project manager listener which will be replaced between plugin reloads.
	 *
	 * @param parentDisposable disposable to automatically unregister this listener
	 *                         (e.g. "pluginDisposable" to remove listener on plugin reload)
	 * @param listener see https://github.com/JetBrains/intellij-community/blob/master/platform/projectModel-api/src/com/intellij/openapi/project/ProjectManagerListener.java
	 */
	@CanCallWithinRunReadActionOrFromEDT
	static def registerProjectListener(Disposable parentDisposable, ProjectManagerListener listener) {
		Projects.registerProjectListener(parentDisposable, listener)
	}

	@CanCallWithinRunReadActionOrFromEDT
	static def registerProjectListener(String id, Closure closure) {
		registerProjectListener(registerDisposable(id), closure)
	}

	@CanCallWithinRunReadActionOrFromEDT
	static def registerProjectListener(String id, ProjectManagerListener listener) {
		registerProjectListener(registerDisposable(id), listener)
	}

	@CanCallWithinRunReadActionOrFromEDT
	static unregisterProjectListener(String id) {
		unregisterDisposable(id)
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
	static registerToolWindow(String toolWindowId, Disposable disposable = null, ToolWindowAnchor location = RIGHT,
	                          ActionGroup toolbarActionGroup = null, Closure<JComponent> createComponent) {
		invokeOnEDT {
			runWriteAction {
				ToolWindows.registerToolWindow(toolWindowId, disposable, location, toolbarActionGroup, createComponent)
			}
		}
	}

	@CanCallFromAnyThread
	static ToolWindow registerToolWindow(@NotNull Project project, String toolWindowId, Disposable disposable = null,
	                                     ToolWindowAnchor location = RIGHT, ActionGroup toolbarActionGroup = null,
	                                     Closure<JComponent> createComponent) {
		invokeOnEDT {
			runWriteAction {
				ToolWindows.registerToolWindow(project, toolWindowId, disposable, location, toolbarActionGroup, createComponent)
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
				ToolWindows.unregisterToolWindow(toolWindowId)
			}
		}
	}

	@CanCallFromAnyThread
	static unregisterToolWindow(String toolWindowId, Project project) {
		invokeOnEDT {
			runWriteAction {
				ToolWindows.unregisterToolWindow(toolWindowId, project)
			}
		}
	}

	/**
	 * @param widgetId id of widget
	 * @param disposable disposable to automatically unregister listener
	 *                   (e.g. "pluginDisposable" to remove listener on plugin reload)
	 * @param anchor string in the format "<before|after> <widget id>"
	 *               (e.g. see https://github.com/JetBrains/intellij-community/blob/master/platform/platform-impl/src/com/intellij/openapi/wm/impl/IdeFrameImpl.java#L430-L430).
	 *               Some of built-in widget ids:
	 *               IdeMessagePanel.FATAL_ERROR - error notification icon;
	 *               Position - goto line widget;
	 *               IdeNotificationArea.WIDGET_ID - notification toolwindow widget;
	 *               Encoding - encoding widget;
	 *               LineSeparator - line separator widget;
	 * @param presentation see examples of implementations here
	 *                     https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/openapi/wm/StatusBarWidget.java#L49
	 * @see {@link #updateWidget(java.lang.String)}
	 */
	@CanCallFromAnyThread
	static registerWidget(String widgetId, Disposable disposable,
	                      String anchor = "before Position", StatusBarWidget.WidgetPresentation presentation) {
		invokeOnEDT {
			Widgets.registerWidget(widgetId, disposable, anchor, presentation)
		}
	}

	@CanCallFromAnyThread
	static registerWidget(String widgetId, Project project, Disposable disposable = project,
	                      String anchor = "before Position", StatusBarWidget.WidgetPresentation presentation) {
		invokeOnEDT {
			Widgets.registerWidget(widgetId, project, disposable, anchor, presentation)
		}
	}

	@CanCallFromAnyThread
	static unregisterWidget(String widgetId) {
		invokeOnEDT {
			Widgets.unregisterWidget(widgetId)
		}
	}

	@CanCallFromAnyThread
	static updateWidget(String widgetId) {
		invokeOnEDT {
			Widgets.updateWidget(widgetId)
		}
	}

	@CanCallFromAnyThread
	static findWidget(String widgetId, Project project) {
		invokeOnEDT {
			Widgets.findWidget(widgetId, project)
		}
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
	@Nullable static String showInputDialog(String message = "", String title = "", @Nullable Icon icon = null) {
		Messages.showInputDialog(message, title, icon)
	}

	/**
	 * @return currently open editor; null if there are no open editors
	 */
	@CanCallWithinRunReadActionOrFromEDT
	@Nullable static Editor currentEditorIn(@NotNull Project project) {
		Editors.currentEditorIn(project)
	}

	/**
	 * @return editor which is visible but not active
	 * (this is the case when windows is split or editor is dragged out of project frame)
	 *
	 * It is intended to be used while writing plugin code which modifies content of another open editor.
	 */
	@CanCallWithinRunReadActionOrFromEDT
	@NotNull static Editor anotherOpenEditorIn(@NotNull Project project) {
		Editors.anotherOpenEditorIn(project)
	}

	static registerEditorListener(Project project, Disposable disposable, FileEditorManagerListener listener) {
		Editors.registerEditorListener(project, disposable, listener)
	}

	static registerEditorListener(Disposable disposable, FileEditorManagerListener listener) {
		Editors.registerEditorListener(disposable, listener)
	}

	static openInEditor(@NotNull String filePath, Project project = currentProjectInFrame()) {
		openUrlInEditor("file://${filePath}", project)
	}

	@Nullable static VirtualFile openUrlInEditor(String fileUrl, Project project = currentProjectInFrame()) {
		Editors.openUrlInEditor(fileUrl, project)
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

	static PsiFile findFileByName(String filePath, @NotNull Project project, boolean searchInLibraries = false) {
		FileSearch.findFileByName(filePath, project, searchInLibraries)
	}

	static List<PsiFile> findAllFilesByName(String filePath, @NotNull Project project, boolean searchInLibraries = false) {
		FileSearch.findAllFilesByName(filePath, project, searchInLibraries)
	}

	static List<VirtualFile> sourceRootsIn(@NotNull Project project) {
		ProjectRootManager.getInstance(project).contentSourceRoots.toList()
	}

	static List<VcsRoot> vcsRootsIn(@NotNull Project project) {
		ProjectRootManager.getInstance(project).contentSourceRoots
				.collect{ ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(it) }
				.findAll{ it.path != null }.unique()
	}


	// note that com.intellij.openapi.compiler.CompilationStatusListener is not imported because
	// it doesn't exist in IDEs without compilation (e.g. in PhpStorm)
	static void registerCompilationListener(Disposable disposable, /*CompilationStatusListener*/ listener) {
		Compilation.registerCompilationListener(disposable, listener)
	}

	static void registerCompilationListener(Disposable disposable, Project project, /*CompilationStatusListener*/ listener) {
		Compilation.registerCompilationListener(disposable, project, listener)
	}

	static compile(Project project, Closure onSuccessfulCompilation = {}) {
		Compilation.compile(project, onSuccessfulCompilation)
	}

	static void registerCompilationListener(String id, Project project, /*CompilationStatusListener*/ listener) {
		registerCompilationListener(registerDisposable(id), project, listener)
	}

	static void unregisterCompilationListener(String id) {
		unregisterDisposable(id)
	}


	static registerUnitTestListener(Disposable disposable, UnitTests.Listener listener) {
		UnitTests.registerUnitTestListener(disposable, listener)
	}

	static registerUnitTestListener(Disposable disposable, Project project, UnitTests.Listener listener) {
		UnitTests.registerUnitTestListener(disposable, project, listener)
	}

	static registerUnitTestListener(String id, Project project, UnitTests.Listener listener) {
		UnitTests.registerUnitTestListener(id, project, listener)
	}

	static unregisterUnitTestListener(String id) {
		UnitTests.unregisterUnitTestListener(id)
	}

	static registerVcsListener(Disposable disposable, VcsActions.Listener listener) {
		VcsActions.registerVcsListener(disposable, listener)
	}

	static registerVcsListener(Disposable disposable, Project project, VcsActions.Listener listener) {
		VcsActions.registerVcsListener(disposable, project, listener)
	}

	@Deprecated // use method with "disposable" instead
	static registerVcsListener(String id, Project project, VcsActions.Listener listener) {
		VcsActions.registerVcsListener(id, project, listener)
	}

	@Deprecated // use method with "disposable" instead
	static unregisterVcsListener(String id) {
		VcsActions.unregisterVcsListener(id)
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
	                              String modificationName = "Modified from LivePlugin", String modificationGroup = "LivePlugin",
	                              Closure callback) {
		runWriteAction {
			CommandProcessor.instance.executeCommand(project, {
				callback.call(document)
			}, modificationName, modificationGroup, UndoConfirmationPolicy.DEFAULT, document)
		}
	}

	static replace(Document document, String regexp, String... replacement) {
		def replacementAsClosures = replacement.collect{ String s ->
			return { matchingString -> s }
		}
		replace(document, regexp, replacementAsClosures)
	}

	static replace(Document document, String regexp, Closure<String>... replacement) {
		replace(document, regexp, replacement.toList())
	}

	static replace(Document document, String regexp, List<Closure<String>> replacement) {
		regexp = "(?s).*" + regexp + ".*"

		def matcher = Pattern.compile(regexp).matcher(document.text)
		if (!matcher.matches()) throw new IllegalStateException("Nothing matched '${regexp}' in ${document}")

		def matchResult = matcher.toMatchResult()
		if (matchResult.groupCount() != replacement.size())
			throw new IllegalStateException("Expected ${matchResult.groupCount()} replacements but was: ${replacement.size()}")

		(1..matchResult.groupCount()).each { i ->
			def replacementString = replacement[i - 1].call(matchResult.group(i))
			document.replaceString(matchResult.start(i), matchResult.end(i), replacementString)
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
	 * @param modificationName name of text modification as it will be displayed in Main Menu -> Edit -> Undo/Redo
	 * @param transformer called with selected text as {@link String};
	 *                    returns {@link String} which will replace selected text or null if no replacement is required
	 */
	@CanCallFromAnyThread
	static transformSelectedText(@NotNull Project project, String modificationName = "Transform text", Closure transformer) {
		def editor = currentEditorIn(project)
		runDocumentWriteAction(project, editor.document, modificationName) {
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
		GlobalVar.changeGlobalVar(varName, initialValue, callback)
	}

	@Nullable static <T> T setGlobalVar(String varName, @Nullable varValue) {
		GlobalVar.setGlobalVar(varName, varValue)
	}

	@Nullable static <T> T getGlobalVar(String varName, @Nullable initialValue = null) {
		GlobalVar.getGlobalVar(varName, initialValue)
	}

	@Nullable static <T> T removeGlobalVar(String varName) {
		GlobalVar.removeGlobalVar(varName)
	}

	@CanCallFromAnyThread
	static <T> GlobalVar<T> newGlobalVar(String id, T value = null, Disposable disposable = null) {
		new GlobalVar<T>(id, value, disposable)
	}

	@CanCallFromAnyThread
	static doInBackground(String taskDescription = "A task", boolean canBeCancelledByUser = true,
	                      PerformInBackgroundOption backgroundOption = ALWAYS_BACKGROUND,
	                      Closure task, Closure whenCancelled = {}, Closure whenDone = {}) {
		invokeOnEDT {
			AtomicReference result = new AtomicReference(null)
			new Task.Backgroundable(null, taskDescription, canBeCancelledByUser, backgroundOption) {
				@Override void run(ProgressIndicator indicator) { result.set(task.call(indicator)) }
				@Override void onSuccess() { whenDone.call(result.get()) }
				@Override void onCancel() { whenCancelled.call() }
			}.queue()
		}
	}

	@CanCallFromAnyThread
	static doInModalMode(String taskDescription = "A task", boolean canBeCancelledByUser = true, Closure task) {
		new Task.Modal(null, taskDescription, canBeCancelledByUser) {
			@Override void run(ProgressIndicator indicator) { task.call(indicator) }
		}.queue()
	}

	static showPopupMenu(Map menuDescription, String popupTitle = "", JComponent contextComponent) {
		def dataContext = new MapDataContext().put(PlatformDataKeys.CONTEXT_COMPONENT.name, contextComponent)
		showPopupMenu(menuDescription, popupTitle, dataContext)
	}

	/**
	 * Shows popup menu in which each leaf item is associated with action.
	 *
	 * @param menuDescription see javadoc for {@link #createNestedActionGroup(java.util.Map)}
	 * @param popupTitle (optional) title of the popup
	 * @param dataContext (optional) data context which is passed to popup menu and action selected in the popup.
	 *                    It's usually a good idea to pass dataContext from current actionEvent.
	 *                    Note that popup expects dataContext to have {@link PlatformDataKeys#CONTEXT_COMPONENT}.
	 * @param selectionAidMethod determines how popup menu displays/searches for items.
	 * @param isPreselected closure which takes instance of {@link AnAction} and returns true is the action should be preselected.
	 */
	static showPopupMenu(Map menuDescription, String popupTitle = "", @Nullable DataContext dataContext = null,
	                     JBPopupFactory.ActionSelectionAid selectionAidMethod = SPEEDSEARCH, Closure isPreselected = {false}) {
		Popups.showPopupMenu(menuDescription, popupTitle, dataContext, selectionAidMethod, isPreselected)
	}

	/**
	 * @param description map describing popup menu. Keys are text presentation of items.
	 *                   Entries can be map, closure or {@link AnAction} (note that {@link com.intellij.openapi.actionSystem.Separator)} is also an action)
	 *                   - Map is interpreted as nested popup menu.
	 *                   - Close is a callback which takes one parameter with "key" and "event" attributes.
	 * @param actionGroup (optional) action group to which actions will be added
	 * @return actionGroup with actions
	 */
	@Contract(pure = true)
	static ActionGroup createNestedActionGroup(Map description, actionGroup = new DefaultActionGroup()) {
		Popups.createNestedActionGroup(description, actionGroup)
	}

	static showPopupSearch(String prompt, Project project, String initialText = "", boolean lenientMatch = false,
	                       Collection items, Closure onItemChosen) {
		Popups.showPopupSearch(prompt, project, initialText, lenientMatch, items, onItemChosen)
	}

	static showPopupSearch(String prompt, Project project, String initialText = "",
	                         Closure<Collection> itemProvider, Closure onItemChosen) {
		Popups.showPopupSearch(prompt, project, initialText, itemProvider, onItemChosen)
	}


	static registerInMetaClassesContextOf(AnActionEvent actionEvent, List metaClasses = [Object.metaClass],
	                                      Map contextKeys = ["project": PlatformDataKeys.PROJECT]) {
		metaClasses.each { aMetaClass ->
			contextKeys.each { entry ->
				aMetaClass."${entry.key}" = actionEvent.getData(entry.value as DataKey)
			}
		}
	}

	/**
	 * Please note this is NOT the right way to get current project.
	 * This method only exists to be used in "quick-and-dirty" code.
	 *
	 * For non-throwaway code get project from {@AnActionEvent}, {@DataContext} or in some other proper way.
	 */
	@CanCallFromAnyThread
	@Nullable static Project currentProjectInFrame() {
		invokeOnEDT {
			Projects.currentProjectInFrame()
		}
	}

	/**
	 * Loads and opens project from specified path.
	 * If project is already open, switches focus to its frame.
	 */
	@CanCallFromAnyThread
	@Nullable static Project openProject(@NotNull String projectPath) {
		Projects.openProject(projectPath)
	}

	@CanCallFromAnyThread
	@NotNull static JFrame switchToFrameOf(@NotNull Project project) {
		invokeOnEDT {
			def frame = WindowManager.instance.getFrame(project)
			frame.toFront()
			frame.requestFocus()
			frame
		}
	}

	static String openInBrowser(@NotNull String url) {
		BrowserUtil.open(url)
		url
	}

	@CanCallFromAnyThread
	static Map execute(String fullCommand) {
		ShellCommands.execute(fullCommand)
	}

	@CanCallFromAnyThread
	static Map execute(String command, String parameters) {
		ShellCommands.execute(command, parameters)
	}

	@CanCallFromAnyThread
	static Map execute(String command, Collection<String> parameters) {
		ShellCommands.execute(command, parameters)
	}

	@Nullable static <T> T catchingAll(Closure<T> closure) {
		Misc.catchingAll(closure)
	}

	@Nullable static <T> T accessField(Object o, List<String> possibleFieldNames, Class<T> fieldClass = null) {
		Misc.accessField(o, possibleFieldNames, fieldClass)
	}

	@Nullable static <T> T accessField(Object o, String fieldName, Class<T> fieldClass = null) {
		Misc.accessField(o, fieldName, fieldClass)
	}

	@Deprecated @Nullable static accessField(Object o, String fieldName, Closure callback) {
		Misc.accessField(o, fieldName, callback)
	}

	/**
	 * Original version was borrowed from here
	 * http://code.google.com/p/idea-string-manip/source/browse/trunk/src/main/java/osmedile/intellij/stringmanip/AbstractStringManipAction.java
	 */
	private static transformSelectionIn(Editor editor, Closure<String> transformer) {
		def selectionModel = editor.selectionModel

		int[] blockStarts = selectionModel.blockSelectionStarts
		int[] blockEnds = selectionModel.blockSelectionEnds
		if (selectionModel.selectedText == null) {
			blockStarts[0] = editor.caretModel.visualLineStart
			blockEnds[0] = editor.caretModel.visualLineEnd
		}

		for (int i = 0; i < blockStarts.length; i++) {
			def text = editor.document.charsSequence.subSequence(blockStarts[i], blockEnds[i]).toString()
			def newTextPart = transformer(text)
			if (newTextPart != null) {
				editor.document.replaceString(blockStarts[i], blockEnds[i], newTextPart)
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
}

// Annotations to make clear which thread particular method can be invoked from.
// See also http://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/general_threading_rules.html
@interface CanCallFromAnyThread {}
@interface CanCallWithinRunReadActionOrFromEDT {}