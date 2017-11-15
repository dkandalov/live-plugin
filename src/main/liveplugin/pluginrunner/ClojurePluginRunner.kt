package liveplugin.pluginrunner

import clojure.lang.*
import liveplugin.MyFileUtil.asUrl
import liveplugin.MyFileUtil.findScriptFileIn
import liveplugin.MyFileUtil.readLines
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.createClassLoaderWithDependencies
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.findClasspathAdditions
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.findPluginDependencies
import java.io.File
import java.io.IOException
import java.util.*

/**
 * This class should not be loaded unless clojure libs are on classpath.
 */
class ClojurePluginRunner(
    private val errorReporter: ErrorReporter,
    private val environment: MutableMap<String, String>
): PluginRunner {

    override fun canRunPlugin(pathToPluginFolder: String) = findScriptFileIn(pathToPluginFolder, mainScript) != null

    override fun runPlugin(pathToPluginFolder: String, pluginId: String,
                           binding: Map<String, *>, runOnEDT: (() -> Unit) -> Unit) {
        if (!initialized) {
            // need this to avoid "java.lang.IllegalStateException: Attempting to call unbound fn: #'clojure.core/refer"
            // use classloader of RunPluginAction assuming that clojure was first initialized from it
            // (see https://groups.google.com/forum/#!topic/clojure/F3ERon6Fye0)
            Thread.currentThread().contextClassLoader = RunPluginAction::class.java.classLoader

            // need to initialize RT before Compiler, otherwise Compiler initialization fails with NPE
            RT.init()
            initialized = true
        }

        val scriptFile = findScriptFileIn(pathToPluginFolder, mainScript)!!

        val dependentPlugins = ArrayList<String>()
        val additionalPaths = ArrayList<File>()
        try {
            environment.put("PLUGIN_PATH", pathToPluginFolder)

            dependentPlugins.addAll(findPluginDependencies(readLines(asUrl(scriptFile)), clojureDependsOnPluginKeyword))
            additionalPaths.addAll(findClasspathAdditions(readLines(asUrl(scriptFile)), clojureAddToClasspathKeyword, environment, onError = { path ->
                errorReporter.addLoadingError(pluginId, "Couldn't find dependency '$path'")
            }).map{ File(it) })
        } catch (e: IOException) {
            errorReporter.addLoadingError(pluginId, "Error reading script file: " + scriptFile)
            return
        }

        val classLoader = createClassLoaderWithDependencies(additionalPaths, dependentPlugins, pluginId, errorReporter)


        runOnEDT {
            try {
                var bindings = Var.getThreadBindings()
                for ((key1, value) in binding) {
                    val key = createKey("*$key1*")
                    bindings = bindings.assoc(key, value)
                }
                bindings = bindings.assoc(Compiler.LOADER, classLoader)
                Var.pushThreadBindings(bindings)

                // assume that clojure Compile is thread-safe
                Compiler.loadFile(scriptFile.absolutePath)

            } catch (e: IOException) {
                errorReporter.addLoadingError(pluginId, "Error reading script file: " + scriptFile)
            } catch (e: LinkageError) {
                errorReporter.addLoadingError(pluginId, "Error linking script file: " + scriptFile)
            } catch (e: Error) {
                errorReporter.addLoadingError(pluginId, e)
            } catch (e: Exception) {
                errorReporter.addRunningError(pluginId, e)
            } finally {
                Var.popThreadBindings()
            }
        }
    }

    override fun scriptName() = mainScript

    companion object {
        val mainScript = "plugin.clj"
        private val clojureAddToClasspathKeyword = "; " + PluginRunner.addToClasspathKeyword
        private val clojureDependsOnPluginKeyword = "; " + PluginRunner.dependsOnPluginKeyword

        private var initialized: Boolean = false

        private fun createKey(name: String): Var =
            Var.intern(Namespace.findOrCreate(Symbol.intern("clojure.core")), Symbol.intern(name), "no_" + name).setDynamic()
    }
}
