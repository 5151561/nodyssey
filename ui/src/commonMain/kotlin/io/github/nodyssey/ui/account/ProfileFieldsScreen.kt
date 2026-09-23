package io.github.nodyssey.ui.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nodyssey.ui.composer.EditorActions
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.account_action_save
import io.github.nodyssey.ui.resources.account_avatar
import io.github.nodyssey.ui.resources.account_avatar_change
import io.github.nodyssey.ui.resources.account_avatar_change_short
import io.github.nodyssey.ui.resources.account_avatar_pending
import io.github.nodyssey.ui.resources.account_avatar_pick
import io.github.nodyssey.ui.resources.account_avatar_take_photo
import io.github.nodyssey.ui.resources.account_bio
import io.github.nodyssey.ui.resources.account_bio_hint
import io.github.nodyssey.ui.resources.account_markdown_tag
import io.github.nodyssey.ui.resources.account_profile_title
import io.github.nodyssey.ui.resources.account_readme
import io.github.nodyssey.ui.resources.account_readme_helper
import io.github.nodyssey.ui.resources.account_signature
import io.github.nodyssey.ui.resources.account_signature_helper
import io.github.nodyssey.ui.resources.action_back
import io.github.plaza.designsys.component.AvatarShape
import io.github.plaza.designsys.component.LayerCard
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.OneHandTopAppBar
import io.github.plaza.designsys.component.PlazaFieldDefaults
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.UserAvatar
import io.github.plaza.designsys.component.rememberOneHandAppBarState
import io.github.plaza.designsys.editor.EditorToolbar
import io.github.plaza.designsys.editor.applyMarkdown
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.PlazaTheme
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.paddingWithKeyboard
import io.github.plaza.designsys.theme.readableWidth
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProfileFieldsRoute(
    viewModel: ProfileFieldsViewModel,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    /** Clears a Cloudflare challenge, then comes back to this page. */
    onVerify: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    AccountMessageSnackbar(
        message = state.message,
        snackbarHostState = snackbarHostState,
        onShown = viewModel::consumeMessage,
        onSignIn = onSignIn,
        onVerify = onVerify,
    )

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
    val bioLabel = stringResource(Res.string.account_bio)
    val readmeFocus = remember { FocusRequester() }
    // Sticky rather than plain focus: a toolbar key takes focus off the field the moment it is
    // pressed, so a strip that hid on blur would vanish under the finger that was using it.
    var target by remember { mutableStateOf<MarkdownTarget?>(null) }

    val appBarState = rememberOneHandAppBarState()
    Scaffold(
        modifier = modifier.nestedScroll(appBarState.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            OneHandTopAppBar(
                title = stringResource(Res.string.account_profile_title),
                state = appBarState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
                actions = {
                    // 3b's filled pill: saving is this page's one commit, and it waits up here while
                    // the fields below are edited.
                    Button(
                        onClick = onSave,
                        enabled = state.canSave,
                        shape = CircleShape,
                        modifier = Modifier.padding(end = Spacing.sm),
                    ) {
                        Text(stringResource(Res.string.account_action_save))
                    }
                },
            )
        },
    ) { padding ->
        // Edge-to-edge makes API 30+ ignore the manifest's adjustResize, and Scaffold's default
        // insets exclude the IME, so without this the keyboard covers Readme with no way to scroll
        // it back into view. It sits on the outer column so the strip rides the keyboard's top edge
        // rather than scrolling away with the form — see [paddingWithKeyboard] for why the strip
        // rests on the keyboard instead of hovering a navigation bar above it.
        Column(
            modifier =
            Modifier
                .paddingWithKeyboard(padding)
                .fillMaxSize(),
        ) {
            Column(
                modifier =
                Modifier
                    .weight(1f)
                    .readableWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = LayerPageGutter, end = LayerPageGutter, bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AvatarEditor(
                    state = state,
                    onPicked = onAvatarPicked,
                    onFailed = onAvatarFailed,
                )

                // The three texts on one card, each a label over a recessed field (3b): the fields are
                // wells in the card rather than outlined boxes floating on the page.
                LayerCard(
                    contentPadding = PaddingValues(start = 12.dp, top = 14.dp, end = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Bio is one line of plain text — the site renders no Markdown in it — so it gets
                    // no formatting keys. It has to *dismiss* them, though: the strip's target is
                    // sticky, and one left standing while the caret sits here would write into a field
                    // the user can no longer see. Focus landing on any plain field is the end of the
                    // strip's business.
                    FieldBlock(label = stringResource(Res.string.account_bio), markdown = false) {
                        OutlinedTextField(
                            value = state.bio,
                            onValueChange = onBioChange,
                            placeholder = { Text(stringResource(Res.string.account_bio_hint)) },
                            singleLine = true,
                            shape = PlazaFieldDefaults.shape,
                            colors = PlazaFieldDefaults.colors(inCard = true),
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = bioLabel }
                                .onFocusChanged { if (it.isFocused) target = null },
                        )
                    }

                    FieldBlock(
                        label = stringResource(Res.string.account_signature),
                        markdown = true,
                        helper = stringResource(Res.string.account_signature_helper),
                    ) {
                        MarkdownField(
                            fieldState = signatureState,
                            label = stringResource(Res.string.account_signature),
                            minLines = SIGNATURE_MIN_LINES,
                            focusRequester = signatureFocus,
                            onFocused = { target = MarkdownTarget.SIGNATURE },
                        )
                    }

                    FieldBlock(
                        label = stringResource(Res.string.account_readme),
                        markdown = true,
                        helper = stringResource(Res.string.account_readme_helper),
                    ) {
                        MarkdownField(
                            fieldState = readmeState,
                            label = stringResource(Res.string.account_readme),
                            minLines = README_MIN_LINES,
                            focusRequester = readmeFocus,
                            onFocused = { target = MarkdownTarget.README },
                        )
                    }
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
 * 3b's avatar card: the picture with its camera badge, what it is and whether it is saved yet, and
 * 更换 — both the badge and the button open the same menu.
 *
 * A menu rather than a bottom sheet: two items, anchored to the thing they act on. A sheet would
 * cover the avatar the user is trying to change. There is no 移除 item because the site has no such
 * operation — an account that has an avatar cannot go back to not having one.
 *
 * 3b also says where a pending picture came from (「来自相册」). [PendingAvatar] keeps the bitmap and
 * the upload, not the source, so the card says only that it is waiting to be saved.
 */
@Composable
private fun AvatarEditor(
    state: ProfileFieldsUiState,
    onPicked: (PendingAvatar) -> Unit,
    onFailed: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val picker = rememberAvatarPicker(onPicked = onPicked, onFailed = onFailed)
    val card = LocalPlazaLayers.current.card

    LayerCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp)) {
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
                        // The ring is the card's own colour, so the badge reads as sitting on top of
                        // the avatar rather than being a hole punched in it.
                        modifier =
                        Modifier
                            .size(AVATAR_BADGE_SIZE)
                            .background(card, CircleShape)
                            .padding(3.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                PlazaIcons.PhotoCamera,
                                contentDescription = stringResource(Res.string.account_avatar_change),
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.account_avatar_take_photo)) },
                            leadingIcon = { Icon(PlazaIcons.PhotoCamera, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                picker.takePhoto()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.account_avatar_pick)) },
                            leadingIcon = { Icon(PlazaIcons.Image, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                picker.pickImage()
                            },
                        )
                    }
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(Res.string.account_avatar),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                if (state.pendingAvatar != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            stringResource(Res.string.account_avatar_pending),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            OutlinedButton(onClick = { menuOpen = true }, shape = CircleShape) {
                Text(stringResource(Res.string.account_avatar_change_short))
            }
        }
    }
}

/**
 * One field of the card: its label (and a Markdown tag when the site renders it as Markdown) over the
 * field, with the site's helper text under it.
 */
@Composable
private fun FieldBlock(
    label: String,
    markdown: Boolean,
    helper: String? = null,
    field: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (markdown) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        stringResource(Res.string.account_markdown_tag),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        field()
        helper?.let { AccountFieldHelper(it) }
    }
}

/**
 * A form field whose text is Markdown. The strip that formats it belongs to the screen, not here:
 * both of these share one, so the field's job is to report when it becomes the strip's target.
 *
 * [label] is the field's accessible name: the visible label sits above it in [FieldBlock], outside
 * the field, so the field says it again for a screen reader.
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
        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = minLines),
        shape = PlazaFieldDefaults.shape,
        colors = PlazaFieldDefaults.colors(inCard = true),
        textStyle = MaterialTheme.typography.bodyLarge,
        modifier =
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label }
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

private val AVATAR_SIZE = 64.dp
private val AVATAR_BADGE_SIZE = 28.dp

/** The badge straddles the avatar's edge rather than sitting inside it, as the design shows. */
private val AVATAR_BADGE_OVERHANG = 6.dp
private const val SIGNATURE_MIN_LINES = 3
private const val README_MIN_LINES = 5

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun ProfileFieldsPreview() {
    PlazaTheme {
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
