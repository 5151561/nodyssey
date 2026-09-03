package io.github.nodyssey

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import io.github.nodyssey.data.composer.PostEditTarget
import io.github.nodyssey.ui.login.WebViewGoal
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.net.URI
import java.util.jar.JarFile

/**
 * Every destination round-trips through the configuration the back stack actually uses.
 *
 * [NavKeySavedStateConfiguration] is a hand-written list of `subclass()` registrations, and nothing
 * makes the compiler check it against the destinations that exist — a key declared but not
 * registered compiles, navigates, and only fails when the process is killed and the stack it was on
 * tries to come back. `PostDetailKeySavedStateTest` next door pins one key's *contents*; this pins
 * the list itself.
 *
 * Two tests rather than one because they fail for different reasons. The round trip encodes each key
 * *as a [NavKey]*, which is what `rememberNavBackStack` does and what makes the registration matter
 * — a concrete-typed encode would pass whether or not the key were on the list. The scan is what
 * catches a destination nobody added here either: it reads the compiled classes rather than a second
 * hand-written list, so it grows on its own.
 */
@RunWith(RobolectricTestRunner::class)
class NavKeySavedStateConfigurationTest {
    // `getPolymorphic` is how the module answers "is this one on the list" without an instance.
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `every declared navigation key is registered`() {
        val declared = declaredNavKeys()

        // If the scan ever finds nothing — a packaging change puts the classes in a jar, say — an
        // empty result would otherwise read as "all registered" and the test would go quiet.
        assertTrue(
            "the class scan found ${declared.size} keys, which is too few to be the whole set",
            declared.size >= 30,
        )
        assertEquals(
            "declared in NavigationKeys.kt but missing from NavKeySavedStateConfiguration",
            emptyList<String>(),
            declared.filter { name ->
                NavKeySavedStateConfiguration.serializersModule.getPolymorphic(NavKey::class, name) == null
            },
        )
    }

    @Test
    fun `every navigation key round-trips as a NavKey`() {
        EVERY_KEY.forEach { key ->
            val restored =
                decodeFromSavedState<NavKey>(
                    encodeToSavedState<NavKey>(key, NavKeySavedStateConfiguration),
                    NavKeySavedStateConfiguration,
                )

            assertEquals(key, restored)
        }
        assertEquals(
            "one instance per destination, so the round trip covers all of them",
            declaredNavKeys().size,
            EVERY_KEY.size,
        )
    }
}

/**
 * The keys as the compiler sees them, by their serial names — which are their fully-qualified class
 * names, no `@SerialName` anywhere.
 *
 * Reads the compiled classes rather than a list, so a destination added tomorrow is in it without
 * anyone remembering to say so. They are all top level in one package, which is what makes a flat
 * listing enough.
 *
 * Located by asking the class loader for one key it already knows, rather than through
 * `codeSource` — under Robolectric's sandbox loader that comes back empty, and an empty scan is a
 * scan that agrees with everything. The two branches are the two shapes the answer takes: a
 * directory during a plain JVM run, a jar here, because this module's classes reach the test runtime
 * through `bundleAndroidMainClassesToRuntimeJar`.
 */
private fun declaredNavKeys(): List<String> {
    val loader = PostListKey::class.java.classLoader ?: return emptyList()
    val marker = loader.getResource("$PACKAGE_PATH/PostListKey.class") ?: return emptyList()
    val entries =
        when (marker.protocol) {
            "file" ->
                File(marker.toURI()).parentFile?.listFiles().orEmpty().filter { it.isFile }.map { it.name }

            "jar" ->
                JarFile(File(URI(marker.path.substringBefore("!")))).use { jar ->
                    jar
                        .entries()
                        .toList()
                        .map { it.name }
                        .filter { it.startsWith("$PACKAGE_PATH/") }
                        .map { it.removePrefix("$PACKAGE_PATH/") }
                        .filter { !it.contains('/') }
                }

            else -> emptyList()
        }
    return entries
        .filter { it.endsWith(".class") }
        .map { "io.github.nodyssey." + it.removeSuffix(".class") }
        .filter { name ->
            // `false` so a screen's companion object does not run its initializer just for being
            // looked at.
            val type = runCatching { Class.forName(name, false, loader) }.getOrNull()
            type != null && NavKey::class.java.isAssignableFrom(type) && !type.isInterface
        }.sorted()
}

private const val PACKAGE_PATH = "io/github/nodyssey"

/**
 * One instance of each destination, with the optional fields filled rather than defaulted — a field
 * left at its default round-trips even when the format drops it.
 */
private val EVERY_KEY: List<NavKey> =
    listOf(
        PostListKey,
        SearchKey,
        NotificationsKey,
        ProfileKey,
        SettingsKey,
        ThemeSettingsKey,
        DynamicColorKey,
        NotificationSettingsKey,
        ProxySettingsKey,
        DohSettingsKey,
        NetworkCheckKey,
        ImageHostKey,
        AboutAppKey,
        AboutCommunityKey,
        PrivacyKey,
        ChangelogKey,
        HelpKey,
        OpenSourceLicensesKey,
        AccountSettingsKey,
        AccountProfileFieldsKey,
        AccountSecurityKey,
        AccountContactKey,
        AccountBlockListKey,
        AccountPreferencesKey,
        FollowKey,
        BookmarksKey,
        ReadHistoryKey,
        CreditKey,
        StardustKey,
        CommunityToolsKey,
        AwardKey,
        LuckyKey,
        InviteKey,
        RulingKey,
        PostComposerKey(
            edit = PostEditTarget(postId = 703863, commentId = 127, page = 4, isOpeningPost = false),
        ),
        MessageThreadKey(uid = 42, userName = "someone"),
        PostDetailKey(
            postId = 703863,
            floor = "#127",
            page = 4,
            preview =
            ThreadPreview(
                title = "NodeSeek 签到脚本更新",
                authorName = "someone",
                avatarUrl = "https://www.nodeseek.com/avatar/1.png",
                categoryTitle = "技术",
                categorySlug = "tech",
                isAwarded = true,
            ),
        ),
        UserSpaceKey(uid = 42, isSelf = true, openCollections = true),
        AssetsKey,
        ImageViewerKey(urls = listOf("https://example.invalid/a.png", "https://example.invalid/b.png"), index = 1),
        WebKey(url = "https://www.nodeseek.com/signIn.html", title = "Nodyssey", goal = WebViewGoal.SIGN_IN),
        SignInKey,
    )
