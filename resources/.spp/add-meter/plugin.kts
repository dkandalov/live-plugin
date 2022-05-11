import com.intellij.openapi.project.Project
import liveplugin.registerCommand
import liveplugin.show
import spp.jetbrains.marker.extend.LiveCommand
import spp.jetbrains.marker.extend.LiveCommandContext

class AddMeterCommand(project: Project) : LiveCommand(project) {
    override val name = message("add_meter")
    override val description = "<html><span style=\"font-size: 80%; color: ${getCommandTypeColor()}\">" +
            message("live_instrument") + " ➛ " + message("add") + " ➛ " + message("location") +
            ": </span><span style=\"font-size: 80%; color: ${getCommandHighlightColor()}\">" + message("on_line") +
            " *lineNumber*</span></html>"
    override val selectedIcon = ".spp/add-meter/icons/live-meter_selected.svg"
    override val unselectedIcon = ".spp/add-meter/icons/live-meter_unselected.svg"

    override fun trigger(context: LiveCommandContext) {
        show("todo meter")
//        ApplicationManager.getApplication().runWriteAction {
//            LiveStatusManager.showMeterStatusBar(editor, prevCommandBar.lineNumber)
//        }
    }
}

registerCommand { AddMeterCommand(project!!) }
