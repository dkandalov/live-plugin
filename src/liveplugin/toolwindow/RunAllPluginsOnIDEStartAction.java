package liveplugin.toolwindow;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import liveplugin.Settings;

class RunAllPluginsOnIDEStartAction extends ToggleAction {
	public RunAllPluginsOnIDEStartAction() {
		super("Run All Live Plugins on IDE Start");
	}

	@Override public boolean isSelected(AnActionEvent event) {
		return Settings.getInstance().runAllPluginsOnIDEStartup;
	}

	@Override public void setSelected(AnActionEvent event, boolean state) {
		Settings.getInstance().runAllPluginsOnIDEStartup = state;
	}
}
