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
     * back to null by the composition as soon as it has been handled, so that a rotation does not
     * replay a link the user has already followed.
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
        // needs the composition to go somewhere it would not have gone on its own.
        launchRequest = intent?.let(::deepLinkOf)

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

    private fun deepLinkOf(intent: Intent): LaunchRequest.OpenLink? =
        intent
            .takeIf { it.action == Intent.ACTION_VIEW }
            ?.data
            ?.toString()
            ?.let(LaunchRequest::OpenLink)

    companion object {
        const val EXTRA_OPEN_TAB = "io.github.nodyssey.OPEN_TAB"
        const val TAB_NOTIFICATIONS = "notifications"
    }
}
