package liveplugin.pluginrunner.kotlin

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import liveplugin.LivePluginPaths
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.*
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.withUpdatedClasspath


class LivePluginKotlinScriptProvider: ScriptDefinitionsProvider {
    override val id = "LivePluginKotlinScriptProvider"
    override fun getDefinitionClasses() = listOf(LivePluginScript::class.java.canonicalName)
    override fun getDefinitionsClassPath() = LivePluginPaths.livePluginLibPath.listFiles()
    override fun useDiscovery() = false
}

@KotlinScript(
    filePathPattern = ".*live-plugins.*\\.kts", // Use this regex, because otherwise LivePluginScript makes highlighting red in Kotlin worksheets
    compilationConfiguration = LivePluginScriptCompilationConfiguration::class
)
abstract class LivePluginScript(
    /**
     * True on IDE startup, otherwise false.
     * Where "IDE startup" means executing code on LivePlugin application component initialisation.
     * Use "Plugins toolwindow -> Settings -> Run all plugins on IDE start" to enable/disable it.
     */
    val isIdeStartup: Boolean,

    /**
     * Project in which plugin is executed, can be null on IDE startup or if no projects are open.
     */
    val project: Project?,

    /**
     * Absolute path to the current plugin folder.
     */
    val pluginPath: String,

    /**
     * Instance of `com.intellij.openapi.Disposable` which is disposed just before re-running plugin.
     * Can be useful for cleanup, e.g. un-registering IDE listeners.
     */
    val pluginDisposable: Disposable
)

object LivePluginScriptCompilationConfiguration: ScriptCompilationConfiguration(body = {
    fun classpath(scriptText: List<String>, scriptPath: String) =
        ideLibFiles() +
        dependenciesOnOtherPluginsForHighlighting(scriptText) +
        findClasspathAdditionsForHighlighting(scriptText, scriptPath) +
        livePluginLibAndSrcFiles()

    refineConfiguration {
        beforeParsing { context ->
            ResultWithDiagnostics.Success(
                value = context.compilationConfiguration.withUpdatedClasspath(classpath(context.script.text.split('\n'), context.script.locationId ?: "")),
                reports = emptyList()
            )
        }
        beforeCompiling { context ->
            ResultWithDiagnostics.Success(
                value = context.compilationConfiguration.withUpdatedClasspath(classpath(context.script.text.split('\n'), context.script.locationId ?: "")),
                reports = emptyList()
            )
        }
    }
    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
        dependenciesSources(JvmDependency(livePluginLibAndSrcFiles()))
    }
})
