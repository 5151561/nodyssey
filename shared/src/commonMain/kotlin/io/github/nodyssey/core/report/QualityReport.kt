package io.github.nodyssey.core.report

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
 *
 * Serializable because 测评对比 keeps the reports a reader set aside, and keeps them past a restart:
 * the basket stores the parsed report rather than the post it came from, so comparing never needs
 * the network. The short [SerialName]s are stored inside that basket — rename a discriminator and a
 * basket written by an older build decodes as empty.
 */
@Serializable
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
    @Serializable
    data class Section(
        /** The numbering prefix is kept off: `一、` is a position, not part of the name. */
        val title: String,
        val blocks: List<Block>,
    )

    @Serializable
    sealed interface Block {
        /** `标签：值`, with any indented continuation lines that followed it. */
        @Serializable
        @SerialName("field")
        data class Field(
            val label: String,
            val values: List<Value>,
        ) : Block

        /** A row of `✔`/`✘` capability markers, such as 指令集 or 超开指标. */
        @Serializable
        @SerialName("badges")
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
        @Serializable
        @SerialName("table")
        data class Table(
            val columns: List<String>,
            val rows: List<Row>,
        ) : Block

        /** Anything the parser did not recognise, preserved verbatim rather than dropped. */
        @Serializable
        @SerialName("note")
        data class Note(val text: String) : Block
    }

    @Serializable
    data class Badge(
        val text: String,
        val tone: Tone,
    )

    @Serializable
    data class Row(
        val label: String,
        /** One entry per column in the owning table; a cell the row did not fill is empty. */
        val cells: List<Value>,
    )

    /**
     * A piece of text and what the terminal's colour was saying about it.
     *
     * The scripts do not only colour for decoration: a red 高风险 next to a green 低风险 is the
     * finding, and the risk table is eight columns of 是/否 that mean nothing until you see which
     * ones are red. Reading those into a [Tone] here — rather than a packed colour — is what lets the
     * card draw them in the theme's own palette, dark mode included.
     */
    @Serializable
    data class Value(
        val text: String,
        val tone: Tone = Tone.Neutral,
    )

    enum class Tone {
        /** No verdict: ordinary values, which the scripts write in their plain value ink. */
        Neutral,
        Good,
        Warn,
        Bad,
    }
}
