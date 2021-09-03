package liveplugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowAnchor.RIGHT
import com.intellij.openapi.wm.ToolWindowManager
import liveplugin.pluginrunner.kotlin.LivePluginScript
import javax.swing.JComponent

fun LivePluginScript.registerIdeToolWindow(
    toolWindowId: String,
    component: JComponent,
    anchor: ToolWindowAnchor = RIGHT
) {
    registerProjectOpenListener(pluginDisposable) { project ->
        registerProjectToolWindow(toolWindowId, component, project, pluginDisposable, anchor)
    }
}

fun LivePluginScript.registerProjectToolWindow(
    toolWindowId: String,
    component: JComponent,
    anchor: ToolWindowAnchor = RIGHT
): ToolWindow? =
    if (project == null) {
        show("Can't register toolwindow '$toolWindowId' because project is null")
        null
    } else {
        registerProjectToolWindow(toolWindowId, component, project!!, pluginDisposable, anchor)
    }

fun registerProjectToolWindow(
    toolWindowId: String,
    component: JComponent,
    project: Project,
    disposable: Disposable,
    anchor: ToolWindowAnchor = RIGHT
): ToolWindow = withWriteLockOnEdt {
    val toolWindow = ToolWindowManager.getInstance(project)
        .registerToolWindow(RegisterToolWindowTask(toolWindowId, anchor, component))
    newDisposable(whenDisposed = { toolWindow.remove() }).registerParent(disposable, project)
    toolWindow
}