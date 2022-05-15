import com.intellij.openapi.application.runWriteAction
import spp.plugin.*
import spp.command.*
import spp.jetbrains.sourcemarker.PluginUI.*
import spp.jetbrains.sourcemarker.PluginBundle.message
import spp.jetbrains.sourcemarker.status.LiveStatusManager

class AddLogCommand : LiveCommand() {
    override val name = message("add_log")
    override val description = "<html><span style=\"color: ${getCommandTypeColor()}\">" +
            message("live_instrument") + " ➛ " + message("add") + " ➛ " + message("location") +
            ": </span><span style=\"color: ${getCommandHighlightColor()}\">" + message("on_line") +
            " *lineNumber*</span></html>"
    override val selectedIcon = "add-log/icons/live-log_selected.svg"
    override val unselectedIcon = "add-log/icons/live-log_unselected.svg"

    override fun trigger(context: LiveCommandContext) {
        runWriteAction {
            LiveStatusManager.showLogStatusBar(project.currentEditor!!, context.lineNumber, false)
        }
    }
}

registerCommand(AddLogCommand())
