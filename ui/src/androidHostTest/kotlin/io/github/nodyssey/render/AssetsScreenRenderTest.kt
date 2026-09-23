package io.github.nodyssey.render

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.paging.PagingData
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.nodyssey.data.CreditEntry
import io.github.nodyssey.data.DailyQuota
import io.github.nodyssey.data.StardustEntry
import io.github.nodyssey.data.StardustType
import io.github.nodyssey.ui.assets.AssetsScreen
import io.github.nodyssey.ui.assets.AssetsUiState
import io.github.nodyssey.ui.assets.CreditScreen
import io.github.nodyssey.ui.assets.CreditUiState
import io.github.nodyssey.ui.assets.InviteConfirmDialog
import io.github.nodyssey.ui.assets.RecipientCheck
import io.github.nodyssey.ui.assets.StardustScreen
import io.github.nodyssey.ui.assets.StardustUiState
import io.github.nodyssey.ui.assets.TransferForm
import io.github.plaza.designsys.theme.PlazaTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 账户与成长 and its two ledgers (8a, 8b, 8c), the transfer confirmation (8d) and the invite
 * shortfall (9e), in both themes. The two confirmation layers are dialogs — windows of their own — so
 * they are taken with [captureScreenRoboImage], as `ComposerRenderTest` explains.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class AssetsScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Assets() {
        AssetsScreen(
            state = ASSETS,
            onBack = {},
            onRetry = {},
            onRequestAttendance = {},
            onDismissAttendanceChooser = {},
            onSignInForToday = {},
            onOpenBoard = {},
            onDismissBoard = {},
            onRetryBoard = {},
            onChickenLedger = {},
            onStardust = {},
            onOpenBrowser = {},
            onSignIn = {},
        )
    }

    @Composable
    private fun Credit() {
        CreditScreen(
            state = CreditUiState(level = 1, chickenCount = 344, levelFloorChicken = 100, nextLevelChicken = 400),
            entries = remember { flowOf(PagingData.from(CREDIT)) },
            onBack = {},
            onRetry = {},
            onOpenBrowser = {},
            onSignIn = {},
        )
    }

    @Composable
    private fun Stardust(state: StardustUiState) {
        StardustScreen(
            state = state,
            entries = remember { flowOf(PagingData.from(STARDUST)) },
            snackbarHostState = remember { SnackbarHostState() },
            amountState = rememberTextFieldState("2"),
            recipientState = rememberTextFieldState("11203"),
            refState = rememberTextFieldState("108"),
            onBack = {},
            onRetry = {},
            onOpenBrowser = {},
            onSignIn = {},
            onVerify = {},
            onOpenTransfer = {},
            onDismissTransfer = {},
            onRequestConfirm = {},
            onDismissConfirm = {},
            onConfirmTransfer = {},
        )
    }

    private fun render(
        name: String,
        darkTheme: Boolean,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent { PlazaTheme(darkTheme = darkTheme) { content() } }
        composeRule.onRoot().captureRender(name)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun renderScreen(
        name: String,
        darkTheme: Boolean,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent { PlazaTheme(darkTheme = darkTheme) { content() } }
        composeRule.waitForIdle()
        captureScreenRoboImage(filePath = "build/outputs/renders/$name.png")
    }

    @Test
    fun `assets in light`() = render("assets-light", darkTheme = false) { Assets() }

    @Test
    fun `assets in dark`() = render("assets-dark", darkTheme = true) { Assets() }

    /** Tall enough to reach the sign-in row and the board row, before today's sign-in. */
    @Config(qualifiers = "w360dp-h1200dp")
    @Test
    fun `assets before signing in, whole page`() =
        render("assets-unsigned-tall-light", darkTheme = false) {
            AssetsScreen(
                state = ASSETS.copy(hasSignedInToday = false, attendanceGain = null),
                onBack = {},
                onRetry = {},
                onRequestAttendance = {},
                onDismissAttendanceChooser = {},
                onSignInForToday = {},
                onOpenBoard = {},
                onDismissBoard = {},
                onRetryBoard = {},
                onChickenLedger = {},
                onStardust = {},
                onOpenBrowser = {},
                onSignIn = {},
            )
        }

    @Test
    fun `chicken ledger in light`() = render("credit-light", darkTheme = false) { Credit() }

    @Test
    fun `chicken ledger in dark`() = render("credit-dark", darkTheme = true) { Credit() }

    @Test
    fun `stardust ledger in light`() = render("stardust-light", darkTheme = false) { Stardust(STARDUST_STATE) }

    @Test
    fun `stardust ledger in dark`() = render("stardust-dark", darkTheme = true) { Stardust(STARDUST_STATE) }

    @Test
    fun `transfer confirmation in light`() =
        renderScreen("transfer-confirm-light", darkTheme = false) { Stardust(CONFIRMING) }

    @Test
    fun `transfer confirmation in dark`() =
        renderScreen("transfer-confirm-dark", darkTheme = true) { Stardust(CONFIRMING) }

    @Test
    fun `invite shortfall in light`() =
        renderScreen("invite-short-light", darkTheme = false) {
            InviteConfirmDialog(chickenCount = 344, onConfirm = {}, onDismiss = {})
        }

    @Test
    fun `invite confirmation in dark`() =
        renderScreen("invite-confirm-dark", darkTheme = true) {
            InviteConfirmDialog(chickenCount = 1_286, onConfirm = {}, onDismiss = {})
        }

    private companion object {
        val ASSETS =
            AssetsUiState(
                isLoading = false,
                level = 1,
                chickenCount = 344,
                starCount = 4,
                levelFloorChicken = 100,
                nextLevelChicken = 400,
                levelBarRank = 1,
                postQuota = DailyQuota(0, 20),
                commentQuota = DailyQuota(3, 20),
                attendanceQuota = DailyQuota(5, 5),
                feedingQuota = DailyQuota(0, 1),
                hasSignedInToday = true,
                attendanceGain = 5,
            )

        val CREDIT =
            listOf(
                CreditEntry(5, 344, "签到收益5个鸡腿", 1_785_573_691_000),
                CreditEntry(1, 339, "回帖奖励", 1_785_567_394_000),
                CreditEntry(1, 338, "回帖奖励", 1_785_398_200_000),
                CreditEntry(-1, 337, "投喂鸡腿", 1_785_308_455_000),
                CreditEntry(1, 338, "被codemonkey投喂鸡腿", 1_785_143_620_000),
                CreditEntry(5, 337, "发帖奖励", 1_785_043_620_000),
            )

        val STARDUST =
            listOf(
                StardustEntry(186_400, StardustType.TRANSFER, "transfer", -2, 4, 11_203, null, 108, 1_785_388_939_000),
                StardustEntry(187_103, StardustType.UPVOTE, "upvote", 1, 6, 9_667, 11_491_930, 10, 1_785_296_901_000),
                StardustEntry(157_149, StardustType.SYSTEM, "system", 5, 5, null, null, 10, 1_781_567_592_000),
            )

        val STARDUST_STATE = StardustUiState(isLoadingBalance = false, uid = 21_736, balance = 4)

        val CONFIRMING =
            STARDUST_STATE.copy(
                confirmOpen = true,
                form = TransferForm(amountValue = 2, recipientValue = 11_203, refValue = 108),
                recipient = RecipientCheck.Named("kvm_fan"),
            )
    }
}
