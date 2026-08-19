package io.github.plaza.designsys.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The Material Symbols the design uses that `material-icons-core` does not ship.
 *
 * Declared here rather than pulling in `material-icons-extended`, which would add roughly two
 * thousand unused vectors — and, more to the point, a dependency-graph change that this project's
 * strict lockfile makes a separate, deliberate operation.
 *
 * The bar for adding one: the icon has to carry meaning a core icon cannot. 账号设置's `tune` became
 * `Icons.Default.Settings` and `logout` became `ExitToApp` for exactly that reason.
 */
object PlazaIcons {
    /** Decorative welcome glyph used by the signed-out profile illustration (board c7). */
    val WavingHand: ImageVector by lazy {
        materialIcon(
            name = "WavingHand",
            pathData =
            "M23,17c0,3.31 -2.69,6 -6,6v-1.5c2.48,0 4.5,-2.02 4.5,-4.5H23z" +
                "M1,7c0,-3.31 2.69,-6 6,-6v1.5C4.52,2.5 2.5,4.52 2.5,7H1z" +
                "M8.01,4.32l-4.6,4.6c-3.22,3.22 -3.22,8.45 0,11.67s8.45,3.22 11.67,0l7.07,-7.07" +
                "c0.49,-0.49 0.49,-1.28 0,-1.77s-1.28,-0.49 -1.77,0l-4.42,4.42 -0.71,-0.71" +
                "l6.54,-6.54c0.49,-0.49 0.49,-1.28 0,-1.77s-1.28,-0.49 -1.77,0l-5.83,5.83 -0.71,-0.71" +
                "l6.89,-6.89c0.49,-0.49 0.49,-1.28 0,-1.77s-1.28,-0.49 -1.77,0l-6.89,6.89 -0.71,-0.71" +
                "l5.48,-5.48c0.49,-0.49 0.49,-1.28 0,-1.77s-1.28,-0.49 -1.77,0l-7.62,7.62" +
                "c1.22,1.57 1.11,3.84 -0.33,5.28l-0.71,-0.71c1.17,-1.17 1.17,-3.07 0,-4.24" +
                "l-0.35,-0.35 4.07,-4.07c0.49,-0.49 0.49,-1.28 0,-1.77s-1.29,-0.48 -1.78,0.01z",
        )
    }

    /** Entering the authenticated session, kept distinct from the existing sign-out glyph. */
    val Login: ImageVector by lazy {
        materialIcon(
            name = "Login",
            pathData =
            "M11,7L9.6,8.4 12.2,11H2v2h10.2l-2.6,2.6L11,17l5,-5 -5,-5z" +
                "M20,19h-8v2h8c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2h-8v2h8v14z",
        )
    }

    /** Calendar check used for the daily check-in benefit in the signed-out profile. */
    val EventAvailable: ImageVector by lazy {
        materialIcon(
            name = "EventAvailable",
            pathData =
            "M16.53,11.06L15.47,10l-4.88,4.88 -2.12,-2.12 -1.06,1.06L10.59,17l5.94,-5.94z" +
                "M19,3h-1V1h-2v2H8V1H6v2H5C3.89,3 3.01,3.9 3.01,5L3,19c0,1.1 0.89,2 2,2h14" +
                "c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM19,19H5V8h14v11z",
        )
    }

    /**
     * The un-collected half of the detail screen's star. `Icons.Default.Star` is the filled one.
     *
     * Outline versus fill is the only thing separating "collect this" from "already collected" at a
     * glance, and core ships the filled star alone — the other three marks on that row get away with
     * a single glyph because they are one-way and only ever gain colour.
     */
    val StarBorder: ImageVector by lazy {
        materialIcon(
            name = "StarBorder",
            pathData =
            "M22,9.24l-7.19,-0.62L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21 12,17.27 18.18,21" +
                "l-1.63,-7.03L22,9.24zM12,15.4l-3.76,2.27 1,-4.28 -3.32,-2.88 4.38,-0.38L12,6.1" +
                "l1.71,4.04 4.38,0.38 -3.32,2.88 1,4.28L12,15.4z",
        )
    }

    /**
     * Material Symbols `poll` — the three bars that mark a vote.
     *
     * Nothing in core says "vote": `ThumbUp` is already the 点赞 mark on every floor, and a bar chart
     * is the glyph the site itself and every other forum use for this.
     */
    val Poll: ImageVector by lazy {
        materialIcon(
            name = "Poll",
            pathData =
            "M19,3H5C3.9,3 3,3.9 3,5v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5C21,3.9 20.1,3 19,3z" +
                "M19,19H5V5h14V19z" +
                "M7,10h2v7H7V10z" +
                "M11,7h2v10h-2V7z" +
                "M15,13h2v4h-2V13z",
        )
    }

    val History: ImageVector by lazy {
        materialIcon(
            name = "History",
            pathData =
            "M13,3a9,9 0,0 0,-9,9H1l4,4 4,-4H6a7,7 0,1 1,2.05,4.95l-1.42,1.42A9,9 0,1 0,13,3z" +
                "m-1,5v5l4.25,2.52.75,-1.23 -3.5,-2.04V8z",
        )
    }

    val PersonSearch: ImageVector by lazy {
        materialIcon(
            name = "PersonSearch",
            pathData =
            "M10,8a4,4 0,1 1,-8,0 4,4 0,0 1,8,0zM6,14c-2.67,0 -6,1.34 -6,4v2h9.35" +
                "A7,7 0,0 1,9,18c0,-1.38 .4,-2.66 1.1,-3.74A16,16 0,0 0,6,14z" +
                "M16,12a3,3 0,1 0,1.66,5.49L20.17,20 21,19.17l-2.51,-2.51A3,3 0,0 0,16,12z" +
                "M16,14a1,1 0,1 1,0,2 1,1 0,0 1,0,-2z",
        )
    }

    /** Reply action used by the detail screen's sole primary FAB. */
    val Reply: ImageVector by lazy {
        materialIcon(
            name = "Reply",
            pathData =
            "M10,9V5L3,12l7,7v-4.1c5,0 8.5,1.6 11,5.1-1,-5-4,-11-11,-11z",
        )
    }

    /** 第一页, on the jump sheet: a left chevron against the wall it cannot pass. */
    val FirstPage: ImageVector by lazy {
        materialIcon(
            name = "FirstPage",
            pathData = "M18.41,16.59L13.82,12l4.59,-4.59L17,6l-6,6 6,6zM6,6h2v12H6z",
        )
    }

    /** 最后一页, the mirror of [FirstPage]. */
    val LastPage: ImageVector by lazy {
        materialIcon(
            name = "LastPage",
            pathData = "M5.59,7.41L10.18,12l-4.59,4.59L7,18l6,-6 -6,-6zM16,6h2v12h-2z",
        )
    }

    /** 上次阅读 — the place a previous visit left behind. */
    val Bookmark: ImageVector by lazy {
        materialIcon(
            name = "Bookmark",
            pathData = "M17,3H7c-1.1,0 -2,0.9 -2,2v16l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2z",
        )
    }

    /** 到最新 — the newest floor, which is the foot of the last page. */
    val VerticalAlignBottom: ImageVector by lazy {
        materialIcon(
            name = "VerticalAlignBottom",
            pathData = "M16,13h-3V3h-2v10H8l4,4 4,-4zM4,19v2h16v-2H4z",
        )
    }

    /** 楼层 / 页码 — the chip that turns the jump sheet's scroller into a number to type. */
    val Dialpad: ImageVector by lazy {
        materialIcon(
            name = "Dialpad",
            pathData =
            "M3,3a2,2 0 1,0 4,0a2,2 0 1,0 -4,0zM10,3a2,2 0 1,0 4,0a2,2 0 1,0 -4,0zM17,3a2,2 0 1,0 4,0a2,2 0 1,0 -4,0zM3,9a2,2 0 1,0 4,0a2,2 0 1,0 -4,0zM10,9a2,2 0 1,0 4,0a2,2 0 1,0 -4,0zM17,9a2,2 0 1,0 4,0a2,2 0 1,0 -4,0zM3,15a2,2 0 1,0 4,0a2,2 0 1,0 -4,0zM10,15a2,2 0 1,0 4,0a2,2 0 1,0 -4,0zM17,15a2,2 0 1,0 4,0a2,2 0 1,0 -4,0zM10,21a2,2 0 1,0 4,0a2,2 0 1,0 -4,0z",
        )
    }

    /** 最新, on a list whose newest is at its head — the feed, where page 1 is today. */
    val VerticalAlignTop: ImageVector by lazy {
        materialIcon(
            name = "VerticalAlignTop",
            pathData = "M8,11h3v10h2V11h3l-4,-4 -4,4zM4,3v2h16V3H4z",
        )
    }

    /** Sort order — the only action in the home app bar. */
    val SwapVert: ImageVector by lazy {
        materialIcon(
            name = "SwapVert",
            pathData = "M16,17.01L16,10h-2v7.01h-3L15,21l4,-3.99h-3zM9,3L5,6.99h3L8,14h2L10,6.99h3L9,3z",
        )
    }

    /** Marks a pinned thread in the list. */
    val PushPin: ImageVector by lazy {
        materialIcon(
            name = "PushPin",
            pathData =
            "M16,9V4l1,0c0.55,0 1,-0.45 1,-1s-0.45,-1 -1,-1H7C6.45,2 6,2.45 6,3s0.45,1 1,1l1,0v5" +
                "c0,1.66 -1.34,3 -3,3v2h5.97v7l1,1 1,-1v-7H19v-2c-1.66,0 -3,-1.34 -3,-3z",
        )
    }

    /** View count on the detail screen. */
    val Visibility: ImageVector by lazy {
        materialIcon(
            name = "Visibility",
            pathData =
            "M12,4.5C7,4.5 2.73,7.61 1,12c1.73,4.39 6,7.5 11,7.5s9.27,-3.11 11,-7.5" +
                "c-1.73,-4.39 -6,-7.5 -11,-7.5zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5 5,2.24 5,5" +
                " -2.24,5 -5,5zM12,9c-1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3 -1.34,-3 -3,-3z",
        )
    }

    /** Copies a code block, which is most of what people come to these forums for. */
    val ContentCopy: ImageVector by lazy {
        materialIcon(
            name = "ContentCopy",
            pathData =
            "M16,1H4C2.9,1 2,1.9 2,3v14h2V3h12V1zM19,5H8C6.9,5 6,5.9 6,7v14c0,1.1 0.9,2 2,2h11" +
                "c1.1,0 2,-0.9 2,-2V7c0,-1.1 -0.9,-2 -2,-2zM19,21H8V7h11v14z",
        )
    }

    /** Curated reading list. */
    val MenuBook: ImageVector by lazy {
        materialIcon(
            name = "MenuBook",
            pathData =
            "M21,5c-1.1,-0.35 -2.3,-0.5 -3.5,-0.5 -1.95,0 -4.05,0.4 -5.5,1.5" +
                "c-1.45,-1.1 -3.55,-1.5 -5.5,-1.5S2.45,4.9 1,6v14.65c0,0.25 0.25,0.5 0.5,0.5" +
                "c0.1,0 0.15,-0.05 0.25,-0.05C3.1,20.45 5.05,20 6.5,20c1.95,0 4.05,0.4 5.5,1.5" +
                "c1.35,-0.85 3.8,-1.5 5.5,-1.5 1.65,0 3.35,0.3 4.75,1.05 0.1,0.05 0.15,0.05 0.25,0.05" +
                "c0.25,0 0.5,-0.25 0.5,-0.5V6c-0.6,-0.45 -1.25,-0.75 -2,-1z" +
                "M21,18.5c-1.1,-0.35 -2.3,-0.5 -3.5,-0.5 -1.7,0 -4.15,0.65 -5.5,1.5V8" +
                "c1.35,-0.85 3.8,-1.5 5.5,-1.5 1.2,0 2.4,0.15 3.5,0.5v11.5z",
        )
    }

    /** Lucky draw — the site's T-floor notary tool. */
    val Casino: ImageVector by lazy {
        materialIcon(
            name = "Casino",
            pathData =
            "M19,3H5C3.9,3 3,3.9 3,5v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2z" +
                "M7.5,18C6.67,18 6,17.33 6,16.5S6.67,15 7.5,15 9,15.67 9,16.5 8.33,18 7.5,18z" +
                "M7.5,9C6.67,9 6,8.33 6,7.5S6.67,6 7.5,6 9,6.67 9,7.5 8.33,9 7.5,9z" +
                "M12,13.5c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z" +
                "M16.5,18c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z" +
                "M16.5,9c-0.83,0 -1.5,-0.67 -1.5,-1.5S15.67,6 16.5,6 18,6.67 18,7.5 17.33,9 16.5,9z",
        )
    }

    /** Invite code. */
    val ConfirmationNumber: ImageVector by lazy {
        materialIcon(
            name = "ConfirmationNumber",
            pathData =
            "M22,10V6c0,-1.1 -0.9,-2 -2,-2H4C2.9,4 2.01,4.9 2.01,6v4C3.11,10 4,10.9 4,12s-0.89,2 -2,2v4" +
                "c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2v-4c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2z" +
                "M13,17.5h-2v-2h2v2zM13,13h-2v-2h2v2zM13,8.5h-2v-2h2v2z",
        )
    }

    /** Moderation log. */
    val Gavel: ImageVector by lazy {
        materialIcon(
            name = "Gavel",
            pathData =
            "M2,20h12v2H2zM14.5,2.5l-3,3 6,6 3,-3zM10.4,6.6l-6.5,6.5 3,3 6.5,-6.5z",
        )
    }

    /** Balances and the transfer sheet. */
    val Wallet: ImageVector by lazy {
        materialIcon(
            name = "Wallet",
            pathData =
            "M21,18v1c0,1.1 -0.9,2 -2,2H5c-1.11,0 -2,-0.9 -2,-2V5c0,-1.1 0.89,-2 2,-2h14" +
                "c1.1,0 2,0.9 2,2v1h-9c-1.11,0 -2,0.9 -2,2v8c0,1.1 0.89,2 2,2h9z" +
                "M12,16h10V8H12v8zM16,13.5c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5" +
                " -0.67,1.5 -1.5,1.5z",
        )
    }

    /** Two-factor authentication. */
    val Shield: ImageVector by lazy {
        materialIcon(
            name = "Shield",
            pathData =
            "M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12V5l-9,-4z" +
                "M12,11.99h7c-0.53,4.12 -3.28,7.79 -7,8.94V12H5V6.3l7,-3.11v8.8z",
        )
    }

    /** Blocked users. */
    val Block: ImageVector by lazy {
        materialIcon(
            name = "Block",
            pathData =
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z" +
                "M4,12c0,-4.42 3.58,-8 8,-8 1.85,0 3.55,0.63 4.9,1.69L5.69,16.9C4.63,15.55 4,13.85 4,12z" +
                "M12,20c-1.85,0 -3.55,-0.63 -4.9,-1.69L18.31,7.1C19.37,8.45 20,10.15 20,12c0,4.42 -3.58,8 -8,8z",
        )
    }

    /** Saves a viewed image to the gallery. */
    val Download: ImageVector by lazy {
        materialIcon(
            name = "Download",
            pathData = "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z",
        )
    }

    /** Leaves the app for the site. */
    val OpenInNew: ImageVector by lazy {
        materialIcon(
            name = "OpenInNew",
            pathData =
            "M19,19H5V5h7V3H5c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2v-7h-2v7z" +
                "M14,3v2h3.59l-9.83,9.83 1.41,1.41L19,6.41V10h2V3h-7z",
        )
    }

    /** Follows and followers. */
    val Group: ImageVector by lazy {
        materialIcon(
            name = "Group",
            pathData =
            "M16,11c1.66,0 2.99,-1.34 2.99,-3S17.66,5 16,5c-1.66,0 -3,1.34 -3,3s1.34,3 3,3z" +
                "M8,11c1.66,0 2.99,-1.34 2.99,-3S9.66,5 8,5C6.34,5 5,6.34 5,8s1.34,3 3,3z" +
                "M8,13c-2.33,0 -7,1.17 -7,3.5V19h14v-2.5c0,-2.33 -4.67,-3.5 -7,-3.5z" +
                "M16,13c-0.29,0 -0.62,0.02 -0.97,0.05 1.16,0.84 1.97,1.97 1.97,3.45V19h6v-2.5" +
                "c0,-2.33 -4.67,-3.5 -7,-3.5z",
        )
    }

    /** Starts a conversation from the message list's FAB (board 7e). */
    val AddComment: ImageVector by lazy {
        materialIcon(
            name = "AddComment",
            pathData =
            "M22,4c0,-1.1 -0.9,-2 -2,-2H4c-1.1,0 -2,0.9 -2,2v18l4,-4h14c1.1,0 2,-0.9 2,-2V4z" +
                "M17,11h-4v4h-2v-4H7V9h4V5h2v4h4v2z",
        )
    }

    /** A message that failed to send (board 7f). */
    val ErrorCircle: ImageVector by lazy {
        materialIcon(
            name = "ErrorCircle",
            pathData =
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2z" +
                "m1,15h-2v-2h2v2zm0,-4h-2L11,7h2v6z",
        )
    }

    val ThumbDown: ImageVector by lazy {
        materialIcon(
            name = "ThumbDown",
            pathData =
            "M15,3H6c-0.8,0 -1.5,0.5 -1.8,1.2L1.1,11.3C0.6,12.6 1.5,14 3,14h5.7" +
                "l-1,4.6C7.5,19.4 8.1,20 8.8,20h0.4c0.4,0 0.8,-0.2 1.1,-0.5L16,13.8V5" +
                "c0,-1.1 -0.9,-2 -2,-2zM18,3v11h4V3z",
        )
    }

    /** Bio — the one-line self-introduction, on the 账号设置 list. */
    val Badge: ImageVector by lazy {
        materialIcon(
            name = "Badge",
            pathData =
            "M20,7h-5V4c0,-1.1 -0.9,-2 -2,-2h-2c-1.1,0 -2,0.9 -2,2v3H4C2.9,7 2,7.9 2,9v11" +
                "c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V9C22,7.9 21.1,7 20,7zM11,4h2v3h-2V4z" +
                "M12,12.5c0.83,0 1.5,0.67 1.5,1.5s-0.67,1.5 -1.5,1.5s-1.5,-0.67 -1.5,-1.5" +
                "S11.17,12.5 12,12.5zM15,18H9v-0.75c0,-1 1.34,-1.81 3,-1.81s3,0.81 3,1.81V18z",
        )
    }

    /** Readme — the long Markdown block shown on a user's page. */
    val Article: ImageVector by lazy {
        materialIcon(
            name = "Article",
            pathData =
            "M19,3H5C3.9,3 3,3.9 3,5v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5C21,3.9 20.1,3 19,3z" +
                "M14,17H7v-2h7V17zM17,13H7v-2h10V13zM17,9H7V7h10V9z",
        )
    }

    /** Which boards the home strip shows. */
    val DashboardCustomize: ImageVector by lazy {
        materialIcon(
            name = "DashboardCustomize",
            pathData =
            "M3,11h8V3H3V11zM5,5h4v4H5V5zM13,3v8h8V3H13zM19,9h-4V5h4V9zM3,21h8v-8H3V21z" +
                "M5,15h4v4H5V15zM18,13h-2v3h-3v2h3v3h2v-3h3v-2h-3V13z",
        )
    }

    /** The struck-through eye on a password field. Pairs with [Visibility]. */
    val VisibilityOff: ImageVector by lazy {
        materialIcon(
            name = "VisibilityOff",
            pathData =
            "M12,7c2.76,0 5,2.24 5,5c0,0.65 -0.13,1.26 -0.36,1.83l2.92,2.92" +
                "c1.51,-1.26 2.7,-2.89 3.43,-4.75c-1.73,-4.39 -6,-7.5 -11,-7.5c-1.4,0 -2.74,0.25 -3.98,0.7" +
                "l2.16,2.16C10.74,7.13 11.35,7 12,7zM2,4.27l2.28,2.28l0.46,0.46C3.08,8.3 1.78,10.02 1,12" +
                "c1.73,4.39 6,7.5 11,7.5c1.55,0 3.03,-0.3 4.38,-0.84l0.42,0.42L19.73,22L21,20.73L3.27,3L2,4.27z" +
                "M7.53,9.8l1.55,1.55c-0.05,0.21 -0.08,0.43 -0.08,0.65c0,1.66 1.34,3 3,3c0.22,0 0.44,-0.03 0.65,-0.08" +
                "l1.55,1.55c-0.67,0.33 -1.41,0.53 -2.2,0.53c-2.76,0 -5,-2.24 -5,-5C7,11.21 7.2,10.47 7.53,9.8z" +
                "M11.84,9.02l3.15,3.15l0.02,-0.16c0,-1.66 -1.34,-3 -3,-3L11.84,9.02z",
        )
    }

    /** Take a new avatar photo. */
    val PhotoCamera: ImageVector by lazy {
        materialIcon(
            name = "PhotoCamera",
            pathData =
            "M12,12m-3.2,0a3.2,3.2 0,1 1,6.4 0a3.2,3.2 0,1 1,-6.4 0" +
                "M9,2L7.17,4H4C2.9,4 2,4.9 2,6v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6" +
                "c0,-1.1 -0.9,-2 -2,-2h-3.17L15,2H9zM12,17c-2.76,0 -5,-2.24 -5,-5s2.24,-5 5,-5" +
                "s5,2.24 5,5S14.76,17 12,17z",
        )
    }

    // --- Editor toolbar (boards 6d / 7a / c5 / c6) ---------------------------

    val FormatBold: ImageVector by lazy {
        materialIcon(
            name = "FormatBold",
            pathData =
            "M15.6,10.79c0.97,-0.67 1.65,-1.77 1.65,-2.79c0,-2.26 -1.75,-4 -4,-4H7v14h7.04" +
                "c2.09,0 3.71,-1.7 3.71,-3.79c0,-1.52 -0.86,-2.82 -2.15,-3.42z" +
                "M10,6.5h3c0.83,0 1.5,0.67 1.5,1.5s-0.67,1.5 -1.5,1.5h-3V6.5z" +
                "M13.5,15.5H10v-3h3.5c0.83,0 1.5,0.67 1.5,1.5s-0.67,1.5 -1.5,1.5z",
        )
    }

    val FormatItalic: ImageVector by lazy {
        materialIcon(name = "FormatItalic", pathData = "M10,4v3h2.21l-3.42,8H6v3h8v-3h-2.21l3.42,-8H18V4z")
    }

    val StrikethroughS: ImageVector by lazy {
        materialIcon(
            name = "StrikethroughS",
            pathData = "M10,19h4v-3h-4v3zM5,4v3h5v3h4V7h5V4H5zM3,14h18v-2H3v2z",
        )
    }

    /** Heading. Material's `title` glyph rather than `format_h2`, which is text rendered as a path. */
    val Title: ImageVector by lazy {
        materialIcon(name = "Title", pathData = "M5,4v3h5.5v12h3V7H19V4z")
    }

    val Code: ImageVector by lazy {
        materialIcon(
            name = "Code",
            pathData =
            "M9.4,16.6L4.8,12l4.6,-4.6L8,6l-6,6 6,6 1.4,-1.4z" +
                "M14.6,16.6l4.6,-4.6 -4.6,-4.6L16,6l6,6 -6,6 -1.4,-1.4z",
        )
    }

    val FormatQuote: ImageVector by lazy {
        materialIcon(
            name = "FormatQuote",
            pathData = "M6,17h3l2,-4V7H5v6h3zM14,17h3l2,-4V7h-6v6h3z",
        )
    }

    val FormatListBulleted: ImageVector by lazy {
        materialIcon(
            name = "FormatListBulleted",
            pathData =
            "M4,10.5c-0.83,0 -1.5,0.67 -1.5,1.5s0.67,1.5 1.5,1.5 1.5,-0.67 1.5,-1.5 -0.67,-1.5 -1.5,-1.5z" +
                "M4,4.5c-0.83,0 -1.5,0.67 -1.5,1.5S3.17,7.5 4,7.5 5.5,6.83 5.5,6 4.83,4.5 4,4.5z" +
                "M4,16.5c-0.83,0 -1.5,0.68 -1.5,1.5s0.68,1.5 1.5,1.5 1.5,-0.68 1.5,-1.5 -0.67,-1.5 -1.5,-1.5z" +
                "M7,19h14v-2H7v2zM7,13h14v-2H7v2zM7,5v2h14V5H7z",
        )
    }

    val Link: ImageVector by lazy {
        materialIcon(
            name = "Link",
            pathData =
            "M3.9,12c0,-1.71 1.39,-3.1 3.1,-3.1h4V7H7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5h4v-1.9H7" +
                "c-1.71,0 -3.1,-1.39 -3.1,-3.1zM8,13h8v-2H8v2zM17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1" +
                "s-1.39,3.1 -3.1,3.1h-4V17h4c2.76,0 5,-2.24 5,-5s-2.24,-5 -5,-5z",
        )
    }

    /** Insert an image from the photo library — the editor toolbar, and the avatar picker. */
    val Image: ImageVector by lazy {
        materialIcon(
            name = "Image",
            pathData =
            "M21,19V5c0,-1.1 -0.9,-2 -2,-2H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2z" +
                "M8.5,13.5l2.5,3.01L14.5,12l4.5,6H5l3.5,-4.5z",
        )
    }

    /**
     * A picture that did not arrive — the mark [ImageFallback] leaves where one was expected.
     *
     * Deliberately the torn version of [Image] rather than [Image] itself: the two stand side by
     * side in a post, one saying "skipped, tap to load" and one saying "this one failed", and a
     * reader has to be able to tell them apart at 20sp without reading a word.
     */
    val BrokenImage: ImageVector by lazy {
        materialIcon(
            name = "BrokenImage",
            pathData =
            "M21,5v6.59l-3,-3.01 -4,4.01 -4,-4 -4,4 -3,-3.01V5c0,-1.1 0.9,-2 2,-2h14c1.1,0 2,0.9 2,2z" +
                "M18,11.42l3,3.01V19c0,1.1 -0.9,2 -2,2H5c-1.1,0 -2,-0.9 -2,-2v-6.58l3,2.99 4,-4 4,4 4,-3.99z",
        )
    }

    /**
     * The key that opens the toolbar's own settings, at the end of the strip.
     *
     * A wrench rather than Material's `tune` sliders: `tune` sits in a row of formatting glyphs and
     * reads as one of them — three horizontal bars is very nearly the list key. A wrench cannot be
     * mistaken for something that edits the text.
     */
    val Build: ImageVector by lazy {
        materialIcon(
            name = "Build",
            pathData =
            "M22.7,19l-9.1,-9.1c0.9,-2.3 0.4,-5 -1.5,-6.9 -2,-2 -5,-2.4 -7.4,-1.3L9,6 6,9 1.6,4.7" +
                "C0.4,7.1 0.9,10.1 2.9,12.1c1.9,1.9 4.6,2.4 6.9,1.5l9.1,9.1c0.4,0.4 1,0.4 1.4,0" +
                "l2.3,-2.3c0.5,-0.4 0.5,-1.1 0.1,-1.4z",
        )
    }

    /** The grab handle on a reorderable row. */
    val DragHandle: ImageVector by lazy {
        materialIcon(name = "DragHandle", pathData = "M20,9H4v2h16V9zM4,15h16v-2H4V15z")
    }

    /** The emoji panel's entry point. */
    val Mood: ImageVector by lazy {
        materialIcon(
            name = "Mood",
            pathData =
            "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2z" +
                "M12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8z" +
                "M15.5,11c0.83,0 1.5,-0.67 1.5,-1.5S16.33,8 15.5,8 14,8.67 14,9.5s0.67,1.5 1.5,1.5z" +
                "M8.5,11c0.83,0 1.5,-0.67 1.5,-1.5S9.33,8 8.5,8 7,8.67 7,9.5 7.67,11 8.5,11z" +
                "M12,17.5c2.33,0 4.31,-1.46 5.11,-3.5H6.89c0.8,2.04 2.78,3.5 5.11,3.5z",
        )
    }

    /** The 回复主题 notification channel (board f4). */
    val ChatBubble: ImageVector by lazy {
        materialIcon(
            name = "ChatBubble",
            pathData = "M20,2H4C2.9,2 2,2.9 2,4v18l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2z",
        )
    }

    /** Mentions another member; the reply editor's `@`. */
    val AlternateEmail: ImageVector by lazy {
        materialIcon(
            name = "AlternateEmail",
            pathData =
            "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10h5v-2h-5c-4.34,0 -8,-3.66 -8,-8s3.66,-8 8,-8 8,3.66 8,8" +
                "v1.43c0,0.79 -0.71,1.57 -1.5,1.57s-1.5,-0.78 -1.5,-1.57V12c0,-2.76 -2.24,-5 -5,-5s-5,2.24 -5,5" +
                " 2.24,5 5,5c1.38,0 2.64,-0.56 3.54,-1.47 0.65,0.89 1.77,1.47 2.96,1.47 1.97,0 3.5,-1.6 3.5,-3.57" +
                "V12c0,-5.52 -4.48,-10 -10,-10zM12,15c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3 3,1.34 3,3 -1.34,3 -3,3z",
        )
    }

    /** The emoji panel's delete key. */
    val Backspace: ImageVector by lazy {
        materialIcon(
            name = "Backspace",
            pathData =
            "M22,3H7c-0.69,0 -1.23,0.35 -1.59,0.88L0,12l5.41,8.11c0.36,0.53 0.9,0.89 1.59,0.89h15" +
                "c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM19,15.59L17.59,17 14,13.41 10.41,17 9,15.59 12.59,12" +
                " 9,8.41 10.41,7 14,10.59 17.59,7 19,8.41 15.41,12 19,15.59z",
        )
    }

    /** The forum rules card on the publish preview. */
    val Campaign: ImageVector by lazy {
        materialIcon(
            name = "Campaign",
            pathData =
            "M18,11v2h4v-2h-4zM16,17.61c0.96,0.71 2.21,1.65 3.2,2.39 0.4,-0.53 0.8,-1.07 1.2,-1.6" +
                " -0.99,-0.74 -2.24,-1.68 -3.2,-2.4 -0.4,0.54 -0.8,1.08 -1.2,1.61z" +
                "M20.4,5.6c-0.4,-0.53 -0.8,-1.07 -1.2,-1.6 -0.99,0.74 -2.24,1.68 -3.2,2.4 0.4,0.53 0.8,1.07 1.2,1.6" +
                " 0.96,-0.72 2.21,-1.65 3.2,-2.4zM4,9c-1.1,0 -2,0.9 -2,2v2c0,1.1 0.9,2 2,2h1v4h2v-4h1l5,3V6L8,9H4z" +
                "M15.5,12c0,-1.33 -0.58,-2.53 -1.5,-3.35v6.69c0.92,-0.81 1.5,-2.01 1.5,-3.34z",
        )
    }

    /** Draft recovery. */
    val Drafts: ImageVector by lazy {
        materialIcon(
            name = "Drafts",
            pathData =
            "M21.99,8c0,-0.72 -0.37,-1.35 -0.94,-1.7L12,1 2.95,6.3C2.38,6.65 2,7.28 2,8v10c0,1.1 0.9,2 2,2h16" +
                "c1.1,0 2,-0.9 2,-2l-0.01,-10zM12,13L3.74,7.84 12,3l8.26,4.84L12,13z",
        )
    }

    /** An attachment that has not started uploading yet; also "awaiting verification" on a freshly changed email. */
    val Schedule: ImageVector by lazy {
        materialIcon(
            name = "Schedule",
            pathData =
            "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2z" +
                "M12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8s8,3.58 8,8S16.42,20 12,20z" +
                "M12.5,7H11v6l5.25,3.15l0.75,-1.23l-4.5,-2.67z",
        )
    }

    /** The disabled 添加手机 card — the site's SMS verification is itself switched off. */
    val Sms: ImageVector by lazy {
        materialIcon(
            name = "Sms",
            pathData =
            "M20,2H4C2.9,2 2,2.9 2,4v18l4,-4h14c1.1,0 2,-0.9 2,-2V4C22,2.9 21.1,2 20,2z" +
                "M9,11H7V9h2V11zM13,11h-2V9h2V11zM17,11h-2V9h2V11z",
        )
    }

    /** Unbinding Telegram — the destructive counterpart of the bind link. */
    val LinkOff: ImageVector by lazy {
        materialIcon(
            name = "LinkOff",
            pathData =
            "M17,7h-4v1.9h4c1.71,0 3.1,1.39 3.1,3.1 0,1.43 -0.98,2.63 -2.31,2.98l1.46,1.46" +
                "C20.88,15.61 22,13.95 22,12c0,-2.76 -2.24,-5 -5,-5z" +
                "M2,4.27l3.11,3.11C3.29,8.12 2,9.91 2,12c0,2.76 2.24,5 5,5h4v-1.9H7" +
                "c-1.71,0 -3.1,-1.39 -3.1,-3.1 0,-1.59 1.21,-2.9 2.76,-3.07L8.73,11H8v2h2.73L13,15.27V17h1.73" +
                "l4.01,4L20,19.74 3.27,3 2,4.27zM13.5,8l2,2h.5V8h-2.5z",
        )
    }

    /** Bind an authenticator app by scanning a code. */
    val QrCode: ImageVector by lazy {
        materialIcon(
            name = "QrCode",
            pathData =
            "M3,11h8V3H3V11zM5,5h4v4H5V5zM3,21h8v-8H3V21zM5,15h4v4H5V15zM13,3v8h8V3H13z" +
                "M19,9h-4V5h4V9zM19,19h2v2h-2V19zM13,13h2v2h-2V13zM15,15h2v2h-2V15z" +
                "M13,17h2v2h-2V17zM15,19h2v2h-2V19zM17,17h2v2h-2V17zM17,13h2v2h-2V13zM19,15h2v2h-2V15z",
        )
    }

    /**
     * 回复数 on a list row: the speech bubble, filled, so it still reads as a shape at 13sp.
     *
     * [Reply]'s arrow is the *action*; this is the *count*. Drawing the count with the action's
     * glyph would offer a tap the row does not have.
     */
    val ModeComment: ImageVector by lazy {
        materialIcon(
            name = "ModeComment",
            pathData = "M20,2H4C2.9,2 2,2.9 2,4v18l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2z",
        )
    }

    /** Material's `apps` grid — the editors' menu of things a forum can embed in a post. */
    val Apps: ImageVector by lazy {
        materialIcon(
            name = "Apps",
            pathData =
            "M4,8h4V4H4V8zM10,20h4v-4h-4V20zM4,20h4v-4H4V20zM4,14h4v-4H4V14zM10,14h4v-4h-4V14z" +
                "M16,4v4h4V4H16zM10,8h4V4h-4V8zM16,14h4v-4h-4V14zM16,20h4v-4h-4V20z",
        )
    }

    /** 收藏页状态条：这台设备上已经有离线内容了（板 i1）。 */
    val CloudDone: ImageVector by lazy {
        materialIcon(
            name = "CloudDone",
            pathData =
            "M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14" +
                "c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96z" +
                "M10,17l-3.5,-3.5 1.41,-1.41L10,14.17 15.18,9l1.41,1.41L10,17z",
        )
    }

    /** 「全部下载」胶囊。与 [Download] 分开：那个是把一张图存进相册，这个是把一批帖子存下来。 */
    val CloudDownload: ImageVector by lazy {
        materialIcon(
            name = "CloudDownload",
            pathData =
            "M19.35,10.04C18.67,6.59 15.64,4 12,4 9.11,4 6.6,5.64 5.35,8.04 2.34,8.36 0,10.91 0,14" +
                "c0,3.31 2.69,6 6,6h13c2.76,0 5,-2.24 5,-5 0,-2.64 -2.05,-4.78 -4.65,-4.96z" +
                "M17,13l-5,5 -5,-5h3V9h4v4h3z",
        )
    }

    /** 离线版落后于站点，等着补新回复（板 i1）。 */
    val Sync: ImageVector by lazy {
        materialIcon(
            name = "Sync",
            pathData =
            "M12,4V1L8,5l4,4V6c3.31,0 6,2.69 6,6 0,1.01 -0.25,1.97 -0.7,2.8l1.46,1.46" +
                "C19.54,15.03 20,13.57 20,12c0,-4.42 -3.58,-8 -8,-8z" +
                "M12,18c-3.31,0 -6,-2.69 -6,-6 0,-1.01 0.25,-1.97 0.7,-2.8L5.24,7.74" +
                "C4.46,8.97 4,10.43 4,12c0,4.42 3.58,8 8,8v3l4,-4 -4,-4v3z",
        )
    }

    /** 把选中的帖子移出收藏（板 i1 多选工具栏）。 */
    val BookmarkRemove: ImageVector by lazy {
        materialIcon(
            name = "BookmarkRemove",
            pathData =
            "M17,3H7c-1.1,0 -2,0.9 -2,2v16l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2zM15,9H9V7h6v2z",
        )
    }

    /** 仅 Wi-Fi 下载。 */
    val Wifi: ImageVector by lazy {
        materialIcon(
            name = "Wifi",
            pathData =
            "M1,9l2,2c4.97,-4.97 13.03,-4.97 18,0l2,-2C16.93,2.93 7.08,2.93 1,9z" +
                "M9,17l3,3 3,-3c-1.65,-1.66 -4.34,-1.66 -6,0z" +
                "M5,13l2,2c2.76,-2.76 7.24,-2.76 10,0l2,-2C15.14,9.14 8.87,9.14 5,13z",
        )
    }

    /** 落后的回复数前面那个下箭头——方向是「还要往下拉」，不是排序。 */
    val ArrowDownward: ImageVector by lazy {
        materialIcon(
            name = "ArrowDownward",
            pathData = "M20,12l-1.41,-1.41L13,16.17V4h-2v12.17l-5.58,-5.59L4,12l8,8 8,-8z",
        )
    }

    /**
     * 进度环中心的方块：按一下停掉这一篇的下载。
     *
     * 方块而不是 ✕，因为它压在一个还在转的进度环里——✕ 在那个尺寸下会和环上的缺口混在一起。
     */
    val Stop: ImageVector by lazy {
        materialIcon(
            name = "Stop",
            pathData = "M6,6h12v12H6z",
        )
    }

    /** 预设 — 配色来源 tile. 调色板, the one glyph that says "a set of colours somebody chose". */
    val Palette: ImageVector by lazy {
        materialIcon(
            name = "Palette",
            pathData =
            "M12,2C6.49,2,2,6.49,2,12s4.49,10,10,10c1.38,0,2.5-1.12,2.5-2.5c0-0.61-0.23-1.2-0.64-1.67" +
                "c-0.08-0.1-0.13-0.21-0.13-0.33c0-0.28,0.22-0.5,0.5-0.5H16c3.31,0,6-2.69,6-6C22,6.04,17.51,2,12,2" +
                "z M17.5,13c-0.83,0-1.5-0.67-1.5-1.5c0-0.83,0.67-1.5,1.5-1.5s1.5,0.67,1.5,1.5" +
                "C19,12.33,18.33,13,17.5,13z M14.5,9C13.67,9,13,8.33,13,7.5C13,6.67,13.67,6,14.5,6S16,6.67,16,7.5" +
                "C16,8.33,15.33,9,14.5,9z M5,11.5C5,10.67,5.67,10,6.5,10S8,10.67,8,11.5C8,12.33,7.33,13,6.5,13" +
                "S5,12.33,5,11.5z M11,7.5C11,8.33,10.33,9,9.5,9S8,8.33,8,7.5C8,6.67,8.67,6,9.5,6S11,6.67,11,7.5z",
        )
    }

    /** 动态取色 — the source that reads the phone's own wallpaper. */
    val Wallpaper: ImageVector by lazy {
        materialIcon(
            name = "Wallpaper",
            pathData =
            "M4 4h7V2H4c-1.1 0-2 .9-2 2v7h2V4zm6 9l-4 5h12l-3-4-2.03 2.71L10 13zm7-4.5c0-.83-.67-1.5-1.5-1.5" +
                "S14 7.67 14 8.5s.67 1.5 1.5 1.5S17 9.33 17 8.5zM20 2h-7v2h7v7h2V4c0-1.1-.9-2-2-2zm0 18h-7v2h7" +
                "c1.1 0 2-.9 2-2v-7h-2v7zM4 13H2v7c0 1.1.9 2 2 2h7v-2H4v-7z",
        )
    }

    /** 吸管 — lifting a colour out of a picture the reader already has. */
    val Colorize: ImageVector by lazy {
        materialIcon(
            name = "Colorize",
            pathData =
            "M20.71 5.63l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-3.12 3.12-1.93-1.91-1.41 1.41 1.42 1.42L3 16.25" +
                "V21h4.75l8.92-8.92 1.42 1.42 1.41-1.41-1.92-1.92 3.12-3.12c.4-.4.4-1.03.01-1.42zM6.92 19L5 17.08" +
                "l8.06-8.06 1.92 1.92L6.92 19z",
        )
    }

    /** 保存为我的主题 — the bookmark with a plus, distinct from [Bookmark]'s "already saved". */
    val BookmarkAdd: ImageVector by lazy {
        materialIcon(
            name = "BookmarkAdd",
            pathData =
            "M21,7h-2v2h-2V7h-2V5h2V3h2v2h2V7z M19,21l-7-3l-7,3V5c0-1.1,0.9-2,2-2l7,0c-0.63,0.84-1,1.87-1,3" +
                "c0,2.76,2.24,5,5,5c0.34,0,0.68-0.03,1-0.1V21z",
        )
    }

    /** 使用系统调色板 — the row that hands the decision to the OS. */
    val Android: ImageVector by lazy {
        materialIcon(
            name = "Android",
            pathData =
            "M17.6,9.48l1.84-3.18c0.16-0.31,0.04-0.69-0.26-0.85c-0.29-0.15-0.65-0.06-0.83,0.22l-1.88,3.24" +
                "c-2.86-1.21-6.08-1.21-8.94,0L5.65,5.67c-0.19-0.29-0.58-0.38-0.87-0.2C4.5,5.65,4.41,6.01,4.56,6.3" +
                "L6.4,9.48C3.3,11.25,1.28,14.44,1,18h22C22.72,14.44,20.7,11.25,17.6,9.48z M7,15.25" +
                "c-0.69,0-1.25-0.56-1.25-1.25c0-0.69,0.56-1.25,1.25-1.25S8.25,13.31,8.25,14" +
                "C8.25,14.69,7.69,15.25,7,15.25z M17,15.25c-0.69,0-1.25-0.56-1.25-1.25c0-0.69,0.56-1.25,1.25-1.25" +
                "s1.25,0.56,1.25,1.25C18.25,14.69,17.69,15.25,17,15.25z",
        )
    }

    /** The wordmark's companion in 主题's preview card — a miniature of the app's own top bar. */
    val Forum: ImageVector by lazy {
        materialIcon(
            name = "Forum",
            pathData =
            "M21 6h-2v9H6v2c0 .55.45 1 1 1h11l4 4V7c0-.55-.45-1-1-1zm-4 6V3c0-.55-.45-1-1-1H3" +
                "c-.55 0-1 .45-1 1v14l4-4h10c.55 0 1-.45 1-1z",
        )
    }

    /** The same preview card's trailing control. */
    val Sort: ImageVector by lazy {
        materialIcon(
            name = "Sort",
            pathData =
            "M3 18h6v-2H3v2zM3 6v2h18V6H3zm0 7h12v-2H3v2z",
        )
    }
}

/**
 * A 24dp Material Symbol from its path data.
 *
 * Public because an app declares its own icons the same way: anything naming that site's own
 * concepts — a currency, a badge — belongs in that app rather than here, and it should still come
 * out the same size and shape as these.
 */
fun materialIcon(
    name: String,
    pathData: String,
): ImageVector =
    ImageVector
        .Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(pathData),
            // Black is a placeholder: `Icon` tints the whole vector, so the fill never survives.
            fill = SolidColor(Color.Black),
        ).build()
