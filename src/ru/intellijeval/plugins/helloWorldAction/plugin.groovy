import com.intellij.notification.*
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapManager

import javax.swing.KeyStroke


static show(String htmlBody, String title = "", notificationType = NotificationType.INFORMATION) {
	def notification = new Notification("", title, htmlBody, notificationType)
	((Notifications) NotificationsManager.notificationsManager).notify(notification)
}

static registerAction(String actionId, String keyStroke = "", String secondKeyStroke = "", Closure closure) {
	def actionManager = ActionManager.instance
	def keymap = KeymapManager.instance.activeKeymap

	def alreadyRegistered = (actionManager.getAction(actionId) != null)
	if (alreadyRegistered) {
		keymap.removeAllActionShortcuts(actionId)
		actionManager.unregisterAction(actionId)
	}

	if (!keyStroke.empty) keymap.addShortcut(actionId, new KeyboardShortcut(KeyStroke.getKeyStroke(keyStroke), null))
	actionManager.registerAction(actionId, new AnAction() {
		@Override void actionPerformed(AnActionEvent e) {
			closure.call(e)
		}
	})

	show("Plugin '${actionId}' reloaded")
}

registerAction("HelloWorldAction", "ctrl shift alt H") {
	show("Hello IntelliJ from action")
}
