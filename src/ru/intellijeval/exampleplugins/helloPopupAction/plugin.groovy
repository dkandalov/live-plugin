import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.ui.popup.JBPopupFactory

import javax.swing.*


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

	show("Plugin '${actionId}' reloaded")
}

static ActionGroup createActions(data, actionGroup = new DefaultActionGroup()) {
	data.each { entry ->
		if (entry.value instanceof String) {
			actionGroup.add(new AnAction(entry.key as String) {
				@Override void actionPerformed(AnActionEvent e) {
					show(entry.value)
				}
			})
		} else {
			def subActions = createActions(entry.value, new DefaultActionGroup(entry.key.toString(), true))
			actionGroup.add(subActions)
		}
	}
	actionGroup
}


registerAction("helloPopupAction", "ctrl alt shift P") { AnActionEvent event ->
	def actionGroup = createActions([
			"World 1": [
					"sub-world 11" : "Hello sub-world 11!!",
					"sub-world 12" : "hello sub-world 12",
			],
			"World 2": [
					"sub-world 21" : "sub-world 21 hello",
					"sub-world 22" : "sub-world hello 22",
			],
			"World 3" : "Hey world 3!"
	])
	JBPopupFactory.instance.createActionGroupPopup(
			"Say hello to",
			actionGroup,
			event.dataContext,
			JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
			true
	).showCenteredInCurrentWindow(event.project)
}