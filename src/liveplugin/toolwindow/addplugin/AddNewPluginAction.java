package liveplugin.toolwindow.addplugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import liveplugin.IDEUtil;
import liveplugin.Icons;
import liveplugin.LivePluginAppComponent;
import liveplugin.pluginrunner.GroovyPluginRunner;
import liveplugin.toolwindow.RefreshPluginsPanelAction;
import liveplugin.toolwindow.util.PluginsIO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

@SuppressWarnings("ComponentNotRegistered")
public class AddNewPluginAction extends AnAction implements DumbAware {
	private static final Logger log = Logger.getInstance(AddNewPluginAction.class);
	private static final String addNewPluginTitle = "Add New Plugin";

	public AddNewPluginAction() {
		super("New Plugin", "Create new plugin", Icons.NEW_PLUGIN_ICON);
	}

	@Override public void actionPerformed(@NotNull AnActionEvent event) {
		String newPluginId = Messages.showInputDialog(
				event.getProject(),
				"Enter new plugin name:",
				addNewPluginTitle,
				null, "", new PluginIdValidator()
		);
		if (newPluginId == null) return;

		try {

			String text = LivePluginAppComponent.defaultPluginScript();
			PluginsIO.createFile(LivePluginAppComponent.pluginsRootPath() + "/" + newPluginId, GroovyPluginRunner.MAIN_SCRIPT, text);

		} catch (IOException e) {
			Project project = event.getProject();
			if (project != null) {
				IDEUtil.showErrorDialog(
						project,
						"Error adding plugin \"" + newPluginId + "\" to " + LivePluginAppComponent.pluginsRootPath(),
						addNewPluginTitle
				);
			}
			log.error(e);
		}

		RefreshPluginsPanelAction.refreshPluginTree();
	}

	public static class PluginIdValidator implements InputValidatorEx {
		private String errorText;

		@Override public boolean checkInput(String pluginId) {
			boolean isValid = !LivePluginAppComponent.pluginExists(pluginId);
			errorText = isValid ? null : "There is already a plugin with this name";
			return isValid;
		}

		@Nullable @Override public String getErrorText(String pluginId) {
			return errorText;
		}

		@Override public boolean canClose(String pluginId) {
			return true;
		}
	}
}
