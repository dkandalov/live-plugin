package liveplugin.implementation
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerAdapter
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import static liveplugin.implementation.GlobalVars.changeGlobalVar
import static liveplugin.implementation.Misc.newDisposable

class Projects {
	static def registerProjectListener(Disposable disposable, Closure closure) {
		registerProjectListener(disposable, new ProjectManagerAdapter() {
			@Override void projectOpened(Project project) {
				closure(project)
			}
		})
		ProjectManager.instance.openProjects.each { project ->
			closure(project)
		}
	}

	static def registerProjectListener(Disposable disposable, ProjectManagerListener listener) {
		ProjectManager.instance.addProjectManagerListener(listener, disposable)
	}

	static def registerProjectListener(String listenerId, ProjectManagerListener listener) {
		Disposable disposable = (Disposable) changeGlobalVar(listenerId) { Disposable previousDisposable ->
			if (previousDisposable != null) Disposer.dispose(previousDisposable)
			newDisposable([])
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
