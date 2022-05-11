@file:Suppress("unused")

package liveplugin

import com.intellij.openapi.util.Key
import spp.jetbrains.marker.extend.LiveCommand
import java.util.function.BiConsumer
import java.util.function.Consumer

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

