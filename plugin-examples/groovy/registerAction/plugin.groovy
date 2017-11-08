import com.intellij.openapi.actionSystem.AnActionEvent

import static liveplugin.PluginUtil.*

// All user interactions with IDE are performed using actions.
// Conceptually, Action is a stateless function which takes AnActionEvent object
// and creates some side effect (e.g. moves cursor up).

registerAction("HelloWorldAction", "alt shift H") { AnActionEvent event ->
	show("Hello '${event.project.name}'")
}

if (!isIdeStartup) show("Loaded 'HelloWorldAction'<br/>Use alt+shift+H to run it")


// In the code above registerAction() and show() methods are part of LivePlugin.
// They wrap IntelliJ API to hide boilerplate code needed for registering an action.
//
// You can find source code here https://github.com/dkandalov/live-plugin/blob/master/src/runtime/liveplugin/PluginUtil.groovy.
// Or in IDEs with java support you can make code navigable by doing the following:
//  - install/enable Groovy plugin
//  - "Plugin toolwindow -> Settings -> Add LivePlugin Jar to Project"
//    (the jar also includes source code for PluginUtil)
//  - "Plugin toolwindow -> Settings -> Add IDEA Jars to Project"
//
// Admittedly, adding jars unrelated to current project is a hack
// but if you use separate build system for creating releases it shouldn't cause many problems.


// See next "popupMenu" example.
