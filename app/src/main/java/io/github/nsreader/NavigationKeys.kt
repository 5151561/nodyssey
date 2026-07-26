package io.github.nsreader

import androidx.navigation3.runtime.NavKey
import io.github.nsreader.ui.login.WebViewGoal
import kotlinx.serialization.Serializable

/** The four bottom-navigation destinations. */
@Serializable
data object PostListKey : NavKey

@Serializable
data object SearchKey : NavKey

@Serializable
data object NotificationsKey : NavKey

@Serializable
data object ProfileKey : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object PostComposerKey : NavKey

@Serializable
data class PostDetailKey(
    val postId: Long,
    val floor: String? = null,
) : NavKey

/**
 * Opens the restricted in-app browser for login and Cloudflare challenges.
 *
 * [goal] is what turns it from a browser into a step in a flow: it names the cookie the screen is
 * waiting for, so the screen can close itself once that cookie arrives instead of leaving the user to
 * work out that they are done.
 */
@Serializable
data class WebKey(
    val url: String,
    val title: String,
    val goal: WebViewGoal,
) : NavKey
