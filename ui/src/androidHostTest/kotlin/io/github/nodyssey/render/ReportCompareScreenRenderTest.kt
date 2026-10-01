package io.github.nodyssey.render

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.core.report.QualityReportParser
import io.github.nodyssey.core.report.ReportComparison
import io.github.nodyssey.data.settings.ReportCompareEntry
import io.github.nodyssey.guard.repositoryRoot
import io.github.nodyssey.ui.compare.ReportCompareScreen
import io.github.nodyssey.ui.compare.ReportCompareUiState
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 测评对比 at phone width: three captured reports from two script families, side by side. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h2400dp")
class ReportCompareScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    private fun fixture(name: String) =
        checkNotNull(
            QualityReportParser.parse(
                File(repositoryRoot(), "shared/src/commonTest/resources/fixtures/reports/$name").readText(),
            ),
        )

    @Test
    fun `three reports compared in light`() {
        val entries =
            listOf("hardware-quality.txt", "ip-quality.txt", "nodequality-yabs.txt").mapIndexed { index, name ->
                ReportCompareEntry(
                    report = fixture(name),
                    postId = index.toLong() + 1,
                    threadTitle = name,
                    floor = "#${index + 1}",
                )
            }
        composeRule.setContent {
            PlazaTheme(darkTheme = false) {
                ReportCompareScreen(
                    state =
                    ReportCompareUiState(
                        loaded = true,
                        entries = entries,
                        selected = entries,
                        comparison = ReportComparison.of(entries.map { it.report }),
                    ),
                    onBack = {},
                    onToggle = {},
                    onRemove = {},
                    onOpenPost = { _, _ -> },
                )
            }
        }

        composeRule.onRoot().captureRender("report-compare-light")
    }
}
