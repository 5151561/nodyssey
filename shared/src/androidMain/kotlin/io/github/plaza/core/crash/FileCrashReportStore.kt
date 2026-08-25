package io.github.plaza.core.crash

import android.os.Build
import io.github.plaza.core.AppDispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [CrashReportStore] as one small file under the app's private storage.
 *
 * A file rather than DataStore or Room because of *when* the write happens: inside an uncaught
 * exception handler, on whatever thread just died, with the process about to be killed. That moment
 * cannot await a coroutine, must not touch a database whose executor may be the thing that crashed,
 * and gets exactly one attempt — plain blocking `java.io` is the only tool that fits it. The read
 * side is ordinary and suspends like everything else.
 */
class FileCrashReportStore(
    private val directory: File,
    private val dispatchers: AppDispatchers,
) : CrashReportStore {
    override suspend fun latest(): CrashReport? =
        withContext(dispatchers.io) { readCrashRecord(directory) }

    override suspend fun clear() {
        withContext(dispatchers.io) { crashRecordFile(directory).delete() }
    }
}

/**
 * Writes the record a future [FileCrashReportStore.latest] will read. Called from the crash handler.
 *
 * Swallows its own failures, which is banned everywhere else in this codebase — but this runs while
 * the process is dying, and the alternative to swallowing is an exception inside the exception
 * handler, which silently eats the *original* crash and the system's own reporting with it. Losing
 * the record is the acceptable failure; losing the crash is not.
 *
 * The device line is written here rather than at read time because it has to describe the device as
 * it was when it crashed — an OS update between crash and export would otherwise misreport it.
 */
fun writeCrashRecord(
    directory: File,
    versionName: String,
    occurredAtMillis: Long,
    thread: Thread,
    error: Throwable,
) {
    try {
        directory.mkdirs()
        val header =
            "Nodyssey $versionName · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" +
                " · ${Build.MANUFACTURER} ${Build.MODEL}\nThread: ${thread.name}\n\n"
        val text = (header + error.stackTraceToString()).take(MAX_REPORT_CHARS)
        crashRecordFile(directory).writeText("$occurredAtMillis\n$versionName\n$text")
    } catch (_: Exception) {
        // Nothing to do and nowhere to report it; see the KDoc.
    }
}

internal fun readCrashRecord(directory: File): CrashReport? {
    val file = crashRecordFile(directory)
    if (!file.isFile) return null
    val lines = file.readText().split('\n', limit = 3)
    if (lines.size < 3) return null
    val occurredAt = lines[0].toLongOrNull() ?: return null
    return CrashReport(
        occurredAtMillis = occurredAt,
        versionName = lines[1],
        text = lines[2],
    )
}

internal fun crashRecordFile(directory: File): File = File(directory, "last-crash.txt")

/**
 * Roughly 120KB of UTF-16 — well under the 1MB binder ceiling the share sheet's intent has to fit
 * inside, with room to spare for the rest of the transaction. A stack trace that long has said
 * everything it is going to say.
 */
private const val MAX_REPORT_CHARS = 120_000
