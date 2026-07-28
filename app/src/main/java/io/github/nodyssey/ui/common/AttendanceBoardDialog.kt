package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nodyssey.R
import io.github.nodyssey.core.net.NodeSeekError
import io.github.nodyssey.data.AttendanceBoardEntry
import io.github.nodyssey.ui.theme.Spacing
import io.github.nodyssey.ui.theme.TABULAR_FIGURES

/** 今日签到榜，共享给“我的”和“账户与成长”，不附带任何页面导航行为。 */
@Composable
fun AttendanceBoardDialog(
    isLoading: Boolean,
    entries: List<AttendanceBoardEntry>,
    error: NodeSeekError?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(stringResource(R.string.assets_board)) },
        text = {
            when {
                isLoading ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(Modifier.size(24.dp)) }

                error != null ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            stringResource(R.string.assets_board_failed),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                    }

                else ->
                    LazyColumn(Modifier.height(BOARD_LIST_HEIGHT)) {
                        // No `key`: the board is a one-shot snapshot that never reorders or grows,
                        // and names are not guaranteed unique — a duplicate key would crash for the
                        // sake of an identity Lazy already gets from the index.
                        itemsIndexed(entries) { index, entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            ) {
                                Text(
                                    text = (index + 1).toString(),
                                    style =
                                    MaterialTheme.typography.labelMedium.copy(
                                        fontFeatureSettings = TABULAR_FIGURES,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(22.dp),
                                )
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                entry.gain?.let {
                                    Text(
                                        text = stringResource(R.string.assets_board_gain, it),
                                        style =
                                        MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFeatureSettings = TABULAR_FIGURES,
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

private val BOARD_LIST_HEIGHT = 360.dp
