package com.sledje.agentpro.core.data

/**
 * The outcome of a repository call (docs/ARCHITECTURE.md, Patterns: `Result<T, AppError>`). Importing
 * this type shadows `kotlin.Result` in that file, which is intended: repositories never throw.
 */
sealed interface Result<out T, out E> {
    data class Ok<out T>(val value: T) : Result<T, Nothing>
    data class Err<out E>(val error: E) : Result<Nothing, E>
}

/** A repository result whose failure is an [AppError]. */
typealias AppResult<T> = Result<T, AppError>

val Result<*, *>.isOk: Boolean get() = this is Result.Ok

fun <T> Result<T, *>.getOrNull(): T? = (this as? Result.Ok)?.value

fun <E> Result<*, E>.errorOrNull(): E? = (this as? Result.Err)?.error

inline fun <T, E, R> Result<T, E>.map(transform: (T) -> R): Result<R, E> = when (this) {
    is Result.Ok -> Result.Ok(transform(value))
    is Result.Err -> this
}

inline fun <T, E, R> Result<T, E>.flatMap(transform: (T) -> Result<R, E>): Result<R, E> = when (this) {
    is Result.Ok -> transform(value)
    is Result.Err -> this
}

inline fun <T, E> Result<T, E>.onOk(action: (T) -> Unit): Result<T, E> = also { if (it is Result.Ok) action(it.value) }

inline fun <T, E> Result<T, E>.onErr(action: (E) -> Unit): Result<T, E> = also { if (it is Result.Err) action(it.error) }
