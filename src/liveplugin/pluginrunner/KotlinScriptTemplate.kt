package liveplugin.pluginrunner

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project

/**
 * This class exists to make Kotlin compiler process built-in variable in plugin.kts files.
 * Its fields must be in sync with vals in script-implicits.kt file.
 */
abstract class KotlinScriptTemplate(
    val project: Project,
    val isIdeStartup: Boolean,
    val pluginPath: String,
    val pluginDisposable: Disposable
)