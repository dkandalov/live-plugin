package liveplugin.toolwindow.addplugin.git

import liveplugin.toolwindow.addplugin.addFromGistAction
import liveplugin.toolwindow.addplugin.addFromGitHubAction

class GitDependentAppComponent {
    init {
        addFromGistAction = AddPluginFromGistAction()
        addFromGitHubAction = AddPluginFromGitAction()
    }
}
