package liveplugin.pluginrunner

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.lang.UrlClassLoader
import groovy.lang.GroovyClassLoader
import liveplugin.pluginrunner.AnError.LoadingError
import liveplugin.pluginrunner.Result.Failure
import liveplugin.pluginrunner.Result.Success
import liveplugin.toUrl
import org.apache.oro.io.GlobFilenameFilter
import java.io.File
import java.io.FileFilter

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
            pluginDescriptors: List<IdeaPluginDescriptor>,
            plugin: LivePlugin
        ): Result<ClassLoader, LoadingError> {
            val classLoader = GroovyClassLoader(createParentClassLoader(pluginDescriptors, plugin))

            additionalClasspath.forEach { file ->
                if (!file.exists()) return Failure(LoadingError(plugin.id, "Didn't find plugin dependency '${file.absolutePath}'."))
            }
            additionalClasspath.forEach { file -> classLoader.addURL(file.toUrl()) }

            return Success(classLoader)
        }

        private fun createParentClassLoader(pluginDescriptors: List<IdeaPluginDescriptor>, plugin: LivePlugin): ClassLoader {
            val parentLoaders = pluginDescriptors.map { it.pluginClassLoader } + PluginRunner::class.java.classLoader
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
                .map { PluginManagerCore.getPlugin(PluginId.getId(it))?.asSuccess() ?: Failure("Failed to find dependent plugin '$it'.") }
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
            var wasModified = false
            environment.forEach { (key, value) ->
                result = result.replace("$$key", value)
                wasModified = true
            }
            if (wasModified) logger.info("Additional classpath with inlined env variables: $result")
            return result
        }
    }
}
