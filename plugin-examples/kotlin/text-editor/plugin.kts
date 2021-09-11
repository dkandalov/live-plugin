import com.intellij.openapi.actionSystem.AnActionEvent
import liveplugin.*

// One of the most fundamental things you can do in an Action is to modify text
// in the current editor using `com.intellij.openapi.editor.Document` API.
// See also https://plugins.jetbrains.com/docs/intellij/documents.html.

registerAction(id = "Insert Hello World", keyStroke = "ctrl shift W") { event: AnActionEvent ->
    val project = event.project ?: return@registerAction // Can be null if there are no open projects.
    val editor = event.editor ?: return@registerAction // Can be null if focus is not in the editor or no editors are open.

    // Document modifications must be done inside "commands" which will support undo/redo functionality.
    editor.document.executeCommand(project, description = "Insert Hello World") {
        insertString(editor.caretModel.offset, "/* Hello world */")
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
if (!isIdeStartup) show("Loaded 'Insert Hello World' action<br/>Use 'Ctrl+Shift+W' to run it")

// See next popup-menu example.
//          ^^^^^^^^^^
