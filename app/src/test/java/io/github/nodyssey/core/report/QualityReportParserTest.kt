package io.github.nodyssey.core.report

import io.github.nodyssey.core.html.AnsiParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Driven by the two reports from post 845099, captured verbatim after ANSI decoding.
 *
 * They are the real thing rather than a reduction because every hard case here — a row that is short
 * one cell, a header padded differently from its own body, arithmetic connectors mixed in with data
 * — is something the scripts do that nobody would think to invent.
 */
class QualityReportParserTest {

    private fun load(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/reports/$name"))
            .bufferedReader()
            .readText()

    private val hardware = checkNotNull(QualityReportParser.parse(load("hardware-quality.txt")))
    private val ip = checkNotNull(QualityReportParser.parse(load("ip-quality.txt")))

    private fun QualityReport.section(title: String) =
        checkNotNull(sections.firstOrNull { it.title == title }) { "no section $title in ${sections.map { it.title }}" }

    private fun QualityReport.Section.field(label: String) =
        checkNotNull(blocks.filterIsInstance<QualityReport.Block.Field>().firstOrNull { it.label == label })

    private fun QualityReport.Section.table() =
        checkNotNull(blocks.filterIsInstance<QualityReport.Block.Table>().firstOrNull())

    private fun QualityReport.Section.badges(label: String) =
        checkNotNull(blocks.filterIsInstance<QualityReport.Block.Badges>().firstOrNull { it.label == label })

    // --- Banner -------------------------------------------------------------

    @Test
    fun `reads the banner`() {
        assertEquals("硬件质量体检报告", hardware.title)
        assertEquals("86.53.*.*", hardware.target)
        assertEquals("2026-07-28 12:14:43 CST", hardware.generatedAt)
        assertEquals("v2026-05-21", hardware.scriptVersion)

        assertEquals("IP质量体检报告", ip.title)
        assertEquals("v2026-03-29", ip.scriptVersion)
    }

    @Test
    fun `keeps the closing notes`() {
        assertEquals(2, hardware.footnotes.size)
        assertTrue(hardware.footnotes.last().contains("https://Report.Check.Place/hardware/YKCFLFS1L.svg"))
    }

    @Test
    fun `drops the numbering from section titles`() {
        assertEquals(
            listOf("操作系统信息", "主板信息", "CPU测评", "内存测评", "硬盘测评", "HQ硬件加权评分"),
            hardware.sections.map { it.title },
        )
    }

    // --- Fields -------------------------------------------------------------

    @Test
    fun `reflows the padding the terminal needed`() {
        assertEquals(
            listOf("Debian GNU/Linux 12 (bookworm) 5.10.0-14-cloud-amd64"),
            hardware.section("操作系统信息").field("操作系统/内核").values,
        )
        assertEquals(
            listOf("读取 43989.9 MB/s 写入 19808.9 MB/s 延迟 169 ns"),
            hardware.section("内存测评").field("Sysbench").values,
        )
    }

    /** A value too long for eighty columns is continued indented, and is not a row of its own. */
    @Test
    fun `folds continuation lines into the row above`() {
        assertEquals(
            listOf(
                "Intel 82371SB PIIX3 ISA [Natoma/Triton II]",
                "Intel 440FX - 82441FX PMC [Natoma]",
            ),
            hardware.section("主板信息").field("芯片组").values,
        )
        assertEquals(2, hardware.section("CPU测评").field("CPU").values.size)
    }

    /** With the bar's colours and its backspace gone, a leading pipe is all that is left of it. */
    @Test
    fun `strips what is left of the bar charts`() {
        assertEquals(listOf("945"), hardware.section("CPU测评").field("GB5单核").values)
        assertEquals(listOf("低风险"), ip.section("风险评分").field("DB-IP").values)
    }

    // --- Tables -------------------------------------------------------------

    @Test
    fun `reads a table whose header is padded differently from its body`() {
        // 风险因子 separates its eight names by one space; its rows use four.
        val table = ip.section("风险因子").table()

        assertEquals(
            listOf("IP2Location", "ipapi", "ipregistry", "IPQS", "Scamalytics", "ipdata", "IPinfo", "DB-IP"),
            table.columns,
        )
        assertEquals(7, table.rows.size)
        assertEquals("服务器", table.rows[4].label)
        assertEquals(listOf("是", "是", "是", "无", "否", "否", "否", "无"), table.rows[4].cells)
    }

    /**
     * 公司类型 has nothing to say about AbuseIPDB, so it is one cell short.
     *
     * Index order would still be right here because the gap is at the end, but the parser does not
     * get to assume that — the cells are placed under the column they physically sit beneath.
     */
    @Test
    fun `places a short row under the columns it lines up with`() {
        val table = ip.section("IP类型属性").table()

        assertEquals(listOf("IPinfo", "ipregistry", "ipapi", "IP2Location", "AbuseIPDB"), table.columns)
        assertEquals(listOf("家宽", "家宽", "家宽", "机房", "家宽"), table.rows[0].cells)
        assertEquals(listOf("家宽", "机房", "家宽", "机房", ""), table.rows[1].cells)
    }

    @Test
    fun `splits the fio row on its own column separator`() {
        val table = hardware.section("硬盘测评").table()

        assertEquals(
            listOf("RND4K/Q1", "IOPS", "RND4K/Q32", "IOPS", "SEQ1M/Q1", "IOPS", "SEQ1M/Q8", "IOPS"),
            table.columns,
        )
        assertEquals(listOf("37.9MB/s", "9.7k", "222MB/s", "57k", "2719MB/s", "2.7k", "5270MB/s", "5.3k"), table.rows[0].cells)
    }

    @Test
    fun `treats the score row's arithmetic as arithmetic`() {
        val table = hardware.section("HQ硬件加权评分").table()

        // `总 分` is letter-spaced for the terminal, and `= + +` joins the parts of a sum.
        assertEquals(listOf("总分", "CPU", "GPU", "内存", "硬盘"), table.columns)
        assertEquals(listOf("42832", "21839", "N/A", "18168", "2825"), table.rows[0].cells)
        assertEquals(listOf("13.9%", "19.8%", "N/A", "15.7%", "10.7%"), table.rows[1].cells)
    }

    /** A single spaced-out row is a value, not a one-row table. */
    @Test
    fun `does not mistake a lone spaced row for a table`() {
        val risk = ip.section("风险评分")

        assertTrue(risk.blocks.none { it is QualityReport.Block.Table })
        assertEquals(listOf("极低 低 中等 高 极高"), risk.field("风险等级").values)
    }

    // --- Badges -------------------------------------------------------------

    @Test
    fun `reads the capability markers`() {
        val items = hardware.section("CPU测评").badges("指令集").items

        assertEquals(listOf("VT-x/AMD-V", "AES-NI", "AVX2", "BMI1/2", "EPT/NPT"), items.map { it.text })
    }

    @Test
    fun `turns the mail run into separate verdicts`() {
        val items = ip.section("邮局连通性及黑名单检测").badges("通信").items

        assertEquals(12, items.size)
        assertEquals("Gmail", items[0].text)
        assertTrue(items[0].passed)
        assertEquals("QQ", items[4].text)
        assertTrue(!items[4].passed)
    }

    /**
     * The glyph and the colour disagree on 超开指标, and the colour is the one that means anything.
     *
     * A working balloon reclaim is written `✔ 气球回收` on a red ground because it means the host is
     * overselling memory. Reading the tick as good would tell the reader the opposite of the truth.
     */
    @Test
    fun `takes good and bad from the colour, not from the tick`() {
        val decoded =
            AnsiParser.decode(
                listOf(
                    "++++++++",
                    "        硬件质量体检报告：1.2.3.4",
                    "        报告时间：2026-07-28 12:14:43 CST  脚本版本：v2026-05-21",
                    "++++++++",
                    "五、内存测评",
                    "超开指标：\u001B[41m ✔ 气球回收 \u001B[0m   \u001B[42m ✘ KSM 复用 \u001B[0m",
                    "========",
                ).joinToString("\n"),
            )

        val report = checkNotNull(QualityReportParser.parse(decoded.text, decoded.spans))
        val items = report.section("内存测评").badges("超开指标").items

        assertEquals(listOf("气球回收", "KSM 复用"), items.map { it.text })
        assertTrue("a red ✔ is not good news", !items[0].passed)
        assertTrue("a green ✘ is good news", items[1].passed)
    }

    @Test
    fun `falls back to the tick when there is no colour`() {
        val items = hardware.section("内存测评").badges("超开指标").items

        assertTrue(items[0].passed)
        assertTrue(!items[1].passed)
    }

    // --- Rejection ----------------------------------------------------------

    /**
     * NetQuality rules with `*`, and is usually posted as the script's SVG rather than as text — so
     * this covers the case where someone pastes it instead.
     *
     * Its latency bars are drawn with block characters and no label, which is what [Block.Note] is
     * for: the alignment is the only structure they have left, so the spacing is kept.
     */
    @Test
    fun `reads a report ruled with asterisks`() {
        val report =
            checkNotNull(
                QualityReportParser.parse(
                    listOf(
                        "*".repeat(72),
                        "        网络质量体检报告：182.16.*.*",
                        "        报告时间：2026-07-26 21:00:00 CST  脚本版本：v2026-01-25",
                        "*".repeat(72),
                        "一、BGP信息（BGP.TOOLS & HE.NET）",
                        "注册信息：            APNIC, AS45753 Netsec Limited, Prefix/24",
                        "四、三网TCP大包延迟",
                        "津    156    156    154 冀      0    181    161",
                        "=".repeat(72),
                        "今日网络检测量：1490",
                    ).joinToString("\n"),
                ),
            )

        assertEquals("网络质量体检报告", report.title)
        assertEquals("v2026-01-25", report.scriptVersion)
        assertEquals(
            listOf("APNIC, AS45753 Netsec Limited, Prefix/24"),
            report.section("BGP信息（BGP.TOOLS & HE.NET）").field("注册信息").values,
        )

        val bars = report.section("三网TCP大包延迟").blocks.single() as QualityReport.Block.Note
        assertEquals("津    156    156    154 冀      0    181    161", bars.text)
    }

    @Test
    fun `returns null for text that is not a report`() {
        assertNull(QualityReportParser.parse("curl -sL https://run.nodequality.com | bash"))
        // Rules alone are not enough; something between them has to name a report.
        assertNull(QualityReportParser.parse("++++++++\nhello\n++++++++"))
    }

    @Test
    fun `survives a report that stops halfway`() {
        val truncated = load("hardware-quality.txt").substringBefore("五、内存测评")

        val report = QualityReportParser.parse(truncated)

        assertNotNull(report)
        assertEquals("硬件质量体检报告", report?.title)
        assertTrue(report?.footnotes.isNullOrEmpty())
    }
}
