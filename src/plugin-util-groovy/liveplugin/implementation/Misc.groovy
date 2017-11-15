package liveplugin.implementation
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import liveplugin.IdeUtil
import liveplugin.PluginUtil
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicBoolean

class Misc {

	static String asString(@Nullable message) {
		if (message?.getClass()?.isArray()) Arrays.toString(message)
		else if (message instanceof MapWithDefault) "{" + message.entrySet().join(", ") + "}"
		else if (message instanceof Throwable) {
			IdeUtil.unscrambleThrowable(message)
		} else {
			String.valueOf(message)
		}
	}

	@Nullable static <T> T accessField(Object o, Collection<String> possibleFieldNames, Class<T> fieldClass = null) {
		for (String fieldName : possibleFieldNames) {
			try {
				def result = accessField(o, fieldName, fieldClass)
				if (result != null) return result as T
			} catch (Exception ignored) {
			}
		}
		def className = fieldClass == null ? "" : " (with class ${fieldClass.canonicalName})"
		throw new IllegalStateException("Didn't find any of the fields ${possibleFieldNames.join(",")} ${className} in object ${o}")
	}

	@Nullable static <T> T accessField(Object o, String fieldName, Class<T> fieldClass = null) {
		Class aClass = o.class
		List<Class> allClasses = []
		while (aClass != Object) {
			allClasses.add(aClass)
			aClass = aClass.superclass
		}
		def allFields = allClasses*.declaredFields.toList().flatten()

		for (field in allFields) {
			if (field.name == fieldName && (fieldClass == null || fieldClass.isAssignableFrom(field.type))) {
				field.setAccessible(true)
				return field.get(o) as T
			}
		}
		def className = fieldClass == null ? "" : " (with class ${fieldClass.canonicalName})"
		throw new IllegalStateException("Didn't find field '${fieldName}'${className} in object ${o}")
	}

	@Deprecated static accessField(Object o, String fieldName, Closure callback) {
		catchingAll {
			Class aClass = o.class
			List<Class> allClasses = []
			while (aClass != Object) {
				allClasses.add(aClass)
				aClass = aClass.superclass
			}
			def allFields = allClasses*.declaredFields.toList().flatten()

			for (field in allFields) {
				if (field.name == fieldName) {
					field.setAccessible(true)
					callback(field.get(o))
					return
				}
			}
		}
	}

	@Nullable static <T> T catchingAll(Closure<T> closure) {
		try {

			closure.call()

		} catch (Exception e) {
			ProjectManager.instance.openProjects.each { Project project ->
				PluginUtil.showInConsole(e, e.class.simpleName, project)
			}
			null
		}
	}

	static Disposable newDisposable(Disposable parent, Closure closure = {}) {
		newDisposable([parent], closure)
	}

	static Disposable newDisposable(Collection<Disposable> parents, Closure closure = {}) {
		def isDisposed = new AtomicBoolean(false)
		def disposable = new Disposable() {
			@Override void dispose() {
				if (!isDisposed.get()) {
					isDisposed.set(true)
					closure()
				}
			}
		}
		parents.each { parent ->
			// can't use here "Disposer.register(parent, disposable)"
			// because Disposer only allows one parent to one child registration of Disposable objects
			Disposer.register(parent, new Disposable() {
				@Override void dispose() {
					Disposer.dispose(disposable)
				}
			})
		}
		disposable
	}

	// TODO consider using com.intellij.openapi.util.Disposer.ourKeyDisposables
	static Disposable registerDisposable(String id) {
		def disposable = new GlobalVar<Disposable>(id).set { Disposable oldDisposable ->
			if (oldDisposable != null) Disposer.dispose(oldDisposable)
			def newDisposable = new Disposable() {
				@Override void dispose() {}
				@Override String toString() {
					"LivePlugin disposable for id='${id}'"
				}
			}
			// Use application as parent so that disposable is disposed on application close.
			// Otherwise, IJ logs an error even though this "memory leak" is not important.
			Disposer.register(ApplicationManager.application, newDisposable)
			newDisposable
		}
		disposable
	}

	static void unregisterDisposable(String id) {
		def disposable = GlobalVar.removeGlobalVar(id) as Disposable
		if (disposable != null) {
			Disposer.dispose(disposable)
		}
	}

	static scheduleTask(Alarm alarm, long taskFrequencyMillis, Closure closure) {
		alarm.addRequest({
			closure()
			scheduleTask(alarm, taskFrequencyMillis, closure)
		} as Runnable, taskFrequencyMillis)
	}

	@NotNull static ScheduledExecutorService scheduler() {
		JobScheduler.scheduler
	}

}
