package liveplugin.implementation.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import liveplugin.implementation.ExamplePlugin
import liveplugin.implementation.GroovyExamples
import liveplugin.implementation.KotlinExamples
import liveplugin.implementation.LivePlugin.Companion.livePluginsById
import liveplugin.implementation.common.IdeUtil.runLaterOnEdt
import liveplugin.implementation.common.IdeUtil.showError

class AddGroovyExamplesActionGroup : DefaultActionGroup("Groovy Examples", true) {
    init {
        GroovyExamples.all.forEach {
            add(AddExamplePluginAction(it))
        }
        addSeparator()
        add(PerformAllGroupActions(name = "Add All", description = "", actionGroup = this))
    }
}

class AddKotlinExamplesActionGroup : DefaultActionGroup("Kotlin Examples", true) {
    init {
        KotlinExamples.all.forEach {
            add(AddExamplePluginAction(it))
        }
        addSeparator()
        add(PerformAllGroupActions(name = "Add All", description = "", actionGroup = this))
    }
}

private class AddExamplePluginAction(private val examplePlugin: ExamplePlugin) : AnAction(), DumbAware {
    init {
        templatePresentation.text = examplePlugin.pluginId
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        examplePlugin.installPlugin(
            whenCreated = { if (project != null) FileEditorManager.getInstance(project).openFile(it, true) },
            handleError = { e, pluginPath ->
                event.project.showError("Error adding plugin \"$pluginPath\": ${e.message}", e)
            }
        )
    }

    override fun update(event: AnActionEvent) {
        val alreadyAdded = livePluginsById().containsKey(examplePlugin.pluginId)
        event.presentation.isEnabled = !alreadyAdded
    }

    override fun getActionUpdateThread() = BGT
}

private class PerformAllGroupActions(
    name: String,
    description: String,
    private val actionGroup: DefaultActionGroup
) : AnAction(name, description, null), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        actionGroup.childActionsOrStubs
            .filter { it != this && it !is Separator }
            .forEach { performAction(it) }
    }

    private fun performAction(action: AnAction) {
        val event = AnActionEvent.createEvent(EMPTY_CONTEXT, null, "", ActionUiKind.NONE, null)
        runLaterOnEdt { action.actionPerformed(event) }
    }
}
