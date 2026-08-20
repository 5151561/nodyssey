package io.github.plaza.designsys.image

/**
 * Unknown, and deliberately not a guess at more.
 *
 * The three distinctions above [ImageLoadFailure.Unknown] — unreachable, timed out, some other
 * transport failure — are read off exception *types*, and on this platform there are not any yet:
 * `java.net` is not here, and which types appear instead is decided by the network engine Coil is
 * given, which on iOS is nothing so far. Writing the `when` now would mean naming exceptions that no
 * code in this repository can raise, and the branches would be wrong in a way nothing catches.
 *
 * So the honest answer is the one the sealed interface already has a name for. What decides this
 * file is the step that gives iOS an image pipeline at all; the HTTP half above it — a challenge
 * against an ordinary 403 — is Coil's own type and already answered in `commonMain`.
 */
internal actual fun platformImageFailure(throwable: Throwable): ImageLoadFailure =
    ImageLoadFailure.Unknown
