package io.github.nodyssey.ui.settings

import androidx.compose.runtime.Composable
import io.github.nodyssey.core.Site

/**
 * Hands back the action that moves the app to another [Site].
 *
 * **Why this is a platform call and not a repository write.** [io.github.nodyssey.core.ActiveSite] is
 * read by the whole graph — the database file's name, every URL, the trusted-host set — and most of
 * those readers cached their answer when they were built. Flipping it under a running graph would
 * leave repositories issuing requests to the new site and writing the rows into the old site's
 * database, for as long as their in-flight coroutines lasted. That window is short, silent, and
 * produces one site's threads under the other's ids, which is the worst way for this to go wrong.
 *
 * So the choice is stored and the app is started again from it. What "started again" means is the
 * platform's business, and the two platforms genuinely differ — see the actuals.
 */
@Composable
expect fun rememberSiteSwitch(): (Site) -> Unit

/**
 * Whether [rememberSiteSwitch] takes effect on its own, or only at the next launch.
 *
 * The switcher shows this to the reader rather than deciding for them: an app that restarts itself
 * without warning and an app that asks to be reopened are two different things to be handed, and
 * neither should be discovered by watching what happens.
 */
expect val siteSwitchRestartsApp: Boolean
