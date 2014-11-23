package liveplugin.implementation

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.compiler.CompilationStatusAdapter
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.project.Project
import liveplugin.PluginUtil
import org.jdesktop.swingx.action.ActionManager

class Compilation {
	static void addCompilationListener(String id, Project project, CompilationStatusAdapter listener) {
		PluginUtil.changeGlobalVar(id){ oldListener ->
			def compilerManager = CompilerManager.getInstance(project)
			if (oldListener != null) {
				compilerManager.removeCompilationStatusListener(oldListener)
			}
			compilerManager.addCompilationStatusListener(listener)
			listener
		}
	}

	static AnAction compileAction() {
		ActionManager.instance.getAction("CompileDirty")
	}
}
