package io.github.nsreader.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.nsreader.R
import io.github.nsreader.ui.common.ComingSoonState
import io.github.nsreader.ui.common.SignedOutState

/**
 * The tabs whose screens are designed but not yet approved.
 *
 * They exist so the navigation bar is real rather than decorative — a tab that does nothing when
 * tapped is worse than one that says plainly what it is waiting for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComingSoonScreen(
    title: String,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            ComingSoonState(label = title)
        }
    }
}

/**
 * "我的", signed out.
 *
 * The signed-in half needs the profile endpoints and a settled design; the signed-out half is
 * already specified, is the state most users will see first, and is where signing in lives now that
 * the app bar no longer carries an account button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.tab_profile),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            SignedOutState(onSignIn = onSignIn)
        }
    }
}
