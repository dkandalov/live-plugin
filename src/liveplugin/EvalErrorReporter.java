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

/**
 * User: dima
 * Date: 17/08/2012
 */
class EvalErrorReporter {
	private final List<String> loadingErrors = new LinkedList<String>();
	private final LinkedHashMap<String, Exception> evaluationExceptions = new LinkedHashMap<String, Exception>();

	public void addLoadingError(String pluginId, String message) {
		loadingErrors.add("Error loading plugin: \"" + pluginId + "\". " + message);
	}

	public void addEvaluationException(String pluginId, Exception e) {
		//noinspection ThrowableResultOfMethodCallIgnored
		evaluationExceptions.put(pluginId, e);
	}

	public void reportLoadingErrors(AnActionEvent actionEvent) {
		StringBuilder text = new StringBuilder();
		for (String s : loadingErrors) text.append(s);
		if (text.length() > 0)
			Util.displayInConsole("Loading errors", text.toString() + "\n",
					ConsoleViewContentType.ERROR_OUTPUT, actionEvent.getData(PlatformDataKeys.PROJECT));
	}

	public void reportEvaluationExceptions(AnActionEvent actionEvent) {
		for (Map.Entry<String, Exception> entry : evaluationExceptions.entrySet()) {
			StringWriter writer = new StringWriter();

			//noinspection ThrowableResultOfMethodCallIgnored
			entry.getValue().printStackTrace(new PrintWriter(writer));
			String s = UnscrambleDialog.normalizeText(writer.getBuffer().toString());

			Util.displayInConsole(entry.getKey(), s, ConsoleViewContentType.ERROR_OUTPUT, actionEvent.getData(PlatformDataKeys.PROJECT));
		}
	}

}
