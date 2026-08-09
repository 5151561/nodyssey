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
