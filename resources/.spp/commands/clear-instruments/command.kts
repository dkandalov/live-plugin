import com.intellij.notification.NotificationType.ERROR
import spp.command.LiveCommand
import spp.command.LiveCommandContext
import spp.jetbrains.sourcemarker.PluginBundle.message
import spp.jetbrains.sourcemarker.PluginUI.getCommandTypeColor
import spp.plugin.registerCommand
import spp.plugin.show

class ClearInstrumentsCommand : LiveCommand() {
    override val name = "Clear Instruments"
    override val description = "<html><span style=\"color: ${getCommandTypeColor()}\">" +
            message("live_instrument") + " ➛ " + message("clear_all") + "</span></html>"
    override val selectedIcon: String = "clear-instruments/icons/clear-instruments_selected.svg"
    override val unselectedIcon: String = "clear-instruments/icons/clear-instruments_unselected.svg"

    override suspend fun triggerSuspend(context: LiveCommandContext) {
        if (liveInstrumentService == null) {
            show("Live instrument service unavailable", notificationType = ERROR)
        } else {
            liveInstrumentService.clearAllLiveInstruments(null).onComplete {
                if (it.succeeded()) {
                    show("Successfully cleared active live instrument(s)")
                } else {
                    show(it.cause().message, notificationType = ERROR)
                }
            }
        }
    }
}

registerCommand(ClearInstrumentsCommand())
