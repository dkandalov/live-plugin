package ru.intellijeval;

import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;

import javax.swing.*;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.unscramble.AnalyzeStacktraceUtil;
import com.intellij.unscramble.ThreadState;
import com.intellij.unscramble.UnscrambleDialog;

/**
 * @author DKandalov
 */
public class Util {

	public static void showInUnscrambleDialog(Exception e, Project project) {
		StringWriter writer = new StringWriter();
		e.printStackTrace(new PrintWriter(writer));
		String s = UnscrambleDialog.normalizeText(writer.getBuffer().toString());
		ConsoleView console = UnscrambleDialog.addConsole(project, Collections.<ThreadState>emptyList());
		AnalyzeStacktraceUtil.printStacktrace(console, s);
	}

	public static void displayInConsole(String header, String text, ConsoleViewContentType contentType, Project project) {
		ConsoleView console = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
		console.print(text, contentType);

		DefaultActionGroup toolbarActions = new DefaultActionGroup();
		JComponent consoleComponent = new MyConsolePanel(console, toolbarActions);
		RunContentDescriptor descriptor = new RunContentDescriptor(console, null, consoleComponent, header) {
			public boolean isContentReuseProhibited() {
				return true;
			}
		};
		Executor executor = DefaultRunExecutor.getRunExecutorInstance();

		toolbarActions.add(new CloseAction(executor, descriptor, project));
		for (AnAction action : console.createConsoleActions()) {
			toolbarActions.add(action);
		}

		ExecutionManager.getInstance(project).getContentManager().showRunContent(executor, descriptor);
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
