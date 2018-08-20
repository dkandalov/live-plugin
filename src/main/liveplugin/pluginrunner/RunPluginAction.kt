package liveplugin.pluginrunner

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState.NON_MODAL
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil.toSystemIndependentName
import liveplugin.Icons
import liveplugin.IdeUtil
import liveplugin.IdeUtil.SingleThreadBackgroundRunner
import liveplugin.LivePluginAppComponent.Companion.checkThatGroovyIsOnClasspath
import liveplugin.LivePluginAppComponent.Companion.clojureIsOnClassPath
import liveplugin.LivePluginAppComponent.Companion.livePluginsPath
import liveplugin.LivePluginAppComponent.Companion.scalaIsOnClassPath
import liveplugin.MyFileUtil.allFilesInDirectory
import liveplugin.MyFileUtil.findScriptFileIn
import liveplugin.pluginrunner.GroovyPluginRunner.Companion.mainScript
import liveplugin.pluginrunner.PluginRunner.Companion.ideStartup
import liveplugin.pluginrunner.kotlin.KotlinPluginRunner
import java.io.File
import java.util.*


class RunPluginAction: AnAction("Run Plugin", "Run selected plugins", Icons.runPluginIcon), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        IdeUtil.saveAllFiles()
        val errorReporter = ErrorReporter()
        val pluginRunners = createPluginRunners(errorReporter)
        runPlugins(event.selectedFiles(), event, errorReporter, pluginRunners)
    }

    override fun update(event: AnActionEvent) {
        val pluginRunners = createPluginRunners(ErrorReporter())
        event.presentation.isEnabled = event.selectedFiles().canBeHandledBy(pluginRunners)
    }
}


const val pluginDisposableKey = "pluginDisposable"
const val pluginPathKey = "pluginPath"
const val isIdeStartupKey = "isIdeStartup"
const val projectKey = "project"

private val backgroundRunner = SingleThreadBackgroundRunner("LivePlugin runner thread")
private val bindingByPluginId = WeakHashMap<String, Map<String, Any?>>()

fun runPlugins(
    pluginFilePaths: List<String>,
    event: AnActionEvent,
    errorReporter: ErrorReporter,
    pluginRunners: List<PluginRunner>
) {
    if (!checkThatGroovyIsOnClasspath()) return

    val project = event.project
    val isIdeStartup = event.place == ideStartup

    val pluginDataAndRunners = pluginFilePaths.mapNotNull { path ->
        val pluginFolder = pluginFolder(path)
        val pluginId = File(pluginFolder).name

        val pluginRunner =
            pluginRunners.find { it.scriptName() == File(path).name } ?:
                pluginRunners.find { findScriptFileIn(pluginFolder, it.scriptName()) != null }

        if (pluginRunner == null) {
            errorReporter.addNoScriptError(pluginId, pluginRunners.map { it.scriptName() })
            null
        } else {
            Triple(pluginId, pluginFolder!!, pluginRunner)
        }
    }.distinct()

    val tasks = pluginDataAndRunners.map { (pluginId, pluginFolder, pluginRunner) ->
        val task = {
            try {
                val oldBinding = bindingByPluginId[pluginId]
                if (oldBinding != null) {
                    runOnEdt {
                        try {
                            Disposer.dispose(oldBinding[pluginDisposableKey] as Disposable)
                        } catch (e: Exception) {
                            errorReporter.addRunningError(pluginId, e)
                        }
                    }
                }
                val binding = createBinding(pluginFolder, project, isIdeStartup)
                bindingByPluginId[pluginId] = binding

                pluginRunner.runPlugin(pluginFolder, pluginId, binding, ::runOnEdt)
            } catch (e: Error) {
                errorReporter.addLoadingError(pluginId, e)
            } finally {
                errorReporter.reportAllErrors { title, message -> IdeUtil.displayError(title, message, project) }
            }
        }
        Triple(pluginId, pluginFolder, task)
    }

    tasks.forEach { (pluginId, _, task) ->
        backgroundRunner.run(project, "Loading live-plugin '$pluginId'", task)
    }
}

private fun runOnEdt(f: () -> Unit) = ApplicationManager.getApplication().invokeAndWait(f, NON_MODAL)

fun createPluginRunners(errorReporter: ErrorReporter): List<PluginRunner> {
    return ArrayList<PluginRunner>().apply {
        add(GroovyPluginRunner(mainScript, errorReporter, environment()))
        add(KotlinPluginRunner(errorReporter, environment()))
        if (scalaIsOnClassPath()) add(ScalaPluginRunner(errorReporter, environment()))
        if (clojureIsOnClassPath()) add(ClojurePluginRunner(errorReporter, environment()))
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

fun environment(): MutableMap<String, String> = HashMap(System.getenv())

fun pluginFolder(path: String): String? {
    val parent = File(path).parent ?: return null
    return if (toSystemIndependentName(parent) == livePluginsPath) path else pluginFolder(parent)
}

fun List<String>.canBeHandledBy(pluginRunners: List<PluginRunner>): Boolean =
    mapNotNull { path -> pluginFolder(path) }
        .any { folder ->
            pluginRunners.any { runner ->
                allFilesInDirectory(File(folder)).any { runner.scriptName() == it.name }
            }
        }

fun AnActionEvent.selectedFiles(): List<String> =
    (dataContext.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: emptyArray()).map { it.path }
