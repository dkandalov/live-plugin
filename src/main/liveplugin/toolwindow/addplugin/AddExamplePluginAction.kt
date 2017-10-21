package liveplugin.toolwindow.addplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import liveplugin.IDEUtil
import liveplugin.LivePluginAppComponent
import liveplugin.toolwindow.RefreshPluginsPanelAction
import liveplugin.toolwindow.util.ExamplePluginInstaller

class AddExamplePluginAction(pluginPath: String, sampleFiles: List<String>): AnAction(), DumbAware {
    private val logger = Logger.getInstance(AddExamplePluginAction::class.java)
    private val pluginId = ExamplePluginInstaller.extractPluginIdFrom(pluginPath)
    private val examplePluginInstaller = ExamplePluginInstaller(pluginPath, sampleFiles)

    init {
        templatePresentation.text = pluginId
    }

    override fun actionPerformed(event: AnActionEvent) {
        examplePluginInstaller.installPlugin(object: ExamplePluginInstaller.Listener {
            override fun onException(e: Exception, pluginPath: String) {
                logException(e, event, pluginPath)
            }
        })
        RefreshPluginsPanelAction.refreshPluginTree()
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = !LivePluginAppComponent.pluginExists(pluginId)
    }

    private fun logException(e: Exception, event: AnActionEvent, pluginPath: String) {
        val project = event.project
        if (project != null) {
            IDEUtil.showErrorDialog(
                project,
                "Error adding plugin \"" + pluginPath + "\" to " + LivePluginAppComponent.pluginsRootPath(),
                "Add Plugin"
            )
        }
        logger.error(e)
    }
}
