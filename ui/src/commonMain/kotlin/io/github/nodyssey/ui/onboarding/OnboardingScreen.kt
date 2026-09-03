package io.github.nodyssey.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.onboarding_app_links_action
import io.github.nodyssey.ui.resources.onboarding_app_links_body
import io.github.nodyssey.ui.resources.onboarding_app_links_enabled
import io.github.nodyssey.ui.resources.onboarding_app_links_title
import io.github.nodyssey.ui.resources.onboarding_composer_body
import io.github.nodyssey.ui.resources.onboarding_composer_title
import io.github.nodyssey.ui.resources.onboarding_done
import io.github.nodyssey.ui.resources.onboarding_home_boards
import io.github.nodyssey.ui.resources.onboarding_home_page_bar
import io.github.nodyssey.ui.resources.onboarding_home_reselect
import io.github.nodyssey.ui.resources.onboarding_home_sort
import io.github.nodyssey.ui.resources.onboarding_home_title
import io.github.nodyssey.ui.resources.onboarding_more
import io.github.nodyssey.ui.resources.onboarding_next
import io.github.nodyssey.ui.resources.onboarding_one_hand_body
import io.github.nodyssey.ui.resources.onboarding_one_hand_title
import io.github.nodyssey.ui.resources.onboarding_progress
import io.github.nodyssey.ui.resources.onboarding_skip
import io.github.nodyssey.ui.resources.onboarding_welcome_body
import io.github.nodyssey.ui.resources.onboarding_welcome_title
import io.github.plaza.designsys.component.PlazaBackHandler
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * 新手引导 — the five screens shown once, on the first launch after this shipped.
 *
 * It exists because of one recurring report: the blank band a second-level screen opens with is
 * taken for a bug. Nothing in the app said what that band was, and a reader who has decided the app
 * is broken does not go looking through 设置 for the explanation. So the explanation comes to them,
 * once, alongside the other things worth knowing that nobody stumbles onto — the board strip
 * rearranging under a long press, the feed reloading on a second tap of 首页, the editor's toolbar
 * being the reader's to arrange, and 站内链接, which the system will never turn on by itself.
 *
 * Five screens and no more. Every one carries 跳过, and the guide is over the moment it is pressed:
 * this is an explanation offered, not a tour anyone has to sit through. What it leaves out is on
 * 使用帮助, which the last screen names.
 *
 * @param appLinksEnabled whether the system already routes nodeseek.com links here — null where the
 * platform has no such notion. Both non-false answers drop the fourth screen: there is nothing to
 * ask of someone who has already done it, and nothing to offer where the switch does not exist.
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    appLinksEnabled: Boolean? = null,
    onOpenAppLinkSettings: () -> Unit = {},
) {
    /*
     * Fixed on the answer the guide opened with, and deliberately not re-read.
     *
     * Only `false` adds the screen: `true` means it is already done and `null` is a platform where
     * the question does not arise — see the parameter's doc. But once the screen is in, it stays
     * even after the reader comes back from the system settings having thrown the switch. Dropping
     * it at that moment would take the page out from under them and leave the pager on 首页, which
     * reads as the guide having lost their place; keeping it lets the screen show them that what
     * they just did took.
     */
    val pages =
        remember {
            buildList {
                add(OnboardingPage.WELCOME)
                add(OnboardingPage.ONE_HAND)
                add(OnboardingPage.HOME)
                add(OnboardingPage.COMPOSER)
                if (appLinksEnabled == false) add(OnboardingPage.APP_LINKS)
            }
        }
    val pagerState = rememberPagerState(pageCount = pages::size)
    val scope = rememberCoroutineScope()
    val atLast = pagerState.currentPage >= pages.lastIndex

    // Back walks the guide backwards and then leaves it, which is right from either direction it can
    // be opened: on a first launch, backing out of the first screen should put the reader in the app
    // rather than out of it, and from 使用帮助 it should put them back on the page they left.
    PlazaBackHandler {
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        } else {
            onFinish()
        }
    }

    Surface(
        /*
         * The guide is drawn over a live app — see `NodysseyRoot` — and this is what keeps a tap on
         * the empty half of a screen from landing on whatever feed row is underneath.
         *
         * It works by *hit testing*, not by consuming: a `Box` tests its children back to front and
         * stops at the first one hit, so a pointer-input node covering the whole guide means the
         * navigation host below is never even asked. The gesture then travels the ordinary path,
         * and the buttons and the pager on it behave as they would anywhere.
         *
         * Consuming instead — `awaitPointerEvent().changes.forEach { it.consume() }`, which is what
         * shipped first — kills the screen it is supposed to protect. `waitForUpOrCancellation`
         * treats a consumed change as a cancelled press, so every tap with a pixel or two of travel
         * in it, which is every tap a finger makes, was dropped. It survived review because the
         * tests rendered the guide on its own, where there is nothing to fall through to and
         * therefore nothing the handler had to do. `OnboardingOverlayTest` is the arrangement that
         * catches it.
         */
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } },
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { index ->
                OnboardingPageBody(
                    page = pages[index],
                    // The figures are loops, and a pager keeps its neighbours composed — so each one
                    // is told whether it is the page being looked at, and the rest stand still.
                    active = index == pagerState.currentPage,
                    appLinksEnabled = appLinksEnabled,
                    onOpenAppLinkSettings = onOpenAppLinkSettings,
                    showFooter = index == pages.lastIndex,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 跳过 stays on the last screen too, where it does what 开始使用 does. Taking it away
                // there would move the primary button under the finger that has been pressing 下一步.
                TextButton(onClick = onFinish) { Text(stringResource(Res.string.onboarding_skip)) }
                Spacer(Modifier.weight(1f))
                PageDots(current = pagerState.currentPage, total = pages.size)
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        if (atLast) {
                            onFinish()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            if (atLast) Res.string.onboarding_done else Res.string.onboarding_next,
                        ),
                    )
                }
            }
        }
    }
}

/** The screens, in the order they are shown; the last is dropped where it has nothing to ask. */
private enum class OnboardingPage { WELCOME, ONE_HAND, HOME, COMPOSER, APP_LINKS }

@Composable
private fun OnboardingPageBody(
    page: OnboardingPage,
    active: Boolean,
    appLinksEnabled: Boolean?,
    onOpenAppLinkSettings: () -> Unit,
    showFooter: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .readableWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // The figure illustrates the words under it and adds nothing a screen reader can use, so it
        // is one node with no text rather than a dozen empty boxes to swipe through.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(FIGURE_HEIGHT)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            when (page) {
                OnboardingPage.WELCOME -> WelcomeFigure(active)
                OnboardingPage.ONE_HAND -> OneHandFigure(active)
                OnboardingPage.HOME -> BoardStripFigure(active)
                OnboardingPage.COMPOSER -> ComposerToolbarFigure(active)
                OnboardingPage.APP_LINKS -> AppLinksFigure(active)
            }
        }
        Spacer(Modifier.height(Spacing.xl))
        Text(
            text = stringResource(page.title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.md))
        // 首页 is the one screen whose content is three unrelated gestures. Three bullets rather than
        // one paragraph, which would make them read as one procedure to be followed in order.
        val body = page.body
        if (body == null) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                HOME_LINES.forEach { line -> Bullet(stringResource(line)) }
            }
        } else {
            Text(
                text = stringResource(body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (page == OnboardingPage.APP_LINKS) {
            Spacer(Modifier.height(Spacing.lg))
            // Throwing the switch happens in the system settings and comes back as a changed
            // `appLinksEnabled`, so the button is replaced by its own outcome — rather than by a
            // toast that would be gone before the reader is back in the app.
            AnimatedVisibility(visible = appLinksEnabled == true) {
                Text(
                    text = stringResource(Res.string.onboarding_app_links_enabled),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            AnimatedVisibility(visible = appLinksEnabled != true) {
                OutlinedButton(onClick = onOpenAppLinkSettings) {
                    Text(stringResource(Res.string.onboarding_app_links_action))
                }
            }
        }
        if (showFooter) {
            Spacer(Modifier.height(Spacing.xl))
            Text(
                text = stringResource(Res.string.onboarding_more),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val HOME_LINES =
    listOf(
        Res.string.onboarding_home_boards,
        Res.string.onboarding_home_reselect,
        Res.string.onboarding_home_sort,
        Res.string.onboarding_home_page_bar,
    )

private val OnboardingPage.title: StringResource
    get() = when (this) {
        OnboardingPage.WELCOME -> Res.string.onboarding_welcome_title
        OnboardingPage.ONE_HAND -> Res.string.onboarding_one_hand_title
        OnboardingPage.HOME -> Res.string.onboarding_home_title
        OnboardingPage.COMPOSER -> Res.string.onboarding_composer_title
        OnboardingPage.APP_LINKS -> Res.string.onboarding_app_links_title
    }

/** The paragraph under the title, or null for the one screen that draws bullets instead. */
private val OnboardingPage.body: StringResource?
    get() = when (this) {
        OnboardingPage.WELCOME -> Res.string.onboarding_welcome_body
        OnboardingPage.ONE_HAND -> Res.string.onboarding_one_hand_body
        OnboardingPage.COMPOSER -> Res.string.onboarding_composer_body
        OnboardingPage.APP_LINKS -> Res.string.onboarding_app_links_body
        OnboardingPage.HOME -> null
    }

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(6.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Where the guide has got to.
 *
 * Carries the count as its description because it is the only thing that answers "how much of this
 * is left" for a screen reader: a swipe under TalkBack moves focus rather than the pager, so the
 * dots themselves are never felt.
 */
@Composable
private fun PageDots(
    current: Int,
    total: Int,
) {
    val description = stringResource(Res.string.onboarding_progress, current + 1, total)
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        repeat(total) { index ->
            val active = index == current
            val width by animateDpAsState(if (active) 20.dp else 6.dp, label = "dot")
            Box(
                modifier = Modifier
                    .width(width)
                    .height(6.dp)
                    .background(
                        color =
                        if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        shape = RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}

private val FIGURE_HEIGHT = 200.dp

@Preview
@Composable
private fun OnboardingScreenPreview() {
    PlazaTheme {
        OnboardingScreen(onFinish = {}, appLinksEnabled = false)
    }
}

@Preview
@Composable
private fun OnboardingScreenNoAppLinksPreview() {
    PlazaTheme {
        OnboardingScreen(onFinish = {}, appLinksEnabled = null)
    }
}
