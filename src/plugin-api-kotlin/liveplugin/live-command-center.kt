@file:Suppress("unused")

package liveplugin

import com.intellij.openapi.util.Key
import spp.jetbrains.marker.extend.LiveCommand
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Function

fun LiveCommand.registerCommand() {
    registerCommand { this }
}

fun registerCommand(command: () -> LiveCommand) {
    val commandRegister = Key.findKeyByName("SPP_COMMAND_REGISTER")
    if (commandRegister != null) {
        registerCommand(command.invoke(), commandRegister)
    } else {
        runBackgroundTask("Waiting for command center...") {
            while (true) {
                val commandRegister = Key.findKeyByName("SPP_COMMAND_REGISTER")
                if (commandRegister != null) {
                    registerCommand(command.invoke(), commandRegister)
                    break
                }
                Thread.sleep(100)
            }
        }
    }
}

private fun registerCommand(liveCommand: LiveCommand, commandRegister: Key<*>) {
    val commandRegistry = liveCommand.project.getUserData(commandRegister)
            as BiConsumer<String, BiConsumer<String, Consumer<Array<Any?>>>>
    commandRegistry.accept(liveCommand.toJson(), liveCommand.triggerConsumer)
}

fun LiveCommand.message(message: String): String {
    val pluginUIFunctions = Key.findKeyByName("PLUGIN_UI_FUNCTIONS")!!
    val consumer = project.getUserData(pluginUIFunctions) as Function<Array<Any?>, String>
    return consumer.apply(arrayOf("message", message))
}

fun LiveCommand.getCommandTypeColor(): String {
    val pluginUIFunctions = Key.findKeyByName("PLUGIN_UI_FUNCTIONS")!!
    val consumer = project.getUserData(pluginUIFunctions) as Function<Array<Any?>, String>
    return consumer.apply(arrayOf("getCommandTypeColor"))
}

fun LiveCommand.getCommandHighlightColor(): String {
    val pluginUIFunctions = Key.findKeyByName("PLUGIN_UI_FUNCTIONS")!!
    val consumer = project.getUserData(pluginUIFunctions) as Function<Array<Any?>, String>
    return consumer.apply(arrayOf("getCommandHighlightColor"))
}
