package liveplugin.implementation.actions.addplugin.git

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import kotlin.reflect.KMutableProperty0

var addFromGitHubAction: AnAction? = null
class AddPluginFromGitHubDelegateAction: DelegateAction(::addFromGitHubAction)

var shareAsGistAction: AnAction? = null
class SharePluginAsGistDelegateAction: DelegateAction(::shareAsGistAction)

class GitDependentAppComponent : AppLifecycleListener {
    override fun appFrameCreated(commandLineArgs: MutableList<String>) {
        addFromGitHubAction = AddPluginFromGitAction()
        shareAsGistAction = SharePluginAsGistAction()
    }
}

open class DelegateAction(private val property: KMutableProperty0<AnAction?>): AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        property.get()?.actionPerformed(e)
    }

    override fun update(event: AnActionEvent) {
        val delegate = property.get()
        if (delegate == null) {
            event.presentation.apply {
                isVisible = false
                isEnabled = false
            }
        } else {
            delegate.update(event)
            event.presentation.apply {
                val delegatePresentation = delegate.templatePresentation
                text = delegatePresentation.text
                description = delegatePresentation.description
                icon = delegatePresentation.icon
            }
        }
    }
}