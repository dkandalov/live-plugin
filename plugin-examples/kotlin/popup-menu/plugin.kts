import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.ui.Messages
import liveplugin.PluginUtil.*
import liveplugin.*

registerAction(id = "Show Actions Popup", "ctrl alt shift P") { event ->
    val actionGroup = PopupActionGroup("Some Actions",
        AnAction("Execute Shell Command") {
            val command = Messages.showInputDialog("Enter a command (e.g. 'ls'):", "Dialog Title", null)
            if (command != null) show(execute(command).toString().replace("\n", "<br/>"))
        },
        AnAction("Show Current Project") { popupEvent ->
            // Note that "event" from the outer "Show Actions Popup" action cannot be used here
            // because AnActionEvent objects are not shareable between swing events.
            // show("project: ${event.project}") // Will throw "cannot share data context between Swing events".

            // Instead we should use "popupEvent" specific to the "Show current project" action.
            // (In order for "project" to be available in the "popupEvent", createPopup() is called with "event.dataContext" argument.)
            show("project: ${popupEvent.project}")
        },
        HelloAction(),
        Separator.getInstance(),
        AnAction("Edit Popup...") {
            openInEditor("$pluginPath/plugin.kts")
        },
        PopupActionGroup("Documentation",
            AnAction("IntelliJ API Mini-Cheatsheet") {
                openInBrowser("https://github.com/dkandalov/live-plugin/blob/master/IntellijApiCheatSheet.md")
            },
            AnAction("Plugin SDK - Fundamentals") {
                openInBrowser("https://www.jetbrains.org/intellij/sdk/docs/platform/fundamentals.html")
            }
        )
    )
    actionGroup.createPopup(event.dataContext)
        .showCenteredInCurrentWindow(event.project!!)
}

class HelloAction: AnAction("Hello Action") {
    override fun actionPerformed(anActionEvent: AnActionEvent) {
        show("Hello!")
    }
}

if (!isIdeStartup) show("Loaded 'Show Actions Popup'<br/>Use ctrl+alt+shift+P to run it")
