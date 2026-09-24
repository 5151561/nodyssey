package io.github.nodyssey.ui.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.plaza.designsys.component.GroupedRow
import io.github.plaza.designsys.component.PlazaIcons

/**
 * A row of the two 关于 pages' grouped cards — 关于软件 and 关于社区 — laid out by [GroupedRow].
 *
 * [external] swaps the chevron for the leave-the-app arrow, because the difference between "another
 * page of this app" and "your browser" is worth knowing before the tap. [trailing] replaces both for
 * a row that acts in place (复制 RSS). A subtitle that is an address — a repository, an email — is set
 * in monospace, since it is read character by character.
 */
@Composable
internal fun AboutRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    subtitle: String? = null,
    external: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    first: Boolean = false,
    last: Boolean = false,
) {
    GroupedRow(
        title = title,
        subtitle = subtitle,
        subtitleMonospace = subtitle != null && ("github.com" in subtitle || "@" in subtitle),
        first = first,
        last = last,
        onClick = onClick,
        showChevron = !external && trailing == null,
        icon = icon,
        trailing =
        trailing ?: if (external) {
            {
                Icon(
                    PlazaIcons.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            null
        },
    )
}
