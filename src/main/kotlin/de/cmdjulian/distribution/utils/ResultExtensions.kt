package de.cmdjulian.distribution.utils

fun <T, U> Result<T>.fold(block: (t: T) -> Result<U>): Result<U> = mapCatching { block(it).getOrThrow() }

suspend fun <T, U> Result<T>.foldSuspend(block: suspend (t: T) -> Result<U>): Result<U> =
    mapCatching { block(it).getOrThrow() }

fun <T> List<Result<T>>.zip(): Result<List<T>> = this.runCatching { map(Result<T>::getOrThrow) }
