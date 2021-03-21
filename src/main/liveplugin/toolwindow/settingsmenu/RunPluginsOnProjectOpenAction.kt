package liveplugin.toolwindow.settingsmenu

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import liveplugin.Settings

class RunPluginsOnProjectOpenAction: ToggleAction(
    "Run Live Plugins On Project Open",
    "Run all live plugins in the project when it's open. The project-level plugins must be located in '.live-plugins' directory.",
    null
), DumbAware {
    override fun isSelected(event: AnActionEvent) =
        Settings.instance.runPluginsOnProjectOpen

    override fun setSelected(event: AnActionEvent, state: Boolean) {
        Settings.instance.runPluginsOnProjectOpen = state
    }
}
