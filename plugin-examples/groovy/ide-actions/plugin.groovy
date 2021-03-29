import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

import static liveplugin.PluginUtil.registerAction
import static liveplugin.PluginUtil.show

//
// Most of the user interactions with IDE are performed using actions.
// Conceptually, an action is a stateless function which takes AnActionEvent object and creates some side effect,
// for example, moves cursor, modifies source code or displays a message as in the code below.
//

registerAction("HelloProjectAction", "alt shift H") { AnActionEvent event ->
	show("Hello '${event.project.name}'")
}
if (!isIdeStartup) show("Loaded 'HelloProjectAction'<br/>Use alt+shift+H to run it")


class HelloWorldAction extends AnAction implements DumbAware {
	@Override void actionPerformed(AnActionEvent e) { show("Hello world") }
}
registerAction("HelloWorldAction", "ctrl shift H", new HelloWorldAction())
if (!isIdeStartup) show("Loaded 'HelloWorldAction'<br/>Use ctrl+shift+H to run it")


//
// In the code examples above "registerAction" and "show" are functions imported from LivePlugin libraries.
// They wrap IntelliJ API to hide boilerplate code needed for registering an action.
// You can find source code here https://github.com/dkandalov/live-plugin/blob/master/src/plugin-util-groovy/liveplugin/PluginUtil.groovy.
//
// In IDEs with Java/Groovy support you can make code navigable by doing the following:
//  - install/enable Groovy plugin
//  - enable checkbox "Plugins toolwindow -> Settings -> Add LivePlugin and IDE Jars to Project"
//    (this will also include source code for PluginUtil class).
//    Of course, adding jars unrelated to current project is a hack but most of the time
//    it shouldn't cause many problems and you can always undo it in ""Plugins toolwindow -> Settings".
//

//
// See next insert-new-line-above example.
//          ^^^^^^^^^^^^^^^^^^^^^
