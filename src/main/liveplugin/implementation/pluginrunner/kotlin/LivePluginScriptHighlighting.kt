package liveplugin.implementation.pluginrunner.kotlin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import liveplugin.implementation.LivePluginPaths.livePluginLibPath
import liveplugin.implementation.common.toFilePath
import org.jetbrains.kotlin.idea.core.script.loadDefinitionsFromTemplates
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import java.io.File
import kotlin.script.experimental.api.*
import kotlin.script.experimental.api.ScriptAcceptedLocation.Everywhere
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.jvm.*

class LivePluginScriptSource : ScriptDefinitionsSource {
    override val definitions: Sequence<ScriptDefinition>
        get() {
            val scriptDefinitions = loadDefinitionsFromTemplates(
                templateClassNames = listOf(LivePluginScript::class.java.canonicalName),
                templateClasspath = livePluginLibPath.listFiles().map { it.toFile() },
                baseHostConfiguration = defaultJvmScriptingHostConfiguration
            )
            val loadedDefinitions = scriptDefinitions.map {
                kotlin.script.experimental.host.ScriptDefinition(
                    it.compilationConfiguration,
                    it.evaluationConfiguration ?: ScriptEvaluationConfiguration.Default,
                )
            }.toList()

            return loadedDefinitions.map {
                ScriptDefinition.FromNewDefinition(defaultJvmScriptingHostConfiguration, it.copy())
                    .apply { order = Int.MIN_VALUE }
            }.asSequence()
        }
}

class LivePluginScriptHighlightingConfig : ScriptCompilationConfiguration(body = {
    fun createConfig(context: ScriptConfigurationRefinementContext) =
        createScriptConfig(context, ::highlightingClasspath)

    refineConfiguration {
        beforeParsing { context ->
            ResultWithDiagnostics.Success(createConfig(context))
        }
        beforeCompiling { context ->
            ResultWithDiagnostics.Success(createConfig(context))
        }
    }
    compilerOptions("-jvm-target", "17")
    jvm {
        jdkHome(File(System.getProperty("java.home")))
    }
    ide {
        acceptedLocations(Everywhere)
        dependenciesSources(JvmDependency(livePluginLibAndSrcFiles()))
//        dependenciesSources(JvmDependency(listOf( // This doesn't work 😠
//            File(".../Application Support/JetBrains/IntelliJIdea2021.1/plugins/live-plugins/multiple-src-files/foo.kt"),
//            File(".../Application Support/JetBrains/IntelliJIdea2021.1/plugins/live-plugins/multiple-src-files/bar/bar.kt")
//        )))
    }
})

private fun createScriptConfig(context: ScriptConfigurationRefinementContext, classpath: (List<String>, String) -> List<File>) =
    ScriptCompilationConfiguration(context.compilationConfiguration, body = {
        // Attempt to use runReadAction() for syntax highlighting to avoid errors because of accessing data on non-EDT thread.
        // Run as a normal function if there is no application, which is the case when running an embedded compiler.
        val computable = Computable { context.script.locationId }
        val scriptLocationId = ApplicationManager.getApplication()?.runReadAction(computable) ?: computable.compute()

        // Can't do `context.script.text` in the Computable because it throws PsiInvalidElementAccessException from com.intellij.psi.impl.source.PsiFileImpl.getText 🙄
        val scriptText = if (scriptLocationId == null) emptyList() else File(scriptLocationId).readText().split('\n')

        val scriptFolderPath = scriptLocationId?.let { File(it).parent } ?: ""
        updateClasspath(classpath(scriptText, scriptFolderPath))

        // Disabled because it doesn't work and trying to do it again can cause confusion.
        // TODO maybe extend org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension for LivePlugin?
        @Suppress("ConstantConditionIf")
        if (false) {
            val filesInThePluginFolder = scriptFolderPath.toFilePath().allFiles()
                .filter { it.extension == "kt" || it.extension == "kts" }
                .filter { it.value != scriptLocationId }.map { it.toFile() }.toList()
            // The `importScripts` seem to be used by org.jetbrains.kotlin.scripting.resolve.LazyScriptDescriptor.ImportedScriptDescriptorsFinder()
            // which only reads definitions from KtScript PSI elements (.kts files) but doesn't work even if all files are renamed to .kts 😠
            // because multiple .kts files will be compiled referencing each other in constructor arguments.
            // E.g. "class Plugin(..., `$$importedScriptSome`: Some)" which will not work the code creating object from the script class.
            // See also https://youtrack.jetbrains.com/issue/KT-28916
            importScripts.append(filesInThePluginFolder.map { file -> FileScriptSource(file) })
        }
    })

private fun highlightingClasspath(scriptText: List<String>, scriptFolderPath: String) =
    ideLibFiles() +
        dependenciesOnOtherPluginsForHighlighting(scriptText) +
        findClasspathAdditionsForHighlightingAndScriptTemplate(scriptText, scriptFolderPath) +
        livePluginLibAndSrcFiles()
