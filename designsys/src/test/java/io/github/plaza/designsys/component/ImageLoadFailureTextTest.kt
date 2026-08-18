package io.github.plaza.designsys.component

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.plaza.core.image.ImageLoadFailure
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The wording each failure gets — in particular that a challenge does not get the plain 图床拒绝了
 * sentence, which would send the reader back to 重试 forever.
 */
@RunWith(RobolectricTestRunner::class)
class ImageLoadFailureTextTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(failure: ImageLoadFailure?) {
        composeRule.setContent {
            PlazaTheme { Text(imageLoadFailureText(failure) ?: NOTHING_TO_SAY) }
        }
    }

    @Test
    fun `a challenge names the verification, not just the status`() {
        show(ImageLoadFailure.Challenge(403))

        composeRule.onNodeWithText("图床要求人机验证，App 过不去（HTTP 403）").assertExists()
    }

    @Test
    fun `a plain refusal names its status`() {
        show(ImageLoadFailure.Http(404))

        composeRule.onNodeWithText("图床拒绝了这张图（HTTP 404）").assertExists()
    }

    /** Nothing to add beyond the 图片加载失败 above it — see [imageLoadFailureText]. */
    @Test
    fun `an unclassified failure says nothing`() {
        show(ImageLoadFailure.Unknown)

        composeRule.onNodeWithText(NOTHING_TO_SAY).assertExists()
    }

    private companion object {
        /** Stands in for the null the composable returns, which cannot itself be asserted on. */
        const val NOTHING_TO_SAY = "—"
    }
}
