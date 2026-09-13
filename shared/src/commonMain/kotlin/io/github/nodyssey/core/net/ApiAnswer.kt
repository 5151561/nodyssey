package io.github.nodyssey.core.net

import io.github.nodyssey.core.NodeSeekSite
import io.github.plaza.core.net.SiteError
import io.github.plaza.core.net.SiteException
import io.github.plaza.core.net.htmlTitle
import io.github.plaza.core.net.isChallengeAnswer
import io.github.plaza.core.net.isHtmlAnswer

/**
 * Throws when Cloudflare answered instead of the site.
 *
 * Called before any status handling, and that ordering is the point: a blocked call comes back as
 * 403 plus challenge HTML, and 请验证 and 请登录 send the reader down different recoveries.
 *
 * This and [throwIfNotJson] used to be copied into four places — the JSON client, both composer
 * repositories twice over — and the copies had already drifted: 发帖 tested the body for `<` after
 * the status, 编辑 and 评论 tested it before, and one of them called the result a Cloudflare
 * challenge while another did not. See [io.github.plaza.core.net.isChallengeAnswer] for what that
 * misclassification did to the reader.
 */
internal fun throwIfChallenge(
    path: String,
    body: String,
    cfMitigated: String?,
) {
    if (isChallengeAnswer(body, cfMitigated, NodeSeekSite.CONFIG.markers)) {
        // The refused address, not the site's front door: see [SiteError.Cloudflare] for the zone
        // rule that makes the difference between a web view that can clear this and one that cannot.
        throw SiteException(SiteError.Cloudflare(NodeSeekSite.absoluteUrl(path) ?: NodeSeekSite.BASE_URL))
    }
}

/**
 * Throws when the endpoint answered with a web page rather than data.
 *
 * After the status handling — see [io.github.plaza.core.net.isHtmlAnswer]. [path] goes into the
 * message because that name is the whole diagnosis: the endpoint moved, and nothing else in the app
 * can say which one.
 */
internal fun throwIfNotJson(
    path: String,
    body: String,
) {
    if (!isHtmlAnswer(body)) return
    val title = htmlTitle(body)
    throw SiteException(
        SiteError.Unparsable,
        detail = if (title == null) "$path 返回了网页而不是数据" else "$path 返回了网页而不是数据：$title",
    )
}
