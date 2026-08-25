package io.github.nodyssey

import io.github.plaza.core.crash.writeCrashRecord
import java.io.File

/**
 * Writes the crash record 关于 › 导出崩溃日志 later reads, then gets out of the way.
 *
 * Delegating to [next] — the handler that was installed before this one — is the half that matters:
 * that chain ends at the system's own handler, which is what shows the crash dialog and kills the
 * process. A handler that recorded the crash and swallowed it would leave the app frozen in
 * whatever broken state it crashed in, which is worse than crashing.
 */
class NodysseyCrashHandler(
    private val directory: File,
    private val versionName: String,
    private val next: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, error: Throwable) {
        writeCrashRecord(directory, versionName, System.currentTimeMillis(), thread, error)
        next?.uncaughtException(thread, error)
    }
}
