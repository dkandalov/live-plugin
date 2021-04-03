import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import liveplugin.PluginUtil.*
import liveplugin.popups.*
import liveplugin.actions.*
import liveplugin.show

registerAction(id = "Show Popup Menu", "ctrl alt shift P") { event ->
    val project = event.project

    createPopupMenu(PopupActionGroup("Popup Menu",
         PopupActionGroup("Open in Browser",
             AnAction("IntelliJ API mini-cheatsheet") {
                 openInBrowser("https://github.com/dkandalov/live-plugin/blob/master/IntellijApiCheatSheet.md")
             },
             AnAction("IntelliJ Platform SDK DevGuide - Fundamentals") {
                 openInBrowser("https://www.jetbrains.org/intellij/sdk/docs/platform/fundamentals.html")
             }
         ),
         AnAction("Execute shell command") {
             val command = showInputDialog("Enter a command (e.g. 'ls'):")
             if (command != null) show(execute(command).toString().replace("\n", "<br/>"))
         },
         AnAction("Show current project") { popupEvent ->
             // Note that "event" from the "Show Popup Menu" action cannot be used here,
             // i.e. doing something like "event.project" is invalid and will fail.
             // This is because AnActionEvent objects are not shareable between swing events.
             // Instead we should use "popupEvent" which is passed for the current action.
             show("project: $project")
             show("project: ${popupEvent.project}")
         },
         HelloAction(),
         Separator.getInstance(),
         AnAction("Edit Popup Menu...") {
             openInEditor("$pluginPath/plugin.kts")
         }
    )).show()
}

class HelloAction: AnAction("Run AnAction") {
    override fun actionPerformed(anActionEvent: AnActionEvent) {
        show("Running AnAction")
    }
}

if (!isIdeStartup) show("Loaded 'Show Popup Menu'<br/>Use ctrl+alt+shift+P to run it")


//
// See next intention and java-intention examples.
//          ^^^^^^^^^     ^^^^^^^^^^^^^^
