# Nodyssey

Nodyssey is an unofficial, open-source Android client for
[NodeSeek](https://www.nodeseek.com/), built with Kotlin and Jetpack Compose.

NodeSeek has an iOS client but no official Android client. Nodyssey renders posts and comments as
**native Compose content**—there is no WebView in the normal reading path—so text is selectable,
scrolling stays in one list, and the UI follows the system theme.

源码、问题反馈与版本发布：[GitHub](https://github.com/5151561/nodyssey) ·
[Issues](https://github.com/5151561/nodyssey/issues) ·
[Releases](https://github.com/5151561/nodyssey/releases)

> 中文说明见下方。

## 状态

**开发中，但已不再是“只读 v1”。** 当前实现总表见
[docs/implementation-status.md](docs/implementation-status.md)，主要可用能力包括：

- 版块列表、排序、Paging 无限滚动、下拉刷新和 Room 离线缓存
- 帖子详情与评论连续分页；段落、图片、表情、代码、引用、列表、表格和行内链接原生渲染
- WebView 登录 / Cloudflare 验证，Cookie 与 OkHttp 共享
- 帖子 / 用户远程搜索、最近搜索、按需分页与追加失败重试
- @我 / 回复主题 / 私信三组通知、未读数、楼层跳转、私信会话与发送
- 原生发帖编辑器、Markdown 预览、表情、阅读权限和本地草稿
- 用户空间、账号设置二级页、Telegram 流程、成长 / 签到与社区工具
- WorkManager 通知轮询、Android 通知渠道与免打扰设置
- f1 关于与社区页；f2 隐私协议原生长文页，WebView 仅作失败降级
- 图片查看、缩放、保存与分享

仍未接入的关键写操作包括评论发布、NodeImage 上传、点赞 / 反对 / 投喂 / 收藏。关注 / 粉丝、
星辰流水和管理记录依赖尚未确认的动态站点数据源，当前会明确提示并提供网页降级，不会伪装为空数据。

尚未发布的用户可见变化见 [CHANGELOG.md](CHANGELOG.md)。

## Architecture

NodeSeek has no public API. A few JSON endpoints exist (`/api/statistics/*`, `/api/notification/*`,
`/api/content/list-comments`), but list, detail, search and terms pages also depend on server-rendered
HTML. Everything sits behind Cloudflare, so requests have to look like a real mobile browser and
carry cookies obtained from a WebView.

```text
app/src/main/java/io/github/nodyssey/
├── core/
│   ├── NodeSeekSite.kt          URL vocabulary and route parsing
│   ├── html/
│   │   ├── Selectors.kt         shared site selectors
│   │   ├── PostListParser.kt    topic list → PostListPage
│   │   ├── PostDetailParser.kt  post page → PostDetail
│   │   ├── RichContentParser.kt post HTML → block/inline tree
│   │   └── TermsParser.kt       terms article → native reading blocks
│   └── net/                     OkHttp, JSON, cookies and challenge detection
├── model/                       Android-free domain types
├── data/                        repositories, Room, DataStore and composers
├── notifications/               WorkManager polling and Android notifications
└── ui/                          Compose routes, screens and native renderers
```

Two rules keep scraping maintainable when NodeSeek changes its templates:

1. Shared markup knowledge belongs in `core/html`; avoid spreading selectors through UI or data code.
2. Parsers are covered by JVM tests using committed fixtures or focused inline HTML samples. Tests never hit the live site.

Detailed architecture rules are in [docs/architecture.md](docs/architecture.md).

## Build

Requires JDK 21 and the Android SDK (compileSdk 37, minSdk 26).

```bash
./gradlew :app:assembleDebug
./gradlew --stop
```

Full verification matches CI:

```bash
./gradlew spotlessCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew --stop
```

## 数据来源

NodeSeek 没有面向第三方的公开 API。少数功能有 JSON 接口，其余页面需要解析服务端 HTML；站点整体在
Cloudflare 后面，请求必须携带浏览器特征和来自 WebView 的 Cookie。请求频率保持克制，测试不访问线上站点。

具体调用地址以 `core/NodeSeekSite.kt`、`core/net/` 和 `core/html/` 为准。本仓库不发布账号 Cookie、
抓取凭据或已登录页面样本。

## Roadmap

- [x] 原生发帖、Markdown 编辑 / 预览、表情与草稿
- [ ] 接入评论发布与 NodeImage 上传端点
- [ ] 接入点赞 / 反对 / 收藏 / 投喂
- [x] 消息通知、未读数、私信列表 / 会话 / 发送
- [x] 每日签到（`/api/attendance`）
- [x] 帖子 / 用户远程搜索与追加分页
- [x] 用户空间与账号设置二级页
- [x] Room 离线缓存 + 已读标记
- [x] 图片全屏预览、保存与分享
- [x] WorkManager 通知轮询与系统渠道
- [x] f1 关于与社区、f2 隐私协议原生阅读
- [ ] 接入关注 / 粉丝、星辰流水、管理记录的真实数据源

## 架构

四条不可协商的规则：

1. **SSOT**：一份数据一个所有者，其他人只观察，不持有副本
2. **UDF**：`Repository → ViewModel → 不可变 UiState → Compose`，反向只走事件
3. **依赖显式**：构造器注入 + `AppContainer`，没有全局单例
4. **数据层不产生用户文案**：`NodeSeekError` 密封接口 + `strings.xml`

## 设计

完整文档导航见 [docs/README.md](docs/README.md)。

当前设计总纲在 [docs/design-requirements.md](docs/design-requirements.md)，站点实测词典在
[docs/design-requirements-remaining.md](docs/design-requirements-remaining.md)，补遗在
[docs/design-requirements-additions.md](docs/design-requirements-additions.md)。旧版
[design-brief.md](docs/design-brief.md) 只保留为历史输入；实现与设计差异看
[docs/implementation-status.md](docs/implementation-status.md)，已核对画板到代码的映射见
[docs/design-implementation.md](docs/design-implementation.md)。

> “Expressive”指 M3 Expressive 的 token 与版式方向：配色、字阶、形状、tonal 色块和状态变化。
> 当前没有为了动效使用 alpha 版 `MaterialExpressiveTheme` / `MotionScheme` API。

- **Token**：`ui/theme/` 中的 light / dark M3 配色、字阶、形状与间距；品牌色为「石墨青」。
- **首页 / 详情**：密集分割线列表、版块 tonal 标签、已读态、连续评论与原生富文本。
- **搜索 / 通知 / 我的 / 设置**：远程搜索、私信、账号二级页、后台通知设置和 M3E 分组列表。
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
