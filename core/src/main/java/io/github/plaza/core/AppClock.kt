package io.github.plaza.core

/**
 * Wall-clock time, injected rather than read from [System].
 *
 * Cache staleness decides whether opening a screen hits the network, so "is this row too old" is
 * real logic that has to be testable. A test that has to sleep for a real timeout is a test nobody
 * keeps.
 */
fun interface AppClock {
    fun nowMillis(): Long

    companion object {
        val System = AppClock { java.lang.System.currentTimeMillis() }
    }
}
