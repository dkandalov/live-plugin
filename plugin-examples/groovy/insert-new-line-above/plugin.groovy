import com.intellij.openapi.actionSystem.AnActionEvent

import static liveplugin.PluginUtil.*

//
// The action below a follow-up for these posts:
//  - http://martinfowler.com/bliki/InternalReprogrammability.html
//  - http://nealford.com/memeagora/2013/01/22/why_everyone_eventually_hates_maven.html
//
// Note that there is build-in "Start New Line Before Current" action (ctrl+alt+enter) which does almost the same thing.
//

registerAction("InsertNewLineAbove", "ctrl shift ENTER") { AnActionEvent event ->
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
if (!isIdeStartup) show("Loaded 'InsertNewLineAbove' action<br/>Use 'Ctrl+Shift+Enter' to run it")

//
// See next popup-menu example.
//          ^^^^^^^^^^
