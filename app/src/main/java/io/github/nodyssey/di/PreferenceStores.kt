package io.github.nodyssey.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import io.github.nodyssey.core.ActiveSite
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

/**
 * 加密 DNS's own file rather than a corner of the proxy's.
 *
 * They are two settings that happen to sit in the same section of 设置: one decides where a request
 * goes, the other how a name becomes an address, and either is useful with the other switched off.
 */
internal val Context.dnsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "dns",
)

/*
 * The two draft stores are per-site; everything else on this page is not.
 *
 * A draft is keyed by the post it belongs to — `draft-$postId` in `CommentComposerRepository` — and
 * post ids are per-site and overlap, exactly as they do in the database (see `databaseFileName`).
 * One file for both sites would offer a reader the wrong site's half-written reply under the right
 * thread's title.
 *
 * 主题, 语言, 代理, DNS and the image host stay shared on purpose: none of them is a fact about a
 * forum, and a reader who set 字体大小 once should not have to set it again on the other site.
 *
 * The suffix is empty for NodeSeek, so these are still the files already on every installed device —
 * see the note above about names being load-bearing, and `Site.storageSuffix`. `ActiveSite` is
 * installed in `attachBaseContext`, which is before anything can touch this file.
 */
internal val Context.postComposerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "post-composer${ActiveSite.current.storageSuffix}",
)

internal val Context.commentComposerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "comment-composer${ActiveSite.current.storageSuffix}",
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
