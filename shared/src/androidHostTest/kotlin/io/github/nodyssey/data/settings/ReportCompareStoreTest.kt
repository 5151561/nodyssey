package io.github.nodyssey.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.nodyssey.core.html.Fixtures
import io.github.nodyssey.core.report.QualityReport
import io.github.nodyssey.core.report.QualityReportParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** 测评对比's basket, on the real settings store: what it keeps, in what order, and past a restart. */
class ReportCompareStoreTest {
    private val directory = Files.createTempDirectory("nodyssey-settings").toFile().apply { deleteOnExit() }

    private fun store(scope: CoroutineScope): ReportCompareStore =
        SettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope) { File(directory, "settings.preferences_pb") },
        ).reportCompare

    private fun report(title: String) =
        QualityReport(
            title = title,
            target = null,
            generatedAt = null,
            scriptVersion = null,
            sections = emptyList(),
            footnotes = emptyList(),
        )

    private fun entry(
        postId: Long,
        floor: String? = "#1",
        report: QualityReport = report("report"),
    ) = ReportCompareEntry(report = report, postId = postId, threadTitle = "thread $postId", floor = floor)

    @Test
    fun `a full basket lets the oldest go and keeps the order they were added in`() =
        runTest {
            val store = store(backgroundScope)

            (1L..8L).forEach { store.add(entry(it)) }

            assertEquals((3L..8L).toList(), store.entries.first().map { it.postId })
        }

    @Test
    fun `adding a report already in the basket neither repeats nor moves it`() =
        runTest {
            val store = store(backgroundScope)

            store.add(entry(1))
            store.add(entry(2))
            store.add(entry(1))

            assertEquals(listOf(1L, 2L), store.entries.first().map { it.postId })
        }

    /** One floor often carries two reports, and one report title turns up on many floors. */
    @Test
    fun `another floor or another report in the same floor is an entry of its own`() =
        runTest {
            val store = store(backgroundScope)

            store.add(entry(1, floor = "#1", report = report("硬件质量体检报告")))
            store.add(entry(1, floor = "#1", report = report("IP质量体检报告")))
            store.add(entry(1, floor = "#2", report = report("硬件质量体检报告")))

            assertEquals(3, store.entries.first().size)

            store.remove(entry(1, floor = "#1", report = report("IP质量体检报告")))

            assertEquals(
                listOf("#1" to "硬件质量体检报告", "#2" to "硬件质量体检报告"),
                store.entries.first().map { it.floor to it.report.title },
            )
        }

    @Test
    fun `a parsed report survives a restart whole`() =
        runTest {
            val parsed = checkNotNull(QualityReportParser.parse(Fixtures.load("reports/nodequality-yabs.txt")))
            val firstRun = CoroutineScope(coroutineContext + Job())
            store(firstRun).add(entry(42, report = parsed))
            firstRun.coroutineContext[Job]!!.cancelAndJoin()

            val restored = store(backgroundScope).entries.first()

            assertEquals(listOf(entry(42, report = parsed)), restored)
        }
}
