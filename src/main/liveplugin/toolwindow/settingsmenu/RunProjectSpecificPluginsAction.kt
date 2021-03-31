package liveplugin.toolwindow.settingsmenu

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import liveplugin.Settings

class RunProjectSpecificPluginsAction: ToggleAction(
    "Run Project Specific Plugins",
    "Run plugins in the '.live-plugins' directory of the project when it's open.",
    null
), DumbAware {
    override fun isSelected(event: AnActionEvent) =
        Settings.instance.runProjectSpecificPlugins

    override fun setSelected(event: AnActionEvent, state: Boolean) {
        Settings.instance.runProjectSpecificPlugins = state
    }
}
