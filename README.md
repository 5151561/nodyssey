# Nodyssey

[![Telegram Channel](https://img.shields.io/badge/Telegram-Channel-26A5E4?logo=telegram&logoColor=white)](https://t.me/nodyssey_official)
[![Telegram Group](https://img.shields.io/badge/Telegram-Group-26A5E4?logo=telegram&logoColor=white)](https://t.me/+97ANIwVaCYk1MjQ1)

Nodyssey is an unofficial, open-source Android client for
[NodeSeek](https://www.nodeseek.com/), built with Kotlin and Jetpack Compose.

NodeSeek has an iOS client but no official Android client. Nodyssey renders posts and comments as
**native Compose content**—there is no WebView in the normal reading path—so text is selectable,
scrolling stays in one list, and the UI follows the system theme.

源码、问题反馈与版本发布：[GitHub](https://github.com/5151561/nodyssey) ·
[Issues](https://github.com/5151561/nodyssey/issues) ·
[Releases](https://github.com/5151561/nodyssey/releases)

Telegram：[频道](https://t.me/nodyssey_official) · [群组](https://t.me/+97ANIwVaCYk1MjQ1)

各版本的用户可见变化见 [CHANGELOG.md](CHANGELOG.md)，文档导航见 [docs/README.md](docs/README.md)。

> 中文说明见下方。

## Architecture

NodeSeek has no public API. A few JSON endpoints exist (`/api/statistics/*`, `/api/notification/*`,
`/api/vote/*`, `/api/content/list-comments`, `/api/content/new-comment`, and the `/setting` writes), but list, detail,
search and terms pages also depend on server-rendered HTML. Everything sits behind Cloudflare, so requests have to look like a real mobile browser and
carry cookies obtained from a WebView.

Three Gradle modules:

```text
:app        NodeSeek itself — repositories, Room persistence, Compose screens
:shared     the domain model, the parsers and the network layer, as Kotlin Multiplatform:
            Android, the desktop JVM, iOS and macOS. What a site knows about itself arrives
            as `SiteConfig`, not as a constant in here
:designsys  theme, components and the rich-text renderer, with no knowledge of any forum
```

`:shared` is the one module that does not know it is running on Android. Only the Android half of it
is built in CI; the Apple targets build on a Mac. See [docs/kmp-migration-plan.md](docs/kmp-migration-plan.md).

There used to be a fourth, `:core`, holding the Android network shell — OkHttp and the WebView
cookie bridge. Step A5 of the migration moved it into `:shared`: the contract everything above the
network is written against is `commonMain`, and OkHttp is one of its two implementations. The other
is `NSURLSession`.

`:core` and `:designsys` were extracted when a second client shared this repository. That app now
lives in [5151561/plaza](https://github.com/5151561/plaza) carrying a *copy* of both modules as they
then were, so edits here do not reach it; the boundary stays because it is what makes the
site-specific half of this app visible as a thing with edges.

```text
shared/src/commonMain/kotlin/          the half that does not know what it is running on
├── io/github/nodyssey/
│   ├── core/
│   │   ├── NodeSeekSite.kt          URL vocabulary and route parsing
│   │   ├── VoteMarkup.kt            poll markup, read and written
│   │   ├── StardustReceiveMarkup.kt the `nsapp://stardust-receive` payment marker
│   │   ├── html/
│   │   │   ├── Selectors.kt         shared site selectors
│   │   │   ├── SiteBootstrap.kt     the base64 `__config__` every page carries
│   │   │   ├── PostConfigParser.kt  that blob → reaction tallies, by comment id
│   │   │   ├── PostListParser.kt    topic list → PostListPage
│   │   │   ├── PostDetailParser.kt  post page → PostDetail
│   │   │   ├── PostSourceParser.kt  edit page → the Markdown the author actually typed
│   │   │   ├── SearchParser.kt      search results → the same list model
│   │   │   ├── RichContentParser.kt post HTML → block/inline tree
│   │   │   └── TermsParser.kt       terms article → native reading blocks
│   │   └── report/                  NodeQuality report parsing
│   ├── model/                       the domain types
│   └── core/net/                    the JSON client, written against `HttpTransport`
└── io/github/plaza/core/
    ├── net/                         `SiteConfig`, `SiteError`, `WebUrl`, `HttpTransport`,
    │                                `SiteHtmlClient`, the Cloudflare challenge detector and
    │                                the session read model over the shared cookie store
    ├── update/                      the update manifests and version comparison
    ├── richtext/                    the block/inline tree and the Markdown that produces one
    ├── ansi/                        ANSI colour decoding for pasted terminal output
    └── TerminalColumns.kt           column widths for monospaced report tables

shared/src/androidMain/kotlin/         OkHttp, `CookieManager`, `WebSettings`, `PackageManager`
shared/src/appleMain/kotlin/           `NSURLSession` and `NSHTTPCookieStorage`
shared/src/jvmCommonMain/kotlin/       what Android and the desktop JVM answer identically

app/src/main/java/io/github/nodyssey/
├── core/
│   ├── NodeImageSite.kt         nodeimage.com's own vocabulary (off-site, user API key)
│   ├── LuckyDraw.kt             the 抽奖 vocabulary, still on `java.time`
│   └── net/                     vote request signing and the proxy routing OkHttp is given
├── data/                        repositories, Room, DataStore and composers
│   ├── local/                   Room: feed cache, read marks, browse history, reading positions
│   └── update/                  GitHub release lookup, APK download and install
├── platform/                    the Android shells behind `data`'s interfaces
├── di/                          `AppContainer`: constructor injection, no global singletons
├── notifications/               WorkManager polling and Android notifications
└── ui/                          Compose routes, screens and native renderers

core/src/main/java/io/github/plaza/core/
├── net/                         OkHttp, cookies shared with the WebView, rate gate, challenge detection
├── update/                      version-name comparison, release-note trimming
└── image/                       the Wi-Fi-only image network policy
```

Two rules keep scraping maintainable when NodeSeek changes its templates:

1. Shared markup knowledge belongs in `:shared`'s `core/html`; avoid spreading selectors through UI or data code.
2. Parsers are covered by tests using committed fixtures or focused inline HTML samples, and those tests run on both the JVM and Kotlin/Native. Tests never hit the live site.

Detailed architecture rules are in [docs/architecture.md](docs/architecture.md).

## Build

Requires JDK 21 and the Android SDK (compileSdk 37, minSdk 26).

```bash
./gradlew :app:assembleDebug
./gradlew --stop
```

Debug and release builds use different application IDs and labels, so both can sit on one device.

Full verification matches CI:

```bash
./gradlew spotlessCheck testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew --stop
```

## 数据来源

NodeSeek 没有面向第三方的公开 API。少数功能有 JSON 接口，其余页面需要解析服务端 HTML；站点整体在
Cloudflare 后面，请求必须携带浏览器特征和来自 WebView 的 Cookie。请求频率保持克制，测试不访问线上站点。

具体调用地址以 `:shared` 的 `core/NodeSeekSite.kt`、`core/html/` 和 `core/net/` 为准。
本仓库不发布账号 Cookie、抓取凭据或已登录页面样本。

## Roadmap

- [x] 原生发帖、Markdown 编辑 / 预览、表情与草稿
- [x] 评论发布（`/api/content/new-comment`）与六选一的图床上传
- [x] 点赞 / 反对 / 投喂鸡腿（`/api/statistics/{upvote,like,dislike}`）
- [x] 消息通知、未读数、私信列表 / 会话 / 发送
- [x] 每日签到（`/api/attendance`）
- [x] 帖子 / 用户搜索，与论坛列表共用管线
- [x] 用户空间与账号设置二级页（`/setting` 契约全量接入）
- [x] 鸡腿流水与星辰流水的真实分页数据
- [x] Room 离线缓存 + 已读标记
- [x] 图片全屏预览、保存与分享
- [x] WorkManager 通知轮询与系统渠道
- [x] f1 关于与社区、f2 隐私协议原生阅读
- [x] 关注 / 粉丝列表与关注 / 取关（`/api/fans/{follow,fans,add,del}`）
- [x] 等级进度与今日四项额度（`/api/progress/today`）
- [x] 管理记录（`/api/admin/ruling/page-N`）
- [x] 应用内检查更新、下载与安装（GitHub `releases/latest` + `PackageInstaller`）
- [x] 星辰转账原生化（`payment-prepare` 回显收款人 + `send` 提交）
- [x] 投票帖阅读、投票、创建、锁定 / 解锁 / 删除与投票人列表（`/api/vote/*`）
- [x] 星辰收款码渲染、付款与生成（`nsapp://stardust-receive` 标记 + `/api/stardust/list` 统计）
- [x] 帖子收藏（`/api/statistics/collection`）与「我的收藏」入口
- [x] 本机浏览历史与每帖阅读位置（Room，保留条数可调）
- [x] 编辑自己的帖子与回复（先取站点 Markdown 原文，再提交）
- [x] 折叠 `<details>` 渲染成可展开卡片，表格按内容在换行与钉住首列之间二选一

## 架构

四条不可协商的规则：

1. **SSOT**：一份数据一个所有者，其他人只观察，不持有副本
2. **UDF**：`Repository → ViewModel → 不可变 UiState → Compose`，反向只走事件
3. **依赖显式**：构造器注入 + `AppContainer`，没有全局单例
4. **数据层不产生用户文案**：`SiteError` 密封接口 + `strings.xml`

## 设计

完整文档导航见 [docs/README.md](docs/README.md)。

当前设计总纲在 [docs/design-requirements.md](docs/design-requirements.md)，站点实测词典在
[docs/design-requirements-remaining.md](docs/design-requirements-remaining.md)，补遗在
[docs/design-requirements-additions.md](docs/design-requirements-additions.md)。旧版
[design-brief.md](docs/design-brief.md) 只保留为历史输入；实现与设计差异看
[docs/implementation-status.md](docs/implementation-status.md)，已核对画板到代码的映射见
[docs/design-implementation.md](docs/design-implementation.md)。

> “Expressive”指 M3 Expressive 的完整主题方向：配色、字阶、形状、tonal 色块和状态变化。
> 根主题使用 `MaterialExpressiveTheme` 与 `MotionScheme.expressive()`；Material 3 仍固定在提供这些
> 新 API 的 1.5 Alpha，因此每次升级都必须跑完整 UI、Lint 与 release 构建门禁。

- **Token**：`:designsys` 的 `theme/` 中的 light / dark M3 配色、字阶、形状与间距；品牌色为「石墨青」。
- **首页 / 详情**：密集分割线列表、就地展开且可长按拖动重排的版块栏、已读态、连续评论与原生富文本；
  正文一律贪心折行（标题也不例外）并补汉字 / 西文间隙，文字多的表格换行压进屏宽、跑分表保留钉住
  首列的横向滚动，NodeQuality 报告与投票各为独立卡片，列表底栏收纳跳页与「上次阅读」。
- **搜索 / 通知 / 我的 / 设置**：常驻输入框的单页搜索（类型 Tab、版块单选 chip、站点真有的两档排序、
  未提交时显示历史）、私信、账号二级页、浏览历史（分组吸顶、左滑删除）、后台通知设置和 M3E 分组列表。
- **关于 / 隐私**：f1 的两屏滚动节奏；f2 的原生协议排版和失败降级。
- **自适应导航**：4 个 tab；宽窗口由 `NavigationSuiteScaffold` 切换为侧边 rail，每个 tab 保留独立返回栈。

当前截图在 [docs/screenshots/](docs/screenshots/)，完整画板在 `design/`。

## 致谢

- [tyrad/nodeseek](https://github.com/tyrad/nodeseek)（iOS 客户端，MIT）——站点结构的逆向参考
- [mrzhiin/seekmate](https://github.com/mrzhiin/seekmate)（React Native 客户端，MIT）——部分 JSON 端点的发现来源

本项目独立实现，不共享代码。

## 说明

- 本项目与 NodeSeek 官方无关，是社区自制客户端。
- 请求频率保持克制，不做任何自动化刷分行为。
- 应用不在仓库中保存 Cookie 或凭据；登录态由系统 `CookieManager` 持有。

## License

GPL-3.0. See [LICENSE](LICENSE).
