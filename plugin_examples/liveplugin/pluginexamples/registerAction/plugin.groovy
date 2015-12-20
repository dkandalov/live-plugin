import com.intellij.openapi.actionSystem.AnActionEvent

import static liveplugin.PluginUtil.*

registerAction("HelloWorldAction", "alt shift H", TOOLS_MENU) { AnActionEvent event ->
	show("Hello IntelliJ from action")
	show("Current project name: " + event.project.name)
}

if (!isIdeStartup) show("Loaded 'HelloWorldAction'<br/>Use alt+shift+H to run it")
