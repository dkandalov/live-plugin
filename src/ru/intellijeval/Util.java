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
package ru.intellijeval;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.openapi.actionSystem.*;
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
import java.awt.*;

/**
 * @author DKandalov
 */
public class Util {
	public static final Icon ADD_PLUGIN_ICON = IconLoader.getIcon("/general/add.png"); // 16x16
	public static final Icon DELETE_PLUGIN_ICON = IconLoader.getIcon("/general/remove.png"); // 16x16
	public static final Icon REFRESH_PLUGIN_LIST_ICON = IconLoader.getIcon("/actions/sync.png"); // 16x16
	public static final Icon PLUGIN_ICON = IconLoader.getIcon("/nodes/plugin.png"); // 16x16
	public static final Icon EVAL_ICON = IconLoader.getIcon("/actions/execute.png"); // 16x16
	public static final Icon EVAL_ALL_ICON = IconLoader.getIcon("/actions/refreshUsages.png"); // 16x16
	public static final Icon EXPAND_ALL_ICON = IconLoader.getIcon("/actions/expandall.png"); // 16x16
	public static final Icon COLLAPSE_ALL_ICON = IconLoader.getIcon("/actions/collapseall.png"); // 16x16
	public static final Icon SETTINGS_ICON = IconLoader.getIcon("/actions/showSettings.png"); // 16x16
	public static final Icon GROOVY_FILE_TYPE_ICON = IconLoader.getIcon("/ru/intellijeval/toolwindow/groovy_fileType.png");

	public static final FileType GROOVY_FILE_TYPE = FileTypeManager.getInstance().getFileTypeByExtension(".groovy");

	private static final Logger LOG = Logger.getInstance(Util.class);
	private static final DataContext DUMMY_DATA_CONTEXT = new DataContext() {
		@Nullable @Override public Object getData(@NonNls String dataId) {
			return null;
		}
	};

	public static void displayInConsole(String consoleTitle, String text, ConsoleViewContentType contentType, Project project) {
		if (project == null) {
			LOG.warn("Failed to display console because project was 'null'. Text not shown in console: " + text);
			return;
		}

		ConsoleView console = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
		console.print(text, contentType);

		DefaultActionGroup toolbarActions = new DefaultActionGroup();
		JComponent consoleComponent = new MyConsolePanel(console, toolbarActions);
		RunContentDescriptor descriptor = new RunContentDescriptor(console, null, consoleComponent, consoleTitle) {
			@Override public boolean isContentReuseProhibited() {
				return true;
			}

			@Override public Icon getIcon() {
				return PLUGIN_ICON;
			}
		};
		Executor executor = DefaultRunExecutor.getRunExecutorInstance();

		toolbarActions.add(new CloseAction(executor, descriptor, project));
		for (AnAction action : console.createConsoleActions()) {
			toolbarActions.add(action);
		}

		ExecutionManager.getInstance(project).getContentManager().showRunContent(executor, descriptor);
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

	private static final class MyConsolePanel extends JPanel {
		public MyConsolePanel(ExecutionConsole consoleView, ActionGroup toolbarActions) {
			super(new BorderLayout());
			JPanel toolbarPanel = new JPanel(new BorderLayout());
			toolbarPanel.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions, false).getComponent());
			add(toolbarPanel, BorderLayout.WEST);
			add(consoleView.getComponent(), BorderLayout.CENTER);
		}
	}
}
