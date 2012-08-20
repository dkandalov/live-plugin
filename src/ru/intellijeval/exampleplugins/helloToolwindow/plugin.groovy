import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

import javax.swing.*


static ToolWindow registerToolWindow(String id, JComponent component, Project project) {
	def manager = ToolWindowManager.getInstance(project)

	if (manager.getToolWindow(id) != null) {
		manager.unregisterToolWindow(id)
	}

	def toolWindow = manager.registerToolWindow(id, false, ToolWindowAnchor.RIGHT)
	def content = ContentFactory.SERVICE.instance.createContent(component, "", false)
	toolWindow.contentManager.addContent(content)
	toolWindow
}

static panelWithButton(JPanel panel = new JPanel()) {
	def button = new JButton("Hello")
	button.addActionListener({ panelWithButton(panel) } as AbstractAction)
	panel.add(button)
	panel.revalidate()
	panel
}

registerToolWindow("helloToolWindow", panelWithButton(), event.project)
