import com.intellij.openapi.application.runWriteAction
import spp.plugin.*
import spp.command.*

class AddSpanCommand : LiveCommand() {
    override val name = message("add_span")
    override val description = "<html><span style=\"font-size: 80%; color: ${getCommandTypeColor()}\">" +
            message("live_instrument") + " ➛ " + message("add") + " ➛ " + message("location") +
            ": </span><span style=\"font-size: 80%; color: ${getCommandHighlightColor()}\">" + message("on_method") +
            " *methodName*</span></html>"
    override val selectedIcon = "add-span/icons/live-span_selected.svg"
    override val unselectedIcon = "add-span/icons/live-span_unselected.svg"

    override fun trigger(context: LiveCommandContext) {
        runWriteAction {
            showSpanStatusBar(context.lineNumber)
        }
    }
}

registerCommand { AddSpanCommand() }
