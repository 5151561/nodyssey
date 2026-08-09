package io.github.nodyssey.core

import io.github.plaza.core.update.GitHubReleaseSource

/**
 * Where *this app* publishes its own builds — not something about the forum it reads.
 *
 * Kept apart from [NodeSeekSite] for that reason, and out of `:core` because a second app in this
 * repository publishes to a repository of its own. `.github/workflows/release.yml` is what fills the
 * releases page this points at.
 */
object NodysseyRelease {
    const val REPOSITORY = "5151561/nodyssey"

    /** The releases page, for the "打不开就自己去下载" escape hatch on 关于. */
    val RELEASES_URL = GitHubReleaseSource.releasesUrl(REPOSITORY)
}
