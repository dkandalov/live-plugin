package ru.intellijeval;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;

import java.util.Map;

/**
 * @author DKandalov
 */
public class EvaluateAllPluginsAction extends AnAction {

	public EvaluateAllPluginsAction() {
		super("Run all plugins", "Run all plugins", Util.EVAL_ALL_ICON);
	}

	@Override public void actionPerformed(AnActionEvent event) {
		evaluateAllPlugins(event);
	}

	private void evaluateAllPlugins(AnActionEvent event) {
		FileDocumentManager.getInstance().saveAllDocuments();

		EvalErrorReporter errorReporter = new EvalErrorReporter();
		Evaluator evaluator = new Evaluator(errorReporter);

		for (Map.Entry<String, String> entry : EvalComponent.pluginToPathMap().entrySet()) {
			String pluginId = entry.getKey();
			String path = entry.getValue();
			evaluator.doEval(pluginId, path, event);
		}

		errorReporter.reportLoadingErrors(event);
		errorReporter.reportEvaluationExceptions(event);
	}
}
