package io.github.nodyssey.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.nodyssey.ui.common.MediumButton
import io.github.nodyssey.ui.common.MediumButtonStyle
import io.github.nodyssey.ui.common.SelectableMenuItem
import io.github.plaza.designsys.component.GroupCard
import io.github.plaza.designsys.component.GroupedListItem
import io.github.plaza.designsys.component.GroupedRow
import io.github.plaza.designsys.component.LayerPageGutter
import io.github.plaza.designsys.component.PlazaFieldDefaults
import io.github.plaza.designsys.component.groupedRowTitleStyle
import io.github.plaza.designsys.theme.Spacing

/**
 * One group: a single [GroupCard] around whatever it holds. Rows, but also the blocks that are not
 * rows — 主题's source picker and preset grid, 网络自检's report lines — which have no card of their own.
 */
@Composable
internal fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    GroupCard(modifier = modifier, content = content)
}

/** What a settings page scrolls inside: the 12dp card gutter the rest of the layer system uses. */
internal val SettingsPagePadding =
    PaddingValues(start = LayerPageGutter, end = LayerPageGutter, top = 4.dp, bottom = 24.dp)

/** Between one card and the next, and between a section label and its card. */
internal val SettingsItemGap = 8.dp

/** A row whose control needs its own line — a slider, a segmented button, a preview block. */
@Composable
internal fun SettingsBlock(
    title: String,
    top: Boolean = false,
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
        last = false,
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
            ) {
                Text(title, style = groupedRowTitleStyle(), modifier = Modifier.weight(1f))
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
 * A row whose choices open in a menu rather than as rows of their own — 语言, 色彩风格 — with its
 * answer on the second line.
 *
 * The menu hangs off the trailing icon and nothing wider, because a `DropdownMenu` is anchored to its
 * *parent* layout node — `Popup` reads `parentLayoutCoordinates`, not the position of its own
 * zero-sized node. Put it a level up and the anchor becomes the whole row, so the menu opens at the
 * row's bottom left however the enclosing box is aligned; with the icon as its parent, the menu ends
 * tucked under the control that opened it.
 */
@Composable
internal fun <T> SettingsMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    choices: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    last: Boolean = false,
    enabled: Boolean = true,
    menuIcon: ImageVector = Icons.Default.ArrowDropDown,
) {
    var expanded by remember { mutableStateOf(false) }
    GroupedRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        last = last,
        enabled = enabled,
        onClick = { expanded = true },
        showChevron = false,
        trailing = {
            Box {
                Icon(menuIcon, contentDescription = null)
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    choices.forEachIndexed { index, (choice, label) ->
                        SelectableMenuItem(
                            selected = choice == selected,
                            index = index,
                            count = choices.size,
                            text = { Text(label) },
                            onClick = {
                                expanded = false
                                onSelect(choice)
                            },
                        )
                    }
                }
            }
        },
    )
}

/**
 * What a [SettingsBlock]'s label dims to while its control is disabled — Material's own disabled
 * content alpha, so a block reads the same as the disabled rows around it.
 */
internal const val DISABLED_ALPHA = 0.38f

/** A text field on a settings card: one line unless told otherwise, its label inside, in the kit's in-card field style. */
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
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        isError = isError,
        textStyle = textStyle,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        supportingText = supportingText?.let { { Text(it) } },
        shape = PlazaFieldDefaults.shape,
        colors = PlazaFieldDefaults.colors(inCard = true),
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
        MediumButton(
            onClick = onTest,
            style = MediumButtonStyle.Tonal,
            enabled = enabled && !testing,
            busy = testing,
            modifier = Modifier.weight(1f),
        ) {
            Text(testLabel)
        }
        MediumButton(onClick = onSave, enabled = enabled, modifier = Modifier.weight(1f)) {
            Text(saveLabel)
        }
    }
}
