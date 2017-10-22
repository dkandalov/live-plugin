package liveplugin.pluginrunner

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.containers.ContainerUtil.map
import groovy.lang.GroovyClassLoader
import org.apache.commons.httpclient.util.URIUtil
import org.apache.oro.io.GlobFilenameFilter
import java.io.File
import java.io.FileFilter
import java.io.IOException
import java.net.URL
import java.util.*

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
            pathsToAdd: List<String>,
            pluginsToAdd: List<String>,
            mainScriptUrl: String,
            pluginId: String,
            errorReporter: ErrorReporter
        ): ClassLoader {
            val parentLoader = createParentClassLoader(pluginsToAdd, pluginId, errorReporter)
            val classLoader = GroovyClassLoader(parentLoader)
            try {
                pathsToAdd
                    .map { URIUtil.encodePath(it) }
                    .forEach {
                        if (it.startsWith("file:/")) {
                            val url = URL(it)
                            classLoader.addURL(url)
                            classLoader.addClasspath(url.file)
                        } else {
                            classLoader.addURL(URL("file:///" + it))
                            classLoader.addClasspath(it)
                        }
                    }
            } catch (e: IOException) {
                errorReporter.addLoadingError(pluginId, "Error while looking for dependencies in '" + mainScriptUrl + "'. " + e.message)
            }
            return classLoader
        }

        fun createParentClassLoader(dependentPlugins: List<String>, pluginId: String, errorReporter: ErrorReporter): ClassLoader {
            val pluginDescriptors = pluginDescriptorsOf(dependentPlugins, onError = { dependentPluginId ->
                errorReporter.addLoadingError(pluginId, "Couldn't find dependent plugin '$dependentPluginId'")
            })
            val parentLoaders = ArrayList(map(pluginDescriptors) { it -> it.pluginClassLoader })
            parentLoaders.add(PluginRunner::class.java.classLoader)

            val pluginVersion = "1.0.0"
            return PluginClassLoader(
                ArrayList(),
                parentLoaders.toTypedArray(),
                PluginId.getId(pluginId),
                pluginVersion, null
            )
        }

        fun pluginDescriptorsOf(pluginIds: List<String>, onError: (String) -> Unit): List<IdeaPluginDescriptor> {
            val result = ArrayList<IdeaPluginDescriptor>()
            for (pluginIdString in pluginIds) {
                val pluginDescriptor = PluginManager.getPlugin(PluginId.getId(pluginIdString))
                if (pluginDescriptor == null) {
                    onError(pluginIdString)
                } else {
                    result.add(pluginDescriptor)
                }
            }
            return result
        }

        fun findPluginDependencies(lines: Array<String>, prefix: String): List<String> {
            return lines.filter { it.startsWith(prefix) }
                .map { line -> line.replace(prefix, "").trim { it <= ' ' } }
        }

        fun findClasspathAdditions(lines: Array<String>, prefix: String, environment: Map<String, String>, onError: (String) -> Unit): List<String> {
            val pathsToAdd = ArrayList<String>()
            for (line in lines) {
                if (!line.startsWith(prefix)) continue

                var path = line.replace(prefix, "").trim { it <= ' ' }
                path = inlineEnvironmentVariables(path, environment)

                val matchingFiles = findMatchingFiles(path)
                if (matchingFiles.isEmpty()) {
                    onError(path)
                } else {
                    pathsToAdd.addAll(matchingFiles)
                }
            }
            return pathsToAdd
        }

        private fun findMatchingFiles(pathAndPattern: String): List<String> {
            if (File(pathAndPattern).exists()) return listOf(pathAndPattern)

            val separatorIndex = pathAndPattern.lastIndexOf(File.separator)
            val path = pathAndPattern.substring(0, separatorIndex + 1)
            val pattern = pathAndPattern.substring(separatorIndex + 1)

            var files = File(path).listFiles(GlobFilenameFilter(pattern) as FileFilter)
            files = if (files == null) arrayOfNulls(0) else files
            return map(files) { it.absolutePath }
        }

        private fun inlineEnvironmentVariables(path: String, environment: Map<String, String>): String {
            var path = path
            var wasModified = false
            for ((key, value) in environment) {
                path = path.replace("$" + key, value)
                wasModified = true
            }
            if (wasModified) logger.info("Additional classpath with inlined env variables: " + path)
            return path
        }
    }

    companion object {
        val ideStartup = "IDE_STARTUP"
        val addToClasspathKeyword = "add-to-classpath "
        val dependsOnPluginKeyword = "depends-on-plugin "
    }
}
