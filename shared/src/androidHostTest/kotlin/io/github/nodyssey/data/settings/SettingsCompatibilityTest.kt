package io.github.nodyssey.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Android DataStore 根据 SDK 版本选择文件替换方式，测试需要真实的 Android 版本信息。
@RunWith(RobolectricTestRunner::class)
class SettingsCompatibilityTest {
    @get:Rule
    val directory = TemporaryFolder()

    @Test
    fun `旧评论模式偏好被忽略且其他设置仍可保存`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            directory.root.resolve("settings.preferences_pb")
        }
        val settings = SettingsRepository(dataStore)
        val initial = settings.settings.first()
        dataStore.edit { it[booleanPreferencesKey("default_comment_tree")] = true }
        assertEquals(initial, settings.settings.first())
        settings.setHomePageBar(false)
        assertEquals(initial.copy(homePageBar = false), SettingsRepository(dataStore).settings.first())
    }
}
