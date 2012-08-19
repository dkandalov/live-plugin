package ru.intellijeval;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import ru.intellijeval.toolwindow.PluginToolWindowManager;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static ru.intellijeval.toolwindow.PluginToolWindowManager.PluginToolWindow;

/**
 * User: dima
 * Date: 16/08/2012
 */
public class EvaluatePluginAction extends AnAction {
	public EvaluatePluginAction() {
		super("Run plugin", "Run current plugin", Util.EVAL_ICON);
	}

	@Override public void actionPerformed(AnActionEvent event) {
		evalCurrentPlugin(event);
	}

	private void evalCurrentPlugin(AnActionEvent event) {
		FileDocumentManager.getInstance().saveAllDocuments();

		EvalErrorReporter errorReporter = new EvalErrorReporter();
		Evaluator evaluator = new Evaluator(errorReporter);

		List<String> pluginIds = findCurrentPluginId(event);
		for (String pluginId : pluginIds) {
			String path = EvalComponent.pluginToPathMap().get(pluginId);
			evaluator.doEval(pluginId, path, event);
		}

		errorReporter.reportLoadingErrors(event);
		errorReporter.reportEvaluationExceptions(event);
	}

	private List<String> findCurrentPluginId(AnActionEvent event) {
		List<String> pluginIds = findPluginsSelectedInToolWindow(event);
		if (!pluginIds.isEmpty()) return pluginIds;

		return findPluginFromCurrentlyOpenFile(event);
	}

	private List<String> findPluginsSelectedInToolWindow(AnActionEvent event) {
		PluginToolWindow pluginToolWindow = PluginToolWindowManager.getToolWindowFor(event.getProject());
		return pluginToolWindow.selectedPluginIds();
	}

	private List<String> findPluginFromCurrentlyOpenFile(AnActionEvent event) {
		Editor selectedTextEditor = FileEditorManager.getInstance(event.getProject()).getSelectedTextEditor();
		if (selectedTextEditor == null) return Collections.emptyList();

		VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(selectedTextEditor.getDocument());
		if (virtualFile == null) return Collections.emptyList();

		final File file = new File(virtualFile.getPath());
		Map.Entry<String, String> entry = ContainerUtil.find(EvalComponent.pluginToPathMap().entrySet(), new Condition<Map.Entry<String, String>>() {
			@Override
			public boolean value(Map.Entry<String, String> entry) {
				String pluginPath = entry.getValue();
				return FileUtil.isAncestor(new File(pluginPath), file, false);
			}
		});
		if (entry == null) return Collections.emptyList();
		return Collections.singletonList(entry.getKey());
	}
}
