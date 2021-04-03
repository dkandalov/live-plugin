@file:Suppress("unused")

package liveplugin

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationType.INFORMATION
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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import liveplugin.implementation.Editors
import liveplugin.implementation.MapDataContext
import liveplugin.pluginrunner.kotlin.LivePluginScript
import java.awt.Component
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JPanel

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
    notificationListener: NotificationListener? = null
) {
    PluginUtil.show(message, title, notificationType, groupDisplayId, notificationListener)
}

inline fun <T> runOnEdt(crossinline f: () -> T): T {
    val result = AtomicReference<T>()
    ApplicationManager.getApplication().invokeAndWait {
        result.set(f())
    }
    return result.get()
}

inline fun runLaterOnEdt(crossinline f: () -> Any?) =
    ApplicationManager.getApplication().invokeLater { f() }

inline fun <T> withReadLock(crossinline f: () -> T): T =
    ApplicationManager.getApplication().runReadAction(Computable { f() })

inline fun <T> withWriteLockOnEdt(crossinline f: () -> T): T =
    runOnEdt {
        ApplicationManager.getApplication().runWriteAction(Computable { f() })
    }

fun Document.executeCommand(project: Project, description: String? = null, callback: (Document) -> Unit) {
    withWriteLockOnEdt {
        val document = this
        val command = { callback(document) }
        CommandProcessor.getInstance().executeCommand(project, command, description, null, UndoConfirmationPolicy.DEFAULT, document)
    }
}

fun LivePluginScript.registerIntention(intention: IntentionAction): IntentionAction =
    PluginUtil.registerIntention(pluginDisposable, intention)

fun LivePluginScript.registerInspection(inspection: InspectionProfileEntry) {
    PluginUtil.registerInspection(pluginDisposable, inspection)
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
            is MenuEntry.Action   -> {
                actionGroup.add(object: AnAction(it.text) {
                    override fun actionPerformed(event: AnActionEvent) {
                        it.callback(Pair(it.text, event))
                    }
                })
            }
            is MenuEntry.SubMenu  -> {
                val actionGroupName = it.text
                val isPopup = true
                actionGroup.add(createNestedActionGroup(it.nestedEntries, DefaultActionGroup(actionGroupName, isPopup)))
            }
            is MenuEntry.Delegate -> actionGroup.add(it.action)
            MenuEntry.Separator   -> actionGroup.add(Separator.getInstance())
        }
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
        -1
    ) { isPreselected(it) }
}

fun JBPopup.show(dataContext: DataContext? = null) {
    val contextComponent = dataContext?.getData(CONTEXT_COMPONENT.name) as? Component
    if (contextComponent != null) {
        showInCenterOf(contextComponent)
    } else {
        showInFocusCenter()
    }
}

@CanCallWithReadLockOrFromEDT
val AnActionEvent.editor: Editor?
    get() = CommonDataKeys.EDITOR_EVEN_IF_INACTIVE.getData(this.dataContext)

@CanCallWithReadLockOrFromEDT
val AnActionEvent.virtualFile: VirtualFile?
    get() = CommonDataKeys.VIRTUAL_FILE.getData(this.dataContext)

@CanCallWithReadLockOrFromEDT
val AnActionEvent.psiFile: PsiFile?
    get() = CommonDataKeys.PSI_FILE.getData(this.dataContext)

@CanCallWithReadLockOrFromEDT
val AnActionEvent.psiElement: PsiElement?
    get() = CommonDataKeys.PSI_ELEMENT.getData(this.dataContext)

@CanCallWithReadLockOrFromEDT
val VirtualFile.document: Document?
    get() = FileDocumentManager.getInstance().getDocument(this)

@CanCallWithReadLockOrFromEDT
val Project.currentEditor: Editor?
    get() = Editors.currentEditorIn(this)

@CanCallWithReadLockOrFromEDT
val Project.currentFile: VirtualFile?
    get() = (FileEditorManagerEx.getInstance(this) as FileEditorManagerEx).currentFile

@CanCallWithReadLockOrFromEDT
val Project.currentPsiFile: PsiFile?
    get() = currentFile?.let { PsiManager.getInstance(this).findFile(it) }

@CanCallWithReadLockOrFromEDT
val Project.currentDocument: Document?
    get() = currentFile?.document

/**
 * @see liveplugin.PluginUtil.assertNoNeedForEdtOrWriteActionWhenUsingActionManager
 */
inline fun <T> noNeedForEdtOrWriteActionWhenUsingActionManager(f: () -> T) = f()