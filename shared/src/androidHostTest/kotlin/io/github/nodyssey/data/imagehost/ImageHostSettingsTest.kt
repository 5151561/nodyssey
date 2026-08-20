package io.github.nodyssey.data.imagehost

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.nodyssey.data.PreferenceStoreScope
import io.github.nodyssey.data.security.PlainCipher
import io.github.nodyssey.data.security.ReversingCipher
import io.github.nodyssey.data.security.SecretCipher
import io.github.nodyssey.data.security.UnencryptableCipher
import io.github.nodyssey.data.security.UnreadableCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val NODE_IMAGE_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

/**
 * The stored half of 图床设置.
 *
 * Each test gets its own store file, so a test that reads before writing reads an empty store rather
 * than whatever the test before it left behind.
 */
class ImageHostSettingsTest {
    @get:Rule
    val store = PreferenceStoreScope("imagehost")

    /**
     * The real cipher is the platform keystore, which a JVM test does not have — it would fail every
     * call and store nothing, and every assertion below would be about that rather than about this
     * class. See `TestCiphers.kt` for what each stand-in pins down.
     */
    private val settings = DataStoreImageHostSettings(store.dataStore, ReversingCipher)

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

    /** What a backup, or anyone reading the file, would find where the credential used to be. */
    @Test
    fun `a token is stored encrypted and the address beside it is not`() = runTest {
        settings.save(
            ImageHostConfig(ImageHostProvider.LSKY_PRO, siteUrl = "https://img.example.com", token = "1|lskytoken"),
        )

        // Reading the same file back with a cipher that does nothing shows what actually landed on disk.
        val onDisk = DataStoreImageHostSettings(store.dataStore, PlainCipher).config(ImageHostProvider.LSKY_PRO).first()
        assertTrue("the token must be stored as ciphertext", SecretCipher.isEncrypted(onDisk.token))
        assertFalse("and must not contain the token", onDisk.token.contains("lskytoken"))
        assertEquals("https://img.example.com", onDisk.siteUrl)
    }

    /**
     * A custom host's secret is in one of two fields and the app cannot tell which, so both are
     * encrypted — the same two 断开 clears. The names and paths around them stay readable.
     */
    @Test
    fun `a custom host's secret-bearing fields are encrypted and its field names are not`() = runTest {
        settings.save(
            ImageHostConfig(
                provider = ImageHostProvider.CUSTOM,
                siteUrl = "https://img.example.com/api/upload",
                custom = CustomHostFields(
                    fileField = "smfile",
                    headerName = "X-Token",
                    headerValue = "headersecret",
                    formFields = "token=formsecret",
                    urlPath = "data.url",
                ),
            ),
        )

        val onDisk = DataStoreImageHostSettings(store.dataStore, PlainCipher).config(ImageHostProvider.CUSTOM).first()
        assertTrue(SecretCipher.isEncrypted(onDisk.custom.headerValue))
        assertTrue(SecretCipher.isEncrypted(onDisk.custom.formFields))
        assertEquals("X-Token", onDisk.custom.headerName)
        assertEquals("smfile", onDisk.custom.fileField)
        assertEquals("data.url", onDisk.custom.urlPath)

        // And it all comes back the way it was typed.
        val readable = settings.config(ImageHostProvider.CUSTOM).first()
        assertEquals("headersecret", readable.custom.headerValue)
        assertEquals("token=formsecret", readable.custom.formFields)
    }

    /**
     * An install from before this store encrypted anything. Its token is a working credential, and an
     * update that stopped reading it would disconnect the host with nothing on screen to explain why.
     */
    @Test
    fun `a plaintext token from an older version is still read as the token`() = runTest {
        // PlainCipher writes unmarked, which is exactly the shape those installs have on disk.
        DataStoreImageHostSettings(store.dataStore, PlainCipher)
            .save(ImageHostConfig(ImageHostProvider.SMMS, token = "plainsmmstoken"))

        assertEquals("plainsmmstoken", settings.config(ImageHostProvider.SMMS).first().token)
    }

    /** A restored backup carries ciphertext to a phone whose keystore has no key for it. */
    @Test
    fun `a token that cannot be decrypted reads as no token, and the address survives`() = runTest {
        settings.save(
            ImageHostConfig(ImageHostProvider.EASY_IMAGE, siteUrl = "https://img.example.com", token = "gone"),
        )

        val recovered = DataStoreImageHostSettings(store.dataStore, UnreadableCipher)
            .config(ImageHostProvider.EASY_IMAGE)
            .first()
        assertEquals("", recovered.token)
        assertEquals("https://img.example.com", recovered.siteUrl)
    }

    /** Encryption failing is not a reason to fall back to plaintext. */
    @Test
    fun `a device that cannot encrypt stores no token at all`() = runTest {
        DataStoreImageHostSettings(store.dataStore, UnencryptableCipher)
            .save(ImageHostConfig(ImageHostProvider.IMGBB, token = "neverstored"))

        assertEquals("", DataStoreImageHostSettings(store.dataStore, PlainCipher).config(ImageHostProvider.IMGBB).first().token)
    }

    /**
     * The upgrade path: the credentials an install already has are encrypted on the first launch after
     * the update, not whenever the user next happens to open 图床设置 and press 保存.
     */
    @Test
    fun `credentials already in the store are encrypted once, and configuration is left alone`() = runTest {
        val existing = mutablePreferencesOf(
            ImageHostKeys.token(ImageHostProvider.NODE_IMAGE) to "plaintoken",
            ImageHostKeys.siteUrl(ImageHostProvider.LSKY_PRO) to "https://img.example.com",
            ImageHostKeys.CUSTOM_HEADER_VALUE to "plainheader",
            ImageHostKeys.CUSTOM_HEADER_NAME to "X-Token",
        )
        val migration = ImageHostSecretEncryptionMigration(ReversingCipher)

        assertTrue("plaintext in the store is what this migration is for", migration.shouldMigrate(existing))
        val migrated = migration.migrate(existing)

        assertEquals(SecretCipher.MARKER + "nekotnialp", migrated[ImageHostKeys.token(ImageHostProvider.NODE_IMAGE)])
        assertEquals(SecretCipher.MARKER + "redaehnialp", migrated[ImageHostKeys.CUSTOM_HEADER_VALUE])
        assertEquals("https://img.example.com", migrated[ImageHostKeys.siteUrl(ImageHostProvider.LSKY_PRO)])
        assertEquals("X-Token", migrated[ImageHostKeys.CUSTOM_HEADER_NAME])
        assertFalse("it must not run twice", migration.shouldMigrate(migrated))
    }

    /** A keystore that refuses must leave a working credential working, not drop it. */
    @Test
    fun `a device that cannot encrypt keeps its credentials readable`() = runTest {
        val existing = mutablePreferencesOf(ImageHostKeys.token(ImageHostProvider.SMMS) to "plaintoken")

        val migrated = ImageHostSecretEncryptionMigration(UnencryptableCipher).migrate(existing)

        assertEquals("plaintoken", migrated[ImageHostKeys.token(ImageHostProvider.SMMS)])
    }
}
