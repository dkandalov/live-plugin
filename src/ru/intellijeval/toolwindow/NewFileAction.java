package ru.intellijeval.toolwindow;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.UIBundle;
import ru.intellijeval.toolwindow.fileChooser.FileSystemTree;
import ru.intellijeval.toolwindow.fileChooser.actions.FileChooserAction;
import ru.intellijeval.toolwindow.fileChooser.ex.FileChooserKeys;

import javax.swing.*;

/**
 * Fork of {@link ru.intellijeval.toolwindow.fileChooser.actions.NewFileAction}
 *
 * User: dima
 * Date: 13/08/2012
 */
public class NewFileAction extends FileChooserAction {
	public NewFileAction() {
	}

	public NewFileAction(String text, Icon icon) {
		super(text, text, icon);
	}

	protected void update(FileSystemTree fileSystemTree, AnActionEvent e) {
		Presentation presentation = e.getPresentation();
		final FileType fileType = e.getData(FileChooserKeys.NEW_FILE_TYPE);
		if (fileType != null) {
			presentation.setVisible(true);
			VirtualFile selectedFile = fileSystemTree.getNewFileParent();
			presentation.setEnabled(selectedFile != null && selectedFile.isDirectory());
		} else {
			presentation.setVisible(false);
		}
	}

	protected void actionPerformed(FileSystemTree fileSystemTree, AnActionEvent e) {
		final FileType fileType = e.getData(FileChooserKeys.NEW_FILE_TYPE);
		final String initialContent = "";
		if (fileType != null) {
			createNewFile(fileSystemTree, fileType, initialContent);
		}
	}

	private static void createNewFile(FileSystemTree fileSystemTree, final FileType fileType, final String initialContent) {
		final VirtualFile file = fileSystemTree.getNewFileParent();
		if (file == null || !file.isDirectory()) return;

		String newFileName;
		while (true) {
			newFileName = Messages.showInputDialog(UIBundle.message("create.new.file.enter.new.file.name.prompt.text"),
					UIBundle.message("new.file.dialog.title"), Messages.getQuestionIcon());
			if (newFileName == null) {
				return;
			}
			if ("".equals(newFileName.trim())) {
				Messages.showMessageDialog(UIBundle.message("create.new.file.file.name.cannot.be.empty.error.message"),
						UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
				continue;
			}
			@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
			Exception failReason = fileSystemTree.createNewFile(file, newFileName, fileType, initialContent);
			if (failReason != null) {
				Messages.showMessageDialog(UIBundle.message("create.new.file.could.not.create.file.error.message", newFileName),
						UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
				continue;
			}
			return;
		}
	}
}
