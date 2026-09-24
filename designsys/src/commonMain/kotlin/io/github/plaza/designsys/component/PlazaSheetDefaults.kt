package io.github.plaza.designsys.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp

/**
 * What every `ModalBottomSheet` in the app is given in place of Material's defaults: a 20dp top
 * corner rather than 28, and a drag handle that sits 8dp under the sheet's edge rather than 22.
 *
 * Both from the Lean round (3a, 3b), which takes the sheet's corners down a step with every other
 * radius and gives the 44dp of air around Material's handle back to the content under it.
 */
object PlazaSheetDefaults {
    val shape: Shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    /**
     * Material's [BottomSheetDefaults.DragHandle] — its bar, its colour and the description a screen
     * reader hears — with the space around the bar trimmed to [HandleTop] above and [HandleBottom]
     * below.
     *
     * The handle pads itself inside whatever modifier it is handed, and takes no parameter for how
     * much, so the padding cannot be asked away; this measures the padded handle and reports it
     * shorter instead. The padding is read off the measurement rather than assumed, so a Material
     * release that changes it moves nothing here. Replace this with the parameter if the handle ever
     * grows one.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DragHandle() {
        BottomSheetDefaults.DragHandle(
            height = HandleHeight,
            modifier =
            Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val padding = (placeable.height - HandleHeight.roundToPx()) / 2
                val top = HandleTop.roundToPx().coerceAtMost(padding)
                val bottom = HandleBottom.roundToPx().coerceAtMost(padding)
                layout(placeable.width, placeable.height - (padding - top) - (padding - bottom)) {
                    placeable.place(0, top - padding)
                }
            },
        )
    }

    private val HandleHeight = 4.dp
    private val HandleTop = 8.dp
    private val HandleBottom = 10.dp
}
