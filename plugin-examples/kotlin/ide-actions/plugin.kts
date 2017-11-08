import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import liveplugin.registerAction
import liveplugin.show

// Most of the user interactions with IDE are performed using actions.
// Conceptually, an action is a stateless function which takes AnActionEvent object
// and creates some side effect (e.g. moves cursor up).

registerAction(id = "HelloWorldAction1", keyStroke = "ctrl alt shift J", action = HelloWorldAction())

registerAction(id = "HelloWorldAction2", keyStroke = "alt shift H") { event: AnActionEvent ->
    show("Hello '${event.project?.name}'")
}

class HelloWorldAction: AnAction() {
    override fun actionPerformed(event: AnActionEvent) = show("Hello world")
}

if (!isIdeStartup) show("Loaded 'HelloWorldAction'<br/>Use alt+shift+H to run it")
