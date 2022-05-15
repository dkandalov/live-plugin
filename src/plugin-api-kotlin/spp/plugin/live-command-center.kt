@file:Suppress("unused")

package spp.plugin

import liveplugin.implementation.command.LiveCommandService
import liveplugin.implementation.pluginrunner.kotlin.LivePluginScript
import spp.command.LiveCommand

fun LivePluginScript.registerCommand(liveCommand: LiveCommand) {
    LiveCommandService.getInstance(project).registerLiveCommand(liveCommand)

    pluginDisposable.whenDisposed {
        LiveCommandService.getInstance(project).unregisterLiveCommand(liveCommand.name)
    }
}
