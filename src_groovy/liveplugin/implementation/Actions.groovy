package liveplugin.implementation

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.keymap.KeymapManager

import javax.swing.*

class Actions {
	private static final Logger log = Logger.getInstance(ActionWrapper.class)

	static AnAction registerAction(String actionId, String keyStroke = "",
	                               String actionGroupId = null, String displayText = actionId, Closure callback) {
		registerAction(actionId, keyStroke, actionGroupId, displayText, new AnAction() {
			@Override void actionPerformed(AnActionEvent event) { callback(event) }
		})
	}

	static AnAction registerAction(String actionId, String keyStroke = "",
	                               String actionGroupId = null, String displayText = actionId, AnAction action) {
		def actionManager = ActionManager.instance
		def actionGroup = findActionGroup(actionGroupId)

		def alreadyRegistered = (actionManager.getAction(actionId) != null)
		if (alreadyRegistered) {
			actionGroup?.remove(actionManager.getAction(actionId))
			actionManager.unregisterAction(actionId)
		}

		assignKeyStrokeTo(actionId, keyStroke)
		actionManager.registerAction(actionId, action)
		actionGroup?.add(action)
		action.templatePresentation.setText(displayText, true)

		log.info("Action '${actionId}' registered")

		action
	}

	static unregisterAction(String actionId, String actionGroupId = null) {
		def actionManager = ActionManager.instance
		def actionGroup = findActionGroup(actionGroupId)

		def alreadyRegistered = (actionManager.getAction(actionId) != null)
		if (alreadyRegistered) {
			actionGroup?.remove(actionManager.getAction(actionId))
			actionManager.unregisterAction(actionId)
		}
	}

	private static DefaultActionGroup findActionGroup(String actionGroupId) {
		if (actionGroupId != null && actionGroupId) {
			def action = ActionManager.instance.getAction(actionGroupId)
			action instanceof DefaultActionGroup ? action : null
		} else {
			null
		}
	}

	private static void assignKeyStrokeTo(String actionId, String keyStroke) {
		def keymap = KeymapManager.instance.activeKeymap
		keymap.removeAllActionShortcuts(actionId)
		def shortcut = asKeyboardShortcut(keyStroke)
		if (shortcut != null) {
			keymap.addShortcut(actionId, shortcut)
		}
	}

	static KeyboardShortcut asKeyboardShortcut(String keyStroke) {
		if (keyStroke.trim().empty) return null

		def firstKeystroke
		def secondsKeystroke = null
		if (keyStroke.contains(",")) {
			firstKeystroke = KeyStroke.getKeyStroke(keyStroke[0..<keyStroke.indexOf(",")].trim())
			secondsKeystroke = KeyStroke.getKeyStroke(keyStroke[(keyStroke.indexOf(",") + 1)..-1].trim())
		} else {
			firstKeystroke = KeyStroke.getKeyStroke(keyStroke)
		}
		if (firstKeystroke == null) throw new IllegalStateException("Invalid keystroke '$keyStroke'")
		new KeyboardShortcut(firstKeystroke, secondsKeystroke)
	}
}
