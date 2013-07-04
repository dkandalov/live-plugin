package liveplugin.toolwindow;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import liveplugin.IdeUtil;
import liveplugin.LivePluginAppComponent;

import java.util.List;

class AddExamplePluginAction extends AnAction {
	private static final Logger LOG = Logger.getInstance(AddExamplePluginAction.class);

	private final String pluginId;
	private final ExamplePluginInstaller examplePluginInstaller;

	public AddExamplePluginAction(String pluginPath, List<String> sampleFiles) {
		examplePluginInstaller = new ExamplePluginInstaller(pluginPath, sampleFiles);
		pluginId = ExamplePluginInstaller.extractPluginIdFrom(pluginPath);

		getTemplatePresentation().setText(pluginId);
	}

	@Override public void actionPerformed(final AnActionEvent event) {
		examplePluginInstaller.installPlugin(new ExamplePluginInstaller.Listener() {
			@Override public void onException(Exception e, String pluginPath) {
				logException(e, event, pluginPath);
			}
		});
		new RefreshPluginTreeAction().actionPerformed(event);
	}

	@Override public void update(AnActionEvent event) {
		event.getPresentation().setEnabled(!LivePluginAppComponent.pluginExists(pluginId));
	}

	private void logException(Exception e, AnActionEvent event, String pluginPath) {
		Project project = event.getProject();
		if (project != null) {
			IdeUtil.showErrorDialog(
					project,
					"Error adding plugin \"" + pluginPath + "\" to " + LivePluginAppComponent.pluginsRootPath(),
					"Add Plugin"
			);
		}
		LOG.error(e);
	}
}
