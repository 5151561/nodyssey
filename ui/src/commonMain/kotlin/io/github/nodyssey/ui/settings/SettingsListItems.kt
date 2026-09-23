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
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.GroupedRowTrailing
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.component.PlazaSpinner
import io.github.plaza.designsys.component.groupShape
import io.github.plaza.designsys.component.groupedListItemColors
import io.github.plaza.designsys.theme.LocalPlazaLayers
import io.github.plaza.designsys.theme.Spacing

/**
 * One group of rows. The rows draw the card themselves ([GroupedListItem]: outer corners, hairlines and
 * the shadow, sliced per row), so this only stacks them flush — which is also what lets the same rows
 * be spread over a `LazyColumn` where there is no one group to put them in.
 */
@Composable
internal fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), content = content)
}

/** What a settings page scrolls inside: the 12dp card gutter the rest of the layer system uses. */
internal val SettingsPagePadding =
    PaddingValues(start = LayerPageGutter, end = LayerPageGutter, top = 4.dp, bottom = 24.dp)

/** Between one card and the next, and between a section label and its card. */
internal val SettingsItemGap = 8.dp

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
    GroupedListItem(
        first = top,
        last = bottom,
        leadingContent = icon,
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
            ) {
                Text(title, style = settingsRowTitleStyle(), modifier = Modifier.weight(1f))
                value?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        // The control rides in the supporting slot, under the title and at the text's indent, which is
        // where Material puts a row's second line — here it is a slider or a segmented button.
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                subtitle?.let {
                    Text(it, modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA))
                }
                Column(Modifier.padding(top = 2.dp), content = content)
            }
        },
    )
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
    contentColor: Color = Color.Unspecified,
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
    GroupedListItem(
        first = top,
        last = bottom,
        modifier = modifier,
        onClick = onClick,
        selected = selected,
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = groupedListItemColors(contentColor),
        leadingContent = leading,
        headlineContent = { Text(title, style = settingsRowTitleStyle()) },
        supportingContent =
        subtitle?.let {
            {
                Text(
                    it,
                    style =
                    if (subtitleMonospace) {
                        LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                    } else {
                        LocalTextStyle.current
                    },
                )
            }
        },
        trailingContent = { GroupedRowTrailing(trailing = trailing, showChevron = chevron) },
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
