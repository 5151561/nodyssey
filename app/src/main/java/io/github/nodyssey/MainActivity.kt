package io.github.nodyssey

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.nodyssey.ui.navigation.TopLevelDestination
import io.github.nodyssey.ui.settings.AndroidAppLanguage

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
        // A cold start from a notification is already covered by `initialTab` above; only a link
        // needs the composition to go somewhere it would not have gone on its own — and only on a
        // start that is not a recreation. See [launchLinkOf].
        launchRequest = launchLinkOf(intent, isRecreation = savedInstanceState != null)

        setContent {
            NodysseyRoot(
                container = container,
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
        launchRequest =
            deepLinkOf(intent)
                ?: intent
                    .takeIf { it.getStringExtra(EXTRA_OPEN_TAB) == TAB_NOTIFICATIONS }
                    ?.let { LaunchRequest.OpenTab(TopLevelDestination.NOTIFICATIONS) }
    }

    companion object {
        const val EXTRA_OPEN_TAB = "io.github.nodyssey.OPEN_TAB"
        const val TAB_NOTIFICATIONS = "notifications"
    }
}

/** The screen an intent asks for, or null when it is not asking for one. */
internal fun deepLinkOf(intent: Intent): LaunchRequest.OpenLink? =
    intent
        .takeIf { it.action == Intent.ACTION_VIEW }
        ?.data
        ?.toString()
        ?.let(LaunchRequest::OpenLink)

/**
 * The link a starting Activity still has to act on, which on a recreation is none.
 *
 * `getIntent` answers with whatever the task was last handed — `onNewIntent`'s `setIntent` included
 * — and the system hands that same intent to every recreation of the Activity. So `onCreate`
 * reading it unguarded meant a rotation replayed a link the reader had followed once and long since
 * walked away from: the tab was switched back to 首页 and the thread pushed onto 首页's stack a
 * second time, underneath whatever they were actually reading. Back then popped that stack instead
 * of theirs, which is what "返回直接回到首页" was.
 *
 * A non-null `savedInstanceState` is the platform saying this composition has its place saved, and
 * that saved back stack already holds wherever the link took them the first time. Process death is
 * the same answer for the same reason — restoring the stack is what re-opens the thread, not
 * following the link again.
 */
internal fun launchLinkOf(intent: Intent?, isRecreation: Boolean): LaunchRequest.OpenLink? =
    if (isRecreation) null else intent?.let(::deepLinkOf)
