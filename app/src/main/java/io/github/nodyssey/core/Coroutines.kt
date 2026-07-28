package io.github.nodyssey.core

import kotlinx.coroutines.CancellationException

/**
 * Like `runCatching`, but never swallows [CancellationException].
 *
 * `runCatching` catches `Throwable`, and cancelling a coroutine *is* a throwable. Wrapping a
 * suspend call in plain `runCatching` therefore turns "the user navigated away" into "the request
 * failed", which then gets rendered as an error the user never caused. Structured concurrency
 * requires cancellation to keep propagating.
 */
inline fun <T> runCatchingExceptCancellation(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
