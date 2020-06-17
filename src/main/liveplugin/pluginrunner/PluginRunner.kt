package liveplugin.pluginrunner

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import groovy.lang.GroovyClassLoader
import liveplugin.pluginrunner.AnError.LoadingError
import liveplugin.pluginrunner.Result.Failure
import liveplugin.pluginrunner.Result.Success
import liveplugin.toUrl
import org.apache.oro.io.GlobFilenameFilter
import java.io.File
import java.io.FileFilter
import java.net.URL
import kotlin.reflect.jvm.javaType

interface PluginRunner {

    val scriptName: String

    /**
     * @param plugin plugin to be run
     * @param binding map with implicit variables available in plugin script
     * @param runOnEDT callback which should be used to run plugin code on EDT
     */
    fun runPlugin(plugin: LivePlugin, binding: Binding, runOnEDT: (() -> Result<Unit, AnError>) -> Result<Unit, AnError>): Result<Unit, AnError>


    object ClasspathAddition {
        private val logger = Logger.getInstance(ClasspathAddition::class.java)

        fun createClassLoaderWithDependencies(
            additionalClasspath: List<File>,
            dependenciesOnIdePlugins: List<IdeaPluginDescriptor>,
            plugin: LivePlugin
        ): Result<ClassLoader, LoadingError> {
            val parentLoader = createParentClassLoader(dependenciesOnIdePlugins, plugin).onFailure { return it }
            val classLoader = GroovyClassLoader(parentLoader)

            additionalClasspath.forEach { file ->
                if (!file.exists()) return Failure(LoadingError(plugin.id, "Didn't find plugin dependency '${file.absolutePath}'."))
            }
            additionalClasspath.forEach { file -> classLoader.addURL(file.toUrl()) }

            return Success(classLoader)
        }

        private fun createParentClassLoader(dependenciesOnIdePlugins: List<IdeaPluginDescriptor>, plugin: LivePlugin): Result<ClassLoader, LoadingError> {
            val parentLoaders = dependenciesOnIdePlugins.map { it.pluginClassLoader } + PluginRunner::class.java.classLoader

            val constructors = PluginClassLoader::class.constructors
            val pluginClassLoader = constructors
                .find { it.parameters[2].type.javaType == IdeaPluginDescriptor::class.java }
                ?.call( // For compatibility with PluginClassLoader constructor in 202.5103.13-EAP-SNAPSHOT
                    emptyList<URL>(),
                    parentLoaders.toTypedArray(),
                    DefaultPluginDescriptor(plugin.id),
                    null
                ) ?: constructors
                .find { it.parameters.size == 5 && it.parameters[2].type.javaType == PluginId::class.java }
                ?.call( // For compatibility with PluginClassLoader constructor in 201.6668.113
                    emptyList<URL>(),
                    parentLoaders.toTypedArray(),
                    PluginId.getId(plugin.id),
                    "1.0.0",
                    null
                )
            return if (pluginClassLoader != null) Success(pluginClassLoader)
            else Failure(LoadingError(plugin.id, "LivePlugin compatibility error with IntelliJ API"))
        }

        fun findDependenciesOnIdePlugins(lines: List<String>, keyword: String): List<IdeaPluginDescriptor> {
            return lines.filter { it.startsWith(keyword) }
                .map { line -> line.replace(keyword, "").trim { it <= ' ' } }
                .map { PluginManagerCore.getPlugin(PluginId.getId(it)) ?: error("Failed to find jar for dependent plugin '$it'.") }
        }

        fun findClasspathAdditions(lines: List<String>, prefix: String, environment: Map<String, String>): Result<List<File>, String> {
            return lines
                .filter { it.startsWith(prefix) }
                .map { line ->
                    val path = line.replace(prefix, "").trim { it <= ' ' }
                    inlineEnvironmentVariables(path, environment)
                }
                .map { path ->
                    val matchingFiles = findMatchingFiles(path)
                    if (matchingFiles.isEmpty()) Failure(path) else Success(matchingFiles)
                }
                .allValues()
                .map { matchingFiles -> matchingFiles.flatten().map { File(it) } }
        }

        private fun findMatchingFiles(pathAndPattern: String): List<String> {
            if (File(pathAndPattern).exists()) return listOf(pathAndPattern)

            val separatorIndex = pathAndPattern.lastIndexOf(File.separator)
            val path = pathAndPattern.substring(0, separatorIndex + 1)
            val pattern = pathAndPattern.substring(separatorIndex + 1)

            val files = File(path).listFiles(GlobFilenameFilter(pattern) as FileFilter) ?: emptyArray()
            return files.map { it.absolutePath }
        }

        private fun inlineEnvironmentVariables(path: String, environment: Map<String, String>): String {
            var result = path
            var wasModified = false
            for ((key, value) in environment) {
                result = result.replace("$$key", value)
                wasModified = true
            }
            if (wasModified) logger.info("Additional classpath with inlined env variables: $result")
            return result
        }
    }
}
