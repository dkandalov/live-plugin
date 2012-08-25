package ru.intellijeval;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

import java.util.Map;

import static ru.intellijeval.Util.saveAllFiles;

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

	@Override public void update(AnActionEvent event) {
		event.getPresentation().setEnabled(!EvalComponent.pluginToPathMap().isEmpty());
	}

	private void evaluateAllPlugins(AnActionEvent event) {
		saveAllFiles();

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
