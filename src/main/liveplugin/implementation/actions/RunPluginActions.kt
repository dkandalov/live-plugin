package liveplugin.implementation.actions

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import liveplugin.implementation.LivePlugin
import liveplugin.implementation.actions.RunPluginAction.Companion.runPluginsTests
import liveplugin.implementation.common.Icons.rerunPluginIcon
import liveplugin.implementation.common.Icons.runPluginIcon
import liveplugin.implementation.common.Icons.testPluginIcon
import liveplugin.implementation.common.IdeUtil
import liveplugin.implementation.common.IdeUtil.displayError
import liveplugin.implementation.common.IdeUtil.ideStartupActionPlace
import liveplugin.implementation.common.IdeUtil.runOnEdt
import liveplugin.implementation.common.flatMap
import liveplugin.implementation.common.peekFailure
import liveplugin.implementation.common.toFilePath
import liveplugin.implementation.livePlugins
import liveplugin.implementation.pluginrunner.AnError
import liveplugin.implementation.pluginrunner.AnError.LoadingError
import liveplugin.implementation.pluginrunner.AnError.RunningError
import liveplugin.implementation.pluginrunner.Binding
import liveplugin.implementation.pluginrunner.PluginRunner
import liveplugin.implementation.pluginrunner.canBeHandledBy
import liveplugin.implementation.pluginrunner.groovy.GroovyPluginRunner.Companion.mainGroovyPluginRunner
import liveplugin.implementation.pluginrunner.groovy.GroovyPluginRunner.Companion.testGroovyPluginRunner
import liveplugin.implementation.pluginrunner.kotlin.KotlinPluginRunner.Companion.mainKotlinPluginRunner
import liveplugin.implementation.pluginrunner.kotlin.KotlinPluginRunner.Companion.testKotlinPluginRunner

private val pluginRunners = listOf(mainGroovyPluginRunner, mainKotlinPluginRunner)
private val pluginTestRunners = listOf(testGroovyPluginRunner, testKotlinPluginRunner)

class RunPluginAction: AnAction("Run Command", "Run developer command", runPluginIcon), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        runWriteAction { FileDocumentManager.getInstance().saveAllDocuments() }
        runPlugins(event.livePlugins(), event)
    }

    override fun update(event: AnActionEvent) {
        val livePlugins = event.livePlugins()
        event.presentation.isEnabled = livePlugins.canBeHandledBy(pluginRunners)
        if (event.presentation.isEnabled) {
            val hasPluginsToUnload = livePlugins.any { it.canBeUnloaded() }
            val actionName = if (hasPluginsToUnload) "Rerun" else "Run"
            event.presentation.setText("$actionName ${pluginNameInActionText(livePlugins)}", false)
            event.presentation.icon = if (hasPluginsToUnload) rerunPluginIcon else runPluginIcon
        }
    }

    companion object {
        @JvmStatic fun runPlugins(livePlugins: Collection<LivePlugin>, event: AnActionEvent) {
            livePlugins.forEach { it.runWith(pluginRunners, event) }
        }

        @JvmStatic fun runPluginsTests(livePlugins: Collection<LivePlugin>, event: AnActionEvent) {
            livePlugins.forEach { it.runWith(pluginTestRunners, event) }
        }

        fun pluginNameInActionText(livePlugins: List<LivePlugin>): String {
            val pluginNameInActionText = when (livePlugins.size) {
                0    -> "Plugin"
                1    -> "'${livePlugins.first().id}' Plugin"
                else -> "Selected Plugins"
            }
            return pluginNameInActionText
        }
    }
}

class RunPluginTestsAction: AnAction("Run Plugin Tests", "Run plugin integration tests", testPluginIcon), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        runWriteAction { FileDocumentManager.getInstance().saveAllDocuments() }
        runPluginsTests(event.livePlugins(), event)
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.livePlugins().canBeHandledBy(pluginTestRunners)
    }
}

class RunLivePluginsGroup : DefaultActionGroup(
    RunPluginAction().hiddenWhenDisabled(),
    UnloadPluginAction().hiddenWhenDisabled(),
    Separator()
) {
    init {
        isPopup = false
    }

    companion object {
        fun AnAction.hiddenWhenDisabled() = HiddenWhenDisabledAction(this)
    }

    class HiddenWhenDisabledAction(private val delegate: AnAction): AnAction(), DumbAware {
        override fun actionPerformed(event: AnActionEvent) = delegate.actionPerformed(event)
        override fun update(event: AnActionEvent) {
            val presentation = delegate.templatePresentation
            event.presentation.text = presentation.text
            event.presentation.description = presentation.description
            event.presentation.icon = presentation.icon

            delegate.update(event)
            if (!event.presentation.isEnabled) event.presentation.isVisible = false
        }
    }
}

private fun LivePlugin.runWith(pluginRunners: List<PluginRunner>, event: AnActionEvent) {
    val project = event.project
    val binding = Binding.create(this, event)
    val pluginRunner = pluginRunners.find { path.find(it.scriptName) != null }
        ?: return displayError(id, LoadingError(message = "Startup script was not found. Tried: ${pluginRunners.map { it.scriptName }}"), project)

    runInBackground(project, "Running live-plugin '$id'") {
        pluginRunner.setup(this, project)
            .flatMap { runOnEdt { pluginRunner.run(it, binding) } }
            .peekFailure { displayError(id, it, project) }
    }
}

private fun runInBackground(project: Project?, taskDescription: String, function: () -> Any) {
    if (project == null) {
        // Can't use ProgressManager here because it will show with modal dialogs on IDE startup when there is no project
        ApplicationManager.getApplication().executeOnPooledThread {
            function()
        }
    } else {
        ProgressManager.getInstance().run(object: Task.Backgroundable(project, taskDescription, false, ALWAYS_BACKGROUND) {
            override fun run(indicator: ProgressIndicator) {
                function()
            }
        })
    }
}

private val bindingByPluginId = HashMap<String, Binding>()

fun Binding.Companion.lookup(livePlugin: LivePlugin): Binding? =
    bindingByPluginId[livePlugin.id]

fun Binding.Companion.create(livePlugin: LivePlugin, event: AnActionEvent): Binding {
    val oldBinding = bindingByPluginId[livePlugin.id]
    if (oldBinding != null) {
        try {
            Disposer.dispose(oldBinding.pluginDisposable)
        } catch (e: Exception) {
            displayError(livePlugin.id, RunningError(e), event.project)
        }
    }

    val disposable = object: Disposable {
        override fun dispose() {}
        override fun toString() = "LivePlugin: $livePlugin"
    }
    Disposer.register(ApplicationManager.getApplication(), disposable)

    val binding = Binding(event.project, event.place == ideStartupActionPlace, livePlugin.path.value, disposable)
    bindingByPluginId[livePlugin.id] = binding

    return binding
}

fun Binding.dispose() {
    Disposer.dispose(pluginDisposable)
    bindingByPluginId.remove(LivePlugin(pluginPath.toFilePath()).id)
}

private fun displayError(pluginId: String, error: AnError, project: Project?) {
    val (title, message) = when (error) {
        is LoadingError -> Pair("Loading error: $pluginId", error.message + if (error.throwable != null) "\n" + IdeUtil.unscrambleThrowable(error.throwable) else "")
        is RunningError -> Pair("Running error: $pluginId", IdeUtil.unscrambleThrowable(error.throwable))
    }
    displayError(title, message, project)
}
