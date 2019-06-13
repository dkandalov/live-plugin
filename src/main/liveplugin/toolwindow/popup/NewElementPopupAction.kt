package liveplugin.toolwindow.popup

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup

class NewElementPopupAction: AnAction(), DumbAware, PopupAction {
    override fun actionPerformed(event: AnActionEvent) {
        createPopup(event.dataContext).showInBestPositionFor(event.dataContext)
    }

    private fun createPopup(dataContext: DataContext): ListPopup =
        JBPopupFactory.getInstance().createActionGroupPopup(
            IdeBundle.message("title.popup.new.element"),
            livePluginNewElementPopup,
            dataContext, false, true, false, null, -1,
            LangDataKeys.PRESELECT_NEW_ACTION_CONDITION.getData(dataContext)
        )

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = true
    }

    companion object {
        private val livePluginNewElementPopup by lazy {
            ActionManager.getInstance().getAction("LivePlugin.NewElementPopup") as ActionGroup
        }
    }
}
