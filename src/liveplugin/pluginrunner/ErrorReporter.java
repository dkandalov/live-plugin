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

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static liveplugin.IdeUtil.unscrambleThrowable;

class ErrorReporter {
	private final List<String> loadingErrors = new LinkedList<String>();
	private final LinkedHashMap<String, String> runningPluginErrors = new LinkedHashMap<String, String>();

	public void addLoadingError(String pluginId, String message) {
		loadingErrors.add("Error loading plugin: \"" + pluginId + "\". " + message);
	}

	public void addLoadingError(String pluginId, Throwable e) {
		addLoadingError(pluginId, unscrambleThrowable(e));
	}

	public void addRunningError(String pluginId, String message) {
		runningPluginErrors.put(pluginId, message);
	}

	public void addRunningError(String pluginId, Throwable e) {
		addRunningError(pluginId, unscrambleThrowable(e));
	}

	public void reportAllErrors(Callback callback) {
		reportLoadingErrors(callback);
		reportRunningPluginErrors(callback);
	}

	private void reportLoadingErrors(Callback callback) {
		StringBuilder text = new StringBuilder();
		for (String s : loadingErrors) text.append(s);
		if (text.length() > 0) {
			callback.display("Loading errors", text.toString() + "\n");
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
