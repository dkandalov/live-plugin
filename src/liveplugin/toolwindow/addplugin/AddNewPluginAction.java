package liveplugin.toolwindow.addplugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import liveplugin.IdeUtil;
import liveplugin.LivePluginAppComponent;
import liveplugin.pluginrunner.GroovyPluginRunner;
import liveplugin.toolwindow.RefreshPluginTreeAction;
import liveplugin.toolwindow.util.PluginsIO;

import java.io.IOException;

@SuppressWarnings("ComponentNotRegistered")
public class AddNewPluginAction extends AnAction {
	private static final Logger LOG = Logger.getInstance(AddNewPluginAction.class);

	public AddNewPluginAction() {
		super("New Plugin");
	}

	@Override public void actionPerformed(AnActionEvent event) {
		String newPluginId = Messages.showInputDialog(event.getProject(), "Enter new plugin name:", "New Plugin", null);

		// TODO use liveplugin.toolwindow.addplugin.git.AddPluginFromGistAction.PluginIdValidator
		if (newPluginId == null) return;
		if (LivePluginAppComponent.pluginExists(newPluginId)) {
			Messages.showErrorDialog(event.getProject(), "Plugin \"" + newPluginId + "\" already exists.", "New Plugin");
			return;
		}

		try {

			String text = LivePluginAppComponent.defaultPluginScript();
			PluginsIO.createFile(LivePluginAppComponent.pluginsRootPath() + "/" + newPluginId, GroovyPluginRunner.MAIN_SCRIPT, text);

		} catch (IOException e) {
			Project project = event.getProject();
			if (project != null) {
				IdeUtil.showErrorDialog(
						project,
						"Error adding plugin \"" + newPluginId + "\" to " + LivePluginAppComponent.pluginsRootPath(),
						"Add Plugin"
				);
			}
			LOG.error(e);
		}

		new RefreshPluginTreeAction().actionPerformed(event);
	}
}
