package io.github.nsreader

import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A device-level smoke journey for tab selection, configuration restoration, and root back. */
@RunWith(AndroidJUnit4::class)
class TopLevelNavigationJourneyTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun topLevelSelectionSurvivesRecreationAndBackReturnsHome() {
        navigationItem(R.string.tab_home).assertIsSelected()

        navigationItem(R.string.tab_search).performClick().assertIsSelected()
        composeRule.activityRule.scenario.recreate()
        navigationItem(R.string.tab_search).assertIsSelected()

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        navigationItem(R.string.tab_home).assertIsSelected()
    }

    @Test
    fun searchStateSurvivesSwitchingTopLevelDestinations() {
        navigationItem(R.string.tab_search).performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("保留搜索内容")

        navigationItem(R.string.tab_home).performClick()
        navigationItem(R.string.tab_search).performClick()

        composeRule.onNode(hasSetTextAction()).assertTextContains("保留搜索内容")
    }

    private fun navigationItem(@StringRes label: Int): SemanticsNodeInteraction =
        composeRule.onNode(
            hasText(composeRule.activity.getString(label)) and hasClickAction(),
        )
}
