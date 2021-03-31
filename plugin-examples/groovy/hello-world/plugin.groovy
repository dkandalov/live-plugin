import com.intellij.openapi.diagnostic.Logger
import static liveplugin.PluginUtil.*

if (isIdeStartup) return

// To run a plugin press "Run plugin" button in the "Plugins" toolwindow ("alt+C, alt+E" or "ctrl/cmd+shift+L" shortcut).
// This will run the plugin currently selected in the toolwindow or opened in editor.

// The code below will show balloon message with "Hello world" text (which should also appear in "Event Log" toolwindow).
// If there is no balloon, it might be disabled in "IDE Settings - Notifications".
show("Hello world")

// There are several implicit variables available in plugin.groovy files.
//  - "isIdeStartup" which is true on IDE startup, otherwise false. Plugins are executed on IDE startup
//    if "Plugins toolwindow -> Settings -> Run all plugins on IDE start" option is enabled.
//  - "project" in which plugin is executed, it can be null on IDE startup or if there are no open projects.
//    It is an instance of com.intellij.openapi.project.Project, see
//    https://upsource.jetbrains.com/idea-ce/file/idea-ce-ba0c8fc9ab9bf23a71a6a963cd84fc89b09b9fc8/platform/core-api/src/com/intellij/openapi/project/Project.java
//  - "pluginDisposable" instance of com.intellij.openapi.Disposable which will be disposed
//    when you press "Unload Plugin" button in "Plugins" toolwindow or just before the same plugin is run again.
//    It can be useful, for example, to pass into listeners so that when you re-evaluate plugin code, the old listeners are removed.
//    See also https://plugins.jetbrains.com/docs/intellij/disposers.html#implementing-the-disposable-interface
//  - "pluginPath" with an absolute path to this plugin's folder normalised to use "/" as path separator.

show("isIdeStartup: $isIdeStartup")
show("project: $project")
show("pluginDisposable: $pluginDisposable")
show("pluginPath: $pluginPath")

// Using "show()" function is often the simplest way to see what's going on in the plugin.
// However, for large messages it's better to use STDOUT or Logger which will write to "idea.log".
// You can find "idea.log" location using `Main menu - Help - Show log`
// or by evaluating com.intellij.openapi.application.PathManager.getLogPath().
println("Hello world on stdout")
Logger.getInstance("HelloLogger").info("Hello world")

// See next ide-actions example.
//          ^^^^^^^^^^^
