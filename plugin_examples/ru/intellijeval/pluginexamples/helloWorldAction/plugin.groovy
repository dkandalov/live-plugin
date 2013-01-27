import static ru.intellijeval.PluginUtil.*

registerAction("HelloWorldAction", "alt shift H") {
	show("Hello IntelliJ from action")
}

show("Loaded 'HelloWorldAction'<br/>Use alt+shift+H to run it")
