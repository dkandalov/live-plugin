package liveplugin.toolwindow.addplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import liveplugin.IDEUtil
import liveplugin.LivePluginAppComponent.*
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
        event.presentation.isEnabled = !pluginExists(pluginId)
    }

    private fun logException(e: Exception, event: AnActionEvent, pluginPath: String) {
        val project = event.project
        if (project != null) {
            IDEUtil.showErrorDialog(
                project,
                "Error adding plugin \"$pluginPath\" to $livepluginsPath",
                "Add Plugin"
            )
        }
        logger.error(e)
    }

    class PerformAllGroupActions(
        name: String,
        description: String,
        private val actionGroup: DefaultActionGroup,
        private val place: String = ""
    ): AnAction(name, description, null), DumbAware {
        override fun actionPerformed(e: AnActionEvent?) {
            actionGroup.childActionsOrStubs
                .filter { it != this && it !is Separator }
                .forEach { IDEUtil.performAction(it, place) }
        }
    }

    companion object {
        val addGroovyExamplesActionGroup by lazy {
            val group = DefaultActionGroup("Groovy Examples", true)
            group.add(AddExamplePluginAction(groovyExamplesPath + "helloWorld/", listOf("plugin.groovy")))
            group.add(AddExamplePluginAction(groovyExamplesPath + "registerAction/", listOf("plugin.groovy")))
            group.add(AddExamplePluginAction(groovyExamplesPath + "popupMenu/", listOf("plugin.groovy")))
            group.add(AddExamplePluginAction(groovyExamplesPath + "popupSearch/", listOf("plugin.groovy")))
            group.add(AddExamplePluginAction(groovyExamplesPath + "toolWindow/", listOf("plugin.groovy")))
            group.add(AddExamplePluginAction(groovyExamplesPath + "toolbarWidget/", listOf("plugin.groovy")))
            group.add(AddExamplePluginAction(groovyExamplesPath + "textEditor/", listOf("plugin.groovy")))
            group.add(AddExamplePluginAction(groovyExamplesPath + "transformSelectedText/", listOf("plugin.groovy")))
            group.add(AddExamplePluginAction(groovyExamplesPath + "insertNewLineAbove/", listOf("plugin.groovy")))
            group.add(AddExamplePluginAction(groovyExamplesPath + "inspection/", listOf("plugin.groovy")))
            group.add(AddExamplePluginAction(groovyExamplesPath + "intention/", listOf("plugin.groovy")))
            group.add(AddExamplePluginAction(groovyExamplesPath + "projectFilesStats/", listOf("plugin.groovy")))
            group.add(AddExamplePluginAction(groovyExamplesPath + "miscUtil/", listOf("plugin.groovy", "util/AClass.groovy")))
            group.add(AddExamplePluginAction(groovyExamplesPath + "additionalClasspath/", listOf("plugin.groovy")))
            group.add(AddExamplePluginAction(groovyExamplesPath + "integrationTest/", listOf("plugin.groovy", "plugin-test.groovy")))
            group.addSeparator()
            group.add(PerformAllGroupActions("Add All", "", group))
            group
        }

        val addKotlinExamplesActionGroup by lazy {
            val group = DefaultActionGroup("Kotlin Examples", true)
            group.add(AddExamplePluginAction(kotlinExamplesPath + "helloWorld/", listOf("plugin.kts")))
            group.add(AddExamplePluginAction(kotlinExamplesPath + "insertNewLineAbove/", listOf("plugin.kts")))
            group.addSeparator()
            group.add(PerformAllGroupActions("Add All", "", group))
            group
        }
    }
}
