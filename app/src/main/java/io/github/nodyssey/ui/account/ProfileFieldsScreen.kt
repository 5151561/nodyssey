package io.github.nodyssey.ui.account

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
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.R
import io.github.nodyssey.ui.common.AvatarShape
import io.github.nodyssey.ui.common.NodysseyIcons
import io.github.nodyssey.ui.common.UserAvatar
import io.github.nodyssey.ui.composer.EditorActions
import io.github.nodyssey.ui.composer.EditorToolbar
import io.github.nodyssey.ui.composer.applyMarkdown
import io.github.nodyssey.ui.theme.NodysseyTheme
import io.github.nodyssey.ui.theme.Spacing
import io.github.nodyssey.ui.theme.readableWidth

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
        onSave = viewModel::save,
        modifier = modifier,
    )
}

/**
 * 个人信息 (d6 1/4).
 *
 * 签名 and Readme are both Markdown, so both get the composers' formatting strip — one strip, pinned
 * against the keyboard, following whichever of the two is being edited. A strip per field would have
 * put two of them in a form that has room for neither, and the earlier arrangement — a single strip
 * parked under 签名 — read as belonging to the field below it as much as the one above, while wiring
 * to only one of them.
 *
 * Which keys it carries still depends on the field: [EditorActions.Signature] drops images and quotes
 * because NodeSeek's own helper text says signatures support neither, while [EditorActions.Readme]
 * keeps the block-level keys a document wants. Offering buttons the server will strip is the failure
 * mode the split exists to avoid — the per-field action list *is* the documentation.
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
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val signatureState = rememberSeededTextFieldState(state.signature, onSignatureChange)
    val readmeState = rememberSeededTextFieldState(state.readme, onReadmeChange)
    val signatureFocus = remember { FocusRequester() }
    val readmeFocus = remember { FocusRequester() }
    // Sticky rather than plain focus: a toolbar key takes focus off the field the moment it is
    // pressed, so a strip that hid on blur would vanish under the finger that was using it.
    var target by remember { mutableStateOf<MarkdownTarget?>(null) }

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
        // Edge-to-edge makes API 30+ ignore the manifest's adjustResize, and Scaffold's default
        // insets exclude the IME, so without this the keyboard covers Readme with no way to scroll
        // it back into view. It sits on the outer column so the strip rides the keyboard's top edge
        // instead of scrolling away with the form. Same shape as the post composer.
        Column(
            modifier =
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding(),
        ) {
            Column(
                modifier =
                Modifier
                    .weight(1f)
                    .readableWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                AvatarEditor(
                    state = state,
                    onPicked = onAvatarPicked,
                    onFailed = onAvatarFailed,
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
                        fieldState = signatureState,
                        label = stringResource(R.string.account_signature),
                        minLines = SIGNATURE_MIN_LINES,
                        focusRequester = signatureFocus,
                        onFocused = { target = MarkdownTarget.SIGNATURE },
                    )
                    AccountFieldHelper(stringResource(R.string.account_signature_helper))
                }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    MarkdownField(
                        fieldState = readmeState,
                        label = stringResource(R.string.account_readme),
                        minLines = README_MIN_LINES,
                        focusRequester = readmeFocus,
                        onFocused = { target = MarkdownTarget.README },
                    )
                    AccountFieldHelper(stringResource(R.string.account_readme_helper))
                }
            }

            target?.let { field ->
                val signature = field == MarkdownTarget.SIGNATURE
                EditorToolbar(
                    actions = if (signature) EditorActions.Signature else EditorActions.Readme,
                    onAction = { action ->
                        val edited = if (signature) signatureState else readmeState
                        edited.edit { applyMarkdown(action) }
                        // Pressing a key moved focus to the key. A caret the user cannot see is a
                        // caret they have lost track of, so it goes straight back.
                        (if (signature) signatureFocus else readmeFocus).requestFocus()
                    },
                    modifier = Modifier.readableWidth(),
                )
            }
        }
    }
}

/** Which of the two Markdown fields the strip is currently writing into. */
private enum class MarkdownTarget { SIGNATURE, README }

/**
 * The avatar, its camera badge, and the menu the badge opens.
 *
 * A menu rather than a bottom sheet: two items, anchored to the thing they act on. A sheet would
 * cover the avatar the user is trying to change. There is no 移除 item because the site has no such
 * operation — an account that has an avatar cannot go back to not having one.
 */
@Composable
private fun AvatarEditor(
    state: ProfileFieldsUiState,
    onPicked: (PendingAvatar) -> Unit,
    onFailed: () -> Unit,
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
                    modifier = Modifier.size(AVATAR_SIZE).clip(AvatarShape),
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
                            NodysseyIcons.PhotoCamera,
                            contentDescription = stringResource(R.string.account_avatar_change),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.account_avatar_take_photo)) },
                        leadingIcon = { Icon(NodysseyIcons.PhotoCamera, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            picker.takePhoto()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.account_avatar_pick)) },
                        leadingIcon = { Icon(NodysseyIcons.Image, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            picker.pickImage()
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
 * A form field whose text is Markdown. The strip that formats it belongs to the screen, not here:
 * both of these share one, so the field's job is to report when it becomes the strip's target.
 */
@Composable
private fun MarkdownField(
    fieldState: TextFieldState,
    label: String,
    minLines: Int,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
) {
    OutlinedTextField(
        state = fieldState,
        label = { Text(label) },
        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = minLines),
        shape = AccountFieldShape,
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier =
        Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() },
    )
}

/**
 * A [TextFieldState] seeded once from the saved value and read back through [onValueChange].
 *
 * Scoped to the screen, not to the ViewModel: unlike the post composer, nothing here edits the text
 * behind the user's back, so there is no caret to protect from a background write — and a
 * `TextFieldState` parked in a ViewModel observes the global snapshot for as long as it lives.
 *
 * The saved text arrives from the network after the fields are already on screen. Seeding is one-way
 * and one-time; from then on the field is the writer and the ViewModel the reader, which is what the
 * old `if (fieldValue.text != value)` assignment during composition got wrong.
 */
@Composable
private fun rememberSeededTextFieldState(
    value: String,
    onValueChange: (String) -> Unit,
): TextFieldState {
    val fieldState = rememberTextFieldState()
    LaunchedEffect(value) {
        if (value.isNotEmpty() && fieldState.text.isEmpty()) fieldState.setTextAndPlaceCursorAtEnd(value)
    }
    LaunchedEffect(fieldState) {
        snapshotFlow { fieldState.text.toString() }.collect(onValueChange)
    }
    return fieldState
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
    NodysseyTheme {
        ProfileFieldsScreen(
            state =
            ProfileFieldsUiState(
                isLoading = false,
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
            onSave = {},
        )
    }
}
