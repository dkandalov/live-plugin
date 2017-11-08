import com.intellij.openapi.actionSystem.AnActionEvent
import liveplugin.currentEditor
import liveplugin.registerAction
import liveplugin.runWriteAction
import liveplugin.show

registerAction(id = "InsertNewLineAbove", keyStroke = "alt shift ENTER", callback = { event: AnActionEvent ->
    val project = event.project!!
    val editor = project.currentEditor!!
    editor.document.runWriteAction(project, description = "Insert New Line Above", callback = {
        val offset = editor.caretModel.offset
        val currentLine = editor.caretModel.logicalPosition.line
        val lineStartOffset = editor.document.getLineStartOffset(currentLine)

        editor.document.insertString(lineStartOffset, "\n")
        editor.caretModel.moveToOffset(offset + 1)
    })
})

if (!isIdeStartup) show("Loaded 'InsertNewLineAbove' action<br/>Use 'Alt+Shift+Enter' to run it")