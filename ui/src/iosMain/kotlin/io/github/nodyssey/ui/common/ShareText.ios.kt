@file:OptIn(ExperimentalForeignApi::class)

package io.github.nodyssey.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIActivityViewController
import platform.UIKit.popoverPresentationController

/**
 * `UIActivityViewController`, which is what 分享 means on iOS.
 *
 * [chooserTitle] is dropped: the share sheet is titled by the system and labelling it is not
 * something the API offers. It stays in the signature because Android's chooser does take one.
 *
 * The presenting controller comes from [topmostViewController], which every modal here shares.
 */
@Composable
actual fun rememberShareText(): (text: String, chooserTitle: String?) -> Unit =
    remember {
        { text, _ ->
            topmostViewController()?.let { host ->
                val sheet =
                    UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
                // The iPad presentation is a popover and needs an anchor; the window itself is the
                // only one available from here, which puts the arrow at its centre.
                sheet.popoverPresentationController?.sourceView = host.view
                host.presentViewController(sheet, animated = true, completion = null)
            }
        }
    }
