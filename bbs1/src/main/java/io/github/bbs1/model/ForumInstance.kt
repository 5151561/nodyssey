package io.github.bbs1.model

import kotlinx.serialization.Serializable

/**
 * One bbs1org site the user has added.
 *
 * Serialized into DataStore as part of the saved list, so field renames are a data migration, not a
 * refactor.
 *
 * @property id Stable local identity. Selection and deletion key on this rather than on the URL so
 *   that renaming a site's address someday does not orphan whatever hangs off it.
 * @property baseUrl Origin only, no trailing slash — the output of [io.github.bbs1.data.normalizeInstanceUrl].
 * @property name What the user sees in lists; defaults to the host when they left it blank.
 */
@Serializable
data class ForumInstance(
    val id: String,
    val baseUrl: String,
    val name: String,
)
