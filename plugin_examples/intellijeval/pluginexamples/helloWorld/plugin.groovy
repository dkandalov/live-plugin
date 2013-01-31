import com.intellij.openapi.actionSystem.AnActionEvent
import static intellijeval.PluginUtil.*

// To run a plugin you can press "Run plugin" button or use "ctrl+C, ctrl+E" shortcut.
// This will try to run plugin which you currently edit (determined by opened editor)
// otherwise plugin selected in plugin tree will be run.


// This should show balloon message with "Hello IntelliJ" text.
// (If there is no balloon, it might be disabled in "IDE Settings - Notifications".)
show("Hello IntelliJ")


// There are couple implicit variables available in "plugin.groovy" files.
// One of them is "event" which contains information about action executing this script.
show(((AnActionEvent) event))
// It can be used to get reference to the current project (e.g. if IntelliJ API needs to know it).
show(event.project)

// "pluginPath" - absolute path to this plugin folder.
show(pluginPath)

// "isIdeStartup" - true on IDE startup if "Run all plugins on IDE start" option is enabled.
show(isIdeStartup)
