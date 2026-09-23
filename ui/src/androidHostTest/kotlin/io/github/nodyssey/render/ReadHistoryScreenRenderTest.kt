package io.github.nodyssey.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.nodyssey.data.ReadHistoryEntry
import io.github.nodyssey.ui.history.ReadHistoryScreen
import io.github.nodyssey.ui.history.ReadHistoryUiState
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Board 8f — 浏览历史, a card per day, in both themes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class ReadHistoryScreenRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    @Composable
    private fun Screen(darkTheme: Boolean) {
        PlazaTheme(darkTheme = darkTheme) {
            ReadHistoryScreen(
                state = ReadHistoryUiState(isLoading = false, entries = ENTRIES),
                onBack = {},
                onPostClick = {},
                onRemove = {},
                onRestore = {},
                onClearAll = {},
                onLimitChange = {},
                nowMillis = NOW,
            )
        }
    }

    @Test
    fun `history in light`() {
        composeRule.setContent { Screen(darkTheme = false) }

        composeRule.onRoot().captureRender("history-light")
    }

    @Test
    fun `history in dark`() {
        composeRule.setContent { Screen(darkTheme = true) }

        composeRule.onRoot().captureRender("history-dark")
    }

    private companion object {
        /** Midday, so "an hour ago" and "three hours ago" are both still today in any zone near UTC+8. */
        const val NOW = 1_800_000_000_000L
        const val HOUR = 60 * 60 * 1000L

        val ENTRIES =
            listOf(
                ReadHistoryEntry(1, "四盘位 NAS 选 ZFS 还是 Btrfs？跑了一周的对比", "codemonkey", null, "技术", 88, NOW - HOUR),
                ReadHistoryEntry(2, "Caddy 反代多站点配置备忘", "kvm_fan", null, "Dev", 9, NOW - 2 * HOUR),
                ReadHistoryEntry(3, "搬瓦工 DC9 CN2 GIA 年付测评", "轻舟", null, "测评", 214, NOW - 3 * HOUR),
                ReadHistoryEntry(4, "低功耗 NAS 主板推荐，N100 还值得买吗", "省钱达人", null, "技术", 52, NOW - 26 * HOUR),
                ReadHistoryEntry(5, "出一台 HK 2C4G 年付，可小刀", "省钱达人", null, "交易", 17, NOW - 30 * HOUR),
                ReadHistoryEntry(6, "周末去爬了趟梧桐山", "homelab_er", null, "日常", 31, NOW - 4 * 24 * HOUR),
            )
    }
}
