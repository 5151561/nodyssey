package io.github.nodyssey.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.nodyssey.R
import io.github.nodyssey.ui.theme.Spacing

/*
 * The page control for the screens that read a paged site list as one continuous scroll.
 *
 * It exists because "append the next page while scrolling" alone is not enough on a list a hundred
 * pages deep: continuing to read is cheap, but *arriving* somewhere is not, and flinging is a poor
 * way to travel. The pairing — auto-append for reading, an explicit jump for travelling — was built
 * for the comment thread first; 管理记录 has the same shape (a numbered list far too long to scroll)
 * and now shares the control rather than growing a second dialect of it.
 *
 * Deliberately not a whole toolbar: the thread's bar carries a 回复 FAB and the log's does not, so
 * each screen wraps [PageJumpToolbarContent] in its own HorizontalFloatingToolbar. What is shared is
 * the part that must not drift — the wording, the shortcuts, and what the numbers mean.
 */

/**
 * The `‹ 第 3 / 100 页 ›` row that sits inside a floating toolbar.
 *
 * [page] is the page the reader is *looking at*, which on an appending list is not the last page
 * fetched — the caller derives it from whatever is at the top of the viewport.
 */
@Composable
fun PageJumpToolbarContent(
    page: Int,
    totalPages: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageClick: () -> Unit,
) {
    Row(
        modifier = Modifier.height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        IconButton(onClick = onPrevious, enabled = page > 1) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.page_jump_previous),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onPageClick, contentPadding = PaddingValues(horizontal = Spacing.sm)) {
            Text(stringResource(R.string.page_jump_page_of, page, totalPages))
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
        IconButton(onClick = onNext, enabled = page < totalPages) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.page_jump_next),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The jump sheet: a page number, and the three destinations worth one tap.
 *
 * [progress] is the caller's own sentence because only the caller knows what it has loaded — the
 * thread counts 楼, the moderation log counts 条 — and a shared component inventing a noun for both
 * would be wrong in one of them.
 *
 * [loadedPage] is the far end of what is already in the list, which is where "上次阅读" goes. It is
 * distinct from [page]: a reader who has appended their way to page 9 and then scrolled back to
 * page 2 wants both destinations offered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageJumpSheet(
    page: Int,
    loadedPage: Int,
    totalPages: Int,
    progress: String,
    onDismiss: () -> Unit,
    onGo: (Int) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf(page.toString()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.page_jump_title), style = MaterialTheme.typography.titleLarge)
            Text(
                progress,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter { character -> character.isDigit() } },
                label = { Text(stringResource(R.string.page_jump_input, totalPages)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = { onGo(1) }) { Text(stringResource(R.string.page_jump_first)) }
                TextButton(onClick = { onGo(loadedPage) }) { Text(stringResource(R.string.page_jump_latest_read)) }
                TextButton(onClick = { onGo(totalPages) }) { Text(stringResource(R.string.page_jump_last)) }
            }
            Button(onClick = { input.toIntOrNull()?.let(onGo) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.page_jump_go, input.ifBlank { page.toString() }))
            }
        }
    }
}
