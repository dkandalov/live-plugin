package liveplugin.popups

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid
import com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid.*
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.NlsActions.ActionText
import liveplugin.MapDataContext
import javax.swing.JPanel

fun ActionGroup.createPopup(
    dataContext: DataContext? = null,
    selectionAidMethod: ActionSelectionAid = SPEEDSEARCH,
    showNumbers: Boolean = selectionAidMethod == NUMBERING || selectionAidMethod == ALPHA_NUMBERING,
    showDisabledActions: Boolean = false,
    isPreselected: (AnAction) -> Boolean = { false }
): ListPopup =
    JBPopupFactory.getInstance().createActionGroupPopup(
        templatePresentation.text,
        this,
        dataContext ?: MapDataContext(mapOf(CONTEXT_COMPONENT.name to JPanel())), // prevent createActionGroupPopup() from crashing without context component
        showNumbers,
        showDisabledActions,
        selectionAidMethod == MNEMONICS,
        null,
        -1,
        isPreselected
    )

fun PopupActionGroup(@ActionText name: String, vararg actions: AnAction) =
    DefaultActionGroup(name, actions.toList()).also { it.isPopup = true }
