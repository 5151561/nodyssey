package io.github.nodyssey.ui.account

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import io.github.nodyssey.data.account.BlockedUser
import io.github.nodyssey.ui.theme.NodysseyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 屏蔽用户: the list is the account's, and the page can add to it the way the site's form does. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class BlockListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val blocks = mutableListOf<String>()

    /** The name lives in Compose state so the send button re-enables the way it does in the app. */
    private fun setContent(state: BlockListUiState) {
        composeRule.setContent {
            var nameInput by remember { mutableStateOf("") }
            NodysseyTheme {
                BlockListScreen(
                    state = state.copy(nameInput = nameInput),
                    snackbarHostState = SnackbarHostState(),
                    onBack = {},
                    onShowBlockedChange = {},
                    onNameChange = { nameInput = it },
                    onBlock = { blocks += nameInput },
                    onRequestUnblock = {},
                    onDismissUnblock = {},
                    onConfirmUnblock = {},
                )
            }
        }
    }

    private val populated =
        BlockListUiState(
            isLoading = false,
            blocked = listOf(BlockedUser(uid = 7, name = "vps_matthew")),
        )

    /**
     * The badge is the whole point of the row: blocking follows the account, so a reader who expects
     * it to be this-device-only would be surprised on their next login. The site labels it the same.
     */
    @Test
    fun `badges the blocked list as account state`() {
        setContent(populated)

        composeRule.onNodeWithText("Remote").performScrollTo().assertExists()
    }

    @Test
    fun `sends the typed name to the site`() {
        setContent(populated)

        // By its label: the placeholder only renders once the field has focus.
        composeRule.onNodeWithText("添加屏蔽").performScrollTo().performTextInput("someone")
        composeRule.onNodeWithText("添加").performClick()

        assertEquals(listOf("someone"), blocks)
    }

    @Test
    fun `refuses an empty name`() {
        setContent(populated)

        composeRule.onNodeWithText("添加").performScrollTo().performClick()

        assertEquals(emptyList<String>(), blocks)
    }
}
