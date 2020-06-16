import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator

import static liveplugin.PluginUtil.*

registerAction("HelloPopupAction", "ctrl alt shift P"){ AnActionEvent event ->
	def project = event.project
	def popupMenuDescription = [
			"Open in browser": [
					"IntelliJ API mini cheat sheet": {
						openInBrowser("https://github.com/dkandalov/live-plugin/blob/master/IntellijApiCheatSheet.md")
					},
					"IntelliJ Platform SDK DevGuide - Fundamentals": {
						openInBrowser("https://www.jetbrains.org/intellij/sdk/docs/platform/fundamentals.html")
					}
			],
			"Execute shell command": {
				def command = showInputDialog("Enter a command (e.g. 'ls'):")
				if (command == null) return
				show(execute(command))
			},
			"Show current project": { context ->
				// note that "event.project" cannot be used here because AnActionEvents are not shareable between swing events
				// (in this case showing popup menu and choosing menu item are two different swing events)
				show("project: ${project}")
				// you can get AnActionEvent of choosing menu item action as shown below
				// (to make this event aware of current project popup menu is invoke like this "showPopupMenu(..., event.dataContext)"
				show("project: ${context.event.project}")
			},
			"-": new AnAction("Run an action") {
				@Override void actionPerformed(AnActionEvent anActionEvent) {
					show("Running an action")
				}
			},
			"--": Separator.instance,
			"Edit Popup Menu...": {
				openInEditor(pluginPath + "/plugin.groovy")
			}
	]
	def popupTitle = "Hello PopupMenu"
	showPopupMenu(popupMenuDescription, popupTitle, event.dataContext)
}

if (!isIdeStartup) show("Loaded 'helloPopupAction'<br/>Use ctrl+alt+shift+P to run it")
