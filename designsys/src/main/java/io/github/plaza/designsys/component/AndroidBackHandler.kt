package io.github.plaza.designsys.component

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * Runs [onBack] instead of letting the back gesture leave the screen.
 *
 * A one-line delegate to `androidx.activity.compose.BackHandler`, and its own platform-named file,
 * because that artifact is Android-only while what it does is not: Compose Multiplatform ships the
 * same function — same name, same `(enabled, onBack)` signature — from
 * `androidx.compose.ui.backhandler` in `commonMain`, and androidx publishes no such artifact
 * (`androidx.compose.ui:ui-backhandler` is a 404 on Google Maven, checked 2026-08-19). So the
 * component that needs it cannot name either one directly and stay portable; it names this.
 *
 * When this module gains its non-Android targets the file moves to `androidMain` beside an `expect`
 * — or disappears, if by then every target can reach the multiplatform one.
 */
@Composable
fun PlazaBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
) = BackHandler(enabled = enabled, onBack = onBack)
