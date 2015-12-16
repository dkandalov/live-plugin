package liveplugin.implementation

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.unscramble.UnscrambleDialog
import liveplugin.PluginUtil
import org.jetbrains.annotations.Nullable

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
		def disposable = new Disposable() {
			@Override void dispose() {
				closure()
			}
		}
		parents.each { parent ->
			Disposer.register(parent, disposable)
		}
		disposable
	}
}
