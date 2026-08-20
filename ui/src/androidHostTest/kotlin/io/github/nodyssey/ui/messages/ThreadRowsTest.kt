package io.github.nodyssey.ui.messages

import io.github.plaza.core.TimeFormat
import io.github.plaza.designsys.component.ThreadRow
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadRowsTest {
    private val now = TimeFormat.parseTimestamp("2026-07-26 10:22:03")!!

    /**
     * Regression for a thread that opened part-scrolled: the separators are list items too, so the
     * last message sits at `rows.lastIndex`, several positions past `messages.lastIndex`.
     */
    @Test
    fun `a separator per day means more rows than messages`() {
        val rows =
            threadRows(
                listOf(
                    bubble("1", daysAgo = 2),
                    bubble("2", daysAgo = 1),
                    bubble("3", daysAgo = 0),
                ),
                now,
            )

        assertEquals(6, rows.size)
        assertEquals(ThreadRow.Bubble::class, rows.last()::class)
        assertEquals("3", (rows.last() as ThreadRow.Bubble).message.id)
    }

    @Test
    fun `messages sharing a day share one separator`() {
        val rows = threadRows(List(4) { bubble(it.toString(), daysAgo = 0) }, now)

        assertEquals(1, rows.count { it is ThreadRow.Day })
        assertEquals(5, rows.size)
    }

    /** A message the server sent no timestamp for cannot open a day, and must not be dropped. */
    @Test
    fun `an undated message still gets a row`() {
        val rows = threadRows(listOf(bubble("1", daysAgo = null)), now)

        assertEquals(listOf<Any>(ThreadRow.Bubble::class), rows.map { it::class })
    }

    private fun bubble(
        id: String,
        daysAgo: Int?,
    ) = MessageBubble(
        id = id,
        isMine = false,
        content = "内容 $id",
        isMarkdown = true,
        sentAtMillis = daysAgo?.let { now - it * 24L * 60 * 60 * 1000 },
        sentAtText = null,
        status = SendStatus.SENT,
    )
}
