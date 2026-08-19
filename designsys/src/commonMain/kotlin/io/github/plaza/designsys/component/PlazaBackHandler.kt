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
 * (`androidx.compose.ui:ui-backhandler` is a 404 on Google Maven, checked 2026-08-19). That was
 * originally written as "a consumer still on androidx cannot name it, and one of those consumers is
 * `:app`", which stopped being true in step B4 when `:app` moved to the multiplatform coordinates
 * too. The wrapper has no caller outside this module — `MarkdownEditorBar` is the only one — so if
 * that stays true it is worth deleting rather than keeping.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlazaBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
) = BackHandler(enabled = enabled, onBack = onBack)
