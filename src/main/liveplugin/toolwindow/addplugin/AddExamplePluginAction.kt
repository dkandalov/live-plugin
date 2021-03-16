package liveplugin.toolwindow.addplugin

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import liveplugin.IdeUtil
import liveplugin.LivePluginAppComponent.Companion.pluginIdToPathMap
import liveplugin.LivePluginPaths
import liveplugin.LivePluginPaths.groovyExamplesPath
import liveplugin.LivePluginPaths.kotlinExamplesPath
import liveplugin.toolwindow.RefreshPluginsPanelAction
import liveplugin.toolwindow.util.ExamplePluginInstaller

class AddExamplePluginAction(pluginPath: String, private val sampleFiles: List<String>): AnAction(), DumbAware {
    private val logger = Logger.getInstance(AddExamplePluginAction::class.java)
    private val pluginId = ExamplePluginInstaller.extractPluginIdFrom(pluginPath)
    private val examplePluginInstaller = ExamplePluginInstaller(pluginPath, sampleFiles)

    init {
        templatePresentation.text = pluginId
    }

    override fun actionPerformed(event: AnActionEvent) {
        examplePluginInstaller.installPlugin(handleError = { e, pluginPath -> logException(e, event, pluginPath) })
        RefreshPluginsPanelAction.refreshPluginTree()
    }

    override fun update(event: AnActionEvent) {
        val pluginPath = pluginIdToPathMap()[pluginId]
        val isEnabled =
            if (pluginPath == null) true
            else {
                val files = pluginPath.listFiles().map { it.name }
                sampleFiles.none { files.contains(it) }
            }
        event.presentation.isEnabled = isEnabled
    }

    private fun logException(e: Exception, event: AnActionEvent, pluginPath: String) {
        val project = event.project
        if (project != null) {
            IdeUtil.showErrorDialog(
                project,
                "Error adding plugin \"$pluginPath\" to ${LivePluginPaths.livePluginsPath}",
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
        override fun actionPerformed(e: AnActionEvent) {
            actionGroup.childActionsOrStubs
                .filter { it != this && it !is Separator }
                .forEach { performAction(it, place) }
        }

        private fun performAction(action: AnAction, place: String) {
            val event = AnActionEvent(null, IdeUtil.dummyDataContext, place, action.templatePresentation, ActionManager.getInstance(), 0)
            IdeUtil.invokeLaterOnEDT { action.actionPerformed(event) }
        }
    }

    companion object {
        val addGroovyExamplesActionGroup by lazy {
            DefaultActionGroup("Groovy Examples", true).apply {
                add(AddExamplePluginAction(groovyExamplesPath + "hello-world/", listOf("plugin.groovy")))
                add(AddExamplePluginAction(groovyExamplesPath + "ide-actions/", listOf("plugin.groovy")))
                add(AddExamplePluginAction(groovyExamplesPath + "insert-new-line-above/", listOf("plugin.groovy")))
                add(AddExamplePluginAction(groovyExamplesPath + "popup-menu/", listOf("plugin.groovy")))
                add(AddExamplePluginAction(groovyExamplesPath + "popup-search/", listOf("plugin.groovy")))
                add(AddExamplePluginAction(groovyExamplesPath + "tool-window/", listOf("plugin.groovy")))
                add(AddExamplePluginAction(groovyExamplesPath + "toolbar-widget/", listOf("plugin.groovy")))
                add(AddExamplePluginAction(groovyExamplesPath + "text-editor/", listOf("plugin.groovy")))
                add(AddExamplePluginAction(groovyExamplesPath + "transform-selected-text/", listOf("plugin.groovy")))
                add(AddExamplePluginAction(groovyExamplesPath + "java-inspection/", listOf("plugin.groovy")))
                add(AddExamplePluginAction(groovyExamplesPath + "java-intention/", listOf("plugin.groovy")))
                add(AddExamplePluginAction(groovyExamplesPath + "project-files-stats/", listOf("plugin.groovy")))
                add(AddExamplePluginAction(groovyExamplesPath + "misc-util/", listOf("plugin.groovy", "util/AClass.groovy")))
                add(AddExamplePluginAction(groovyExamplesPath + "additional-classpath/", listOf("plugin.groovy")))
                add(AddExamplePluginAction(groovyExamplesPath + "integration-test/", listOf("plugin.groovy", "plugin-test.groovy")))
                addSeparator()
                add(PerformAllGroupActions("Add All", "", this))
            }
        }

        val addKotlinExamplesActionGroup by lazy {
            DefaultActionGroup("Kotlin Examples", true).apply {
                add(AddExamplePluginAction(kotlinExamplesPath + "hello-world/", listOf("plugin.kts")))
                add(AddExamplePluginAction(kotlinExamplesPath + "ide-actions/", listOf("plugin.kts")))
                add(AddExamplePluginAction(kotlinExamplesPath + "insert-new-line-above/", listOf("plugin.kts")))
                add(AddExamplePluginAction(kotlinExamplesPath + "popup-menu/", listOf("plugin.kts")))
                add(AddExamplePluginAction(kotlinExamplesPath + "java-intention/", listOf("plugin.kts")))
                add(AddExamplePluginAction(kotlinExamplesPath + "java-inspection/", listOf("plugin.kts")))
                add(AddExamplePluginAction(kotlinExamplesPath + "kotlin-intention/", listOf("plugin.kts")))
                add(AddExamplePluginAction(kotlinExamplesPath + "additional-classpath/", listOf("plugin.kts")))
                add(AddExamplePluginAction(kotlinExamplesPath + "multiple-src-files/", listOf("plugin.kts", "foo.kt", "bar/bar.kt")))
                addSeparator()
                add(PerformAllGroupActions("Add All", "", this))
            }
        }
    }
}
