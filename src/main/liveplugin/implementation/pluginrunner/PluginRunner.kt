package liveplugin.implementation.pluginrunner

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.util.io.exists
import com.intellij.util.lang.ClassPath
import com.intellij.util.lang.UrlClassLoader
import liveplugin.implementation.LivePlugin
import liveplugin.implementation.common.*
import liveplugin.implementation.pluginrunner.groovy.GroovyPluginRunner.Companion.mainGroovyPluginRunner
import liveplugin.implementation.pluginrunner.groovy.GroovyPluginRunner.Companion.testGroovyPluginRunner
import liveplugin.implementation.pluginrunner.kotlin.KotlinPluginRunner.Companion.mainKotlinPluginRunner
import liveplugin.implementation.pluginrunner.kotlin.KotlinPluginRunner.Companion.testKotlinPluginRunner
import org.apache.oro.io.GlobFilenameFilter
import java.io.File
import java.io.FileFilter
import java.util.*

interface PluginRunner {
    val scriptName: String

    fun setup(plugin: LivePlugin, project: Project?): Result<ExecutablePlugin, SetupError>

    fun run(executablePlugin: ExecutablePlugin, binding: Binding): Result<Unit, RunningError>

    companion object {
        @JvmStatic fun runPlugins(livePlugins: Collection<LivePlugin>, event: AnActionEvent) {
            livePlugins.forEach { it.runWith(pluginRunners, event) }
        }

        @JvmStatic fun runPluginsTests(livePlugins: Collection<LivePlugin>, event: AnActionEvent) {
            livePlugins.forEach { it.runWith(pluginTestRunners, event) }
        }

        @JvmStatic fun unloadPlugins(livePlugins: Collection<LivePlugin>) {
            livePlugins.forEach { Binding.lookup(it)?.dispose() }
        }

        fun List<LivePlugin>.canBeHandledBy(pluginRunners: List<PluginRunner>): Boolean =
            any { livePlugin ->
                pluginRunners.any { runner ->
                    livePlugin.path.allFiles().any { it.name == runner.scriptName }
                }
            }
    }
}

interface ExecutablePlugin

val pluginRunners = listOf(mainGroovyPluginRunner, mainKotlinPluginRunner)
val pluginTestRunners = listOf(testGroovyPluginRunner, testKotlinPluginRunner)

fun systemEnvironment(): Map<String, String> = HashMap(System.getenv())

sealed class PluginError
data class SetupError(val message: String = "", val throwable: Throwable? = null) : PluginError()
data class RunningError(val throwable: Throwable) : PluginError()

object PluginDependencies {
    @Suppress("UnstableApiUsage")
    fun createClassLoaderWithDependencies(
        additionalClasspath: List<File>,
        pluginDescriptors: List<IdeaPluginDescriptor>,
        plugin: LivePlugin
    ): Result<ClassLoader, SetupError> {
        val additionalPaths = additionalClasspath.map { file -> file.toPath() }.onEach { path ->
            if (!path.exists()) return SetupError("Didn't find plugin dependency '${path.toFile().absolutePath}'.").asFailure()
        }
        val parentClassLoaders = pluginDescriptors.mapNotNull { it.pluginClassLoader } + PluginRunner::class.java.classLoader

        return PluginClassLoader_Fork(
            additionalPaths,
            ClassPath(additionalPaths, UrlClassLoader.build(), null, false),
            parentClassLoaders.toTypedArray(),
            DefaultPluginDescriptor(plugin.id),
            PluginManagerCore::class.java.classLoader,
            null,
            null,
            emptyList()
        ).asSuccess()
    }

    fun findPluginDescriptorsOfDependencies(lines: List<String>, keyword: String): List<Result<IdeaPluginDescriptor, String>> {
        return lines.filter { line -> line.startsWith(keyword) }
            .map { line -> line.replace(keyword, "").trim { it <= ' ' } }
            .map { PluginManagerCore.getPlugin(PluginId.getId(it))?.asSuccess() ?: "Failed to find dependent plugin '$it'.".asFailure() }
    }

    fun List<IdeaPluginDescriptor>.withTransitiveDependencies(): List<IdeaPluginDescriptor> {
        val result = HashSet<IdeaPluginDescriptor>()
        val queue = LinkedList(this)
        while (queue.isNotEmpty()) {
            val descriptor = queue.remove()
            if (descriptor !in result) {
                result.add(descriptor)

                val dependenciesDescriptors1 = descriptor.dependencies.mapNotNullTo(HashSet()) {
                    if (it.isOptional) null else PluginManagerCore.getPlugin(it.pluginId)
                }

                @Suppress("UnstableApiUsage") // This is a "temporary" hack for https://youtrack.jetbrains.com/issue/IDEA-206274
                val dependenciesDescriptors2 =
                    if (descriptor !is IdeaPluginDescriptorImpl) emptyList()
                    else descriptor.dependencies.plugins.mapNotNullTo(HashSet()) { PluginManagerCore.getPlugin(it.id) }

                val descriptors = (dependenciesDescriptors1 + dependenciesDescriptors2)
                    .filter { it.pluginId != PluginManagerCore.CORE_ID }.distinctBy { it.pluginId }

                queue.addAll(descriptors)
            }
        }
        return result.toList()
    }

    fun findClasspathAdditions(lines: List<String>, keyword: String, environment: Map<String, String>): List<Result<List<File>, String>> {
        return lines.filter { line -> line.startsWith(keyword) }
            .map { line -> line.replace(keyword, "").trim { it <= ' ' } }
            .map { line -> line.inlineEnvironmentVariables(environment) }
            .map { path ->
                val files = findMatchingFiles(path).map { File(it) }
                if (files.isEmpty()) path.asFailure() else files.asSuccess()
            }
    }

    private fun findMatchingFiles(pathAndPattern: String): List<String> {
        if (File(pathAndPattern).exists()) return listOf(pathAndPattern)

        val separatorIndex = pathAndPattern.lastIndexOf(File.separator)
        val path = pathAndPattern.substring(0, separatorIndex + 1)
        val pattern = pathAndPattern.substring(separatorIndex + 1)

        val files = File(path).listFiles(GlobFilenameFilter(pattern) as FileFilter) ?: emptyArray()
        return files.map { it.absolutePath }
    }

    private fun String.inlineEnvironmentVariables(environment: Map<String, String>): String {
        var result = this
        environment.forEach { (key, value) ->
            result = result.replace("$$key", value)
        }
        return result
    }
}

private fun LivePlugin.runWith(pluginRunners: List<PluginRunner>, event: AnActionEvent) {
    val project = event.project
    val binding = Binding.create(this, event)
    val pluginRunner = pluginRunners.find { path.find(it.scriptName) != null }
        ?: return displayError(id, SetupError(message = "Startup script was not found. Tried: ${pluginRunners.map { it.scriptName }}"), project)

    runInBackground(project, "Running live-plugin '$id'") {
        pluginRunner.setup(this, project)
            .flatMap { IdeUtil.runOnEdt { pluginRunner.run(it, binding) } }
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

fun displayError(pluginId: String, error: PluginError, project: Project?) {
    val (title, message) = when (error) {
        is SetupError   -> Pair("Loading error: $pluginId", error.message + if (error.throwable != null) "\n" + IdeUtil.unscrambleThrowable(error.throwable) else "")
        is RunningError -> Pair("Running error: $pluginId", IdeUtil.unscrambleThrowable(error.throwable))
    }
    IdeUtil.displayError(title, message, project)
}
