package liveplugin.toolwindow.addplugin

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import liveplugin.IdeUtil
import liveplugin.LivePluginAppComponent.Companion.pluginIdToPathMap
import liveplugin.LivePluginPaths
import liveplugin.LivePluginPaths.groovyExamplesPath
import liveplugin.LivePluginPaths.kotlinExamplesPath
import liveplugin.toolwindow.RefreshPluginsPanelAction
import liveplugin.toolwindow.util.ExamplePluginInstaller

class AddExamplePluginAction(pluginPath: String, private vararg val sampleFiles: String): AnAction(), DumbAware {
    private val logger = Logger.getInstance(AddExamplePluginAction::class.java)
    private val pluginId = ExamplePluginInstaller.extractPluginIdFrom(pluginPath)
    private val examplePluginInstaller = ExamplePluginInstaller(pluginPath, sampleFiles.toList())

    init {
        templatePresentation.text = pluginId
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        examplePluginInstaller.installPlugin(
            whenCreated = { if (project != null) FileEditorManager.getInstance(project).openFile(it, true) },
            handleError = { e, pluginPath -> logException(e, event, pluginPath) }
        )
        RefreshPluginsPanelAction.refreshPluginTree()
    }

    override fun update(event: AnActionEvent) {
        val pluginPath = pluginIdToPathMap()[pluginId]
        val isEnabled =
            if (pluginPath == null) true
            else (sampleFiles.toList() - pluginPath.listFiles().map { it.name }).isEmpty()
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
                add(AddExamplePluginAction(groovyExamplesPath + "hello-world/", "plugin.groovy"))
                add(AddExamplePluginAction(groovyExamplesPath + "ide-actions/", "plugin.groovy"))
                add(AddExamplePluginAction(groovyExamplesPath + "insert-new-line-above/", "plugin.groovy"))
                add(AddExamplePluginAction(groovyExamplesPath + "popup-menu/", "plugin.groovy"))
                add(AddExamplePluginAction(groovyExamplesPath + "popup-search/", "plugin.groovy"))
                add(AddExamplePluginAction(groovyExamplesPath + "tool-window/", "plugin.groovy"))
                add(AddExamplePluginAction(groovyExamplesPath + "toolbar-widget/", "plugin.groovy"))
                add(AddExamplePluginAction(groovyExamplesPath + "text-editor/", "plugin.groovy"))
                add(AddExamplePluginAction(groovyExamplesPath + "transform-selected-text/", "plugin.groovy"))
                add(AddExamplePluginAction(groovyExamplesPath + "java-inspection/", "plugin.groovy"))
                add(AddExamplePluginAction(groovyExamplesPath + "java-intention/", "plugin.groovy"))
                add(AddExamplePluginAction(groovyExamplesPath + "project-files-stats/", "plugin.groovy"))
                add(AddExamplePluginAction(groovyExamplesPath + "misc-util/", "util/AClass.groovy", "plugin.groovy"))
                add(AddExamplePluginAction(groovyExamplesPath + "additional-classpath/", "plugin.groovy"))
                add(AddExamplePluginAction(groovyExamplesPath + "integration-test/", "plugin-test.groovy", "plugin.groovy"))
                addSeparator()
                add(PerformAllGroupActions("Add All", "", this))
            }
        }

        val addKotlinExamplesActionGroup by lazy {
            DefaultActionGroup("Kotlin Examples", true).apply {
                add(AddExamplePluginAction(kotlinExamplesPath + "hello-world/", "plugin.kts"))
                add(AddExamplePluginAction(kotlinExamplesPath + "ide-actions/", "plugin.kts"))
                add(AddExamplePluginAction(kotlinExamplesPath + "insert-new-line-above/", "plugin.kts"))
                add(AddExamplePluginAction(kotlinExamplesPath + "popup-menu/", "plugin.kts"))
                add(AddExamplePluginAction(kotlinExamplesPath + "java-intention/", "plugin.kts"))
                add(AddExamplePluginAction(kotlinExamplesPath + "java-inspection/", "plugin.kts"))
                add(AddExamplePluginAction(kotlinExamplesPath + "kotlin-intention/", "plugin.kts"))
                add(AddExamplePluginAction(kotlinExamplesPath + "additional-classpath/", "plugin.kts"))
                add(AddExamplePluginAction(kotlinExamplesPath + "multiple-src-files/", "foo.kt", "bar/bar.kt", "plugin.kts"))
                addSeparator()
                add(PerformAllGroupActions("Add All", "", this))
            }
        }
    }
}
