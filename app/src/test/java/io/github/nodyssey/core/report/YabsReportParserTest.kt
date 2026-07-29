package io.github.nodyssey.core.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The other report family: NodeQuality's own header block, which is yabs output.
 *
 * Shapes taken from `part/header.sh` and `part/yabs.sh` in LloydAsp/NodeQuality — a banner with no
 * report name in it, headings underlined with dashes, `Label     : value` on a half-width colon, and
 * pipe-delimited tables with a dashed separator row.
 */
class YabsReportParserTest {

    private val report =
        checkNotNull(
            QualityReportParser.parse(
                checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/reports/nodequality-yabs.txt"))
                    .bufferedReader()
                    .readText(),
            ),
        )

    private fun section(title: String) =
        checkNotNull(report.sections.firstOrNull { it.title == title }) {
            "no section $title in ${report.sections.map { it.title }}"
        }

    private fun QualityReport.Section.field(label: String) =
        checkNotNull(blocks.filterIsInstance<QualityReport.Block.Field>().firstOrNull { it.label == label })

    /** This banner names no report, so the project that produced it is the only name available. */
    @Test
    fun `names the report after the project when the banner does not`() {
        assertEquals("NodeQuality", report.title)
        assertEquals(null, report.target)
        assertEquals("2025-03-24 10:39:57 CST", report.generatedAt)
        assertEquals("v0.0.1", report.scriptVersion)
    }

    @Test
    fun `takes a heading from the dashes drawn under it`() {
        assertEquals(
            listOf(
                "Basic System Information",
                "IPv4 Network Information",
                "fio Disk Speed Tests (Mixed R/W 50/50) (Partition -)",
                "iperf3 Network Speed Tests (IPv4)",
            ),
            report.sections.map { it.title },
        )
    }

    @Test
    fun `reads a label written with a half-width colon`() {
        assertEquals(listOf("Intel Xeon Processor (SierraForest)"), section("Basic System Information").field("Processor").values)
        assertEquals(listOf("2 @ 2699.998 MHz"), section("Basic System Information").field("CPU cores").values)
    }

    /** A colon inside a value must not be mistaken for the label's own. */
    @Test
    fun `does not cut a value at a colon of its own`() {
        assertEquals(listOf("AS25820 IT7 Networks Inc"), section("IPv4 Network Information").field("ASN").values)
    }

    @Test
    fun `reads the cross mark yabs uses`() {
        val basics = section("Basic System Information")
        val aes = basics.blocks.filterIsInstance<QualityReport.Block.Badges>().first { it.label == "AES-NI" }

        assertEquals(listOf("Enabled"), aes.items.map { it.text })
        assertTrue(aes.items.single().passed)

        val virt = basics.blocks.filterIsInstance<QualityReport.Block.Badges>().first { it.label == "VM-x/AMD-V" }
        assertTrue(!virt.items.single().passed)
    }

    /** yabs writes two verdicts into one field where xykt would have padded them apart. */
    @Test
    fun `splits a field holding two verdicts`() {
        val stack =
            section("Basic System Information")
                .blocks
                .filterIsInstance<QualityReport.Block.Badges>()
                .first { it.label == "IPv4/IPv6" }

        assertEquals(listOf("Online", "Offline"), stack.items.map { it.text })
        assertEquals(listOf(true, false), stack.items.map { it.passed })
    }

    @Test
    fun `reads a pipe-delimited table`() {
        val table = section("iperf3 Network Speed Tests (IPv4)").blocks.filterIsInstance<QualityReport.Block.Table>().single()

        assertEquals(listOf("Location (Link)", "Send Speed", "Recv Speed", "Ping"), table.columns)
        assertEquals(6, table.rows.size)
        assertEquals("Clouvider", table.rows.first().label)
        assertEquals(listOf("London, UK (10G)", "872 Mbits/sec", "920 Mbits/sec", "186 ms"), table.rows.first().cells)
    }

    /**
     * fio prints two block-size tables into one section, parted by a row of empty cells.
     *
     * Both have to survive as tables of their own, or the second one's numbers end up filed under
     * the first one's block sizes.
     */
    @Test
    fun `reads both of the tables fio puts in one section`() {
        val tables = section("fio Disk Speed Tests (Mixed R/W 50/50) (Partition -)")
            .blocks
            .filterIsInstance<QualityReport.Block.Table>()

        assertEquals(2, tables.size)
        assertEquals(listOf("Read", "Write", "Total"), tables[0].rows.map { it.label })
        assertEquals(listOf("Read", "Write", "Total"), tables[1].rows.map { it.label })
        assertTrue(tables[0].columns.first().startsWith("4k"))
        assertTrue(tables[1].columns.first().startsWith("512k"))

        // Nothing drawn survives as a row of its own.
        val labels = tables.flatMap { it.rows }.map { it.label }
        assertTrue("separator survived: $labels", labels.none { it.isEmpty() || it.all { c -> c == '-' } })
    }
}
