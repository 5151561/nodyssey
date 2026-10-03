package io.github.nodyssey.core.report

/**
 * Two or three [QualityReport]s lined up row against row, for 测评对比.
 *
 * Reports are generic — the parser keeps a section's title and a field's label exactly as the script
 * printed them, with no notion of "the CPU" or "the disk" — so the alignment is by name: a row is
 * one section title plus one label, and each report either has that row or does not. Section and
 * label together, because the scripts reuse labels: xykt prints a `Sysbench：` under both CPU测评
 * and 内存测评, and those are two different numbers that must never share a row.
 *
 * Reports from different script families (yabs next to xykt) share almost no section titles, and
 * that is reported as it is — every row present, with the reports that lack it left empty — rather
 * than guessed at. A mapping from `Processor` to `CPU` would be a claim about two scripts' output
 * that nothing here can check, and a wrong pairing is worse than a visible gap.
 */
data class ReportComparison(
    /** How many reports are being compared; every [Row.cells] has exactly this many entries. */
    val reportCount: Int,
    val sections: List<Section>,
) {
    data class Section(
        val title: String,
        val rows: List<Row>,
    )

    data class Row(
        val label: String,
        /**
         * One entry per report, in the order they were passed: what that report said, or null when
         * it has no such row. A field's continuation lines stay separate values, each with its own
         * tone, so a red verdict on the second line stays red.
         */
        val cells: List<List<QualityReport.Value>?>,
    )

    companion object {
        /**
         * Aligns [reports] by section title and label.
         *
         * Rows come out in the order the reports print them: the first report's order first, and a
         * row only a later report has is placed after the last row it shares with what came before,
         * so a section reads in the script's own order rather than with everything new at its foot.
         *
         * A table contributes one row per cell, labelled `row · column` — 风险因子's eight databases,
         * or fio's block sizes — since a whole table cannot sit in one cell of a comparison. A row
         * label printed twice in one report (iperf3 lists Clouvider for London and for Los Angeles)
         * is kept as two rows and paired by occurrence, first with first.
         *
         * [QualityReport.Block.Note]s are left out: they are whatever the parser could not read, and
         * text nobody could take apart cannot be lined up either. Footnotes too — they are counters
         * and links, not findings.
         */
        fun of(reports: List<QualityReport>): ReportComparison {
            val flattened = reports.map(::flatten)
            val sectionOrder = mergeOrder(flattened.map { report -> report.map { it.section }.distinct() })
            val sections =
                sectionOrder.mapNotNull { title ->
                    val perReport = flattened.map { report -> report.filter { it.section == title } }
                    val keys = mergeOrder(perReport.map { entries -> entries.map { it.key } })
                    if (keys.isEmpty()) return@mapNotNull null
                    Section(
                        title = title,
                        rows =
                        keys.map { key ->
                            Row(
                                label = key.label,
                                cells = perReport.map { entries -> entries.firstOrNull { it.key == key }?.values },
                            )
                        },
                    )
                }
            return ReportComparison(reportCount = reports.size, sections = sections)
        }

        private data class Key(
            val label: String,
            /** 0 for the first time a report prints this label in this section, 1 for the second… */
            val occurrence: Int,
        )

        private class Entry(
            val section: String,
            val key: Key,
            val values: List<QualityReport.Value>,
        )

        private fun flatten(report: QualityReport): List<Entry> {
            val entries = mutableListOf<Entry>()
            val seen = mutableMapOf<Pair<String, String>, Int>()

            fun add(
                section: String,
                label: String,
                values: List<QualityReport.Value>,
            ) {
                if (values.isEmpty() || values.all { it.text.isBlank() }) return
                val occurrence = seen[section to label] ?: 0
                seen[section to label] = occurrence + 1
                entries += Entry(section, Key(label, occurrence), values)
            }

            report.sections.forEach { section ->
                section.blocks.forEach { block ->
                    when (block) {
                        is QualityReport.Block.Field -> add(section.title, block.label, block.values)

                        is QualityReport.Block.Badges ->
                            add(section.title, block.label, block.items.map { QualityReport.Value(it.text, it.tone) })

                        is QualityReport.Block.Table ->
                            block.rows.forEach { row ->
                                block.columns.forEachIndexed { index, column ->
                                    val cell = row.cells.getOrNull(index) ?: return@forEachIndexed
                                    val label =
                                        when {
                                            column.isBlank() -> row.label
                                            row.label.isBlank() -> column
                                            else -> "${row.label} · $column"
                                        }
                                    add(section.title, label, listOf(cell))
                                }
                            }

                        is QualityReport.Block.Note -> Unit
                    }
                }
            }
            return entries
        }

        /**
         * One order that every sequence in [sequences] agrees with as far as it can.
         *
         * Items new to the result are held until the next item their sequence shares with what is
         * already placed, and go in just before it — so a row only one report has lands between
         * the neighbours it was printed between. A run with nothing shared after it goes in after
         * the last shared item, and a sequence sharing nothing at all goes on the end.
         */
        private fun <K> mergeOrder(sequences: List<List<K>>): List<K> {
            val order = mutableListOf<K>()
            sequences.forEach { sequence ->
                // Where the last item of this sequence sits in [order], or -1 before the first.
                var cursor = -1
                val pending = mutableListOf<K>()
                sequence.forEach { item ->
                    val at = order.indexOf(item)
                    if (at < 0) {
                        pending += item
                        return@forEach
                    }
                    if (pending.isNotEmpty()) {
                        order.addAll(if (at > cursor) at else cursor + 1, pending)
                        pending.clear()
                    }
                    cursor = maxOf(cursor, order.indexOf(item))
                }
                order.addAll(if (cursor >= 0) cursor + 1 else order.size, pending)
            }
            return order
        }
    }
}
