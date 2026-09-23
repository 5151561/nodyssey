package io.github.nodyssey.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.component.LayerDivider
import io.github.plaza.designsys.component.groupShape
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.cardShadow

/**
 * The quiet heading above a group of cards — 今天, 更早, 全部私信.
 *
 * Not `SectionLabel`: that one is primary-coloured and labels a block of settings, while these only
 * say where one run of the same list ends and the next begins, and 5a draws them in the grey of the
 * time stamps under the rows.
 */
@Composable
internal fun ListGroupLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 8.dp, top = 14.dp, bottom = 6.dp),
    )
}
