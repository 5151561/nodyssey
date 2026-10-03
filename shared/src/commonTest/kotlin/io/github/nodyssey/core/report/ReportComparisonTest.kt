package io.github.nodyssey.core.report

import io.github.nodyssey.core.html.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 测评对比's alignment, driven by the real reports the parser tests use. */
class ReportComparisonTest {

    private fun load(name: String): String = Fixtures.load("reports/$name")

    private fun parse(text: String): QualityReport = checkNotNull(QualityReportParser.parse(text))

    private val hardware = parse(load("hardware-quality.txt"))
    private val yabs = parse(load("nodequality-yabs.txt"))

    /** The hardware report as a machine whose script printed no `缓存：` line. */
    private val hardwareWithoutCache =
        parse(load("hardware-quality.txt").lines().filterNot { it.startsWith("缓存") }.joinToString("\n"))

    private fun ReportComparison.section(title: String) =
        checkNotNull(sections.firstOrNull { it.title == title }) { "no section $title in ${sections.map { it.title }}" }

    private fun ReportComparison.Section.row(label: String) =
        checkNotNull(rows.firstOrNull { it.label == label }) { "no row $label in ${rows.map { it.label }}" }

    private fun ReportComparison.Section.rowsLabelled(label: String) = rows.filter { it.label == label }

    private fun List<QualityReport.Value>?.texts() = this?.map { it.text }

    @Test
    fun `keeps a label printed under two sections apart`() {
        val comparison = ReportComparison.of(listOf(hardware, hardware))

        val cpu = comparison.section("CPU测评").row("Sysbench")
        val memory = comparison.section("内存测评").row("Sysbench")

        assertNotEquals(cpu.cells[0].texts(), memory.cells[0].texts())
        assertEquals(memory.cells[0].texts(), memory.cells[1].texts())
        assertTrue(memory.cells[0].texts().orEmpty().any { "43989.9" in it }, "memory row holds ${memory.cells[0].texts()}")
    }

    @Test
    fun `leaves the report without a row empty and keeps the row in its place`() {
        val comparison = ReportComparison.of(listOf(hardware, hardwareWithoutCache))
        val cpu = comparison.section("CPU测评")

        val cache = cpu.row("缓存")
        assertNotNull(cache.cells[0])
        assertNull(cache.cells[1])
        // Every other CPU row lines up value for value.
        cpu.rows.filter { it.label != "缓存" }.forEach { row ->
            assertEquals(row.cells[0].texts(), row.cells[1].texts(), "row ${row.label}")
        }

        // Missing from the first report this time: the row still goes after CPU, not at the foot.
        val reversed = ReportComparison.of(listOf(hardwareWithoutCache, hardware)).section("CPU测评")
        assertEquals(listOf("CPU", "缓存", "指令集"), reversed.rows.map { it.label }.take(3))
        assertNull(reversed.row("缓存").cells[0])
    }

    @Test
    fun `sets a yabs report beside an xykt one with every row from both`() {
        val comparison = ReportComparison.of(listOf(yabs, hardware))

        assertEquals(2, comparison.reportCount)
        assertTrue(comparison.sections.flatMap { it.rows }.all { it.cells.size == 2 })

        val processor = comparison.section("Basic System Information").row("Processor")
        assertNull(processor.cells[1])
        assertEquals(listOf("Intel Xeon Processor (SierraForest)"), processor.cells[0].texts())

        val cpu = comparison.section("CPU测评").row("CPU")
        assertNull(cpu.cells[0])
        assertNotNull(cpu.cells[1])

        // The yabs sections come first because yabs was passed first.
        assertEquals("Basic System Information", comparison.sections.first().title)
    }

    @Test
    fun `gives each table cell a row of its own`() {
        val disk = ReportComparison.of(listOf(yabs, yabs)).section("fio Disk Speed Tests (Mixed R/W 50/50) (Partition -)")

        // fio's two tables share row labels; the column is what keeps 4k and 512k apart.
        val reads = disk.rows.filter { it.label.startsWith("Read · ") }
        assertEquals(4, reads.size)
        assertEquals(reads.size, reads.map { it.label }.distinct().size)
        assertTrue(reads.first().cells[0].texts().orEmpty().single().startsWith("74.48 MB/s"))
    }

    @Test
    fun `pairs a row label a report prints twice by occurrence`() {
        val network = ReportComparison.of(listOf(yabs, yabs)).section("iperf3 Network Speed Tests (IPv4)")

        // iperf3 lists Clouvider for London and again for Los Angeles.
        val sends = network.rowsLabelled("Clouvider · Send Speed")
        assertEquals(listOf(listOf("872 Mbits/sec"), listOf("1.12 Gbits/sec")), sends.map { it.cells[0].texts() })
        sends.forEach { assertEquals(it.cells[0].texts(), it.cells[1].texts()) }
    }
}
