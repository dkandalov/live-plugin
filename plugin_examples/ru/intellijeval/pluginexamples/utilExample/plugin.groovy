import util.AClass

import static ru.intellijeval.PluginUtil.*
//
// For more details please see
// https://github.com/dkandalov/intellij_eval/blob/master/src_groovy/ru/intellijeval/PluginUtil.groovy
//

// log message in "Event Log"
log("Hello IntelliJ")

// popup notification
show("IntelliJ", "Hello")

// using imported class
show(AClass.sayHello(), "Hello")

// shows text in console
showInConsole("Hello console", "my console", project)
showInConsole("....", "my console", project)

// shows exception's stacktrace in new console
showInNewConsole(new Exception("This is a fake exception to show exception in a console"), "console with exception", project)
