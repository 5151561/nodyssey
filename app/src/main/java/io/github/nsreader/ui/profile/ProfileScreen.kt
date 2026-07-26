package io.github.nsreader.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nsreader.R
import io.github.nsreader.core.net.NodeSeekError
import io.github.nsreader.ui.common.LoadingState
import io.github.nsreader.ui.common.NodeSeekErrorState
import io.github.nsreader.ui.common.NodeSeekIcons
import io.github.nsreader.ui.common.SignedOutState
import io.github.nsreader.ui.common.UserAvatar
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.Sizes
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.StatusShapes
import io.github.nsreader.ui.theme.readableWidth

@Composable
fun ProfileRoute(
    viewModel: ProfileViewModel,
    onSignIn: () -> Unit,
    onSettings: () -> Unit,
    onOpenWebsite: () -> Unit,
    onEditProfile: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileScreen(
        state = state,
        onSignIn = onSignIn,
        onSignOut = viewModel::signOut,
        onRetry = viewModel::refresh,
        onSettings = onSettings,
        onOpenWebsite = onOpenWebsite,
        onEditProfile = { state.uid?.let(onEditProfile) },
        modifier = modifier,
    )
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
    onOpenWebsite: () -> Unit,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { padding ->
        if (!state.isSignedIn) {
            SignedOutState(onSignIn = onSignIn, modifier = Modifier.padding(padding))
            return@Scaffold
        }

        if (state.isLoading && !state.hasProfile) {
            LoadingState(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        if (state.error != null && !state.hasProfile) {
            NodeSeekErrorState(
                error = state.error,
                onRetry = onRetry,
                onOpenBrowser =
                if (state.error == NodeSeekError.LoginRequired) onSignIn else onOpenWebsite,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier =
            Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth(),
            contentPadding =
            androidx.compose.foundation.layout.PaddingValues(
                horizontal = Spacing.lg,
                vertical = Spacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item(key = "profile-header") {
                ProfileHeader(state, onEditProfile)
            }
            item(key = "resources") {
                ResourceCards(state)
            }
            item(key = "attendance") {
                Button(
                    onClick = onOpenWebsite,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Text(
                        stringResource(R.string.profile_attendance),
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
            }
            item(key = "content-menu") {
                ProfileMenuGroup(
                    items =
                    listOf(
                        ProfileMenuItem(
                            R.string.profile_topics,
                            Icons.Default.Home,
                            onOpenWebsite,
                        ),
                        ProfileMenuItem(
                            R.string.profile_comments,
                            Icons.Default.Person,
                            onOpenWebsite,
                        ),
                        ProfileMenuItem(
                            R.string.profile_bookmarks,
                            Icons.Default.Check,
                            onOpenWebsite,
                        ),
                    ),
                )
            }
            item(key = "settings-menu") {
                ProfileMenuGroup(
                    items =
                    listOf(
                        ProfileMenuItem(R.string.settings_title, Icons.Default.Settings, onSettings),
                        ProfileMenuItem(
                            R.string.profile_open_web,
                            Icons.AutoMirrored.Filled.ExitToApp,
                            onOpenWebsite,
                        ),
                    ),
                )
            }
            item(key = "sign-out") {
                TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_sign_out))
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    state: ProfileUiState,
    onEditProfile: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        UserAvatar(
            url = state.avatarUrl,
            name = state.displayName,
            size = Sizes.avatarProfile,
            shape = StatusShapes.Welcome,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(start = Spacing.sm),
                ) {
                    Text(
                        state.level ?: stringResource(R.string.profile_level_unknown),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = state.memberSince ?: stringResource(R.string.profile_session_active),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onEditProfile,
            modifier =
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Icon(
                imageVector = NodeSeekIcons.Edit,
                contentDescription = stringResource(R.string.profile_edit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ResourceCards(state: ProfileUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        ResourceCard(
            value = state.chickenCount?.toString() ?: "—",
            label = stringResource(R.string.profile_chicken),
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp, 5.dp, 5.dp, 18.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        ResourceCard(
            value = state.starCount?.toString() ?: "—",
            label = stringResource(R.string.profile_stars),
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(5.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        ResourceCard(
            value = state.streakDays?.toString() ?: "—",
            label = stringResource(R.string.profile_streak),
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(5.dp, 18.dp, 18.dp, 5.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun ResourceCard(
    value: String,
    label: String,
    shape: RoundedCornerShape,
    color: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = shape, color = color, contentColor = contentColor) {
        Column(Modifier.padding(horizontal = Spacing.lg, vertical = 14.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private data class ProfileMenuItem(
    val title: Int,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun ProfileMenuGroup(items: List<ProfileMenuItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { index, item ->
            val first = index == 0
            val last = index == items.lastIndex
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape =
                RoundedCornerShape(
                    topStart = if (first) 18.dp else 5.dp,
                    topEnd = if (first) 18.dp else 5.dp,
                    bottomEnd = if (last) 18.dp else 5.dp,
                    bottomStart = if (last) 18.dp else 5.dp,
                ),
                modifier = Modifier.clickable(onClick = item.onClick),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(item.title),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.weight(1f),
                    )
                    Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun ProfileSignedInPreview() {
    NodeSeekTheme {
        ProfileScreen(
            state =
            ProfileUiState(
                isSignedIn = true,
                displayName = "nsreader_dev",
                level = "Lv 3",
                memberSince = "2023年5月 注册 · UID 88423",
                chickenCount = 1_284,
                starCount = 356,
                streakDays = 27,
            ),
            onSignIn = {},
            onSignOut = {},
            onRetry = {},
            onSettings = {},
            onOpenWebsite = {},
            onEditProfile = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun ProfileSignedOutPreview() {
    NodeSeekTheme {
        ProfileScreen(
            state = ProfileUiState(),
            onSignIn = {},
            onSignOut = {},
            onRetry = {},
            onSettings = {},
            onOpenWebsite = {},
            onEditProfile = {},
        )
    }
}
