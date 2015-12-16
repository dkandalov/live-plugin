package liveplugin.implementation
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import static liveplugin.implementation.GlobalVars.changeGlobalVar

class Projects {
	static def registerProjectListener(Disposable parentDisposable, ProjectManagerListener listener) {
		ProjectManager.instance.addProjectManagerListener(listener, parentDisposable)
	}

	static def registerProjectListener(String listenerId, ProjectManagerListener listener) {
		Disposable disposable = (Disposable) changeGlobalVar(listenerId) { Disposable previousDisposable ->
			if (previousDisposable != null) Disposer.dispose(previousDisposable)
			new Disposable() {
				@Override void dispose() {}
			}
		}
		ProjectManager.instance.addProjectManagerListener(listener, disposable)
	}

	@Nullable static Project openProject(@NotNull String projectPath) {
		def projectManager = ProjectManager.instance
		def project = projectManager.openProjects.find{ it.basePath == projectPath }
		if (project != null) project
		else projectManager.loadAndOpenProject(projectPath)
	}

	@Nullable static Project currentProjectInFrame() {
		IdeFocusManager.findInstance().lastFocusedFrame?.project
	}
}
