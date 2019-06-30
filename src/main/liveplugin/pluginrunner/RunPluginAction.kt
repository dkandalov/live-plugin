package liveplugin.pluginrunner

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState.NON_MODAL
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import liveplugin.*
import liveplugin.LivePluginAppComponent.Companion.checkThatGroovyIsOnClasspath
import liveplugin.pluginrunner.AnError.LoadingError
import liveplugin.pluginrunner.AnError.RunningError
import liveplugin.pluginrunner.PluginRunner.Companion.ideStartup
import liveplugin.pluginrunner.Result.Failure
import liveplugin.pluginrunner.groovy.GroovyPluginRunner
import liveplugin.pluginrunner.groovy.GroovyPluginRunner.Companion.mainScript
import liveplugin.pluginrunner.kotlin.KotlinPluginRunner
import java.io.File
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference


class RunPluginAction: AnAction("Run Plugin", "Run selected plugins", Icons.runPluginIcon), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        IdeUtil.saveAllFiles()
        val errorReporter = ErrorReporter()
        runPlugins(event.selectedFiles(), event, errorReporter)
    }

    override fun update(event: AnActionEvent) {
        val pluginRunners = pluginRunners
        event.presentation.isEnabled = event.selectedFiles().canBeHandledBy(pluginRunners)
    }
}


const val pluginDisposableKey = "pluginDisposable"
const val pluginPathKey = "pluginPath"
const val isIdeStartupKey = "isIdeStartup"
const val projectKey = "project"

private val backgroundRunner = HashMap<String, SingleThreadBackgroundRunner>()
private val bindingByPluginId = WeakHashMap<String, Map<String, Any?>>()

fun runPlugins(pluginFilePaths: List<String>, event: AnActionEvent, errorReporter: ErrorReporter) {
    if (!checkThatGroovyIsOnClasspath()) return

    val project = event.project
    val isIdeStartup = event.place == ideStartup

    val pluginDataAndRunners = pluginFilePaths.mapNotNull { path ->
        val pluginFolder = pluginFolder(path)
        val pluginId = File(pluginFolder).name

        val pluginRunner =
            pluginRunners.find { it.scriptName == File(path).name } ?:
            pluginRunners.find { findScriptFileIn(pluginFolder, it.scriptName) != null }

        if (pluginRunner == null) {
            errorReporter.addNoScriptError(pluginId, pluginRunners.map { it.scriptName })
            null
        } else {
            Triple(pluginId, pluginFolder!!, pluginRunner)
        }
    }.distinct()

    pluginDataAndRunners.forEach { (pluginId, pluginFolder, pluginRunner) ->
        val task: () -> Result<Unit, AnError> = {
            try {
                val oldBinding = bindingByPluginId[pluginId]
                if (oldBinding != null) {
                    runOnEdt {
                        try {
                            Disposer.dispose(oldBinding[pluginDisposableKey] as Disposable)
                        } catch (e: Exception) {
                            IdeUtil.displayError(RunningError(pluginId, e), project)
                        }
                    }
                }
                val binding = createBinding(pluginFolder, project, isIdeStartup)
                bindingByPluginId[pluginId] = binding
                backgroundRunner[pluginRunner.scriptName] = SingleThreadBackgroundRunner("LivePlugin runner thread")

                pluginRunner.runPlugin(pluginFolder, pluginId, binding, ::runOnEdt)

            } catch (e: Throwable) {
                Failure(LoadingError(pluginId, throwable = e))
            }
        }

        val runner = backgroundRunner[pluginRunner.scriptName]!!
        runner.run(project, "Loading live-plugin '$pluginId'") {
            task().peekFailure { IdeUtil.displayError(it, project) }
        }
    }
}

private fun <T> runOnEdt(f: () -> T): T {
    val result = AtomicReference<T>()
    ApplicationManager.getApplication().invokeAndWait({ result.set(f()) }, NON_MODAL)
    return result.get()
}

val pluginRunners = listOf(GroovyPluginRunner(mainScript), KotlinPluginRunner())

private class SingleThreadBackgroundRunner(threadName: String) {
    private val singleThreadExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, threadName) }

    fun run(project: Project?, taskDescription: String, runnable: () -> Unit) {
        object: Task.Backgroundable(project, taskDescription, false, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    singleThreadExecutor.submit(runnable).get()
                } catch (ignored: InterruptedException) {
                } catch (ignored: ExecutionException) { }
            }
        }.queue()
    }
}

private fun createBinding(pluginFolderPath: String, project: Project?, isIdeStartup: Boolean): Map<String, Any?> {
    val disposable = object: Disposable {
        override fun dispose() {}
        override fun toString() = "LivePlugin: $pluginFolderPath"
    }
    Disposer.register(ApplicationManager.getApplication(), disposable)

    return mapOf(
        projectKey to project,
        isIdeStartupKey to isIdeStartup,
        pluginPathKey to pluginFolderPath,
        pluginDisposableKey to disposable
    )
}

fun systemEnvironment(): Map<String, String> = HashMap(System.getenv())

private fun pluginFolder(path: String): String? {
    val parent = File(path).parent ?: return null
    return if (toSystemIndependentName(parent) == LivePluginPaths.livePluginsPath) path else pluginFolder(parent)
}

fun List<String>.canBeHandledBy(pluginRunners: List<PluginRunner>): Boolean =
    mapNotNull { path -> pluginFolder(path) }
        .any { folder ->
            pluginRunners.any { runner ->
                allFilesInDirectory(File(folder)).any { runner.scriptName == it.name }
            }
        }

fun AnActionEvent.selectedFiles(): List<String> =
    (dataContext.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: emptyArray()).map { it.path }
