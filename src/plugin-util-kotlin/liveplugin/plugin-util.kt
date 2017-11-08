@file:Suppress("unused")

package liveplugin

import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid.*
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import liveplugin.implementation.Actions
import liveplugin.implementation.Editors
import liveplugin.implementation.MapDataContext
import liveplugin.implementation.Threads
import java.awt.Component
import java.util.function.Function
import javax.swing.JPanel

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
    val function = Function<AnActionEvent, Unit> { callback(it) }
    return Actions.registerAction(id, keyStroke, actionGroupId, displayText, disposable, function)
}

fun registerAction(
    id: String, keyStroke: String = "", actionGroupId: String? = null,
    displayText: String = id, disposable: Disposable? = null,
    action: AnAction
): AnAction = Actions.registerAction(id, keyStroke, actionGroupId, displayText, disposable, action)

fun Document.runWriteAction(project: Project, description: String? = null, callback: (Document) -> Unit) {
    runWriteAction {
        val f = { callback(this) }
        CommandProcessor.getInstance().executeCommand(project, f, description, null, UndoConfirmationPolicy.DEFAULT, this)
    }
}

sealed class MenuEntry {
    data class Action(val text: String, val callback: (Pair<String, AnActionEvent>) -> Unit): MenuEntry()

    data class SubMenu(val text: String, val nestedEntries: List<MenuEntry>): MenuEntry() {
        constructor(text: String, vararg nestedEntries: MenuEntry): this(text, nestedEntries.toList())
    }

    data class Delegate(val action: AnAction): MenuEntry()

    object Separator: MenuEntry()
}

fun createNestedActionGroup(description: List<MenuEntry>, actionGroup: DefaultActionGroup = DefaultActionGroup()): ActionGroup {
    description.forEach {
        when (it) {
            is MenuEntry.Action -> {
                actionGroup.add(object: AnAction(it.text) {
                    override fun actionPerformed(event: AnActionEvent) {
                        it.callback(Pair(it.text, event))
                    }
                })
            }
            is MenuEntry.SubMenu -> {
                val actionGroupName = it.text
                val isPopup = true
                actionGroup.add(createNestedActionGroup(it.nestedEntries, DefaultActionGroup(actionGroupName, isPopup)))
            }
            is MenuEntry.Delegate -> actionGroup.add(it.action)
            MenuEntry.Separator -> actionGroup.add(Separator.getInstance())
        }.let{}
    }
    return actionGroup
}

fun createPopupMenu(
    menuDescription: List<MenuEntry>,
    popupTitle: String = "",
    dataContext: DataContext? = null,
    selectionAidMethod: JBPopupFactory.ActionSelectionAid = SPEEDSEARCH,
    isPreselected: (AnAction) -> Boolean = { false }
): ListPopup {
    return JBPopupFactory.getInstance().createActionGroupPopup(
        popupTitle,
        createNestedActionGroup(menuDescription),
        dataContext ?: MapDataContext().put(CONTEXT_COMPONENT.name, JPanel()), // prevent createActionGroupPopup() from crashing without context component
        selectionAidMethod == NUMBERING || selectionAidMethod == ALPHA_NUMBERING,
        false,
        selectionAidMethod == MNEMONICS,
        null,
        -1,
        Condition { isPreselected(it) }
    )
}

fun JBPopup.show(dataContext: DataContext? = null) {
    val contextComponent = dataContext?.getData(PlatformDataKeys.CONTEXT_COMPONENT.name) as? Component
    if (contextComponent != null) {
        showInCenterOf(contextComponent)
    } else {
        showInFocusCenter()
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
