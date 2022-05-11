import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import liveplugin.*
import spp.jetbrains.marker.extend.LiveCommand
import spp.jetbrains.marker.extend.LiveCommandContext

class AddMeterCommand(project: Project) : LiveCommand(project) {
    override val name = message("add_meter")
    override val description = "<html><span style=\"font-size: 80%; color: ${getCommandTypeColor()}\">" +
            message("live_instrument") + " ➛ " + message("add") + " ➛ " + message("location") +
            ": </span><span style=\"font-size: 80%; color: ${getCommandHighlightColor()}\">" + message("on_line") +
            " *lineNumber*</span></html>"
    override val selectedIcon = "add-meter/icons/live-meter_selected.svg"
    override val unselectedIcon = "add-meter/icons/live-meter_unselected.svg"

    override fun trigger(context: LiveCommandContext) {
        runWriteAction {
            showMeterStatusBar(context.lineNumber)
        }
    }
}

registerCommand { AddMeterCommand(project!!) }
