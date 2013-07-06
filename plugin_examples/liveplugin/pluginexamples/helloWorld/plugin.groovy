import static liveplugin.PluginUtil.show

// To run a plugin you can press "Run plugin" button or use "alt+C, alt+E" shortcut.
// This will try to run plugin which you currently edit (determined by opened editor)
// otherwise plugin selected in plugin tree will be run.


// This should show balloon message with "Hello IntelliJ" text.
// (If there is no balloon, it might be disabled in "IDE Settings - Notifications".)
show("Hello IntelliJ")


// There are couple implicit variables available in "plugin.groovy" files.
// "project" - project in which plugin is executed, can be null if no projects are open
show(project)

// "pluginPath" - absolute path to this plugin folder.
show(pluginPath)

// "isIdeStartup" - true on IDE startup if "Run all plugins on IDE start" option is enabled.
show(isIdeStartup)
