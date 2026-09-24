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
    /** The gear in the app bar — signed in or out — which also carries the update dot. */
    val settings: () -> Unit = {},
    /** The avatar block: 个人主页, which is where the profile itself is read and edited. */
    val space: () -> Unit = {},
    /** The 等级 card: 账户与成长 is where the level is explained. */
    val assets: () -> Unit = {},
    /** The 鸡腿 card: 鸡腿流水. */
    val credit: () -> Unit = {},
    /** The 星辰 card: 星辰流水, which also carries 转账. */
    val stardust: () -> Unit = {},
    // 我的内容
    val topics: () -> Unit = {},
    val comments: () -> Unit = {},
    val collections: () -> Unit = {},
    val history: () -> Unit = {},
    val following: () -> Unit = {},
    val followers: () -> Unit = {},
    // 社区
    val invite: () -> Unit = {},
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
     * 社区工具, the last of the signed-out screen's guest tiles, after the four tools 9f draws.
     *
     * Signed in, the six links it holds are tiles of their own in 社区 and the page is not reached
     * from here at all — the grid exists to remove exactly that hop.
     */
    val tools: () -> Unit = {},
)
