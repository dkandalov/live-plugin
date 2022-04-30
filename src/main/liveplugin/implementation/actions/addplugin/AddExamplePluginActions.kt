package liveplugin.implementation.actions.addplugin

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import liveplugin.implementation.LivePlugin.Companion.livePluginsById
import liveplugin.implementation.actions.RefreshPluginsPanelAction
import liveplugin.implementation.common.IdeUtil
import liveplugin.implementation.common.IdeUtil.runLaterOnEdt
import liveplugin.implementation.ExamplePlugin
import liveplugin.implementation.GroovyExamples
import liveplugin.implementation.KotlinExamples

class AddGroovyExamplesActionGroup: DefaultActionGroup("Groovy Examples", true) {
    init {
        GroovyExamples.all.forEach {
            add(AddExamplePluginAction(it))
        }
        addSeparator()
        add(PerformAllGroupActions("Add All", "", this))
    }
}

class AddKotlinExamplesActionGroup: DefaultActionGroup("Kotlin Examples", true) {
    init {
        KotlinExamples.all.forEach {
            add(AddExamplePluginAction(it))
        }
        addSeparator()
        add(PerformAllGroupActions("Add All", "", this))
    }
}

private class AddExamplePluginAction(private val examplePlugin: ExamplePlugin): AnAction(), DumbAware {
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
        val alreadyAdded = livePluginsById().containsKey(examplePlugin.pluginId)
        event.presentation.isEnabled = !alreadyAdded
    }

    private fun logException(e: Exception, event: AnActionEvent, pluginPath: String) {
        val project = event.project
        if (project != null) IdeUtil.showErrorDialog(project, "Error adding plugin \"$pluginPath\"", "Add Plugin")
        logger.error(e)
    }
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
        runLaterOnEdt { action.actionPerformed(event) }
    }
}

fun installLivepluginTutorialExamples() {
    runLaterOnEdt {
        listOf(GroovyExamples.helloWorld, GroovyExamples.ideActions, GroovyExamples.modifyDocument, GroovyExamples.popupMenu).forEach {
            it.installPlugin()
        }
    }
}
