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
showInConsole("Hello console", "my console", project)
showInConsole("....", "my console", project)

// shows exception's stacktrace in console
showInConsole(new Exception("This is a fake exception to show exception in a console"), "console with exception", event.project)
