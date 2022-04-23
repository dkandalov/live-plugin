package liveplugin.implementation.common

import liveplugin.implementation.common.Result.Failure
import liveplugin.implementation.common.Result.Success

// This is pretty much a copy of https://github.com/fork-handles/forkhandles/tree/trunk/result4k

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
