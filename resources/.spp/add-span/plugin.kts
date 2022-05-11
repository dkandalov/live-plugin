import com.intellij.openapi.project.Project
import liveplugin.registerCommand
import liveplugin.show
import spp.jetbrains.marker.extend.LiveCommand
import spp.jetbrains.marker.extend.LiveCommandContext

class AddSpanCommand(project: Project) : LiveCommand(project) {
    override val name = message("add_span")
    override val description = "<html><span style=\"font-size: 80%; color: ${getCommandTypeColor()}\">" +
            message("live_instrument") + " ➛ " + message("add") + " ➛ " + message("location") +
            ": </span><span style=\"font-size: 80%; color: ${getCommandHighlightColor()}\">" + message("on_method") +
            " *methodName*</span></html>"
    override val selectedIcon = ".spp/add-span/icons/live-span_selected.svg"
    override val unselectedIcon = ".spp/add-span/icons/live-span_unselected.svg"

    override fun trigger(context: LiveCommandContext) {
        show("todo span")
//        ApplicationManager.getApplication().runWriteAction {
//            LiveStatusManager.showSpanStatusBar(editor, prevCommandBar.lineNumber)
//        }
    }
}

registerCommand { AddSpanCommand(project!!) }
