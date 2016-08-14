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
	private static final def aNull = new Object() // because ConcurrentHashMap doesn't allow nulls

	private final String name
	private final boolean isPersisted


	GlobalVar(String name, T value = null) {
		this(name, value, false)
	}

	private GlobalVar(String name, T value = null, boolean isPersisted) {
		this.name = name
		this.isPersisted = isPersisted
		if (value != null) {
			setGlobalVar(name, value, isPersisted)
		}
	}

	GlobalVar<T> parentDisposable(Disposable disposable) {
		if (disposable != null) {
			Disposer.register(disposable, this)
		}
		this
	}

	GlobalVar<T> persisted(boolean isPersisted = true) {
		new GlobalVar<T>(name, null, isPersisted)
	}

	@Nullable T get() {
		getGlobalVar(name, null, isPersisted)
	}

	T set(@Nullable T value) {
		set{ value }
	}

	T set(Closure closure) {
		changeGlobalVar(name, null, isPersisted, closure)
	}

	@Override void dispose() {
		removeGlobalVar(name, isPersisted)
	}

	@Nullable static <T> T getGlobalVar(String varName, @Nullable initialValue = null, boolean isPersisted = false) {
		changeGlobalVar(varName, initialValue, isPersisted, {it})
	}

	@Nullable static <T> T setGlobalVar(String varName, @Nullable varValue, boolean isPersisted = false) {
		changeGlobalVar(varName, null, isPersisted){ varValue }
	}

	@Nullable static <T> T changeGlobalVar(String varName, @Nullable initialValue = null, boolean isPersisted = false, Closure<T> callback) {
		def varValue = unwrapNull(keysByVarName.get(varName))
		if (isPersisted && varValue == null) {
			varValue = Persistence.load(varName, varValue)
		}

		def prevValue = (varValue == null ? initialValue : varValue) // can't use "?:" here because 0 is false in Groovy
		def newValue = callback(prevValue)

		keysByVarName.put(varName, wrapNull(newValue))

		if (isPersisted && prevValue != newValue) {
			Persistence.save(varName, newValue)
		}

		newValue as T
	}

	@Nullable static <T> T removeGlobalVar(String varName, boolean isPersisted = false) {
		def varValue = unwrapNull(keysByVarName.get(varName))

		keysByVarName.remove(varName)
		if (isPersisted) {
			Persistence.remove(varName)
		}

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

	private static def wrapNull(value) {
		value == null ? aNull : value
	}

	private static def unwrapNull(value) {
		value == aNull ? null : value
	}
}
