package io.github.nodyssey.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import io.github.nodyssey.data.imagehost.ImageHostSecretEncryptionMigration
import io.github.nodyssey.data.imagehost.LEGACY_NODE_IMAGE_KEY
import io.github.nodyssey.data.imagehost.LegacyNodeImageKeyMigration
import io.github.nodyssey.platform.KeystoreSecretCipher
import kotlinx.coroutines.flow.first

/**
 * Every `DataStore` file the app owns, and the only place a `Context` is needed to open one.
 *
 * They are gathered here rather than sitting beside the repositories that read them because opening
 * a file is the one part of storing a setting that is about Android: `preferencesDataStore` is a
 * `Context` extension, it decides where on the filesystem the file goes, and it enforces one
 * instance per name per process. What is *in* the file — the keys, the defaults, the migrations —
 * is a fact about the app, and that stayed with the repository.
 *
 * The names are load-bearing. Each one is an existing file on every installed device; renaming one
 * is indistinguishable, from the app's side, from the user never having opened the screen.
 */
internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
)

internal val Context.proxyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "proxy",
)

internal val Context.postComposerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "post-composer",
)

internal val Context.commentComposerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "comment-composer",
)

internal val Context.offlineDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "offline",
)

internal val Context.imageHostDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "imagehost",
    produceMigrations = { context ->
        listOf(
            LegacyNodeImageKeyMigration {
                context.legacyNodeImageDataStore.data.first()[LEGACY_NODE_IMAGE_KEY]
            },
            // After the one above, not before: the key it copies in arrives in plaintext, and this is
            // what turns it into ciphertext on the same first read.
            ImageHostSecretEncryptionMigration(KeystoreSecretCipher()),
        )
    },
)

/**
 * Where the NodeImage key lived when nodeimage.com was the only host the app could talk to.
 *
 * Kept declared for exactly one reason: [LegacyNodeImageKeyMigration] reads it once. Deleting the
 * declaration would make every existing install look like a fresh one with no host connected.
 */
private val Context.legacyNodeImageDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nodeimage",
)
