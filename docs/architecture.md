# 架构与 MAD 现代化

首次评估：2026-07-25 · 最近复核：2026-07-28 · 范围：整个 `:app` 模块 · 基线：Android 官方架构指南 + Now in Android

这份文档记录**架构约定**和**为什么这么定**。改架构前先读这里；新增代码违反这里的约定，要么改代码，要么先改这份文档。

当前状态：阶段一至三已完成，阶段四的大部分页面与批次 F（f1–f4）已经落地。当前工作区有
409 个 JVM/Robolectric 测试；Room/Paging 离线优先、WorkManager 通知轮询和 CI 门禁均已接入。
下一步是帖子互动（点赞/反对/投喂）等真实写操作，以及仍缺可靠数据源的动态站点页面。

功能是否真的可用以 [`implementation-status.md`](implementation-status.md) 为准；设计画板完成度不作为实现证据。

---

## 1. 核心约定（不可协商的四条）

### 1.1 单一数据源（SSOT）

**任何一份数据只有一个所有者，其他人只能观察，不能持有副本。**

| 数据 | 所有者 | 消费方式 |
|---|---|---|
| 用户设置 | `SettingsRepository`（DataStore） | `collectAsState(settings)` |
| 最近搜索 | `SettingsRepository`（DataStore） | `SearchViewModel` 组合进 UiState |
| 版块列表 | `boards` 表 → `CategoryRepository.boards`（Flow） | ViewModel `onEach` 镜像进 UiState |
| 帖子列表 | `posts` + `feed_positions` 表 → `PostRepository.feed()` | `collectAsLazyPagingItems()` |
| 帖子详情 | `post_details` + `post_comments` 表 → `PostRepository.thread()` | ViewModel `onEach` 镜像进 UiState |
| 已读状态 | `post_read_marks` 表 | 在 SQL 里 join 进列表行，UI 不再单独查 |
| 我的资料 | `self_profile` 表 → `ProfileRepository.observeProfile()` | 先回放同会话缓存，再后台刷新写回 Room |
| App 设置与通知轮询配置 | `SettingsRepository`（DataStore） | UI 与 `NodysseyApp` 分别 collect 同一设置流 |
| 隐私协议文档 | `TermsRepository` 的单次结果 | `PrivacyViewModel` 持有当前加载状态；失败不写入伪造正文 |

**需要离线持久化的内容以 Room 为 SSOT，设置以 DataStore 为 SSOT；一次性远程文档由对应 Repository
负责。** 对帖子链路而言，这不是“加个缓存”，而是把数据所有权从 ViewModel 搬到数据库：ViewModel
不再持有帖子列表，`PostListUiState` 里连 `posts` 字段都没有了。

**反面教材**（本项目真实发生过）：`CategoryRepository` 最初用 `private var cached: List<Board>?` 缓存。
那是一个不可观察、非线程安全的手工缓存——刷新之后谁拿到新值取决于调用顺序。先改成 `StateFlow` + `Mutex`，
阶段二再改成 Room 表。

> 设置类数据尤其要守死这条。设置被复制进 ViewModel 字段或 `object` 单例，是"改了设置有的地方生效有的地方不生效"的唯一成因。

### 1.2 单向数据流（UDF）

```
读：Room(SSOT) → Repository → ViewModel → 不可变 UiState → Compose
写：Compose 事件 → ViewModel 方法 → Repository → 网络/本地存储 → SSOT 更新 → 触发上面的读
```

- `UiState` 是 `data class`，全部字段不可变。
- ViewModel 只通过 `_uiState.update { }` 改状态，不暴露 `MutableStateFlow`。
- Composable 不持有业务状态，不直接调 Repository。
- **刷新成功的回调里不许直接写内容进 UiState。** 内容只能从 Room 的观察流里回来，
  否则同一份数据有两条到达屏幕的路径，两条就会不一致。见 `PostDetailViewModel.load()` 的 `onSuccess`：
  它只清 loading 标志。

**分页是例外，而且是官方认可的例外**：列表的行、loading 和错误状态由 `PagingData` /
`CombinedLoadStates` 承载，不进 `PostListUiState`。把它们镜像进 UiState 就等于又造了一份副本。
所以 `PostListUiState` 只剩"不是列表的东西"：版块和当前选中的版块。

### 1.3 依赖显式且可替换

依赖走**构造器注入**，由 `AppContainer` 组装，`NodysseyApp` 创建并向下传递。

**没有全局单例。** 早期版本用过 `object ServiceLocator`，后果是 ViewModel 无法测试——
没有任何办法塞进一个假的 Repository。现在 `AppContainer` 是接口，测试可以整体替换。

**为什么不用 Hilt**：不是技术上不能，是不值得。手工构造器注入同样满足"依赖显式、可替换、作用域正确"，
官方架构指南也明确接受；当前仍是单模块和一个 `AppContainer`，Hilt 换来的主要是注解与生成代码开销。

> **KSP 的阻塞条件已经解除。** 原先的判断是 KSP 版本必须与 Kotlin 版本逐段绑定，
> 但 KSP2 已经**放弃了 `<kotlin 版本>-<ksp 版本>` 的坐标格式**并独立发布。
> 因此 Room 可以继续使用 KSP；升级 Kotlin 时应验证 KSP 与 Room 的实际构建，而不是比较版本字符串。
>
> 也就是说 Hilt 现在随时可以接。之所以没接，是上面那条"不值得"，而不是技术阻塞——
> 别再把"等 KSP"写进任何待办里。真要迁移的话，把 `AppContainer` 的各 `by lazy` 换成
> `@Module @Provides`，ViewModel 加 `@HiltViewModel`，因为已经是构造器注入，改动是机械的。
>
> **教训**：把"最新版本是 X"写进架构文档，就等于给文档设了一个静默过期时间。
> 这类判断要写清依据（坐标格式、编译目标），下一个人才能自己复核，而不是照抄一个过期结论。

### 1.4 数据层不产生用户可见文案

`NodeSeekError` 是密封接口，**不带任何字符串**。文案在 `res/values/strings.xml`，
由 `NodeSeekError.message()` 一个地方翻译。

早期版本把中文错误消息写在 `NodeSeekException` 里，等于把 UI 语言硬编码进了网络层，且无法本地化。

---

## 2. 分层

```
ui/          Compose。Route(有状态) + Screen(无状态) 分离，Screen 可 @Preview 可测
  └ 只依赖 UiState / LazyPagingItems 和回调，不认识 Repository

ViewModel    状态容器。镜像 SSOT 进 UiState，转发用户意图，不持有内容

data/        Repository。隐藏数据源，暴露领域模型
  ├ PostRepository          离线优先：Room 为 SSOT + Pager
  ├ FeedRemoteMediator      把网络页写进 Room，自己不返回数据
  ├ PostRemoteDataSource    只抓取和解析（原来的 NetworkPostRepository）
  ├ CategoryRepository      JSON 接口 → boards 表
  ├ ProfileRepository       我的资料离线优先：按会话指纹隔离，Room 先显示、网络后刷新
  ├ local/                  Room：实体、DAO、TypeConverter
  ├ session/                共享 cookie store 的读模型（登录态 + 人机验证态）
  ├ nodeimage/              站外图床 nodeimage.com：API Key + 上传/列表/删除
  ├ update/                 GitHub Releases 查询、APK 下载与 PackageInstaller 会话
  └ settings/               DataStore，SSOT

core/        无 Android 依赖的纯逻辑（parser、错误类型、URL 词表、时钟）
  ├ html/    jsoup 解析，选择器全部集中在 Selectors.kt
  ├ net/     OkHttp 客户端、Cookie 桥、challenge 检测
  └ update/  版本名比较与 Release 说明裁剪

notifications/ WorkManager 周期轮询、系统通知渠道与免打扰判断
```

**线程约定**：
- `AppDispatchers` 注入，任何地方不得直接引用 `Dispatchers.IO/Default`。
- 网络在 `dispatchers.io`，**解析在 `dispatchers.default`**（80 KB 页面的 jsoup 解析是真 CPU 活）。
- Repository 保证主线程安全，调用方不需要自己切线程。Room 的 suspend DAO 自带这个保证。

**时间约定**：
- **不许直接读 `System.currentTimeMillis()`。** 注入 `AppClock`。
  缓存新鲜度决定"打开这个屏幕要不要发请求"，那是真逻辑，必须能测；
  而需要真的 sleep 才能测的过期逻辑，等于没人会测。

**站外服务约定（nodeimage.com）**：
- NodeSeek 自己**没有图床**——帖子里只存 Markdown，图片全是外链，网页版编辑器上那句
  「NodeImage已就绪」来自浏览器扩展而不是站点。所以图床是一个独立账号的独立服务，
  URL 词表单独放在 `core/NodeImageSite.kt`，不并进 `NodeSeekSite`。
- **它用自己的 `OkHttpClient`，不复用 `AppContainer.okHttpClient`。** 后者挂着 WebView 的
  cookie jar，并且给每个没写 Referer 的请求补上 `Referer: nodeseek.com`。把这两样发给一个
  我们交了 API Key 的第三方主机，等于白送它一份与它无关的浏览会话信息。
  连接池是共享的——连接池本来就按 host 分桶，不会串。
- 错误类型也是分开的（`NodeImageError`，不是 `NodeSeekError`）：NodeSeek 的 401 意思是
  "去论坛登录"，NodeImage 的 401 意思是"你的 Key 不对"，把后者导去论坛登录页是帮倒忙。
- **四个端点里只有上传真的认 API Key。** 站点 API 页面把 Key 写成通用凭证，但真机实测
  （2026-07-28）：同一把刚上传成功的 Key，`GET /api/images` 返回 401
  `{"error":"未认证，请先通过NodeSeek授权登录"}`。所以 `execute()` 带一个 `keyIsEnough` 参数决定
  401 的**含义**——上传的 401 是 `InvalidKey`，列表/删除的 401 是 `SessionRequired`，图床页对后者
  显示"要去网页操作"并给出站点入口，而不是让用户去重新生成一把本来能用的 Key。
- **上传响应有两种形状，两种都要读。** Key 认证的 `/api/upload` 回 snake_case 且 URL 嵌在
  `links.direct` 里；网页版 cookie 认证的 `/upload` 回扁平的 `url`。只读后者，就是"图床已经存下了
  图，App 却报上传失败"那个 bug。

**应用内更新约定（github.com）**：
- 分发渠道就是本项目的 GitHub Releases，所以「更新」= 下载那个 Release 上的签名 APK 并交给系统
  安装器。数据层在 `data/update/`，纯粹的版本号比较在 `core/update/VersionNames.kt`。
- **第三个 `OkHttpClient`。** 理由和 nodeimage 那条一样：`AppContainer.okHttpClient` 挂着 WebView 的
  cookie jar 并补 `Referer: nodeseek.com`，这两样发给 GitHub 都没道理。User-Agent 这里写的是
  `Nodyssey/<版本>`——GitHub API 要求调用方自报家门，而这里没有需要伪装成浏览器的挑战。
- **只信 `releases/latest`，不做任何第三方中转。** GitHub 已经把草稿和预发布排除在这个端点之外；
  连不上就明说连不上并给出手动入口，而不是换一条来路不明的下载链路。
- **比较的是 tag 和 `PackageManager` 报的 versionName**，不是 versionCode——Release 上没有
  versionCode。`release.yml` 里那道「tag 必须等于 versionName」的闸门是这件事成立的前提。
- **`PackageInstaller` 而不是 `FileProvider` + `ACTION_VIEW`。** 会话直接读我们自己的流，不需要
  导出任何 URI，也不需要给别的应用授权；结果以状态码回到 `ApkInstallResultReceiver`，而不是在
  另一个 Activity 打开的瞬间丢失。**不设 `setAppPackageName`**：debug 构建的 id 带 `.debug` 后缀，
  写错会让会话当场失败，等于这条路在它被开发的那种构建上不可测。
- **更新器活在容器里，不在 ViewModel 里。** 下载要能熬过用户退出关于页去看别的，红点要能同时被
  设置页和「我的」读到。为此 `DefaultAppContainer` 有一个进程级 `appScope`，目前只有它一个租户。
- **上次检查的结果落盘**（`SettingsRepository` 的更新簿记，和 `notificationSeenCounts` 同类，
  不进 `UserSettings`）：红点要在冷启动第一帧就在，而不是一次网络往返之后才出现；
  落盘的答案也让六小时内的重启不再问 GitHub。读出来时**仍然按当前 versionName 过滤一遍**——
  记录会比写下它的那个版本活得久，装完之后那条记录还在说「1.2.0 出了」。

**会话约定**：
- **不许硬编码 User-Agent。** 用 `resolveUserAgent()` 从 WebView 读，WebView 那边则**一行都不设**。
  Cloudflare 的 managed challenge 会拿 `User-Agent` header 去和 JS 环境（`navigator.userAgentData`、
  `Sec-CH-UA`）交叉核对，而后者是 WebView 按真实 Chromium 版本上报的，`setUserAgentString` 改不动它。
  header 和 JS 自相矛盾 → 再发一次挑战 → 无限勾选。这个 bug 真实存在过，
  `UserAgentTest` 锁住"UA 来自 WebView"和"WebView 不被覆盖"两条。
  注意 `setUserAgentString` 会把 UA 标记为已覆盖并改变客户端提示的上报方式，
  **所以把它设成它本来就有的值不是 no-op。**
- **cookie 就是会话。** WebView 和 OkHttp 共用 `CookieManager`，没有第二份拷贝，也没有 token 存储。
- `CookieManager` 没有变更通知，所以 `SessionRepository` 的读取是显式调用的，
  **且只有 WebView 那个屏幕会调**。这是刻意的：`generation` 只在用户真的走过一次 WebView 后才变，
  数据层可以安全地拿它当"重新加载"的信号；如果它每次 Cloudflare 轮换 cookie 都变，
  列表就会在用户滚动时自己刷掉。
- **`peek()` 只读不发布，`sync()` 才发布，这个区分是承重的。** WebView 每 500ms 轮询用 `peek()`，
  `sync()` 只在 `onDispose` 调一次。发布会顶 `generation` → 清缓存 → 开始请求；
  在用户还在勾 Cloudflare 勾选框时这么做，等于朝一个进行中的挑战打一串非浏览器流量，
  能把一个本来能过的挑战变成过不去的。同理，`cf_chl_*` 这类挑战中间态 cookie
  必须排除在 fingerprint 之外（`isCloudflareNoise`，`cf_clearance` 例外）。
- **会话变化后必须先 `PostRepository.invalidateCaches()` 再重建 Pager。** 顺序反了，
  `FeedRemoteMediator.initialize()` 会返回 `SKIP_INITIAL_REFRESH`，
  刚登录的用户会继续读未登录时抓的那份列表——这个 bug 真实存在过，
  由 `PostListViewModelTest.signing in refetches the feed instead of serving the signed-out cache` 覆盖。
- **判断"是否登录"靠 cookie 名，是全项目唯一一处猜测。** 因此 `fingerprint`（决定是否刷新的信号）
  刻意不依赖名字：名字猜错只会让"我的"页面的文案不对，内容照样会重新加载。
- **认证缓存带持久化会话标记。** `cache_session` 记录缓存是否可能来自登录态以及 Cookie
  fingerprint；列表或详情从 Room 读取前必须先 `reconcileSession`。退出、Cookie 过期或切换账号时，
  帖子列表、详情和已读标记在一个事务中清除，版块与设置保留。这样进程恢复也不会先闪出旧账号内容。
- **内置 WebView 不是通用浏览器。** 只允许 HTTPS 的 `nodeseek.com` / `www.nodeseek.com` 主页面；
  帖子外链、图片和跨域跳转交给系统 URI handler。WebView 显式关闭 file/content 访问和混合内容。
- **站外链接走 Custom Tab，靠替换 `LocalUriHandler` 实现。** `MainActivity` 在主题内部把
  `CustomTabUriHandler`（`ui/common/ExternalLinks.kt`）提供给整棵树，所以显式的 `openUri` 调用和
  Compose 自己解析的正文链接注解走同一条路，不会漏掉一处。只接 http(s)：`mailto:` / `tg://` /
  `otpauth://` 原样落回平台 handler，并保留它「无人接收就抛异常」的语义——两步验证页靠这个失败告诉
  用户没装验证器。工具栏取当前 `MaterialTheme` 配色，`setColorScheme` 用 App 自己解析的深浅色而不是
  `COLOR_SCHEME_SYSTEM`（深色和定时会让 App 变深而系统不变）。设置 › 内容 › 站外链接可切回系统浏览器；
  没有浏览器支持 Custom Tab 时，多余的 extras 被忽略，等同于普通 `ACTION_VIEW`。

**导航约定**：
- **`NavDisplay` 必须显式传 `entryDecorators`。** 它的默认值是
  `listOf(rememberSaveableStateHolderNavEntryDecorator())`——只有这一个。少了
  `rememberViewModelStoreNavEntryDecorator()`，`entry {}` 里的 `viewModel()` 就落到 Activity 的
  ViewModelStore 上：返回不清除、`onCleared()` 永不触发，看过的每个帖子都留下一个还攥着整棵评论树的
  `PostDetailViewModel`，读一晚上论坛就是几十份。注意**写了这个参数就覆盖默认值**，
  SaveableStateHolder 那个必须一起列出来，否则丢的是滚动位置。
- **每个 tab 一条返回栈。** 之前切 tab 用 `backStack.clear()`，entry 被移除 →
  SaveableStateHolder 丢掉它的状态 → 列表滚动位置归零。四条栈各自 `rememberNavBackStack`，
  切换只换当前指向哪条。
- **二级 tab 在根部按返回回首页，而不是退出应用。** `NavDisplay` 只在当前栈还有东西可弹时接管返回，
  所以这条要自己挂 `BackHandler`。
- **顶层导航不要写死 `NavigationBar`。** 用 `NavigationSuiteScaffold`，窗口宽了自己变 rail。
  targetSdk 36 之后大屏不能再拒绝缩放，手机形状的单列布局是会真的被用户看到的。
  隐藏导航用 `NavigationSuiteType.None`——那是「不为导航留空间」，不是画一条空栏。

**取消约定**：
- **禁止用 `runCatching` 包裹挂起函数。** 用 `runCatchingExceptCancellation`。
  `runCatching` 捕获 `Throwable`，而协程取消就是抛异常——包裹挂起调用会把"用户切走了"
  变成"请求失败了"，然后渲染成一个用户没触发过的错误。这个 bug 在本项目真实存在过，
  由 `PostDetailViewModelTest.cancelling an in-flight load does not surface an error` 覆盖。
- 同一条规则对 `RemoteMediator` 同样成立：Paging 会取消 mediator，
  所以 `FeedRemoteMediator.load()` 里 `CancellationException` 必须重新抛出，不能变成 `MediatorResult.Error`。
  由 `FeedRemoteMediatorTest.cancellation propagates instead of becoming an error result` 覆盖。
- 竞态的处理方式变了：原来靠 `requestedSlug` 守卫手工比对，现在切换版块是
  `flatMapLatest` 换一条流 + `cachedIn`，旧流直接被取消，**没有"晚到的响应"可以污染新列表**。

---

## 3. 现状评估

按官方十个维度。`适用性 / 成熟度(0-4) / 置信度`。括号里是阶段二、三之前的分数。

| # | 维度 | 适用性 | 成熟度 | 置信度 | 说明 |
|---|---|---|---|---|---|
| 1 | 架构、状态、职责边界 | 适用 | **4**（3） | 高 | SSOT 全部落到 Room；ViewModel 不再持有内容 |
| 2 | 模块化与依赖边界 | 部分适用 | **2** | 高 | 单模块。包边界清晰，但没有编译期约束防止 ui→core/net 直连 |
| 3 | Kotlin、协程、生命周期、DI | 适用 | **3** | 高 | 构造器注入、dispatcher 与时钟均可替换、取消语义正确 |
| 4 | 数据、同步、后台任务 | 适用 | **3**（1） | 高 | Room + Paging 3 离线优先；WorkManager 轮询未读并按设置投递系统通知 |
| 5 | UI、Compose、导航、设计系统 | 适用 | **3** | 高 | Compose + M3 + Nav3；批次 F 与主要一、二级页已落地，部分写操作仍待接入 |
| 6 | 自适应、无障碍、本地化 | 适用 | **2** | 中 | 字符串外置、语义标题/按钮与自适应导航已覆盖；仍需真机验证 200% 字号和 TalkBack |
| 7 | 测试、静态质量、CI | 适用 | **4**（2） | 高 | 当前工作区有 409 个 JVM/Robolectric 测试；CI + spotless + lint 门禁齐全 |
| 8 | 性能、可靠性、可观测性 | 适用 | **1** | 中 | 无 baseline profile、无 benchmark、无崩溃上报 |
| 9 | 工具链、构建、依赖治理 | 适用 | **3**（2） | 高 | version catalog + `gradle.lockfile` 锁定传递依赖 + CI 复现构建 |
| 10 | 安全、隐私、发布完整性 | 适用 | **3**（2） | 中 | 会话缓存隔离；认证 WebView 域白名单；Cookie/Room 缓存不进入备份 |

### 已修复的问题

| 严重度 | 问题 | 修复 |
|---|---|---|
| **P1** | `runCatching` 吞掉 `CancellationException`，下拉刷新打断分页时会闪出假错误 | `runCatchingExceptCancellation` + 回归测试 |
| **P1** | `ServiceLocator` 全局单例导致 ViewModel 完全无法测试 | `AppContainer` 接口 + 构造器注入 |
| **P1** | 零持久化：离线全白，返回列表必重新请求且丢失位置 | Room 为 SSOT + Paging 3 `RemoteMediator`（阶段二） |
| **P1** | 退出或 Cookie 失效后，Room 仍可能向未登录 UI 暴露登录态缓存 | `cache_session` 会话标记 + 读取前对齐 + 事务清理 |
| **P1** | 刷新详情第 1 页时删除后续评论，却保留旧 `loadedPages`，下一次追加会跳页 | 第 1 页替换时把连续页游标重置为 1 + 回归测试 |
| **P1** | 帖子作者控制的外链进入启用 JavaScript/Cookie 的认证 WebView | WebView 精确域白名单；普通外链/图片改走系统浏览器 |
| **P2** | 数据层生产中文 UI 文案 | `NodeSeekError` 密封接口 + strings.xml |
| **P2** | `CategoryRepository` 用非线程安全的 `var` 手工缓存 | 先 `StateFlow` + `Mutex`，阶段二改为 `boards` 表 |
| **P2** | jsoup 解析跑在 IO 线程池 | 解析移到 `dispatchers.default` |
| **P2** | WebView 目录误写为 backup `file` 域，实际 Cookie 位于数据根目录 | 两套规则都排除 `root/app_webview`；Room 缓存排除整个 `database` 域 |
| **P2** | 无 CI、无格式化门禁 | GitHub Actions：锁文件 + spotless + 单测 + lint + assemble + schema 一致性 |
| **P2** | 未读数必须打开 App 才刷新 | WorkManager 周期轮询 + 网络约束 + 免打扰 + Android 通知渠道 |
| **P2** | 隐私协议直接用 WebView 承载 | `/termsofservice` 解析为原生标题、段落和列表；WebView 只作加载/解析失败降级 |
| **P3** | 切换版块时旧响应可能污染新列表 | 先 `requestedSlug` 守卫，阶段二改为 `flatMapLatest` 换流（守卫随之删除） |
| **P3** | 登录页 WebView 硬编码中文 "关闭" | 改用 `R.string.action_close`（由 lint `UnusedResources` 暴露） |
| **P3** | 表情图片 `contentDescription` 可能为 null，读屏器读不出 | 回退到 `R.string.image_description_sticker` |

**阶段二、三期间在真机上发现并修掉的 bug**（单测当时全绿，是设备验证抓到的）：

| 严重度 | 问题 | 修复 |
|---|---|---|
| **P2** | 离线打开没缓存的帖子，屏幕上只有报错，帖子却被标成已读、在列表里变灰 | "已读"改为由 Room 内容流的非空发射触发，而不是"打开了这个屏幕" |

> 这条值得单独记：`markThreadRead` 原来放在"打开屏幕"和"抓取成功"两处，看起来覆盖全了，
> 实际漏了"抓取失败但有缓存"、多算了"抓取失败且无缓存"。
> 判据换成**内容真的到了屏幕上**之后，三种情况自然都对了——
> 因为那正是"已读"的定义。回归测试：`PostDetailViewModelTest` 里那两条。

### 待办（按优先级）

| 严重度 | 问题 | 计划 |
|---|---|---|
| **P2** | 单模块，没有编译期约束防止 `ui/` 直连 `core/net` | 拆 `:core` / `:data` / `:feature:*`，或先上 lint 的依赖规则 |
| **P2** | 修改邮箱与绑定 Telegram 只能转网页（Turnstile 与 Telegram 登录挂件） | 想原生化就得在 WebView 里跑挂件并把令牌回传；在此之前保持明确交接，不做能提交却必然失败的表单 |
| **P3** | 未验证字号缩放 200% 与 TalkBack | 用真实设备和至少一台大屏设备做发布前验收 |
| **P3** | 邀请码购买只有网页闭环 | 有可靠契约后原生化，接入前保留明确网页交接。星辰转账已按这条路走完（`payment-prepare` / `send`） |
| **P3** | 无 baseline profile / macrobenchmark | 有真实卡顿反馈后再做，不预先优化 |
| **P3** | 无崩溃上报 | 发布前再定，涉及隐私取舍 |

---

## 4. 分阶段路线图

### 阶段一 ✅ 已完成

移除全局单例；构造器注入 + `AppContainer`；`AppDispatchers` 可替换；类型化错误 + 字符串外置；
设置 SSOT（DataStore）；`CategoryRepository` 改为可观察 SSOT；修正取消语义；
Route/Screen 拆分 + Preview；ViewModel 测试（含两个回归用例）。

**验收**：26 个 JVM 测试通过；模拟器实跑正常；无全局可变状态。

### 阶段二 ✅ 已完成 — 离线优先

- Room 9 张表（当前 schema 版本 5）：`boards` / `posts` / `feed_positions` / `feed_remote_keys` /
  `post_details` / `post_comments` / `post_read_marks` / `cache_session` / `self_profile`。
  schema 随代码入库（`app/schemas/`），CI 校验一致性。
- 列表改 Paging 3 + `FeedRemoteMediator`。mediator 只往 Room 写，Room 失效 PagingSource，UI 自己更新。
- 已读标记 + "N 条新回复"角标；已读帖子标题变灰。
- **原先写的阻塞条件（等 KSP）已不存在**：KSP2 改用独立版本号；Room 直接用 KSP，没引入 kapt。

**验收**（在模拟器上实测，不是推演）：
- 飞行模式冷启动，版块条和 98 条帖子全部从 Room 渲染，没有白屏。
- 飞行模式下打开之前读过的帖子，正文、引用、楼层、头像齐全（头像来自 Coil 的磁盘缓存）。
- 5 分钟缓存窗口内返回列表不发请求（`SKIP_INITIAL_REFRESH`），因此位置不丢。

两个设计上值得记住的点：

- **帖子身份和"在哪个流里"是分开的两张表。** 同一个帖子会同时出现在综合页和它自己的版块页，
  回复数还一直在变。如果行存在流里面，就会存两份、然后两份不一致。
- **流内顺序是一列显式的 `sortIndex`，不是插入顺序。** NodeSeek 按最后活跃排序，
  这个顺序推导不出来。追加时 `feed_positions` 用 `IGNORE` 而不是 `REPLACE`：
  第 1 页的帖子经常几秒后又出现在第 2 页，`REPLACE` 会把它挪到列表底部，正好在用户手指底下跳走。

### 阶段三 ✅ 已完成 — 工程护栏

- GitHub Actions（`.github/workflows/ci.yml`）：spotless → 单测 → lint → assemble → Room schema 一致性。
  五道都是硬门禁。锁文件不单列一步：locking 是 STRICT 模式，上面任何一步解析类路径时都会因
  "not part of the dependency lock state" 直接失败。
- spotless + ktlint（版本在 version catalog 里钉住）。
- Compose UI 测试持续扩展，当前工作区 JVM/Robolectric 总计 739 条（`6f3031c`），覆盖列表→详情、
  各类错误态、账号二级页和 f1/f2 关键动作。
  **跑在 Robolectric 上，所以 CI 不需要模拟器。**
- `gradle.lockfile` 用 STRICT 模式锁定 6 条类路径上的 277 个模块；
  `./gradlew resolveAndLockAll --write-locks` 更新。
- Android lint 开 `warningsAsErrors`。只禁用"按日历触发而不是按提交触发"的检查
  （`GradleDependency` 等、`OldTargetApi`）——否则上游一发新版，没改过任何代码的提交就会红。

三个踩过的坑：

- **ktlint 的 `ktlint_official` 风格不适合这个代码库。** 它会把每个多行赋值的值推到下一行缩进，
  并把函数签名拆成一行一个参数，光是无关文件就改了 800 多行、而且更难读。
  改用 `intellij_idea` 并关掉 `multiline-expression-wrapping`、`function-signature` 两条规则。
- **Spotless 的 ktlint step 不读 `.editorconfig`。** 规则写在那里看着生效、实际上静默用了 ktlint 默认值。
  所以规则放在根 `build.gradle.kts` 的 `ktlintRules` 里，`.editorconfig` 只留 IDE 用的编码和缩进。
- **Room 的 `Flow` 在它自己的 query executor 上发射**，`advanceUntilIdle()` 等不到它。
  测试里断言"由 Room 推过来的状态"时，必须把测试 dispatcher 传给
  `inMemoryDatabase(dispatcher)`（见 `TestDoubles.kt`）。
- **依赖锁定有两个坑，都是"看着生效其实没有"**：
  默认 lock mode 对"压根没有锁记录的新依赖"不报错，必须用 `LockMode.STRICT`；
  而 `lockAllConfigurations()` 在 STRICT 下会被 AGP 的内部 configuration（`androidApis`）搞崩，
  所以只锁那 6 条真正决定产物的类路径。
  另外 `./gradlew :app:dependencies` **不能**当校验步骤用：它把违规模块打印成 FAILED 然后返回 0。

### 阶段四 🚧 进行中 — 设计落地与写操作

已完成：首页、详情、远程搜索、三组通知与私信、我的/公开用户页、账号设置二级页、资产/社区工具、
图片查看器，以及批次 F 的 Telegram、通知设置、关于与隐私。f1/f2 的逐项映射见
[`design-implementation.md`](design-implementation.md)。

账号设置已经接完：15 个 `/setting` 请求全部来自站点前端分块里的真实契约（记录在不提交的
`docs/private/api-notes.md`），其中修改邮箱与绑定 Telegram 因 Turnstile 和 Telegram 登录挂件只能转网页。

鸡腿流水与星辰流水已经接完（2026-07-30）：两条契约都取自站点自己的前端分块并对登录账号只读核对过，
记录在不提交的 `docs/private/api-notes.md`。星辰不再被当成“只来自评论被点赞、只能用于转账”——
站点自己就有点赞/转账/系统/管理/购买邀请码五类，且金额可以为负。

关注/粉丝也接完了（2026-08-02），来源同样是站点自己的前端分块：两份列表不分页，写操作是
`/api/fans/{add,del}`，关注状态来自 `getInfo` 的 `followed`。未登录读列表会拿到 200 加空数组，
所以会话判断必须发生在请求之前。

今日额度与等级进度同日接完：`GET /api/progress/today` 不带 scope 就一次返回发帖、评论、
免费投喂三项，签到那项取自签到榜响应里的 `record`；等级门槛是 `rank² × 100` 的公式（Lv5 封顶）。

管理记录也接完了（2026-08-02）：契约来自站点自己的 `/static/js/ruling.*.js`，
`GET /api/admin/ruling/page-N` 任何已登录账号都读得到（未登录回 500 而不是 401），站点只服务前 100 页。
分页控件抽到 `ui/common/PageJump.kt` 与帖子评论共用；接页与跳页是两种语义，接页只接紧邻的下一页，
跳页整段替换成目标页，所以状态里同时有 `firstLoadedPage` 和 `lastLoadedPage`。跳页不受在飞的接页
阻挡：控件就长在列表底部那条工具栏上，而自动接页恰好也在那里跑，谁挡谁的结果是大部分跳页被静默丢掉。
跳页是读者对接页的覆盖，`load()` 会取消被它覆盖的那次请求。

帖子读到哪儿了存在 DataStore（`ReadingPositionStore`，键 `thread_reading_positions`），不是
`post_read_marks` 的一列：位置是边滚边写的记录，和「一次阅读写一次」的读标不是一种东西，压到那张
三个界面都在观察的表上等于一页唤醒它们好几次。**存多少条**是另一个问题，答案取自读标那边——留的位置
数就是「浏览历史」留的帖子数（`read_history_limit`，含无上限），全 App 只有一个「记得多少」的数字，
而不是第二个没人设过、也没有任何选择器够得着的上限。
`PostDetailViewModel` 在开屏时把存下的位置读进 `resumePosition` 并整次持有——帖子默认从顶部打开，
若这个提议跟着滚动重算，第一帧写下的第 1 页就会把它盖掉。

检查更新于同日搬进 App：数据源是本项目自己的 GitHub Releases 而不是 NodeSeek，
约定与理由见上面第 2 节的「应用内更新约定」。

尚未完成：邀请码购买的原生闭环；200% 字号、TalkBack 与大屏的发布级真机验收。
当前详情页没有收藏操作控件。界面存在不等于功能接入，完整边界以
[`implementation-status.md`](implementation-status.md) 为准。

---

## 5. 常见修改的正确姿势

**加一个设置项** → 只改 `SettingsRepository`（加 key、加 `UserSettings` 字段、加 setter）。
消费方 collect 就行。**不要**在任何 ViewModel 里缓存它。

**加一个屏幕** → `XxxUiState` + `XxxViewModel`（带 `factory(container)`）+ `XxxRoute`（有状态）
+ `XxxScreen`（无状态、可 Preview）。

**加一个接口调用** → 优先找 JSON 端点（本地笔记 `docs/private/api-notes.md`），没有才抓 HTML。
选择器进 `Selectors.kt`，配 fixture 测试。

**站点改版导致解析失败** → 只改 `Selectors.kt`，跑 `./gradlew :app:testDebugUnitTest`。

**给帖子加一个要持久化的字段** → 改 `PostEntity` + 两个 mapper，`NodeSeekDatabase` 的 `version` 加一。
schema 会重新导出到 `app/schemas/`，**这个 diff 必须一起提交**，否则 CI 会拦。
这些表全是公开页面的缓存、没有用户原创内容，所以走 `fallbackToDestructiveMigration`：
重新下载比写迁移便宜。**哪天存了用户自己写的东西（草稿、离线队列），这条就不成立了，必须改成写真迁移。**

**加一个 Repository 方法** → 读一律返回 `Flow`，不要返回一次性的 getter。
getter 会让调用方把结果存进字段，那就是又一份副本。

**改一处缓存过期时间** → 只有三个常量：`FeedRemoteMediator.CACHE_TTL_MILLIS`（列表，5 分钟）、
`OfflineFirstPostRepository.THREAD_CACHE_TTL_MILLIS`（帖子，2 分钟）、
`CategoryRepository.CACHE_TTL_MILLIS`（版块，12 小时）。都由 `AppClock` 驱动，都有测试覆盖。

**动了依赖** → `./gradlew resolveAndLockAll --write-locks`，把 `app/gradle.lockfile` 一起提交。

**提交前** → `./gradlew spotlessApply` 然后
`./gradlew spotlessCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
（就是 CI 跑的那几道）。
