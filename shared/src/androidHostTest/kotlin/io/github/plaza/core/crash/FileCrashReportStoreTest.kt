package io.github.plaza.core.crash

import io.github.plaza.core.AppDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The writer runs in a dying process and the reader in a healthy one; the file is their contract. */
@RunWith(RobolectricTestRunner::class)
class FileCrashReportStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dispatchers =
        AppDispatchers(io = Dispatchers.Unconfined, default = Dispatchers.Unconfined)

    @Test
    fun `what the handler writes is what the store reads`() = runTest {
        val directory = folder.newFolder("crash")
        val error = IllegalStateException("the reason it died")

        writeCrashRecord(directory, "1.3.0", 1_756_000_000_000, Thread.currentThread(), error)
        val report = FileCrashReportStore(directory, dispatchers).latest()

        requireNotNull(report)
        assertEquals(1_756_000_000_000, report.occurredAtMillis)
        assertEquals("1.3.0", report.versionName)
        assertEquals(true, "the reason it died" in report.text)
        assertEquals(true, "FileCrashReportStoreTest" in report.text)
    }

    @Test
    fun `no crash means no report, and clearing forgets the last one`() = runTest {
        val directory = folder.newFolder("crash")
        val store = FileCrashReportStore(directory, dispatchers)

        assertNull(store.latest())

        writeCrashRecord(directory, "1.3.0", 1L, Thread.currentThread(), RuntimeException("x"))
        requireNotNull(store.latest())

        store.clear()
        assertNull(store.latest())
    }

    /** A record from a directory that never existed, or a torn write, reads as no record — not a crash. */
    @Test
    fun `an unreadable record is silently no record`() = runTest {
        val directory = folder.newFolder("crash")
        val store = FileCrashReportStore(directory, dispatchers)

        crashRecordFile(directory).writeText("not-a-timestamp\n1.3.0\ntext")
        assertNull(store.latest())

        crashRecordFile(directory).writeText("only one line")
        assertNull(store.latest())
    }

    /** A crash inside a crash must not replace the crash — the writer swallows its own failures. */
    @Test
    fun `writing into an impossible directory does not throw`() {
        val blocked = folder.newFile("a-plain-file")

        writeCrashRecord(blocked, "1.3.0", 1L, Thread.currentThread(), RuntimeException("x"))
    }
}
