package liveplugin.pluginrunner

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState.NON_MODAL
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import liveplugin.*
import liveplugin.IdeUtil.ideStartupActionPlace
import liveplugin.pluginrunner.AnError.LoadingError
import liveplugin.pluginrunner.AnError.RunningError
import liveplugin.pluginrunner.Binding.Companion.create
import liveplugin.pluginrunner.RunPluginAction.Companion.runPluginsTests
import liveplugin.pluginrunner.groovy.GroovyPluginRunner
import liveplugin.pluginrunner.kotlin.KotlinPluginRunner
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

private val pluginRunners = listOf(GroovyPluginRunner.main, KotlinPluginRunner.main)
private val pluginTestRunners = listOf(GroovyPluginRunner.test, KotlinPluginRunner.test)

class RunPluginAction: AnAction("Run Plugin", "Run selected plugins", Icons.runPluginIcon), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        IdeUtil.saveAllFiles()
        runPlugins(event.selectedFiles(), event)
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.selectedFiles().canBeHandledBy(pluginRunners)
    }

    companion object {
        @JvmStatic fun runPlugins(pluginFilePaths: List<FilePath>, event: AnActionEvent) {
            runPlugins(pluginFilePaths, event, pluginRunners)
        }

        @JvmStatic fun runPluginsTests(pluginFilePaths: List<FilePath>, event: AnActionEvent) {
            runPlugins(pluginFilePaths, event, pluginTestRunners)
        }
    }
}

class RunPluginTestsAction: AnAction("Run Plugin Tests", "Run plugin integration tests", Icons.testPluginIcon), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        IdeUtil.saveAllFiles()
        runPluginsTests(event.selectedFiles(), event)
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.selectedFiles().canBeHandledBy(pluginTestRunners)
    }
}

private val backgroundRunner = BackgroundRunner()

private fun <T> runOnEdt(f: () -> T): T {
    val result = AtomicReference<T>()
    ApplicationManager.getApplication().invokeAndWait({ result.set(f()) }, NON_MODAL)
    return result.get()
}

data class LivePlugin(val path: FilePath) {
    val id: String = path.toFile().name
}

private fun runPlugins(pluginFilePaths: List<FilePath>, event: AnActionEvent, pluginRunners: List<PluginRunner>) {
    pluginFilePaths
        .mapNotNull { findPluginFolder(it) }.distinct()
        .forEach { LivePlugin(it).runWith(pluginRunners, event) }
}

private fun LivePlugin.runWith(pluginRunners: List<PluginRunner>, event: AnActionEvent) {
    val pluginRunner = pluginRunners.find { path.find(it.scriptName) != null }
        ?: return IdeUtil.displayError(LoadingError("Plugin: \"$id\". Startup script was not found. Tried: ${pluginRunners.map { it.scriptName }}"), event.project)

    val binding = create(this, event)

    backgroundRunner.run(pluginRunner.scriptName, event.project, "Running live-plugin '$id'") {
        pluginRunner.runPlugin(this, binding, ::runOnEdt).peekFailure { IdeUtil.displayError(it, event.project) }
    }
}



private class BackgroundRunner {
    private val runnerById = HashMap<String, SingleThreadBackgroundRunner>()

    fun run(id: String, project: Project?, taskDescription: String, runnable: () -> Unit) {
        runnerById.getOrPut(id, { SingleThreadBackgroundRunner() })
            .run(project, taskDescription, runnable)
    }

    private class SingleThreadBackgroundRunner(threadName: String = "LivePlugin runner thread") {
        private val singleThreadExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, threadName) }

        fun run(project: Project?, taskDescription: String, runnable: () -> Unit) {
            singleThreadExecutor.submit {
                val latch = CountDownLatch(1)
                object: Task.Backgroundable(project, taskDescription, false, ALWAYS_BACKGROUND) {
                    override fun run(indicator: ProgressIndicator) {
                        try {
                            runnable()
                        } finally {
                            latch.countDown()
                        }
                    }
                }.queue()
                latch.await()
            }
        }
    }
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

    companion object {
        const val pluginDisposableKey = "pluginDisposable"
        const val pluginPathKey = "pluginPath"
        const val isIdeStartupKey = "isIdeStartup"
        const val projectKey = "project"
        private val bindingByPluginId = HashMap<String, Binding>()

        fun create(livePlugin: LivePlugin, event: AnActionEvent): Binding {
            val oldBinding = bindingByPluginId[livePlugin.id]
            if (oldBinding != null) {
                try {
                    Disposer.dispose(oldBinding.pluginDisposable)
                } catch (e: Exception) {
                    IdeUtil.displayError(RunningError(livePlugin.id, e), event.project)
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
    mapNotNull { path -> findPluginFolder(path) }
        .any { folder ->
            pluginRunners.any { runner ->
                folder.allFiles().any { it.name == runner.scriptName }
            }
        }

private fun AnActionEvent.selectedFiles(): List<FilePath> =
    (dataContext.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: emptyArray())
        .map { it.toFilePath() }

// TODO similar to VirtualFile.pluginFolder
private fun findPluginFolder(fullPath: FilePath, path: FilePath = fullPath): FilePath? {
    val parent = path.toFile().parent?.toFilePath() ?: return null
    return if (parent == LivePluginPaths.livePluginsPath) path
    else findPluginFolder(fullPath, parent)
}
