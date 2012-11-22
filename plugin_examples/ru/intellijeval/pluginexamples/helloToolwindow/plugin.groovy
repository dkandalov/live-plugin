import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerAdapter
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

import javax.swing.*

static ToolWindow registerToolWindowIn(Project project, String id, JComponent component) {
	def manager = ToolWindowManager.getInstance(project)

	if (manager.getToolWindow(id) != null) {
		manager.unregisterToolWindow(id)
	}

	def toolWindow = manager.registerToolWindow(id, false, ToolWindowAnchor.RIGHT)
	def content = ContentFactory.SERVICE.instance.createContent(component, "", false)
	toolWindow.contentManager.addContent(content)
	toolWindow
}

static unregisterToolWindowIn(Project project, String id) {
	ToolWindowManager.getInstance(project).unregisterToolWindow(id)
}

static registerToolWindow(String id, JComponent component) {
	ProjectManager.instance.addProjectManagerListener(new ProjectManagerAdapter() {
		@Override void projectOpened(Project project) {
			registerToolWindowIn(project, id, component)
		}

		@Override void projectClosed(Project project) {
			unregisterToolWindowIn(project, id)
		}
	})

	ProjectManager.instance.openProjects.each { project ->
		registerToolWindowIn(project, id, component)
	}
}

static panelWithButton(JPanel panel = new JPanel()) {
	def button = new JButton("Hello")
	button.addActionListener({ panelWithButton(panel) } as AbstractAction)
	panel.add(button)
	panel.revalidate()
	panel
}

registerToolWindow("helloToolWindow", panelWithButton())
