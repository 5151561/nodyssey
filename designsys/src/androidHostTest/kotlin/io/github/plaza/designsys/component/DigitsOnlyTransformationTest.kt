package io.github.plaza.designsys.component

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The caret must not drift when input is rejected.
 *
 * This is the regression these fields were migrated off `onValueChange` filtering to fix. Filtering
 * the string afterwards and handing the shorter one back left the caret at the index it held in the
 * *unfiltered* text, so every rejected character slid it one place right and the next digit landed in
 * the wrong position. Measured against the old shape, `1234` with the caret at 2 gave caret 4 for both
 * `9a` and `ab`; the expectations below are what the input-layer rejection produces instead.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class DigitsOnlyTransformationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var focusManager: FocusManager? = null

    /**
     * Closes the input session this test opened.
     *
     * A focused text field left behind outlives the rule's teardown, and the next Compose test in the
     * same JVM then renders an empty tree — a Paging-backed list never delivers its first page, so
     * every assertion in it fails to find a node. Nothing in this suite typed before, which is why the
     * ordering hazard had never surfaced. Any future test that drives text input needs this too.
     */
    @After
    fun releaseInputSession() {
        val manager = focusManager ?: return
        composeRule.runOnUiThread { manager.clearFocus(force = true) }
        composeRule.waitForIdle()
    }

    private fun setField(
        initialText: String = "",
        caret: Int = 0,
        maxLength: Int = 12,
    ) {
        composeRule.setContent {
            PlazaTheme {
                focusManager = LocalFocusManager.current
                OutlinedTextField(
                    state = remember { TextFieldState(initialText, TextRange(caret)) },
                    label = { Text("number") },
                    inputTransformation = digitsOnly(maxLength),
                    modifier = Modifier.testTag(TAG),
                )
            }
        }
    }

    /** With `1234` already present and the caret between `12` and `34`, types [typed]. */
    private fun editInMiddle(typed: String): Pair<String, TextRange> {
        val node = composeRule.onNodeWithTag(TAG)
        node.performTextInput(typed)
        composeRule.waitForIdle()
        val config = node.fetchSemanticsNode().config
        return config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty() to
            (config.getOrNull(SemanticsProperties.TextSelectionRange) ?: TextRange.Zero)
    }

    @Test
    fun `an accepted digit beside a rejected character leaves the caret after the digit`() {
        setField(initialText = "1234", caret = 2)
        val (text, selection) = editInMiddle("9a")
        assertEquals("12934", text)
        assertEquals(TextRange(3), selection)
    }

    @Test
    fun `rejecting every typed character leaves the caret untouched`() {
        setField(initialText = "1234", caret = 2)
        val (text, selection) = editInMiddle("ab")
        assertEquals("1234", text)
        assertEquals(TextRange(2), selection)
    }

    @Test
    fun `input past the cap is refused rather than truncated`() {
        setField(initialText = "1234", caret = 4, maxLength = 4)
        val node = composeRule.onNodeWithTag(TAG)
        node.performTextInput("5")
        composeRule.waitForIdle()
        val text =
            node
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.EditableText)
                ?.text
        assertEquals("1234", text)
    }

    private companion object {
        const val TAG = "number-field"
    }
}
