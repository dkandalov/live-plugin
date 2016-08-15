package liveplugin.toolwindow.addplugin.git;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.DumbAware;
import liveplugin.toolwindow.PluginToolWindowManager;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("ComponentNotRegistered")
public class GitDependentAppComponent implements ApplicationComponent, DumbAware {

	@Override public void initComponent() {
		PluginToolWindowManager.addFromGistAction = new AddPluginFromGistAction();
		PluginToolWindowManager.addFromGitHubAction = new AddPluginFromGitAction();
	}

	@Override public void disposeComponent() {
	}

	@NotNull @Override public String getComponentName() {
		return "GitDependentAppComponent";
	}

}
