package io.github.nodyssey.ui.stardust

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What a 收款码 card is allowed to say, and when.
 *
 * The theme running through these: money leaves this account when the button is pressed, so a number
 * the card has not read yet must be absent rather than guessed, and the button must not appear where
 * pressing it can only end in a refusal.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class StardustReceiveCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setCard(
        state: StardustReceiveUiState,
        node: RichNode.StardustReceive = CODE,
        onPay: () -> Unit = {},
        onSignIn: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            PlazaTheme {
                StardustReceiveCard(
                    node = node,
                    state = state,
                    onPay = onPay,
                    onRetry = onRetry,
                    onSignIn = onSignIn,
                    onFailureShown = {},
                )
            }
        }
    }

    /** Everything the card draws about the ask comes from the marker, and all of it has to show. */
    @Test
    fun `shows the amount, the note, the ref and the one-off marker`() {
        setCard(loaded())

        composeRule.onNodeWithText("2 🌌").assertIsDisplayed()
        composeRule.onNodeWithText("请我喝杯咖啡").assertIsDisplayed()
        composeRule.onNodeWithText("Ref ID 100").assertIsDisplayed()
        composeRule.onNodeWithText("一次性").assertIsDisplayed()
    }

    @Test
    fun `shows the tally once it has loaded`() {
        setCard(loaded())

        composeRule.onNodeWithText("你未付款 · 共 3 人付款 · 收到 6 🌌").assertIsDisplayed()
    }

    /**
     * A tally still in flight says so instead of printing zeroes.
     *
     * "你未付款，共 0 人付款" that changes its mind a moment later is exactly the reading somebody acts
     * on before it settles — and for a one-off code that mistake costs stardust that cannot come back.
     */
    @Test
    fun `says nothing about the tally before it has arrived`() {
        setCard(StardustReceiveUiState(isLoading = true, isSignedIn = true, selfUid = 9))

        composeRule.onNodeWithText("正在读取收款情况…").assertIsDisplayed()
    }

    /**
     * A failed read offers 重试 and no 付款.
     *
     * Paying without knowing whether you already have is the one thing a one-off code makes expensive.
     */
    @Test
    fun `a failed read replaces the pay button with a retry`() {
        var retried = 0
        setCard(
            StardustReceiveUiState(isLoading = false, error = SiteError.Network, isSignedIn = true, selfUid = 9),
            onRetry = { retried++ },
        )

        composeRule.onNodeWithText("付款").assertDoesNotExist()
        composeRule.onNodeWithText("重试").performClick()
        assertEquals(1, retried)
    }

    /** Paying yourself is a refusal waiting to happen, so the button is absent rather than disabled. */
    @Test
    fun `the payee's own code offers no button`() {
        setCard(loaded().copy(selfUid = CODE.memberId))

        composeRule.onNodeWithText("付款").assertDoesNotExist()
    }

    /** Signed out the button becomes the way in, and must not go straight to a payment. */
    @Test
    fun `a signed-out reader is sent to sign in rather than to a payment`() {
        var paid = 0
        var signIn = 0
        setCard(loaded().copy(isSignedIn = false, selfUid = null), onPay = { paid++ }, onSignIn = { signIn++ })

        composeRule.onNodeWithText("登录后付款").performClick()

        assertEquals(0, paid)
        assertEquals(1, signIn)
    }

    /** 付款 opens the confirmation; nothing leaves the account on the first tap. */
    @Test
    fun `pressing pay confirms before it sends`() {
        var paid = 0
        setCard(loaded(), onPay = { paid++ })

        composeRule.onNodeWithText("付款").performClick()

        assertEquals(0, paid)
        composeRule.onNodeWithText("确认付款 2 星辰？").assertIsDisplayed()
        composeRule.onNodeWithText("确认付款").performClick()
        assertEquals(1, paid)
    }

    /** The one-off warning is the one worth reading twice, so the dialog says which kind this is. */
    @Test
    fun `the confirmation names the one-off rule`() {
        setCard(loaded())

        composeRule.onNodeWithText("付款").performClick()

        composeRule.onNodeWithText("这是一次性收款码，同一账号只能付一次。星辰转出后无法撤回。").assertIsDisplayed()
    }

    private fun loaded() =
        StardustReceiveUiState(
            payerCount = 3,
            received = 6,
            paidByMe = false,
            isLoading = false,
            isSignedIn = true,
            selfUid = 9,
        )

    private companion object {
        val CODE =
            RichNode.StardustReceive(
                memberId = 52_425,
                refId = 100,
                amount = 2,
                description = "请我喝杯咖啡",
                onetime = true,
            )
    }
}
