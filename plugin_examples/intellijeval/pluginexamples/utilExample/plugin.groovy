import util.AClass

import static intellijeval.PluginUtil.*

// For more util methods please see
// https://github.com/dkandalov/live-plugin/blob/master/src_groovy/intellijeval/PluginUtil.groovy

// log message in "Event Log"
log("Hello IntelliJ")

// popup notification
show("IntelliJ", "Hello")

// using imported class
show(AClass.sayHello(), "Hello")

// shows text in console
// (note that text is appended to exisiting console with the same title)
showInConsole("Hello console", "my console", project)
showInConsole("....", "my console", project)

// shows exception's stacktrace in new console
showInNewConsole(new Exception("This is a fake exception to show exception in a console"), "console with exception", event.project)
