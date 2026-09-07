package io.github.nodyssey.ui.profile

/**
 * Everywhere 我的 can send you.
 *
 * A parameter object rather than twenty-odd lambdas on [ProfileScreen], because board n1 turned the
 * screen into a directory: every tile in the grid is one jump to a real destination, and the
 * signature would otherwise be longer than the layout. Each default is a no-op so a preview or a
 * test names only the tile it actually presses.
 */
data class ProfileDestinations(
    /** The gear in the app bar, and the row the update dot rides on. */
    val settings: () -> Unit = {},
    /** The avatar block: 个人主页, which is where the profile itself is read and edited. */
    val space: () -> Unit = {},
    /** The three resource cards, all three of them: 账户与成长 is where a balance is explained. */
    val assets: () -> Unit = {},
    // 我的内容
    val topics: () -> Unit = {},
    val comments: () -> Unit = {},
    val collections: () -> Unit = {},
    val history: () -> Unit = {},
    val following: () -> Unit = {},
    val followers: () -> Unit = {},
    // 资产
    val credit: () -> Unit = {},
    val stardust: () -> Unit = {},
    val transfer: () -> Unit = {},
    val invite: () -> Unit = {},
    // 社区
    val award: () -> Unit = {},
    val lucky: () -> Unit = {},
    val ruling: () -> Unit = {},
    val providers: () -> Unit = {},
    val friends: () -> Unit = {},
    val blockList: () -> Unit = {},
    val aboutCommunity: () -> Unit = {},
    // 设置与其他
    val accountSettings: () -> Unit = {},
    val notificationSettings: () -> Unit = {},
    val themeSettings: () -> Unit = {},
    val about: () -> Unit = {},
    /**
     * 社区工具, the signed-out screen's second guest row.
     *
     * Signed in, the six links it holds are tiles of their own in 社区 and the page is not reached
     * from here at all — the grid exists to remove exactly that hop.
     */
    val tools: () -> Unit = {},
)
