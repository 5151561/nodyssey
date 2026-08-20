package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler

/**
 * Runs [onBack] instead of letting the back gesture leave the screen.
 *
 * This was `AndroidBackHandler.kt`, a one-line delegate to `androidx.activity.compose.BackHandler`,
 * and it was its own platform-named file because that artifact is Android-only while what it does is
 * not. It is common now, which is the outcome that file's comment named as the good one: Compose
 * Multiplatform ships the same function — same name, same `(enabled, onBack)` signature — from
 * `androidx.compose.ui.backhandler`, and on Android it reaches the same `OnBackPressedDispatcher`
 * the previous implementation did.
 *
 * The module's own name stays rather than call sites importing `BackHandler` directly, because
 * `ui-backhandler` is an `implementation` dependency here and so reaches nobody else's compile
 * classpath — and there is no androidx artifact of that name to reach for instead
 * (`androidx.compose.ui:ui-backhandler` is a 404 on Google Maven, checked 2026-08-19).
 *
 * This used to end with "the wrapper has no caller outside this module, so it is worth deleting
 * rather than keeping". Step D1 answered that: the five screens that were on
 * `androidx.activity.compose.BackHandler` — the two composers, 收藏, the board strip, the web view —
 * are in `commonMain` now and all of them call this.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlazaBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
) = BackHandler(enabled = enabled, onBack = onBack)
