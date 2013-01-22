import javax.swing.*

import static ru.intellijeval.PluginUtil.*
import util.AClass

// log message in "Event Log"
log("Hello IntelliJ")

// popup notification
show("IntelliJ", "Hello")

// using imported class
show(AClass.sayHello(), "Hello")

// shows text in console (useful for stacktraces or if text is large)
showInConsole("Hello console", event.project)

// shows exception's stacktrace in console
showExceptionInConsole(new Exception("This is a fake exception to show exception in a console"), event.project)

registerAction("My Action", "ctrl alt shift H") {
	show("IntelliJ", "Hello")
}

registerToolWindow("My Toolwindow", new JTextArea("Hello"))