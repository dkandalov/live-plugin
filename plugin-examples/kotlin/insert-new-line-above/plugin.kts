import com.intellij.openapi.actionSystem.AnActionEvent
import liveplugin.actions.registerAction
import liveplugin.currentEditor
import liveplugin.runWriteActionOnEdt
import liveplugin.show

//
// The action below a follow-up for these posts:
//  - http://martinfowler.com/bliki/InternalReprogrammability.html
//  - http://nealford.com/memeagora/2013/01/22/why_everyone_eventually_hates_maven.html
//
// Note that there is build-in "Start New Line Before Current" action (ctrl+alt+enter) which does almost the same thing.
//

registerAction(id = "InsertNewLineAbove", keyStroke = "ctrl shift ENTER", callback = { event: AnActionEvent ->
    val project = event.project!!
    val editor = project.currentEditor!!
    editor.document.runWriteActionOnEdt(project, description = "Insert New Line Above", callback = {
        val offset = editor.caretModel.offset
        val currentLine = editor.caretModel.logicalPosition.line
        val lineStartOffset = editor.document.getLineStartOffset(currentLine)

        editor.document.insertString(lineStartOffset, "\n")
        editor.caretModel.moveToOffset(offset + 1)
    })
})
if (!isIdeStartup) show("Loaded 'InsertNewLineAbove' action<br/>Use 'Ctrl+Shift+Enter' to run it")

//
// See next popup-menu example.
//          ^^^^^^^^^^
