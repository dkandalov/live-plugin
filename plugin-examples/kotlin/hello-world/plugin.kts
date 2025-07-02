import com.intellij.openapi.diagnostic.Logger
import liveplugin.show

// To run a plugin, press the "Run plugin" button in the "Plugins" tool window ("alt+C, alt+E" or "ctrl/cmd+shift+L" shortcut).
// This will run the plugin currently selected in the tool window or opened in the editor.
// You might notice that it takes a few seconds the first time you run a Kotlin plugin.
// However, it should get faster on the following runs.

// The code below will show a balloon message with "Hello world" text (which should also appear in the "Event Log" tool window).
// If there is no balloon, it might be disabled in "IDE Settings - Notifications".
show("Hello Kotlin world")

// There are several implicit variables available in plugin.kts files.
//  - "isIdeStartup" which is true on IDE start, otherwise false. Plugins are executed on IDE start
//    if "Plugins tool window -> Settings -> Run Plugins on IDE Start" option is enabled.
//  - "project" in which the plugin is executed, it can be null on IDE start or if there are no open projects.
//    It is an instance of com.intellij.openapi.project.Project, see
//    https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/project/Project.java
//  - "pluginDisposable" instance of com.intellij.openapi.Disposable which will be disposed
//    when you press the "Unload Plugin" button in the "Plugins" tool window or just before the same plugin is run again.
//    It can be useful, for example, to pass into listeners so that when you re-evaluate plugin code, the old listeners are removed.
//    See also https://plugins.jetbrains.com/docs/intellij/disposers.html#implementing-the-disposable-interface
//  - "pluginPath" with an absolute path to this plugin's folder normalised to use "/" as a path separator.

show("isIdeStartup: $isIdeStartup")
show("project: $project")
show("pluginDisposable: $pluginDisposable")
show("pluginPath: $pluginPath")

// Using the "show()" function is often the simplest way to see what's going on in the plugin.
// However, for large messages it's better to use STDOUT or Logger which will write to "idea.log".
// You can find "idea.log" location using `Main menu - Help - Show log`
// or by evaluating com.intellij.openapi.application.PathManager.getLogPath().
println("Hello world on stdout")
Logger.getInstance("HelloLogger").info("Hello world")

//
// See next ide-actions example.
//          ^^^^^^^^^^^
