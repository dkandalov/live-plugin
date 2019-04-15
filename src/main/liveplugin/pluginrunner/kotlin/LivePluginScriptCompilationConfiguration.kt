package liveplugin.pluginrunner.kotlin

import liveplugin.LivePluginAppComponent
import java.io.File
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.withUpdatedClasspath

object LivePluginScriptCompilationConfiguration: ScriptCompilationConfiguration(body = {
    val sources = File(LivePluginAppComponent.livePluginLibsPath).listFiles().toList()
    val classpath = sources +
        File("${LivePluginAppComponent.ideJarsPath}/../plugins/Kotlin/lib/").listFiles() +
        File(LivePluginAppComponent.ideJarsPath).listFiles()

    refineConfiguration {
        beforeParsing { context: ScriptConfigurationRefinementContext ->
            ResultWithDiagnostics.Success(
                value = context.compilationConfiguration.withUpdatedClasspath(classpath),
                reports = emptyList()
            )
        }
        beforeCompiling { context: ScriptConfigurationRefinementContext ->
            ResultWithDiagnostics.Success(
                value = context.compilationConfiguration.withUpdatedClasspath(classpath),
                reports = emptyList()
            )
        }
    }
    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
        dependenciesSources(JvmDependency(sources))
    }
})