package liveplugin.implementation

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.wm.IdeFocusManager
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

class Projects {
	static registerProjectListener(Disposable disposable, Closure onEachProject) {
		registerProjectListener(disposable, new ProjectManagerListener() {
			@Override void projectOpened(Project project) {
				onEachProject(project)
			}
		})
		ProjectManager.instance.openProjects.each { project ->
			onEachProject(project)
		}
	}

	static registerProjectListener(Disposable disposable, ProjectManagerListener listener) {
		def connection = ApplicationManager.application.messageBus.connect(disposable)
		connection.subscribe(ProjectManager.TOPIC, listener)
	}

	@Nullable static Project openProject(@NotNull String projectPath) {
		def projectManager = ProjectManager.instance
		def project = projectManager.openProjects.find { it.basePath == projectPath }
		if (project != null) project
		else projectManager.loadAndOpenProject(projectPath)
	}

	@Nullable static Project currentProjectInFrame() {
		IdeFocusManager.findInstance().lastFocusedFrame?.project
	}
}
