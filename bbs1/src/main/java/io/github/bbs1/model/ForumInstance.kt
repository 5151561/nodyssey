package io.github.bbs1.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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
 * @property session The credential for this site, or null when nobody is signed in to it. Part of
 *   the instance to everything that reads one — sites are signed in to one at a time, under
 *   different names — but `@Transient`, because it is stored in a separate file that is kept out of
 *   backups. [io.github.bbs1.data.InstanceRepository] is what joins the two back together.
 */
@Serializable
data class ForumInstance(
    val id: String,
    val baseUrl: String,
    val name: String,
    @Transient val session: InstanceSession? = null,
)

/**
 * A signed-in identity on one site.
 *
 * The token is an HMAC the server re-derives from the user's password hash, so it is a bearer
 * credential in app-private storage and nothing more: it cannot be refreshed, it dies with a password
 * change, and it is worth exactly one account on one forum.
 *
 * @property expiresAt Unix seconds, from the server. Checked before use so an obviously dead token
 *   costs a login prompt rather than a round trip that fails.
 */
@Serializable
data class InstanceSession(
    val token: String,
    val expiresAt: Long,
    val userId: Long,
    val username: String,
    val avatarUrl: String = "",
) {
    /** True once [expiresAt] has passed. A server that sent no expiry (0) is taken at its word. */
    fun isExpiredAt(nowSeconds: Long): Boolean = expiresAt in 1 until nowSeconds
}
