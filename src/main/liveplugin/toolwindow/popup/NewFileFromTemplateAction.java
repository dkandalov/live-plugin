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
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.actions.FileChooserAction;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.UIBundle;
import liveplugin.LivePluginAppComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class NewFileFromTemplateAction extends FileChooserAction {
	private final String newFileName;
	private final String fileContent;
	private final FileType fileType;

	public NewFileFromTemplateAction(String text, String newFileName, String fileContent, @Nullable Icon icon, @NotNull FileType fileType) {
		super(text, text, icon);
		this.newFileName = newFileName;
		this.fileContent = fileContent;
		this.fileType = fileType;
	}

	@Override protected void update(FileSystemTree fileSystemTree, AnActionEvent e) {
		boolean isAtPluginRoot = false;
		VirtualFile parentFile = fileSystemTree.getNewFileParent();
		if (parentFile != null) {
			isAtPluginRoot = LivePluginAppComponent.pluginIdToPathMap().containsValue(parentFile.getCanonicalPath());
		}

		boolean fileDoesNotExist = false;
		if (isAtPluginRoot) {
			fileDoesNotExist = (parentFile.findChild(newFileName) == null);
		}

		Presentation presentation = e.getPresentation();
		presentation.setVisible(isAtPluginRoot && fileDoesNotExist);
		presentation.setEnabled(isAtPluginRoot && fileDoesNotExist);
		presentation.setIcon(fileType.getIcon());
	}

	@Override protected void actionPerformed(FileSystemTree fileSystemTree, AnActionEvent e) {
		VirtualFile parentFile = fileSystemTree.getNewFileParent();

		//noinspection ThrowableResultOfMethodCallIgnored
		Exception failReason = ((FileSystemTreeImpl)fileSystemTree).createNewFile(parentFile, newFileName, fileType, fileContent);
		if (failReason != null) {
			String message = UIBundle.message("create.new.file.could.not.create.file.error.message", newFileName);
			String title = UIBundle.message("error.dialog.title");
			Messages.showMessageDialog(message, title, Messages.getErrorIcon());
		}
	}
}