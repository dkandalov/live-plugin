@file:Suppress("unused")

package liveplugin

import com.intellij.openapi.util.Key
import liveplugin.implementation.pluginrunner.kotlin.LivePluginScript
import spp.jetbrains.marker.extend.LiveCommand
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Function

private const val SPP_COMMAND_REGISTER = "SPP_COMMAND_REGISTER"
private const val SPP_COMMAND_UNREGISTER = "SPP_COMMAND_UNREGISTER"
private const val PLUGIN_UI_FUNCTIONS = "PLUGIN_UI_FUNCTIONS"

fun LivePluginScript.registerCommand(command: () -> LiveCommand) {
    if (Key.findKeyByName(SPP_COMMAND_REGISTER) != null) {
        registerCommand(command.invoke())
    } else {
        runBackgroundTask("Waiting for command center...") {
            while (true) {
                if (Key.findKeyByName(SPP_COMMAND_REGISTER) != null) {
                    registerCommand(command.invoke())
                    break
                }
                Thread.sleep(100)
            }
        }
    }
}

private fun LivePluginScript.registerCommand(liveCommand: LiveCommand) {
    val commandRegister = Key.findKeyByName(SPP_COMMAND_REGISTER)!!
    val commandRegisterFunc = project.getUserData(commandRegister)
            as BiConsumer<String, BiConsumer<String, Consumer<Array<Any?>>>>
    commandRegisterFunc.accept(liveCommand.toJson(), liveCommand.triggerConsumer)

    pluginDisposable.whenDisposed {
        val commandUnregister = Key.findKeyByName(SPP_COMMAND_UNREGISTER)!!
        val commandUnregisterFunc = project.getUserData(commandUnregister) as Consumer<String>
        commandUnregisterFunc.accept(liveCommand.name)
    }
}

fun LivePluginScript.message(message: String): String {
    val pluginUIFunctions = Key.findKeyByName(PLUGIN_UI_FUNCTIONS)!!
    val consumer = project.getUserData(pluginUIFunctions) as Function<Array<Any?>, String>
    return consumer.apply(arrayOf("message", message))
}

fun LivePluginScript.getCommandTypeColor(): String {
    val pluginUIFunctions = Key.findKeyByName(PLUGIN_UI_FUNCTIONS)!!
    val consumer = project.getUserData(pluginUIFunctions) as Function<Array<Any?>, String>
    return consumer.apply(arrayOf("getCommandTypeColor"))
}

fun LivePluginScript.getCommandHighlightColor(): String {
    val pluginUIFunctions = Key.findKeyByName(PLUGIN_UI_FUNCTIONS)!!
    val consumer = project.getUserData(pluginUIFunctions) as Function<Array<Any?>, String>
    return consumer.apply(arrayOf("getCommandHighlightColor"))
}
