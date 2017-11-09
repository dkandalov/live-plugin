import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import liveplugin.MenuEntry.*
import liveplugin.PluginUtil.*
import liveplugin.createPopupMenu
import liveplugin.registerAction
import liveplugin.show

registerAction("HelloPopupAction", "ctrl alt shift P") { event ->
    val project = event.project
    val menuDescription = listOf(
        SubMenu(
            "Hello",
            Action("sub-menu 1") { show("sub-menu 1") },
            Action("sub-menu 2") { show("sub-menu 2") }
        ),
        SubMenu(
            "Open in browser",
            Action("IntelliJ Architectural Overview") {
                openInBrowser("http://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview.html")
            },
            Action("IntelliJ API mini cheat sheet") {
                openInBrowser("https://github.com/dkandalov/live-plugin/blob/master/IntellijApiCheatSheet.md")
            }
        ),
        Action("Execute command") {
            val command = showInputDialog("Enter a command:")
            if (command != null) show(execute(command).toString().replace("\n", "<br/>"))
        },
        Action("Show current project") { (_, popupEvent) ->
            // Note "event" cannot be used here (e.g. "event.project") because AnActionEvent objects are not shareable between swing events.
            // There is "event" created when HelloPopupAction is performed. It's passed to "registerAction()" callback.
            // And there is "popupEvent" created when user clicks on popup menu item.
            show("project: $project")
            show("project: ${popupEvent.project}")
        },
        Delegate(HelloAction()),
        Separator,
        Action("Edit Popup Menu...") {
            openInEditor(pluginPath + "/plugin.kts")
        }
    )
    createPopupMenu(menuDescription, popupTitle = "Hello PopupMenu", dataContext = event.dataContext).show()
}

class HelloAction: AnAction("Run actual action") {
    override fun actionPerformed(anActionEvent: AnActionEvent) {
        show("Running actual action")
    }
}