package liveplugin.implementation
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.compiler.CompilationStatusListener
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.project.Project

import static liveplugin.implementation.Misc.newDisposable

class Compilation {
	static void registerCompilationListener(Disposable disposable, CompilationStatusListener listener) {
		Projects.registerProjectListener(disposable) { Project project ->
			registerCompilationListener(newDisposable([project, disposable]), project, listener)
		}
	}

	static void registerCompilationListener(Disposable disposable, Project project, CompilationStatusListener listener) {
		def compilerManager = CompilerManager.getInstance(project)
		compilerManager.addCompilationStatusListener(listener)

		newDisposable(disposable) {
			compilerManager.removeCompilationStatusListener(listener)
		}
	}

	static AnAction compileAction() {
		ActionManager.instance.getAction("CompileDirty")
	}

	static AnAction rebuildProjectAction() {
		ActionManager.instance.getAction("CompileProject")
	}
}
