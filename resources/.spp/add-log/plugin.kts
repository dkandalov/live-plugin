import com.intellij.openapi.application.runWriteAction
import spp.plugin.*
import spp.command.*

class AddLogCommand : LiveCommand() {
    override val name = message("add_log")
    override val description = "<html><span style=\"font-size: 80%; color: ${getCommandTypeColor()}\">" +
            message("live_instrument") + " ➛ " + message("add") + " ➛ " + message("location") +
            ": </span><span style=\"font-size: 80%; color: ${getCommandHighlightColor()}\">" + message("on_line") +
            " *lineNumber*</span></html>"
    override val selectedIcon = "add-log/icons/live-log_selected.svg"
    override val unselectedIcon = "add-log/icons/live-log_unselected.svg"

    override fun trigger(context: LiveCommandContext) {
        runWriteAction {
            showLogStatusBar(context.lineNumber)
        }
    }
}

registerCommand { AddLogCommand() }
