import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator

import static liveplugin.PluginUtil.*

registerAction("Show Actions Popup", "ctrl alt shift P") { AnActionEvent event ->
	def project = event.project
	def popupMenuDescription = [
		"Execute Shell Command": {
			def command = showInputDialog("Enter a command (e.g. 'ls'):")
			if (command != null) show(execute(command))
		},
		"Show Current Project": { context ->
			// Note that "event" from the outer "Show Actions Popup" action cannot be used here
			// because AnActionEvent objects are not shareable between swing events.
			// show("project: ${event.project}") // Will throw "cannot share data context between Swing events".
			show("project: ${project}") // But it's ok to use "project" from the previous event.

			// You can get AnActionEvent of the chosen menu item action as shown below
			// (to make event aware of the current project showPopupMenu() is invoked with "event.dataContext" argument).
			show("project: ${context.event.project}")
		},
		"-": new AnAction("Hello Action") {
			@Override void actionPerformed(AnActionEvent anActionEvent) {
				show("Hello!")
			}
		},
		"--": Separator.instance,
		"Edit Popup...": {
			openInEditor(pluginPath + "/plugin.groovy")
		},
		"Documentation": [
			"IntelliJ API Mini-Cheatsheet": {
				openInBrowser("https://github.com/dkandalov/live-plugin/blob/master/IntellijApiCheatSheet.md")
			},
			"IntelliJ Platform SDK DevGuide - Fundamentals": {
				openInBrowser("https://plugins.jetbrains.com/docs/intellij/fundamentals.html")
			}
		],
	]
	def popupTitle = "Some Actions"
	showPopupMenu(popupMenuDescription, popupTitle, event.dataContext)
}

if (!isIdeStartup) show("Loaded 'Show Actions Popup'<br/>Use ctrl+alt+shift+P to run it")
