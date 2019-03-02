package liveplugin.pluginrunner.kotlin

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project

/**
 * This class exists to make Kotlin compiler be to process built-in variables in plugin.kts files.
 * The fields below must be in sync with vals in script-implicits.kt file.
 */
abstract class KotlinScriptTemplate(
    val project: Project?,
    val isIdeStartup: Boolean,
    val pluginPath: String,
    val pluginDisposable: Disposable
)