import com.intellij.execution.ui.ConsoleView
import liveplugin.implementation.Console
import util.AClass

import static liveplugin.PluginUtil.*

// For more util methods see
// https://github.com/dkandalov/live-plugin/blob/master/src_groovy/liveplugin/PluginUtil.groovy

// log message in "Event Log"
log("Hello IntelliJ")

// popup notification
show("IntelliJ", "Hello")

// using imported class
show(AClass.sayHello(), "Hello")

// shows text in console
ConsoleView console = showInConsole("Hello console", "my console", project)
console.print("....", Console.guessContentTypeOf("....")) // append text to the same console


// shows exception's stacktrace in console
showInConsole(new Exception("This is a fake exception to show exception in a console"), "console with exception", project)
