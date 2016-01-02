package liveplugin.implementation
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import org.jetbrains.annotations.NotNull

import javax.swing.*

import static com.intellij.openapi.wm.ToolWindowAnchor.RIGHT
import static liveplugin.implementation.Misc.*

class ToolWindows {

	static registerToolWindow(String toolWindowId, Disposable disposable = null, ToolWindowAnchor location = RIGHT,
	                          Closure<JComponent> createComponent) {
		def disposableId = registerDisposable(toolWindowId)
		disposable = (disposable == null ? disposableId : newDisposable([disposable, disposableId]))

		Projects.registerProjectListener(disposable) { Project project ->
			registerToolWindowIn(project, toolWindowId, newDisposable([project, disposable]), createComponent(), location)
		}
	}
	static registerToolWindow(Project project, String toolWindowId, Disposable disposable = null, ToolWindowAnchor location = RIGHT,
	                          Closure<JComponent> createComponent) {
		def disposableId = registerDisposable(toolWindowId)
		disposable = (disposable == null ? disposableId : newDisposable([disposable, disposableId]))

		registerToolWindowIn(project, toolWindowId, newDisposable([project, disposable]), createComponent(), location)
	}

	static unregisterToolWindow(String toolWindowId) {
		unregisterDisposable(toolWindowId)
	}

	private static ToolWindow registerToolWindowIn(@NotNull Project project, String toolWindowId, Disposable disposable,
	                                       JComponent component, ToolWindowAnchor location = RIGHT) {
		newDisposable(disposable) {
			ToolWindowManager.getInstance(project).unregisterToolWindow(toolWindowId)
		}

		def manager = ToolWindowManager.getInstance(project)
		if (manager.getToolWindow(toolWindowId) != null) {
			manager.unregisterToolWindow(toolWindowId)
		}

		def toolWindow = manager.registerToolWindow(toolWindowId, false, location)
		def content = ContentFactory.SERVICE.instance.createContent(component, "", false)
		toolWindow.contentManager.addContent(content)
		toolWindow
	}
}
