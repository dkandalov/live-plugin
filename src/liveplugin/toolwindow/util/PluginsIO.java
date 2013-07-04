package liveplugin.toolwindow.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ThrowableRunnable;

import java.io.IOException;

public class PluginsIO {
	private static final String REQUESTOR = PluginsIO.class.getCanonicalName();

	public static void createFile(final String parentPath, final String fileName, final String text) throws IOException {
		runIOAction("createFile", new ThrowableRunnable<IOException>() {
			@Override public void run() throws IOException {

				VirtualFile parentFolder = VfsUtil.createDirectoryIfMissing(parentPath);
				if (parentFolder == null) throw new IOException("Failed to create folder " + parentPath);

				VirtualFile file = parentFolder.createChildData(REQUESTOR, fileName);
				VfsUtil.saveText(file, text);

			}
		});
	}

	public static void copyFolder(final String folder, final String toFolder) throws IOException {
		runIOAction("copyFolder", new ThrowableRunnable<IOException>() {
			@Override public void run() throws IOException {

				VirtualFile targetFolder = VfsUtil.createDirectoryIfMissing(toFolder);
				if (targetFolder == null) throw new IOException("Failed to create folder " + toFolder);
				VirtualFile folderToCopy = VirtualFileManager.getInstance().findFileByUrl("file://" + folder);
				if (folderToCopy == null) throw new IOException("Failed to find folder " + folder);

				VfsUtil.copy(REQUESTOR, folderToCopy, targetFolder);

			}
		});
	}

	public static void delete(final String filePath) throws IOException {
		runIOAction("delete", new ThrowableRunnable<IOException>() {
			@Override public void run() throws IOException {

				VirtualFile file = VirtualFileManager.getInstance().findFileByUrl("file://" + filePath);
				if (file == null) throw new IOException("Failed to find file " + filePath);

				file.delete(REQUESTOR);

			}
		});
	}

	private static void runIOAction(String actionName, final ThrowableRunnable<IOException> runnable) throws IOException {
		final IOException[] exception = new IOException[]{null};
		CommandProcessor.getInstance().executeCommand(null, new Runnable() {
			@Override public void run() {
				ApplicationManager.getApplication().runWriteAction(new Runnable() {
					public void run() {
						try {

							runnable.run();

						} catch (IOException e) {
							exception[0] = e;
						}
					}
				});
			}
		}, actionName, "LivePlugin");

		if (exception[0] != null) throw exception[0];
	}
}
