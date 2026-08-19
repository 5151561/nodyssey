package io.github.plaza.designsys.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.plaza.designsys.R
import io.github.plaza.designsys.image.ImageLoadFailure

/**
 * One line saying why an image is missing, or null when there is nothing honest to say.
 *
 * [ImageLoadFailure.Unknown] deliberately maps to null: "加载失败·未知错误" costs a line and tells
 * the reader exactly what the broken-image glyph above it already did.
 */
@Composable
fun imageLoadFailureText(failure: ImageLoadFailure?): String? =
    when (failure) {
        is ImageLoadFailure.Challenge -> stringResource(R.string.richtext_image_failed_challenge, failure.code)
        is ImageLoadFailure.Http -> stringResource(R.string.richtext_image_failed_http, failure.code)
        ImageLoadFailure.Unreachable -> stringResource(R.string.richtext_image_failed_unreachable)
        ImageLoadFailure.Timeout -> stringResource(R.string.richtext_image_failed_timeout)
        ImageLoadFailure.Connection -> stringResource(R.string.richtext_image_failed_connection)
        ImageLoadFailure.Unknown, null -> null
    }
