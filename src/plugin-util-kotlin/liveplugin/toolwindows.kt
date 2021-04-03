package liveplugin.toolwindows

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowAnchor.RIGHT
import com.intellij.openapi.wm.ToolWindowManager
import liveplugin.disposable.newDisposable
import liveplugin.disposable.registerParent
import liveplugin.pluginrunner.kotlin.LivePluginScript
import liveplugin.projects.registerProjectOpenListener
import liveplugin.show
import liveplugin.withWriteLockOnEdt
import javax.swing.JComponent

fun LivePluginScript.registerIdeToolWindow(
    toolWindowId: String,
    anchor: ToolWindowAnchor = RIGHT,
    component: JComponent
) {
    registerProjectOpenListener(pluginDisposable) { project ->
        registerProjectToolWindow(toolWindowId, project, pluginDisposable, anchor, component)
    }
}

fun LivePluginScript.registerProjectToolWindow(
    toolWindowId: String,
    anchor: ToolWindowAnchor = RIGHT,
    component: JComponent
): ToolWindow? =
    if (project == null) {
        show("Can't register toolwindow '$toolWindowId' because project is null")
        null
    } else {
        registerProjectToolWindow(toolWindowId, project!!, pluginDisposable, anchor, component)
    }

fun registerProjectToolWindow(
    toolWindowId: String,
    project: Project,
    disposable: Disposable,
    anchor: ToolWindowAnchor = RIGHT,
    component: JComponent
): ToolWindow = withWriteLockOnEdt {
    val toolWindow = ToolWindowManager.getInstance(project)
        .registerToolWindow(RegisterToolWindowTask(toolWindowId, anchor, component))
    newDisposable(whenDisposed = { toolWindow.remove() }).registerParent(disposable, project)
    toolWindow
}