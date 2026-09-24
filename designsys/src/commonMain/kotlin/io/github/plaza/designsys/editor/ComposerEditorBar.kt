package io.github.plaza.designsys.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.plaza.designsys.component.LayerCardShape
import io.github.plaza.designsys.component.PlazaBackHandler
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.resources.Res
import io.github.plaza.designsys.resources.composer_format_close
import io.github.plaza.designsys.resources.composer_format_hint
import io.github.plaza.designsys.resources.composer_format_open
import io.github.plaza.designsys.resources.composer_toolbar_customize
import io.github.plaza.designsys.theme.ControlShape
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.cardBorderStroke
import io.github.plaza.designsys.theme.floatShadow
import org.jetbrains.compose.resources.stringResource

/**
 * The post and reply editors' bar: "先写，后排版" (boards 1d, 1e, 2c).
 *
 * Most of what a topic or a reply needs is typed, not formatted, so what sits on the keyboard's top
 * edge is the short list of things that are *inserted* — a picture, a sticker, an @ — on a rounded
 * quick bar, with a 格式 pill at its end. The pill swaps the bar for a card of the eight formatting
 * keys, big enough to hit without looking, and 收起 swaps it back.
 *
 * [actions] is the writer's own arrangement (the wrench panel edits it), so someone who wants 加粗 one
 * tap away can pin it to the bar. The card always offers all of [FormatKeys] in a fixed order, because
 * it is a grid, and a grid whose cells move depending on a setting elsewhere is one you have to read
 * every time you open it.
 *
 * The card stays open across taps — bold, then a link, is one visit — and the writer closes it.
 * Opening the emoji panel closes it too; see [MarkdownEditorState.toggleEmoji]. Like that panel it
 * takes the keyboard's place rather than stacking on it: opening it puts the keyboard away, and the
 * keyboard coming back — the writer tapped into the text — puts the card away. Stacked, the two are
 * most of a phone's height, and what does not fit is the card's bottom row or the text being
 * formatted.
 *
 * The wrench ([onCustomize]) sits in the card's footer: the bar's width is spoken for by the pill, and
 * the card is where the writer has already stopped to arrange things.
 */
@Composable
fun ComposerEditorBar(
    actions: List<EditorAction>,
    bodyState: TextFieldState,
    editorState: MarkdownEditorState,
    modifier: Modifier = Modifier,
    /**
     * The quick bar and the 格式 card only, not the emoji panel under them — a host that keeps its
     * text to a readable column narrows the keys with it here. The panel stands in for the keyboard
     * and takes the width a keyboard would, which [modifier] would have narrowed along with the keys.
     */
    barModifier: Modifier = Modifier,
    /** The host owns the photo picker, so [EditorAction.IMAGE] comes back out rather than acting. */
    onPickImages: () -> Unit = {},
    /** Runs after markup is applied — both editors put focus back in the body with it. */
    onFormatted: () -> Unit = {},
    onCustomize: (() -> Unit)? = null,
    /** Rides at the end of the bar's keys, ahead of the 格式 pill. */
    appMenu: (@Composable () -> Unit)? = null,
    /** Drawn in the keyboard's place while [EditorAction.EMOJI] is lit. */
    emojiPanel: @Composable (EmojiPanelScope) -> Unit = {},
) {
    val keyboard = LocalSoftwareKeyboardController.current
    // Either panel stands in for part of the keyboard, and back puts it away before it leaves.
    PlazaBackHandler(enabled = editorState.emojiOpen || editorState.formatOpen) { editorState.closePanels() }
    val onAction: (EditorAction) -> Unit = { action ->
        editorState.dispatch(action, bodyState, onPickImages, onFormatted) { keyboard?.hide() }
    }
    val openFormat: () -> Unit = {
        editorState.toggleFormat()
        keyboard?.hide()
    }
    // The inset rather than `WindowInsets.isImeVisible`, which is not in the common source set. Read
    // through derivedStateOf so the keyboard's animation recomposes this once per edge, not per frame.
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val imeVisible by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    LaunchedEffect(imeVisible) { if (imeVisible && editorState.formatOpen) editorState.toggleFormat() }
    val motion = MaterialTheme.motionScheme

    Column(modifier) {
        AnimatedContent(
            targetState = editorState.formatOpen,
            modifier = barModifier,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
            label = "composer-format",
        ) { formatOpen ->
            if (formatOpen) {
                FormatCard(
                    onAction = onAction,
                    onCustomize = onCustomize,
                    onClose = editorState::toggleFormat,
                )
            } else {
                QuickBar(
                    actions = actions,
                    active = if (editorState.emojiOpen) setOf(EditorAction.EMOJI) else emptySet(),
                    onAction = onAction,
                    appMenu = appMenu,
                    onOpenFormat = openFormat,
                )
            }
        }
        if (editorState.emojiOpen) emojiPanel(editorState.emojiPanelScope(bodyState))
    }
}

/** The eight keys the 格式 card offers, in the order of board 1e's two rows. */
private val FormatKeys: List<EditorAction> =
    listOf(
        EditorAction.BOLD,
        EditorAction.STRIKETHROUGH,
        EditorAction.HEADING,
        EditorAction.LINK,
        EditorAction.QUOTE,
        EditorAction.LIST,
        EditorAction.CODE,
        EditorAction.ITALIC,
    )

@Composable
private fun QuickBar(
    actions: List<EditorAction>,
    active: Set<EditorAction>,
    onAction: (EditorAction) -> Unit,
    appMenu: (@Composable () -> Unit)?,
    onOpenFormat: () -> Unit,
) {
    val layers = LocalPlazaLayers.current
    // Recessed rather than raised: the bar is part of the page the writer is typing on, and it is the
    // 格式 pill on it that stands up — the one thing on the bar that opens more.
    Surface(
        color = layers.inset,
        shape = LayerCardShape,
        modifier = Modifier.fillMaxWidth().padding(BarMargin),
    ) {
        Row(modifier = Modifier.padding(start = 2.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions.forEach { action ->
                    // 40dp keys, rounded a step inside the bar's own corners; Material still hit-tests
                    // them at 48.
                    ToolbarKey(
                        icon = action.icon,
                        contentDescription = stringResource(action.label),
                        checkable = action.opensPanel,
                        selected = action in active,
                        onClick = { onAction(action) },
                        size = QuickKeySize,
                        shape = MaterialTheme.shapes.medium,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                appMenu?.invoke()
            }
            // Surface's click overload sets no role, so the two pills here state theirs.
            Surface(
                onClick = onOpenFormat,
                shape = FormatPillShape,
                color = layers.raised,
                border = layers.cardBorderStroke,
                modifier = Modifier.padding(start = 4.dp).height(32.dp).semantics { role = Role.Button },
            ) {
                Row(
                    modifier = Modifier.padding(start = 8.dp, end = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(PlazaIcons.TextFormat, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(Res.string.composer_format_open),
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * Board 1e: the formatting keys as a floating card in the quick bar's place.
 *
 * It floats (the float shadow, the raised colour) where the bar it replaces is recessed, because it
 * is a transient layer over the text rather than part of the page — the same step up a menu takes.
 */
@Composable
private fun FormatCard(
    onAction: (EditorAction) -> Unit,
    onCustomize: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val layers = LocalPlazaLayers.current
    Surface(
        color = layers.raised,
        shape = LayerCardShape,
        border = layers.cardBorderStroke,
        modifier = Modifier.fillMaxWidth().padding(BarMargin).floatShadow(LayerCardShape, layers.shadows),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Two rows of four with weights rather than a lazy grid: eight fixed cells never scroll,
            // and a `Row` lets each key take an equal share of whatever width the card has.
            FormatKeys.chunked(FORMAT_COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { action ->
                        FilledTonalIconButton(
                            onClick = { onAction(action) },
                            shape = ControlShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = layers.inset,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            modifier = Modifier.weight(1f).height(KeySize),
                        ) {
                            Icon(action.icon, contentDescription = stringResource(action.label))
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp).size(18.dp),
                )
                Text(
                    text = stringResource(Res.string.composer_format_hint),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    letterSpacing = 0.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                onCustomize?.let { customize ->
                    IconButton(onClick = customize, modifier = Modifier.size(IconButtonDefaults.extraSmallContainerSize())) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = stringResource(Res.string.composer_toolbar_customize),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Surface(
                    onClick = onClose,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.height(32.dp).semantics { role = Role.Button },
                ) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(Res.string.composer_format_close),
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

private val BarMargin = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp)
private val QuickKeySize = 40.dp

/** The 格式 pill's corners — a 32dp button's, a step inside the bar's. */
private val FormatPillShape = RoundedCornerShape(10.dp)
private val KeySize = 48.dp
private const val FORMAT_COLUMNS = 4
