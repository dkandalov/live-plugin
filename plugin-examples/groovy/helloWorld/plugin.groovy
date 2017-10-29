import static liveplugin.PluginUtil.show

if (isIdeStartup) return

// To run a plugin you can press "Run plugin" button or use "alt+C, alt+E" shortcut.
// This will run plugin currently open in editor or plugin selected in the plugin toolwindow.

// The code below will show balloon message with "Hello world" text (it will also appear in IDE "Event Log" toolwindow).
// (If there is no balloon, it might be disabled in "IDE Settings - Notifications".)
show("Hello world")

// "isIdeStartup" - true on IDE startup, otherwise false. Plugins are executed on IDE startup
//                  if "Plugins toolwindow -> Settings -> Run all plugins on IDE start" option is enabled.
show("isIdeStartup: $isIdeStartup")

// There are several implicit variables available in plugin files.
// "project" - project in which plugin is executed, can be null on IDE startup or if no projects are open.
show("project: $project")

// "pluginPath" - absolute path to this plugin folder.
show("pluginPath: $pluginPath")

// "pluginDisposable" - instance of com.intellij.openapi.Disposable which is disposed before plugin is run again.
show("pluginDisposable: $pluginDisposable")

// See next "registerAction" example.