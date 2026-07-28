package io.github.nodyssey.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Dispatchers are injected rather than referenced statically so tests can run everything on a
 * single test scheduler. Nothing below the UI layer may touch [Dispatchers] directly.
 */
data class AppDispatchers(
    val io: CoroutineDispatcher = Dispatchers.IO,
    val default: CoroutineDispatcher = Dispatchers.Default,
)
