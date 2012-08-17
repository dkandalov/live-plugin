package ru.intellijeval;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import ru.intellijeval.toolwindow.PluginsToolWindow;

import java.util.List;

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
		PluginsToolWindow pluginsToolWindow = PluginsToolWindow.getInstance(event.getProject());
		List<String> pluginIds = pluginsToolWindow.selectedPluginIds();
		if (!pluginIds.isEmpty()) return pluginIds;

		// TODO implement
		return null;
	}
}
