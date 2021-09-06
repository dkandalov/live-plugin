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
        project.registerToolWindow(toolWindowId, component, pluginDisposable, anchor)
    }
}

fun LivePluginScript.registerProjectToolWindow(
    toolWindowId: String,
    component: JComponent,
    anchor: ToolWindowAnchor = RIGHT
): ToolWindow =
    if (project == null) {
        error("Can't register toolwindow '$toolWindowId' because project is null")
    } else {
        project!!.registerToolWindow(toolWindowId, component, pluginDisposable, anchor)
    }

fun Project.registerToolWindow(
    toolWindowId: String,
    component: JComponent,
    disposable: Disposable,
    anchor: ToolWindowAnchor = RIGHT
): ToolWindow = runOnEdtWithWriteLock {
    val toolWindow = ToolWindowManager.getInstance(this)
        .registerToolWindow(RegisterToolWindowTask(toolWindowId, anchor, component))
    newDisposable(whenDisposed = { toolWindow.remove() }).registerParent(disposable, this)
    toolWindow
}