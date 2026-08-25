package io.github.plaza.core.crash

/**
 * The record of the app's last crash, kept on the device and nowhere else.
 *
 * This project ships no telemetry on purpose — no crash SDK, no analytics endpoint — which leaves a
 * gap: when a user says "它闪退了", neither side has anything to look at. This store is the
 * zero-telemetry answer to that gap. The crash is written to local storage at the moment it happens,
 * 关于 shows a 导出 row when a record exists, and the stack trace travels only if the user themselves
 * hands it over through the share sheet. Nothing leaves the device on its own.
 */
interface CrashReportStore {
    /** The most recent crash, or null when the app has not crashed since the last [clear]. */
    suspend fun latest(): CrashReport?

    /** Forgets the stored crash; the 导出 row disappears with it. */
    suspend fun clear()
}

data class CrashReport(
    val occurredAtMillis: Long,
    /** The version that crashed — not necessarily the one reading the record, after an update. */
    val versionName: String,
    /** The formatted report: device line, thread, stack trace. Already capped to a shareable size. */
    val text: String,
)

/**
 * The store for a platform where nothing writes crashes yet.
 *
 * iOS uses this today: capture there means `setUnhandledExceptionHook` plus something for the
 * NSException/signal paths Kotlin never sees, and neither is wired up. This object keeps the 关于
 * screen honest in the meantime — no record, no 导出 row — instead of pretending a capture exists.
 */
object NoCrashReports : CrashReportStore {
    override suspend fun latest(): CrashReport? = null

    override suspend fun clear() = Unit
}
