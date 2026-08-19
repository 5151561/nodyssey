package io.github.plaza.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

// `NSDate` rather than `kotlin.system.getTimeMillis()`: the same wall clock the rest of an Apple
// process reads, and it moves with the system's idea of the time rather than with uptime.
internal actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1_000).toLong()

// `Default`, not `IO`, and not by preference: `Dispatchers.IO` is `internal` in the Native build of
// kotlinx-coroutines 1.11 — the JVM is the only target where it is public. What makes the
// substitution safe *today* is that nothing on this side blocks a thread: `NSURLSession` is
// callback-driven, so a request occupies no dispatcher while it is in flight. That stops being true
// the moment a blocking store arrives on these targets — SQLite through Room is the one to expect —
// and the fix then is a pool of this module's own, not this line.
internal actual fun ioDispatcher(): CoroutineDispatcher = Dispatchers.Default
