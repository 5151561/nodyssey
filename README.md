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

**已发布 1.2.3，仍在持续开发。** 读和写都已不再是“只读 v1”，当前实现总表见
[docs/implementation-status.md](docs/implementation-status.md)，主要可用能力包括：

- 版块列表、排序、Paging 无限滚动、下拉刷新和 Room 离线缓存；点底栏已选中的「首页」或顶栏应用名回到列表顶部
- 首页板块栏可自定义：长按拖动重排、把不看的板块移到队尾，顺序存本机，冷启动即生效
- 帖子详情与评论连续分页；段落、图片、表情、代码、引用、列表、表格和行内链接原生渲染
- 帖子底栏的跳页控件：下滑自动接页之外还能直接跳页、回第一页 / 最后一页；离开帖子时记下停在哪一页
  哪一楼（只存本机），下次打开点「上次阅读」回到那一楼，按钮上直接写着要去第几页
- 投票帖原生渲染：一个选项一行的投票卡片，支持投票、楼主锁定、管理员解锁与删除，公开投票可展开
  投票人列表，发帖时也能带一个投票；未投票时不显示票数和百分比，投票不入本地缓存
- 星辰收款码原生渲染：卡片写明收款人、数额、备注、Ref ID 与是否一次性，下方是你付没付、
  几人付款、共收到多少；付款走站点同一个 `send` 接口并先弹确认，自己的码与读不出收款情况时不给按钮。
  发帖与回复的底栏合成一个 APP 菜单，投票和收款码都从这里插入
- WebView 登录 / Cloudflare 验证，Cookie 与 OkHttp 共享
- 帖子 / 用户搜索：输入框常驻顶部，先选版块和类型再搜；与论坛列表共享同一条管线，
  因此结果行有一致的已读态与新回复角标，并高亮命中的关键词
- @我 / 回复主题 / 私信三组通知、未读数、楼层跳转、私信会话与发送；读过的通知与会话会回传站点，
  未读数因此真的会降下来
- 原生发帖编辑器、Markdown 预览、表情、阅读权限和本地草稿；发帖与回复都走站点接口
- 点赞 / 投喂鸡腿 / 点踩，真实计数与「已操作」状态；消耗鸡腿的两个会先说明代价再发
- 帖子收藏与收藏人数，「我的」里的「我的收藏」直达空间收藏列表；是否已收藏以站点回应为准
- 浏览历史（只在本机）：与信息流一致的行样式，按今天 / 昨天 / 最近七天 / 更早分组吸顶，
  左滑删单条可撤销、右上角菜单全部清除，保留条数可选 100 / 300 / 1000 / 无上限
- 图床可选六家：NodeImage、兰空 Lsky Pro、简单图床 EasyImage、SM.MS、imgbb，以及按上传地址和
  取值路径手填的自定义图床；图片按最长边 2048 转 WebP 后上传，密钥按图床分别存在本机
- 用户空间、账号设置二级页（资料、头像、密码、2FA、绑定状态、偏好、屏蔽列表）与 Telegram 流程
- 屏蔽按站点的判定生效：被屏蔽者的帖子不进列表，楼层折叠成一行可单条展开，「临时显示被屏蔽内容」
  只是本机的一次性开关；名单跟着账号走，在页内可按用户名添加或解除
- 关注 / 粉丝两份列表，以及公开用户页上的关注 / 取关按钮
- 等级进度与今日四项额度、鸡腿与星辰余额，鸡腿流水与星辰流水两条真实分页流水，签到 / 签到榜与社区工具
- 管理记录：处罚与奖励公示，下滑自动接页、跳页与帖子评论共用同一套控件，一条决定的几个动作连在
  一起显示，点行进对应帖子与楼层
- WorkManager 通知轮询、Android 通知渠道与免打扰设置
- f1 关于与社区页；f2 隐私协议原生长文页，WebView 仅作失败降级
- 图片查看、缩放、保存与分享
- 站外链接默认开在应用内的 Chrome Custom Tab，返回键回到帖子；设置 › 内容 › 站外链接可改回系统浏览器
- 应用内更新：从本项目 GitHub Releases 查新版、看更新说明、下载后直接拉起系统安装器；
  冷启动静默查一次（最多每六小时问一次 GitHub），有新版就在「我的」和关于入口上点红点，
  并在启动后提醒一次（写清版本号、包大小和更新日志；「稍后」后该版本不再提醒，
  可在设置 › 关于 › 启动时检查更新关掉）

修改邮箱、绑定 Telegram、邀请码购买没有原生闭环，会带用户到真实站点完成。
逐项清单见 [docs/implementation-status.md](docs/implementation-status.md)。

各版本的用户可见变化见 [CHANGELOG.md](CHANGELOG.md)，当前版本 1.2.3。

## Architecture

NodeSeek has no public API. A few JSON endpoints exist (`/api/statistics/*`, `/api/notification/*`,
`/api/vote/*`, `/api/content/list-comments`, `/api/content/new-comment`, and the `/setting` writes), but list, detail,
search and terms pages also depend on server-rendered HTML. Everything sits behind Cloudflare, so requests have to look like a real mobile browser and
carry cookies obtained from a WebView.

```text
app/src/main/java/io/github/nodyssey/
├── core/
│   ├── NodeSeekSite.kt          URL vocabulary and route parsing
│   ├── NodeImageSite.kt         nodeimage.com's own vocabulary (off-site, user API key)
│   ├── html/
│   │   ├── Selectors.kt         shared site selectors
│   │   ├── SiteBootstrap.kt     the base64 `__config__` every page carries
│   │   ├── PostConfigParser.kt  that blob → reaction tallies, by comment id
│   │   ├── PostListParser.kt    topic list → PostListPage
│   │   ├── PostDetailParser.kt  post page → PostDetail
│   │   ├── SearchParser.kt      search results → the same list model
│   │   ├── RichContentParser.kt post HTML → block/inline tree
│   │   └── TermsParser.kt       terms article → native reading blocks
│   ├── net/                     OkHttp, JSON, cookies, rate gate, challenge detection
│   ├── report/                  NodeQuality report parsing
│   └── update/                  version-name comparison, release-note trimming
├── model/                       Android-free domain types
├── data/                        repositories, Room, DataStore and composers
│   ├── local/                   Room: feed cache, read marks, browse history, reading positions
│   └── update/                  GitHub release lookup, APK download and install
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

Debug and release builds use different application IDs and labels, so both can sit on one device.

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

> “Expressive”指 M3 Expressive 的完整主题方向：配色、字阶、形状、tonal 色块和状态变化。
> 根主题使用 `MaterialExpressiveTheme` 与 `MotionScheme.expressive()`；Material 3 仍固定在提供这些
> 新 API 的 1.5 Alpha，因此每次升级都必须跑完整 UI、Lint 与 release 构建门禁。

- **Token**：`ui/theme/` 中的 light / dark M3 配色、字阶、形状与间距；品牌色为「石墨青」。
- **首页 / 详情**：密集分割线列表、就地展开且可长按拖动重排的版块栏、已读态、连续评论与原生富文本；
  正文启用平台最优断行与汉字 / 西文间隙，表格首列钉住，NodeQuality 报告与投票各为独立卡片，
  列表底栏收纳跳页与「上次阅读」。
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
