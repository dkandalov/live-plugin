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
package liveplugin;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author DKandalov
 */
public class IdeUtil {
	// icons paths are inlined "in case API changes but path to icons does not"
	// it's probably not worth doing
	public static final Icon ADD_PLUGIN_ICON = IconLoader.getIcon("/general/add.png"); // 16x16
	public static final Icon DELETE_PLUGIN_ICON = IconLoader.getIcon("/general/remove.png"); // 16x16
	public static final Icon REFRESH_PLUGIN_LIST_ICON = IconLoader.getIcon("/actions/sync.png"); // 16x16
	public static final Icon PLUGIN_ICON = IconLoader.getIcon("/nodes/plugin.png"); // 16x16
	public static final Icon RUN_PLUGIN_ICON = IconLoader.getIcon("/actions/execute.png"); // 16x16
	public static final Icon EXPAND_ALL_ICON = IconLoader.getIcon("/actions/expandall.png"); // 16x16
	public static final Icon COLLAPSE_ALL_ICON = IconLoader.getIcon("/actions/collapseall.png"); // 16x16
	public static final Icon SETTINGS_ICON = IconLoader.getIcon("/actions/showSettings.png"); // 16x16
	public static final Icon GROOVY_FILE_TYPE_ICON = IconLoader.getIcon("/liveplugin/toolwindow/groovy_fileType.png");

	public static final FileType GROOVY_FILE_TYPE = FileTypeManager.getInstance().getFileTypeByExtension(".groovy");

	private static final Logger LOG = Logger.getInstance(IdeUtil.class);
	public static final DataContext DUMMY_DATA_CONTEXT = new DataContext() {
		@Nullable @Override public Object getData(@NonNls String dataId) {
			return null;
		}
	};

	public static ConsoleView displayInConsole(String consoleTitle, String text, ConsoleViewContentType contentType, Project project) {
		if (project == null) {
			LOG.warn("Failed to display console because project was 'null'. Text not shown in console: " + text);
			return null;
		}
		return PluginUtil.showInConsole(text, consoleTitle, project, contentType);
	}

	public static void showErrorDialog(Project project, String message, String title) {
		Messages.showMessageDialog(project, message, title, Messages.getErrorIcon());
	}

	public static void saveAllFiles() {
		ApplicationManager.getApplication().runWriteAction(new Runnable() {
			public void run() {
				FileDocumentManager.getInstance().saveAllDocuments();
			}
		});
	}

	public static void runAction(final AnAction action, String place) {
		final AnActionEvent event = new AnActionEvent(
				null,
				DUMMY_DATA_CONTEXT,
				place,
				action.getTemplatePresentation(),
				ActionManager.getInstance(),
				0
		);
		ApplicationManager.getApplication().invokeLater(new Runnable() {
			@Override public void run() {
				action.actionPerformed(event);
			}
		});
	}
}
