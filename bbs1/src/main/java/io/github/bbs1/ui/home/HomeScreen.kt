package io.github.bbs1.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.bbs1.R
import io.github.bbs1.model.ForumInstance
import io.github.plaza.designsys.component.StatusAction
import io.github.plaza.designsys.component.StatusView

/**
 * The stand-in for the real feed: it shows which site is current and says why there is no content
 * yet. It exists so the app has somewhere to land after picking a site, and so the site switcher has
 * something to come back from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    current: ForumInstance?,
    onOpenInstances: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.name ?: stringResource(R.string.bbs1_app_name)) },
                actions = {
                    IconButton(onClick = onOpenInstances) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.bbs1_manage_instances),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (current == null) {
                StatusView(
                    icon = Icons.Default.Info,
                    shape = MaterialTheme.shapes.extraLarge,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    title = stringResource(R.string.bbs1_home_no_instance_title),
                    description = stringResource(R.string.bbs1_home_no_instance_body),
                    primaryAction = StatusAction(stringResource(R.string.bbs1_manage_instances), onOpenInstances),
                )
            } else {
                StatusView(
                    icon = Icons.Default.Info,
                    shape = MaterialTheme.shapes.extraLarge,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    title = stringResource(R.string.bbs1_home_placeholder_title),
                    description = stringResource(R.string.bbs1_home_placeholder_body),
                    footnote = current.baseUrl,
                )
            }
        }
    }
}
