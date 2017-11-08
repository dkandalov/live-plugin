import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project

/**
 * True on IDE startup, otherwise false.
 * Plugins are executed on IDE startup if "Plugins toolwindow -> Settings -> Run all plugins on IDE start" option is enabled.
 */
val isIdeStartup: Boolean = error("Stub implementation")

/**
 * Project in which plugin is executed, can be null on IDE startup or if no projects are open.
 */
val project: Project? = error("Stub implementation")

/**
 * Absolute path to this plugin folder.
 */
val pluginPath: String = error("Stub implementation")

/**
 * Instance of `com.intellij.openapi.Disposable` which is disposed just before re-running plugin.
 * Can be useful for cleanup, e.g. un-registering IDE listeners.
 */
val pluginDisposable: Disposable = error("Stub implementation")