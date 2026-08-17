package io.github.nodyssey.core

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
    const val RELEASES_URL = "https://github.com/$REPOSITORY/releases"

    /**
     * Where the update manifests live: the `updates` branch, served by GitHub Pages.
     *
     * A branch of this repository rather than the API or a release asset. `releases/latest/download/`
     * would have served the stable channel with no extra machinery, but it points at the newest
     * *non-prerelease* by definition, so the dev channel would have needed somewhere else anyway — and
     * two channels reachable the same way is worth more than saving a branch.
     *
     * Pages rather than `raw.githubusercontent.com`, which serves the same branch and was the first
     * choice: raw applies its own per-address throttle, and on the proxy exit this app was tested
     * behind it answers 429 to everyone sharing that address — the same failure the GitHub API was
     * abandoned for, one host over. `<user>.github.io` is the Pages CDN and answered in under a second
     * on that same connection. **Pages has to stay enabled for the `updates` branch in the repository
     * settings**; with it off these files 404 and the app says so rather than reporting 已是最新.
     *
     * `.github/workflows/release.yml` writes the three files; `UpdateManifestSource` reads them.
     */
    const val UPDATES_BASE_URL = "https://5151561.github.io/nodyssey/"
}
