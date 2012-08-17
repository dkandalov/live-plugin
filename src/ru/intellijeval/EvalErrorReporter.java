package ru.intellijeval;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.unscramble.UnscrambleDialog;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static ru.intellijeval.Util.displayInConsole;

/**
 * User: dima
 * Date: 17/08/2012
 */
class EvalErrorReporter {
	private final List<String> loadingErrors = new LinkedList<String>();
	private final LinkedHashMap<String, Exception> evaluationExceptions = new LinkedHashMap<String, Exception>();

	public void addLoadingError(String pluginId, String message) {
		loadingErrors.add("Plugin: " + pluginId + ". " + message);
	}

	public void addEvaluationException(String pluginId, Exception e) {
		//noinspection ThrowableResultOfMethodCallIgnored
		evaluationExceptions.put(pluginId, e);
	}

	public void reportLoadingErrors(AnActionEvent actionEvent) {
		StringBuilder text = new StringBuilder();
		for (String s : loadingErrors) text.append(s);
		if (text.length() > 0)
			displayInConsole("Loading errors", text.toString(), ConsoleViewContentType.ERROR_OUTPUT, actionEvent.getData(PlatformDataKeys.PROJECT));
	}

	@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
	public void reportEvaluationExceptions(AnActionEvent actionEvent) {
		for (Map.Entry<String, Exception> entry : evaluationExceptions.entrySet()) {
			StringWriter writer = new StringWriter();
			entry.getValue().printStackTrace(new PrintWriter(writer));
			String s = UnscrambleDialog.normalizeText(writer.getBuffer().toString());

			displayInConsole(entry.getKey(), s, ConsoleViewContentType.ERROR_OUTPUT, actionEvent.getData(PlatformDataKeys.PROJECT));
		}
	}

}
