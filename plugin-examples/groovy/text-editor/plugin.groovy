import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

import static liveplugin.PluginUtil.*

// One of the most fundamental things you can do in an Action is to modify text
// in the current editor using `com.intellij.openapi.editor.Document` API.
// See also https://plugins.jetbrains.com/docs/intellij/documents.html.

registerAction("Insert Hello World", "ctrl shift W") { AnActionEvent event ->
	def project = event?.project
	def editor = CommonDataKeys.EDITOR.getData(event.dataContext)
	if (project == null || editor == null) return

	// Document modifications must be done inside "commands" which will support undo/redo functionality.
	runDocumentWriteAction(event.project, editor.document, "Insert Hello World") { document ->
		document.insertString(editor.caretModel.offset, "/* Hello world */")
	}

	// This is related to the topic of threading rules:
	//  - it's ok to read data with a read lock or on event dispatch thread (aka EDT or UI thread)
	//  - it's ok to modify data with a write lock and only on EDT

	// In practice, it's not very complicated because actions run on EDT and can read any data.
	// So the only thing to remember is to use commands for modifications.

	// See also https://plugins.jetbrains.com/docs/intellij/general-threading-rules.html
	// and javadoc for the Application class https://upsource.jetbrains.com/idea-ce/file/idea-ce-73bd72cb4bb9b64d0b1f44c3f6f22246e2850921/platform/core-api/src/com/intellij/openapi/application/Application.java
	// Note that IntelliJ documentation is talking about read/write "actions" which is
	// an overloaded term and is not directly related to AnAction class.
}
if (!isIdeStartup) show("Loaded 'Insert Hello World' action<br/>Use 'ctrl+shift+W' to run it")

//
// See next popup-menu example.
//          ^^^^^^^^^^
