package liveplugin.pluginrunner.kotlin

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import liveplugin.LivePluginPaths
import liveplugin.toFilePath
import java.io.File
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.updateClasspath


class LivePluginKotlinScriptProvider: ScriptDefinitionsProvider {
    override val id = "LivePluginKotlinScriptProvider"
    override fun getDefinitionClasses() = listOf(LivePluginScript::class.java.canonicalName)
    override fun getDefinitionsClassPath() = LivePluginPaths.livePluginLibPath.listFiles()
        // + File(".../live-plugin/build/idea-sandbox/plugins/live-plugins/multiple-src-files/foo.kt") This doesn't work WTF!! 😡
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
    refineConfiguration {
        beforeParsing { context ->
            ResultWithDiagnostics.Success(value = createScriptCompilationConfig(context), reports = emptyList())
        }
        beforeCompiling { context ->
            ResultWithDiagnostics.Success(value = createScriptCompilationConfig(context), reports = emptyList())
        }
    }
    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
        dependenciesSources(JvmDependency(livePluginLibAndSrcFiles()))
//        dependenciesSources(JvmDependency(listOf( // This doesn't work 😠
//            File(".../Application Support/JetBrains/IntelliJIdea2021.1/plugins/live-plugins/multiple-src-files/foo.kt"),
//            File(".../Application Support/JetBrains/IntelliJIdea2021.1/plugins/live-plugins/multiple-src-files/bar/bar.kt")
//        )))
    }
})

private fun createScriptCompilationConfig(context: ScriptConfigurationRefinementContext): ScriptCompilationConfiguration {
    return ScriptCompilationConfiguration(context.compilationConfiguration) {
        val scriptText = context.script.text.split('\n')
        val scriptFolderPath = context.script.locationId?.let { File(it).parent } ?: ""
        updateClasspath(classpath(scriptText, scriptFolderPath))

        val filesInThePluginFolder = scriptFolderPath.toFilePath()
            .allFiles().filter { it.path != context.script.locationId }.toList()
        // These `importScripts` seem to be used by org.jetbrains.kotlin.scripting.resolve.LazyScriptDescriptor.ImportedScriptDescriptorsFinder()
        // which only reads definitions from KtScript psi elements (.kts files) but it doesn't work even if all files are renamed to .kts 😠 The scripting API is endless WTF!!!!!
        // See also https://youtrack.jetbrains.com/issue/KT-28916
        importScripts.append(filesInThePluginFolder.map { file -> FileScriptSource(file) })
    }
}

private fun classpath(scriptText: List<String>, scriptFolderPath: String) =
    ideLibFiles() +
    dependenciesOnOtherPluginsForHighlighting(scriptText) +
    findClasspathAdditionsForHighlighting(scriptText, scriptFolderPath) +
    livePluginLibAndSrcFiles()
