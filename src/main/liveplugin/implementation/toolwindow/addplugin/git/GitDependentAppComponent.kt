package liveplugin.implementation.toolwindow.addplugin.git

import com.intellij.ide.AppLifecycleListener
import liveplugin.implementation.toolwindow.addplugin.addFromGistAction
import liveplugin.implementation.toolwindow.addplugin.addFromGitHubAction
import liveplugin.implementation.toolwindow.addplugin.shareAsGistAction

class GitDependentAppComponent : AppLifecycleListener {
    override fun appFrameCreated(commandLineArgs: MutableList<String>) {
        addFromGistAction = AddPluginFromGistAction()
        addFromGitHubAction = AddPluginFromGitAction()
        shareAsGistAction = SharePluginAsGistAction()
    }
}
