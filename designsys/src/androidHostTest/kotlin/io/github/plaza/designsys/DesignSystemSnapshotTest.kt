package io.github.plaza.designsys

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.ThresholdValidator
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.plaza.core.richtext.InlineNode
import io.github.plaza.core.richtext.InlineStyle
import io.github.plaza.core.richtext.RichNode
import io.github.plaza.designsys.component.TonalTag
import io.github.plaza.designsys.richtext.RichContent
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the design system *draws*, pinned to golden images — the half of the alpha-dependency guard
 * the lockfile cannot be.
 *
 * This module deliberately rides Material 3 alpha versions, and the lockfile pins exactly which
 * alpha — but a version number says nothing about pixels, and an alpha is precisely the release
 * channel that changes them without asking. Before these goldens, a bump's visual diff was reviewed
 * by whoever happened to open the app afterwards. Now `testAndroidHostTest` compares this sample
 * against `src/androidHostTest/snapshots/` on every run; re-recording (see the build file) and
 * reviewing the PNG diff is how a wanted change lands.
 *
 * One composite sample per theme rather than a screenshot per component: the goldens exist to catch
 * *unasked-for* drift anywhere, and one tall column of typography, buttons, tags and rich text
 * covers more surface per golden than a gallery of crops. The comparison tolerates 0.5% of pixels
 * so a different host OS's font antialiasing does not read as drift; a real layout or colour change
 * moves far more than that.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class DesignSystemSnapshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the light theme draws what the golden says`() {
        composeRule.setContent {
            PlazaTheme(darkTheme = false) { Sample() }
        }

        composeRule.onRoot().captureRoboImage(
            filePath = "src/androidHostTest/snapshots/design-system-light.png",
            roborazziOptions = OPTIONS,
        )
    }

    @Test
    fun `the dark theme draws what the golden says`() {
        composeRule.setContent {
            PlazaTheme(darkTheme = true) { Sample() }
        }

        composeRule.onRoot().captureRoboImage(
            filePath = "src/androidHostTest/snapshots/design-system-dark.png",
            roborazziOptions = OPTIONS,
        )
    }

    @Composable
    private fun Sample() {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
        ) {
            Text("标题的样子", style = MaterialTheme.typography.headlineSmall)
            Text("正文的样子，和它的行距。", style = MaterialTheme.typography.bodyLarge)
            Text("辅助说明的样子", style = MaterialTheme.typography.labelMedium)
            Row {
                TonalTag(
                    text = "置顶",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                TonalTag(
                    text = "dev 测试版",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Row(Modifier.padding(top = 8.dp)) {
                Button(onClick = {}) { Text("主要动作") }
                OutlinedButton(onClick = {}, modifier = Modifier.padding(start = 8.dp)) { Text("次要动作") }
            }
            RichContent(
                nodes = SAMPLE_CONTENT,
                onLinkClick = {},
                onImageClick = {},
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    private companion object {
        val OPTIONS =
            RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(resultValidator = ThresholdValidator(0.005f)),
            )

        /** A post body in miniature: the shapes whose rendering has actually regressed before. */
        val SAMPLE_CONTENT: List<RichNode> =
            listOf(
                RichNode.Heading(2, listOf(InlineNode.Text("帖子里的小标题"))),
                RichNode.Paragraph(
                    listOf(
                        InlineNode.Text("一段正文，"),
                        InlineNode.Text("加粗的部分", InlineStyle(bold = true)),
                        InlineNode.Text("和"),
                        InlineNode.Text("行内代码", InlineStyle(code = true)),
                        InlineNode.Text("，以及"),
                        InlineNode.Link("一个链接", "https://www.nodeseek.com"),
                        InlineNode.Text("。"),
                    ),
                ),
                RichNode.Quote(
                    listOf(RichNode.Paragraph(listOf(InlineNode.Text("被引用的一句话。")))),
                ),
                RichNode.CodeBlock(code = "uname -a\nLinux vps 6.8.0", language = "bash"),
                RichNode.Divider,
            )
    }
}
