package io.github.nodyssey.ui.vote

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.model.Vote
import io.github.nodyssey.model.VoteItem
import io.github.plaza.designsys.theme.NodysseyTheme
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
            NodysseyTheme {
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

    /**
     * The single most load-bearing assertion in the file. NodeSeek withholds the counts until this
     * account votes, so an unvoted card that showed a percentage would be inventing one.
     */
    @Test
    fun `an unvoted card shows no counts, no percentages and no total`() {
        setCard(VoteUiState(vote = unvoted(), isLoading = false, isSignedIn = true))

        composeRule.onNodeWithText("移动").assertIsDisplayed()
        composeRule.onNodeWithText("投票后可以看到结果").assertIsDisplayed()
        composeRule.onNodeWithText("12 票").assertDoesNotExist()
        composeRule.onNodeWithText("30%").assertDoesNotExist()
        composeRule.onNodeWithText("共 40 票").assertDoesNotExist()
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

    @Test
    fun `an anonymous vote is labelled`() {
        setCard(VoteUiState(vote = unvoted(isPublic = false), isLoading = false, isSignedIn = true))

        composeRule.onNodeWithText("匿名投票").assertIsDisplayed()
    }

    /** A row rather than a full-screen state: this sits inside an article the reader is still on. */
    @Test
    fun `a failed read offers a retry without taking over`() {
        var retries = 0
        setCard(VoteUiState(vote = null, isLoading = false, error = NodeSeekError.Network), onRetry = { retries++ })

        composeRule.onNodeWithText("重试").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun `the manage menu is absent for a bystander`() {
        setCard(VoteUiState(vote = unvoted(), isLoading = false, isSignedIn = true, selfUid = 999))

        composeRule.onNodeWithContentDescription("管理投票").assertDoesNotExist()
    }

    /** The owner may lock; unlocking and deleting are the moderator's, and must not be offered. */
    @Test
    fun `the owner's manage menu offers only locking`() {
        setCard(VoteUiState(vote = unvoted(), isLoading = false, isSignedIn = true, selfUid = OWNER_UID))

        composeRule.onNodeWithContentDescription("管理投票").performClick()

        composeRule.onNodeWithText("锁定投票").assertIsDisplayed()
        composeRule.onNodeWithText("删除投票").assertDoesNotExist()
        composeRule.onNodeWithText("解锁投票").assertDoesNotExist()
    }

    @Test
    fun `a moderator's manage menu offers unlocking and deleting`() {
        setCard(
            VoteUiState(
                vote = unvoted(locked = true),
                isLoading = false,
                isSignedIn = true,
                selfUid = 999,
                isAdmin = true,
            ),
        )

        composeRule.onNodeWithContentDescription("管理投票").performClick()

        composeRule.onNodeWithText("解锁投票").assertIsDisplayed()
        composeRule.onNodeWithText("删除投票").assertIsDisplayed()
    }

    @Test
    fun `a deleted vote says so instead of showing an empty card`() {
        setCard(VoteUiState(vote = null, isLoading = false, deleted = true))

        composeRule.onNodeWithText("投票已删除").assertIsDisplayed()
    }

    private companion object {
        const val OWNER_UID = 57815L

        fun unvoted(
            locked: Boolean = false,
            isPublic: Boolean = true,
        ) = Vote(
            id = 2871,
            title = "哪个运营商比较好",
            ownerUid = OWNER_UID,
            isPublic = isPublic,
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
