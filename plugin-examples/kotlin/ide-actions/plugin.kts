import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import liveplugin.actions.registerAction
import liveplugin.show

// Most of the user interactions with IDE are performed using actions.
// Conceptually, an action is a stateless function that takes AnActionEvent object and creates some side effect.
// For example, moves cursor, modifies source code or displays a message as in the code below.
// (See also https://plugins.jetbrains.com/docs/intellij/basic-action-system.html)

// Option 1.
// You can create and register an action using registerAction() function which takes:
//  - action id (must be unique)
//  - keystroke (see https://docs.oracle.com/javase/8/docs/api/javax/swing/KeyStroke.html#getKeyStroke-java.lang.String-)
//  - callback which will be invoked when the action is executed
//  - disposable which will unregister the action when disposed;
//    it can be passed explicitly or via LivePluginScript.registerAction() extension function like in the code below
registerAction(id = "Hello World", keyStroke = "alt shift H") { event: AnActionEvent ->
    show("Hello from action! Project: ${event.project?.name}")
}
if (!isIdeStartup) show("Loaded 'Hello World'<br/>Use alt+shift+H to run it")

// Option 2.
// You can create an instance of AnAction class and pass it to registerAction().
class ProjectPathAction: AnAction(), DumbAware {
    override fun actionPerformed(event: AnActionEvent) =
        show("Project path: ${event.project?.basePath}")
}
registerAction(id = "Show Project Path", keyStroke = "ctrl shift H", action = ProjectPathAction())
if (!isIdeStartup) show("Loaded 'Show Project Path'<br/>Use ctrl+shift+H to run it")

// Option 3.
// Use ActionManager directly to add, remove or find actions.
// See https://upsource.jetbrains.com/idea-ce/file/idea-ce-d60a9e652f1458d4e67e0bb6e8215fb125f5a478/platform/editor-ui-api/src/com/intellij/openapi/actionSystem/ActionManager.java

// In the code examples above "registerAction" and "show" are functions imported from LivePlugin libraries.
// They wrap IntelliJ API to hide boilerplate code needed for registering an action.
// You can find source code here https://github.com/dkandalov/live-plugin/blob/master/src/plugin-util-groovy/liveplugin/PluginUtil.groovy.

// In IDEs with Kotlin support you should be able to auto-complete and navigate the code above.
// LivePlugin comes with the source code for its libraries so you should be to see
// implementation of "show" and "registerAction".

// See next insert-new-line-above example.
//          ^^^^^^^^^^^^^^^^^^^^^
