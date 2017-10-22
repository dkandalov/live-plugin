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
package liveplugin.pluginrunner

import liveplugin.IDEUtil.unscrambleThrowable
import java.util.*

/**
 * Thread-safe.
 */
class ErrorReporter {
    private val loadingErrors = LinkedList<String>()
    private val runningPluginErrors = LinkedHashMap<String, String>()

    @Synchronized fun addNoScriptError(pluginId: String, scriptNames: List<String>) {
        val scripts = scriptNames.joinToString(", ") { "\"$it\"" }
        loadingErrors.add("Plugin: \"$pluginId\". Startup script was not found. Tried: $scripts")
    }

    @Synchronized fun addLoadingError(pluginId: String, message: String) {
        loadingErrors.add("Couldn't load plugin: \"$pluginId\". $message")
    }

    @Synchronized fun addLoadingError(pluginId: String, e: Throwable) {
        addLoadingError(pluginId, unscrambleThrowable(e))
    }

    @Synchronized fun addRunningError(pluginId: String, message: String) {
        runningPluginErrors.put(pluginId, message)
    }

    @Synchronized fun addRunningError(pluginId: String, e: Throwable) {
        addRunningError(pluginId, unscrambleThrowable(e))
    }

    @Synchronized fun reportAllErrors(callback: Callback) {
        reportLoadingErrors(callback)
        reportRunningPluginErrors(callback)
        loadingErrors.clear()
        runningPluginErrors.clear()
    }

    private fun reportLoadingErrors(callback: Callback) {
        val text = StringBuilder()
        for (s in loadingErrors) text.append(s)
        if (text.isNotEmpty()) {
            callback.display("Loading errors", text.toString() + "\n")
        }
    }

    private fun reportRunningPluginErrors(callback: Callback) {
        for ((key, value) in runningPluginErrors) {
            callback.display(key, value)
        }
    }

    interface Callback {
        fun display(title: String, message: String)
    }
}
