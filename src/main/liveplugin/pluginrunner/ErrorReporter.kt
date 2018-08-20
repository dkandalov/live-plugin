package liveplugin.pluginrunner

import liveplugin.IdeUtil.unscrambleThrowable
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
        runningPluginErrors[pluginId] = message
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

    @Synchronized fun reportAllErrors(callback: (title: String, message: String) -> Unit) {
        reportAllErrors(object: Callback {
            override fun display(title: String, message: String) {
                callback(title, message)
            }
        })
    }

    private fun reportLoadingErrors(callback: Callback) {
        if (loadingErrors.isNotEmpty()) {
            val text = loadingErrors.joinToString("\n") + "\n"
            callback.display("Loading errors", text)
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
