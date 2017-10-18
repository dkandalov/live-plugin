import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import liveplugin.CanCallFromAnyThread
import liveplugin.PluginUtil
import liveplugin.implementation.Actions
import liveplugin.implementation.Threads
import java.util.function.Function

@CanCallFromAnyThread
fun <T> invokeOnEDT(f: () -> T): T = Threads.invokeOnEDT { f() }

@CanCallFromAnyThread
fun <T> runWriteAction(f: () -> T): T =
    invokeOnEDT {
        ApplicationManager.getApplication().runWriteAction(Computable { f() })
    }

@CanCallFromAnyThread
fun registerAction(
    actionId: String, keyStroke: String = "", actionGroupId: String? = null,
    displayText: String = actionId, disposable: Disposable? = null,
    callback: (AnActionEvent) -> Unit
): AnAction {
    return Actions.registerAction(
        actionId, keyStroke, actionGroupId, displayText, disposable,
        Function<AnActionEvent, Unit> { callback(it) }
    )
}

@CanCallFromAnyThread
fun runDocumentWriteAction(
    project: Project, document: Document? = PluginUtil.currentDocumentIn(project),
    modificationName: String = "Modified from LivePlugin", modificationGroup: String = "LivePlugin",
    callback: (Document?) -> Unit
) {
    runWriteAction {
        CommandProcessor.getInstance().executeCommand(project, {
            callback(document)
        }, modificationName, modificationGroup, UndoConfirmationPolicy.DEFAULT, document)
    }
}
