package io.github.bbs1

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeKey : NavKey

@Serializable
data object InstancesKey : NavKey

/**
 * One thread on the current site. Only the id rides in the key — the base URL is the current
 * instance's, resolved where the entry is built, so a restored stack cannot pin a stale site.
 */
@Serializable
data class TopicKey(val id: Long) : NavKey

/** Signing in to the current site. */
@Serializable
data object LoginKey : NavKey

/**
 * Writing a new thread on the current site.
 *
 * @property forumId The board the feed was filtered to when the composer opened, so the picker starts
 *   where the reader already was. Null — the "全部" filter — means the composer picks the first board
 *   this account may post in.
 */
@Serializable
data class ComposeTopicKey(val forumId: Long?) : NavKey
