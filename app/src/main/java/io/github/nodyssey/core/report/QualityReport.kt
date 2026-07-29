package io.github.nodyssey.core.report

/**
 * A NodeQuality benchmark report, read back out of the fixed-width text a terminal drew it as.
 *
 * The 测评 board runs on xykt's Check.Place scripts, whose output is eighty columns of aligned ASCII
 * built for a desktop terminal. Eighty columns cannot be shown on a phone: fitting them across
 * 328dp puts the type below 7sp, and letting them scroll sideways means panning to read one line.
 * Neither is reading. So the text is taken apart here and rebuilt out of ordinary Compose rows,
 * which can wrap, at a size a person can actually read.
 *
 * This is deliberately a *view* of the report rather than a replacement for it. The scripts are
 * versioned and their layout moves, so anything not recognised survives as [Block.Note] and the
 * untouched terminal text stays one tap away — see the fallback in the renderer.
 */
data class QualityReport(
    /** e.g. `硬件质量体检报告`. */
    val title: String,
    /** The host the report is about, usually a masked address such as `86.53.*.*`. */
    val target: String?,
    val generatedAt: String?,
    val scriptVersion: String?,
    val sections: List<Section>,
    /** The lines below the closing rule: detection counts and the permanent report link. */
    val footnotes: List<String>,
) {
    data class Section(
        /** The numbering prefix is kept off: `一、` is a position, not part of the name. */
        val title: String,
        val blocks: List<Block>,
    )

    sealed interface Block {
        /** `标签：值`, with any indented continuation lines that followed it. */
        data class Field(
            val label: String,
            val values: List<String>,
        ) : Block

        /** A row of `✔`/`✘` capability markers, such as 指令集 or 超开指标. */
        data class Badges(
            val label: String,
            val items: List<Badge>,
        ) : Block

        /**
         * A run of rows sharing one header, such as 风险因子's eight databases.
         *
         * Kept as a grid rather than flattened into fields because the comparison across columns is
         * the whole point of these — which database disagrees with the others is the question a
         * reader has.
         */
        data class Table(
            val columns: List<String>,
            val rows: List<Row>,
        ) : Block

        /** Anything the parser did not recognise, preserved verbatim rather than dropped. */
        data class Note(val text: String) : Block
    }

    data class Badge(
        val text: String,
        val passed: Boolean,
    )

    data class Row(
        val label: String,
        /** One entry per column in the owning table; missing cells are empty strings. */
        val cells: List<String>,
    )
}
