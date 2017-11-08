@file:Suppress("unused")

package liveplugin

import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import liveplugin.implementation.Actions
import liveplugin.implementation.Editors
import liveplugin.implementation.Threads
import java.util.function.Function

fun show(
    message: Any?,
    title: String = "",
    notificationType: NotificationType = INFORMATION,
    groupDisplayId: String  = "",
    notificationListener: NotificationListener? = null
) {
    PluginUtil.show(message, title, notificationType, groupDisplayId, notificationListener)
}

fun <T> invokeOnEDT(f: () -> T): T = Threads.invokeOnEDT { f() }

fun invokeLaterOnEDT(f: () -> Any) {
    ApplicationManager.getApplication().invokeLater { f.invoke() }
}

fun <T> runWriteAction(f: () -> T): T =
    invokeOnEDT {
        ApplicationManager.getApplication().runWriteAction(Computable { f() })
    }

fun registerAction(
    id: String, keyStroke: String = "", actionGroupId: String? = null,
    displayText: String = id, disposable: Disposable? = null,
    callback: (AnActionEvent) -> Unit
): AnAction {
    return Actions.registerAction(
        id, keyStroke, actionGroupId, displayText, disposable,
        Function<AnActionEvent, Unit> { callback(it) }
    )
}

fun Document.runWriteAction(project: Project, description: String? = null, callback: (Document) -> Unit) {
    runWriteAction {
        val f = { callback(this) }
        CommandProcessor.getInstance().executeCommand(project, f, description, null, UndoConfirmationPolicy.DEFAULT, this)
    }
}

@CanCallWithinRunReadActionOrFromEDT
val Project.currentEditor: Editor? get() = Editors.currentEditorIn(this)

@CanCallWithinRunReadActionOrFromEDT
val Project.currentFile: VirtualFile? get() = (FileEditorManagerEx.getInstance(this) as FileEditorManagerEx).currentFile

@CanCallWithinRunReadActionOrFromEDT
val Project.currentDocument: Document? get() = this.currentFile?.document

@CanCallWithinRunReadActionOrFromEDT
val VirtualFile.document: Document? get() = FileDocumentManager.getInstance().getDocument(this)
