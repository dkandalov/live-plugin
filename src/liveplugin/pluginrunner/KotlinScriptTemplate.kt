package liveplugin.pluginrunner

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project

abstract class KotlinScriptTemplate(
    val project: Project,
    val isIdeStartup: Boolean,
    val pluginPath: String,
    val pluginDisposable: Disposable
)