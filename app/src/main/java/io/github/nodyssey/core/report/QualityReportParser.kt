package io.github.nodyssey.core.report

import io.github.nodyssey.model.AnsiSpan
import io.github.plaza.core.TerminalColumns

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

    /** ANSI palette indices the scripts colour their verdicts with; 8 above each is its bright half. */
    private const val ANSI_RED = 1
    private const val ANSI_GREEN = 2
    private const val ANSI_YELLOW = 3
    private const val ANSI_BRIGHT = 8

    /** Black and white, the two the palette has that say nothing on their own. */
    private val CHIP_INKS = setOf(0, 7)

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
            sections =
            sections(
                lines = lines,
                offsets = offsets,
                from = rules[1] + 1,
                until = closing,
                colours = Colours(code, spans),
            ),
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
        colours: Colours,
    ): List<QualityReport.Section> {
        val sections = mutableListOf<QualityReport.Section>()
        var title: String? = null
        var rows = mutableListOf<RawRow>()

        fun flush() {
            val pending = title ?: return
            sections += QualityReport.Section(pending, blocks(rows, colours))
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
                // row above rather than to a row of its own. It is kept as a row so that it keeps its
                // offset, which is what its own colour is found by.
                previous.continuations += RawRow(label = "", line = line, valueFrom = 0, lineOffset = offsets[index])
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
        /** The indented lines the value ran on to, each one a row of its own so it keeps its offset. */
        val continuations: MutableList<RawRow> = mutableListOf(),
    ) {
        val value: String get() = line.substring(valueFrom)

        /** The value's place in the whole report, which is the range its colours are looked up in. */
        val valueFromInReport: Int get() = lineOffset + valueFrom
        val valueUntilInReport: Int get() = lineOffset + line.length
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
        colours: Colours,
    ): List<QualityReport.Block> {
        val blocks = mutableListOf<QualityReport.Block>()
        var index = 0

        while (index < rows.size) {
            val row = rows[index]

            val piped = pipeTableAt(rows, index, colours)
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
                blocks += badges(row, colours)
                index++
                continue
            }

            val signed = collapseSpaces(row.value)
            if (SIGNED_RUN.matches(signed)) {
                blocks += QualityReport.Block.Badges(label = row.label, items = signedBadges(signed))
                index++
                continue
            }

            val table = tableAt(rows, index, colours)
            if (table != null) {
                blocks += table.block
                index = table.next
                continue
            }

            blocks += QualityReport.Block.Field(
                label = row.label,
                // Reflowing is the point: the padding held a terminal column that no longer exists.
                values =
                (listOf(row) + row.continuations)
                    .map {
                        QualityReport.Value(
                            text = collapseSpaces(it.value),
                            tone = colours.toneAt(it.valueFromInReport, it.valueUntilInReport),
                        )
                    }.filter { it.text.isNotBlank() },
            )
            index++
        }
        return blocks
    }

    private fun badges(row: RawRow, colours: Colours): QualityReport.Block.Badges {
        val items =
            cellsOf(row.line, row.valueFrom, row.lineOffset).flatMap { it.splitOnMarks() }.map { cell ->
                val marker = cell.text.firstOrNull()
                // The glyph says whether the feature is present; the colour says whether that is good
                // news. They disagree on 超开指标, where a working balloon reclaim is a warning.
                // A row of marks is chips by construction, so its fills are read whatever ink the
                // script put on them — xykt colours the ground and leaves the glyph's own colour be.
                val tone = colours.toneAt(cell.fromInReport, cell.untilInReport, filled = true)
                QualityReport.Badge(
                    text = cell.text.drop(1).trim().trim('/').trim(),
                    tone =
                    when {
                        tone != QualityReport.Tone.Neutral -> tone
                        marker != null && marker in PASS_MARKS -> QualityReport.Tone.Good
                        else -> QualityReport.Tone.Bad
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
                lineOffset = lineOffset,
            )
        }
    }

    private fun signedBadges(value: String): List<QualityReport.Badge> =
        Regex("""[+-][^+-]+""").findAll(value).map { match ->
            QualityReport.Badge(
                text = match.value.drop(1).trim(),
                tone = if (match.value.first() == '+') QualityReport.Tone.Good else QualityReport.Tone.Bad,
            )
        }.toList()

    // --- Colour -------------------------------------------------------------

    /**
     * The report's text together with the colour runs over it, which only mean anything as a pair.
     *
     * These scripts colour for two different reasons and the card must only follow one of them. A
     * verdict is a colour *about* the value — a red 高风险, a green 解锁 — and that is what belongs
     * on the card. The rest is drawing: HardwareQuality writes its fio and ATTO throughputs onto
     * coloured bars, so a slow disk and a fast one differ in how much of the row is filled, not in
     * what the colour means. Reading those as verdicts would paint a page of ordinary numbers red.
     */
    private class Colours(private val text: String, private val spans: List<AnsiSpan>) {

        /**
         * What the escape sequences over `[from, until)` were saying about that text.
         *
         * Three shapes, in the order they override each other:
         *
         * 1. A **bold** colour. This is how every script writes a verdict it means — `低风险`,
         *    the 是/否 of the risk table — and nothing draws bars in bold.
         * 2. A **fill under contrasting ink**: ` 机房 ` in white on red is a chip, and the ink is
         *    what separates it from a bar, which colours the ground and leaves the text as it was.
         *    A range carrying several fills at once is 风险等级's legend, not a verdict.
         * 3. A plain red or yellow run that is **exactly this text, on a row that draws no bars**.
         *    `本地25端口出站：阻断` is written no louder than that. Both halves of the condition are
         *    needed: a bar chart's own labels are drawn in the bar's colour, and some of them do land
         *    on exactly one cell — but a row that fills anything is drawing, so nothing plain on it
         *    is a verdict.
         *
         * Plain green never counts: it is the ink the scripts write ordinary values in, and taking
         * it as good news would turn most of the card green and leave the findings no louder than
         * the timezone.
         *
         * @param filled the range is known to be a chip, so its fill counts whatever the ink. Only
         *   the `✔`/`✘` rows pass this: they are chips by construction, and xykt colours the ground
         *   under those while leaving the glyph its own colour.
         */
        fun toneAt(from: Int, until: Int, filled: Boolean = false): QualityReport.Tone {
            if (from >= until) return QualityReport.Tone.Neutral
            val covering = spans.filter { it.start < until && it.end > from }

            covering.firstNotNullOfOrNull { span -> if (span.bold) toneOf(span.fg) else null }
                ?.let { return it }

            val fills = covering.filter { filled || it.fg.isChipInk() }.mapNotNull { toneOf(it.bg) }.distinct()
            if (fills.size == 1) return fills.single()
            if (fills.isNotEmpty()) return QualityReport.Tone.Neutral

            if (drawsBars(from, until)) return QualityReport.Tone.Neutral
            return covering.firstNotNullOfOrNull { span ->
                toneOf(span.fg)
                    ?.takeIf { it != QualityReport.Tone.Good }
                    ?.takeIf { inkOf(span.start, span.end) == inkOf(from, until) }
            } ?: QualityReport.Tone.Neutral
        }

        /** Whether the row this range sits on fills anything, which makes it a drawing. */
        private fun drawsBars(from: Int, until: Int): Boolean {
            val start = text.lastIndexOf('\n', (from - 1).coerceAtLeast(0)) + 1
            val end = text.indexOf('\n', until).takeIf { it >= 0 } ?: text.length
            return spans.any { it.bg != null && it.start < end && it.end > start }
        }

        /** What a run actually put on screen, so that a run and a value can be compared as text. */
        private fun inkOf(from: Int, until: Int): String {
            val start = from.coerceIn(0, text.length)
            return text.substring(start, until.coerceIn(start, text.length)).trim()
        }

        /** A palette index, bright half folded onto the plain one — the two mean the same here. */
        private fun toneOf(colour: Int?): QualityReport.Tone? = when (colour?.rem(ANSI_BRIGHT)) {
            ANSI_RED -> QualityReport.Tone.Bad
            ANSI_GREEN -> QualityReport.Tone.Good
            ANSI_YELLOW -> QualityReport.Tone.Warn
            else -> null
        }

        /** Black or white: the ink a script picks when it means the colour behind it to be a label. */
        private fun Int?.isChipInk(): Boolean = this != null && this.rem(ANSI_BRIGHT) in CHIP_INKS
    }

    // --- Tables -------------------------------------------------------------

    private class Table(val block: QualityReport.Block.Table, val next: Int)

    /**
     * yabs' own table: pipes for columns and a row of dashes under the header.
     *
     * Read before the padded kind because these rows are aligned *and* pipe-separated, and splitting
     * them on their padding would give a different — wrong — number of columns.
     */
    private fun pipeTableAt(rows: List<RawRow>, index: Int, colours: Colours): Table? {
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

        val columns = pipeCells(rows[index])
        if (columns.size < 2) return null

        return Table(
            block =
            QualityReport.Block.Table(
                // The first column names the row rather than a column, and becomes the label.
                columns = columns.drop(1).map { it.text },
                rows =
                rows.subList(index + 2, end).map { row ->
                    val cells = pipeCells(row)
                    QualityReport.Row(
                        label = cells.first().text,
                        cells = cells.drop(1).map { it.value(colours) },
                    )
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
    private fun tableAt(rows: List<RawRow>, index: Int, colours: Colours): Table? {
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
                rows =
                body.map { row ->
                    QualityReport.Row(row.label, align(dataCells(row), columns, rows[index], colours))
                },
            ),
            next = end,
        )
    }

    private fun isBadgeRow(row: RawRow): Boolean = row.value.any { it in MARKS }

    private fun dataCells(row: RawRow): List<Cell> =
        cellsOf(row.line, row.valueFrom, row.lineOffset)
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
        val padded = cellsOf(header.line, header.valueFrom, header.lineOffset).flatMap { it.splitOnColumnBreak() }
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
        colours: Colours,
    ): List<QualityReport.Value> {
        if (cells.size == columns.size) return cells.map { it.value(colours) }

        val result = MutableList(columns.size) { QualityReport.Value("") }
        cells.forEach { cell ->
            val column = columns.indices.maxByOrNull { overlap(cell, columns[it], header) } ?: return@forEach
            if (overlap(cell, columns[column], header) > 0 && result[column].text.isEmpty()) {
                result[column] = cell.value(colours)
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
        /** Offset of the line this was cut out of, which turns [from] into a place in the report. */
        val lineOffset: Int,
    ) {
        val fromInReport: Int get() = lineOffset + from
        val untilInReport: Int get() = lineOffset + until
    }

    private fun Cell.value(colours: Colours): QualityReport.Value =
        QualityReport.Value(text = text, tone = colours.toneAt(fromInReport, untilInReport))

    /**
     * Splits a padded line into cells.
     *
     * Two spaces separate columns and one space does not, which is the rule the scripts pad to:
     * `总 分` and `0.80, 0.36` are single values that happen to contain a space.
     */
    private fun cellsOf(line: String, from: Int, lineOffset: Int): List<Cell> {
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
                lineOffset = lineOffset,
            )
            index = scan
        }
        return cells
    }

    /** yabs' own cells, cut at the pipes it draws its columns with. */
    private fun pipeCells(row: RawRow): List<Cell> {
        val cells = mutableListOf<Cell>()
        var start = 0
        while (start <= row.line.length) {
            val pipe = row.line.indexOf(PIPE, start).takeIf { it >= 0 } ?: row.line.length
            var from = start
            var until = pipe
            while (from < until && row.line[from].isWhitespace()) from++
            while (until > from && row.line[until - 1].isWhitespace()) until--
            cells += Cell(
                text = row.line.substring(from, until),
                from = from,
                until = until,
                columnFrom = TerminalColumns.columnAt(row.line, from),
                columnUntil = TerminalColumns.columnAt(row.line, until),
                lineOffset = row.lineOffset,
            )
            start = pipe + 1
        }
        return cells
    }

    private fun Cell.splitOnColumnBreak(): List<Cell> {
        if (!text.contains(COLUMN_BREAK)) return listOf(this)
        // Positions stop being meaningful once a cell is cut up, and only badges need them.
        return text.split(COLUMN_BREAK).map { part ->
            Cell(part.trim(), from, until, columnFrom, columnUntil, lineOffset)
        }.filter { it.text.isNotEmpty() }
    }

    private fun Cell.splitOnSingleSpaces(): List<Cell> =
        text.split(' ').filter { it.isNotBlank() }.map { part ->
            Cell(part, from, until, columnFrom, columnUntil, lineOffset)
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
