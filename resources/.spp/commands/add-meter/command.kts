import com.intellij.openapi.application.runWriteAction
import spp.plugin.*
import spp.command.*
import spp.jetbrains.sourcemarker.PluginUI.*
import spp.jetbrains.sourcemarker.PluginBundle.message
import spp.jetbrains.sourcemarker.status.LiveStatusManager

class AddMeterCommand : LiveCommand() {
    override val name = message("add_meter")
    override val description = "<html><span style=\"color: ${getCommandTypeColor()}\">" +
            message("live_instrument") + " ➛ " + message("add") + " ➛ " + message("location") +
            ": </span><span style=\"color: ${getCommandHighlightColor()}\">" + message("on_line") +
            " *lineNumber*</span></html>"
    override val selectedIcon = "add-meter/icons/live-meter_selected.svg"
    override val unselectedIcon = "add-meter/icons/live-meter_unselected.svg"

    override fun trigger(context: LiveCommandContext) {
        runWriteAction {
            LiveStatusManager.showMeterStatusBar(project.currentEditor!!, context.lineNumber)
        }
    }
}

if (liveInstrumentService != null) {
    registerCommand(AddMeterCommand())
}
