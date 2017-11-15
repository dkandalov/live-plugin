package liveplugin.pluginrunner.kotlin

import liveplugin.LivePluginAppComponent.Companion.ideJarsPath
import liveplugin.LivePluginAppComponent.Companion.livepluginLibsPath
import org.jetbrains.kotlin.idea.core.script.dependencies.KotlinScriptResolveScopeProvider.Companion.USE_NULL_RESOLVE_SCOPE
import org.jetbrains.kotlin.script.ScriptTemplatesProvider
import java.io.File
import kotlin.script.dependencies.Environment
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.dependencies.DependenciesResolver
import kotlin.script.experimental.dependencies.DependenciesResolver.ResolveResult
import kotlin.script.experimental.dependencies.ScriptDependencies
import kotlin.script.templates.standard.ScriptTemplateWithArgs

/**
 * Based on `org.jetbrains.kotlin.idea.script.StandardKotlinScriptTemplateProvider`.
 */
class LivePluginKotlinScriptTemplateProvider: ScriptTemplatesProvider {
    override val id: String = javaClass.simpleName
    override val isValid: Boolean = true
    override val templateClassNames: Iterable<String> get() = listOf(ScriptTemplateWithArgs::class.qualifiedName!!)
    override val templateClasspath get() = emptyList<File>()
    override val environment: Map<String, Any?>? = mapOf(USE_NULL_RESOLVE_SCOPE to true)

    override val resolver: DependenciesResolver = object: DependenciesResolver {
        override fun resolve(scriptContents: ScriptContents, environment: Environment): ResolveResult =
            ResolveResult.Success(
                ScriptDependencies(
                    javaHome = File(System.getProperty("java.home")),
                    classpath =
                        File(livepluginLibsPath).listFiles().toList() +
                        File(ideJarsPath + "/../plugins/Kotlin/lib/").listFiles() +
                        File(ideJarsPath).listFiles(),
                    sources = File(livepluginLibsPath).listFiles().toList()
                ),
                emptyList()
            )
    }

    override val additionalResolverClasspath: List<File> get() = emptyList()
}
