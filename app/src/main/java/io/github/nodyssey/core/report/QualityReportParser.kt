package io.github.nodyssey.core.report

import io.github.nodyssey.core.TerminalColumns
import io.github.nodyssey.model.AnsiSpan

/**
 * Reads a [QualityReport] back out of the fixed-width text a Check.Place script drew.
 *
 * The layout is regular enough to take apart: a banner between two rules, numbered sections, and
 * inside them `标签：值` rows padded into columns. What varies is everything else — the scripts are
 * versioned, sections come and go with the hardware, and the column counts differ per report — so
 * every step here degrades rather than fails. An unrecognised line becomes a [QualityReport.Block.Note]
 * and text that is not a report at all returns null, which is the renderer's signal to keep showing
 * the terminal block instead.
 */
object QualityReportParser {

    /**
     * The banner and footer rules, each one repeated character.
     *
     * Which character depends on the script rather than on the position: HardwareQuality opens with
     * `++++`, IPQuality with `####`, NetQuality with `****`, and all three close with `====`.
     */
    private val RULE = Regex("""^([*+#=])\1{7,}$""")

    /** `一、操作系统信息`. The numeral is a position in the report, not part of the name. */
    private val SECTION = Regex("""^[一二三四五六七八九十百]+、(.+)$""")

    /**
     * `Basic System Information:` over `---------`, which is how yabs opens a section.
     *
     * NodeQuality's own header block is yabs output, so an older report — or the 基本信息 tab of one
     * — is this shape rather than the numbered one. The underline is what distinguishes a heading
     * from an ordinary row that happens to end in a colon.
     */
    private val SECTION_UNDERLINE = Regex("""^-{3,}$""")

    private val TITLE = Regex("""^(\S*报告)：(.*)$""")
    private val META = Regex("""报告时间：(.+?)\s{2,}脚本版本：(\S+)""")

    /** Falls back to naming the report after the project that produced it. */
    private val PROJECT = Regex("""github\.com/[\w.-]+/([\w.-]+)""")

    /** The xykt scripts write labels with a full-width colon, which values never contain. */
    private const val COLON = '：'

    /**
     * yabs writes `Uptime     : 4 days`, padding the label out to a fixed width.
     *
     * The colon has to be followed by a space for this to be a label at all — otherwise the `:` in
     * `https://` would cut every row holding a URL in half.
     */
    private val ASCII_LABEL = Regex("""^([^:]{1,40}?)\s*: (.*)$""")

    /** yabs' tables are pipe-delimited, with a row of dashes under the header. */
    private const val PIPE = '|'
    private val TABLE_SEPARATOR = Regex("""^[-\s|]+$""")

    /** Connectors in the weighted-score row: `总分 = CPU + 内存 + 硬盘` is arithmetic, not data. */
    private val CONNECTORS = setOf("=", "+", "-")

    /** The Fio row's own column separator. */
    private const val COLUMN_BREAK = "||"

    /** xykt marks with `✔`/`✘`; yabs with `✔`/`❌`. */
    private val PASS_MARKS = charArrayOf('✔', '✅')
    private val FAIL_MARKS = charArrayOf('✘', '❌')
    private val MARKS = PASS_MARKS + FAIL_MARKS

    /**
     * 邮局连通性 writes its verdicts as one unspaced run: `+Gmail+Outlook-QQ+MailRU`.
     *
     * It carries the same yes/no meaning as a `✔`/`✘` row and reads as one word without this.
     */
    private val SIGNED_RUN = Regex("""^([+-][^+-]+){3,}$""")

    /** ANSI palette indices for the badge backgrounds the scripts use to mean good and bad. */
    private const val ANSI_RED = 1
    private const val ANSI_GREEN = 2

    /** A table needs at least this many rows under one header before it is worth calling a table. */
    private const val MIN_TABLE_ROWS = 2

    /** ...and each of those rows at least this many cells, or it is just a value with spaces in it. */
    private const val MIN_TABLE_CELLS = 3

    fun parse(
        code: String,
        spans: List<AnsiSpan> = emptyList(),
    ): QualityReport? {
        val lines = code.lines()
        val offsets = lineOffsets(lines)

        val rules = lines.indices.filter { RULE.matches(lines[it].trim()) }
        if (rules.size < 2) return null

        val banner = lines.subList(rules.first() + 1, rules[1])
        val meta = banner.firstNotNullOfOrNull { META.find(it) }
        val title = banner.firstNotNullOfOrNull { TITLE.find(it.trim()) }
        // NodeQuality's own banner names no report — it is the wrapper, and the four reports it runs
        // are the tabs. Its timestamp and version line is what says this is a report at all, and the
        // project link is the only name available.
        val project = banner.firstNotNullOfOrNull { PROJECT.find(it) }?.groupValues?.get(1)
        if (title == null && (meta == null || project == null)) return null

        // The closing rule is a different character from the banner's, and only present once the
        // script finished; a truncated report simply has no footnotes.
        val closing = rules.lastOrNull()?.takeIf { it > rules[1] } ?: lines.size

        return QualityReport(
            title = title?.groupValues?.get(1) ?: project.orEmpty(),
            target = title?.groupValues?.get(2)?.trim()?.ifBlank { null },
            generatedAt = meta?.groupValues?.get(1)?.trim(),
            scriptVersion = meta?.groupValues?.get(2),
            sections = sections(lines, offsets, from = rules[1] + 1, until = closing, spans = spans),
            footnotes =
            lines
                .drop(closing + 1)
                .map { it.trim() }
                .filter { it.isNotBlank() },
        )
    }

    // --- Sections -----------------------------------------------------------

    private fun sections(
        lines: List<String>,
        offsets: List<Int>,
        from: Int,
        until: Int,
        spans: List<AnsiSpan>,
    ): List<QualityReport.Section> {
        val sections = mutableListOf<QualityReport.Section>()
        var title: String? = null
        var rows = mutableListOf<RawRow>()

        fun flush() {
            val pending = title ?: return
            sections += QualityReport.Section(pending, blocks(rows, spans))
            rows = mutableListOf()
        }

        for (index in from until minOf(until, lines.size)) {
            val line = lines[index]
            if (line.isBlank()) continue

            val section = SECTION.find(line.trim())
            if (section != null) {
                flush()
                title = section.groupValues[1].trim()
                continue
            }
            // The yabs shape: a heading is a line with a rule of dashes drawn under it.
            if (SECTION_UNDERLINE.matches(lines.getOrNull(index + 1)?.trim().orEmpty())) {
                flush()
                title = line.trim().removeSuffix(":")
                continue
            }
            if (SECTION_UNDERLINE.matches(line.trim())) continue
            // Content ahead of the first section header has nowhere to go but its own section.
            if (title == null) title = ""

            // A pipe-delimited row is indented too — fio centres the rule under its header — and it
            // belongs to the table rather than to the row above.
            val indented = line.first().isWhitespace() && !line.contains(PIPE)
            val previous = rows.lastOrNull()
            if (indented && previous != null) {
                // A value too long for one line is continued indented under it, and belongs to the
                // row above rather than to a row of its own.
                previous.continuations += line.trim()
                continue
            }
            rows += rawRow(line, offsets[index])
        }
        flush()
        return sections
    }

    private data class RawRow(
        val label: String,
        val line: String,
        /** Where the value starts within [line]; the label and its colon sit before it. */
        val valueFrom: Int,
        /** Offset of [line] within the whole report, so cells can be found in the ANSI spans. */
        val lineOffset: Int,
        val continuations: MutableList<String> = mutableListOf(),
    ) {
        val value: String get() = line.substring(valueFrom)
    }

    private fun rawRow(line: String, offset: Int): RawRow {
        val colon = line.indexOf(COLON)
        if (colon >= 0) {
            return RawRow(
                label = line.take(colon).trim(),
                line = line,
                valueFrom = colon + 1,
                lineOffset = offset,
            )
        }

        // A pipe-delimited row is a table row, and its first cell is the label rather than a
        // heading — leaving it to the ASCII rule below would cut it at a colon inside a cell.
        if (line.none { it == PIPE }) {
            ASCII_LABEL.find(line)?.let { match ->
                return RawRow(
                    label = match.groupValues[1].trim(),
                    line = line,
                    valueFrom = match.groups[2]!!.range.first,
                    lineOffset = offset,
                )
            }
        }

        // A line with no label at all is kept whole, so it can survive as a note.
        return RawRow(label = "", line = line, valueFrom = 0, lineOffset = offset)
    }

    // --- Blocks -------------------------------------------------------------

    private fun blocks(
        rows: List<RawRow>,
        spans: List<AnsiSpan>,
    ): List<QualityReport.Block> {
        val blocks = mutableListOf<QualityReport.Block>()
        var index = 0

        while (index < rows.size) {
            val row = rows[index]

            val piped = pipeTableAt(rows, index)
            if (piped != null) {
                blocks += piped.block
                index = piped.next
                continue
            }

            // The dashes under a header, and the empty row fio puts between its two tables, are
            // drawings that belong to a table already read.
            if (TABLE_SEPARATOR.matches(row.line)) {
                index++
                continue
            }

            if (row.label.isEmpty()) {
                // Left padding is kept. A line with no label is one the parser could not take apart,
                // and for those the terminal's own alignment is the only structure left — NetQuality
                // draws its latency bars this way.
                blocks += QualityReport.Block.Note(row.line.trimEnd())
                index++
                continue
            }

            if (row.value.any { it in MARKS }) {
                blocks += badges(row, spans)
                index++
                continue
            }

            val signed = collapseSpaces(row.value)
            if (SIGNED_RUN.matches(signed)) {
                blocks += QualityReport.Block.Badges(label = row.label, items = signedBadges(signed))
                index++
                continue
            }

            val table = tableAt(rows, index)
            if (table != null) {
                blocks += table.block
                index = table.next
                continue
            }

            blocks += QualityReport.Block.Field(
                label = row.label,
                // Reflowing is the point: the padding held a terminal column that no longer exists.
                values = (listOf(row.value) + row.continuations).map(::collapseSpaces).filter { it.isNotBlank() },
            )
            index++
        }
        return blocks
    }

    private fun badges(row: RawRow, spans: List<AnsiSpan>): QualityReport.Block.Badges {
        val items =
            cellsOf(row.line, row.valueFrom).flatMap { it.splitOnMarks() }.map { cell ->
                val marker = cell.text.firstOrNull()
                // The glyph says whether the feature is present; the background says whether that is
                // good news. They disagree on 超开指标, where a working balloon reclaim is a warning.
                val background = backgroundAt(spans, row.lineOffset + cell.from, row.lineOffset + cell.until)
                QualityReport.Badge(
                    text = cell.text.drop(1).trim().trim('/').trim(),
                    passed =
                    when (background) {
                        ANSI_GREEN -> true
                        ANSI_RED -> false
                        else -> marker != null && marker in PASS_MARKS
                    },
                )
            }
        return QualityReport.Block.Badges(label = row.label, items = items.filter { it.text.isNotEmpty() })
    }

    /**
     * Cuts a cell at every mark inside it.
     *
     * yabs writes two verdicts into one field — `IPv4/IPv6  : ✔ Online / ❌ Offline` — where xykt
     * would have padded them apart. Splitting at the marks reads both shapes.
     */
    private fun Cell.splitOnMarks(): List<Cell> {
        val cuts = text.indices.filter { text[it] in MARKS }
        if (cuts.size < 2) return listOf(this)
        return cuts.mapIndexed { position, start ->
            val end = cuts.getOrElse(position + 1) { text.length }
            Cell(
                text = text.substring(start, end).trimEnd(),
                from = from + start,
                until = from + end,
                columnFrom = columnFrom,
                columnUntil = columnUntil,
            )
        }
    }

    private fun signedBadges(value: String): List<QualityReport.Badge> =
        Regex("""[+-][^+-]+""").findAll(value).map { match ->
            QualityReport.Badge(
                text = match.value.drop(1).trim(),
                passed = match.value.first() == '+',
            )
        }.toList()

    private fun backgroundAt(spans: List<AnsiSpan>, from: Int, until: Int): Int? =
        spans.firstOrNull { it.bg != null && it.start < until && it.end > from }?.bg

    // --- Tables -------------------------------------------------------------

    private class Table(val block: QualityReport.Block.Table, val next: Int)

    /**
     * yabs' own table: pipes for columns and a row of dashes under the header.
     *
     * Read before the padded kind because these rows are aligned *and* pipe-separated, and splitting
     * them on their padding would give a different — wrong — number of columns.
     */
    private fun pipeTableAt(rows: List<RawRow>, index: Int): Table? {
        if (!rows[index].line.contains(PIPE)) return null
        // The separator is the whole test. xykt writes `3|低风险` and `IOPS||RND4K/Q32` with pipes
        // too, and neither draws a rule under its header.
        if (!TABLE_SEPARATOR.matches(rows.getOrNull(index + 1)?.line.orEmpty())) return null

        var end = index + 2
        while (end < rows.size) {
            val line = rows[end].line
            if (!line.contains(PIPE)) break
            // fio prints two block-size tables into one section, parted by a row of empty cells.
            // That row ends this table; the next header's own rule opens the following one.
            if (TABLE_SEPARATOR.matches(line)) break
            end++
        }
        if (end <= index + 2) return null

        val columns = rows[index].line.split(PIPE).map { it.trim() }
        if (columns.size < 2) return null

        return Table(
            block =
            QualityReport.Block.Table(
                // The first column names the row rather than a column, and becomes the label.
                columns = columns.drop(1),
                rows =
                rows.subList(index + 2, end).map { row ->
                    val cells = row.line.split(PIPE).map { it.trim() }
                    QualityReport.Row(label = cells.first(), cells = cells.drop(1))
                },
            ),
            next = end,
        )
    }

    /**
     * Reads [index] as a table header if enough rows beneath it share its shape.
     *
     * The header is the row whose cells name the columns — 数据库, 库, 服务商, 项目 — but which of
     * those it is differs per script, so it is found by shape rather than by name.
     */
    private fun tableAt(rows: List<RawRow>, index: Int): Table? {
        var end = index + 1
        while (end < rows.size && dataCells(rows[end]).size >= MIN_TABLE_CELLS && !isBadgeRow(rows[end])) end++
        if (end - (index + 1) < MIN_TABLE_ROWS) return null

        val body = rows.subList(index + 1, end)
        val widest = body.maxOf { dataCells(it).size }
        val columns = headerCells(rows[index], widest)
        if (columns.size < MIN_TABLE_CELLS) return null

        return Table(
            block =
            QualityReport.Block.Table(
                columns = columns.map { tightenCjk(it.text) },
                rows = body.map { row -> QualityReport.Row(row.label, align(dataCells(row), columns, rows[index])) },
            ),
            next = end,
        )
    }

    private fun isBadgeRow(row: RawRow): Boolean = row.value.any { it in MARKS }

    private fun dataCells(row: RawRow): List<Cell> =
        cellsOf(row.line, row.valueFrom)
            .flatMap { it.splitOnColumnBreak() }
            .filter { it.text !in CONNECTORS }

    /**
     * The header's own cells, re-split on single spaces when the padded split came up short.
     *
     * 风险因子 separates its eight database names by one space each while its data rows use four, so
     * the same rule cannot read both; 项目 conversely writes `总 分` with a space inside one cell and
     * must not be re-split. Which applies is decided by whether the counts already agree.
     */
    private fun headerCells(header: RawRow, widest: Int): List<Cell> {
        val padded = cellsOf(header.line, header.valueFrom).flatMap { it.splitOnColumnBreak() }
        if (padded.size >= widest) return padded

        val resplit = padded.flatMap { it.splitOnSingleSpaces() }
        return if (resplit.size == widest) resplit else padded
    }

    /**
     * Puts each cell under a column.
     *
     * Index order is right whenever the counts agree. When a row is short — 公司类型 has nothing to
     * say about AbuseIPDB — the gap need not be at the end, so those fall back to the column the
     * cell physically sits under, which is the alignment the report was written in.
     */
    private fun align(
        cells: List<Cell>,
        columns: List<Cell>,
        header: RawRow,
    ): List<String> {
        if (cells.size == columns.size) return cells.map { it.text }

        val result = MutableList(columns.size) { "" }
        cells.forEach { cell ->
            val column = columns.indices.maxByOrNull { overlap(cell, columns[it], header) } ?: return@forEach
            if (overlap(cell, columns[column], header) > 0 && result[column].isEmpty()) {
                result[column] = cell.text
            }
        }
        return result
    }

    /** Cells and columns only line up in terminal cells, never in characters — CJK is two wide. */
    private fun overlap(cell: Cell, column: Cell, header: RawRow): Int {
        val cellFrom = cell.columnFrom
        val cellUntil = cell.columnUntil
        val columnFrom = TerminalColumns.columnAt(header.line, column.from)
        val columnUntil = TerminalColumns.columnAt(header.line, column.until)
        return (minOf(cellUntil, columnUntil) - maxOf(cellFrom, columnFrom)).coerceAtLeast(0)
    }

    // --- Cells --------------------------------------------------------------

    private class Cell(
        val text: String,
        val from: Int,
        val until: Int,
        val columnFrom: Int,
        val columnUntil: Int,
    )

    /**
     * Splits a padded line into cells.
     *
     * Two spaces separate columns and one space does not, which is the rule the scripts pad to:
     * `总 分` and `0.80, 0.36` are single values that happen to contain a space.
     */
    private fun cellsOf(line: String, from: Int): List<Cell> {
        val cells = mutableListOf<Cell>()
        var index = from
        while (index < line.length) {
            while (index < line.length && line[index] == ' ') index++
            if (index >= line.length) break

            var scan = index
            var lastInk = index
            while (scan < line.length) {
                if (line[scan] == ' ') {
                    if (scan + 1 >= line.length || line[scan + 1] == ' ') break
                } else {
                    lastInk = scan
                }
                scan++
            }
            val until = lastInk + 1
            cells += Cell(
                text = line.substring(index, until),
                from = index,
                until = until,
                columnFrom = TerminalColumns.columnAt(line, index),
                columnUntil = TerminalColumns.columnAt(line, until),
            )
            index = scan
        }
        return cells
    }

    private fun Cell.splitOnColumnBreak(): List<Cell> {
        if (!text.contains(COLUMN_BREAK)) return listOf(this)
        // Positions stop being meaningful once a cell is cut up, and only badges need them.
        return text.split(COLUMN_BREAK).map { part ->
            Cell(part.trim(), from, until, columnFrom, columnUntil)
        }.filter { it.text.isNotEmpty() }
    }

    private fun Cell.splitOnSingleSpaces(): List<Cell> =
        text.split(' ').filter { it.isNotBlank() }.map { part ->
            Cell(part, from, until, columnFrom, columnUntil)
        }

    // --- Text ---------------------------------------------------------------

    private fun collapseSpaces(text: String): String =
        text
            .trim()
            .replace(Regex("""\s{2,}"""), " ")
            // The GB5 rows draw a bar and mark the result's place on it with a pipe. With the bar's
            // colours and its backspace gone, a leading pipe is all that is left of the drawing.
            .removePrefix("|")
            .trim()

    /**
     * Closes up the space the scripts letter-space CJK headings with — `总 分`, `内 存`.
     *
     * Only between two wide glyphs, which is the idiom; `Netflix Youtube` keeps its space because
     * neither side of it is CJK.
     */
    private fun tightenCjk(text: String): String =
        Regex("""(?<=[一-鿿]) (?=[一-鿿])""").replace(text, "")

    private fun lineOffsets(lines: List<String>): List<Int> {
        var offset = 0
        return lines.map {
            val start = offset
            offset += it.length + 1
            start
        }
    }
}
