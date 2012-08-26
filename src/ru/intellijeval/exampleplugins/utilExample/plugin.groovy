import javax.swing.*

import static ru.intellijeval.PluginUtil.*
import util.AClass

// log message in "Event Log"
log("Hello IntelliJ")

// popup notification
show("IntelliJ", "Hello")

// "event" is an instance of AnActionEvent, it's implicitly available in all plugin.groovy scripts
// (for more details about AnActionEvent please see IntelliJ API)
show(event.project.name, "Project")

// external class
show(AClass.produceAString(), "Hello")

// shows text in console (useful for stacktraces or if text is large)
showInConsole("Hello console", event.project)

// shows exception's stacktrace in console
showExceptionInConsole(new IllegalStateException(), event.project)

registerAction("My Action", "ctrl alt shift H") {
	show("IntelliJ", "Hello")
}
//unregisterAction("My Action")

registerToolWindow("My Toolwindow", new JTextArea("Hello"))
//unregisterToolWindow("My Toolwindow")
