package ru.intellijeval.git;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.checkout.GitCheckoutProvider;
import git4idea.commands.Git;
import icons.GithubIcons;
import org.jetbrains.annotations.NotNull;
import ru.intellijeval.toolwindow.PluginToolWindowManager;

import java.io.File;

/**
 * User: dima
 * Date: 20/01/2013
 */
@SuppressWarnings("ComponentNotRegistered")
public class GithubComponent implements ApplicationComponent {
	@Override public void initComponent() {
		PluginToolWindowManager.addFromGitHubAction = new AddPluginFromGitHubAction();
	}

	@Override public void disposeComponent() {
	}

	@NotNull @Override public String getComponentName() {
		return "GithubComponent";
	}

	/**
	 * Partially copied from {@link org.jetbrains.plugins.github.GithubCheckoutProvider}.
	 */
	private static class AddPluginFromGitHubAction extends AnAction {

		private AddPluginFromGitHubAction() {
			super("Plugin from GitHub", "Plugin from GitHub", GithubIcons.Github_icon);
		}

		@Override public void actionPerformed(final AnActionEvent event) {
			GitCloneDialog dialog = new GitCloneDialog(event.getProject());
			dialog.show();
			if (!dialog.isOK()) return;
			dialog.rememberSettings();

			VirtualFile destinationParent = LocalFileSystem.getInstance().findFileByIoFile(new File(dialog.getParentDirectory()));
			if (destinationParent == null) return;

			GitCheckoutProvider.clone(
					event.getProject(),
					ServiceManager.getService(Git.class),
					new CheckoutProvider.Listener() {
						@Override public void directoryCheckedOut(File directory, VcsKey vcs) {}

						@Override public void checkoutCompleted() {
							new PluginToolWindowManager.RefreshPluginListAction().actionPerformed(event);
						}
					},
					destinationParent,
					dialog.getSourceRepositoryURL(),
					dialog.getDirectoryName(),
					dialog.getParentDirectory()
			);

			// TODO check that project contains "plugin.groovy"
		}
	}
}
