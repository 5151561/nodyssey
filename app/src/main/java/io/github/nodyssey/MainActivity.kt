package io.github.nodyssey

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import io.github.nodyssey.data.composer.PickedImage
import io.github.nodyssey.data.settings.SettingsRepository
import io.github.nodyssey.data.settings.UserSettings
import io.github.nodyssey.ui.composer.MAX_IMAGES_PER_PICK
import io.github.nodyssey.ui.navigation.TopLevelDestination
import io.github.nodyssey.ui.settings.AndroidAppLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    /*
     * An intent that arrived after this Activity was already up, waiting to be acted on once.
     *
     * A state rather than a plain field because the composition is what carries it out, and under
     * singleTask an intent delivered to a running app never passes through `onCreate` at all. Set
     * back to null by the composition as soon as it has been handled — which stops a recomposition
     * from acting on it twice, and nothing more. Surviving a *recreation* is [launchLinkOf]'s job:
     * this field dies with the Activity, so it cannot be what remembers a link is spent.
     */
    private var launchRequest by mutableStateOf<LaunchRequest?>(null)

    /**
     * The same wrapping `NodysseyApp` does, for this activity's own resources.
     *
     * An activity does not inherit the application's base context: the system builds it one from the
     * device configuration. Without this line `LocalConfiguration` here would still name the device's
     * language while every string on screen was drawn in the chosen one — which is what
     * `rememberGroupedNumber` reads to pick a thousands separator.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AndroidAppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // First frame only, styled from the OS's night mode — the best guess available before the
        // settings have loaded. `SystemBarsMatchTheme` inside `NodysseyRoot` re-styles with the
        // theme the app actually resolved, which can disagree with the OS (主题外观 forced 深色/浅色).
        enableEdgeToEdge()

        val container = (application as NodysseyApp).container
        // Only a poll notification carries the extra; a cold start from it should land on 通知.
        // A saved UI state still wins — see the rememberSaveable inside MainNavigation.
        val initialTab =
            if (intent?.getStringExtra(EXTRA_OPEN_TAB) == TAB_NOTIFICATIONS) {
                TopLevelDestination.NOTIFICATIONS
            } else {
                TopLevelDestination.HOME
            }
        // A cold start from a notification is already covered by `initialTab` above; a link, a share,
        // a selected word or a launcher shortcut needs the composition to go somewhere it would not
        // have gone on its own — and only on a start that is not a recreation. See [launchRequestOf].
        launchRequest =
            launchRequestOf(intent, isRecreation = savedInstanceState != null, contentResolver, packageName)
        val initialSettings = readSettingsForFirstFrame(container.settingsRepository)

        setContent {
            NodysseyRoot(
                container = container,
                initialSettings = initialSettings,
                initialTab = initialTab,
                launchRequest = launchRequest,
                onLaunchRequestHandled = { launchRequest = null },
            )
        }
    }

    /*
     * Under singleTask every later intent lands here — the notification tap that used to only bring
     * the task forward without switching tab, and every site link the system hands us.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchRequest = requestOf(intent, contentResolver, packageName)
    }

    companion object {
        /** Read by a notification tap and by the 通知 launcher shortcut (`res/xml/shortcuts.xml`). */
        const val EXTRA_OPEN_TAB = "io.github.nodyssey.OPEN_TAB"
        const val TAB_NOTIFICATIONS = "notifications"

        /** Which launcher shortcut this is; the values are spelled again in `res/xml/shortcuts.xml`. */
        const val EXTRA_SHORTCUT = "io.github.nodyssey.SHORTCUT"
        const val SHORTCUT_SEARCH = "search"
        const val SHORTCUT_COMPOSE = "compose"
    }
}

/**
 * The stored settings, fetched before the first frame is composed.
 *
 * The store is read off the disk asynchronously, so a composition that starts without an answer
 * paints its first frame in the factory settings and then swaps to the reader's — visibly, as a
 * flash of 石墨青 under whatever theme they actually chose. Blocking here rather than letting that
 * frame out costs nothing on screen: this runs before `setContent`, so what is up during the wait is
 * the launch window the system has been showing since the icon was tapped.
 *
 * [SETTINGS_READ_TIMEOUT_MS] is the promise that this cannot become an ANR on a device where the
 * read is pathologically slow. Timing out is not a failure case: null is what the composition
 * already handles, and it puts the app back exactly where it was before this function existed.
 */
private fun readSettingsForFirstFrame(repository: SettingsRepository): UserSettings? =
    runBlocking { withTimeoutOrNull(SETTINGS_READ_TIMEOUT_MS) { repository.settings.first() } }

/** Long enough for a slow disk, far short of the five seconds that make an ANR. */
private const val SETTINGS_READ_TIMEOUT_MS = 1_000L

/**
 * Where an intent asks the app to go, or null when it asks for nothing.
 *
 * Everything here arrives through an exported Activity, so every extra is something another app
 * wrote: text is capped, only `content://` pictures are taken, and never more of them than one pick
 * in the editor may add. [contentResolver] answers a picture's type and name, and is null in tests,
 * where the intent's own type has to be enough. [ownPackage] is this app's, whose own providers a
 * share is not allowed to name — the share cache `rememberShareImage` writes is one of them.
 */
internal fun requestOf(
    intent: Intent,
    contentResolver: ContentResolver? = null,
    ownPackage: String = "",
): LaunchRequest? =
    when (intent.action) {
        Intent.ACTION_VIEW -> deepLinkOf(intent) ?: shortcutOf(intent)
        Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> shareOf(intent, contentResolver, ownPackage)
        Intent.ACTION_PROCESS_TEXT -> selectedTextOf(intent)
        else -> null
    } ?: intent
        .takeIf { it.getStringExtra(MainActivity.EXTRA_OPEN_TAB) == MainActivity.TAB_NOTIFICATIONS }
        ?.let { LaunchRequest.OpenTab(TopLevelDestination.NOTIFICATIONS) }

/** The screen a site link asks for, or null when the intent carries no link. */
internal fun deepLinkOf(intent: Intent): LaunchRequest.OpenLink? =
    intent
        .takeIf { it.action == Intent.ACTION_VIEW }
        ?.data
        ?.toString()
        ?.let(LaunchRequest::OpenLink)

/** 搜索 or 发帖 from the launcher's long-press menu. 通知 rides [MainActivity.EXTRA_OPEN_TAB] instead. */
private fun shortcutOf(intent: Intent): LaunchRequest? =
    when (intent.getStringExtra(MainActivity.EXTRA_SHORTCUT)) {
        MainActivity.SHORTCUT_SEARCH -> LaunchRequest.Search(query = null)
        MainActivity.SHORTCUT_COMPOSE -> LaunchRequest.OpenComposer
        else -> null
    }

/** 在 NodeSeek 搜索 from another app's text selection. A blank selection asks for nothing. */
private fun selectedTextOf(intent: Intent): LaunchRequest.Search? =
    intent
        .getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
        ?.toString()
        ?.trim()
        ?.take(MAX_SEARCH_QUERY_LENGTH)
        ?.takeIf(String::isNotEmpty)
        ?.let { LaunchRequest.Search(query = it) }

/**
 * A share into a new post. Null when nothing usable came with it — a share of only a video, say, or
 * of a picture whose address the app could not read anyway.
 */
private fun shareOf(
    intent: Intent,
    contentResolver: ContentResolver?,
    ownPackage: String,
): LaunchRequest.ShareToComposer? {
    val streams =
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        }
    val images =
        streams
            .asSequence()
            .filterNotNull()
            .filter { it.scheme == ContentResolver.SCHEME_CONTENT }
            // Our own providers are ours to read, which is exactly why another app must not be able
            // to point the upload at them.
            .filter { ownPackage.isEmpty() || it.authority?.startsWith(ownPackage) != true }
            .filter { uri -> isImage(intent.type, contentResolver?.let { runCatching { it.getType(uri) }.getOrNull() }) }
            .distinct()
            .take(MAX_IMAGES_PER_PICK)
            .map { uri -> PickedImage(source = uri.toString(), name = displayNameOf(uri, contentResolver)) }
            .toList()
    val text =
        intent
            .getCharSequenceExtra(Intent.EXTRA_TEXT)
            ?.toString()
            ?.trim()
            ?.take(MAX_SHARED_TEXT_LENGTH)
            ?.takeIf(String::isNotEmpty)
    val title =
        intent
            .getStringExtra(Intent.EXTRA_SUBJECT)
            ?.trim()
            ?.take(MAX_SHARED_TITLE_LENGTH)
            ?.takeIf(String::isNotEmpty)
    if (images.isEmpty() && text == null) return null
    return LaunchRequest.ShareToComposer(title = title, text = text, images = images)
}

/**
 * Whether a shared stream is a picture: by the provider's own answer when there is one, and by the
 * intent's declared type otherwise. A mixed share declares a wildcard type, which says nothing either way,
 * so without the provider's answer it is refused rather than guessed.
 */
private fun isImage(intentType: String?, providerType: String?): Boolean =
    (providerType ?: intentType)?.startsWith("image/") == true

private fun displayNameOf(uri: Uri, contentResolver: ContentResolver?): String {
    val fromProvider =
        contentResolver?.let { resolver ->
            runCatching {
                resolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull()
        }
    return fromProvider?.takeIf(String::isNotBlank)
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)
        ?: FALLBACK_IMAGE_NAME
}

/**
 * What a starting Activity still has to act on, which on a recreation is nothing.
 *
 * `getIntent` answers with whatever the task was last handed — `onNewIntent`'s `setIntent` included
 * — and the system hands that same intent to every recreation of the Activity. So `onCreate`
 * reading it unguarded meant a rotation replayed a link the reader had followed once and long since
 * walked away from: the tab was switched back to 首页 and the thread pushed onto 首页's stack a
 * second time, underneath whatever they were actually reading. Back then popped that stack instead
 * of theirs, which is what "返回直接回到首页" was. A share replayed the same way would add its
 * pictures to the editor a second time.
 *
 * A non-null `savedInstanceState` is the platform saying this composition has its place saved, and
 * that saved back stack already holds wherever the intent took them the first time. Process death is
 * the same answer for the same reason — restoring the stack is what re-opens the thread, not
 * following the link again.
 */
internal fun launchRequestOf(
    intent: Intent?,
    isRecreation: Boolean,
    contentResolver: ContentResolver? = null,
    ownPackage: String = "",
): LaunchRequest? = if (isRecreation) null else intent?.let { requestOf(it, contentResolver, ownPackage) }

/** A selection longer than this is a paragraph, not something anybody meant to search for. */
private const val MAX_SEARCH_QUERY_LENGTH = 100

/** Generous for a post, and a ceiling on what another app can push into the editor in one go. */
private const val MAX_SHARED_TEXT_LENGTH = 20_000

private const val MAX_SHARED_TITLE_LENGTH = 200

/** Only reached when the provider answers with no name and the address has no last segment. */
private const val FALLBACK_IMAGE_NAME = "image"
