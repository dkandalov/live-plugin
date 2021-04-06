package liveplugin.pluginrunner

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState.NON_MODAL
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import liveplugin.*
import liveplugin.IdeUtil.displayError
import liveplugin.IdeUtil.ideStartupActionPlace
import liveplugin.LivePluginAppComponent.Companion.findPluginFolder
import liveplugin.pluginrunner.AnError.LoadingError
import liveplugin.pluginrunner.AnError.RunningError
import liveplugin.pluginrunner.RunPluginAction.Companion.runPluginsTests
import liveplugin.pluginrunner.groovy.GroovyPluginRunner
import liveplugin.pluginrunner.kotlin.KotlinPluginRunner
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

private val pluginRunners = listOf(GroovyPluginRunner.main, KotlinPluginRunner.main)
private val pluginTestRunners = listOf(GroovyPluginRunner.test, KotlinPluginRunner.test)

class RunPluginAction: AnAction("Run Plugin", "Run selected plugins", Icons.runPluginIcon), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        IdeUtil.saveAllFiles()
        runPlugins(event.selectedFilePaths(), event)
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.selectedFilePaths().canBeHandledBy(pluginRunners)
    }

    companion object {
        @JvmStatic fun runPlugins(pluginFilePaths: List<FilePath>, event: AnActionEvent) {
            pluginFilePaths.toLivePlugins().forEach { it.runWith(pluginRunners, event) }
        }

        @JvmStatic fun runPluginsTests(pluginFilePaths: List<FilePath>, event: AnActionEvent) {
            pluginFilePaths.toLivePlugins().forEach { it.runWith(pluginTestRunners, event) }
        }

        private fun List<FilePath>.toLivePlugins() =
            mapNotNull { it.findPluginFolder() }.distinct().map { LivePlugin(it) }
    }
}

class RunPluginTestsAction: AnAction("Run Plugin Tests", "Run plugin integration tests", Icons.testPluginIcon), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        IdeUtil.saveAllFiles()
        runPluginsTests(event.selectedFilePaths(), event)
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.selectedFilePaths().canBeHandledBy(pluginTestRunners)
    }
}

data class LivePlugin(val path: FilePath) {
    val id: String = path.toFile().name
}

private fun LivePlugin.runWith(pluginRunners: List<PluginRunner>, event: AnActionEvent) {
    val pluginRunner = pluginRunners.find { path.find(it.scriptName) != null }
        ?: return displayError(LoadingError(id, message = "Startup script was not found. Tried: ${pluginRunners.map { it.scriptName }}"), event.project)
    val binding = Binding.create(this, event)

    runInBackground(event.project, "Running live-plugin '$id'") {
        pluginRunner.runPlugin(this, binding, ::runOnEdt)
    }.whenComplete { result, throwable ->
        if (throwable != null) displayError(LoadingError(id, message = "Unexpected Error", throwable), event.project)
        else result.peekFailure { displayError(it, event.project) }
    }
}

private fun <T> runOnEdt(f: () -> T): T {
    val result = AtomicReference<T>()
    ApplicationManager.getApplication().invokeAndWait({ result.set(f()) }, NON_MODAL)
    return result.get()
}

private fun runInBackground(project: Project?, taskDescription: String, function: () -> Result<Unit, AnError>): CompletableFuture<Result<Unit, AnError>> {
    val futureResult = CompletableFuture<Result<Unit, AnError>>()
    if (project == null) {
        // Can't use ProgressManager here because it will show with modal dialogs on IDE startup when there is no project
        try {
            futureResult.complete(function())
        } catch (e: Exception) {
            futureResult.completeExceptionally(e)
        }
    } else {
        ProgressManager.getInstance().run(object: Task.Backgroundable(project, taskDescription, false, ALWAYS_BACKGROUND) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    futureResult.complete(function())
                } catch (e: Exception) {
                    futureResult.completeExceptionally(e)
                }
            }
        })
    }
    return futureResult
}

class Binding(
    val project: Project?,
    val isIdeStartup: Boolean,
    val pluginPath: String,
    val pluginDisposable: Disposable
) {
    fun toMap(): Map<String, Any?> = mapOf(
        projectKey to project,
        isIdeStartupKey to isIdeStartup,
        pluginPathKey to pluginPath,
        pluginDisposableKey to pluginDisposable
    )

    internal fun dispose() {
        Disposer.dispose(pluginDisposable)
        bindingByPluginId.remove(LivePlugin(pluginPath.toFilePath()).id)
    }

    companion object {
        private const val pluginDisposableKey = "pluginDisposable"
        private const val pluginPathKey = "pluginPath"
        private const val isIdeStartupKey = "isIdeStartup"
        private const val projectKey = "project"
        private val bindingByPluginId = HashMap<String, Binding>()

        fun lookup(livePlugin: LivePlugin): Binding? =
            bindingByPluginId[livePlugin.id]

        fun create(livePlugin: LivePlugin, event: AnActionEvent): Binding {
            val oldBinding = bindingByPluginId[livePlugin.id]
            if (oldBinding != null) {
                try {
                    Disposer.dispose(oldBinding.pluginDisposable)
                } catch (e: Exception) {
                    displayError(RunningError(livePlugin.id, e), event.project)
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

private fun List<FilePath>.canBeHandledBy(pluginRunners: List<PluginRunner>): Boolean =
    mapNotNull { path -> path.findPluginFolder() }
        .any { folder ->
            pluginRunners.any { runner ->
                folder.allFiles().any { it.name == runner.scriptName }
            }
        }

fun AnActionEvent.selectedFilePaths(): List<FilePath> =
    (dataContext.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: emptyArray())
        .map { it.toFilePath() }
