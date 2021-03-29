import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import liveplugin.actions.registerAction
import liveplugin.show

//
// Most of the user interactions with IDE are performed using actions.
// Conceptually, an action is a stateless function which takes AnActionEvent object and creates some side effect,
// for example, moves cursor, modifies source code or displays a message as in the code below.
//

registerAction(id = "HelloWorldActionAsFunction", keyStroke = "alt shift H") { event: AnActionEvent ->
    show("Hello '${event.project?.name}'")
}
if (!isIdeStartup) show("Loaded 'HelloWorldActionAsFunction'<br/>Use alt+shift+H to run it")


class HelloWorldAction: AnAction(), DumbAware {
    override fun actionPerformed(event: AnActionEvent) = show("Hello world")
}
registerAction(id = "HelloWorldAction", keyStroke = "ctrl shift H", action = HelloWorldAction())
if (!isIdeStartup) show("Loaded 'HelloWorldAction'<br/>Use ctrl+shift+H to run it")

//
// In the code examples above "registerAction" and "show" are functions imported from LivePlugin libraries.
// They wrap IntelliJ API to hide boilerplate code needed for registering an action.
// You can find source code here https://github.com/dkandalov/live-plugin/blob/master/src/plugin-util-kotlin/liveplugin/plugin-util.kt.
//
// In IDEs with Kotlin support you should be able to auto-complete and navigate the code above.
// LivePlugin comes with the source code for its libraries so you should be to see implementation of "show" and "registerAction".
//

//
// See next insert-new-line-above example.
//          ^^^^^^^^^^^^^^^^^^^^^
