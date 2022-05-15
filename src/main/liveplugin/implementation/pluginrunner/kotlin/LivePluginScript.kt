package liveplugin.implementation.pluginrunner.kotlin

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import liveplugin.implementation.command.LiveCommandService
import spp.jetbrains.monitor.skywalking.SkywalkingMonitorService
import spp.protocol.service.LiveInstrumentService
import spp.protocol.service.LiveService
import spp.protocol.service.LiveViewService
import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(
    filePathPattern = ".*spp.*\\.kts", // Use this regex, because otherwise LivePluginScript makes highlighting red in Kotlin worksheets
    compilationConfiguration = LivePluginScriptHighlightingConfig::class
)
abstract class LivePluginScript(
    /**
     * True on IDE start, otherwise false.
     * Use "Plugins tool window -> Settings -> Run Plugins on IDE Start" to enable/disable it.
     */
    open val isIdeStartup: Boolean,

    /**
     * Project in which plugin is executed.
     */
    open val project: Project,

    /**
     * Absolute path to the current plugin folder.
     */
    open val pluginPath: String,

    /**
     * Instance of `com.intellij.openapi.Disposable` which is disposed just before re-running plugin.
     * Can be useful for cleanup, e.g. un-registering IDE listeners.
     */
    open val pluginDisposable: Disposable,

    open val liveService: LiveService,
    open val liveViewService: LiveViewService,
    open val liveInstrumentService: LiveInstrumentService? = null,
    open val liveCommandService: LiveCommandService,
    open val skywalkingMonitorService: SkywalkingMonitorService
)
