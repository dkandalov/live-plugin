package liveplugin.implementation

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.compiler.CompilationStatusAdapter
import com.intellij.openapi.compiler.CompilationStatusListener
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompilerTopics
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

import static liveplugin.PluginUtil.registerCompilationListener

class Compilation {
	static void registerCompilationListener(Disposable disposable, CompilationStatusListener listener) {
		Projects.registerProjectListener(disposable) { Project project ->
			registerCompilationListener(disposable, project, listener)
		}
	}

	static void registerCompilationListener(Disposable disposable, Project project, CompilationStatusListener listener) {
		def connection = project.messageBus.connect(newDisposable([disposable, project]))
		connection.subscribe(CompilerTopics.COMPILATION_STATUS, listener)
	}

	static compile(Project project, Closure onSuccessfulCompilation = {}) {
		def disposable = Disposer.newDisposable()
		registerCompilationListener(disposable, project, new CompilationStatusAdapter() {
			@Override void compilationFinished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
				try {
					if (errors == 0) {
						onSuccessfulCompilation()
					}
				} finally {
					Disposer.dispose(disposable)
				}
			}
		})
		compileAction().actionPerformed(Actions.anActionEvent())
	}

	static AnAction compileAction() {
		ActionManager.instance.getAction("CompileDirty")
	}

	static AnAction rebuildProjectAction() {
		ActionManager.instance.getAction("CompileProject")
	}
}
