package io.github.nodyssey.ui.account

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.requestFocus
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The formatting strip is shared by 签名 and Readme, so what these cover is the wiring between them:
 * which field the keys write into, and which keys are offered while they do it.
 *
 * The fields are focused rather than tapped, because focus — not the tap — is what picks the strip's
 * target, and asking for it directly keeps Robolectric's IME out of the run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class ProfileFieldsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        state: ProfileFieldsUiState = ProfileFieldsUiState(isLoading = false),
        onSignatureChange: (String) -> Unit = {},
        onReadmeChange: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            PlazaTheme {
                ProfileFieldsScreen(
                    state = state,
                    snackbarHostState = remember { SnackbarHostState() },
                    onBack = {},
                    onBioChange = {},
                    onSignatureChange = onSignatureChange,
                    onReadmeChange = onReadmeChange,
                    onAvatarPicked = {},
                    onAvatarFailed = {},
                    onSave = {},
                )
            }
        }
    }

    @Test
    fun `no field focused means no strip`() {
        setContent()

        composeRule.onNodeWithContentDescription("加粗").assertDoesNotExist()
    }

    @Test
    fun `signature keys write into the signature`() {
        var signature = ""
        setContent(onSignatureChange = { signature = it })

        composeRule.onNodeWithText("签名").performScrollTo().requestFocus()
        composeRule.onNodeWithContentDescription("加粗").performClick()

        assertEquals("**加粗文字**", signature)
    }

    @Test
    fun `readme keys write into the readme`() {
        var readme = ""
        setContent(onReadmeChange = { readme = it })

        composeRule.onNodeWithText("Readme").performScrollTo().requestFocus()
        composeRule.onNodeWithContentDescription("加粗").performClick()

        assertEquals("**加粗文字**", readme)
    }

    @Test
    fun `the plain bio field sends the strip away`() {
        setContent()

        composeRule.onNodeWithText("签名").performScrollTo().requestFocus()
        composeRule.onNodeWithContentDescription("加粗").assertIsDisplayed()

        // Bio holds no Markdown, so keys standing over it would write somewhere off screen.
        composeRule.onNodeWithText("Bio").performScrollTo().requestFocus()
        composeRule.onNodeWithContentDescription("加粗").assertDoesNotExist()
    }

    @Test
    fun `readme offers the block keys a signature does not`() {
        setContent()

        composeRule.onNodeWithText("Readme").performScrollTo().requestFocus()
        composeRule.onNodeWithContentDescription("二级标题").assertIsDisplayed()

        composeRule.onNodeWithText("签名").performScrollTo().requestFocus()
        composeRule.onNodeWithContentDescription("二级标题").assertDoesNotExist()
    }
}
