package io.github.plaza.core.net

import io.github.plaza.core.AppClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps consecutive calls at least [minIntervalMillis] apart.
 *
 * A forum that publishes a throttle — one request per two seconds, say — answers a burst with 429,
 * and answering a 429 with a retry is how that burst becomes a Cloudflare challenge. Waiting the
 * difference out is both cheaper and the behaviour the site asked for.
 *
 * The lock is held only while the interval is measured and stamped, not for the call itself, so a
 * slow request never blocks the next one beyond the interval it owes. The stamp is taken at
 * dispatch because that is what the server times between.
 */
class MinIntervalGate(
    private val minIntervalMillis: Long,
    private val clock: AppClock,
) {
    private val mutex = Mutex()
    private var lastStartedAtMillis = Long.MIN_VALUE

    suspend fun <T> spaced(block: suspend () -> T): T {
        mutex.withLock {
            val owed = lastStartedAtMillis + minIntervalMillis - clock.nowMillis()
            // A clock that jumped backwards would otherwise park the caller for as long as the jump.
            if (owed > 0) delay(owed.coerceAtMost(minIntervalMillis))
            lastStartedAtMillis = clock.nowMillis()
        }
        return block()
    }
}
