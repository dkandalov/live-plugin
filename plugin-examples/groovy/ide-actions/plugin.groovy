import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

import static liveplugin.PluginUtil.registerAction
import static liveplugin.PluginUtil.show

// Most of the user interactions with IDE are performed using actions.
// Conceptually, an action is a stateless function that takes AnActionEvent object and creates some side effect.
// For example, moves the cursor, modifies source code or displays a message as in the code below.
// (See also https://plugins.jetbrains.com/docs/intellij/basic-action-system.html)

// Option 1.
// You can create and register an action using registerAction() function which takes:
//  - actionId (must be unique)
//  - keystroke (see https://docs.oracle.com/javase/8/docs/api/javax/swing/KeyStroke.html#getKeyStroke-java.lang.String-)
//  - callback which will be invoked when the action is executed
registerAction("Hello World", "alt shift H") { AnActionEvent event ->
	show("Hello from action! Project: ${event.project.name}")
}
if (!isIdeStartup) show("Loaded 'Hello World'<br/>Use alt+shift+H to run it")

// Option 2.
// You can create an instance of AnAction class and pass it to registerAction().
class ShowProjectPathAction extends AnAction implements DumbAware {
	@Override void actionPerformed(AnActionEvent event) {
		show("Project path: ${event.project.basePath}")
	}
}
registerAction("Show Project Path", "alt shift P", new ShowProjectPathAction())
if (!isIdeStartup) show("Loaded 'Show Project Path'<br/>Use alt+shift+P to run it")

// Option 3.
// Use ActionManager directly to add, remove or find actions.
// See https://upsource.jetbrains.com/idea-ce/file/idea-ce-d60a9e652f1458d4e67e0bb6e8215fb125f5a478/platform/editor-ui-api/src/com/intellij/openapi/actionSystem/ActionManager.java

// In the code examples above "registerAction" and "show" are functions imported from LivePlugin libraries.
// They wrap IntelliJ API to hide boilerplate code needed for registering an action.
// You can find source code here https://github.com/dkandalov/live-plugin/blob/master/src/plugin-util-groovy/liveplugin/PluginUtil.groovy.

// In IDEs with Java/Groovy support you can make code navigable by doing the following:
//  - install/enable Groovy plugin
//  - enable checkbox "Plugins tool window -> Settings -> Add LivePlugin and IDE Jars to Project"
//    (this will also include source code for PluginUtil class).
//    Of course, adding jars unrelated to current project is a hack but most of the time
//    it shouldn't cause many problems and you can always undo it in "Plugins tool window -> Settings".

// See next text-editor example.
//          ^^^^^^^^^^^
