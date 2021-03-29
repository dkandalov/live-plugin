import liveplugin.show

//
// To run a plugin press "Run plugin" button in the "Plugins" toolwindow ("alt+C, alt+E" or "ctrl/cmd+shift+L" shortcut).
// This will run the plugin currently selected in the toolwindow or opened in editor.
//
// You might notice that it takes few seconds the first time you run a Kotlin plugin.
// However, it should get faster on the following runs.
//

// The code below will show balloon message with "Hello world" text (which should also appear in IDE "Event Log" toolwindow).
// If there is no balloon, it might be disabled in "IDE Settings - Notifications".
show("Hello kotlin world")

// There are several implicit variables available in plugin.groovy files.
// "isIdeStartup" - true on IDE startup, otherwise false. Plugins are executed on IDE startup
//                  if "Plugins toolwindow -> Settings -> Run all plugins on IDE start" option is enabled.
show("isIdeStartup: $isIdeStartup")

// "project" - project in which plugin is executed, it can be null on IDE startup or if there are no open projects.
// It is an instance of com.intellij.openapi.project.Project, see
// https://upsource.jetbrains.com/idea-ce/file/idea-ce-ba0c8fc9ab9bf23a71a6a963cd84fc89b09b9fc8/platform/core-api/src/com/intellij/openapi/project/Project.java
show("project: $project")

// "pluginDisposable" - instance of com.intellij.openapi.Disposable which will be disposed before the plugin is run again.
// It can be useful, for example, to pass into listeners so that when you re-evaluate plugin code, old listeners are removed.
// See also https://plugins.jetbrains.com/docs/intellij/disposers.html#implementing-the-disposable-interface
show("pluginDisposable: $pluginDisposable")

// "pluginPath" - absolute path to this plugin's folder normalised to use "/" as path separator.
show("pluginPath: $pluginPath")

//
// See next ide-actions example.
//          ^^^^^^^^^^^
