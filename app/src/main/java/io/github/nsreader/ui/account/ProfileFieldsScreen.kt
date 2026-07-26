package io.github.nsreader.ui.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nsreader.R
import io.github.nsreader.ui.common.MARKDOWN_LINK_CARET
import io.github.nsreader.ui.common.MARKDOWN_LINK_SUFFIX
import io.github.nsreader.ui.common.MarkdownInsertion
import io.github.nsreader.ui.common.NodeSeekIcons
import io.github.nsreader.ui.common.UserAvatar
import io.github.nsreader.ui.common.applyMarkdown
import io.github.nsreader.ui.theme.NodeSeekTheme
import io.github.nsreader.ui.theme.Spacing
import io.github.nsreader.ui.theme.readableWidth

@Composable
fun ProfileFieldsRoute(
    viewModel: ProfileFieldsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.let { accountMessageText(it) }

    LaunchedEffect(state.message, messageText) {
        if (messageText == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(messageText)
        viewModel.consumeMessage()
    }

    ProfileFieldsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onBioChange = viewModel::updateBio,
        onSignatureChange = viewModel::updateSignature,
        onReadmeChange = viewModel::updateReadme,
        onAvatarPicked = viewModel::setPendingAvatar,
        onAvatarFailed = viewModel::reportAvatarFailure,
        onAvatarRemove = viewModel::removeAvatar,
        onSave = viewModel::save,
        modifier = modifier,
    )
}

/**
 * 个人信息 (d6 1/4).
 *
 * The signature editor's toolbar is the detail worth protecting: five actions, no image and no quote,
 * because NodeSeek's own helper text says signatures do not support either. Offering the buttons and
 * letting the server drop the markup is the failure mode this screen exists to avoid — the reduced
 * toolbar *is* the documentation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileFieldsScreen(
    state: ProfileFieldsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onBioChange: (String) -> Unit,
    onSignatureChange: (String) -> Unit,
    onReadmeChange: (String) -> Unit,
    onAvatarPicked: (PendingAvatar) -> Unit,
    onAvatarFailed: () -> Unit,
    onAvatarRemove: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingAvatarRemoval by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = state.canSave) {
                        Text(stringResource(R.string.account_action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
            Modifier
                .padding(padding)
                .fillMaxSize()
                .readableWidth()
                .verticalScroll(rememberScrollState())
                // Edge-to-edge makes API 30+ ignore the manifest's adjustResize, and Scaffold's
                // default insets exclude the IME, so without this the keyboard covers Readme with
                // no way to scroll it back into view. Same call the post composer makes.
                .imePadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            if (state.endpointPending) EndpointPendingBanner()

            AvatarEditor(
                state = state,
                onPicked = onAvatarPicked,
                onFailed = onAvatarFailed,
                onRemoveRequested = { confirmingAvatarRemoval = true },
            )

            OutlinedTextField(
                value = state.bio,
                onValueChange = onBioChange,
                label = { Text(stringResource(R.string.account_bio)) },
                placeholder = { Text(stringResource(R.string.account_bio_hint)) },
                singleLine = true,
                shape = AccountFieldShape,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                MarkdownField(
                    value = state.signature,
                    onValueChange = onSignatureChange,
                    label = stringResource(R.string.account_signature),
                    minLines = SIGNATURE_MIN_LINES,
                )
                AccountFieldHelper(stringResource(R.string.account_signature_helper))
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = state.readme,
                    onValueChange = onReadmeChange,
                    label = { Text(stringResource(R.string.account_readme)) },
                    minLines = README_MIN_LINES,
                    shape = AccountFieldShape,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
                AccountFieldHelper(stringResource(R.string.account_readme_helper))
            }
        }
    }

    if (confirmingAvatarRemoval) {
        HighRiskDialog(
            icon = Icons.Default.Delete,
            title = stringResource(R.string.account_confirm_avatar_remove_title),
            body = stringResource(R.string.account_confirm_avatar_remove_body),
            confirmLabel = stringResource(R.string.account_confirm_avatar_remove_action),
            destructive = true,
            onConfirm = {
                confirmingAvatarRemoval = false
                onAvatarRemove()
            },
            onDismiss = { confirmingAvatarRemoval = false },
        )
    }
}

/**
 * The avatar, its camera badge, and the menu the badge opens.
 *
 * A menu rather than a bottom sheet: three items, one of which is destructive, anchored to the thing
 * they act on. A sheet would cover the avatar the user is trying to change.
 */
@Composable
private fun AvatarEditor(
    state: ProfileFieldsUiState,
    onPicked: (PendingAvatar) -> Unit,
    onFailed: () -> Unit,
    onRemoveRequested: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val picker = rememberAvatarPicker(onPicked = onPicked, onFailed = onFailed)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            val pending = state.pendingAvatar
            if (pending == null) {
                UserAvatar(
                    url = state.avatarUrl,
                    name = state.displayName,
                    size = AVATAR_SIZE,
                )
            } else {
                Image(
                    bitmap = pending.preview,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(AVATAR_SIZE).clip(CircleShape),
                )
            }
            Box(Modifier.offset(x = AVATAR_BADGE_OVERHANG, y = AVATAR_BADGE_OVERHANG)) {
                Surface(
                    onClick = { menuOpen = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    // The ring is the page background, so the badge reads as sitting on top of the
                    // avatar rather than being a hole punched in it.
                    modifier =
                    Modifier
                        .size(AVATAR_BADGE_SIZE)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .padding(3.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            NodeSeekIcons.PhotoCamera,
                            contentDescription = stringResource(R.string.account_avatar_change),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.account_avatar_take_photo)) },
                        leadingIcon = { Icon(NodeSeekIcons.PhotoCamera, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            picker.takePhoto()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.account_avatar_pick)) },
                        leadingIcon = { Icon(NodeSeekIcons.Image, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            picker.pickImage()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.account_avatar_remove),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onRemoveRequested()
                        },
                    )
                }
            }
        }

        if (state.pendingAvatar != null) {
            Text(
                stringResource(R.string.account_avatar_pending),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * A Markdown field with the five formatting actions a signature is allowed.
 *
 * The glyph labels rather than icons match the post composer's toolbar, which made the same call for
 * the same reason: `material-icons-core` ships none of `format_bold`, `format_italic` or
 * `strikethrough_s`, and hand-drawing three vectors to say what "B" and "I" already say is a poor
 * trade. Each button still carries a spoken description.
 */
@Composable
private fun MarkdownField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    minLines: Int,
) {
    // Held here rather than in the ViewModel: the selection is a property of this text field, and the
    // formatting buttons are the only thing that reads it.
    var fieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    if (fieldValue.text != value) {
        fieldValue = fieldValue.copy(text = value)
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = {
                fieldValue = it
                onValueChange(it.text)
            },
            label = { Text(label) },
            minLines = minLines,
            shape = AccountFieldShape,
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(Modifier.padding(2.dp)) {
                SignatureFormat.entries.forEach { format ->
                    val description = stringResource(format.descriptionRes)
                    IconButton(
                        onClick = {
                            fieldValue = fieldValue.applyMarkdown(format.insertion)
                            onValueChange(fieldValue.text)
                        },
                        modifier = Modifier.semantics { contentDescription = description },
                    ) {
                        Text(format.glyph, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/**
 * Bold, italic, strikethrough, link, inline code — and deliberately nothing else.
 *
 * The absent actions are the point: NodeSeek's own helper text says a signature supports neither
 * images nor quotes. The insertion mechanics come from [applyMarkdown], shared with the post
 * composer, so only the *set* differs between the two editors.
 */
private enum class SignatureFormat(
    val glyph: String,
    val descriptionRes: Int,
    val insertion: MarkdownInsertion,
) {
    Bold("B", R.string.account_format_bold, MarkdownInsertion("**", "**", "加粗文字")),
    Italic("I", R.string.account_format_italic, MarkdownInsertion("*", "*", "斜体文字")),
    Strikethrough("S", R.string.account_format_strikethrough, MarkdownInsertion("~~", "~~", "删除线")),
    Link(
        "↗",
        R.string.account_format_link,
        MarkdownInsertion(
            prefix = "[",
            suffix = MARKDOWN_LINK_SUFFIX,
            placeholder = "链接文字",
            caretInSuffix = MARKDOWN_LINK_CARET,
        ),
    ),
    Code("</>", R.string.account_format_code, MarkdownInsertion("`", "`", "code")),
}

private val AVATAR_SIZE = 84.dp
private val AVATAR_BADGE_SIZE = 30.dp

/** The badge straddles the avatar's edge rather than sitting inside it, as the design shows. */
private val AVATAR_BADGE_OVERHANG = 4.dp
private const val SIGNATURE_MIN_LINES = 3
private const val README_MIN_LINES = 5

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun ProfileFieldsPreview() {
    NodeSeekTheme {
        ProfileFieldsScreen(
            state =
            ProfileFieldsUiState(
                isLoading = false,
                endpointPending = true,
                displayName = "花间一壶酒",
                bio = "常驻杭州",
                signature = "**出杭州腾讯云轻量** · 长期收闲置小鸡 · 交易走 [星辰担保](https://ns.run/dan)",
                readme = "### 关于我\n爱折腾的 MJJ 一枚，主力小鸡在 HK。\n- 交易一律走星辰担保，勿私",
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onBioChange = {},
            onSignatureChange = {},
            onReadmeChange = {},
            onAvatarPicked = {},
            onAvatarFailed = {},
            onAvatarRemove = {},
            onSave = {},
        )
    }
}
