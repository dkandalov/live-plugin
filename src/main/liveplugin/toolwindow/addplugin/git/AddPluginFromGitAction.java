package liveplugin.toolwindow.addplugin.git;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import git4idea.checkout.GitCheckoutProvider;
import git4idea.commands.Git;
import icons.GithubIcons;
import liveplugin.IDEUtil;
import liveplugin.pluginrunner.GroovyPluginRunner;
import liveplugin.toolwindow.RefreshPluginsPanelAction;
import liveplugin.toolwindow.util.PluginsIO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static liveplugin.LivePluginAppComponent.isInvalidPluginFolder;
import static liveplugin.LivePluginAppComponent.livepluginsPath;

/**
 * Partially copied from org.jetbrains.plugins.github.GithubCheckoutProvider (became com.intellij.dvcs.ui.CloneDvcsDialog in IJ13)
 */
class AddPluginFromGitAction extends AnAction implements DumbAware {
	private static final Logger logger = Logger.getInstance(AddPluginFromGitAction.class);
	private static final String dialogTitle = "Clone Plugin From Git";


	AddPluginFromGitAction() {
		super("Clone from Git", "Clone from Git", GithubIcons.Github_icon);
	}

	@Override public void actionPerformed(@NotNull AnActionEvent event) {
		Project project = event.getProject();
		if (project == null) return;

		GitCloneDialog dialog = new GitCloneDialog(project);
		dialog.show();
		if (!dialog.isOK()) return;
		dialog.rememberSettings();

		VirtualFile destinationFolder = LocalFileSystem.getInstance().findFileByIoFile(new File(dialog.getParentDirectory()));
		if (destinationFolder == null) return;

		GitCheckoutProvider.clone(
				project,
				ServiceManager.getService(Git.class),
				new MyCheckoutListener(project, destinationFolder, dialog.getDirectoryName()),
				destinationFolder,
				dialog.getSourceRepositoryURL(),
				dialog.getDirectoryName(),
				dialog.getParentDirectory()
		);
	}

	private static class MyCheckoutListener implements CheckoutProvider.Listener {
		private final VirtualFile destinationFolder;
		private final String pluginName;
		private final Project project;

		public MyCheckoutListener(@Nullable Project project, VirtualFile destinationFolder, String pluginName) {
			this.destinationFolder = destinationFolder;
			this.pluginName = pluginName;
			this.project = project;
		}

		/**
		 * Copied from {@link com.intellij.openapi.vcs.checkout.CompositeCheckoutListener}
		 */
		private static VirtualFile refreshVFS(final File directory) {
			final Ref<VirtualFile> result = new Ref<>();
			ApplicationManager.getApplication().runWriteAction(() -> {
				final LocalFileSystem lfs = LocalFileSystem.getInstance();
				final VirtualFile vDir = lfs.refreshAndFindFileByIoFile(directory);
				result.set(vDir);
				if (vDir != null) {
					final LocalFileSystem.WatchRequest watchRequest = lfs.addRootToWatch(vDir.getPath(), true);
					((NewVirtualFile) vDir).markDirtyRecursively();
					vDir.refresh(false, true);
					if (watchRequest != null) {
						lfs.removeWatchedRoot(watchRequest);
					}
				}
			});
			return result.get();
		}

		@Override public void directoryCheckedOut(File directory, VcsKey vcs) {
			refreshVFS(directory);
		}

		@Override public void checkoutCompleted() {
			VirtualFile pluginsRoot = VirtualFileManager.getInstance().findFileByUrl("file://" + livepluginsPath);
			if (pluginsRoot == null) return;

			RefreshQueue.getInstance().refresh(false, true, () -> {
				VirtualFile clonedFolder = destinationFolder.findChild(pluginName);
				if (clonedFolder == null) {
					logger.error("Couldn't find virtual file for checked out plugin: " + pluginName);
					return;
				}

				try {

					if (isInvalidPluginFolder(clonedFolder) && userDoesNotWantToKeepIt()) {
						PluginsIO.delete(clonedFolder.getPath());
					}

				} catch (Exception e) {
					if (project != null) {
						IDEUtil.showErrorDialog(project, "Error deleting plugin \"" + clonedFolder.getPath(), "Delete Plugin");
					}
					logger.error(e);
				}

				RefreshPluginsPanelAction.Companion.refreshPluginTree();
			}, pluginsRoot);
		}

		private boolean userDoesNotWantToKeepIt() {
			int answer = Messages.showYesNoDialog(
					project,
					"It looks like \"" + pluginName + "\" is not a valid plugin because it does not contain \"" +
							GroovyPluginRunner.mainScript + "\".\n\nDo you want to add it anyway?",
					dialogTitle,
					Messages.getQuestionIcon()
			);
			return answer != Messages.YES;
		}
	}
}
