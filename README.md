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

尚未实现（见 [Roadmap](#roadmap)）：回复、点赞、收藏、签到、通知、搜索、离线缓存。

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

## 已知的 JSON 接口

浏览相关的内容没有接口，只能抓 HTML；以下是实测可用的 JSON 端点：

| 接口 | 说明 | 是否需要登录 |
|---|---|---|
| `GET /api/content/list-categories` | 版块列表（含描述、图标、是否显示在导航） | 否 |
| `GET /api/account/getInfo/{uid}` | 用户资料 | — |
| `GET /api/notification/unread-count` | 未读数 | 是（未登录返回 500） |
| `GET /api/statistics/list-collection?page=` | 收藏列表 | 是（未登录返回 500） |
| `GET /api/content/list-comments?uid=&page=` | 某用户的评论 | — |
| `GET /api/content/list-discussions?uid=&page=` | 某用户的主题 | — |
| `POST /api/attendance?random=` | 签到 | 是 |
| `GET /api/attendance/board?page=` | 签到榜 | — |
| `POST /api/block-list/add` | 屏蔽用户 | 是 |
| `GET /api/statistics/{like,dislike,collection,upvote}` | 点赞 / 踩 / 收藏 / 鸡腿 | 是 |

列表页支持 `?sortBy=` 参数（按发帖时间 / 回复时间排序）。`/categories/inside`（内版）在未登录时返回 **HTTP 400**。

## Roadmap

- [ ] 回复、引用、表情、图片上传（走 JSON 接口，失败回退隐藏 WebView 注入 JS）
- [ ] 点赞 / 踩 / 收藏 / 鸡腿
- [ ] 消息通知与未读数（`/api/notification/*`）
- [ ] 每日签到（`/api/attendance`）
- [ ] 搜索、用户主页
- [ ] Room 离线缓存 + 已读标记
- [ ] 图片全屏预览与保存

## 致谢

站点结构的逆向来自 [tyrad/nodeseek](https://github.com/tyrad/nodeseek)（iOS 客户端，MIT）——测试用
的 HTML fixture 也取自该仓库。本项目独立实现，不共享代码。

## 说明

- 本项目与 NodeSeek 官方无关，是社区自制客户端。
- 请求频率保持克制，不做任何自动化刷分行为。
- 应用不在仓库中保存任何 Cookie 或凭据；登录态由系统 `CookieManager` 持有。

## License

GPL-3.0. See [LICENSE](LICENSE).
