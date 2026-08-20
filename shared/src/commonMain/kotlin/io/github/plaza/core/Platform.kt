package io.github.plaza.core

import kotlinx.coroutines.CoroutineDispatcher

/**
 * The two things below the app that every target answers differently and nothing else does.
 *
 * Both used to be a call into `java.*` sitting inside an otherwise neutral file — `System
 * .currentTimeMillis()` in [AppClock], `Dispatchers.IO` as [AppDispatchers]' default. Neither is
 * reachable from `commonMain`: the clock because the JVM's is a JVM class, the dispatcher because
 * `kotlinx.coroutines` declares `IO` per platform rather than in its common surface.
 *
 * `internal` on purpose. What a consumer injects is still an [AppClock] and an [AppDispatchers]; these
 * are only what those two default to when nobody says otherwise.
 */
internal expect fun currentTimeMillis(): Long

/**
 * The dispatcher for blocking work — a request, a file, a database.
 *
 * `Dispatchers.IO` on every target this module builds for; it is the *declaration* that is not
 * common, not the concept.
 */
internal expect fun ioDispatcher(): CoroutineDispatcher
