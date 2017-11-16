import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

import static liveplugin.PluginUtil.registerAction
import static liveplugin.PluginUtil.show

//
// Most of the user interactions with IDE are performed using actions.
// Conceptually, an action is a stateless function which takes AnActionEvent object
// and creates some side effect (e.g. moves cursor up).
//

class HelloWorldAction extends AnAction {
	@Override void actionPerformed(AnActionEvent e) { show("Hello world") }
}
registerAction("HelloWorldAction", "ctrl shift H", new HelloWorldAction())
if (!isIdeStartup) show("Loaded 'HelloWorldAction'<br/>Use ctrl+shift+H to run it")


registerAction("HelloWorldAction", "alt shift H") { AnActionEvent event ->
	show("Hello '${event.project.name}'")
}
if (!isIdeStartup) show("Loaded 'HelloWorldAction'<br/>Use alt+shift+H to run it")

//
// In the code above registerAction() and show() methods are part of LivePlugin.
// They wrap IntelliJ API to hide boilerplate code needed for registering an action.
// You can find source code here https://github.com/dkandalov/live-plugin/blob/master/src/plugin-util-groovy/liveplugin/PluginUtil.groovy.
//
// In IDEs with Java/Groovy support you can make code navigable by doing the following:
//  - install/enable Groovy plugin
//  - "Plugin toolwindow -> Settings -> Add LivePlugin and IDE Jars to Project"
//    (the jar also includes source code for PluginUtil class).
//    Of course, adding jars unrelated to current project is a hack
//    but if you use separate build system for creating releases it shouldn't cause many problems.
//

//
// See next insert-new-line-above example.
//          ^^^^^^^^^^^^^^^^^^^^^
