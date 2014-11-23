package liveplugin.implementation

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.compiler.CompilationStatusAdapter
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.project.Project

class Compilation {
	static void registerCompilationListener(String id, Project project, CompilationStatusAdapter listener) {
		GlobalVars.changeGlobalVar(id){ oldListener ->
			def compilerManager = CompilerManager.getInstance(project)
			if (oldListener != null) {
				compilerManager.removeCompilationStatusListener(oldListener)
			}
			compilerManager.addCompilationStatusListener(listener)
			listener
		}
	}

	static void unregisterCompilationListener(String id, Project project) {
		def oldListener = GlobalVars.removeGlobalVar(id)
		if (oldListener != null) {
			CompilerManager.getInstance(project).removeCompilationStatusListener(oldListener)
		}
	}

	static AnAction compileAction() {
		ActionManager.instance.getAction("CompileDirty")
	}

	static AnAction rebuildProjectAction() {
		ActionManager.instance.getAction("CompileProject")
	}
}
