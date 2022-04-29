package liveplugin.implementation.pluginrunner.groovy

import com.intellij.openapi.project.Project
import groovy.util.GroovyScriptEngine
import liveplugin.implementation.LivePlugin
import liveplugin.implementation.common.*
import liveplugin.implementation.pluginrunner.*
import liveplugin.implementation.pluginrunner.AnError.LoadingError
import liveplugin.implementation.pluginrunner.AnError.RunningError
import liveplugin.implementation.pluginrunner.PluginRunner.ClasspathAddition.createClassLoaderWithDependencies
import liveplugin.implementation.pluginrunner.PluginRunner.ClasspathAddition.findClasspathAdditions
import liveplugin.implementation.pluginrunner.PluginRunner.ClasspathAddition.findPluginDescriptorsOfDependencies
import liveplugin.implementation.pluginrunner.PluginRunner.ClasspathAddition.withTransitiveDependencies
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

    override fun setup(plugin: LivePlugin, project: Project?): Result<ExecutablePlugin, AnError> {
        try {
            val mainScript = plugin.path.find(scriptName)
                ?: return LoadingError(message = "Startup script $scriptName was not found.").asFailure()

            val pluginDescriptors = findPluginDescriptorsOfDependencies(mainScript.readLines(), groovyDependsOnPluginKeyword)
                .map { it.onFailure { (message) -> return LoadingError(message).asFailure() } }
                .onEach { if (!it.isEnabled) return LoadingError("Dependent plugin '${it.pluginId}' is disabled").asFailure() }
                .withTransitiveDependencies()

            val environment = systemEnvironment + Pair("PLUGIN_PATH", plugin.path.value) + Pair("PROJECT_PATH", project?.basePath ?: "PROJECT_PATH")
            val additionalClasspath = findClasspathAdditions(mainScript.readLines(), groovyAddToClasspathKeyword, environment)
                .flatMap { it.onFailure { (path) -> return LoadingError("Couldn't find dependency '$path'").asFailure() } }

            val classLoader = createClassLoaderWithDependencies(additionalClasspath + plugin.path.toFile(), pluginDescriptors, plugin)
                .onFailure { return LoadingError(it.reason.message).asFailure() }

            val pluginFolderUrl = "file:///${plugin.path}/" // Prefix with "file:///" so that unix-like path works on Windows.
            // Assume that GroovyScriptEngine is thread-safe
            // (according to this http://groovy.329449.n5.nabble.com/Is-the-GroovyScriptEngine-thread-safe-td331407.html)
            val scriptEngine = GroovyScriptEngine(pluginFolderUrl, classLoader)
            scriptEngine.config.targetBytecode = "11"
            try {
                scriptEngine.loadScriptByName(mainScript.toUrlString())
            } catch (e: Exception) {
                return LoadingError(throwable = e).asFailure()
            }

            return ExecutableGroovyPlugin(scriptEngine, mainScript.toUrlString()).asSuccess()

        } catch (e: IOException) {
            return LoadingError("Error creating scripting engine. ${e.message}").asFailure()
        } catch (e: CompilationFailedException) {
            return LoadingError("Error compiling script. ${e.message}").asFailure()
        } catch (e: LinkageError) {
            return LoadingError("Error linking script. ${e.message}").asFailure()
        } catch (e: Error) {
            return LoadingError(throwable = e).asFailure()
        } catch (e: Exception) {
            return LoadingError(throwable = e).asFailure()
        }
    }

    override fun run(executablePlugin: ExecutablePlugin, binding: Binding): Result<Unit, AnError> {
        val (scriptEngine, scriptUrl) = executablePlugin as ExecutableGroovyPlugin
        return try {
            scriptEngine.run(scriptUrl, GroovyBinding(
                mapOf(
                    "project" to binding.project,
                    "isIdeStartup" to binding.isIdeStartup,
                    "pluginPath" to binding.pluginPath,
                    "pluginDisposable" to binding.pluginDisposable
                )
            ))
            Unit.asSuccess()
        } catch (e: Exception) {
            RunningError(e).asFailure()
        }
    }

    companion object {
        const val groovyScriptFile = "plugin.groovy"
        const val groovyTestScriptFile = "plugin-test.groovy"
        const val groovyAddToClasspathKeyword = "// add-to-classpath "
        const val groovyDependsOnPluginKeyword = "// depends-on-plugin "

        val mainGroovyPluginRunner = GroovyPluginRunner(groovyScriptFile)
        val testGroovyPluginRunner = GroovyPluginRunner(groovyTestScriptFile)
    }
}

class LivePluginGdslScriptProvider: GdslScriptProvider
