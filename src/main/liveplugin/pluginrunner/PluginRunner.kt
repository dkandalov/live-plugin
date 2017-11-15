package liveplugin.pluginrunner

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import groovy.lang.GroovyClassLoader
import liveplugin.MyFileUtil.toUrl
import org.apache.oro.io.GlobFilenameFilter
import java.io.File
import java.io.FileFilter

interface PluginRunner {

    fun scriptName(): String

    /**
     * @param pathToPluginFolder absolute path to plugin folder
     * @return true if [PluginRunner] can run plugin in this folder
     */
    fun canRunPlugin(pathToPluginFolder: String): Boolean

    /**
     * @param pathToPluginFolder absolute path to plugin folder
     * @param pluginId plugin id, e.g. to distinguish it from other plugins in error messages
     * @param binding map with implicit variables available in plugin script
     * @param runOnEDT callback which should be used to run plugin code on EDT
     */
    fun runPlugin(pathToPluginFolder: String, pluginId: String, binding: Map<String, *>, runOnEDT: (() -> Unit) -> Unit)


    object ClasspathAddition {
        private val logger = Logger.getInstance(ClasspathAddition::class.java)

        fun createClassLoaderWithDependencies(
            classPath: List<File>,
            pluginsToAdd: List<String>,
            pluginId: String,
            errorReporter: ErrorReporter
        ): ClassLoader {
            val parentLoader = createParentClassLoader(pluginsToAdd, pluginId, errorReporter)
            val classLoader = GroovyClassLoader(parentLoader)
            classPath.forEach { file ->
                if (!file.exists()) {
                    errorReporter.addLoadingError(pluginId, "Didn't find plugin dependency '${file.absolutePath}'.")
                } else {
                    classLoader.addURL(file.toUrl())
                }
            }
            return classLoader
        }

        fun createParentClassLoader(dependentPlugins: List<String>, pluginId: String, errorReporter: ErrorReporter): ClassLoader {
            val pluginDescriptors = pluginDescriptorsOf(dependentPlugins, onError = { dependentPluginId ->
                errorReporter.addLoadingError(pluginId, "Couldn't find dependent plugin '$dependentPluginId'")
            })
            val parentLoaders = pluginDescriptors.map { it.pluginClassLoader } + PluginRunner::class.java.classLoader
            val pluginVersion = "1.0.0"

            return PluginClassLoader(
                emptyList(),
                parentLoaders.toTypedArray(),
                PluginId.getId(pluginId),
                pluginVersion, null
            )
        }

        fun pluginDescriptorsOf(pluginIds: List<String>, onError: (String) -> Unit): List<IdeaPluginDescriptor> {
            return pluginIds.mapNotNull { pluginIdString ->
                val pluginDescriptor = PluginManager.getPlugin(PluginId.getId(pluginIdString))
                if (pluginDescriptor == null) onError(pluginIdString)
                pluginDescriptor
            }
        }

        fun findPluginDependencies(lines: List<String>, prefix: String): List<String> {
            return lines.filter { it.startsWith(prefix) }
                .map { line -> line.replace(prefix, "").trim { it <= ' ' } }
        }

        fun findClasspathAdditions(lines: List<String>, prefix: String, environment: Map<String, String>, onError: (String) -> Unit): List<String> {
            return lines
                .filter { it.startsWith(prefix) }
                .map { line ->
                    val path = line.replace(prefix, "").trim { it <= ' ' }
                    inlineEnvironmentVariables(path, environment)
                }
                .flatMap { path ->
                    val matchingFiles = findMatchingFiles(path)
                    if (matchingFiles.isEmpty()) onError(path)
                    matchingFiles
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

        private fun inlineEnvironmentVariables(path: String, environment: Map<String, String>): String {
            var result = path
            var wasModified = false
            for ((key, value) in environment) {
                result = result.replace("$" + key, value)
                wasModified = true
            }
            if (wasModified) logger.info("Additional classpath with inlined env variables: $result")
            return result
        }
    }

    companion object {
        val ideStartup = "IDE_STARTUP"
        val addToClasspathKeyword = "add-to-classpath "
        val dependsOnPluginKeyword = "depends-on-plugin "
    }
}
