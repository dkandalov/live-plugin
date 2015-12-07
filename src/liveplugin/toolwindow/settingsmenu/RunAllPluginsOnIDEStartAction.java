package liveplugin.toolwindow.settingsmenu;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import liveplugin.Settings;

public class RunAllPluginsOnIDEStartAction extends ToggleAction implements DumbAware {
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
