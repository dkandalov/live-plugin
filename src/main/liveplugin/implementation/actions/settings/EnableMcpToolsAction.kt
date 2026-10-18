package liveplugin.implementation.actions.settings

import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import liveplugin.implementation.Settings

class EnableMcpToolsAction : ToggleAction("Expose LivePlugin Tools via MCP", null, null), DumbAware {
    override fun isSelected(event: AnActionEvent) =
        Settings.instance.mcpToolsEnabled

    override fun setSelected(event: AnActionEvent, state: Boolean) {
        Settings.instance.mcpToolsEnabled = state
    }

    override fun getActionUpdateThread() = BGT
}
