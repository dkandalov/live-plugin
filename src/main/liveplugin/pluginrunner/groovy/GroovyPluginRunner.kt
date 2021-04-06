package liveplugin.pluginrunner.groovy

import groovy.util.GroovyScriptEngine
import liveplugin.*
import liveplugin.Result.Failure
import liveplugin.Result.Success
import liveplugin.pluginrunner.*
import liveplugin.pluginrunner.AnError.LoadingError
import liveplugin.pluginrunner.AnError.RunningError
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.createClassLoaderWithDependencies
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.findClasspathAdditions
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.findPluginDescriptorsOfDependencies
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.withTransitiveDependencies
import org.codehaus.groovy.control.CompilationFailedException
import org.jetbrains.plugins.groovy.dsl.GdslScriptProvider
import java.io.IOException
import groovy.lang.Binding as GroovyBinding

class GroovyPluginRunner(
    override val scriptName: String,
    private val systemEnvironment: Map<String, String> = systemEnvironment()
): PluginRunner {

    private data class ExecutableGroovyPlugin(
        val scriptEngine: GroovyScriptEngine,
        val scriptUrl: String
    ) : ExecutablePlugin

    override fun setup(plugin: LivePlugin): Result<ExecutablePlugin, AnError> {
        try {
            val mainScript = plugin.path.find(scriptName)
                ?: return LoadingError(message = "Startup script $scriptName was not found.").asFailure()

            val pluginDescriptors = findPluginDescriptorsOfDependencies(mainScript.readLines(), groovyDependsOnPluginKeyword)
                .map { it.onFailure { (message) -> return Failure(LoadingError(message)) } }
                .onEach { if (!it.isEnabled) return Failure(LoadingError("Dependent plugin '${it.pluginId}' is disabled")) }
                .withTransitiveDependencies()

            val environment = systemEnvironment + Pair("PLUGIN_PATH", plugin.path.value)
            val additionalClasspath = findClasspathAdditions(mainScript.readLines(), groovyAddToClasspathKeyword, environment)
                .flatMap { it.onFailure { (path) -> return Failure(LoadingError("Couldn't find dependency '$path'")) } }

            val classLoader = createClassLoaderWithDependencies(additionalClasspath + plugin.path.toFile(), pluginDescriptors, plugin)
                .onFailure { return Failure(LoadingError(it.reason.message)) }

            val pluginFolderUrl = "file:///${plugin.path}/" // prefix with "file:///" so that unix-like path works on windows
            // assume that GroovyScriptEngine is thread-safe
            // (according to this http://groovy.329449.n5.nabble.com/Is-the-GroovyScriptEngine-thread-safe-td331407.html)
            val scriptEngine = GroovyScriptEngine(pluginFolderUrl, classLoader)
            try {
                scriptEngine.loadScriptByName(mainScript.toUrlString())
            } catch (e: Exception) {
                return Failure(LoadingError(throwable = e))
            }

            return ExecutableGroovyPlugin(scriptEngine, mainScript.toUrlString()).asSuccess()

        } catch (e: IOException) {
            return Failure(LoadingError("Error creating scripting engine. ${e.message}"))
        } catch (e: CompilationFailedException) {
            return Failure(LoadingError("Error compiling script. ${e.message}"))
        } catch (e: LinkageError) {
            return Failure(LoadingError("Error linking script. ${e.message}"))
        } catch (e: Error) {
            return Failure(LoadingError(throwable = e))
        } catch (e: Exception) {
            return Failure(LoadingError(throwable = e))
        }
    }

    override fun run(executablePlugin: ExecutablePlugin, binding: Binding): Result<Unit, AnError> {
        val (scriptEngine, scriptUrl) = executablePlugin as ExecutableGroovyPlugin
        return try {
            scriptEngine.run(scriptUrl, GroovyBinding(binding.toMap()))
            Success(Unit)
        } catch (e: Exception) {
            Failure(RunningError(e))
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

class LivePluginGdslScriptProvider: GdslScriptProvider
