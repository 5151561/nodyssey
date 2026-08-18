package io.github.nodyssey.core.report

import io.github.nodyssey.core.html.AnsiParser
import io.github.nodyssey.core.html.Fixtures
import io.github.plaza.core.ansi.AnsiDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Driven by the two reports from post 845099, captured verbatim after ANSI decoding.
 *
 * They are the real thing rather than a reduction because every hard case here — a row that is short
 * one cell, a header padded differently from its own body, arithmetic connectors mixed in with data
 * — is something the scripts do that nobody would think to invent.
 */
class QualityReportParserTest {

    private fun load(name: String): String = Fixtures.load("reports/$name")

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

    /**
     * A report whose escapes are still in it, read the way the app reads one: decoded first.
     *
     * The two fixtures were captured after decoding and have no colour left in them, which is right
     * for everything the parser reads out of the *text* — but a verdict's colour is not in the text,
     * so the cases below are written out with their escapes, in the shapes the scripts draw them in.
     */
    private fun coloured(vararg body: String): QualityReport {
        val banner =
            listOf(
                "++++++++",
                "        IP质量体检报告：1.2.3.4",
                "        报告时间：2026-07-28 12:14:43 CST  脚本版本：v2026-03-29",
                "++++++++",
            )
        val decoded = AnsiDecoder.decode((banner + body + "========").joinToString("\n"))
        return checkNotNull(QualityReportParser.parse(decoded.text, decoded.spans))
    }

    private fun QualityReport.Block.Field.texts() = values.map { it.text }

    private fun QualityReport.Row.texts() = cells.map { it.text }

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
            hardware.section("操作系统信息").field("操作系统/内核").texts(),
        )
        assertEquals(
            listOf("读取 43989.9 MB/s 写入 19808.9 MB/s 延迟 169 ns"),
            hardware.section("内存测评").field("Sysbench").texts(),
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
            hardware.section("主板信息").field("芯片组").texts(),
        )
        assertEquals(2, hardware.section("CPU测评").field("CPU").values.size)
    }

    /** With the bar's colours and its backspace gone, a leading pipe is all that is left of it. */
    @Test
    fun `strips what is left of the bar charts`() {
        assertEquals(listOf("945"), hardware.section("CPU测评").field("GB5单核").texts())
        assertEquals(listOf("低风险"), ip.section("风险评分").field("DB-IP").texts())
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
        assertEquals(listOf("是", "是", "是", "无", "否", "否", "否", "无"), table.rows[4].texts())
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
        assertEquals(listOf("家宽", "家宽", "家宽", "机房", "家宽"), table.rows[0].texts())
        assertEquals(listOf("家宽", "机房", "家宽", "机房", ""), table.rows[1].texts())
    }

    @Test
    fun `splits the fio row on its own column separator`() {
        val table = hardware.section("硬盘测评").table()

        assertEquals(
            listOf("RND4K/Q1", "IOPS", "RND4K/Q32", "IOPS", "SEQ1M/Q1", "IOPS", "SEQ1M/Q8", "IOPS"),
            table.columns,
        )
        assertEquals(
            listOf("37.9MB/s", "9.7k", "222MB/s", "57k", "2719MB/s", "2.7k", "5270MB/s", "5.3k"),
            table.rows[0].texts(),
        )
    }

    @Test
    fun `treats the score row's arithmetic as arithmetic`() {
        val table = hardware.section("HQ硬件加权评分").table()

        // `总 分` is letter-spaced for the terminal, and `= + +` joins the parts of a sum.
        assertEquals(listOf("总分", "CPU", "GPU", "内存", "硬盘"), table.columns)
        assertEquals(listOf("42832", "21839", "N/A", "18168", "2825"), table.rows[0].texts())
        assertEquals(listOf("13.9%", "19.8%", "N/A", "15.7%", "10.7%"), table.rows[1].texts())
    }

    /** A single spaced-out row is a value, not a one-row table. */
    @Test
    fun `does not mistake a lone spaced row for a table`() {
        val risk = ip.section("风险评分")

        assertTrue(risk.blocks.none { it is QualityReport.Block.Table })
        assertEquals(listOf("极低 低 中等 高 极高"), risk.field("风险等级").texts())
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
        assertEquals(QualityReport.Tone.Good, items[0].tone)
        assertEquals("QQ", items[4].text)
        assertEquals(QualityReport.Tone.Bad, items[4].tone)
    }

    /**
     * The glyph and the colour disagree on 超开指标, and the colour is the one that means anything.
     *
     * A working balloon reclaim is written `✔ 气球回收` on a red ground because it means the host is
     * overselling memory. Reading the tick as good would tell the reader the opposite of the truth.
     */
    @Test
    fun `takes good and bad from the colour rather than from the tick`() {
        val report =
            coloured(
                "五、内存测评",
                "超开指标：\u001B[41m ✔ 气球回收 \u001B[0m   \u001B[42m ✘ KSM 复用 \u001B[0m",
            )
        val items = report.section("内存测评").badges("超开指标").items

        assertEquals(listOf("气球回收", "KSM 复用"), items.map { it.text })
        assertEquals(QualityReport.Tone.Bad, items[0].tone, "a red ✔ is not good news")
        assertEquals(QualityReport.Tone.Good, items[1].tone, "a green ✘ is good news")
    }

    @Test
    fun `falls back to the tick when there is no colour`() {
        val items = hardware.section("内存测评").badges("超开指标").items

        assertEquals(QualityReport.Tone.Good, items[0].tone)
        assertEquals(QualityReport.Tone.Bad, items[1].tone)
    }

    // --- Colour -------------------------------------------------------------

    /**
     * Green is the ink these scripts write *every* value in, so on its own it says nothing.
     *
     * Taking it as a verdict would paint most of the card green and leave the actual findings no
     * louder than the timezone.
     */
    @Test
    fun `plain green is the value ink rather than a verdict`() {
        val section =
            coloured(
                "一、基础信息",
                "\u001B[36m组织：\u001B[32mDMIT Cloud Services\u001B[0m",
                "\u001B[36mIP类型：\u001B[42m\u001B[1m\u001B[37m 原生IP \u001B[0m",
            ).section("基础信息")

        assertEquals(QualityReport.Tone.Neutral, section.field("组织").values.single().tone)
        // A chip is the same green, but as a ground rather than as ink, and that is a verdict.
        assertEquals(QualityReport.Tone.Good, section.field("IP类型").values.single().tone)
    }

    /**
     * 风险评分 draws a green-yellow-red bar and then writes the verdict at the end of it.
     *
     * Every colour in the row but the last one belongs to the drawing — and the drawing is wider than
     * the finding, so anything that went by which colour covers the most of the row would read a high
     * risk as good news.
     */
    @Test
    fun `reads the verdict at the end of a bar rather than the bar`() {
        val section =
            coloured(
                "三、风险评分",
                "\u001B[36mIP2Location：\u001B[37m\u001B[1m   \u001B[42m      \u001B[43m      " +
                    "\u001B[41m 74|\u001B[0m\u001B[31m\u001B[1m高风险\u001B[0m",
                "\u001B[36mDB-IP：\u001B[37m\u001B[1m         \u001B[42m|\u001B[43m\u001B[41m\u001B[0m" +
                    "\u001B[32m\u001B[1m低风险\u001B[0m",
            ).section("风险评分")

        assertEquals(QualityReport.Tone.Bad, section.field("IP2Location").values.single().tone)
        assertEquals(QualityReport.Tone.Good, section.field("DB-IP").values.single().tone)
    }

    /**
     * HardwareQuality writes its throughputs onto coloured bars, and a bar is a magnitude.
     *
     * The fill under `6.01MB/s` says how fast that is next to the rest of the row, not that something
     * is wrong; the row's own text is then drawn in the bar's colour to match. Reading either as a
     * verdict paints a page of ordinary numbers red and amber.
     */
    @Test
    fun `leaves a bar chart's own colours out of it`() {
        val table =
            coloured(
                "六、硬盘测评",
                "Crystal： RND4K/Q1    IOPS||RND4K/Q32   IOPS",
                "读取：    \u001B[0m\u001B[43m6.0\u001B[0m\u001B[33m1MB/s    1.5k\u001B[0m" +
                    "\u001B[36m||\u001B[0m\u001B[43m89.7MB/s\u001B[0m\u001B[33m     23k\u001B[0m",
                "写入：    \u001B[0m\u001B[43m48.8MB/\u001B[0m\u001B[33ms     12k\u001B[0m" +
                    "\u001B[36m||\u001B[0m\u001B[42m66.4MB/s    \u001B[32m17k\u001B[0m",
            ).section("硬盘测评").table()

        assertEquals(listOf("6.01MB/s", "1.5k", "89.7MB/s", "23k"), table.rows[0].texts())
        assertTrue(
            table.rows.all { row -> row.cells.all { it.tone == QualityReport.Tone.Neutral } },
            "a bar is not a verdict: ${table.rows.map { row -> row.cells.map { it.tone } }}",
        )
    }

    /** Not every verdict is bold or filled: 邮局连通性 writes this one as plain red and nothing else. */
    @Test
    fun `reads a verdict written in plain red`() {
        val section =
            coloured(
                "六、邮局连通性及黑名单检测",
                "\u001B[36m本地25端口出站：\u001B[0m\u001B[31m阻断\u001B[0m",
            ).section("邮局连通性及黑名单检测")

        assertEquals(QualityReport.Tone.Bad, section.field("本地25端口出站").values.single().tone)
    }

    /** The risk table is eight columns of 是/否 whose whole meaning is which of them came back red. */
    @Test
    fun `colours a table cell by cell`() {
        val table =
            coloured(
                "四、风险因子",
                "库：          IP2Location    ipapi    ipregistry",
                "代理：        \u001B[32m\u001B[1m 否 \u001B[0m    \u001B[31m\u001B[1m 是 \u001B[0m    " +
                    "\u001B[32m\u001B[1m 否 \u001B[0m",
                "Tor：         \u001B[32m\u001B[1m 否 \u001B[0m    \u001B[32m\u001B[1m 否 \u001B[0m    " +
                    "\u001B[31m\u001B[1m 是 \u001B[0m",
            ).section("风险因子").table()

        assertEquals(listOf("否", "是", "否"), table.rows[0].texts())
        assertEquals(
            listOf(QualityReport.Tone.Good, QualityReport.Tone.Bad, QualityReport.Tone.Good),
            table.rows[0].cells.map { it.tone },
        )
        assertEquals(
            listOf(QualityReport.Tone.Good, QualityReport.Tone.Good, QualityReport.Tone.Bad),
            table.rows[1].cells.map { it.tone },
        )
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
            report.section("BGP信息（BGP.TOOLS & HE.NET）").field("注册信息").texts(),
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
