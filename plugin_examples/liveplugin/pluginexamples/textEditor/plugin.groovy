import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes

import java.awt.*

import static liveplugin.PluginUtil.*


registerAction("HelloTextEditorAction", "ctrl shift alt E") { AnActionEvent event ->
	runDocumentWriteAction(event.project) {
		def editor = currentEditorIn(event.project)

		editor.document.text += "\nHello IntelliJ"

		def from = editor.document.text.length() - 8
		def to = editor.document.text.length()
		def textAttributes = new TextAttributes(Color.BLACK, Color.YELLOW, Color.YELLOW, EffectType.SEARCH_MATCH, Font.PLAIN)
		editor.markupModel.addRangeHighlighter(from, to, 1, textAttributes, HighlighterTargetArea.EXACT_RANGE)
	}
}

if (!isIdeStartup) show("Loaded 'HelloTextEditorAction'<br/>Use ctrl+alt+shift+E to run it")
