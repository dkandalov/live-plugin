package liveplugin.implementation

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.Project

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

	static AnActionListener registerActionListener(Disposable parentDisposable, AnActionListener actionListener) {
		ActionManager.instance.addAnActionListener(actionListener, parentDisposable)
		actionListener
	}

	static AnActionListener registerActionListener(String listenerId, AnActionListener actionListener) {
		GlobalVars.changeGlobalVar(listenerId) { oldListener ->
			if (oldListener != null) {
				ActionManager.instance.removeAnActionListener(oldListener)
			}
			ActionManager.instance.addAnActionListener(actionListener)
			actionListener
		}
	}

	static AnActionListener unregisterActionListener(String listenerId) {
		def oldListener = GlobalVars.removeGlobalVar(listenerId) as AnActionListener
		if (oldListener != null) {
			ActionManager.instance.removeAnActionListener(oldListener)
		}
		oldListener
	}

	// there is no "Run" action for each run configuration, so the only way is to do it in code
	static runConfiguration(String configurationName, Project project) {
		def settings = RunManager.getInstance(project).allSettings.find{ it.name.contains(configurationName) }
		ProgramRunnerUtil.executeConfiguration(project, settings, DefaultRunExecutor.runExecutorInstance)
	}

	/**
	 * See http://devnet.jetbrains.com/message/5195728#5195728
	 * https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/openapi/actionSystem/ex/CheckboxAction.java#L60
	 */
	static anActionEvent(DataContext dataContext = DataManager.instance.dataContextFromFocus.resultSync,
	                     Presentation templatePresentation = new Presentation()) {
		def actionManager = ActionManager.instance
		new AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, templatePresentation, actionManager, 0)
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
