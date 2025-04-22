package liveplugin.implementation.pluginrunner.kotlin

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(
    filePathPattern = ".*plugin.kts", // Use this regex, otherwise LivePluginScript makes highlighting red in Kotlin worksheets
    compilationConfiguration = LivePluginScriptHighlightingConfig::class
)
abstract class LivePluginScript(
    /**
     * True on IDE start, otherwise false.
     * Use "Plugins tool window -> Settings -> Run Plugins on IDE Start" to enable/disable it.
     */
    open val isIdeStartup: Boolean,

    /**
     * Project in which a plugin is executed can be null on IDE start or if no projects are open.
     */
    open val project: Project?,

    /**
     * Absolute path to the current plugin folder.
     */
    open val pluginPath: String,

    /**
     * Instance of `com.intellij.openapi.Disposable` which is disposed just before re-running plugin.
     * Can be useful for clean-up, e.g. unregistering IDE listeners.
     */
    open val pluginDisposable: Disposable
)
