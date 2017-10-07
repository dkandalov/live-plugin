package liveplugin

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.ex.PathUtilEx
import liveplugin.toolwindow.settingsmenu.EnableLivePluginAutoComplete.findGroovyJarOn
import org.jetbrains.kotlin.idea.core.script.dependencies.KotlinScriptResolveScopeProvider
import org.jetbrains.kotlin.script.ScriptTemplatesProvider
import java.io.File
import kotlin.script.dependencies.Environment
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.dependencies.DependenciesResolver
import kotlin.script.experimental.dependencies.ScriptDependencies
import kotlin.script.templates.standard.ScriptTemplateWithArgs

class LivePluginKotlinScriptTemplateProvider(val project: Project) : ScriptTemplatesProvider {
    override val id: String = "LivePluginKotlinScriptTemplateProvider"
    override val isValid: Boolean = true

    override val templateClassNames: Iterable<String> get() = listOf(ScriptTemplateWithArgs::class.qualifiedName!!)
    override val templateClasspath get() = emptyList<File>()

    override val environment: Map<String, Any?>? get() {
        return mapOf(
            KotlinScriptResolveScopeProvider.USE_NULL_RESOLVE_SCOPE to true,
            "sdk" to getScriptSDK(project)
        )
    }

    override val resolver: DependenciesResolver = object: DependenciesResolver {
        override fun resolve(scriptContents: ScriptContents, environment: Environment): DependenciesResolver.ResolveResult {
            val ideJarsPath = PathManager.getHomePath() + "/lib/"
            val ideJars = listOf(
                "openapi.jar",
                "idea.jar",
                "idea_rt.jar",
                "annotations.jar",
                "util.jar",
                "extensions.jar"
            ).map {
                File(ideJarsPath + it)
            }.plus(File(ideJarsPath + findGroovyJarOn(ideJarsPath)))

            return DependenciesResolver.ResolveResult.Success(
                ScriptDependencies(
                    javaHome = File("/Library/Java/JavaVirtualMachines/jdk1.8.0_60.jdk/Contents/Home/jre/"),
                    classpath = listOf(File("/Users/dima/IdeaProjects/live-plugin/LivePlugin.jar")) + ideJars,
                    sources = listOf(
                        File("/Users/dima/IdeaProjects/live-plugin/src"),
                        File("/Users/dima/IdeaProjects/live-plugin/src_groovy")
                    )
                ),
                emptyList()
            )
        }
    }

    override val additionalResolverClasspath: List<File> get() = emptyList()

    private fun getScriptSDK(project: Project): String? {
        val jdk = PathUtilEx.getAnyJdk(project) ?:
            ProjectJdkTable.getInstance().allJdks.firstOrNull { sdk -> sdk.sdkType is JavaSdk }

        return jdk?.homePath
    }
}
