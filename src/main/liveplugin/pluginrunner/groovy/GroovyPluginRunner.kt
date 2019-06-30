package liveplugin.pluginrunner.groovy

import groovy.lang.Binding
import groovy.util.GroovyScriptEngine
import liveplugin.findScriptFileIn
import liveplugin.pluginrunner.*
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.createClassLoaderWithDependencies
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.findClasspathAdditions
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.findPluginDependencies
import liveplugin.pluginrunner.Result.*
import liveplugin.readLines
import liveplugin.toUrlString
import org.codehaus.groovy.control.CompilationFailedException
import java.io.File
import java.io.IOException

class GroovyPluginRunner(
    override val scriptName: String,
    private val errorReporter: ErrorReporter,
    private val systemEnvironment: Map<String, String> = systemEnvironment()
): PluginRunner {

    override fun runPlugin(pluginFolderPath: String, pluginId: String, binding: Map<String, *>, runOnEDT: (() -> Unit) -> Unit): Result<Unit, AnError> {
        val mainScript = findScriptFileIn(pluginFolderPath, scriptName)!!
        return runGroovyScript(mainScript.toUrlString(), pluginFolderPath, pluginId, binding, runOnEDT)
    }

    private fun runGroovyScript(
        mainScriptUrl: String,
        pluginFolderPath: String,
        pluginId: String,
        binding: Map<String, *>,
        runOnEDT: (() -> Unit) -> Unit
    ): Result<Unit, AnError> {
        try {
            val environment = systemEnvironment + Pair("PLUGIN_PATH", pluginFolderPath)

            val dependentPlugins = findPluginDependencies(readLines(mainScriptUrl), groovyDependsOnPluginKeyword)
            val pathsToAdd = findClasspathAdditions(
                readLines(mainScriptUrl),
                groovyAddToClasspathKeyword,
                environment,
                onError = { path ->
                    errorReporter.addLoadingError(pluginId, "Couldn't find dependency '$path'")
                }
            ).map { File(it) }.toMutableList()
            pathsToAdd.add(File(pluginFolderPath))
            val classLoader = createClassLoaderWithDependencies(pathsToAdd, dependentPlugins, pluginId).onFailure {
                errorReporter.addLoadingError(it.reason.pluginId, it.reason.message)
                return Success(Unit)
            }

            val pluginFolderUrl = "file:///$pluginFolderPath/" // prefix with "file:///" so that unix-like path works on windows
            // assume that GroovyScriptEngine is thread-safe
            // (according to this http://groovy.329449.n5.nabble.com/Is-the-GroovyScriptEngine-thread-safe-td331407.html)
            val scriptEngine = GroovyScriptEngine(pluginFolderUrl, classLoader)
            try {
                scriptEngine.loadScriptByName(mainScriptUrl)
            } catch (e: Exception) {
                errorReporter.addLoadingError(pluginId, e)
                return Success(Unit)
            }

            runOnEDT {
                try {
                    scriptEngine.run(mainScriptUrl, createGroovyBinding(binding))
                } catch (e: Exception) {
                    errorReporter.addRunningError(pluginId, e)
                }
            }

        } catch (e: IOException) {
            errorReporter.addLoadingError(pluginId, "Error creating scripting engine. ${e.message}")
        } catch (e: CompilationFailedException) {
            errorReporter.addLoadingError(pluginId, "Error compiling script. ${e.message}")
        } catch (e: LinkageError) {
            errorReporter.addLoadingError(pluginId, "Error linking script. ${e.message}")
        } catch (e: Error) {
            errorReporter.addLoadingError(pluginId, e)
        } catch (e: Exception) {
            errorReporter.addLoadingError(pluginId, e)
        }
        return Success(Unit)
    }

    private fun createGroovyBinding(binding: Map<String, *>): Binding {
        val result = Binding()
        for ((key, value) in binding) {
            result.setVariable(key, value)
        }
        return result
    }

    companion object {
        const val mainScript = "plugin.groovy"
        const val testScript = "plugin-test.groovy"
        const val groovyAddToClasspathKeyword = "// " + PluginRunner.addToClasspathKeyword
        const val groovyDependsOnPluginKeyword = "// " + PluginRunner.dependsOnPluginKeyword
    }
}
