import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import liveplugin.registerAction
import liveplugin.show

// Most of the user interactions with IDE are performed using actions.
// Conceptually, an action is a stateless function which takes AnActionEvent object
// and creates some side effect (e.g. moves cursor up).

registerAction(id = "HelloWorldAction1", keyStroke = "alt shift H") { event: AnActionEvent ->
    show("Hello '${event.project?.name}'")
}
if (!isIdeStartup) show("Loaded 'HelloWorldAction1'<br/>Use alt+shift+H to run it")


class HelloWorldAction: AnAction() {
    override fun actionPerformed(event: AnActionEvent) = show("Hello world")
}
registerAction(id = "HelloWorldAction2", keyStroke = "ctrl shift H", action = HelloWorldAction())

if (!isIdeStartup) show("Loaded 'HelloWorldAction2'<br/>Use ctrl+shift+J to run it")
