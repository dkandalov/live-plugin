import com.intellij.openapi.actionSystem.AnActionEvent

import static liveplugin.PluginUtil.*


// (Note that there is built-in toUpperCase action in Intellij; ctrl+shift+U)

registerAction("MyUpperCaseAction", "ctrl alt shift U") { AnActionEvent event ->
	transformSelectedText(event.project) { String s -> s.toUpperCase() }
}
if (!isIdeStartup) show("Loaded 'MyUpperCaseAction'<br/>Select text in editor and press ctrl+shift+alt+U to run it")
