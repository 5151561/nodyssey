@file:OptIn(ExperimentalForeignApi::class)

package io.github.plaza.core.image

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.CFStringRef
import platform.CoreGraphics.CGImageRef
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePNGRepresentation

/*
 * Decoding a picture at a bounded size, for the three places that need one.
 *
 * Bytes in, bytes out, and never a full-size `UIImage` in between — a `UIImage` is a *decoded*
 * image, so the moment one exists for a camera roll photo the app is holding tens of megabytes of
 * pixels it is about to throw away. That is the same trade the `inSampleSize` passes in
 * `AvatarPicker.android.kt` and `DefaultImagePreparer.kt` make, and this is the Apple spelling of it.
 *
 * In `:shared` rather than beside any one caller because there are three of them in two modules —
 * the avatar picker and the palette sampler in `:ui`, the upload preparer in `:iosapp` — and the
 * duplicate would be forty lines of Core Foundation reference counting, which is exactly the kind of
 * code that stops agreeing with its copy.
 *
 * In `iosMain` and not `appleMain`, which is the mistake this file was written as: `UIImage` is
 * UIKit and UIKit is iOS only, so an `appleMain` copy compiles for both iOS arches and fails for
 * `macosArm64` — a target this module has and the two above it do not. `:shared:macosArm64Test` is
 * the gate that says so, and it is the reason that gate is run by hand on a Mac.
 */

/**
 * Re-encodes at a bounded size as JPEG, or null when the bytes are not an image.
 *
 * @param quality 0..1, JPEG's own scale. Callers that state a percentage divide.
 */
fun NSData.downscaledJpeg(maxEdgePx: Int, quality: Double): NSData? =
    withBoundedImage(maxEdgePx) { image -> UIImageJPEGRepresentation(image, quality) }

/**
 * The same, as PNG.
 *
 * Lossless because the caller that asks for it samples *colours* out of the result: JPEG's chroma
 * subsampling moves them by little enough to be invisible and by more than enough to shift a palette
 * derived from three pixels.
 */
fun NSData.downscaledPng(maxEdgePx: Int): NSData? =
    withBoundedImage(maxEdgePx) { image -> UIImagePNGRepresentation(image) }

/**
 * Decodes at most [maxEdgePx] pixels on the longest edge and hands the result to [use].
 *
 * `CGImageSourceCreateThumbnailAtIndex` is this platform's `inSampleSize`: it reads the header, then
 * decodes only as many pixels as were asked for. `…WithTransform` applies the EXIF orientation on the
 * way, without which a photo taken in portrait arrives on its side — the kind of thing only a real
 * camera reproduces.
 *
 * The three Core Foundation objects are released here because the two `Create` calls return them
 * owned; [use] gets a borrowed image and must not keep it.
 */
private fun <T> NSData.withBoundedImage(maxEdgePx: Int, use: (UIImage) -> T?): T? {
    @Suppress("UNCHECKED_CAST")
    val data = CFBridgingRetain(this) as CFDataRef
    try {
        val source = CGImageSourceCreateWithData(data, null) ?: return null
        try {
            @Suppress("UNCHECKED_CAST")
            val options =
                CFBridgingRetain(
                    mapOf(
                        kCGImageSourceCreateThumbnailFromImageAlways.asNSString() to true,
                        kCGImageSourceCreateThumbnailWithTransform.asNSString() to true,
                        kCGImageSourceThumbnailMaxPixelSize.asNSString() to maxEdgePx,
                    ),
                ) as CFDictionaryRef
            val thumbnail: CGImageRef =
                try {
                    CGImageSourceCreateThumbnailAtIndex(source, index = 0.convert(), options = options)
                } finally {
                    CFRelease(options)
                } ?: return null
            return try {
                use(UIImage.imageWithCGImage(thumbnail))
            } finally {
                CFRelease(thumbnail)
            }
        } finally {
            CFRelease(source)
        }
    } finally {
        CFRelease(data)
    }
}

/**
 * Bridges one of ImageIO's `CFString` option keys into something a Kotlin map can be keyed by.
 *
 * The retain is what makes the release inside `CFBridgingRelease` balance: these are process
 * constants this file never owned a reference to, and consuming one would be an over-release of a
 * global.
 */
private fun CFStringRef?.asNSString(): NSString = CFBridgingRelease(CFRetain(this)) as NSString
