package io.github.nodyssey.ui.common

import androidx.compose.material3.Badge
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.nodyssey.R

/**
 * The 有新版本 dot, on whatever row leads to 关于 Nodyssey.
 *
 * Material's own [Badge] with no count — a number would have nothing to count. It carries a
 * description of its own because a bare dot is the one thing on these rows that a screen reader
 * would otherwise pass over in silence.
 */
@Composable
fun UpdateDot(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.update_dot_description)
    Badge(modifier = modifier.semantics { contentDescription = description })
}
