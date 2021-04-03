import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import liveplugin.PluginUtil.*
import liveplugin.popups.*
import liveplugin.popups.MenuEntry.*
import liveplugin.actions.registerAction
import liveplugin.show

registerAction("HelloPopupAction", "ctrl alt shift P") { event ->
    val project = event.project
    val menuDescription = listOf(
        SubMenu(
            "Open in browser",
            Action("IntelliJ API mini-cheatsheet") {
                openInBrowser("https://github.com/dkandalov/live-plugin/blob/master/IntellijApiCheatSheet.md")
            },
            Action("IntelliJ Platform SDK DevGuide - Fundamentals") {
                openInBrowser("https://plugins.jetbrains.com/docs/intellij/fundamentals.html")
            }
        ),
        Action("Execute shell command") {
            val command = showInputDialog("Enter a command (e.g. 'ls'):")
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
            openInEditor("$pluginPath/plugin.kts")
        }
    )
    createPopupMenu(menuDescription, popupTitle = "Hello PopupMenu", dataContext = event.dataContext).show()
}

class HelloAction: AnAction("Run AnAction") {
    override fun actionPerformed(anActionEvent: AnActionEvent) {
        show("Running AnAction")
    }
}

if (!isIdeStartup) show("Loaded 'helloPopupAction'<br/>Use ctrl+alt+shift+P to run it")

//
// See next intention and java-intention examples.
//          ^^^^^^^^^     ^^^^^^^^^^^^^^
