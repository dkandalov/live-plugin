import com.intellij.openapi.application.runWriteAction
import liveplugin.*
import spp.jetbrains.marker.extend.LiveCommand
import spp.jetbrains.marker.extend.LiveCommandContext

class AddBreakpointCommand : LiveCommand() {
    override val name = message("add_breakpoint")
    override val description = "<html><span style=\"font-size: 80%; color: ${getCommandTypeColor()}\">" +
            message("live_instrument") + " ➛ " + message("add") + " ➛ " + message("location") +
            ": </span><span style=\"font-size: 80%; color: ${getCommandHighlightColor()}\">" + message("on_line") +
            " *lineNumber*</span></html>"
    override val selectedIcon = "add-breakpoint/icons/live-breakpoint_selected.svg"
    override val unselectedIcon = "add-breakpoint/icons/live-breakpoint_unselected.svg"

    override fun trigger(context: LiveCommandContext) {
        runWriteAction {
            showBreakpointStatusBar(context.lineNumber)
        }
    }
}

registerCommand { AddBreakpointCommand() }
