# NodeSeek Android

An unofficial, open-source Android client for [NodeSeek](https://www.nodeseek.com/), built with
Kotlin and Jetpack Compose.

NodeSeek has an iOS client but no Android one, and the mobile web experience leaves a lot on the
table. This app renders posts and comments as **native Compose content** — no WebView for reading —
so text is selectable, scrolling stays in a single list, and the whole thing follows the system
theme.

> 中文说明见下方。

## 状态

**v1 开发中：只读 + 登录浏览。** 目前已经可用的功能：

- 版块列表来自 `/api/content/list-categories`（实时，不写死），无限滚动 + 下拉刷新
- 帖子详情：正文、评论分页（自动续接为一条长列表）
- **原生正文渲染**：段落、图片、表情、代码块、引用、列表、表格、行内链接
- 头像加载失败时回退到首字母色块
- 深色模式
- WebView 登录 / Cloudflare 验证，Cookie 与 OkHttp 共享
- 本地缓存搜索 + 最近搜索
- 回复 / @ / 私信通知、未读数与楼层跳转
- “我的”与设置（主题、阅读字号、图片策略、清缓存）

尚未实现（见 [Roadmap](#roadmap)）：回复、点赞、收藏、签到与全站远程搜索。

## Architecture

NodeSeek has no public API. A few JSON endpoints exist (`/api/statistics/*`, `/api/notification/*`,
`/api/content/list-comments`), but **list and detail pages are server-rendered HTML** and must be
scraped. Everything also sits behind Cloudflare, so requests have to look like a real mobile browser
and carry cookies obtained from a WebView.

```
app/src/main/java/io/github/nsreader/
├── core/
│   ├── NodeSeekSite.kt        URL vocabulary, categories, route parsing
│   ├── html/
│   │   ├── Selectors.kt       every CSS selector that depends on site markup
│   │   ├── PostListParser.kt  topic list → PostListPage
│   │   ├── PostDetailParser.kt post page → PostDetail
│   │   └── RichContentParser.kt  post HTML → block/inline tree
│   └── net/
│       ├── NodeSeekClient.kt     OkHttp + browser headers
│       ├── WebViewCookieJar.kt   one cookie store for OkHttp and the WebView
│       └── ChallengeDetector.kt  Cloudflare / login-wall detection
│       └── NodeSeekJsonClient.kt  the few real JSON endpoints
├── model/       parsed domain types (no Android dependencies)
├── data/        repository
└── ui/          Compose screens + the native rich-text renderer
```

Two rules keep this maintainable when NodeSeek changes its templates:

1. **All markup knowledge lives in `Selectors.kt`.** Parsers never inline a selector string.
2. **Parsers are covered by JVM tests against captured HTML fixtures**
   (`app/src/test/resources/fixtures/`). Tests never hit the live site — the markup changes and
   Cloudflare will not answer a CI runner.

```bash
./gradlew :app:testDebugUnitTest
```

## Build

Requires JDK 21 and the Android SDK (compileSdk 36, minSdk 26).

```bash
./gradlew :app:assembleDebug
```

## 数据来源

NodeSeek 没有面向第三方的公开 API。少数功能有 JSON 接口，其余（帖子列表、帖子详情）只能解析
服务端渲染的 HTML；站点整体在 Cloudflare 后面，请求需要携带浏览器特征和来自 WebView 的 Cookie。

具体调用了哪些地址，看 `core/net/` 和 `core/html/Selectors.kt` 就是全部——本仓库不额外整理和发布
接口清单或抓取指南。请求频率保持克制。

## Roadmap

- [ ] 回复、引用、表情、图片上传（走 JSON 接口，失败回退隐藏 WebView 注入 JS）
- [ ] 点赞 / 踩 / 收藏 / 鸡腿
- [x] 消息通知与未读数（`/api/notification/*`）
- [ ] 每日签到（`/api/attendance`）
- [x] 已缓存帖子 / 用户搜索
- [ ] 全站远程搜索、完整用户资料
- [x] Room 离线缓存 + 已读标记
- [ ] 图片全屏预览与保存

## 架构

约定与 MAD 现代化评估见 [docs/architecture.md](docs/architecture.md)。四条不可协商的规则：

1. **SSOT**：一份数据一个所有者，其他人只观察，不持有副本
2. **UDF**：`Repository → ViewModel → 不可变 UiState → Compose`，反向只走事件
3. **依赖显式**：构造器注入 + `AppContainer`，没有全局单例
4. **数据层不产生用户文案**：`NodeSeekError` 密封接口 + `strings.xml`

```bash
./gradlew :app:testDebugUnitTest
```

## 设计

需求文档在 [docs/design-brief.md](docs/design-brief.md)，视觉稿据此产出，已落地的部分：

> 关于「Expressive」的口径：落地的是 M3 Expressive 的 **token 与版式方向**——配色、字阶、形状、
> tonal 色块、选中态换填充图标。**没有**用到 `MaterialExpressiveTheme` / `MotionScheme` 那套
> 动效 API：它们在 material3 1.4.0（当前 BOM 解析到的版本）里是 `internal`，1.5.0 才转正式 API，
> 而 1.5.0 目前还是 alpha。为一套动效把整个应用挪到 alpha 版 Material 不划算，等它转 stable 再说，
> 触发条件写在 `ui/theme/Theme.kt` 里。

- **Token**：`ui/theme/` 下的 light / dark 全量 M3 配色（品牌色「石墨青」`#35606E`，
  深色 surface `#121318` 而非纯黑，保证分割线可见）、字阶、形状与间距。不跟随壁纸取色。
- **首页**：分割线密集列表（一屏 9 条）、tonal 版块标签按四类分组、已读态降对比度、
  置顶 / 锁帖标识、骨架屏、人机验证态、需登录态、排序切换。
- **详情**：滚动后才出现标题的 App Bar + 24dp tonal 楼主卡 + 无分割线评论流；`@某人 #3`
  折叠成可点 chip 并滚动到对应楼层；鸡腿确认弹层已预留写操作入口。
  正文排版规范见 `RichContent.kt` 里的 `RichContentSpec` preview。
- **状态合集**：`ui/common/StatusViews.kt`，一套 tonal 有机形 + 图标 + 说明 + 动作。
- **搜索 / 通知 / 我的 / 设置**：离线缓存搜索、最近记录、通知分段与未读、个人资源卡、M3E 分组设置。
- **底部导航**：4 tab；窗口够宽时由
  `NavigationSuiteScaffold` 自动换成侧边 rail，每个 tab 各留一条返回栈。

当前实现截图在 [docs/screenshots/](docs/screenshots/)。

## 致谢

- [tyrad/nodeseek](https://github.com/tyrad/nodeseek)（iOS 客户端，MIT）——站点结构的逆向参考，
  测试用的 HTML fixture 也取自该仓库
- [mrzhiin/seekmate](https://github.com/mrzhiin/seekmate)（React Native 客户端，MIT）——
  `/api/content/list-categories`、`/api/account/getInfo/{uid}` 等 JSON 端点由该项目发现

本项目独立实现，不共享代码。

## 说明

- 本项目与 NodeSeek 官方无关，是社区自制客户端。
- 请求频率保持克制，不做任何自动化刷分行为。
- 应用不在仓库中保存任何 Cookie 或凭据；登录态由系统 `CookieManager` 持有。

## License

GPL-3.0. See [LICENSE](LICENSE).
