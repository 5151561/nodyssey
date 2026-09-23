package io.github.nodyssey.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.common.describedAsLoading
import io.github.plaza.designsys.component.GroupRowDivider
import io.github.plaza.designsys.component.LayerGroup
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.groupShape
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Spacing

/**
 * The grouped-list vocabulary the settings screens are built from.
 *
 * Shared rather than copied because the grouping is the point: a group is one white card on the grey
 * page, split by inset hairlines. Two screens grouping their rows differently is exactly the kind of
 * drift nobody notices in review and everybody notices side by side.
 */
@Composable
internal fun SettingsSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = Spacing.md, top = 10.dp, bottom = 2.dp).semantics { heading() },
    )
}

/**
 * One group: a single [LayerGroup] card around its rows.
 *
 * One card rather than a card per row, because the card's soft shadow is drawn around its outline —
 * rows that each cast their own would print a shadow onto the row above them at every seam. The
 * rows still take `top` / `bottom`, which is what decides where the inset hairlines go and lets the
 * same rows be spread over a `LazyColumn` where there is no one card to put them in.
 */
@Composable
internal fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    LayerGroup(modifier = modifier.fillMaxWidth(), content = content)
}

/** What a settings page scrolls inside: the 12dp card gutter the rest of the layer system uses. */
internal val SettingsPagePadding =
    PaddingValues(start = LayerPageGutter, end = LayerPageGutter, top = 4.dp, bottom = 24.dp)

/** Between one card and the next, and between a section label and its card. */
internal val SettingsItemGap = 8.dp

/** Where a row's hairline starts: under its text, past the icon column when there is one. */
private fun dividerInset(hasIcon: Boolean): Dp = if (hasIcon) 56.dp else Spacing.lg

/** A row's title: 15sp medium, one step under a list title so eight rows still fit a screen. */
@Composable
internal fun settingsRowTitleStyle(): TextStyle =
    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)

/** A row whose control needs its own line — a slider, a segmented button, a preview block. */
@Composable
internal fun SettingsBlock(
    title: String,
    top: Boolean = false,
    bottom: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    /** The control's current reading, in the primary colour at the end of the title line — 「16sp」. */
    value: String? = null,
    /**
     * Dims the label, and only the label.
     *
     * The control below it is the caller's — every one of them takes its own `enabled`, and dimming
     * the whole block on top of that would multiply the two and leave a segmented button too faint to
     * read as anything at all.
     */
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = LocalPlazaLayers.current.card,
        shape = groupShape(first = top, last = bottom),
    ) {
        Column {
            if (!top) GroupRowDivider(startInset = dividerInset(icon != null))
            Column(
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
                ) {
                    icon?.invoke()
                    Column(
                        modifier = Modifier.weight(1f).padding(start = if (icon == null) 0.dp else Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(title, style = settingsRowTitleStyle())
                        subtitle?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    value?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                content()
            }
        }
    }
}

/**
 * A single-line row: leading icon, title, optional second line, optional trailing control.
 *
 * [contentColor] exists for the one destructive row in the family (退出登录), which is the same shape
 * as its neighbours and differs only in colour — a separate composable for it would duplicate the
 * layout to change two tints.
 */
@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    top: Boolean = false,
    bottom: Boolean = false,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    /**
     * Set on a row that is one of several choices, so the row reads as a radio button rather than as
     * a button that happens to have one drawn in it. Takes [onClick] as its action.
     */
    selected: Boolean? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    /**
     * A chevron at the trailing edge, for a row that opens a page of its own. Off by default because
     * the family also holds rows that act in place — 清除缓存, 退出登录 — and a chevron on those
     * promises a screen that never comes.
     */
    chevron: Boolean = false,
    /** For a subtitle that is an address or a value to be read character by character — a URL. */
    subtitleMonospace: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Surface(
        color = LocalPlazaLayers.current.card,
        contentColor = contentColor,
        shape = groupShape(first = top, last = bottom),
        modifier = modifier.then(
            when {
                checked != null && onCheckedChange != null ->
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    )

                selected != null && onClick != null ->
                    Modifier.selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = onClick,
                    )

                onClick != null -> Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)

                else -> Modifier
            },
        ),
    ) {
        Column {
            if (!top) GroupRowDivider(startInset = dividerInset(leading != null))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = Spacing.lg, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                leading?.invoke()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = settingsRowTitleStyle())
                    subtitle?.let {
                        Text(
                            it,
                            style =
                            MaterialTheme.typography.labelSmall.let { style ->
                                if (subtitleMonospace) style.copy(fontFamily = FontFamily.Monospace) else style
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                trailing()
                if (chevron) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

/**
 * The switch every settings row carries: Material's own, with the tick in its thumb while it is on.
 *
 * `onCheckedChange` is always null — the row around it is the toggle, which is what gives the whole
 * row a hit target and one semantics node rather than a row and a switch that both claim the tap.
 */
@Composable
internal fun SettingsSwitch(
    checked: Boolean,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = null,
        enabled = enabled,
        thumbContent =
        if (checked) {
            {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        } else {
            null
        },
    )
}

/**
 * What a screen dims the settings *behind* a master switch to.
 *
 * Shared by the three screens that have one, for the same reason the group shapes are: a settings
 * screen that dims to a different value than its neighbour looks broken rather than different.
 */
internal const val DISABLED_ALPHA = 0.5f

/**
 * The outlined segmented control: 8dp ends, a tick on the selected segment, card-white where it is
 * not selected so it reads as a control sitting on the card rather than a hole in it. Shared by 明暗,
 * 测评报告, 配色来源 and 代理类型 so the four cannot drift.
 */
@Composable
internal fun ConnectedChoiceButtons(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val card = LocalPlazaLayers.current.card
    val colors =
        SegmentedButtonDefaults.colors(
            inactiveContainerColor = card,
            disabledInactiveContainerColor = card,
        )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                colors = colors,
                shape =
                SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = labels.size,
                    baseShape = SegmentShape,
                ),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
        }
    }
}

private val SegmentShape = RoundedCornerShape(8.dp)

/**
 * A text field on a settings card: Material's outlined field, filled with the inset tone so it reads
 * as a well in the white card, with 6e's 14dp corners.
 */
@Composable
internal fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle = LocalTextStyle.current,
) {
    val inset = LocalPlazaLayers.current.inset
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        isError = isError,
        textStyle = textStyle,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        supportingText = supportingText?.let { { Text(it) } },
        shape = RoundedCornerShape(14.dp),
        colors =
        OutlinedTextFieldDefaults.colors(
            focusedContainerColor = inset,
            unfocusedContainerColor = inset,
            disabledContainerColor = inset,
            errorContainerColor = inset,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

/**
 * 测试 and 保存, side by side as two pills: the tonal one checks, the filled one commits. Shared by
 * 代理 and 加密 DNS, whose two actions are the same pair.
 */
@Composable
internal fun SettingsTestSaveButtons(
    testLabel: String,
    saveLabel: String,
    testing: Boolean,
    enabled: Boolean,
    onTest: () -> Unit,
    onSave: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FilledTonalButton(
            onClick = onTest,
            enabled = enabled && !testing,
            modifier = Modifier.weight(1f).heightIn(min = ActionButtonHeight),
        ) {
            if (testing) {
                PlazaSpinner(Modifier.describedAsLoading(), size = 18.dp)
            } else {
                Text(testLabel, style = MaterialTheme.typography.titleMedium)
            }
        }
        Button(
            onClick = onSave,
            enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = ActionButtonHeight),
        ) {
            Text(saveLabel, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private val ActionButtonHeight = 52.dp

/**
 * The outcome of a test, as a tinted strip under the fields: [error] in the error container,
 * otherwise in the tertiary one. Announced politely, because it arrives after a wait the reader did
 * not have to watch.
 */
@Composable
internal fun SettingsResultBanner(
    text: String,
    error: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = if (error) scheme.errorContainer else scheme.tertiaryContainer,
        contentColor = if (error) scheme.onErrorContainer else scheme.onTertiaryContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(
                if (error) PlazaIcons.ErrorCircle else Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        }
    }
}

/** Small print under a page's cards — what a setting cannot do, said before anyone types. */
@Composable
internal fun SettingsNote(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = Spacing.md),
    )
}
