package io.github.plaza.designsys.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The stored-arrangement rules from [toolbarLayout], which are the ones a version bump breaks. */
class ToolbarLayoutTest {
    @Test
    fun `an untouched arrangement is the surface's own defaults`() {
        val layout = toolbarLayout(emptyList(), ReplyKeys)

        assertEquals(ReplyKeys, layout.enabled)
        assertTrue(EditorAction.LIST in layout.available)
    }

    @Test
    fun `a stored arrangement keeps the order it was saved in`() {
        val stored = listOf("EMOJI", "BOLD", "LINK")

        assertEquals(
            listOf(EditorAction.EMOJI, EditorAction.BOLD, EditorAction.LINK),
            toolbarLayout(stored, PostKeys).enabled,
        )
    }

    @Test
    fun `a name this version no longer has is dropped, not fatal`() {
        // What an action removed in a later release leaves behind in everyone's stored strip.
        val layout = toolbarLayout(listOf("BOLD", "BLINK", "CODE"), PostKeys)

        assertEquals(listOf(EditorAction.BOLD, EditorAction.CODE), layout.enabled)
    }

    @Test
    fun `an arrangement of nothing but junk falls back rather than emptying the strip`() {
        assertEquals(PostKeys, toolbarLayout(listOf("BLINK"), PostKeys).enabled)
    }

    @Test
    fun `a key this version has just added is offered, never silently enabled`() {
        // Standing in for the next release's new action: a key the stored arrangement predates.
        val layout = toolbarLayout(listOf("BOLD", "CODE"), PostKeys)

        assertTrue(EditorAction.STRIKETHROUGH in layout.available)
        assertTrue(EditorAction.STRIKETHROUGH !in layout.enabled)
    }

    @Test
    fun `the pool is the catalogue order, not the order things left the strip`() {
        val layout = toolbarLayout(listOf("EMOJI"), PostKeys)

        assertEquals(EditorAction.entries.filterNot { it == EditorAction.EMOJI }, layout.available)
    }

    @Test
    fun `a duplicated name cannot put the same key on the strip twice`() {
        val layout = toolbarLayout(listOf("BOLD", "BOLD", "CODE"), PostKeys)

        assertEquals(listOf(EditorAction.BOLD, EditorAction.CODE), layout.enabled)
    }
}

/*
 * Stand-ins for a surface's own default keys.
 *
 * The real sets live with the app that owns those surfaces — which keys a topic editor offers is a
 * product decision, not a rule of the arrangement code. What is under test here is what
 * `toolbarLayout` does with *any* defaults, so these are spelled out rather than imported.
 */
private val PostKeys =
    listOf(
        EditorAction.BOLD,
        EditorAction.CODE,
        EditorAction.LIST,
        EditorAction.LINK,
        EditorAction.IMAGE,
        EditorAction.EMOJI,
    )

private val ReplyKeys =
    listOf(
        EditorAction.BOLD,
        EditorAction.CODE,
        EditorAction.QUOTE,
        EditorAction.MENTION,
        EditorAction.IMAGE,
        EditorAction.EMOJI,
    )
