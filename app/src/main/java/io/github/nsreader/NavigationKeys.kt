package io.github.nsreader

import androidx.navigation3.runtime.NavKey
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
data class PostDetailKey(
    val postId: Long,
) : NavKey

/** Opens the in-app browser for login, Cloudflare challenges and outbound links. */
@Serializable
data class WebKey(
    val url: String,
    val title: String,
) : NavKey
