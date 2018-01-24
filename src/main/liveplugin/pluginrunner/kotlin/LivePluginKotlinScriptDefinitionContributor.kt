package liveplugin.pluginrunner.kotlin

import liveplugin.LivePluginAppComponent.Companion.ideJarsPath
import liveplugin.LivePluginAppComponent.Companion.livePluginLibsPath
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionContributor
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import java.io.File
import kotlin.script.dependencies.Environment
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.dependencies.DependenciesResolver
import kotlin.script.experimental.dependencies.DependenciesResolver.ResolveResult
import kotlin.script.experimental.dependencies.ScriptDependencies
import kotlin.script.templates.standard.ScriptTemplateWithArgs

/**
 * Based on `org.jetbrains.kotlin.idea.core.script.StandardScriptDefinitionContributor` from kotlin plugin for IJ.
 */
class LivePluginKotlinScriptDefinitionContributor: ScriptDefinitionContributor {
    private val resolver = object: DependenciesResolver {
        override fun resolve(scriptContents: ScriptContents, environment: Environment): ResolveResult =
            ResolveResult.Success(
                ScriptDependencies(
                    javaHome = File(System.getProperty("java.home")),
                    classpath = File(livePluginLibsPath).listFiles().toList() +
                        File(ideJarsPath + "/../plugins/Kotlin/lib/").listFiles() +
                        File(ideJarsPath).listFiles(),
                    sources = File(livePluginLibsPath).listFiles().toList()
                ),
                emptyList()
            )
    }

    override val id: String = javaClass.simpleName

    override fun getDefinitions(): List<KotlinScriptDefinition> =
        listOf(object: KotlinScriptDefinition(ScriptTemplateWithArgs::class) {
            override val dependencyResolver = resolver
        })
}
