package io.github.nodyssey.ui.postdetail

import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Robolectric has no surface to draw the text-selection magnifier on and throws dismissing it.
 * For tests where a long press starts a selection: the gesture is what is under test, not the loupe.
 */
@Implements(android.widget.Magnifier::class)
class NoopMagnifier {
    @Implementation
    fun show(sourceCenterX: Float, sourceCenterY: Float) = Unit

    @Implementation
    fun show(sourceCenterX: Float, sourceCenterY: Float, magnifierCenterX: Float, magnifierCenterY: Float) = Unit

    @Implementation
    fun dismiss() = Unit

    @Implementation
    fun update() = Unit
}
