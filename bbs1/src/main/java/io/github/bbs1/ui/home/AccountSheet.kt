package io.github.bbs1.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.bbs1.R
import io.github.bbs1.model.InstanceSession
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.theme.Sizes
import io.github.plaza.designsys.theme.Spacing

/**
 * Who is signed in to this site, and the way out.
 *
 * Per site on purpose: an account belongs to one forum, and a sheet that offered "sign out" for the
 * app as a whole would be signing out of forums the user is not looking at.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    session: InstanceSession,
    siteName: String,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                UserAvatar(
                    url = session.avatarUrl.takeIf { it.isNotBlank() },
                    name = session.username,
                    size = Sizes.avatarProfile,
                )
                Column {
                    Text(session.username, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = siteName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.bbs1_action_sign_out))
            }
        }
    }
}
