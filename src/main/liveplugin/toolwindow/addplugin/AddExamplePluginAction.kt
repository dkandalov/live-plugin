package liveplugin.toolwindow.addplugin

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import liveplugin.IdeUtil
import liveplugin.LivePluginAppComponent.Companion.pluginIdToPathMap
import liveplugin.LivePluginPaths.livePluginsPath
import liveplugin.toolwindow.RefreshPluginsPanelAction
import liveplugin.toolwindow.util.ExamplePlugin
import liveplugin.toolwindow.util.GroovyExamples
import liveplugin.toolwindow.util.KotlinExamples
import liveplugin.toolwindow.util.installPlugin

class AddExamplePluginAction(private val examplePlugin: ExamplePlugin): AnAction(), DumbAware {
    private val logger = Logger.getInstance(AddExamplePluginAction::class.java)

    init {
        templatePresentation.text = examplePlugin.pluginId
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        examplePlugin.installPlugin(
            whenCreated = { if (project != null) FileEditorManager.getInstance(project).openFile(it, true) },
            handleError = { e, pluginPath -> logException(e, event, pluginPath) }
        )
        RefreshPluginsPanelAction.refreshPluginTree()
    }

    override fun update(event: AnActionEvent) {
        val pluginPath = pluginIdToPathMap()[examplePlugin.pluginId] ?: return
        val alreadyAdded = pluginPath.allFiles().map { it.value.removePrefix(pluginPath.value + "/") }.toList().containsAll(examplePlugin.filePaths)
        event.presentation.isEnabled = !alreadyAdded
    }

    private fun logException(e: Exception, event: AnActionEvent, pluginPath: String) {
        val project = event.project
        if (project != null) {
            IdeUtil.showErrorDialog(
                project,
                "Error adding plugin \"$pluginPath\" to $livePluginsPath",
                "Add Plugin"
            )
        }
        logger.error(e)
    }

    private class PerformAllGroupActions(
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
            val event = AnActionEvent(null, EMPTY_CONTEXT, place, action.templatePresentation, ActionManager.getInstance(), 0)
            IdeUtil.invokeLaterOnEDT { action.actionPerformed(event) }
        }
    }

    companion object {
        val addGroovyExamplesActionGroup by lazy {
            DefaultActionGroup("Groovy Examples", true).apply {
                GroovyExamples.all.forEach {
                    add(AddExamplePluginAction(it))
                }
                addSeparator()
                add(PerformAllGroupActions("Add All", "", this))
            }
        }

        val addKotlinExamplesActionGroup by lazy {
            DefaultActionGroup("Kotlin Examples", true).apply {
                KotlinExamples.all.forEach {
                    add(AddExamplePluginAction(it))
                }
                addSeparator()
                add(PerformAllGroupActions("Add All", "", this))
            }
        }
    }
}
