package liveplugin.implementation

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.unscramble.UnscrambleDialog
import liveplugin.PluginUtil
import org.jetbrains.annotations.Nullable

import java.util.concurrent.atomic.AtomicBoolean

class Misc {

	static String asString(@Nullable message) {
		if (message?.getClass()?.isArray()) Arrays.toString(message)
		else if (message instanceof MapWithDefault) "{" + message.entrySet().join(", ") + "}"
		else if (message instanceof Throwable) {
			def writer = new StringWriter()
			message.printStackTrace(new PrintWriter(writer))
			UnscrambleDialog.normalizeText(writer.buffer.toString())
		} else {
			String.valueOf(message)
		}
	}

	static accessField(Object o, String fieldName, Closure callback) {
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
		def disposable = GlobalVars.changeGlobalVar(id) { Disposable oldDisposable ->
			if (oldDisposable != null) Disposer.dispose(oldDisposable)
			Disposer.newDisposable()
		}
		disposable
	}

	static void unregisterDisposable(String id) {
		def disposable = GlobalVars.removeGlobalVar(id) as Disposable
		if (disposable != null) {
			Disposer.dispose(disposable)
		}
	}
}
