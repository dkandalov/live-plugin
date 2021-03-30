package liveplugin.projects

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener

fun registerProjectOpenListener(disposable: Disposable, onProjectOpen: (Project) -> Unit) {
    registerProjectListener(disposable, object: ProjectManagerListener {
        override fun projectOpened(project: Project) {
            onProjectOpen(project)
        }
    })
}

fun registerProjectListener(disposable: Disposable, listener: ProjectManagerListener) {
    ApplicationManager.getApplication()
        .messageBus.connect(disposable)
        .subscribe(ProjectManager.TOPIC, listener)

    ProjectManager.getInstance().openProjects.forEach { project ->
        listener.projectOpened(project)
    }
}
