package io.github.plaza.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.plaza.core.ansi.AnsiDecoder
import io.github.plaza.core.richtext.parseMarkdown
import io.github.plaza.designsys.component.BadgeChip
import io.github.plaza.designsys.component.BadgeTone
import io.github.plaza.designsys.component.GroupedColumn
import io.github.plaza.designsys.component.GroupedRow
import io.github.plaza.designsys.component.ImageFallback
import io.github.plaza.designsys.component.MetaText
import io.github.plaza.designsys.component.SectionLabel
import io.github.plaza.designsys.component.SkeletonBar
import io.github.plaza.designsys.component.SkippedImagePlaceholder
import io.github.plaza.designsys.component.ThreadRow
import io.github.plaza.designsys.component.ThreadRowTitle
import io.github.plaza.designsys.component.TonalTag
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.component.rememberClipboardCopy
import io.github.plaza.designsys.component.rememberTerminalText
import io.github.plaza.designsys.editor.EditorAction
import io.github.plaza.designsys.editor.EditorToolbar
import io.github.plaza.designsys.richtext.RichContent
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing

/**
 * `:designsys` in a desktop window.
 *
 * This is step B3 of `docs/kmp-migration-plan.md` made visible. The module it draws was, until this
 * commit, 6,600 lines of Compose that could not compile for anything but Android; what is on screen
 * here is that same code, unchanged, resolved through a JVM variant, with a Skia canvas under it
 * instead of a `View`.
 *
 * It is a probe rather than a product. Nothing ships it and nothing depends on it, so it earns its
 * place only by being run — `./gradlew :gallery:run` — and by covering the parts of the module whose
 * portability was in question rather than the parts that were obviously fine: text that had to be
 * measured, a component tree fed by `:shared`'s own parser, and the two seams (`platformSystemColorScheme`,
 * `rememberPlainTextClip`) whose desktop halves exist only for this.
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Plaza Design System",
        state = rememberWindowState(width = 900.dp, height = 1000.dp),
    ) {
        GalleryContent()
    }
}

/**
 * Everything the window shows, separated from the window so a test can compose it.
 *
 * `GalleryTest` runs this through `runComposeUiTest` on the desktop JVM and asserts that the parsed
 * body, the measured list row and the terminal text are all on screen. That is what makes this
 * module a gate rather than a demo: a window somebody forgot to open proves nothing, and CI cannot
 * open one.
 */
@Composable
fun GalleryContent() {
    var dark by remember { mutableStateOf(false) }
    var systemPalette by remember { mutableStateOf(false) }

    PlazaTheme(darkTheme = dark, useSystemPalette = systemPalette) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(selected = dark, onClick = { dark = !dark }, label = { Text("深色") })
                    // Answered by `platformSystemColorScheme`, whose desktop half returns null:
                    // switching this on has to leave the colours exactly where they are. That is
                    // the assertion — a desktop with no wallpaper palette falls back to 石墨青.
                    FilterChip(
                        selected = systemPalette,
                        onClick = { systemPalette = !systemPalette },
                        label = { Text("使用系统调色板") },
                    )
                }

                Section("标签") {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        TonalTag(
                            text = "日常",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        BadgeChip(text = "置顶", tone = BadgeTone.Primary)
                        BadgeChip(text = "已解决", tone = BadgeTone.Accent)
                        BadgeChip(text = "锁定", tone = BadgeTone.Warning)
                    }
                }

                Section("列表行") {
                    // `listAvatarSize` measures two type styles with a `TextMeasurer` and stacks
                    // the results, so a row that lines up here is a font metric that survived the
                    // move off Android's text stack.
                    ThreadRow(
                        onClick = {},
                        leading = { UserAvatar(url = null, name = "苏", size = 36.dp) },
                        title = { ThreadRowTitle(AnnotatedString("这台小鸡跑 Kotlin/Native 编译要多久")) },
                        meta = {
                            MetaText("苏打水")
                            MetaText("2 小时前")
                            MetaText("回复 18")
                        },
                    )
                    ThreadRow(
                        onClick = {},
                        leading = { UserAvatar(url = null, name = "K", size = 36.dp) },
                        title = { ThreadRowTitle(AnnotatedString("桌面端也能跑同一套组件了")) },
                        meta = {
                            MetaText("kmp")
                            MetaText("刚刚")
                        },
                    )
                }

                Section("设置行") {
                    GroupedColumn {
                        GroupedRow(title = "外观", value = if (dark) "深色" else "浅色", first = true, onClick = {})
                        GroupedRow(title = "字号", value = "标准", onClick = {})
                        GroupedRow(title = "仅 Wi-Fi 加载图片", subtitle = "省流量", last = true, onClick = {})
                    }
                }

                Section("编辑器工具栏") {
                    EditorToolbar(
                        actions = EditorAction.entries.take(8),
                        onAction = {},
                        onCustomize = {},
                    )
                }

                Section("正文渲染") {
                    // Parsed rather than hand-built: the tree comes out of `:shared`, so this
                    // section is the parser and the renderer running together on a platform
                    // neither of them was written for.
                    val nodes = remember { parseMarkdown(SAMPLE_MARKDOWN) }
                    RichContent(nodes = nodes, onLinkClick = {}, onImageClick = {})
                }

                Section("终端输出") {
                    val decoded = remember { AnsiDecoder.decode(SAMPLE_ANSI) }
                    Text(
                        text = rememberTerminalText(decoded.text, decoded.spans),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Section("图片占位") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ImageFallback(modifier = Modifier.size(96.dp, 72.dp))
                        ImageFallback(modifier = Modifier.size(96.dp, 72.dp), deferred = true)
                        SkippedImagePlaceholder(onLoad = {}, modifier = Modifier.width(220.dp))
                    }
                }

                Section("骨架屏") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        SkeletonBar(fraction = 0.9f, height = 14.dp)
                        SkeletonBar(fraction = 0.6f, height = 14.dp)
                    }
                }

                Section("剪贴板") {
                    // `rememberPlainTextClip`'s desktop `actual` wraps an AWT `StringSelection`,
                    // and `rememberCopyConfirmation`'s does nothing — the two halves of the seam
                    // `AndroidClipboard.kt` used to be the only side of. Pressing this and then
                    // pasting elsewhere is the test.
                    val copy = rememberClipboardCopy()
                    FilterChip(
                        selected = false,
                        onClick = { copy("gallery", "从桌面端复制的一行", "已复制") },
                        label = { Text("复制一行文本") },
                    )
                }

                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionLabel(title)
        content()
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private val SAMPLE_MARKDOWN =
    """
    # 迁移记录

    这段正文由 `:shared` 的 `parseMarkdown` 解析，由 `:designsys` 的 `RichContent` 绘制，
    两个模块都跑在桌面 JVM 上。

    - 列表项一
    - 列表项二，带一个 [链接](https://www.nodeseek.com)
    - **加粗**、*斜体* 与 ~~删除线~~

    > 引用块也走同一条路径。

    ```kotlin
    fun main() = application { Window(onCloseRequest = ::exitApplication) { } }
    ```
    """.trimIndent()

/** A trimmed NodeQuality-shaped snippet: enough escape sequences to exercise the decoder. */
private val SAMPLE_ANSI =
    "\u001B[1;36m基础信息\u001B[0m\n" +
        "CPU 型号   : \u001B[32mAMD EPYC 7B13\u001B[0m\n" +
        "内存       : \u001B[33m957.4 MB\u001B[0m\n" +
        "\u001B[31m硬盘 I/O   : 108 MB/s\u001B[0m\n"
