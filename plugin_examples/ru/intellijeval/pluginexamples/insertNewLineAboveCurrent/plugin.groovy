import com.intellij.openapi.actionSystem.AnActionEvent

import static ru.intellijeval.PluginUtil.*

// This action inserts new line above current line.
// It's a follow-up for the these posts:
//  - http://martinfowler.com/bliki/InternalReprogrammability.html
//  - http://nealford.com/memeagora/2013/01/22/why_everyone_eventually_hates_maven.html

registerAction("InsertNewLineAbove", "ctrl alt shift I") { AnActionEvent event ->
	runDocumentWriteAction(event.project) {
		currentEditorIn(event.project).with {
			def offset = caretModel.offset
			def currentLine = caretModel.logicalPosition.line
			def lineStartOffset = document.getLineStartOffset(currentLine)

			document.insertString(lineStartOffset, "\n")
			caretModel.moveToOffset(offset + 1)
		}
	}
}
show("Loaded 'InsertNewLineAbove' action. Use 'ctrl alt shift I' to run it.")
