package liveplugin.pluginrunner

import liveplugin.pluginrunner.Result.Failure
import liveplugin.pluginrunner.Result.Success

sealed class Result<out Value, out Reason> {
    data class Success<out Value>(val value: Value) : Result<Value, Nothing>()
    data class Failure<out Reason>(val reason: Reason) : Result<Nothing, Reason>()
}

fun <T> T.asSuccess() = Success(this)
fun <T> T.asFailure() = Failure(this)

inline fun <T, E> Result<T, E>.onFailure(block: (Failure<E>) -> Nothing): T =
    when (this) {
        is Success<T> -> value
        is Failure<E> -> block(this)
    }

inline fun <T, E> Result<T, E>.peekFailure(f: (E) -> Unit) =
    apply { if (this is Failure<E>) f(reason) }

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
