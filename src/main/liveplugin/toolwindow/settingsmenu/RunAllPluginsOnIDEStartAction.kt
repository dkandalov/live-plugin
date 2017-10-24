package liveplugin.toolwindow.settingsmenu

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import liveplugin.Settings

class RunAllPluginsOnIDEStartAction: ToggleAction("Run All Live Plugins on IDE Start"), DumbAware {
    override fun isSelected(event: AnActionEvent): Boolean {
        return Settings.getInstance().runAllPluginsOnIDEStartup
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
        Settings.getInstance().runAllPluginsOnIDEStartup = state
    }
}
