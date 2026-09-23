package io.github.nodyssey.render

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.nodyssey.data.composer.ImageAttachment
import io.github.nodyssey.data.composer.UploadStatus
import io.github.nodyssey.ui.composer.FloorReference
import io.github.nodyssey.ui.composer.PostComposerScreen
import io.github.nodyssey.ui.composer.PostComposerUiState
import io.github.nodyssey.ui.composer.ReplyComposerHost
import io.github.nodyssey.ui.composer.ReplyComposerUiState
import io.github.plaza.designsys.theme.PlazaTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The two editors from boards 1d, 1e and 2c: the post page, its 格式 card, and the reply sheet with
 * the emoji panel open.
 *
 * Robolectric has no IME, so the keyboard the boards draw under the bar is simply absent — the bar
 * sits on the bottom edge instead. The 格式 card and the emoji panel are opened by tapping, the way a
 * writer would, rather than by constructing state the screen owns.
 *
 * The reply sheet is a `ModalBottomSheet`, which is a window of its own; [captureScreenRoboImage]
 * takes every window, where the other renders' `onRoot()` would only see the empty host behind it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp")
class ComposerRenderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun onlyWhenRendering() = assumeRendering()

    private fun setPost(darkTheme: Boolean) {
        composeRule.setContent {
            PlazaTheme(darkTheme = darkTheme) {
                PostComposerScreen(
                    state = POST,
                    titleState = rememberTextFieldState(POST.title),
                    bodyState = rememberTextFieldState(POST.body),
                    snackbarHostState = SnackbarHostState(),
                    onClose = {},
                    onBoardSelect = {},
                    onPermissionSelect = {},
                    onViewModeChange = {},
                    onPickImages = {},
                    onRemoveAttachment = {},
                    onRetryAttachment = {},
                    onPublish = {},
                    onVerify = {},
                    onContinueDraft = {},
                    onDiscardDraft = {},
                    onToolbarChange = {},
                    onToolbarReset = {},
                )
            }
        }
    }

    private fun setReply(darkTheme: Boolean) {
        composeRule.setContent {
            PlazaTheme(darkTheme = darkTheme) {
                ReplyComposerHost(
                    state = REPLY,
                    onDismiss = {},
                    bodyState = rememberTextFieldState(REPLY.body),
                    onClearReplyTo = {},
                    onPreviewChange = {},
                    onPickImages = {},
                    onRemoveAttachment = {},
                    onRetryAttachment = {},
                    onRetryFailedUploads = {},
                    onPublish = {},
                    onClearError = {},
                    onSignIn = {},
                    onVerify = {},
                    onToolbarChange = {},
                    onToolbarReset = {},
                    onCreateVote = { _, _, _, _, _ -> },
                    onDismissVoteCreation = {},
                    payeeUid = { null },
                    onInsertReceiveCode = { _, _, _, _ -> },
                )
            }
        }
    }

    @Test
    fun `the post editor in light`() {
        setPost(darkTheme = false)
        composeRule.onRoot().captureRender("composer-post-light")
    }

    @Test
    fun `the post editor in dark`() {
        setPost(darkTheme = true)
        composeRule.onRoot().captureRender("composer-post-dark")
    }

    @Test
    fun `the format card in light`() {
        setPost(darkTheme = false)
        composeRule.onNodeWithText("格式").performClick()
        composeRule.onRoot().captureRender("composer-format-light")
    }

    @Test
    fun `the format card in dark`() {
        setPost(darkTheme = true)
        composeRule.onNodeWithText("格式").performClick()
        composeRule.onRoot().captureRender("composer-format-dark")
    }

    @Test
    fun `the reply sheet with emoji in light`() {
        setReply(darkTheme = false)
        composeRule.onNodeWithContentDescription("表情").performClick()
        composeRule.waitForIdle()
        captureScreen("composer-reply-light")
    }

    @Test
    fun `the reply sheet with emoji in dark`() {
        setReply(darkTheme = true)
        composeRule.onNodeWithContentDescription("表情").performClick()
        composeRule.waitForIdle()
        captureScreen("composer-reply-dark")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun captureScreen(name: String) {
        captureScreenRoboImage(filePath = "build/outputs/renders/$name.png")
    }

    private companion object {
        val POST =
            PostComposerUiState(
                isSignedIn = true,
                title = "自建 NAS 一年，聊聊我踩过的那些坑和真香时刻",
                body = "折腾了一年 NAS，从最早的一块二手硬盘挂在路由器上，到现在四盘位 + 万兆内网。\n" +
                    "这里挑几个印象最深的，给准备入坑的朋友避个雷",
                boardSlug = "tech",
                boardTitle = "技术",
                draftDecisionMade = true,
                savedAtMillis = 1_700_000_000_000L,
                authorName = "我",
                attachments =
                listOf(
                    ImageAttachment("1", "content://a", "a.png", UploadStatus.UPLOADED, remoteUrl = "https://x/1.webp"),
                    ImageAttachment("2", "content://b", "b.png", UploadStatus.UPLOADING, progress = 0.62f),
                ),
            )

        val REPLY =
            ReplyComposerUiState(
                postId = 1L,
                visible = true,
                body = "不同批次这个太对了，我第一次就是同批四块一起挂了两块",
                replyTo = FloorReference(floor = 1, author = "轻舟", excerpt = "同折腾党，电源那条深有体会。补充一点：硬盘最好分批买。"),
                savedAtMillis = 1_700_000_000_000L,
            )
    }
}
