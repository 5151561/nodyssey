package io.github.nodyssey.core.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesTest {
    @Test
    fun `heading markers and the appended changelog link come off`() {
        val body =
            """
            ### 新增

            - 应用内更新

            ### 修复

            - 跳页现在真的跳到那一页

            **Full Changelog**: https://github.com/5151561/nodyssey/compare/v1.1.0...v1.2.0
            """.trimIndent()

        assertEquals(
            """
            新增

            - 应用内更新

            修复

            - 跳页现在真的跳到那一页
            """.trimIndent(),
            releaseNotesText(body),
        )
    }

    @Test
    fun `a hash that is not a heading is left alone`() {
        // `#12` is a floor number in this project's own notes, not a heading.
        assertEquals("回复写的是 #12 那一行", releaseNotesText("回复写的是 #12 那一行"))
    }

    @Test
    fun `an empty body stays empty`() {
        assertEquals("", releaseNotesText(""))
    }
}
