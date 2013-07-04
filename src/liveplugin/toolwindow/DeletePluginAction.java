package liveplugin.toolwindow;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import liveplugin.IdeUtil;
import liveplugin.toolwindow.util.PluginsIO;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.map;

class DeletePluginAction extends AnAction {
	private static final Logger LOG = Logger.getInstance(DeletePluginAction.class);


	public DeletePluginAction() {
		super("Delete Plugin", "Delete Plugin", IdeUtil.DELETE_PLUGIN_ICON);
	}

	@Override public void actionPerformed(AnActionEvent event) {
		VirtualFile[] files = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(event.getDataContext());
		if (files == null || files.length == 0) return;

		Collection<VirtualFile> pluginRoots = PluginToolWindowManager.PluginToolWindow.findPluginRootsFor(files);
		if (userDoesNotWantToRemovePlugins(pluginRoots, event.getProject())) return;

		for (VirtualFile pluginRoot : pluginRoots) {
			try {

				PluginsIO.delete(pluginRoot.getPath());

			} catch (IOException e) {
				Project project = event.getProject();
				if (project != null) {
					IdeUtil.showErrorDialog(project, "Error deleting plugin \"" + pluginRoot.getPath(), "Delete Plugin");
				}
				LOG.error(e);
			}
		}

		new RefreshPluginTreeAction().actionPerformed(event);
	}

	@Override public void update(AnActionEvent event) {
		VirtualFile[] files = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(event.getDataContext());

		boolean enabled = true;
		if (files == null || files.length == 0)
			enabled = false;
		else if (PluginToolWindowManager.PluginToolWindow.findPluginRootsFor(files).isEmpty())
			enabled = false;

		event.getPresentation().setEnabled(enabled);
	}

	private static boolean userDoesNotWantToRemovePlugins(Collection<VirtualFile> pluginRoots, Project project) {
		List<String> pluginIds = map(pluginRoots, new Function<VirtualFile, String>() {
			@Override public String fun(VirtualFile virtualFile) {
				return virtualFile.getName();
			}
		});

		String message;
		if (pluginIds.size() == 1) {
			message = "Do you want to delete plugin \"" + pluginIds.get(0) + "\"?";
		} else if (pluginIds.size() == 2) {
			message = "Do you want to delete plugin \"" + pluginIds.get(0) + "\" and \"" + pluginIds.get(1) + "\"?";
		} else {
			message = "Do you want to delete plugins \"" + StringUtil.join(pluginIds, ", ") + "\"?";
		}
		int answer = Messages.showOkCancelDialog(
				project,
				message,
				"Delete",
				ApplicationBundle.message("button.delete"),
				CommonBundle.getCancelButtonText(),
				Messages.getQuestionIcon()
		);
		return answer != Messages.OK;
	}
}
