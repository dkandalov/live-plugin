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
package liveplugin.pluginrunner;

import com.intellij.unscramble.UnscrambleDialog;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

class ErrorReporter {
	private final List<String> loadingErrors = new LinkedList<String>();
	private final LinkedHashMap<String, Exception> runningPluginExceptions = new LinkedHashMap<String, Exception>();
	private final LinkedHashMap<String, String> runningPluginErrors = new LinkedHashMap<String, String>();

	public void addLoadingError(String pluginId, String message) {
		loadingErrors.add("Error loading plugin: \"" + pluginId + "\". " + message);
	}

	public void addRunningPluginError(String pluginId, String message) {
		runningPluginErrors.put(pluginId, message);
	}

	public void addRunningPluginException(String pluginId, Exception e) {
		//noinspection ThrowableResultOfMethodCallIgnored
		runningPluginExceptions.put(pluginId, e);
	}

	public void reportAllErrors(Callback callback) {
		reportLoadingErrors(callback);
		reportRunningPluginExceptions(callback);
		reportRunningPluginErrors(callback);
	}

	private void reportLoadingErrors(Callback callback) {
		StringBuilder text = new StringBuilder();
		for (String s : loadingErrors) text.append(s);
		if (text.length() > 0) {
			callback.display("Loading errors", text.toString() + "\n");
		}
	}

	private void reportRunningPluginExceptions(Callback callback) {
		for (Map.Entry<String, Exception> entry : runningPluginExceptions.entrySet()) {
			StringWriter writer = new StringWriter();

			//noinspection ThrowableResultOfMethodCallIgnored
			entry.getValue().printStackTrace(new PrintWriter(writer));
			String s = UnscrambleDialog.normalizeText(writer.getBuffer().toString());

			callback.display(entry.getKey(), s);
		}
	}

	private void reportRunningPluginErrors(Callback callback) {
		for (Map.Entry<String, String> entry : runningPluginErrors.entrySet()) {
			callback.display(entry.getKey(), entry.getValue());
		}
	}

	public interface Callback {
		void display(String title, String message);
	}
}
