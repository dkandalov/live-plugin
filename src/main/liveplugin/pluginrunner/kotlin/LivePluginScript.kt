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

@KotlinScript(
    filePathPattern = ".*live-plugins.*\\.kts", // Use this regex, because otherwise LivePluginScript makes highlighting red in Kotlin worksheets
    compilationConfiguration = LivePluginScriptHighlightingConfig::class
)
abstract class LivePluginScript(
    /**
     * True on IDE start, otherwise false.
     * Use "Plugins tool window -> Settings -> Run Plugins on IDE Start" to enable/disable it.
     */
    open val isIdeStartup: Boolean,

    /**
     * Project in which plugin is executed, can be null on IDE start or if no projects are open.
     */
    open val project: Project?,

    /**
     * Absolute path to the current plugin folder.
     */
    open val pluginPath: String,

    /**
     * Instance of `com.intellij.openapi.Disposable` which is disposed just before re-running plugin.
     * Can be useful for cleanup, e.g. un-registering IDE listeners.
     */
    open val pluginDisposable: Disposable
)

@KotlinScript(
    filePathPattern = ".*live-plugins.*\\.kts",
    compilationConfiguration = LivePluginScriptCompilationConfig::class
)
abstract class LivePluginScriptForCompilation(
    override val isIdeStartup: Boolean,
    override val project: Project?,
    override val pluginPath: String,
    override val pluginDisposable: Disposable
) : LivePluginScript(isIdeStartup, project, pluginPath, pluginDisposable)

object LivePluginScriptHighlightingConfig: LivePluginScriptConfig({ createScriptConfig(it, ::highlightingClasspath) })
object LivePluginScriptCompilationConfig: LivePluginScriptConfig({ createScriptConfig(it, ::compilingClasspath) })

open class LivePluginScriptConfig(
    createConfig: (ScriptConfigurationRefinementContext) -> ScriptCompilationConfiguration
): ScriptCompilationConfiguration(body = {
    refineConfiguration {
        beforeParsing { context -> ResultWithDiagnostics.Success(createConfig(context), reports = emptyList()) }
        beforeCompiling { context -> ResultWithDiagnostics.Success(createConfig(context), reports = emptyList()) }
    }
    ide {
        compilerOptions("-jvm-target", "11")
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
        dependenciesSources(JvmDependency(livePluginLibAndSrcFiles()))
//        dependenciesSources(JvmDependency(listOf( // This doesn't work 😠
//            File(".../Application Support/JetBrains/IntelliJIdea2021.1/plugins/live-plugins/multiple-src-files/foo.kt"),
//            File(".../Application Support/JetBrains/IntelliJIdea2021.1/plugins/live-plugins/multiple-src-files/bar/bar.kt")
//        )))
    }
})

private fun createScriptConfig(context: ScriptConfigurationRefinementContext, classpath: (List<String>, String) -> List<File>) =
    ScriptCompilationConfiguration(context.compilationConfiguration, body = {
        val scriptText = context.script.text.split('\n')
        val scriptFolderPath = context.script.locationId?.let { File(it).parent } ?: ""
        updateClasspath(classpath(scriptText, scriptFolderPath))

        // Disabled because it doesn't work and can only cause confusion
        // because multiple .kts files will be compiled referencing each other in constructor arguments.
        // E.g. "class Plugin(..., `$$importedScriptSome`: Some)" which will not work the code creating object from script class.
        @Suppress("ConstantConditionIf")
        if (false) {
            val filesInThePluginFolder = scriptFolderPath.toFilePath().allFiles()
                .filter { it.extension == "kt" || it.extension == "kts" }
                .filter { it.value != context.script.locationId }.map { it.toFile() }.toList()
            // The `importScripts` seem to be used by org.jetbrains.kotlin.scripting.resolve.LazyScriptDescriptor.ImportedScriptDescriptorsFinder()
            // which only reads definitions from KtScript psi elements (.kts files) but it doesn't work even if all files are renamed to .kts 😠 The scripting API is endless WTF!!!!!
            // See also https://youtrack.jetbrains.com/issue/KT-28916
            importScripts.append(filesInThePluginFolder.map { file -> FileScriptSource(file) })
        }
    })

private fun highlightingClasspath(scriptText: List<String>, scriptFolderPath: String) =
    ideLibFiles() +
    dependenciesOnOtherPluginsForHighlighting(scriptText) +
    findClasspathAdditionsForHighlightingAndScriptTemplate(scriptText, scriptFolderPath) +
    livePluginLibAndSrcFiles()

// Similar to highlightingClasspath() but without depenencies on other plugins
// because PluginDescriptorLoader fails with "NoClassDefFoundError: Could not initialize class com.intellij.ide.plugins.PluginXmlFactory"
// when running KotlinPluginCompiler which I guess somehow picks it up via @KotlinScript annotation.
// All dependent plugins are directly added to the compiler classpath anyway in KotlinPluginCompiler.compile().
private fun compilingClasspath(scriptText: List<String>, scriptFolderPath: String) =
    ideLibFiles() +
    findClasspathAdditionsForHighlightingAndScriptTemplate(scriptText, scriptFolderPath) +
    livePluginLibAndSrcFiles()

class LivePluginKotlinScriptProvider: ScriptDefinitionsProvider {
    override val id = "LivePluginKotlinScriptProvider"
    override fun getDefinitionClasses() = listOf(LivePluginScript::class.java.canonicalName)
    override fun getDefinitionsClassPath() = LivePluginPaths.livePluginLibPath.listFiles().map { it.toFile() }
        // + File(".../live-plugin/build/idea-sandbox/plugins/live-plugins/multiple-src-files/foo.kt") This doesn't work 😠
    override fun useDiscovery() = false
}
