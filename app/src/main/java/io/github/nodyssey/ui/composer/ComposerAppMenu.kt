package io.github.nodyssey.ui.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.composer_app_menu
import io.github.nodyssey.ui.resources.composer_insert_stardust
import io.github.nodyssey.ui.resources.composer_insert_vote
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.editor.EditorToolbarDefaults
import org.jetbrains.compose.resources.stringResource

/**
 * The editors' APP menu: the things NodeSeek lets a post embed rather than merely say.
 *
 * The site keeps both behind one entry in its own editor, and so does this — two keys on a strip that
 * already scrolls would be two more things to swipe past, and neither is reached mid-sentence.
 *
 * [keySize] is the strip's, passed rather than defaulted so this button measures the same as the keys
 * beside it. The reply sheet runs a tighter strip than the post editor, and a menu button that kept
 * the wider metric would be the one thing on that row not on the grid.
 */
@Composable
fun ComposerAppMenu(
    onInsertVote: () -> Unit,
    onInsertStardust: () -> Unit,
    modifier: Modifier = Modifier,
    keySize: Dp = EditorToolbarDefaults.KeySize,
) {
    var open by remember { mutableStateOf(false) }

    Box(modifier) {
        IconButton(onClick = { open = true }, modifier = Modifier.size(keySize)) {
            Icon(
                PlazaIcons.Apps,
                contentDescription = stringResource(Res.string.composer_app_menu),
                modifier = Modifier.size(keySize / 2),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.composer_insert_vote)) },
                leadingIcon = { Icon(PlazaIcons.Poll, contentDescription = null) },
                onClick = {
                    open = false
                    onInsertVote()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.composer_insert_stardust)) },
                leadingIcon = { Icon(PlazaIcons.QrCode, contentDescription = null) },
                onClick = {
                    open = false
                    onInsertStardust()
                },
            )
        }
    }
}
