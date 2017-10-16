import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project

// This file exists to enable code completion for built-in variables in plugin.kts files.
// The vals must be in sync with fields in liveplugin.pluginrunner.kotlin.KotlinScriptTemplate class.

val isIdeStartup: Boolean = error("Stub implementation")
val project: Project = error("Stub implementation")
val pluginPath: String = error("Stub implementation")
val pluginDisposable: Disposable = error("Stub implementation")