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
import liveplugin.pluginrunner.RunPluginAction.Companion.runPluginsTests
import liveplugin.pluginrunner.groovy.GroovyPluginRunner
import liveplugin.pluginrunner.kotlin.KotlinPluginRunner
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference


class RunPluginAction: AnAction("Run Plugin", "Run selected plugins", Icons.runPluginIcon), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        IdeUtil.saveAllFiles()
        runPlugins(event.selectedFiles(), event)
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.selectedFiles().canBeHandledBy(pluginRunners)
    }

    companion object {
        @JvmStatic fun runPlugins(pluginFilePaths: List<String>, event: AnActionEvent) {
            runPlugins(pluginFilePaths, event, pluginRunners)
        }

        @JvmStatic fun runPluginsTests(pluginFilePaths: List<String>, event: AnActionEvent) {
            runPlugins(pluginFilePaths, event, pluginTestRunners)
        }
    }
}

class RunPluginTestsAction: AnAction("Run Plugin Tests", "Run Plugin Integration Tests", Icons.testPluginIcon), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        IdeUtil.saveAllFiles()
        runPluginsTests(event.selectedFiles(), event)
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.selectedFiles().canBeHandledBy(pluginTestRunners)
    }
}


const val pluginDisposableKey = "pluginDisposable"
const val pluginPathKey = "pluginPath"
const val isIdeStartupKey = "isIdeStartup"
const val projectKey = "project"

private val backgroundRunner = HashMap<String, SingleThreadBackgroundRunner>()
private val bindingByPluginId = ConcurrentHashMap<String, Map<String, Any?>>()

private fun <T> runOnEdt(f: () -> T): T {
    val result = AtomicReference<T>()
    ApplicationManager.getApplication().invokeAndWait({ result.set(f()) }, NON_MODAL)
    return result.get()
}

private data class LivePlugin(val path: String) {
    val pluginId: String get() = File(path).name
}

private fun runPlugins(pluginFilePaths: List<String>, event: AnActionEvent, pluginRunners: List<PluginRunner>) {
    if (!checkThatGroovyIsOnClasspath()) return
    pluginFilePaths
        .map { LivePlugin(findPluginFolder(it)!!) }.distinct()
        .forEach { it.runWith(pluginRunners, event.project, event.place == ideStartup) }
}

private fun LivePlugin.runWith(pluginRunners: List<PluginRunner>, project: Project?, isIdeStartup: Boolean) {
    val pluginRunner = pluginRunners.find { findScriptFileIn(path, it.scriptName) != null }
        ?: return IdeUtil.displayError(LoadingError("Plugin: \"$pluginId\". Startup script was not found. Tried: ${pluginRunners.map { it.scriptName }}"), project)

    val backgroundRunner = backgroundRunner.getOrPut(pluginRunner.scriptName, { SingleThreadBackgroundRunner() })
    backgroundRunner.run(project, "Running live-plugin '$pluginId'") {
        val binding = createBinding(this, project, isIdeStartup)
        pluginRunner.runPlugin(path, pluginId, binding, ::runOnEdt)
            .peekFailure { IdeUtil.displayError(it, project) }
    }
}

private val pluginRunners = listOf(GroovyPluginRunner.main, KotlinPluginRunner.main)
private val pluginTestRunners = listOf(GroovyPluginRunner.test, KotlinPluginRunner.test)

private class SingleThreadBackgroundRunner(threadName: String = "LivePlugin runner thread") {
    private val singleThreadExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, threadName) }

    fun run(project: Project?, taskDescription: String, runnable: () -> Unit) {
        singleThreadExecutor.submit {
            val latch = CountDownLatch(1)
            object: Task.Backgroundable(project, taskDescription, false, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
                override fun run(indicator: ProgressIndicator) {
                    runnable()
                    latch.countDown()
                }
            }.queue()
            latch.await()
        }
    }
}

private fun createBinding(livePlugin: LivePlugin, project: Project?, isIdeStartup: Boolean): Map<String, Any?> {
    val oldBinding = bindingByPluginId[livePlugin.pluginId]
    if (oldBinding != null) {
        runOnEdt {
            try {
                Disposer.dispose(oldBinding[pluginDisposableKey] as Disposable)
            } catch (e: Exception) {
                IdeUtil.displayError(RunningError(livePlugin.pluginId, e), project)
            }
        }
    }

    val disposable = object: Disposable {
        override fun dispose() {}
        override fun toString() = "LivePlugin: $livePlugin"
    }
    Disposer.register(ApplicationManager.getApplication(), disposable)

    val binding = mapOf(
        projectKey to project,
        isIdeStartupKey to isIdeStartup,
        pluginPathKey to livePlugin.path,
        pluginDisposableKey to disposable
    )
    bindingByPluginId[livePlugin.pluginId] = binding

    return binding
}

fun systemEnvironment(): Map<String, String> = HashMap(System.getenv())

private fun List<String>.canBeHandledBy(pluginRunners: List<PluginRunner>): Boolean =
    mapNotNull { path -> findPluginFolder(path) }
        .any { folder ->
            pluginRunners.any { runner ->
                allFilesInDirectory(File(folder)).any { runner.scriptName == it.name }
            }
        }

private fun AnActionEvent.selectedFiles(): List<String> =
    (dataContext.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: emptyArray()).map { it.path }

private fun findPluginFolder(fullPath: String, path: String = fullPath): String? {
    val parent = File(path).parent ?: return null
    return if (toSystemIndependentName(parent) == LivePluginPaths.livePluginsPath) path else findPluginFolder(fullPath, parent)
}
