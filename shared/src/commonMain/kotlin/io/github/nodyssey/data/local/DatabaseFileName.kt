package io.github.nodyssey.data.local

import io.github.nodyssey.core.Site

/**
 * What [site]'s cache file is called. One file per site, and that is not tidiness.
 *
 * Every table in this package is keyed on a bare `postId` — `posts`, `post_details`,
 * `post_read_marks`, `post_reading_positions`, `collected_post_meta`, `offline_threads` — and post
 * ids are per-site and overlap: DeepFlood's newest thread on 2026-09-10 was `/post-38952-1`, an id
 * NodeSeek passed years ago. Sharing one file would serve one site's thread under the other's
 * title, silently, with no schema error to notice.
 *
 * Two files rather than a `site` column on six tables: no migration to write, no query to revisit,
 * and no row that can be read without its site by forgetting a `WHERE`. It also means a request
 * still in flight when the site changes writes into the file it was built against.
 *
 * NodeSeek's name is unsuffixed — see [Site.storageSuffix]. Renaming the file every installed device
 * already has would look, from the app's side, exactly like a user who had never opened it.
 */
fun databaseFileName(site: Site): String = "nodeseek${site.storageSuffix}.db"
