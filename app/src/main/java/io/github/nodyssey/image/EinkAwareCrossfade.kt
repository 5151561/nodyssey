package io.github.nodyssey.image

import coil3.request.ImageResult
import coil3.transition.CrossfadeTransition
import coil3.transition.Transition
import coil3.transition.TransitionTarget

/**
 * Coil's crossfade, with an off switch the singleton loader cannot otherwise have.
 *
 * 墨水屏模式 is a setting, and every other thing it turns off is inside the composition, where the
 * setting is simply a value. The `ImageLoader` is not: it is built once by [io.github.nodyssey
 * .NodysseyApp] as a `SingletonImageLoader.Factory`, before any composition exists, and it is never
 * rebuilt — so reading the setting where the loader is configured would answer with whatever was
 * stored at launch and keep answering that until the app was killed.
 *
 * Hence a factory that decides per image rather than a loader that decided once. The flag is written
 * from the settings collector in `NodysseyApp` — the same arrangement the notification scheduler
 * uses, and for the same reason: the SSOT drives the process-level thing directly, so it stays right
 * even when the setting changes with the settings screen closed.
 *
 * `@Volatile` because the write is on the collector's dispatcher and the reads are on whichever
 * thread finished an image.
 */
internal object EinkAwareCrossfade : Transition.Factory {
    @Volatile
    var crossfading: Boolean = true

    private val crossfade = CrossfadeTransition.Factory()

    override fun create(target: TransitionTarget, result: ImageResult): Transition =
        if (crossfading) {
            crossfade.create(target, result)
        } else {
            Transition.Factory.NONE.create(target, result)
        }
}
