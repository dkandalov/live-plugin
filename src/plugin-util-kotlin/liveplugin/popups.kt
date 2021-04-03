package liveplugin.popups

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid.*
import com.intellij.openapi.ui.popup.ListPopup
import liveplugin.implementation.MapDataContext
import java.awt.Component
import javax.swing.JPanel

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

fun createNestedActionGroup(description: List<MenuEntry>, actionGroup: DefaultActionGroup = DefaultActionGroup()): ActionGroup {
    description.forEach {
        when (it) {
            is MenuEntry.Action   -> {
                actionGroup.add(object: AnAction(it.text) {
                    override fun actionPerformed(event: AnActionEvent) {
                        it.callback(Pair(it.text, event))
                    }
                    override fun isDumbAware() = true
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

sealed class MenuEntry {
    data class Action(val text: String, val callback: (Pair<String, AnActionEvent>) -> Unit): MenuEntry()

    data class SubMenu(val text: String, val nestedEntries: List<MenuEntry>): MenuEntry() {
        constructor(text: String, vararg nestedEntries: MenuEntry): this(text, nestedEntries.toList())
    }

    data class Delegate(val action: AnAction): MenuEntry()

    object Separator: MenuEntry()
}
