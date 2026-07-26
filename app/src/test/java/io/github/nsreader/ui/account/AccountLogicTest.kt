package io.github.nsreader.ui.account

import io.github.nsreader.data.Board
import io.github.nsreader.data.settings.visibleHomeBoards
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
    fun `an empty home-board preference means every board`() {
        assertEquals(boards, visibleHomeBoards(boards, emptySet()))
    }

    @Test
    fun `a home-board preference keeps only the chosen boards, in the server's order`() {
        assertEquals(
            listOf(boards[0], boards[2]),
            visibleHomeBoards(boards, setOf("trade", "daily")),
        )
    }

    /**
     * The case that matters after the site renames a board: the stored slugs match nothing, and
     * honouring the preference literally would leave the home strip empty and looking broken.
     */
    @Test
    fun `a home-board preference matching nothing falls back to every board`() {
        assertEquals(boards, visibleHomeBoards(boards, setOf("gone", "also-gone")))
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

    @Test
    fun `email validation accepts ordinary addresses`() {
        assertTrue(isEmailAddress("hikari.zhg@gmail.com"))
        assertTrue(isEmailAddress("ns+tag@outlook.co.uk"))
    }

    @Test
    fun `email validation rejects the mistakes it exists to catch`() {
        assertFalse(isEmailAddress("hikari.zhg"))
        assertFalse(isEmailAddress("@gmail.com"))
        assertFalse(isEmailAddress("a@b"))
        assertFalse(isEmailAddress("two@at@signs.com"))
        assertFalse(isEmailAddress("trailing@dot."))
    }
}
