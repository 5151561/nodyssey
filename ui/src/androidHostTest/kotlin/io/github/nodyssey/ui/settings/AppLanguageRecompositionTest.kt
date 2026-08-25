package io.github.nodyssey.ui.settings

import android.os.LocaleList
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.nodyssey.data.settings.AppLanguage
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.settings_language
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * That a language can change on screen without the activity being torn down.
 *
 * This is the assertion the whole of `ProvideAppLanguage` rests on, and it is worth one test of its
 * own because what it pins is somebody else's implementation detail. Compose Resources picks a
 * bundle through `LocalComposeEnvironment`, which is `internal` — there is no handing it a language.
 * The Android actual gets at it sideways, by providing a `Configuration` that differs, and the note
 * there sets out why that has to invalidate the default environment.
 *
 * If this goes red the sideways route is gone, and 语言 has to go back to recreating the activity.
 * A compile error would not say so; only this will.
 */
@RunWith(RobolectricTestRunner::class)
class AppLanguageRecompositionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `changing the language reresolves strings with nothing recreated`() {
        composeRule.setContent {
            var language by remember { mutableStateOf(AppLanguage.SIMPLIFIED_CHINESE) }
            Column {
                ProvideAppLanguage(language) {
                    Text(stringResource(Res.string.settings_language))
                }
                Text(SWITCH, Modifier.clickable { language = AppLanguage.ENGLISH })
            }
        }

        composeRule.onNodeWithText("语言").assertIsDisplayed()
        composeRule.onNodeWithText(SWITCH).performClick()
        composeRule.onNodeWithText("Language").assertIsDisplayed()
    }

    /**
     * The cold-start frames, where the settings store has not answered yet and the language on
     * screen is whatever `attachBaseContext` already applied. Null must change nothing: not the
     * strings, and not the process default that `Accept-Language` and the number formats read —
     * substituting SYSTEM here is exactly the wrong-language flash the nullable exists to prevent.
     */
    @Test
    fun `an unanswered store leaves the applied language alone`() {
        val stored = LocaleList.forLanguageTags("en")
        LocaleList.setDefault(stored)
        composeRule.setContent {
            ProvideAppLanguage(null) {
                Text(stringResource(Res.string.settings_language))
            }
        }

        composeRule.onNodeWithText("Language").assertIsDisplayed()
        assertEquals(stored, LocaleList.getDefault())
    }

    private companion object {
        /** Latin so that it reads the same either side of the switch and never matches a label. */
        const val SWITCH = "switch"
    }
}
