package liveplugin.pluginrunner.groovy

import groovy.lang.Binding as GroovyBinding
import groovy.util.GroovyScriptEngine
import liveplugin.findScriptFileIn
import liveplugin.pluginrunner.*
import liveplugin.pluginrunner.AnError.*
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.createClassLoaderWithDependencies
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.findClasspathAdditions
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.findDependenciesOnIdePlugins
import liveplugin.pluginrunner.Result.*
import liveplugin.readLines
import liveplugin.toUrlString
import org.codehaus.groovy.control.CompilationFailedException
import java.io.File
import java.io.IOException

class GroovyPluginRunner(
    override val scriptName: String,
    private val systemEnvironment: Map<String, String> = systemEnvironment()
): PluginRunner {

    override fun runPlugin(plugin: LivePlugin, binding: Binding, runOnEDT: (() -> Result<Unit, AnError>) -> Result<Unit, AnError>): Result<Unit, AnError> {
        val mainScript = findScriptFileIn(plugin.path, scriptName)!!
        return runGroovyScript(mainScript.toUrlString(), plugin, binding, runOnEDT)
    }

    private fun runGroovyScript(
        mainScriptUrl: String,
        plugin: LivePlugin,
        binding: Binding,
        runOnEDT: (() -> Result<Unit, AnError>) -> Result<Unit, AnError>
    ): Result<Unit, AnError> {
        try {
            val environment = systemEnvironment + Pair("PLUGIN_PATH", plugin.path)

            val dependenciesOnIdePlugins = findDependenciesOnIdePlugins(readLines(mainScriptUrl), groovyDependsOnPluginKeyword)
                .onFailure { return Failure(LoadingError(plugin.id, it.reason)) }
            val additionalClasspath = findClasspathAdditions(readLines(mainScriptUrl), groovyAddToClasspathKeyword, environment)
                .onFailure { path -> return Failure(LoadingError(plugin.id, "Couldn't find dependency '$path'")) }
            val classLoader = createClassLoaderWithDependencies(additionalClasspath + File(plugin.path), dependenciesOnIdePlugins, plugin)
                .onFailure { return Failure(LoadingError(it.reason.pluginId, it.reason.message)) }

            val pluginFolderUrl = "file:///${plugin.path}/" // prefix with "file:///" so that unix-like path works on windows
            // assume that GroovyScriptEngine is thread-safe
            // (according to this http://groovy.329449.n5.nabble.com/Is-the-GroovyScriptEngine-thread-safe-td331407.html)
            val scriptEngine = GroovyScriptEngine(pluginFolderUrl, classLoader)
            try {
                scriptEngine.loadScriptByName(mainScriptUrl)
            } catch (e: Exception) {
                return Failure(LoadingError(plugin.id, throwable = e))
            }

            return runOnEDT {
                try {
                    scriptEngine.run(mainScriptUrl, GroovyBinding(binding.toMap()))
                    Success(Unit)
                } catch (e: Exception) {
                    Failure(RunningError(plugin.id, e))
                }
            }

        } catch (e: IOException) {
            return Failure(LoadingError(plugin.id, "Error creating scripting engine. ${e.message}"))
        } catch (e: CompilationFailedException) {
            return Failure(LoadingError(plugin.id, "Error compiling script. ${e.message}"))
        } catch (e: LinkageError) {
            return Failure(LoadingError(plugin.id, "Error linking script. ${e.message}"))
        } catch (e: Error) {
            return Failure(LoadingError(plugin.id, throwable = e))
        } catch (e: Exception) {
            return Failure(LoadingError(plugin.id, throwable = e))
        }
    }

    companion object {
        const val mainScript = "plugin.groovy"
        const val testScript = "plugin-test.groovy"
        const val groovyAddToClasspathKeyword = "// add-to-classpath "
        const val groovyDependsOnPluginKeyword = "// depends-on-plugin "

        val main = GroovyPluginRunner(mainScript)
        val test = GroovyPluginRunner(testScript)
    }
}
