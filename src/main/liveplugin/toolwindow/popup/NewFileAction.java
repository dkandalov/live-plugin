/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package liveplugin.toolwindow.popup;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.actions.FileChooserAction;
import com.intellij.openapi.fileChooser.ex.FileChooserKeys;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;

/**
 * Originally forked from {@link com.intellij.openapi.fileChooser.actions.NewFileAction}
 */
public class NewFileAction extends FileChooserAction {
    @Nullable private final Icon icon;
    @Nullable private final FileType fileType;

    public NewFileAction(String text, @NotNull Icon icon) {
        this(text, icon, null);
    }

	public NewFileAction(String text, @NotNull FileType fileType) {
        this(text, fileType.getIcon(), fileType);
    }

	private NewFileAction(String text, @Nullable Icon icon, @Nullable FileType fileType) {
		super(text, text, icon);
        this.fileType = fileType;
        this.icon = icon;
    }

	protected void update(FileSystemTree fileSystemTree, AnActionEvent e) {
		Presentation presentation = e.getPresentation();
		presentation.setVisible(true);
		VirtualFile selectedFile = fileSystemTree.getNewFileParent();
		presentation.setEnabled(selectedFile != null);
		// FORK DIFF (got rid of layered "new" icon because it's ugly)
		presentation.setIcon(icon);
	}

	protected void actionPerformed(FileSystemTree fileSystemTree, AnActionEvent e) {
		String initialContent = e.getData(FileChooserKeys.NEW_FILE_TEMPLATE_TEXT);
		// FORK DIFF (don't really care if initial content is null)
		if (initialContent == null) initialContent = "";
		createNewFile(fileSystemTree, e.getProject(), fileType, initialContent);
	}

    private void createNewFile(FileSystemTree fileSystemTree, Project project, FileType fileType, final String initialContent) {
        VirtualFile file = fileSystemTree.getNewFileParent();
        if (file == null) return;
        if (!file.isDirectory()) file = file.getParent();
        if (file == null) return;

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
            Exception failReason = createNewFile(project, (FileSystemTreeImpl)fileSystemTree, file, newFileName, fileType, initialContent);
            if (failReason != null) {
                Messages.showMessageDialog(UIBundle.message("create.new.file.could.not.create.file.error.message", newFileName),
                        UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
                continue;
            }
            return;
        }
    }

    // modified copy of com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl.createNewFile()
    private Exception createNewFile(Project project, final FileSystemTreeImpl fileSystemTree,
                                    final VirtualFile parentDirectory, final String newFileName,
                                    final FileType fileType, final String initialContent) {
        final Exception[] failReason = new Exception[] { null };
        CommandProcessor.getInstance().executeCommand(
                project, new Runnable() {
                    public void run() {
                        ApplicationManager.getApplication().runWriteAction(new Runnable() {
                            public void run() {
                                try {
                                    String newFileNameWithExtension = newFileName;
                                    if (fileType != null && !(fileType instanceof UnknownFileType)) {
                                        newFileNameWithExtension = newFileName.endsWith('.'+fileType.getDefaultExtension())? newFileName : newFileName+'.'+fileType.getDefaultExtension();
                                    }
                                    final VirtualFile file = parentDirectory.createChildData(this, newFileNameWithExtension);
                                    VfsUtil.saveText(file, initialContent != null ? initialContent : "");
                                    fileSystemTree.updateTree();
                                    fileSystemTree.select(file, null);
                                }
                                catch (IOException e) {
                                    failReason[0] = e;
                                }
                            }
                        });
                    }
                },
                UIBundle.message("file.chooser.create.new.file.command.name"),
                null
        );
        return failReason[0];
    }
}