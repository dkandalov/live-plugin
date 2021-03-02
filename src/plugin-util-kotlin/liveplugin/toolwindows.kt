package liveplugin.toolwindows

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionGroup.EMPTY_GROUP
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowAnchor.RIGHT
import liveplugin.implementation.ToolWindows
import liveplugin.pluginrunner.kotlin.LivePluginScript
import liveplugin.runWriteActionOnEdt
import javax.swing.JComponent

fun LivePluginScript.registerToolWindow(
    toolWindowId: String,
    anchor: ToolWindowAnchor = RIGHT,
    actionGroup: ActionGroup = EMPTY_GROUP,
    createComponent: () -> JComponent
) = registerToolWindow(toolWindowId, pluginDisposable, anchor, actionGroup, createComponent)

fun registerToolWindow(
    toolWindowId: String,
    disposable: Disposable,
    anchor: ToolWindowAnchor = RIGHT,
    actionGroup: ActionGroup = EMPTY_GROUP,
    createComponent: () -> JComponent
): Unit = runWriteActionOnEdt {
    ToolWindows.registerToolWindow(toolWindowId, disposable, anchor, actionGroup) { createComponent() }
}