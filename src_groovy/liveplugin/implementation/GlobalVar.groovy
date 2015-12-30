package liveplugin.implementation

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.Nullable

class GlobalVar<T> implements Disposable {
	private final String id

	GlobalVar(String id, T defaultValue = null, Disposable disposable = null) {
		this.id = id
		if (get() == null) {
			setGlobalVar(id, defaultValue)
		}
		if (disposable != null) {
			Disposer.register(disposable, this)
		}
	}

	@Nullable T get() {
		getGlobalVar(id)
	}

	void set(@Nullable T value) {
		changeGlobalVar(id){ value }
	}

	void set(Closure closure) {
		changeGlobalVar(id, closure)
	}

	@Override void dispose() {
		removeGlobalVar(id)
	}

	@Nullable static <T> T changeGlobalVar(String varName, @Nullable initialValue = null, Closure<T> callback) {
		def actionManager = ActionManager.instance
		def action = actionManager.getAction(asActionId(varName))

		def prevValue = (action == null ? initialValue : action.value)
		T newValue = (T) callback.call(prevValue)

		// unregister action only after callback has been invoked in case it crashes
		if (action != null) actionManager.unregisterAction(asActionId(varName))

		// anonymous class below will keep reference to outer object but that should be ok
		// because its class is not a part of reloadable plugin
		actionManager.registerAction(asActionId(varName), new AnAction() {
			final def value = newValue
			@Override void actionPerformed(AnActionEvent e) {}
		})

		newValue
	}

	@Nullable static <T> T getGlobalVar(String varName, @Nullable initialValue = null) {
		changeGlobalVar(varName, initialValue, {it})
	}

	@Nullable static <T> T setGlobalVar(String varName, @Nullable varValue) {
		changeGlobalVar(varName){ varValue }
	}

	@Nullable static <T> T removeGlobalVar(String varName) {
		def action = ActionManager.instance.getAction(asActionId(varName))
		if (action == null) return null
		ActionManager.instance.unregisterAction(asActionId(varName))
		action.value
	}

	private static String asActionId(String globalVarKey) {
		"LivePlugin-GlobalVar-" + globalVarKey
	}
}
