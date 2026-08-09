package io.github.nodyssey.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.nodyssey.model.TermsBlock
import io.github.nodyssey.model.TermsDocument
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.readableWidth

@Composable
fun PrivacyRoute(
    viewModel: PrivacyViewModel,
    onBack: () -> Unit,
    onOpenOriginal: () -> Unit,
    onOpenWebFallback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PrivacyScreen(
        state = state,
        onBack = onBack,
        onOpenOriginal = onOpenOriginal,
        onRetry = viewModel::retry,
        onOpenWebFallback = onOpenWebFallback,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    state: PrivacyUiState,
    onBack: () -> Unit,
    onOpenOriginal: () -> Unit,
    onRetry: () -> Unit,
    onOpenWebFallback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.privacy_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenOriginal) {
                            Icon(
                                PlazaIcons.OpenInNew,
                                contentDescription = stringResource(R.string.privacy_open_original),
                            )
                        }
                    },
                )
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { padding ->
        when (state) {
            PrivacyUiState.Loading -> Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            PrivacyUiState.Error -> PrivacyError(
                onRetry = onRetry,
                onOpenWebFallback = onOpenWebFallback,
                modifier = Modifier.padding(padding),
            )

            is PrivacyUiState.Content -> TermsContent(
                document = state.document,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun TermsContent(document: TermsDocument, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val showScrollHint by remember { derivedStateOf { listState.canScrollForward } }
    val background = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().readableWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Spacing.xl,
                end = Spacing.xl,
                top = Spacing.lg,
                bottom = 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.semantics { heading() },
                )
            }
            item { TermsMetadata(document.effectiveDate) }
            items(document.blocks) { block -> TermsBlockContent(block) }
        }
        if (showScrollHint) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(76.dp)
                    .drawWithContent {
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(background.copy(alpha = 0f), background),
                                start = Offset(0f, 0f),
                                end = Offset(0f, size.height),
                            ),
                        )
                        drawContent()
                    },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.padding(bottom = Spacing.sm),
                ) {
                    Text(
                        stringResource(R.string.privacy_keep_scrolling),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    )
                }
            }
        }
    }
}

@Composable
private fun TermsMetadata(effectiveDate: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        effectiveDate?.let {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    stringResource(R.string.privacy_effective_date, it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
        }
        Text(
            stringResource(R.string.privacy_source),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TermsBlockContent(block: TermsBlock) {
    when (block) {
        is TermsBlock.Heading -> Text(
            text = block.text,
            style = if (block.level == 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = Spacing.sm).semantics { heading() },
        )

        is TermsBlock.Paragraph -> Text(
            text = block.text,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
        )

        is TermsBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            block.items.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.Top) {
                    if (block.ordered) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(28.dp),
                        )
                    } else {
                        Spacer(
                            Modifier.padding(top = 8.dp).size(6.dp).background(
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                CircleShape,
                            ),
                        )
                        Spacer(Modifier.width(Spacing.md))
                    }
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyError(
    onRetry: () -> Unit,
    onOpenWebFallback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.privacy_load_failed), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.privacy_load_failed_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = Spacing.md),
        )
        Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        TextButton(onClick = onOpenWebFallback) { Text(stringResource(R.string.privacy_open_web_fallback)) }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "f2 隐私协议")
@Composable
private fun PrivacyPreview() {
    PlazaTheme {
        PrivacyScreen(
            state = PrivacyUiState.Content(
                TermsDocument(
                    title = "本网站服务协议",
                    effectiveDate = "2022-11-24",
                    blocks = listOf(
                        TermsBlock.Heading(2, "定义和说明"),
                        TermsBlock.Paragraph("《本网站服务协议》是用户与本网站之间的协议。"),
                        TermsBlock.ListBlock(false, listOf("本平台：https://www.nodeseek.com；", "本网站提供给您的其他服务。")),
                    ),
                ),
            ),
            onBack = {},
            onOpenOriginal = {},
            onRetry = {},
            onOpenWebFallback = {},
        )
    }
}
