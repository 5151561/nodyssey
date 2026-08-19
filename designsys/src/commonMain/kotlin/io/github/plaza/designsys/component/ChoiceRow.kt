package io.github.plaza.designsys.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow

/**
 * One option in a single-choice list: radio, label, whole row tappable.
 *
 * The row rather than the button carries the click — `RadioButton(onClick = null)` is deliberate, and
 * is what makes the label part of the target and lets TalkBack announce the pair as one selectable.
 * No height of its own: the radio's own 48dp touch target sets it, which is the minimum this needs
 * to clear anyway.
 */
@Composable
fun ChoiceRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
        modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = onSelect,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
