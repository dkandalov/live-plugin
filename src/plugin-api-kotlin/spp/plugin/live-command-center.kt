@file:Suppress("unused")

package spp.plugin

import liveplugin.implementation.command.SourceCommander
import liveplugin.implementation.pluginrunner.kotlin.LivePluginScript
import spp.command.LiveCommand

fun LivePluginScript.registerCommand(liveCommand: LiveCommand) {
    SourceCommander.commandService.registerLiveCommand(liveCommand)

    pluginDisposable.whenDisposed {
        SourceCommander.commandService.unregisterLiveCommand(liveCommand.name)
    }
}
