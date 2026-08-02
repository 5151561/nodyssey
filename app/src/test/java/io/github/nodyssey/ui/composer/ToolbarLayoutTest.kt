package io.github.nodyssey.ui.composer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stored-arrangement rules from [toolbarLayout], which are the ones a version bump breaks. */
class ToolbarLayoutTest {
    @Test
    fun `an untouched arrangement is the surface's own defaults`() {
        val layout = toolbarLayout(emptyList(), EditorActions.Reply)

        assertEquals(EditorActions.Reply, layout.enabled)
        assertTrue(EditorAction.LIST in layout.available)
    }

    @Test
    fun `a stored arrangement keeps the order it was saved in`() {
        val stored = listOf("EMOJI", "BOLD", "LINK")

        assertEquals(
            listOf(EditorAction.EMOJI, EditorAction.BOLD, EditorAction.LINK),
            toolbarLayout(stored, EditorActions.Post).enabled,
        )
    }

    @Test
    fun `a name this version no longer has is dropped, not fatal`() {
        // What an action removed in a later release leaves behind in everyone's stored strip.
        val layout = toolbarLayout(listOf("BOLD", "BLINK", "CODE"), EditorActions.Post)

        assertEquals(listOf(EditorAction.BOLD, EditorAction.CODE), layout.enabled)
    }

    @Test
    fun `an arrangement of nothing but junk falls back rather than emptying the strip`() {
        assertEquals(EditorActions.Post, toolbarLayout(listOf("BLINK"), EditorActions.Post).enabled)
    }

    @Test
    fun `a key this version has just added is offered, never silently enabled`() {
        // Standing in for the next release's new action: a key the stored arrangement predates.
        val layout = toolbarLayout(listOf("BOLD", "CODE"), EditorActions.Post)

        assertTrue(EditorAction.STRIKETHROUGH in layout.available)
        assertTrue(EditorAction.STRIKETHROUGH !in layout.enabled)
    }

    @Test
    fun `the pool is the catalogue order, not the order things left the strip`() {
        val layout = toolbarLayout(listOf("EMOJI"), EditorActions.Post)

        assertEquals(EditorAction.entries.filterNot { it == EditorAction.EMOJI }, layout.available)
    }

    @Test
    fun `a duplicated name cannot put the same key on the strip twice`() {
        val layout = toolbarLayout(listOf("BOLD", "BOLD", "CODE"), EditorActions.Post)

        assertEquals(listOf(EditorAction.BOLD, EditorAction.CODE), layout.enabled)
    }
}
