import com.intellij.openapi.actionSystem.AnActionEvent

import static liveplugin.PluginUtil.*

// Most of user interaction with IDE is done through actions.
// Action conceptually is a stateless function which takes AnActionEvent object
// and creates some side effect (e.g. moving editor cursor up).
//
// The code below registers action with id "HelloWorldAction" and alt+shift+H shortcut.
// PluginUtil.registerAction() and show() method are wrappers around IntelliJ API.
//
// From practical point of view actions can be used for running frequent routine tasks,
// e.g. running your favorite shell script for current project.

registerAction("HelloWorldAction", "alt shift H") { AnActionEvent event ->
	show("Hello world from action")
	show("Current project name: ${event.project.name}")
}

if (!isIdeStartup) show("Loaded 'HelloWorldAction'<br/>Use alt+shift+H to run it")


// If you were wondering what registerAction() method does,
// you can look at the source code here https://github.com/dkandalov/live-plugin/blob/master/src_groovy/liveplugin/PluginUtil.groovy.
//
// Or even better in IDEs with java support you can make code navigable:
//  - install/enable Groovy plugin
//  - "Plugin toolwindow -> Settings -> Add LivePlugin Jar to Project"
//    (the jar also includes source code for PluginUtil)
//  - "Plugin toolwindow -> Settings -> Add IDEA Jars to Project"
//
// Admittedly, adding jars unrelated to your actual project is a hack
// but there seems to be no major problems with it.


// See next "popupMenu" example.
