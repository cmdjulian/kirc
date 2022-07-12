package oci.distribution.client.utils

fun <T, U> Result<T>.fold(block: (t: T) -> Result<U>): Result<U> = mapCatching { block(it).getOrThrow() }
