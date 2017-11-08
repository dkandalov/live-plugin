import com.intellij.openapi.actionSystem.AnActionEvent
import liveplugin.registerAction
import liveplugin.show

// All user interactions with IDE are performed using actions.
// Conceptually, action is a stateless function which takes AnActionEvent object
// and creates some side effect (e.g. moves cursor up).

registerAction(
    id = "HelloWorldAction",
    keyStroke = "alt shift H",
    disposable = pluginDisposable
) { event: AnActionEvent ->
    show("Hello '${event.project?.name}'")
}

if (!isIdeStartup) show("Loaded 'HelloWorldAction'<br/>Use alt+shift+H to run it")
