import com.intellij.notification.*
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.KeymapManager

import javax.swing.KeyStroke
import javax.swing.SwingUtilities


static show(String htmlBody, String title = "", NotificationType notificationType = NotificationType.INFORMATION) {
	SwingUtilities.invokeLater({
		def notification = new Notification("", title, htmlBody, notificationType)
		ApplicationManager.application.messageBus.syncPublisher(Notifications.TOPIC).notify(notification)
	} as Runnable)
}

static registerAction(String actionId, String keyStroke = "", Closure closure) {
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

	show("Loaded '${actionId}'<br/>Use alt+shift+H to run it")
}

registerAction("HelloWorldAction", "alt shift H", {
	show("Hello IntelliJ from action")
})
