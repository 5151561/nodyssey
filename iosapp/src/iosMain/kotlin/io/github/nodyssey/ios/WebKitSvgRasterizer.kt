@file:OptIn(ExperimentalForeignApi::class)

package io.github.nodyssey.ios

import io.github.plaza.core.image.SvgSize
import io.github.plaza.core.toByteArray
import io.github.plaza.core.toNSData
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawPDFPage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGDataProviderCreateWithData
import platform.CoreGraphics.CGDataProviderRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGPDFDocumentCreateWithProvider
import platform.CoreGraphics.CGPDFDocumentGetPage
import platform.CoreGraphics.CGPDFDocumentRelease
import platform.CoreGraphics.CGPDFPageGetBoxRect
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGPDFMediaBox
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.base64EncodedStringWithOptions
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIScrollViewContentInsetAdjustmentBehavior
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKPDFConfiguration
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Draws an SVG the way the site's own readers see it, by asking WebKit.
 *
 * The alternative on this platform is Skia, and [io.github.plaza.core.image.SvgMarkup] has the whole
 * argument for why it is not enough: it draws no `<text>` and no `<image>`, and there is no binding
 * that could teach it either. WebKit has a full SVG renderer, the device's own font fallback — which
 * is the half that matters for 中文 in a document asking for `Segoe UI` — and it is already in the
 * app, since the sign-in screen is a `WKWebView`.
 *
 * **The document is loaded as an image, not as a page.** `<img src="data:image/svg+xml;…">` puts the
 * SVG in what the spec calls secure static mode: no script runs, no external reference is fetched,
 * no cookie is reachable. That matters because these documents are written by other people — a
 * readme, an attachment, a badge in a post — and the safe way to render a stranger's markup is the
 * mode a browser already refuses to run anything in. It is also why there is a wrapper page at all
 * rather than the markup being handed to `loadData`, which would load it as a document.
 *
 * **The capture is a PDF, not a screenshot.** `takeSnapshot` was the first thing tried and it is the
 * wrong tool: it composites *the screen*, so the web view has to be in the window and on top of it,
 * and what comes back carries whatever the window did to it — the safe-area inset the scroll view
 * applied, the app's own pixels where they overlapped, an image at 1% alpha when the view was faded
 * out to hide it. `createPDF` renders the page itself, offscreen, with the view in no window at all;
 * the page comes back as vector, and rasterising it here is the one place the output size is decided.
 *
 * **One at a time.** The [gate] holds this to a single render: a feed scrolling past twenty badges
 * must not have twenty web views alive at once, and each of these is a process.
 */
internal class WebKitSvgRasterizer {
    private val gate = Mutex()

    /**
     * Renders [markup] into PNG bytes [widthPx] × [heightPx], or null when it could not be done — a
     * load that never finished, a page WebKit would not print, a PDF that would not rasterise.
     *
     * Null is not an error path so much as the *other* renderer's cue: the caller falls back to Skia,
     * which draws the shapes. A document with less in it than it should have beats no document.
     */
    suspend fun rasterize(
        markup: ByteArray,
        intrinsic: SvgSize,
        widthPx: Int,
        heightPx: Int,
    ): ByteArray? =
        gate.withLock {
            // WebKit's own API contract: a `WKWebView` is a main-thread object from `init` onwards.
            withContext(Dispatchers.Main) {
                withTimeoutOrNull(RENDER_TIMEOUT_MILLIS) { render(markup, intrinsic, widthPx, heightPx) }
            }
        }

    private suspend fun render(
        markup: ByteArray,
        intrinsic: SvgSize,
        widthPx: Int,
        heightPx: Int,
    ): ByteArray? {
        // The frame is the document's own size, in points, so that one CSS pixel is one point and the
        // page needs no scaling of its own. Nothing is drawn at this size — the PDF is vector, and the
        // pixels are decided when it is rasterised — so a big document costs no memory here.
        val frame = CGRectMake(0.0, 0.0, intrinsic.width.toDouble(), intrinsic.height.toDouble())
        val webView = offscreenWebView(frame)
        // Held for the length of the render: `navigationDelegate` is a weak reference, and a delegate
        // that only the web view knows about is deallocated before it is ever called back.
        val delegate = LoadDelegate()
        webView.navigationDelegate = delegate
        try {
            val page = wrapperPage(markup, intrinsic)
            if (!delegate.awaitLoad { webView.loadHTMLString(page, baseURL = null) }) return null
            val pdf = printToPdf(webView, frame) ?: return null
            return pdf.toByteArray().toPng(widthPx, heightPx)
        } finally {
            webView.stopLoading()
            webView.navigationDelegate = null
        }
    }

    private fun offscreenWebView(frame: CValue<CGRect>): WKWebView {
        val configuration =
            WKWebViewConfiguration().apply {
                // Nothing about this render should outlive it, and nothing about the reader's session
                // belongs in it: a document in secure static mode can reach neither, and a data store
                // of its own makes that true of the web view as well.
                websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
                defaultWebpagePreferences.allowsContentJavaScript = false
            }
        return WKWebView(frame = frame, configuration = configuration).apply {
            // An SVG is drawn over whatever is behind it — a card on a dark theme included — so the
            // page, the view and its scroll view all have to stop painting white.
            setOpaque(false)
            backgroundColor = UIColor.clearColor
            scrollView.backgroundColor = UIColor.clearColor
            scrollView.scrollEnabled = false
            scrollView.contentInsetAdjustmentBehavior =
                UIScrollViewContentInsetAdjustmentBehavior.UIScrollViewContentInsetAdjustmentNever
            setUserInteractionEnabled(false)
        }
    }

    /** The loaded page as a one-page PDF of exactly [frame], or null when WebKit refused. */
    private suspend fun printToPdf(webView: WKWebView, frame: CValue<CGRect>): NSData? =
        suspendCancellableCoroutine { continuation ->
            val configuration = WKPDFConfiguration().apply { rect = frame }
            webView.createPDFWithConfiguration(configuration) { data, _ ->
                continuation.resume(data)
            }
        }

    /**
     * The first page of a PDF, drawn into a bitmap of exactly [widthPx] × [heightPx] and encoded.
     *
     * The context starts transparent and nothing fills it, so a document with holes in it keeps them.
     * No flip: a bitmap context and a PDF page both put their origin in the bottom-left corner, and
     * the flip this had at first drew the card upside down.
     */
    private fun ByteArray.toPng(widthPx: Int, heightPx: Int): ByteArray? =
        usePinned { pinned ->
            val provider = CGDataProviderCreateWithData(null, pinned.addressOf(0), size.toULong(), null)
            val document = CGPDFDocumentCreateWithProvider(provider)
            CGDataProviderRelease(provider)
            if (document == null) return@usePinned null
            try {
                val page = CGPDFDocumentGetPage(document, 1u) ?: return@usePinned null
                val box = CGPDFPageGetBoxRect(page, kCGPDFMediaBox)
                val boxWidth = box.useContents { size.width }
                val boxHeight = box.useContents { size.height }
                if (boxWidth <= 0.0 || boxHeight <= 0.0) return@usePinned null

                val colorSpace = CGColorSpaceCreateDeviceRGB()
                val context =
                    CGBitmapContextCreate(
                        data = null,
                        width = widthPx.toULong(),
                        height = heightPx.toULong(),
                        bitsPerComponent = 8u,
                        bytesPerRow = 0u,
                        space = colorSpace,
                        bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
                    )
                CGColorSpaceRelease(colorSpace)
                if (context == null) return@usePinned null
                try {
                    CGContextScaleCTM(context, widthPx / boxWidth, heightPx / boxHeight)
                    CGContextDrawPDFPage(context, page)
                    val image = CGBitmapContextCreateImage(context) ?: return@usePinned null
                    try {
                        UIImagePNGRepresentation(UIImage.imageWithCGImage(image))?.toByteArray()
                    } finally {
                        CGImageRelease(image)
                    }
                } finally {
                    CGContextRelease(context)
                }
            } finally {
                CGPDFDocumentRelease(document)
            }
        }

    /**
     * The page the document is drawn in: the SVG at its own size, on a viewport of that same width.
     *
     * The viewport line is what keeps the two sizes in step. A `WKWebView` lays a page out at 980 CSS
     * pixels wide unless it is told otherwise, and then scales that to the frame; declaring the
     * viewport as the document's own width makes one CSS pixel one point instead, so the image fills
     * the frame the caller built from the same numbers.
     */
    private fun wrapperPage(markup: ByteArray, intrinsic: SvgSize): String {
        val encoded = markup.toNSData().base64EncodedStringWithOptions(0u)
        val cssWidth = intrinsic.width.roundToInt().coerceAtLeast(1)
        return """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=$cssWidth">
            <style>html,body{margin:0;padding:0;background:transparent}
            img{width:100%;height:auto;display:block}</style></head>
            <body><img src="data:image/svg+xml;base64,$encoded"></body></html>
        """.trimIndent()
    }

    /**
     * `WKNavigationDelegate` as a Kotlin object, holding the one continuation the load is awaited on.
     *
     * Every callback resumes it, because every one of them ends the wait: a document that failed to
     * load is one this renderer has nothing to draw, and the caller's fallback is the same either way.
     */
    private class LoadDelegate :
        NSObject(),
        WKNavigationDelegateProtocol {
        private var pending: ((Boolean) -> Unit)? = null

        suspend fun awaitLoad(start: () -> Unit): Boolean =
            suspendCancellableCoroutine { continuation ->
                pending = { finished ->
                    pending = null
                    continuation.resume(finished)
                }
                start()
            }

        override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
            pending?.invoke(true)
        }

        // The two failures share a signature once Objective-C selectors are erased, which is what the
        // annotation is for; they mean the same thing here anyway.
        @ObjCSignatureOverride
        override fun webView(
            webView: WKWebView,
            didFailNavigation: WKNavigation?,
            withError: NSError,
        ) {
            pending?.invoke(false)
        }

        @ObjCSignatureOverride
        override fun webView(
            webView: WKWebView,
            didFailProvisionalNavigation: WKNavigation?,
            withError: NSError,
        ) {
            pending?.invoke(false)
        }
    }

    private companion object {
        /**
         * Long enough for a document with a megabyte of embedded bitmaps in it, short enough that a
         * renderer that has wedged does not hold the gate against every image behind it.
         */
        const val RENDER_TIMEOUT_MILLIS = 8_000L
    }
}
