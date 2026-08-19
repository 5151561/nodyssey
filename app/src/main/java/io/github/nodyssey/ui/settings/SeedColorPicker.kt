package io.github.nodyssey.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.nodyssey.R
import io.github.plaza.designsys.theme.LocalPlazaDarkTheme
import io.github.plaza.designsys.theme.PlazaSeedHct
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.plazaSeedColorScheme
import io.github.plaza.designsys.theme.toPlazaSeedHct
import java.util.Locale

/**
 * The colours 自选 offers before anyone opens the picker.
 *
 * Nine rather than three: one row of swatches is the whole feature for most readers, and a spread
 * wide enough that everybody finds something they can live with is what keeps them out of the hue
 * slider. 石墨青 leads because it is the app's own colour — picking it is the closest thing to "put
 * it back".
 */
private data class SeedPreset(@StringRes val label: Int, val color: Color)

private val SeedPresets =
    listOf(
        SeedPreset(R.string.settings_seed_teal, Color(0xFF35606E)),
        SeedPreset(R.string.settings_seed_blue, Color(0xFF00639B)),
        SeedPreset(R.string.settings_seed_violet, Color(0xFF6750A4)),
        SeedPreset(R.string.settings_seed_magenta, Color(0xFF9C4472)),
        SeedPreset(R.string.settings_seed_red, Color(0xFF9C4234)),
        SeedPreset(R.string.settings_seed_amber, Color(0xFF8A5100)),
        SeedPreset(R.string.settings_seed_olive, Color(0xFF5F6B2E)),
        SeedPreset(R.string.settings_seed_green, Color(0xFF2E6B4F)),
        SeedPreset(R.string.settings_seed_graphite, Color(0xFF5B5F66)),
    )

private val SwatchSize = 44.dp

/**
 * The preset row, plus the swatch that opens the picker.
 *
 * "Selected" is decided by RGB equality with the stored seed, so a colour dialled in by hand leaves
 * every preset unselected and lights up the last swatch instead — which is exactly what the reader
 * did.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeedSwatchRow(
    seed: Color,
    onSeedChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf(false) }
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SeedPresets.forEach { preset ->
            Swatch(
                fill = Brush.linearGradient(listOf(preset.color, preset.color)),
                selected = preset.color.toArgb() == seed.toArgb(),
                checkColor = contentColorOn(preset.color),
                label = stringResource(preset.label),
                onClick = { onSeedChange(preset.color) },
            )
        }
        val custom = SeedPresets.none { it.color.toArgb() == seed.toArgb() }
        Swatch(
            // The whole hue circle, so the swatch says "any colour" without needing a glyph for it.
            // Once a colour has been dialled in the swatch wears it, the way the presets do.
            fill = if (custom) Brush.linearGradient(listOf(seed, seed)) else Brush.sweepGradient(HueRing),
            selected = custom,
            checkColor = contentColorOn(seed),
            label = stringResource(R.string.settings_seed_custom),
            onClick = { picking = true },
        )
    }
    if (picking) {
        SeedColorDialog(
            initial = seed,
            onDismiss = { picking = false },
            onConfirm = {
                onSeedChange(it)
                picking = false
            },
        )
    }
}

@Composable
private fun Swatch(
    fill: Brush,
    selected: Boolean,
    checkColor: Color,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
        Modifier
            .size(SwatchSize)
            .clip(CircleShape)
            .background(fill)
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                },
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = label, tint = checkColor)
        }
    }
}

/**
 * 色相 and 鲜艳度 rather than R/G/B.
 *
 * Those two are what the generator reads — the seed's HCT hue and chroma decide every palette, and
 * its tone is discarded — so a slider per channel would have let the reader move something that
 * changes nothing. Tone is still carried through [PlazaSeedHct] so that a colour typed in as hex and
 * then nudged comes back as itself.
 */
@Composable
private fun SeedColorDialog(
    initial: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    var hct by remember { mutableStateOf(initial.toPlazaSeedHct()) }
    var hex by remember { mutableStateOf(initial.toHexString()) }
    val color = remember(hct) { hct.toColor() }
    val darkTheme = LocalPlazaDarkTheme.current

    fun moveTo(next: PlazaSeedHct) {
        hct = next
        hex = next.toColor().toHexString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_seed_custom)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SchemePreview(seed = color, darkTheme = darkTheme)
                GradientSlider(
                    label = stringResource(R.string.settings_seed_hue),
                    value = hct.hue,
                    range = PlazaSeedHct.HUE_RANGE,
                    track = Brush.horizontalGradient(hueRamp(hct.chroma, hct.tone)),
                    onValueChange = { moveTo(hct.copy(hue = it)) },
                )
                GradientSlider(
                    label = stringResource(R.string.settings_seed_chroma),
                    value = hct.chroma,
                    range = PlazaSeedHct.CHROMA_RANGE,
                    track = Brush.horizontalGradient(chromaRamp(hct.hue, hct.tone)),
                    onValueChange = { moveTo(hct.copy(chroma = it)) },
                )
                OutlinedTextField(
                    value = hex,
                    onValueChange = { typed ->
                        hex = typed
                        // Only a complete six-digit value moves the sliders; half-typed input has to
                        // be allowed to sit in the field without repainting the dialog under it.
                        parseHexColor(typed)?.let { hct = it.toPlazaSeedHct() }
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_seed_hex)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    isError = parseHexColor(hex) == null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(color) }) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * What the seed turns into, drawn in the roles that carry the theme.
 *
 * A swatch of the seed itself would be misleading: the seed's own tone is thrown away, so two very
 * different-looking swatches can produce the same app. These five are what the reader will actually
 * see, in the light/dark they are currently in.
 */
@Composable
private fun SchemePreview(seed: Color, darkTheme: Boolean) {
    val scheme = remember(seed, darkTheme) { plazaSeedColorScheme(seed, darkTheme) }
    Surface(
        color = scheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                scheme.primary,
                scheme.primaryContainer,
                scheme.secondaryContainer,
                scheme.tertiaryContainer,
                scheme.surfaceContainerHighest,
            ).forEach { tone ->
                Box(
                    modifier =
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(tone),
                )
            }
            Text(
                stringResource(R.string.settings_seed_preview),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A Material slider wearing the ramp it moves along.
 *
 * The gradient goes in through the official `track` slot rather than being painted behind a slider:
 * the stops are real HCT colours at the current chroma and tone, so the bar shows what each position
 * is worth instead of an HSV lookalike.
 */
@Composable
private fun GradientSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    track: Brush,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            track = {
                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(CircleShape)
                        .background(track),
                )
            },
        )
    }
}

/** Twelve stops around the circle: enough that the ring reads as continuous. */
private val HueRing =
    List(13) { index ->
        PlazaSeedHct(hue = index * 30f % 360f, chroma = 48f, tone = 45f).toColor()
    }

private fun hueRamp(chroma: Float, tone: Float): List<Color> =
    List(13) { index -> PlazaSeedHct(hue = index * 30f, chroma = chroma, tone = tone).toColor() }

private fun chromaRamp(hue: Float, tone: Float): List<Color> =
    List(7) { index ->
        PlazaSeedHct(hue = hue, chroma = index * 20f, tone = tone).toColor()
    }

/** Black or white, whichever the swatch can be read against. */
private fun contentColorOn(color: Color): Color =
    if (color.luminance() > 0.4f) Color.Black else Color.White

private fun Color.toHexString(): String =
    String.format(Locale.ROOT, "#%06X", toArgb() and 0xFFFFFF)

/** `#RRGGBB` or bare `RRGGBB`; anything else is still being typed. */
private fun parseHexColor(text: String): Color? {
    val digits = text.removePrefix("#")
    if (digits.length != 6 || digits.any { it.digitToIntOrNull(16) == null }) return null
    return Color(0xFF000000.toInt() or digits.toInt(16))
}
