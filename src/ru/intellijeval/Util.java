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
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;
import java.awt.*;

/**
 * @author DKandalov
 */
public class Util {
	public static final Icon ADD_PLUGIN_ICON = AllIcons.General.Add;
	public static final Icon DELETE_PLUGIN_ICON = AllIcons.General.Remove;
	public static final Icon REFRESH_PLUGIN_LIST_ICON = AllIcons.Actions.Sync;
	public static final Icon PLUGIN_ICON = AllIcons.Nodes.Plugin;
	public static final Icon EVAL_ICON = AllIcons.Actions.Execute;
	public static final Icon EVAL_ALL_ICON = AllIcons.Actions.RefreshUsages;
	public static final Icon EXPAND_ALL_ICON = AllIcons.Actions.Expandall;
	public static final Icon COLLAPSE_ALL_ICON = AllIcons.Actions.Collapseall;
	public static final Icon GROOVY_FILE_TYPE_ICON = IconLoader.getIcon("/ru/intellijeval/toolwindow/groovy_fileType.png");

	public static final FileType GROOVY_FILE_TYPE = FileTypeManager.getInstance().getFileTypeByExtension(".groovy");

	public static void displayInConsole(String header, String text, ConsoleViewContentType contentType, Project project) {
		ConsoleView console = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
		console.print(text, contentType);

		DefaultActionGroup toolbarActions = new DefaultActionGroup();
		JComponent consoleComponent = new MyConsolePanel(console, toolbarActions);
		RunContentDescriptor descriptor = new RunContentDescriptor(console, null, consoleComponent, header) {
			public boolean isContentReuseProhibited() {
				return true;
			}

			@Override public Icon getIcon() {
				return AllIcons.Nodes.Plugin;
			}
		};
		Executor executor = DefaultRunExecutor.getRunExecutorInstance();

		toolbarActions.add(new CloseAction(executor, descriptor, project));
		for (AnAction action : console.createConsoleActions()) {
			toolbarActions.add(action);
		}

		ExecutionManager.getInstance(project).getContentManager().showRunContent(executor, descriptor);
	}

	public static void saveAllFiles() {
		ApplicationManager.getApplication().runWriteAction(new Runnable() {
			public void run() {
				FileDocumentManager.getInstance().saveAllDocuments();
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
