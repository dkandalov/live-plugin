import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project

/**
 * True on IDE startup, otherwise false.
 * Where IDE startup means executing code on LivePlugin application component initialisation.
 * Use "Plugins toolwindow -> Settings -> Run all plugins on IDE start" to enable/disable it.
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