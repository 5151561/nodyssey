package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable

/**
 * A byte count as this platform words it — "8.8 MB", "1.2 GB".
 *
 * An `expect` rather than one shared implementation because Android already has an answer and it is
 * the one three screens have been showing: `Formatter.formatShortFileSize` is locale-aware and, since
 * API 26, counts in powers of ten. Writing a replacement would have changed every size the app
 * displays, which is not something a step that only moves files gets to do.
 *
 * Distinct from `formatBytes` in `ui/account`, which is 1024-based and deliberately so: that one
 * labels an image host's own quota, a number the host itself states in KiB.
 */
@Composable
expect fun rememberFileSizeLabel(bytes: Long): String
