package io.github.nodyssey.ui.account

import io.github.nodyssey.data.Board
import io.github.nodyssey.data.settings.visibleHomeBoards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure decisions behind 8g and d6, tested without a Compose tree or a database. */
class AccountLogicTest {

    private val boards =
        listOf(
            Board("daily", "日常", null),
            Board("tech", "技术", null),
            Board("trade", "交易", null),
        )

    @Test
    fun `an empty hidden set means every board`() {
        assertEquals(boards, visibleHomeBoards(boards, emptySet()))
    }

    @Test
    fun `hiding an optional board removes exactly that board`() {
        assertEquals(
            listOf(boards[0], boards[1]),
            visibleHomeBoards(boards, setOf("trade")),
        )
    }

    /**
     * Only 交易 / 生活 / 贴图 can be switched off — a stray slug in the store (a rename, a bad write)
     * must never thin the strip beyond what the site itself allows.
     */
    @Test
    fun `a hidden slug outside the optional three is ignored`() {
        assertEquals(boards, visibleHomeBoards(boards, setOf("daily", "tech", "gone")))
        assertEquals(
            listOf(boards[0], boards[1]),
            visibleHomeBoards(boards, setOf("trade", "daily", "gone")),
        )
    }

    @Test
    fun `an empty password has no strength at all`() {
        assertNull(passwordStrength(""))
    }

    @Test
    fun `strength rises with length and with character variety`() {
        assertEquals(PasswordStrength.Weak, passwordStrength("abc"))
        assertEquals(PasswordStrength.Weak, passwordStrength("abcdefgh"))
        assertEquals(PasswordStrength.Fair, passwordStrength("abcdefgh1"))
        assertEquals(PasswordStrength.Strong, passwordStrength("Abcdefgh1234"))
        assertEquals(PasswordStrength.Best, passwordStrength("Abcdefgh1234!@#\$"))
    }

    /** The inversion the weighting exists to prevent: `aB1!` is not a stronger password than a passphrase. */
    @Test
    fun `a long single-class password beats a short varied one`() {
        val longPassphrase = passwordStrength("correcthorsebatterystaple")!!
        val shortMixed = passwordStrength("aB1!")!!
        assertTrue(longPassphrase.filledBars > shortMixed.filledBars)
    }

    @Test
    fun `the strength meter never fills more bars than it has`() {
        PasswordStrength.entries.forEach { strength ->
            assertTrue(strength.filledBars in 1..PasswordStrength.TOTAL_BARS)
        }
    }
}
