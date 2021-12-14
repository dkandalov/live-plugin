package liveplugin.implementation

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import org.jetbrains.annotations.Nullable

import javax.swing.*

import static com.intellij.openapi.wm.ToolWindowAnchor.RIGHT
import static liveplugin.PluginUtil.unregisterToolWindow
import static liveplugin.implementation.Misc.*

class ToolWindows {

	static registerToolWindow(String toolWindowId, Disposable disposable = null, ToolWindowAnchor location = RIGHT,
	                          ActionGroup toolbarActionGroup = null, Closure<JComponent> createComponent) {
		def disposableId = registerDisposable(toolWindowId)
		disposable = (disposable == null ? disposableId : newDisposable([disposable, disposableId]))

		Projects.registerProjectListener(disposable) { Project project ->
			registerToolWindowIn(project, toolWindowId, newDisposable([project, disposable]), location, toolbarActionGroup, createComponent)
		}
	}

	static ToolWindow registerToolWindow(Project project, String toolWindowId, Disposable disposable = null, ToolWindowAnchor location = RIGHT,
	                          ActionGroup toolbarActionGroup = null, Closure<JComponent> createComponent) {
		def disposableId = registerDisposable(toolWindowId)
		disposable = (disposable == null ? disposableId : newDisposable([disposable, disposableId]))

		registerToolWindowIn(project, toolWindowId, newDisposable([project, disposable]), location, toolbarActionGroup, createComponent)
	}

	static unregisterToolWindow(String toolWindowId) {
		unregisterDisposable(toolWindowId)
		// manually remove tool windows from open projects in case they were registered without disposable
		ProjectManager.instance.openProjects.each { Project project ->
			def toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)
			if (toolWindowId != null) toolWindow.remove()
		}
	}

	static unregisterToolWindow(String toolWindowId, Project project) {
		unregisterDisposable(toolWindowId)
		def toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)
		if (toolWindow != null) toolWindow.remove()
	}

	static Collection<ToolWindow> findToolWindows(String toolWindowId) {
		ProjectManager.instance.openProjects.collect {
			findToolWindow(toolWindowId, it)
		}.findAll {it != null}
	}

	@Nullable static ToolWindow findToolWindow(String toolWindowId, Project project) {
		ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)
	}

	static DefaultActionGroup createCloseButtonActionGroup(String toolWindowId) {
		new DefaultActionGroup().with {
			add(new AnAction(AllIcons.Actions.Cancel) {
				@Override void actionPerformed(AnActionEvent event) {
					unregisterToolWindow(toolWindowId)
				}
			})
			it
		}
	}

	private static ToolWindow registerToolWindowIn(Project project, String toolWindowId, Disposable disposable,
	                                               ToolWindowAnchor location = RIGHT, ActionGroup toolbarActionGroup = null,
	                                               Closure<JComponent> createComponent) {
		newDisposable(disposable) {
			def toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)
			if (toolWindow != null) toolWindow.remove()
		}

		def manager = ToolWindowManager.getInstance(project)
		if (manager.getToolWindow(toolWindowId) != null) {
			manager.getToolWindow(toolWindowId).remove()
		}

		def component
		if (toolbarActionGroup == null) {
			component = createComponent()
		} else {
			component = new SimpleToolWindowPanel(true)
			component.content = createComponent()
			component.toolbar = ActionManager.instance.createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, toolbarActionGroup, true).component
		}

		def toolWindow = manager.registerToolWindow(RegisterToolWindowTask.notClosable(toolWindowId, location))
		def content = ContentFactory.SERVICE.instance.createContent(component, "", false)
		toolWindow.contentManager.addContent(content)
		toolWindow
	}
}
