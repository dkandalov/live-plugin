package liveplugin.toolwindow.addplugin.git

import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.project.DumbAware
import liveplugin.toolwindow.addplugin.addFromGistAction
import liveplugin.toolwindow.addplugin.addFromGitHubAction

class GitDependentAppComponent: ApplicationComponent, DumbAware {

    override fun initComponent() {
        addFromGistAction = AddPluginFromGistAction()
        addFromGitHubAction = AddPluginFromGitAction()
    }

    override fun disposeComponent() {}

    override fun getComponentName() = "GitDependentAppComponent"
}
