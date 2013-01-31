package intellijeval.toolwindow;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.actions.FileChooserAction;
import com.intellij.openapi.fileChooser.ex.FileNodeDescriptor;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import intellijeval.Util;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeModel;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

/**
 * User: dima
 * Date: 20/01/2013
 */
public class RenameFileAction extends FileChooserAction {

	@Override protected void actionPerformed(final FileSystemTree fileSystemTree, final AnActionEvent event) {
		final VirtualFile file = fileSystemTree.getSelectedFile();
		if (file == null) return;

		String defaultName = file.getName();
		final String newFileName = Messages.showInputDialog("Rename file to:", "Rename", null, defaultName, null);
		if (newFileName == null || newFileName.equals(file.getName())) return;

		ApplicationManager.getApplication().runWriteAction(new Runnable() {
			@Override public void run() {
				try {
					file.rename(null, newFileName);
					updateTreeModel_HACK();
				} catch (IOException e) {
					Util.showErrorDialog(event.getProject(), "Couldn't rename " + file.getName() + " to " + newFileName, "Error");
				}
			}

			/**
			 * Couldn't find any way to update file chooser tree to show new file name, therefore this hack.
			 * There is still a problem with this except that it's a hack.
			 * If new file name is longer than previous name, it's not shown fully.
			 * The workaround is to collapse, expand parent tree node.
			 */
			private void updateTreeModel_HACK() {
				TreeModel model = fileSystemTree.getTree().getModel();
				Queue<DefaultMutableTreeNode> queue = new LinkedList<DefaultMutableTreeNode>();
				DefaultMutableTreeNode node = (DefaultMutableTreeNode) model.getRoot();
				queue.add(node);

				while (!queue.isEmpty()) {
					node = queue.remove();
					final Object userObject = node.getUserObject();
					boolean nodeContainsRenamedFile = userObject instanceof FileNodeDescriptor && file.equals(((FileNodeDescriptor) userObject).getElement().getFile());

					if (nodeContainsRenamedFile) {
						final DefaultMutableTreeNode finalNode = node;
						SwingUtilities.invokeLater(new Runnable() {
							@Override public void run() {
								FileNodeDescriptor nodeDescriptor = (FileNodeDescriptor) userObject;
								FileElement fileElement = new FileElement(file, newFileName);
								fileElement.setParent(nodeDescriptor.getElement().getParent());
								finalNode.setUserObject(new FileNodeDescriptor(
										nodeDescriptor.getProject(),
										fileElement,
										nodeDescriptor.getParentDescriptor(),
										nodeDescriptor.getIcon(),
										newFileName,
										nodeDescriptor.getComment()
								));
							}
						});

						return;
					}

					for (int i = 0; i < model.getChildCount(node); i++) {
						queue.add((DefaultMutableTreeNode) model.getChild(node, i));
					}
				}
			}
		});
	}

	@Override protected void update(FileSystemTree fileChooser, AnActionEvent e) {
		e.getPresentation().setVisible(true);
		e.getPresentation().setEnabled(fileChooser.selectionExists());
	}
}
