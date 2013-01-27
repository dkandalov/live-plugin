import static ru.intellijeval.PluginUtil.*


// (Note that there is built-in toUpperCase action in Intellij; ctrl+shift+U)

registerAction("MyUpperCaseAction") {
	transformSelectedText(event.project) { String s -> s.toUpperCase() }
}
show("Loaded 'MyUpperCaseAction'<br/>Select text in editor and press ctrl+shift+alt+U to run it")
