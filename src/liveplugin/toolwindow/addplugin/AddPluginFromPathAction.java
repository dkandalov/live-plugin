package liveplugin.toolwindow.addplugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import liveplugin.IDEUtil;
import liveplugin.LivePluginAppComponent;
import liveplugin.pluginrunner.GroovyPluginRunner;
import liveplugin.toolwindow.PluginToolWindowManager;
import liveplugin.toolwindow.RefreshPluginTreeAction;
import liveplugin.toolwindow.util.PluginsIO;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

@SuppressWarnings("ComponentNotRegistered")
public class AddPluginFromPathAction extends AnAction {
	private static final Logger LOG = Logger.getInstance(AddPluginFromPathAction.class);
    private static final String dialogTitle = "Copy Plugin From Path";


    public AddPluginFromPathAction() {
		super("Copy from Path");
	}

	private static List<VirtualFile> getFileSystemRoots() {
		LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
		Set<VirtualFile> roots = new HashSet<VirtualFile>();
		File[] ioRoots = File.listRoots();
		if (ioRoots != null) {
			for (File root : ioRoots) {
				String path = FileUtil.toSystemIndependentName(root.getAbsolutePath());
				VirtualFile file = localFileSystem.findFileByPath(path);
				if (file != null) {
					roots.add(file);
				}
			}
		}
		ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
		Collections.addAll(result, VfsUtil.toVirtualFileArray(roots));
		return result;
	}

	@Override public void actionPerformed(@NotNull AnActionEvent event) {
		FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, true, true, true, false);

		PluginToolWindowManager.addRoots(descriptor, getFileSystemRoots());

		VirtualFile virtualFile = FileChooser.chooseFile(descriptor, event.getProject(), null);
		if (virtualFile == null) return;

		if (LivePluginAppComponent.isInvalidPluginFolder(virtualFile) &&
				userDoesNotWantToAddFolder(virtualFile, event.getProject())) return;

		String folderToCopy = virtualFile.getPath();
		String targetFolder = LivePluginAppComponent.pluginsRootPath();
		try {

			PluginsIO.copyFolder(folderToCopy, targetFolder);

		} catch (IOException e) {
			Project project = event.getProject();
			if (project != null) {
				IDEUtil.showErrorDialog(
						project,
						"Error adding plugin \"" + folderToCopy + "\" to " + targetFolder,
                        dialogTitle
				);
			}
			LOG.error(e);
		}

		RefreshPluginTreeAction.refreshPluginTree();
	}

	private boolean userDoesNotWantToAddFolder(VirtualFile virtualFile, Project project) {
		int answer = Messages.showYesNoDialog(
				project,
				"Folder \"" + virtualFile.getPath() + "\" is not valid plugin folder because it does not contain \"" + GroovyPluginRunner.MAIN_SCRIPT + "\"." +
						"\nDo you want to add it anyway?",
                dialogTitle,
				Messages.getQuestionIcon()
		);
		return answer != Messages.YES;
	}
}
