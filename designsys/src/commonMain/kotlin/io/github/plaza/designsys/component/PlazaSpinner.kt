package io.github.plaza.designsys.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.theme.LocalEinkMode

/**
 * "Something is on its way", drawn once by every screen that has to say it.
 *
 * The reason it exists is narrow and worth stating: Material's two indeterminate indicators are the
 * only animations in this app that no setting can stop. `CircularProgressIndicator` runs a
 * `rememberInfiniteTransition` against a hardcoded `InfiniteRepeatableSpec`, and the Expressive
 * `LoadingIndicator` runs its morph out of a `LaunchedEffect` and an `Animatable`; neither reads
 * `MaterialTheme.motionScheme`, so `PlazaTheme`'s snapped scheme — which silences every other
 * animation in the app in one move — goes straight past them. On electronic paper that is a spinner
 * repainting a patch of the panel for as long as the request takes.
 *
 * So 墨水屏模式 does not slow them down, it stops drawing them: a still ring, which is the least a
 * 1-bit screen can spend on "wait" and still say it. Everything else is unchanged, which is why this
 * wraps rather than replaces — a screen calling it gets exactly the Material indicator it had.
 *
 * Only the indeterminate one. A `CircularProgressIndicator` handed a `progress` lambda draws an arc
 * at the fraction it is given and animates nothing on its own, so the two upload rings in this app
 * keep calling Material directly — wrapping them would have cost them the very thing they show.
 * `SpinnerBoundaryTest` draws that line as well as guarding the thirty-two that did move.
 */
@Composable
fun PlazaSpinner(
    modifier: Modifier = Modifier,
    size: Dp = PlazaSpinnerDefaults.Size,
    strokeWidth: Dp = PlazaSpinnerDefaults.StrokeWidth,
    color: Color = ProgressIndicatorDefaults.circularColor,
    /**
     * Passed through and ignored on paper — a still ring has no ends to cap.
     *
     * It is here for the one caller that draws this beside a determinate ring of its own and needs
     * the two to match; without it, that pair would go back to disagreeing whenever it is not the
     * one being looked at.
     */
    strokeCap: StrokeCap = ProgressIndicatorDefaults.CircularIndeterminateStrokeCap,
) {
    if (LocalEinkMode.current) {
        // `progressSemantics` by hand: the Material indicator carries it, and a plain Box would
        // leave TalkBack with a decoration where the app means "still working". Indeterminate, which
        // is the truth — nothing here knows a fraction.
        Box(modifier.progressSemantics().size(size).border(strokeWidth, color, CircleShape))
    } else {
        CircularProgressIndicator(
            modifier = modifier.size(size),
            color = color,
            strokeWidth = strokeWidth,
            strokeCap = strokeCap,
        )
    }
}

/**
 * The full-screen wait, where a screen has nothing else to show yet.
 *
 * Separate from [PlazaSpinner] because the two draw different things when there is a panel behind
 * neither of them: this one is the Expressive shape-morphing indicator, which is Material's answer
 * for a whole screen and reads as far too much for a footer.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlazaLoadingIndicator(modifier: Modifier = Modifier) {
    if (LocalEinkMode.current) {
        PlazaSpinner(modifier)
    } else {
        LoadingIndicator(modifier)
    }
}

object PlazaSpinnerDefaults {
    /**
     * 40dp — `CircularProgressIndicatorTokens.Size`, which Material keeps private.
     *
     * Copied rather than referenced so that a call site passing nothing gets exactly what
     * `CircularProgressIndicator()` gave it before this wrapper existed. If a Material bump moves the
     * token, the goldens in `DesignSystemSnapshotTest` are what will say so.
     */
    val Size = 40.dp

    /** 4dp — `CircularProgressIndicatorTokens.ActiveThickness`, kept private the same way. */
    val StrokeWidth = 4.dp
}
