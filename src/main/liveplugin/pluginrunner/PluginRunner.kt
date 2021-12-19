package liveplugin.pluginrunner

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.util.lang.UrlClassLoader
import groovy.lang.GroovyClassLoader
import liveplugin.Result
import liveplugin.asFailure
import liveplugin.asSuccess
import liveplugin.pluginrunner.AnError.LoadingError
import liveplugin.toUrl
import org.apache.oro.io.GlobFilenameFilter
import java.io.File
import java.io.FileFilter
import java.util.*

interface ExecutablePlugin

interface PluginRunner {
    val scriptName: String

    fun setup(plugin: LivePlugin, project: Project?): Result<ExecutablePlugin, AnError>

    fun run(executablePlugin: ExecutablePlugin, binding: Binding): Result<Unit, AnError>

    object ClasspathAddition {
        fun createClassLoaderWithDependencies(
            additionalClasspath: List<File>,
            pluginDescriptors: List<IdeaPluginDescriptor>,
            plugin: LivePlugin
        ): Result<ClassLoader, LoadingError> {
            val classLoader = GroovyClassLoader(createParentClassLoader(pluginDescriptors, plugin))

            additionalClasspath.forEach { file ->
                if (!file.exists()) return LoadingError("Didn't find plugin dependency '${file.absolutePath}'.").asFailure()
            }
            additionalClasspath.forEach { file -> classLoader.addURL(file.toUrl()) }

            return classLoader.asSuccess()
        }

        private fun createParentClassLoader(pluginDescriptors: List<IdeaPluginDescriptor>, plugin: LivePlugin): ClassLoader {
            val parentLoaders = pluginDescriptors.mapNotNull { it.pluginClassLoader } + PluginRunner::class.java.classLoader
            return PluginClassLoader_Fork(
                UrlClassLoader.build().files(emptyList()).allowLock(true).useCache(),
                parentLoaders.toTypedArray(),
                DefaultPluginDescriptor(plugin.id),
                null,
                PluginManagerCore::class.java.classLoader,
                null,
                null
            )
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

                    val dependenciesDescriptors = descriptor.dependencies.mapNotNull {
                        if (it.isOptional) null else PluginManagerCore.getPlugin(it.pluginId)
                    }

                    @Suppress("UnstableApiUsage") // This is a "temporary" hack for https://youtrack.jetbrains.com/issue/IDEA-206274
                    val dependenciesDescriptors2 =
                        if (descriptor !is IdeaPluginDescriptorImpl) emptyList()
                        else descriptor.dependencies.plugins.mapNotNull { PluginManagerCore.getPlugin(it.id) }

                    queue.addAll((dependenciesDescriptors + dependenciesDescriptors2).distinctBy { it.pluginId })
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
}

sealed class AnError {
    data class LoadingError(val message: String = "", val throwable: Throwable? = null) : AnError()
    data class RunningError(val throwable: Throwable) : AnError()
}
