package io.github.nodyssey.ui.vote

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nodyssey.model.Vote
import io.github.nodyssey.model.VoteItem
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class VoteCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setCard(
        state: VoteUiState,
        onToggle: (Long) -> Unit = {},
        onSubmit: () -> Unit = {},
        onSignIn: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            PlazaTheme {
                VoteCard(
                    state = state,
                    onRetry = onRetry,
                    onToggle = onToggle,
                    onSubmit = onSubmit,
                    onSetLocked = {},
                    onDelete = {},
                    onToggleVoters = {},
                    onLoadMoreVoters = {},
                    onSignIn = onSignIn,
                    onUserClick = {},
                )
            }
        }
    }

    @Test
    fun `a voted card shows the tally and the total`() {
        setCard(VoteUiState(vote = voted(), isLoading = false, isSignedIn = true))

        composeRule.onNodeWithText("12 票").assertIsDisplayed()
        composeRule.onNodeWithText("共 40 票").assertIsDisplayed()
        // Nothing left to submit, so the button is gone rather than sitting there disabled.
        composeRule.onNodeWithText("提交投票").assertDoesNotExist()
    }

    @Test
    fun `submitting asks first and says the vote cannot be changed`() {
        var submits = 0
        setCard(
            VoteUiState(vote = unvoted(), isLoading = false, isSignedIn = true, selectedIds = setOf(13201)),
            onSubmit = { submits++ },
        )

        composeRule.onNodeWithText("提交投票").performClick()

        composeRule.onNodeWithText("确认投票？").assertIsDisplayed()
        composeRule.onNodeWithText("提交后不可修改。").assertIsDisplayed()
        assertEquals(0, submits)

        composeRule.onNodeWithText("提交").performClick()
        assertEquals(1, submits)
    }

    @Test
    fun `nothing ticked leaves the button disabled`() {
        setCard(VoteUiState(vote = unvoted(), isLoading = false, isSignedIn = true))

        composeRule.onNodeWithText("提交投票").assertIsNotEnabled()
    }

    /** Signing in comes before the vote, not after the site has rejected it. */
    @Test
    fun `a signed-out reader is offered sign-in in the site's own words`() {
        var signIn = 0
        setCard(VoteUiState(vote = unvoted(), isLoading = false, isSignedIn = false), onSignIn = { signIn++ })

        composeRule.onNodeWithText("登陆后再投票").performClick()

        assertEquals(1, signIn)
    }

    @Test
    fun `a locked vote says so and offers no way to vote`() {
        setCard(VoteUiState(vote = unvoted(locked = true), isLoading = false, isSignedIn = true))

        composeRule.onNodeWithText("已锁定").assertIsDisplayed()
        composeRule.onNodeWithText("提交投票").assertDoesNotExist()
    }

    private companion object {
        const val OWNER_UID = 57815L

        fun unvoted(locked: Boolean = false) =
            Vote(
                id = 2871,
                title = "哪个运营商比较好",
                ownerUid = OWNER_UID,
                isPublic = true,
                locked = locked,
                multiple = false,
                items =
                listOf(
                    VoteItem(13201, "移动", voted = false),
                    VoteItem(13202, "联通", voted = false),
                    VoteItem(13203, "电信", voted = false),
                ),
            )

        fun voted() =
            Vote(
                id = 2871,
                title = "哪个运营商比较好",
                ownerUid = OWNER_UID,
                isPublic = true,
                locked = false,
                multiple = false,
                items =
                listOf(
                    VoteItem(13201, "移动", voted = true, count = 12),
                    VoteItem(13202, "联通", voted = false, count = 5),
                    VoteItem(13203, "电信", voted = false, count = 23),
                ),
            )
    }
}
