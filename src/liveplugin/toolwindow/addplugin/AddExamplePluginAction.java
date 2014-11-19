package liveplugin.toolwindow.addplugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import liveplugin.IdeUtil;
import liveplugin.LivePluginAppComponent;
import liveplugin.toolwindow.RefreshPluginTreeAction;
import liveplugin.toolwindow.util.ExamplePluginInstaller;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AddExamplePluginAction extends AnAction {
	private static final Logger LOG = Logger.getInstance(AddExamplePluginAction.class);

	private final String pluginId;
	private final ExamplePluginInstaller examplePluginInstaller;

	public AddExamplePluginAction(String pluginPath, List<String> sampleFiles) {
		examplePluginInstaller = new ExamplePluginInstaller(pluginPath, sampleFiles);
		pluginId = ExamplePluginInstaller.extractPluginIdFrom(pluginPath);

		getTemplatePresentation().setText(pluginId);
	}

	@Override public void actionPerformed(@NotNull final AnActionEvent event) {
		examplePluginInstaller.installPlugin(new ExamplePluginInstaller.Listener() {
			@Override public void onException(Exception e, String pluginPath) {
				logException(e, event, pluginPath);
			}
		});
		RefreshPluginTreeAction.refreshPluginTree();
	}

	@Override public void update(@NotNull AnActionEvent event) {
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
