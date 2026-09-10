package io.github.nodyssey.ui.common

import io.github.nodyssey.core.ActiveSite

/**
 * The forum's own name, for the copy that names it.
 *
 * Not [appName], which answers a different question: that one is what *this app* is called
 * (Nodyssey, or Nodyssey·D on a debug build), while this is which forum it is currently pointed at.
 * The two were the same string for as long as there was one forum, which is how twenty-odd pieces of
 * copy came to say 「登录 NodeSeek」 and 「连不上 NodeSeek」 as literals.
 *
 * Not a `@Composable`, and no `CompositionLocal`: unlike the app's name this cannot be blank and has
 * no build-type override to receive — [ActiveSite] is installed before the first frame and does not
 * change while the process lives. A plain read is the whole of it.
 *
 * The four pieces of copy that still say NodeSeek as a literal are the ones that mean it: App Links
 * are registered for nodeseek.com alone, and NodeImage signs its users in with a NodeSeek account
 * whichever forum this app is showing.
 */
val siteName: String get() = ActiveSite.current.displayName

/** The forum's initials — the tile on the sign-in card. See [io.github.nodyssey.core.Site.mark]. */
val siteMark: String get() = ActiveSite.current.mark
