package liveplugin.implementation
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerAdapter
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import liveplugin.CanCallFromAnyThread
import org.jetbrains.annotations.NotNull

import javax.swing.*

import static com.intellij.openapi.wm.ToolWindowAnchor.RIGHT

class ToolWindows {
	// thread-confined to EDT
	private static final Map<ProjectManagerListener, String> pmListenerToToolWindowId = new HashMap()

	static registerToolWindow(String toolWindowId, ToolWindowAnchor location = RIGHT, Closure<JComponent> createComponent) {
		def previousListener = pmListenerToToolWindowId.find{ it.value == toolWindowId }?.key
		if (previousListener != null) {
			ProjectManager.instance.removeProjectManagerListener(previousListener)
			pmListenerToToolWindowId.remove(previousListener)
		}

		def listener = new ProjectManagerAdapter() {
			@Override void projectOpened(Project project) { registerToolWindowIn(project, toolWindowId, createComponent(), location) }
			@Override void projectClosed(Project project) { unregisterToolWindowIn(project, toolWindowId) }
		}
		pmListenerToToolWindowId[listener] = toolWindowId
		ProjectManager.instance.addProjectManagerListener(listener)

		ProjectManager.instance.openProjects.each { project -> registerToolWindowIn(project, toolWindowId, createComponent(), location) }
	}

	@CanCallFromAnyThread
	static unregisterToolWindow(String toolWindowId) {
		def previousListener = pmListenerToToolWindowId.find{ it.value == toolWindowId }?.key
		if (previousListener != null) {
			ProjectManager.instance.removeProjectManagerListener(previousListener)
			pmListenerToToolWindowId.remove(previousListener)
		}

		ProjectManager.instance.openProjects.each { project -> unregisterToolWindowIn(project, toolWindowId) }
	}

	static ToolWindow registerToolWindowIn(@NotNull Project project, String toolWindowId, JComponent component, ToolWindowAnchor location = RIGHT) {
		def manager = ToolWindowManager.getInstance(project)

		if (manager.getToolWindow(toolWindowId) != null) {
			manager.unregisterToolWindow(toolWindowId)
		}

		def toolWindow = manager.registerToolWindow(toolWindowId, false, location)
		def content = ContentFactory.SERVICE.instance.createContent(component, "", false)
		toolWindow.contentManager.addContent(content)
		toolWindow
	}

	static unregisterToolWindowIn(@NotNull Project project, String toolWindowId) {
		ToolWindowManager.getInstance(project).unregisterToolWindow(toolWindowId)
	}

}
