package io.github.nodyssey.data.imagehost

import android.content.Context
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val NODE_IMAGE_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

/**
 * The stored half of 图床设置.
 *
 * Every test writes what it needs before reading it: DataStore is a process singleton keyed by file
 * name, and Robolectric hands every test in this class the same application context, so anything
 * left by an earlier test is still there for the next one.
 */
@RunWith(RobolectricTestRunner::class)
class ImageHostSettingsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val settings = DataStoreImageHostSettings(context)

    @Test
    fun `each host's credentials are kept apart`() = runTest {
        settings.save(ImageHostConfig(ImageHostProvider.NODE_IMAGE, token = NODE_IMAGE_KEY))
        settings.save(
            ImageHostConfig(
                provider = ImageHostProvider.LSKY_PRO,
                siteUrl = "https://img.example.com",
                token = "1|lskytoken",
            ),
        )

        assertEquals(NODE_IMAGE_KEY, settings.config(ImageHostProvider.NODE_IMAGE).first().token)
        assertEquals("1|lskytoken", settings.config(ImageHostProvider.LSKY_PRO).first().token)
        // Storing one host must not give another an address it never had.
        assertEquals("", settings.config(ImageHostProvider.NODE_IMAGE).first().siteUrl)
    }

    /** A pasted address arrives with a trailing slash half the time; every path appended starts with one. */
    @Test
    fun `a trailing slash is stripped from the site url on the way in`() = runTest {
        settings.save(
            ImageHostConfig(ImageHostProvider.EASY_IMAGE, siteUrl = "https://img.example.com/", token = "t"),
        )

        assertEquals("https://img.example.com", settings.config(ImageHostProvider.EASY_IMAGE).first().siteUrl)
    }

    @Test
    fun `the selection survives a switch away and back`() = runTest {
        settings.select(ImageHostProvider.SMMS)
        assertEquals(ImageHostProvider.SMMS, settings.selected.first())

        settings.select(ImageHostProvider.CUSTOM)
        assertEquals(ImageHostProvider.CUSTOM, settings.selected.first())
    }

    /**
     * 断开 forgets the secret and nothing else. Re-typing an address and a JSON path because a token
     * was rotated is the part nobody wants to do twice.
     */
    @Test
    fun `disconnecting a custom host clears both places a credential can live, and keeps the rest`() = runTest {
        settings.save(
            ImageHostConfig(
                provider = ImageHostProvider.CUSTOM,
                siteUrl = "https://img.example.com/api/upload",
                custom = CustomHostFields(
                    fileField = "smfile",
                    headerName = "X-Token",
                    headerValue = "secret",
                    formFields = "token=alsosecret",
                    urlPath = "data.url",
                ),
            ),
        )

        settings.disconnect(ImageHostProvider.CUSTOM)

        val config = settings.config(ImageHostProvider.CUSTOM).first()
        assertEquals("", config.custom.headerValue)
        assertEquals("", config.custom.formFields)
        assertEquals("https://img.example.com/api/upload", config.siteUrl)
        assertEquals("X-Token", config.custom.headerName)
        assertEquals("data.url", config.custom.urlPath)
        assertEquals("smfile", config.custom.fileField)
    }

    @Test
    fun `an unset custom path falls back to the field's own default, not to blank`() = runTest {
        assertEquals("url", ImageHostConfig(ImageHostProvider.CUSTOM).custom.urlPath)
        assertEquals("file", ImageHostConfig(ImageHostProvider.CUSTOM).custom.fileField)
    }

    /**
     * Before this feature there was one host and one key, in its own file under `api-key`. An upgrade
     * that ignored it would silently disconnect everybody who had already set the app up, and the
     * first they would hear of it is a failed attachment halfway through writing a post.
     */
    @Test
    fun `an existing NodeImage key is carried into the multi-host store`() = runTest {
        val migration = LegacyNodeImageKeyMigration { NODE_IMAGE_KEY }

        assertTrue("a store that has never been written must migrate", migration.shouldMigrate(emptyPreferences()))
        val migrated = migration.migrate(emptyPreferences())

        assertEquals(ImageHostProvider.NODE_IMAGE.id, migrated[stringPreferencesKey("selected")])
        assertEquals(NODE_IMAGE_KEY, migrated[stringPreferencesKey("nodeimage.token")])
        assertFalse("it must not run twice", migration.shouldMigrate(migrated))
    }

    /** A fresh install has no old file. That is not an error, and it must not leave the store unwritten. */
    @Test
    fun `a fresh install migrates to a selection with no credential`() = runTest {
        val migrated = LegacyNodeImageKeyMigration { null }.migrate(emptyPreferences())

        assertEquals(ImageHostProvider.NODE_IMAGE.id, migrated[stringPreferencesKey("selected")])
        assertNull(migrated[stringPreferencesKey("nodeimage.token")])
    }

    /** A failure reading the old file must not stop the app from having a usable store. */
    @Test
    fun `an unreadable old store still yields a selection`() = runTest {
        val migrated = LegacyNodeImageKeyMigration { error("corrupt") }.migrate(emptyPreferences())

        assertEquals(ImageHostProvider.NODE_IMAGE.id, migrated[stringPreferencesKey("selected")])
    }
}
