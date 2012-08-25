import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.keymap.KeymapManager

import javax.swing.*


static show(String htmlBody, String title = "", notificationType = NotificationType.INFORMATION) {
	def notification = new Notification("", title, htmlBody, notificationType)
	((Notifications) NotificationsManager.notificationsManager).notify(notification)
}

class MyEditorAction extends EditorAction {
	protected MyEditorAction(Closure closure) {
		super(new EditorWriteActionHandler() {
			@Override void executeWriteAction(Editor editor, DataContext dataContext) {
				closure.call(editor)
			}
		})
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
})
