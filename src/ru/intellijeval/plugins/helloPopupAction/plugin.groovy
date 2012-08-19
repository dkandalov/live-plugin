import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.ui.popup.JBPopupFactory

import javax.swing.*

static show(String htmlBody, String title = "", notificationType = NotificationType.INFORMATION) {
	def notification = new Notification("", title, htmlBody, notificationType)
	((Notifications) NotificationsManager.notificationsManager).notify(notification)
}

static registerAction(String actionId, String keyStroke = "", Closure closure) {
	def actionManager = ActionManager.instance
	def keymap = KeymapManager.instance.activeKeymap

	def alreadyRegistered = (actionManager.getAction(actionId) != null)
	if (alreadyRegistered) {
		keymap.removeAllActionShortcuts(actionId)
		actionManager.unregisterAction(actionId)
	}

	keymap.addShortcut(actionId, new KeyboardShortcut(KeyStroke.getKeyStroke(keyStroke), null))
	actionManager.registerAction(actionId, new AnAction() {
		@Override void actionPerformed(AnActionEvent e) {
			closure.call(e)
		}
	})

	show("Plugin '${actionId}' reloaded")
}

static createActions(actionGroup, data) {
	data.each { entry ->
		if (entry.value instanceof String) {
			actionGroup.add(new AnAction() {
				@Override void actionPerformed(AnActionEvent e) {
					show(entry.key)
				}
			})
		} else {
			def subActions = createActions(new DefaultActionGroup(entry.key.toString(), true), entry.value)
			actionGroup.add(subActions)
		}
	}
	actionGroup
}


registerAction("helloPopupAction") { AnActionEvent event ->
	def data = [
			"Hello world 1": [
					"Hello sub-world 11" : "sub-world 11",
					"Hello sub-world 12" : "sub-world 12",
			],
			"Hello world 2": [
					"Hello sub-world 21" : "sub-world 21",
					"Hello sub-world 22" : "sub-world 22",
			]
	]
	JBPopupFactory.instance.createActionGroupPopup(
			"Open ssh",
			createActions(new DefaultActionGroup(), data),
			event.dataContext,
			JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
			true
	).showCenteredInCurrentWindow(event.project);
}