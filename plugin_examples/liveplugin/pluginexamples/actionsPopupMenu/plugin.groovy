import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator

import static liveplugin.PluginUtil.*

registerAction("helloPopupAction", "ctrl alt shift P"){ AnActionEvent event ->
	def popupMenuDescription = [
			"Hello World"    : [
					"hello, hello"   : { show("hello, hello") },
					"hello, how low?": { show("hello, how low?") },
			],
			"Open in browser": [
					"Live plugin github": {
						openInBrowser("https://github.com/dkandalov/live-plugin")
					},
					"IntelliJ Architectural Overview": {
						openInBrowser("http://confluence.jetbrains.com/display/IDEADEV/IntelliJ+IDEA+Architectural+Overview")
					},
			],
			"Execute command": {
				def command = showInputDialog("Enter a command:")
				if (command == null) return
				show(execute(command))
			},
			"": Separator.instance,
			"Edit Popup Menu...": {
				openInEditor(pluginPath + "/plugin.groovy")
			}
	]
	def popupTitle = "Say hello to..."
	showPopupMenu(popupMenuDescription, popupTitle)
}
show("Loaded 'helloPopupAction'<br/>Use ctrl+alt+shift+P to run it")
