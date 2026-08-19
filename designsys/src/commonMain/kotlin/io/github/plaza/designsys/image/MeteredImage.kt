package io.github.plaza.designsys.image

import coil3.Extras
import coil3.request.ImageRequest
import okio.IOException

/**
 * Per-request opt-out from 仅 Wi-Fi 加载图片.
 *
 * The preference is about not spending mobile data *unasked*. A tap on a skipped image is the user
 * asking, so a request carrying this extra goes to the network on any connection. Without it the
 * switch is a wall rather than a default, and the only way to see one image is to go to 设置 and
 * turn the whole thing off.
 *
 * The key and the exception below live here rather than beside the interceptor that acts on them
 * because they are the halves an image *component* touches: a placeholder sets the extra when it is
 * tapped, and reads the exception to know it was skipped rather than broken. Enforcing the
 * preference is the app's, and the app can see this module.
 */
val AllowMeteredImage = Extras.Key(default = false)

/** Marks this request as one the user asked for by hand — see [AllowMeteredImage]. */
fun ImageRequest.Builder.allowMeteredImage(allow: Boolean): ImageRequest.Builder = apply { extras.set(AllowMeteredImage, allow) }

/**
 * The app chose not to use the network for this image — 仅 Wi-Fi 加载图片 is on and this is not Wi-Fi.
 *
 * Not a failure of the request. The UI tells the two apart so it can name the switch instead of
 * blaming the network, and so it can offer 仍要加载 — which does work — instead of a 重试 that cannot.
 *
 * `okio.IOException` rather than `java.io.IOException`: this type is named from `commonMain`, which
 * has no `java.*` — and on the JVM the two are the same class, because okio's is a typealias to it.
 * Nothing about what an Android consumer catches changes.
 */
class ImagesDeferredException : IOException("Image skipped: images are limited to Wi-Fi")
