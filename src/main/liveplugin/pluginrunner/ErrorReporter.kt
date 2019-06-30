package liveplugin.pluginrunner

import liveplugin.IdeUtil.unscrambleThrowable
import liveplugin.pluginrunner.Result.*
import java.util.*

sealed class Result<out Value, out Reason> {
    class Success<out Value>(val value: Value) : Result<Value, Nothing>()
    class Failure<out Reason>(val reason: Reason) : Result<Nothing, Reason>()
}

inline fun <T, E> Result<T, E>.onFailure(block: (Failure<E>) -> Nothing): T =
    when (this) {
        is Success<T> -> value
        is Failure<E> -> block(this)
    }

inline fun <T, E> Result<T, E>.peekFailure(f: (E) -> Unit) =
    apply { if (this is Failure<E>) f(reason) }

fun <T, E> Iterable<Result<T, E>>.allValues(): Result<List<T>, E> =
    Success(map { r -> r.onFailure { return it } })

inline fun <T, Tʹ, E> Result<T, E>.flatMap(f: (T) -> Result<Tʹ, E>): Result<Tʹ, E> =
    when (this) {
        is Success<T> -> f(value)
        is Failure<E> -> this
    }

inline fun <T, Tʹ, E> Result<T, E>.map(f: (T) -> Tʹ): Result<Tʹ, E> =
    flatMap { value -> Success(f(value)) }


sealed class AnError {
    data class LoadingError(val pluginId: String, val message: String = "", val throwable: Throwable? = null): AnError()
    data class RunningError(val pluginId: String, val throwable: Throwable): AnError()
}

/**
 * Thread-safe.
 */
data class ErrorReporter(
    private val loadingErrors: LinkedList<String> = LinkedList(),
    private val runningPluginErrors: LinkedHashMap<String, String> = LinkedHashMap()
) {

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
