package liveplugin.implementation

import com.intellij.execution.ExecutionException
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.IdeFocusManager
import liveplugin.implementation.common.FilePath
import liveplugin.implementation.common.IdeUtil
import liveplugin.PluginUtil
import liveplugin.implementation.pluginrunner.UnloadPluginAction
import liveplugin.pluginrunner.RunPluginAction
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import javax.swing.*
import java.util.function.Function

import static liveplugin.implementation.Misc.newDisposable

class Actions {
	private static final Logger log = Logger.getInstance(ActionWrapper.class)

	static AnAction registerAction(String actionId, String keyStroke = "", String actionGroupId = null,
	                               String displayText = actionId, Disposable disposable = null, Function<AnActionEvent, ?> callback) {
		registerAction(actionId, keyStroke, actionGroupId, displayText, disposable, new AnAction() {
			@Override void actionPerformed(AnActionEvent event) { callback.apply(event) }
			@Override boolean isDumbAware() { true }
		})
	}

	static AnAction registerAction(String actionId, String keyStroke = "", String actionGroupId = null,
	                               String displayText = actionId, Disposable disposable = null, AnAction action) {
		def actionManager = ActionManager.instance
		def actionGroup = findActionGroup(actionGroupId)

		def alreadyRegistered = (actionManager.getAction(actionId) != null)
		if (alreadyRegistered) {
			actionGroup?.remove(actionManager.getAction(actionId))
			actionManager.unregisterAction(actionId)
		}

		assignKeyStroke(actionId, keyStroke)
		actionManager.registerAction(actionId, action)
		actionGroup?.add(action)
		action.templatePresentation.setText(displayText, true)

		if (disposable != null) {
			newDisposable(disposable) { unregisterAction(actionId) }
		}

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

	static AnActionListener registerActionListener(Disposable disposable, AnActionListener actionListener) {
        ApplicationManager.getApplication()
                .getMessageBus().connect(disposable)
                .subscribe(AnActionListener.TOPIC, actionListener)
		actionListener
	}

	static executeRunConfiguration(@NotNull String configurationName, @NotNull Project project) {
		// there are no "Run" actions corresponding to "Run configurations", so the only way seems to be the API below
		try {

			def settings = RunManager.getInstance(project).allSettings.find{ it.name.contains(configurationName) }
			if (settings == null) {
				return PluginUtil.show("There is no run configuration: <b>${configurationName}</b>.<br/>" +
						"Please create one or change source code to use some other configuration.")
			}
			def builder = ExecutionEnvironmentBuilder.create(DefaultRunExecutor.runExecutorInstance, settings)
			def environment = builder.contentToReuse(null).dataContext(null).activeTarget().build()

			// Execute runner directly instead of using ProgramRunnerUtil.executeConfiguration()
			// because it doesn't allow running multiple instances of the same configuration
			environment.assignNewExecutionId()
			environment.runner.execute(environment)

		} catch (ExecutionException e) {
			return PluginUtil.show(e)
		}
	}

	static runLivePlugin(@NotNull String pluginId, @NotNull Project project) {
		RunPluginAction.runPlugins([new FilePath(pluginId)], dummyEvent(project))
	}

	static unloadLivePlugin(@NotNull String pluginId) {
		UnloadPluginAction.unloadPlugins([new FilePath(pluginId)])
	}

	static testLivePlugin(@NotNull String pluginId, @NotNull Project project) {
		RunPluginAction.runPluginsTests([new FilePath(pluginId)], dummyEvent(project))
	}

	private static AnActionEvent dummyEvent(Project project) {
		def dataContext = new MapDataContext().put(CommonDataKeys.PROJECT.name, project)
		new AnActionEvent(null, dataContext, "", new Presentation(), ActionManager.instance, 0)
	}

	/**
	 * See http://devnet.jetbrains.com/message/5195728#5195728
	 * https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/openapi/actionSystem/ex/CheckboxAction.java#L60
	 */
	static AnActionEvent anActionEvent(DataContext dataContext = dataContextFromFocus(),
	                     Presentation templatePresentation = new Presentation()) {
		def actionManager = ActionManager.instance
		new AnActionEvent(null, dataContext, IdeUtil.livePluginActionPlace, templatePresentation, actionManager, 0)
	}

	static DataContext dataContextFromFocus() {
		DataManager.instance.getDataContext(IdeFocusManager.globalInstance.focusOwner)
	}

	private static DefaultActionGroup findActionGroup(String actionGroupId) {
		if (actionGroupId != null && actionGroupId) {
			def action = ActionManager.instance.getAction(actionGroupId)
			action instanceof DefaultActionGroup ? action : null
		} else {
			null
		}
	}

	static void assignKeyStroke(String actionId, String keyStroke, String macKeyStroke = keyStroke) {
		def keymap = KeymapManager.instance.activeKeymap
		if (!SystemInfo.isMac) {
			def shortcut = asKeyboardShortcut(keyStroke)
			if (shortcut == null) return
			keymap.removeAllActionShortcuts(actionId)
			keymap.addShortcut(actionId, shortcut)
		} else {
			def shortcut = asKeyboardShortcut(macKeyStroke)
			if (shortcut == null) return
			keymap.removeAllActionShortcuts(actionId)
			keymap.addShortcut(actionId, shortcut)
		}
	}

	@Nullable static KeyboardShortcut asKeyboardShortcut(String keyStroke) {
		if (keyStroke.trim().empty) return null

		def firstKeystroke
		def secondKeystroke = null
		if (keyStroke.contains(",")) {
			firstKeystroke = KeyStroke.getKeyStroke(keyStroke[0..<keyStroke.indexOf(",")].trim())
			secondKeystroke = KeyStroke.getKeyStroke(keyStroke[(keyStroke.indexOf(",") + 1)..-1].trim())
		} else {
			firstKeystroke = KeyStroke.getKeyStroke(keyStroke)
		}
		if (firstKeystroke == null) throw new IllegalStateException("Invalid keystroke '$keyStroke'")
		new KeyboardShortcut(firstKeystroke, secondKeystroke)
	}
}
