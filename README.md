# Nodyssey

[![Telegram Channel](https://img.shields.io/badge/Telegram-Channel-26A5E4?logo=telegram&logoColor=white)](https://t.me/nodyssey_official)
[![Telegram Group](https://img.shields.io/badge/Telegram-Group-26A5E4?logo=telegram&logoColor=white)](https://t.me/+97ANIwVaCYk1MjQ1)

Nodyssey 是 [NodeSeek](https://www.nodeseek.com/) 的非官方开源 Android 客户端，用 Kotlin 和 Jetpack
Compose 写成。

NodeSeek 有 iOS 客户端，但没有官方 Android 客户端。Nodyssey 把帖子和评论渲染成**原生 Compose 内容**
——正常阅读路径上没有 WebView——所以文字可选中、滚动只有一条列表、界面跟随系统主题。

源码、问题反馈与版本发布：[GitHub](https://github.com/5151561/nodyssey) ·
[Issues](https://github.com/5151561/nodyssey/issues) ·
[Releases](https://github.com/5151561/nodyssey/releases)

Telegram：[频道](https://t.me/nodyssey_official) · [群组](https://t.me/+97ANIwVaCYk1MjQ1)

各版本的用户可见变化见 [CHANGELOG.md](CHANGELOG.md)，文档导航见 [docs/README.md](docs/README.md)，
界面截图在 [docs/screenshots/](docs/screenshots/)。

## 功能

浏览、发帖、评论、编辑自己的帖子与回复；点赞 / 反对 / 投喂鸡腿、收藏、每日签到；帖子与用户搜索；
消息通知、未读数与私信会话；关注 / 粉丝；用户空间与账号设置；鸡腿与星辰流水、星辰转账与收款码；
投票帖的阅读、投票与管理；管理记录；等级进度与今日额度；六选一的图床上传。

阅读侧另有 Room 离线缓存与已读标记、本机浏览历史与每帖阅读位置、图片全屏预览 / 保存 / 分享、
WorkManager 通知轮询，以及应用内检查更新与安装。

界面是 M3 Expressive：4 个 tab，宽窗口由 `NavigationSuiteScaffold` 换成侧边 rail，每个 tab 各有返回栈；
正文一律贪心折行并补汉字 / 西文间隙，NodeQuality 报告与投票各成卡片，跑分表保留钉住首列的横向滚动。

## 数据来源

NodeSeek 没有面向第三方的公开 API。少数功能有 JSON 接口（`/api/statistics/*`、`/api/notification/*`、
`/api/vote/*`、`/api/content/list-comments`、`/api/content/new-comment` 以及 `/setting` 的写入），
但列表、详情、搜索和条款页仍要解析服务端渲染的 HTML；站点整体在 Cloudflare 后面，请求必须带上浏览器
特征和来自 WebView 的 Cookie。

具体调用地址以 `:shared` 的 `core/NodeSeekSite.kt`、`core/html/` 和 `core/net/` 为准。
请求频率保持克制，测试不访问线上站点，本仓库也不发布账号 Cookie、抓取凭据或已登录页面样本。

## 架构

四条不可协商的规则：

1. **SSOT**：一份数据一个所有者，其他人只观察，不持有副本
2. **UDF**：`Repository → ViewModel → 不可变 UiState → Compose`，反向只走事件
3. **依赖显式**：构造器注入 + `AppContainer`，没有全局单例
4. **数据层不产生用户文案**：`SiteError` 密封接口 + `strings.xml`

三个 Gradle 模块：

```text
:app        NodeSeek 本身 —— Compose 界面、ViewModel 和 Android 外壳
:designsys  主题、组件与富文本渲染器，不知道任何论坛的存在
:shared     界面以下的全部 —— 领域模型、解析器、网络层、Room schema 和仓库 —— 以 Kotlin
            Multiplatform 形式提供：Android、桌面 JVM、iOS 和 macOS。站点自身的信息由
            `SiteConfig` 传入，不写死在这里
```

`:shared` 是唯一不知道自己跑在 Android 上的模块。CI 只构建它的 Android 那一半，Apple 目标在 Mac 上
构建，见 [docs/kmp-migration-plan.md](docs/kmp-migration-plan.md)。

曾经还有第四个模块 `:core`，装着 Android 的网络外壳 —— OkHttp 和 WebView 的 Cookie 桥。迁移的 A5 步
把它并进了 `:shared`：网络之上所有代码面对的契约写在 `commonMain`，OkHttp 是它两个实现之一，另一个是
`NSURLSession`。

`:core` 和 `:designsys` 是当年本仓库还放着第二个客户端时拆出来的。那个 App 现在住在
[5151561/plaza](https://github.com/5151561/plaza)，带走的是两个模块当时的*副本*，所以这边的改动不会
传过去；边界留着，是因为它让这个 App 里站点相关的那一半仍然看得见轮廓。

```text
shared/src/commonMain/kotlin/          不知道自己跑在什么平台上的那一半
├── io/github/nodyssey/
│   ├── core/
│   │   ├── NodeSeekSite.kt          URL 词典与路由解析
│   │   ├── VoteMarkup.kt            投票标记的读与写
│   │   ├── StardustReceiveMarkup.kt `nsapp://stardust-receive` 收款标记
│   │   ├── html/
│   │   │   ├── Selectors.kt         共用的站点选择器
│   │   │   ├── SiteBootstrap.kt     每个页面都带的 base64 `__config__`
│   │   │   ├── PostConfigParser.kt  那段 blob → 按评论 id 的表情统计
│   │   │   ├── PostListParser.kt    帖子列表 → PostListPage
│   │   │   ├── PostDetailParser.kt  帖子页 → PostDetail
│   │   │   ├── PostSourceParser.kt  编辑页 → 作者实际写下的 Markdown
│   │   │   ├── SearchParser.kt      搜索结果 → 与列表相同的模型
│   │   │   ├── RichContentParser.kt 帖子 HTML → 块 / 行内树
│   │   │   └── TermsParser.kt       条款文章 → 原生阅读块
│   │   └── report/                  NodeQuality 报告解析
│   ├── model/                       领域类型
│   ├── core/net/                    JSON 客户端，写在 `HttpTransport` 之上
│   ├── data/                        全部仓库，以及离线下载引擎
│   └── data/local/                  Room：schema、DAO 和每一次迁移
└── io/github/plaza/core/
    ├── net/                         `SiteConfig`、`SiteError`、`WebUrl`、`HttpTransport`、
    │                                `SiteHtmlClient`、Cloudflare 挑战检测，以及共享 Cookie
    │                                存储之上的会话读模型
    ├── update/                      更新清单与版本比较
    ├── richtext/                    块 / 行内树，以及产出它的 Markdown
    ├── ansi/                        粘贴终端输出时的 ANSI 颜色解码
    └── TerminalColumns.kt           等宽报告表格的列宽

shared/src/androidMain/kotlin/         OkHttp、`CookieManager`、`WebSettings`、`PackageManager`，
                                       以及数据库文件在哪里打开
shared/src/appleMain/kotlin/           `NSURLSession`、`NSHTTPCookieStorage`，以及在 Application
                                       Support 下打开的同样两个文件
shared/src/jvmCommonMain/kotlin/       Android 与桌面 JVM 答案相同的部分

app/src/main/java/io/github/nodyssey/
├── core/
│   ├── NodeImageSite.kt         nodeimage.com 自己的词典（站外，用户自带 API key）
│   ├── LuckyDraw.kt             抽奖词典，仍在 `java.time` 上
│   └── net/                     投票请求签名，以及交给 OkHttp 的代理路由
├── data/                        仓库需要平台支持的部分：离线文件、WorkManager、Coil 缓存、
│                                六种上传协议
├── platform/                    `data` 那些接口背后的 Android 外壳
├── di/                          `AppContainer`：构造器注入，没有全局单例
├── notifications/               WorkManager 轮询与 Android 通知
└── ui/                          Compose 路由、界面与原生渲染器

core/src/main/java/io/github/plaza/core/
├── net/                         OkHttp、与 WebView 共享的 Cookie、限速闸、挑战检测
├── update/                      版本号比较、发布说明裁剪
└── image/                       仅 Wi-Fi 加载图片的网络策略
```

NodeSeek 改模板时，靠两条规则维持抓取的可维护性：

1. 共用的标记知识放在 `:shared` 的 `core/html`，不要把选择器散进 UI 或数据层代码。
2. 解析器都有测试覆盖，用提交进仓库的 fixture 或聚焦的内联 HTML 样本，并且在 JVM 和 Kotlin/Native 上
   都跑。测试永不访问线上站点。

详细的架构规则见 [docs/architecture.md](docs/architecture.md)。

## 构建

需要 JDK 21 和 Android SDK（compileSdk 37，minSdk 26）。

```bash
./gradlew :app:assembleDebug
./gradlew --stop
```

debug 与 release 用不同的 applicationId 和名称，可以同时装在一台设备上。

与 CI 一致的完整验证：

```bash
./gradlew spotlessCheck testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew --stop
```

## 致谢

- [tyrad/nodeseek](https://github.com/tyrad/nodeseek)（iOS 客户端，MIT）——站点结构的逆向参考
- [mrzhiin/seekmate](https://github.com/mrzhiin/seekmate)（React Native 客户端，MIT）——部分 JSON 端点的发现来源

本项目独立实现，不共享代码。

## 说明

- 本项目与 NodeSeek 官方无关，是社区自制客户端。
- 请求频率保持克制，不做任何自动化刷分行为。
- 应用不在仓库中保存 Cookie 或凭据；登录态由系统 `CookieManager` 持有。

## 许可

GPL-3.0，见 [LICENSE](LICENSE)。
