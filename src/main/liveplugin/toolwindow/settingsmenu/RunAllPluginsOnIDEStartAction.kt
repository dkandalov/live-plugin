package liveplugin.toolwindow.settingsmenu

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import liveplugin.Settings

class RunAllPluginsOnIDEStartAction: ToggleAction("Run All Live Plugins on IDE Start"), DumbAware {
    override fun isSelected(event: AnActionEvent) =
        Settings.instance.runAllPluginsOnIDEStartup

    override fun setSelected(event: AnActionEvent, state: Boolean) {
        Settings.instance.runAllPluginsOnIDEStartup = state
    }
}
