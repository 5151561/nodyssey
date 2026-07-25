package io.github.nsreader.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nsreader.R
import io.github.nsreader.ui.common.SignedInState
import io.github.nsreader.ui.common.SignedOutState
import io.github.nsreader.ui.theme.NodeSeekTheme

@Composable
fun ProfileRoute(
    viewModel: ProfileViewModel,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileScreen(
        state = state,
        onSignIn = onSignIn,
        onSignOut = viewModel::signOut,
        modifier = modifier,
    )
}

/**
 * "我的".
 *
 * The signed-in half is still waiting on the profile endpoints and a settled design, but it can no
 * longer claim the user is signed out — that was the visible half of the login bug. What it shows
 * instead is the truth it does know: the session exists, and here is how to end it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
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
            if (state.isSignedIn) {
                SignedInState(onSignOut = onSignOut)
            } else {
                SignedOutState(onSignIn = onSignIn)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 700, name = "Profile · signed in")
@Composable
private fun ProfileSignedInPreview() {
    NodeSeekTheme {
        ProfileScreen(state = ProfileUiState(isSignedIn = true), onSignIn = {}, onSignOut = {})
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 700, name = "Profile · signed out")
@Composable
private fun ProfileSignedOutPreview() {
    NodeSeekTheme {
        ProfileScreen(state = ProfileUiState(), onSignIn = {}, onSignOut = {})
    }
}
