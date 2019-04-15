package liveplugin.pluginrunner.kotlin

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(
    compilationConfiguration = LivePluginScriptCompilationConfiguration::class
)
abstract class KotlinScriptTemplate(
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