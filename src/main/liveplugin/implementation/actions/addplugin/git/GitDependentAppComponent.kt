package liveplugin.implementation.actions.addplugin.git

import com.intellij.ide.AppLifecycleListener
import liveplugin.implementation.actions.addplugin.addFromGistAction
import liveplugin.implementation.actions.addplugin.addFromGitHubAction
import liveplugin.implementation.actions.addplugin.shareAsGistAction

class GitDependentAppComponent : AppLifecycleListener {
    override fun appFrameCreated(commandLineArgs: MutableList<String>) {
        addFromGistAction = AddPluginFromGistAction()
        addFromGitHubAction = AddPluginFromGitAction()
        shareAsGistAction = SharePluginAsGistAction()
    }
}
