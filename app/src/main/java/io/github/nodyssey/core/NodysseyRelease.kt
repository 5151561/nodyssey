package io.github.nodyssey.core

import io.github.plaza.core.update.GitHubReleaseSource

/**
 * Where *this app* publishes its own builds — not something about the forum it reads.
 *
 * Kept apart from [NodeSeekSite] for that reason, and out of `:core` because where an app publishes
 * is a fact about that app — `:core` is copied into https://github.com/5151561/plaza, which
 * publishes somewhere else entirely. `.github/workflows/release.yml` is what fills the releases page
 * this points at.
 */
object NodysseyRelease {
    const val REPOSITORY = "5151561/nodyssey"

    /**
     * What this app's APK is called on a release, so the update check downloads that file and not
     * some other attachment on the same release.
     *
     * `GitHubReleaseSource` requires this because `releases/latest` answers for a *repository*: when
     * two apps publish to one, the newest release can easily be the other one's. That is not the case
     * here today — the bbs1org client moved to https://github.com/5151561/plaza — but the parameter
     * has no default, and naming the asset is worth doing anyway: a release also carries a mapping
     * file and a checksum, and `.apk` alone is not specific enough.
     *
     * `release.yml`'s "Name the APK after the release" step is the other end of this string; the two
     * have to be changed together, and that step's comment says so.
     */
    const val ASSET_NAME_PREFIX = "nodyssey-"

    /** The releases page, for the "打不开就自己去下载" escape hatch on 关于. */
    val RELEASES_URL = GitHubReleaseSource.releasesUrl(REPOSITORY)
}
