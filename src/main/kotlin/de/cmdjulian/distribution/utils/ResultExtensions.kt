package de.cmdjulian.distribution.utils

internal suspend fun <T, U> Result<T>.foldSuspend(block: suspend (t: T) -> Result<U>): Result<U> =
    mapCatching { block(it).getOrThrow() }

internal fun <T> List<Result<T>>.zip(): Result<List<T>> = this.runCatching { map(Result<T>::getOrThrow) }
