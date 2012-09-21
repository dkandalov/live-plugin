import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.keymap.KeymapManager

import javax.swing.*
import java.awt.Color
import java.awt.Font

static show(String htmlBody, String title = "", NotificationType notificationType = NotificationType.INFORMATION) {
    SwingUtilities.invokeLater({
        def notification = new Notification("", title, htmlBody, notificationType)
        ApplicationManager.application.messageBus.syncPublisher(Notifications.TOPIC).notify(notification)
    } as Runnable)
}

class MyEditorAction extends EditorAction {
    public MyEditorAction(Closure closure) {
		super(new MyEditorWriteActionHandler(closure))
	}
}

class MyEditorWriteActionHandler extends EditorWriteActionHandler {
    final Closure closure

    MyEditorWriteActionHandler(Closure closure) {
        this.closure = closure
    }

    @Override void executeWriteAction(Editor editor, DataContext dataContext) {
        closure.call(editor)
    }
}

static registerTextEditorAction(String actionId, String keyStroke = "", Closure closure) {
	def actionManager = ActionManager.instance
	def keymap = KeymapManager.instance.activeKeymap

	def alreadyRegistered = (actionManager.getAction(actionId) != null)
	if (alreadyRegistered) {
		keymap.removeAllActionShortcuts(actionId)
		actionManager.unregisterAction(actionId)
	}

	if (!keyStroke.empty) keymap.addShortcut(actionId, new KeyboardShortcut(KeyStroke.getKeyStroke(keyStroke), null))

	actionManager.registerAction(actionId, new MyEditorAction(closure))

	show("Plugin '${actionId}' reloaded")
}

registerTextEditorAction("HelloTextEditorAction", "ctrl shift alt E", { Editor editor ->
	editor.document.text += "\nHello IntelliJ"

	def from = editor.document.text.length() - 8
	def to = editor.document.text.length()
	def textAttributes = new TextAttributes(Color.BLACK, Color.YELLOW, Color.YELLOW, EffectType.SEARCH_MATCH, Font.PLAIN)
	editor.markupModel.addRangeHighlighter(from, to, 1, textAttributes, HighlighterTargetArea.EXACT_RANGE)
})
