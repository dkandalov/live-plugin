
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import liveplugin.registerAction
import liveplugin.show

//
// Most of the user interactions in IDE are performed using actions.
// Conceptually an action is a stateless function which takes AnActionEvent object
// and creates some side effect, e.g. moves cursor up or displays a message as in the code below.
//

class HelloWorldAction: AnAction() {
    override fun actionPerformed(event: AnActionEvent) = show("Hello world")
}
registerAction(id = "HelloWorldAction", keyStroke = "ctrl shift H", action = HelloWorldAction())
if (!isIdeStartup) show("Loaded 'HelloWorldAction'<br/>Use ctrl+shift+H to run it")


registerAction(id = "HelloWorldActionAsFunction", keyStroke = "alt shift H") { event: AnActionEvent ->
    show("Hello '${event.project?.name}'")
}
if (!isIdeStartup) show("Loaded 'HelloWorldActionAsFunction'<br/>Use alt+shift+H to run it")

//
// In the code above registerAction() and show() methods are part of LivePlugin.
// They wrap IntelliJ API to hide boilerplate code needed for registering an action.
// You can find source code here https://github.com/dkandalov/live-plugin/blob/master/src/plugin-util-kotlin/liveplugin/plugin-util.kt.
//
// In IDEs with Java/Kotlin support you should be able to autocomplete and navigate the code above.
// Besides LivePlugin comes with source code for "plugin-util.kt" so it should be available in IDE.
//

//
// See next insert-new-line-above example.
//          ^^^^^^^^^^^^^^^^^^^^^
