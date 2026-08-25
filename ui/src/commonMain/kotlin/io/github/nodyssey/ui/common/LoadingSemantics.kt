package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.loading_indicator_label
import org.jetbrains.compose.resources.stringResource

/**
 * Names a progress indicator for the screen reader.
 *
 * Material's indicators carry range semantics but no name, so TalkBack lands on one and says
 * nothing — and inside a button whose label the spinner replaced, the whole button goes silent.
 * One shared name rather than a per-screen phrase: what the reader needs to know is that this is
 * a wait, not a taxonomy of waits; the screens that do have something more specific to say
 * (进度百分比, 正在读取论坛人数…) already say it in text of their own.
 */
@Composable
internal fun Modifier.describedAsLoading(): Modifier {
    val label = stringResource(Res.string.loading_indicator_label)
    return semantics { contentDescription = label }
}
