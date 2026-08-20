package io.github.nodyssey.ui.settings.theme

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.graphics.get
import io.github.nodyssey.ui.resources.Res
import io.github.nodyssey.ui.resources.settings_seed_apply_once
import io.github.nodyssey.ui.resources.settings_seed_hex
import io.github.nodyssey.ui.resources.settings_seed_name
import io.github.nodyssey.ui.resources.settings_seed_pick_from_image
import io.github.nodyssey.ui.resources.settings_seed_pick_hint
import io.github.nodyssey.ui.resources.settings_seed_pick_loading
import io.github.nodyssey.ui.resources.settings_seed_save
import io.github.nodyssey.ui.resources.settings_seed_sheet_title
import io.github.plaza.designsys.component.PlazaIcons
import io.github.plaza.designsys.theme.LocalPlazaDarkTheme
import io.github.plaza.designsys.theme.PlazaPaletteStyle
import io.github.plaza.designsys.theme.PlazaSeedHct
import io.github.plaza.designsys.theme.Spacing
import io.github.plaza.designsys.theme.plazaSeedColorScheme
import io.github.plaza.designsys.theme.toPlazaSeedHct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * 自定义种子色 — j1 卡3.
 *
 * Nothing here writes through until one of the two buttons is pressed. That is the whole reason the
 * sheet has two of them: 仅本次应用 puts the colour on the app and leaves 我的主题 alone, 保存为我的主题
 * does both. A picker that re-themed the app on every drag would have made the sheet's own surface
 * move under the thumb that was dragging.
 *
 * @param onApply the colour, and a name when it should also be kept. Null name means 仅本次应用.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeedColorSheet(
    initial: Color,
    paletteStyle: PlazaPaletteStyle,
    onDismiss: () -> Unit,
    onApply: (color: Color, name: String?) -> Unit,
) {
    var hct by remember { mutableStateOf(initial.toPlazaSeedHct()) }
    var hex by remember { mutableStateOf(initial.toHexString()) }
    var name by remember { mutableStateOf("") }
    var sampling by remember { mutableStateOf<Uri?>(null) }
    val color = remember(hct) { hct.toColor() }
    val darkTheme = LocalPlazaDarkTheme.current

    fun moveTo(next: PlazaSeedHct) {
        hct = next
        hex = next.toColor().toHexString()
    }

    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            sampling = uri
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Straight to full height: the sheet is a picker, two text fields and two buttons, and a
        // half-open state would have put the actions off screen behind a drag.
        sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            // A sheet gets no `Scaffold` padding, so the keyboard the two text fields raise is
            // handled here rather than through `paddingWithKeyboard`.
            modifier =
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.settings_seed_sheet_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                ) {
                    Icon(
                        PlazaIcons.Colorize,
                        contentDescription = stringResource(Res.string.settings_seed_pick_from_image),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            val samplingUri = sampling
            if (samplingUri == null) {
                ChromaTonePanel(
                    hct = hct,
                    onChange = ::moveTo,
                    modifier = Modifier.fillMaxWidth().height(PanelHeight),
                )
                Spacer(Modifier.height(14.dp))
                HueBar(
                    hue = hct.hue,
                    onChange = { moveTo(hct.copy(hue = it)) },
                    modifier = Modifier.fillMaxWidth().height(HueBarHeight),
                )
            } else {
                ImageSampler(
                    uri = samplingUri,
                    onPick = {
                        moveTo(it.toPlazaSeedHct())
                        sampling = null
                    },
                    onCancel = { sampling = null },
                    modifier = Modifier.fillMaxWidth().height(PanelHeight + HueBarHeight + 14.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextField(
                    value = hex,
                    onValueChange = { typed ->
                        hex = typed
                        // Only a complete six-digit value moves the panel; half-typed input has to
                        // be allowed to sit in the field without repainting the sheet under it.
                        parseHexColor(typed)?.let { hct = it.toPlazaSeedHct() }
                    },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.settings_seed_hex)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    isError = parseHexColor(hex) == null,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }

            Spacer(Modifier.height(14.dp))
            SchemeStrip(
                seed = color,
                paletteStyle = paletteStyle,
                darkTheme = darkTheme,
                modifier = Modifier.fillMaxWidth().height(34.dp),
            )

            Spacer(Modifier.height(12.dp))
            TextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                singleLine = true,
                label = { Text(stringResource(Res.string.settings_seed_name)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = { onApply(color, null) },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text(stringResource(Res.string.settings_seed_apply_once))
                }
                Button(
                    onClick = { onApply(color, name.ifBlank { color.toHexString() }) },
                    modifier = Modifier.weight(1.25f).height(48.dp),
                ) {
                    Icon(
                        PlazaIcons.BookmarkAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(Res.string.settings_seed_save))
                }
            }
        }
    }
}

/**
 * The picker's square: 鲜艳度 across, 明度 down.
 *
 * Drawn in HCT chroma and tone rather than HSV saturation and value, though it looks like every
 * other colour square. The reason is the generator: only the seed's hue and chroma reach the
 * palettes (see `plazaSeedColorScheme`), so on an HSV square a drag straight down the value axis
 * would have moved the marker across half the panel and changed the app by almost nothing. Here
 * every position in the square is a colour the scheme can actually tell apart.
 *
 * The gradient is a small bitmap scaled up rather than a `Brush`: no two-stop gradient describes an
 * HCT plane, and the corner where the requested chroma does not fit in sRGB has a curved edge that a
 * linear ramp cannot draw. 48×48 is enough at this size once the sampler smooths it, and it is
 * recomputed only when the hue moves.
 */
@Composable
private fun ChromaTonePanel(
    hct: PlazaSeedHct,
    onChange: (PlazaSeedHct) -> Unit,
    modifier: Modifier = Modifier,
) {
    val plane = remember(hct.hue) { chromaTonePlane(hct.hue) }
    val markerColor = remember(hct) { hct.toColor() }
    // Captured so the pick lambdas stay stable while the seed moves; `pointerInput(Unit)` keys on
    // nothing, and a lambda that closed over `hct` would go stale after the first drag.
    val current by rememberUpdatedState(hct)

    fun IntSize.pick(position: Offset) {
        if (width <= 0 || height <= 0) return
        onChange(
            current.copy(
                chroma = (position.x / width).coerceIn(0f, 1f) * PlazaSeedHct.CHROMA_RANGE.endInclusive,
                tone = (1f - (position.y / height).coerceIn(0f, 1f)) * MAX_TONE,
            ),
        )
    }

    Canvas(
        modifier =
        modifier
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures { size.pick(it) }
            }.pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { size.pick(it) },
                    onDrag = { change, _ -> size.pick(change.position) },
                )
            },
    ) {
        drawImage(
            image = plane,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        )
        val center =
            Offset(
                x = (hct.chroma / PlazaSeedHct.CHROMA_RANGE.endInclusive).coerceIn(0f, 1f) * size.width,
                y = (1f - (hct.tone / MAX_TONE).coerceIn(0f, 1f)) * size.height,
            )
        drawCircle(markerColor, radius = MarkerRadius.toPx(), center = center)
        drawCircle(
            Color.White,
            radius = MarkerRadius.toPx(),
            center = center,
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

/** One hue's chroma × tone plane, as a small bitmap the canvas scales up. */
private fun chromaTonePlane(hue: Float): ImageBitmap {
    val pixels = IntArray(PLANE_SIZE * PLANE_SIZE)
    for (row in 0 until PLANE_SIZE) {
        val tone = (1f - row / (PLANE_SIZE - 1f)) * MAX_TONE
        for (column in 0 until PLANE_SIZE) {
            val chroma = column / (PLANE_SIZE - 1f) * PlazaSeedHct.CHROMA_RANGE.endInclusive
            pixels[row * PLANE_SIZE + column] =
                PlazaSeedHct(hue = hue, chroma = chroma, tone = tone).toColor().toArgb()
        }
    }
    return Bitmap.createBitmap(pixels, PLANE_SIZE, PLANE_SIZE, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * 色相, the full circle.
 *
 * Painted at a fixed chroma and tone rather than at the seed's own, so that dialling the chroma down
 * to nothing does not turn the hue bar grey and leave the reader with no way back out.
 */
@Composable
private fun HueBar(
    hue: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stops = remember { List(HUE_STOPS) { hueStop(it) } }
    var width by remember { mutableIntStateOf(0) }

    fun IntSize.pick(position: Offset) {
        if (width <= 0) return
        onChange((position.x / width).coerceIn(0f, 1f) * PlazaSeedHct.HUE_RANGE.endInclusive)
    }

    Box(modifier) {
        Canvas(
            modifier =
            Modifier
                .fillMaxWidth()
                .height(HueBarHeight)
                .clip(CircleShape)
                .onSizeChanged { width = it.width }
                .pointerInput(Unit) {
                    detectTapGestures { size.pick(it) }
                }.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { size.pick(it) },
                        onDrag = { change, _ -> size.pick(change.position) },
                    )
                },
        ) {
            drawRect(Brush.horizontalGradient(stops))
        }
        Box(
            modifier =
            Modifier
                .offset {
                    IntOffset(
                        x = ((hue / PlazaSeedHct.HUE_RANGE.endInclusive) * width).roundToInt() -
                            (HandleWidth.toPx() / 2).roundToInt(),
                        y = -((HandleHeight - HueBarHeight) / 2).roundToPx(),
                    )
                }.size(width = HandleWidth, height = HandleHeight)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        )
    }
}

private fun hueStop(index: Int): Color =
    PlazaSeedHct(
        hue = index * (360f / (HUE_STOPS - 1)),
        chroma = HUE_BAR_CHROMA,
        tone = HUE_BAR_TONE,
    ).toColor()

/**
 * Five bands of what the seed above turns into, redrawn on every drag.
 *
 * The roles rather than a tonal ramp: these are the five the reader will meet, and two families
 * rather than one because a scheme's second colour is the half a single-hue ramp never showed.
 */
@Composable
internal fun SchemeStrip(
    seed: Color,
    paletteStyle: PlazaPaletteStyle,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
) {
    val scheme = remember(seed, darkTheme, paletteStyle) { plazaSeedColorScheme(seed, darkTheme, paletteStyle) }
    Row(modifier.clip(RoundedCornerShape(corner))) {
        listOf(
            scheme.primary,
            scheme.secondary,
            scheme.primaryContainer,
            scheme.tertiary,
            scheme.tertiaryContainer,
        ).forEach { band ->
            Box(Modifier.weight(1f).fillMaxHeight().background(band))
        }
    }
}

/**
 * 从图片取色 — the eyedropper, as far as Android lets one exist.
 *
 * There is no system API for lifting a colour off the screen, so the sheet borrows the one surface a
 * colour can be lifted from without a permission: a picture the reader chooses through the photo
 * picker. Tapping the image samples the pixel under the finger.
 *
 * The bitmap is decoded off the main thread and downscaled on the way in — a modern phone photo is
 * tens of megapixels, and the panel it lands in is a few hundred pixels wide.
 */
@Composable
private fun ImageSampler(
    uri: Uri,
    onPick: (Color) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap =
            withContext(Dispatchers.IO) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(
                            ImageDecoder.createSource(context.contentResolver, uri),
                        ) { decoder, info, _ ->
                            val longest = maxOf(info.size.width, info.size.height)
                            decoder.setTargetSampleSize(
                                generateSequence(1) { it * 2 }
                                    .first { longest / it <= SAMPLE_TARGET_PX },
                            )
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }
                }.getOrNull()
            }
        if (bitmap == null) onCancel()
    }

    val image = bitmap
    Box(modifier.clip(RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
        if (image == null) {
            Text(
                stringResource(Res.string.settings_seed_pick_loading),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = stringResource(Res.string.settings_seed_pick_hint),
                contentScale = ContentScale.Crop,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .pointerInput(image) {
                        val drawn = size.toSize()
                        detectTapGestures { position ->
                            // Crop scales the shorter edge to fill and centres the overflow, so the
                            // tap has to be mapped back through the same transform to hit the pixel
                            // the finger is actually on.
                            val scale =
                                maxOf(drawn.width / image.width, drawn.height / image.height)
                            val x = (position.x - (drawn.width - image.width * scale) / 2) / scale
                            val y = (position.y - (drawn.height - image.height * scale) / 2) / scale
                            val pixel =
                                image[
                                    x.roundToInt().coerceIn(0, image.width - 1),
                                    y.roundToInt().coerceIn(0, image.height - 1),
                                ]
                            onPick(Color(pixel))
                        }
                    },
            )
            Text(
                stringResource(Res.string.settings_seed_pick_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(Spacing.sm)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(horizontal = Spacing.sm, vertical = 4.dp),
            )
        }
    }
}

/** Tone above ~95 is white at every chroma, so the panel would end in a band that does nothing. */
private const val MAX_TONE = 95f
private const val PLANE_SIZE = 48
private const val HUE_STOPS = 13
private const val HUE_BAR_CHROMA = 60f
private const val HUE_BAR_TONE = 55f
private const val MAX_NAME_LENGTH = 16
private const val SAMPLE_TARGET_PX = 1024

private val PanelHeight = 150.dp
private val HueBarHeight = 16.dp
private val HandleWidth = 8.dp
private val HandleHeight = 28.dp
private val MarkerRadius = 11.dp
