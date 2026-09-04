package io.github.nodyssey

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.tab_home
import io.github.nodyssey.ui.resources.tab_search
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** A device-level smoke journey for tab selection, configuration restoration, and root back. */
@RunWith(AndroidJUnit4::class)
class TopLevelNavigationJourneyTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    /**
     * 新手引导 out of the way, then the activity — which is the whole reason this is a chain.
     *
     * `OnboardingScreen` is drawn *over* `MainNavigation` on a launch that has not seen it (see
     * `NodysseyRoot`), and it covers the app with a pointer-input node so that a tap on the empty
     * half of the guide cannot land on the feed underneath. A fresh install is exactly that launch:
     * every journey here then taps a tab that is on screen, visible in the semantics tree, and
     * unreachable — the tap appears to land and nothing happens.
     *
     * Marked seen before the activity starts rather than clicked through from inside the test: the
     * guide has its own tests (`OnboardingScreenTest`, `OnboardingOverlayTest`), and walking five
     * pages to reach the tab bar would make every journey in this file depend on the guide's layout.
     * A [RuleChain] rather than an `@Before`, because the compose rule launches [MainActivity] as it
     * is applied, which is earlier than any `@Before` runs.
     */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(OnboardingAlreadySeen()).around(composeRule)

    @Test
    fun topLevelSelectionSurvivesRecreationAndBackReturnsHome() {
        navigationItem(Res.string.tab_home).assertIsSelected()

        navigationItem(Res.string.tab_search).performClick().assertIsSelected()
        composeRule.activityRule.scenario.recreate()
        navigationItem(Res.string.tab_search).assertIsSelected()

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        navigationItem(Res.string.tab_home).assertIsSelected()
    }

    @Test
    fun searchStateSurvivesSwitchingTopLevelDestinations() {
        navigationItem(Res.string.tab_search).performClick()
        searchInputField().performTextInput("保留搜索内容")

        navigationItem(Res.string.tab_home).performClick()
        navigationItem(Res.string.tab_search).performClick()

        searchInputField().assertTextContains("保留搜索内容")
    }

    /**
     * The search screen has one field and focuses it on arrival, so waiting for the focused one is
     * also waiting for the screen to be ready: matching on `hasSetTextAction()` alone can land on
     * the frame before the field exists, which surfaces as "Failed to perform text input" and names
     * the wrong cause.
     */
    @OptIn(ExperimentalTestApi::class)
    private fun searchInputField(): SemanticsNodeInteraction {
        composeRule.waitUntilExactlyOneExists(hasSetTextAction() and isFocused())
        return composeRule.onNode(hasSetTextAction() and isFocused())
    }

    // `runBlocking` around a `suspend` accessor, which is the only way to read a Compose Resource
    // outside a composition. The label is the tab's text, so the alternative is hard-coding it here
    // and finding out from a failed match rather than a failed compile when it changes.
    private fun navigationItem(label: StringResource): SemanticsNodeInteraction =
        composeRule.onNode(
            hasText(runBlocking { getString(label) }) and hasClickAction(),
        )
}

/**
 * Writes 新手引导's "seen" flag through the app's own settings store, before anything is launched.
 *
 * The store rather than a test double: it is what `NodysseyRoot` reads, and the instrumentation runs
 * in the app's own process, so this is the same DataStore the guide would have written itself.
 */
private class OnboardingAlreadySeen : ExternalResource() {
    override fun before() {
        val app = ApplicationProvider.getApplicationContext<NodysseyApp>()
        runBlocking { app.container.settingsRepository.setOnboardingSeen(true) }
    }
}
