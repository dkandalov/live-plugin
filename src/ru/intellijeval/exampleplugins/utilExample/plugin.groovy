import javax.swing.*

import static ru.intellijeval.PluginUtil.*
import util.AClass

// log message in "Event Log"
log("Hello IntelliJ")

// popup notification
show("IntelliJ", "Hello")
show(AClass.produceAString(), "Hello")

// shows text in console (useful for large texts and stacktraces)
showInConsole("Hello console", event.project)

// shows exception stacktrace in console
showExceptionInConsole(new IllegalStateException(), event.project)

registerAction("My Action", "ctrl alt shift H") {
	show("IntelliJ", "Hello")
}

registerToolWindow("My Toolwindow", new JTextArea("Hello"))
