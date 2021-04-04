package liveplugin.popups

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid.*
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.NlsActions.ActionText
import liveplugin.implementation.MapDataContext
import java.awt.Component
import javax.swing.JPanel

fun createPopupMenu(
    actionGroup: ActionGroup,
    dataContext: DataContext? = null,
    showDisabledActions: Boolean = false,
    selectionAidMethod: JBPopupFactory.ActionSelectionAid = SPEEDSEARCH,
    isPreselected: (AnAction) -> Boolean = { false }
): ListPopup {
    return JBPopupFactory.getInstance().createActionGroupPopup(
        actionGroup.templatePresentation.text,
        actionGroup,
        dataContext ?: MapDataContext().put(CONTEXT_COMPONENT.name, JPanel()), // prevent createActionGroupPopup() from crashing without context component
        selectionAidMethod == NUMBERING || selectionAidMethod == ALPHA_NUMBERING,
        showDisabledActions,
        selectionAidMethod == MNEMONICS,
        null,
        -1
    ) { isPreselected(it) }
}

fun PopupActionGroup(@ActionText name: String, vararg actions: AnAction) =
    DefaultActionGroup(name, actions.toList()).also { it.isPopup = true }

fun JBPopup.show(dataContext: DataContext? = null) {
    val contextComponent = dataContext?.getData(CONTEXT_COMPONENT.name) as? Component
    if (contextComponent != null) {
        showInCenterOf(contextComponent)
    } else {
        showInFocusCenter()
    }
}
