package liveplugin.implementation

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.Nullable

import java.util.concurrent.ConcurrentHashMap

class GlobalVar<T> implements Disposable {
	private static final def Key<ConcurrentHashMap<String, Object>> globalVarsKey = Key.create("LivePlugin-GlobalVarsKey")
	private static final def keysByVarName = initKeysByVarName()
	private final String id

	GlobalVar(String id, T value = null, Disposable disposable = null) {
		this.id = id
		if (value != null) {
			setGlobalVar(id, value)
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

	@Nullable static <T> T getGlobalVar(String varName, @Nullable initialValue = null) {
		changeGlobalVar(varName, initialValue, {it})
	}

	@Nullable static <T> T setGlobalVar(String varName, @Nullable varValue) {
		changeGlobalVar(varName){ varValue }
	}

	@Nullable static <T> T changeGlobalVar(String varName, @Nullable initialValue = null, Closure<T> callback) {
		def varValue = keysByVarName[varName]
		def prevValue = varValue ?: initialValue
		def newValue = callback(prevValue)

		keysByVarName.put(varName, newValue)

		newValue as T
	}

	@Nullable static <T> T removeGlobalVar(String varName) {
		def varValue = keysByVarName[varName]

		keysByVarName.remove(varName)

		varValue as T
	}

	private static ConcurrentHashMap<String, Object> initKeysByVarName() {
		def application = ApplicationManager.application
		def keysByVarName = application.getUserData(globalVarsKey)
		if (keysByVarName == null) {
			keysByVarName = new ConcurrentHashMap<String, Object>()
			application.putUserData(globalVarsKey, keysByVarName)
		}
		keysByVarName
	}
}
