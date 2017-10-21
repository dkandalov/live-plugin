package liveplugin.toolwindow.addplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import kotlin.reflect.KMutableProperty0

var addFromGistAction: AnAction? = null
class AddPluginFromGistDelegateAction: DelegateAction(::addFromGistAction)

var addFromGitHubAction: AnAction? = null
class AddPluginFromGitHubDelegateAction: DelegateAction(::addFromGitHubAction)

open class DelegateAction(private val property: KMutableProperty0<AnAction?>): AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        property.get()?.actionPerformed(e)
    }

    override fun update(event: AnActionEvent?) {
        val delegate = property.get()
        if (delegate == null) {
            event!!.presentation.apply {
                isVisible = false
                isEnabled = false
            }
        } else {
            delegate.update(event)
            event?.presentation?.apply {
                val delegatePresentation = delegate.templatePresentation
                text = delegatePresentation.text
                description = delegatePresentation.description
                icon = delegatePresentation.icon
            }
        }
    }
}