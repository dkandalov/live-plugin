@file:Suppress("unused")

package liveplugin

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT
import com.intellij.ide.BrowserUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationListener.URL_OPENING_LISTENER
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy.DEFAULT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import liveplugin.implementation.pluginrunner.kotlin.LivePluginScript
import liveplugin.implementation.registerInspectionIn
import liveplugin.implementation.showInConsole
import java.awt.Component

/**
 * Shows popup balloon notification with the specified [message] (which can include HTML tags),
 * [title], [notificationType] and [groupDisplayId] (see `IDE Settings - Appearance & Behavior - Notifications`).
 *
 * Under the hood, this function sends IDE notification event
 * which is displayed as a "balloon" and added to the `Event Log` console.
 */
fun show(
    message: Any?,
    title: String = "",
    notificationType: NotificationType = INFORMATION,
    groupDisplayId: String = "Live Plugin",
    notificationAction: NotificationAction? = null
) {
    runLaterOnEdt {
        val notification = Notification(groupDisplayId, title, message.toString().ifBlank { "[empty message]" }, notificationType)
        if (notificationAction != null) {
            notification.addAction(notificationAction)
        }
        @Suppress("DEPRECATION") // Because there are no non-deprecated alternatives
        notification.setListener(URL_OPENING_LISTENER)
        ApplicationManager.getApplication().messageBus.syncPublisher(Notifications.TOPIC).notify(notification)
    }
}

fun Project.showInConsole(
    message: String,
    consoleTitle: String = "",
    contentType: ConsoleViewContentType = NORMAL_OUTPUT
): ConsoleView =
    showInConsole(message, consoleTitle, this, contentType)

fun Document.executeCommand(project: Project, description: String? = null, callback: Document.() -> Unit) {
    runOnEdtWithWriteLock {
        val command = { callback(this) }
        CommandProcessor.getInstance().executeCommand(project, command, description, null, DEFAULT, this)
    }
}

fun LivePluginScript.registerIntention(intention: IntentionAction): IntentionAction {
    runOnEdtWithWriteLock {
        IntentionManager.getInstance().addAction(intention)
        pluginDisposable.whenDisposed {
            IntentionManager.getInstance().unregisterIntention(intention)
        }
    }
    return intention
}

fun LivePluginScript.registerInspection(inspection: InspectionProfileEntry) {
    runOnEdtWithWriteLock {
        registerProjectOpenListener(pluginDisposable) { project ->
            registerInspectionIn(project, pluginDisposable.registerParent(project), inspection)
        }
    }
}

fun openInBrowser(url: String) =
    BrowserUtil.browse(url)

fun Project.openInIdeBrowser(url: String, title: String = "") =
    HTMLEditorProvider.openEditor(this, title, url, null)

fun Project.openInEditor(filePath: String) {
    val virtualFile = VirtualFileManager.getInstance().refreshAndFindFileByUrl("file://${filePath}") ?: return
    FileEditorManager.getInstance(this).openFile(virtualFile, true, true)
}

val logger: Logger = Logger.getInstance("LivePlugin")

val AnActionEvent.contextComponent: Component?
    get() = PlatformDataKeys.CONTEXT_COMPONENT.getData(this.dataContext)

val AnActionEvent.editor: Editor?
    get() = CommonDataKeys.EDITOR_EVEN_IF_INACTIVE.getData(this.dataContext)

val AnActionEvent.document: Document?
    get() = editor?.document

val AnActionEvent.virtualFile: VirtualFile?
    get() = CommonDataKeys.VIRTUAL_FILE.getData(this.dataContext)

val AnActionEvent.psiFile: PsiFile?
    get() = CommonDataKeys.PSI_FILE.getData(this.dataContext)

val AnActionEvent.psiElement: PsiElement?
    get() = CommonDataKeys.PSI_ELEMENT.getData(this.dataContext)

val VirtualFile.document: Document?
    get() = FileDocumentManager.getInstance().getDocument(this)

val Project.currentEditor: Editor?
    get() = FileEditorManagerEx.getInstanceEx(this).selectedTextEditor

@Suppress("UnstableApiUsage")
val Project.currentFile: VirtualFile?
    get() = FileEditorManagerEx.getInstanceEx(this).currentFile

val Project.currentPsiFile: PsiFile?
    get() = currentFile?.let { PsiManager.getInstance(this).findFile(it) }

val Project.currentDocument: Document?
    get() = currentFile?.document
