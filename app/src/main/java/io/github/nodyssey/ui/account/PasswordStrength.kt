package io.github.nodyssey.ui.account

import androidx.annotation.StringRes
import io.github.nodyssey.R

/**
 * How strong a candidate password looks, on the four-step meter d6 draws.
 *
 * An app-side addition: NodeSeek's own settings page gives no live feedback at all. It is deliberately
 * a *hint* and never a gate — the meter never blocks the save button, because the server owns the
 * actual policy and a client-side rule that disagrees with it would reject passwords the site accepts.
 */
enum class PasswordStrength(
    val filledBars: Int,
    @StringRes val labelRes: Int,
) {
    Weak(1, R.string.account_password_strength_weak),
    Fair(2, R.string.account_password_strength_fair),
    Strong(3, R.string.account_password_strength_strong),
    Best(4, R.string.account_password_strength_best),
    ;

    /** What would move the meter up, or that nothing needs to. */
    @get:StringRes
    val hintRes: Int
        get() =
            when (this) {
                Weak -> R.string.account_password_strength_hint_length
                Fair, Strong -> R.string.account_password_strength_hint_variety
                Best -> R.string.account_password_strength_hint_done
            }

    companion object {
        const val TOTAL_BARS = 4
    }
}

/**
 * Length and character variety, weighted so that length can win.
 *
 * Not an entropy estimate and not a dictionary check: those need a wordlist this app has no reason to
 * ship, and the meter's job is to stop `qwerty123` reading the same as a passphrase, which two cheap
 * signals already do.
 *
 * Length is worth one more step than variety, deliberately. Weighting them equally scored a
 * twenty-five character passphrase the same as `aB1!`, which inverts the advice the meter exists to
 * give — the four-character one is trivially brute-forced and the passphrase is not.
 */
fun passwordStrength(password: String): PasswordStrength? {
    if (password.isEmpty()) return null

    val classes =
        listOf(
            password.any { it.isLowerCase() },
            password.any { it.isUpperCase() },
            password.any { it.isDigit() },
            password.any { !it.isLetterOrDigit() && !it.isWhitespace() },
        ).count { it }

    val lengthPoints = LENGTH_STEPS.count { password.length >= it }
    val varietyPoints = classes - 1

    return when (lengthPoints + varietyPoints) {
        in 6..MAX_SCORE -> PasswordStrength.Best
        in 4..5 -> PasswordStrength.Strong
        in 2..3 -> PasswordStrength.Fair
        else -> PasswordStrength.Weak
    }
}

/** The one rule the app does enforce, so an obviously-doomed request is not sent at all. */
const val MIN_PASSWORD_LENGTH = 8

private val LENGTH_STEPS = listOf(8, 12, 16, 20)
private const val MAX_SCORE = 7
