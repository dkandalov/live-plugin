package liveplugin.implementation.actions.git

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import liveplugin.implementation.actions.gist.SharePluginAsGistAction
import kotlin.reflect.KMutableProperty0

var shareAsGistAction: AnAction? = null
class SharePluginAsGistDelegateAction: DelegateAction(::shareAsGistAction)
var isGitHubPluginAvailable = false

class GitHubPluginDependencyAppComponent : AppLifecycleListener {
    override fun appFrameCreated(commandLineArgs: MutableList<String>) {
        shareAsGistAction = SharePluginAsGistAction()
        isGitHubPluginAvailable = true
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

    override fun getActionUpdateThread() = BGT
}