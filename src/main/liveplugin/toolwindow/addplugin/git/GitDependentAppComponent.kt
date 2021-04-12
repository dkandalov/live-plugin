package liveplugin.toolwindow.addplugin.git

import com.intellij.ide.AppLifecycleListener
import liveplugin.toolwindow.addplugin.addFromGistAction
import liveplugin.toolwindow.addplugin.addFromGitHubAction
import liveplugin.toolwindow.addplugin.shareAsGistAction

class GitDependentAppComponent : AppLifecycleListener {
    override fun appFrameCreated(commandLineArgs: MutableList<String>) {
        addFromGistAction = AddPluginFromGistAction()
        addFromGitHubAction = AddPluginFromGitAction()
        shareAsGistAction = SharePluginAsGistAction()
    }
}
