package liveplugin.toolwindow.addplugin.git;


import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import icons.GithubIcons;
import liveplugin.IdeUtil;
import liveplugin.LivePluginAppComponent;
import liveplugin.toolwindow.addplugin.git.jetbrains.plugins.github.api.GithubApiUtil;
import liveplugin.toolwindow.addplugin.git.jetbrains.plugins.github.api.GithubGist;
import liveplugin.toolwindow.addplugin.git.jetbrains.plugins.github.util.GithubAuthData;
import liveplugin.toolwindow.util.PluginsIO;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;

import static liveplugin.LivePluginAppComponent.pluginsRootPath;

public class AddPluginFromGistAction extends AnAction {
	private static final Logger log = Logger.getInstance(AddPluginFromGistAction.class);
	private static final Icon defaultIcon = null;

	public AddPluginFromGistAction() {
		super("Plugin from Gist", "Plugin from Gist", GithubIcons.Github_icon);
	}

	@Override public void actionPerformed(AnActionEvent event) {
		String gistUrl = askUserForGistUrl(event);
		if (gistUrl == null) return;

		GithubGist gist;
		try {
			gist = fetchGistFrom(gistUrl);
		} catch (IOException e) {
			showMessageThatFetchingGistFailed(e, event);
			return;
		}

		String newPluginId = askUserForPluginId(event);
		if (newPluginId == null) return;

		try {
			createPluginFrom(gist, newPluginId);
		} catch (IOException e) {
			showMessageThatCreatingPluginFailed(e, newPluginId, event);
		}
	}

	private static String askUserForGistUrl(AnActionEvent event) {
		return Messages.showInputDialog(
				event.getProject(),
				"Enter gist URL:",
				"Add Plugin From Gist",
				defaultIcon, "", new GistUrlValidator());
	}

	private static GithubGist fetchGistFrom(String gistUrl) throws IOException {
		return GithubApiUtil.getGist(GithubAuthData.createAnonymous(), GistUrlValidator.extractGistIdFrom(gistUrl));
	}

	private static String askUserForPluginId(AnActionEvent event) {
		return Messages.showInputDialog(
				event.getProject(),
				"Enter new plugin name:",
				"New Plugin",
				defaultIcon, "", new PluginIdValidator());
	}

	private static void createPluginFrom(GithubGist gist, String pluginId) throws IOException {
		for (GithubGist.GistFile gistFile : gist.getFiles()) {
			PluginsIO.createFile(pluginsRootPath() + "/" + pluginId, gistFile.getFilename(), gistFile.getContent());
		}
	}

	private void showMessageThatCreatingPluginFailed(IOException e, String newPluginId, AnActionEvent event) {
		IdeUtil.showErrorDialog(
				event.getProject(),
				"Error adding plugin \"" + newPluginId + "\" to " + LivePluginAppComponent.pluginsRootPath(),
				"Add Plugin"
		);
		log.info(e);
	}

	private static void showMessageThatFetchingGistFailed(IOException e, AnActionEvent event) {
		IdeUtil.showErrorDialog(event.getProject(), "Failed to fetch gist", "LivePlugin");
		log.info(e);
	}

	private static class GistUrlValidator implements InputValidatorEx {
		@Override public boolean checkInput(String inputString) {
			return inputString.lastIndexOf('/') != -1;
		}

		public static String extractGistIdFrom(String gistUrl) {
			// TODO set error message here like in com.intellij.ide.actions.CreateFileAction.MyValidator ?
			int i = gistUrl.lastIndexOf('/');
			return gistUrl.substring(i + 1);
		}

		@Override public boolean canClose(String inputString) {
			return true;
		}

		@Nullable @Override public String getErrorText(String inputString) {
			return "Gist URL should have at least one '/'";
		}
	}

	private static class PluginIdValidator implements InputValidatorEx {
		@Override public boolean checkInput(String pluginId) {
			return !LivePluginAppComponent.pluginExists(pluginId);
		}

		@Nullable @Override public String getErrorText(String pluginId) {
			return "There is already a plugin with this name";
		}

		@Override public boolean canClose(String pluginId) {
			return true;
		}
	}
}
