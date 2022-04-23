package liveplugin.pluginrunner

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState.NON_MODAL
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import liveplugin.common.IdeUtil.displayError
import liveplugin.common.IdeUtil.ideStartupActionPlace
import liveplugin.LivePluginAppComponent.Companion.findParentPluginFolder
import liveplugin.common.*
import liveplugin.pluginrunner.AnError.LoadingError
import liveplugin.pluginrunner.AnError.RunningError
import liveplugin.pluginrunner.RunPluginAction.Companion.runPluginsTests
import liveplugin.pluginrunner.groovy.GroovyPluginRunner
import liveplugin.pluginrunner.kotlin.KotlinPluginRunner
import java.util.concurrent.atomic.AtomicReference

private val pluginRunners = listOf(GroovyPluginRunner.main, KotlinPluginRunner.main)
private val pluginTestRunners = listOf(GroovyPluginRunner.test, KotlinPluginRunner.test)

class RunPluginAction: AnAction("Run Plugin", "Run selected plugins", Icons.runPluginIcon), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        runWriteAction { FileDocumentManager.getInstance().saveAllDocuments() }
        runPlugins(event.selectedFiles(), event)
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.selectedFiles().canBeHandledBy(pluginRunners)
        val hasPluginsToUnload = event.hasPluginsToUnload()
        event.presentation.text = if (hasPluginsToUnload) "Rerun Plugin" else "Run Plugin"
        event.presentation.icon = if (hasPluginsToUnload) Icons.rerunPluginIcon else Icons.runPluginIcon
    }

    companion object {
        @JvmStatic fun runPlugins(pluginFilePaths: List<FilePath>, event: AnActionEvent) {
            pluginFilePaths.toLivePlugins().forEach { it.runWith(pluginRunners, event) }
        }

        @JvmStatic fun runPluginsTests(pluginFilePaths: List<FilePath>, event: AnActionEvent) {
            pluginFilePaths.toLivePlugins().forEach { it.runWith(pluginTestRunners, event) }
        }
    }
}

class RunPluginTestsAction: AnAction("Run Plugin Tests", "Run plugin integration tests", Icons.testPluginIcon), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        runWriteAction { FileDocumentManager.getInstance().saveAllDocuments() }
        runPluginsTests(event.selectedFiles(), event)
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.selectedFiles().canBeHandledBy(pluginTestRunners)
    }
}

data class LivePlugin(val path: FilePath) {
    val id: String = path.toFile().name
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

private fun <T> runOnEdt(f: () -> T): T {
    val result = AtomicReference<T>()
    ApplicationManager.getApplication().invokeAndWait({ result.set(f()) }, NON_MODAL)
    return result.get()
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

class Binding(
    val project: Project?,
    val isIdeStartup: Boolean,
    val pluginPath: String,
    val pluginDisposable: Disposable
) {
    internal fun dispose() {
        Disposer.dispose(pluginDisposable)
        bindingByPluginId.remove(LivePlugin(pluginPath.toFilePath()).id)
    }

    companion object {
        private val bindingByPluginId = HashMap<String, Binding>()

        fun lookup(livePlugin: LivePlugin): Binding? =
            bindingByPluginId[livePlugin.id]

        fun create(livePlugin: LivePlugin, event: AnActionEvent): Binding {
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
    }
}

fun systemEnvironment(): Map<String, String> = HashMap(System.getenv())

fun List<FilePath>.canBeHandledBy(pluginRunners: List<PluginRunner>): Boolean =
    mapNotNull { path -> path.findParentPluginFolder() }
        .any { folder ->
            pluginRunners.any { runner ->
                folder.allFiles().any { it.name == runner.scriptName }
            }
        }

fun List<FilePath>.toLivePlugins() =
    mapNotNull { it.findParentPluginFolder() }.distinct().map { LivePlugin(it) }

private fun displayError(pluginId: String, error: AnError, project: Project?) {
    val (title, message) = when (error) {
        is LoadingError -> Pair("Loading error: $pluginId", error.message + if (error.throwable != null) "\n" + IdeUtil.unscrambleThrowable(error.throwable) else "")
        is RunningError -> Pair("Running error: $pluginId", IdeUtil.unscrambleThrowable(error.throwable))
    }
    displayError(title, message, project)
}
