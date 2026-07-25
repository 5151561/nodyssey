# 架构与 MAD 现代化

评估日期：2026-07-25（阶段一至三） · 范围：整个 `:app` 模块 · 基线：Android 官方架构指南 + Now in Android

这份文档记录**架构约定**和**为什么这么定**。改架构前先读这里；新增代码违反这里的约定，要么改代码，要么先改这份文档。

当前状态：阶段一至三已完成。96 个 JVM 测试、CI 五道门禁、离线优先已在模拟器实测。
下一步是阶段四（视觉与写操作）。

---

## 1. 核心约定（不可协商的四条）

### 1.1 单一数据源（SSOT）

**任何一份数据只有一个所有者，其他人只能观察，不能持有副本。**

| 数据 | 所有者 | 消费方式 |
|---|---|---|
| 用户设置 | `SettingsRepository`（DataStore） | `collectAsState(settings)` |
| 版块列表 | `boards` 表 → `CategoryRepository.boards`（Flow） | ViewModel `onEach` 镜像进 UiState |
| 帖子列表 | `posts` + `feed_positions` 表 → `PostRepository.feed()` | `collectAsLazyPagingItems()` |
| 帖子详情 | `post_details` + `post_comments` 表 → `PostRepository.thread()` | ViewModel `onEach` 镜像进 UiState |
| 已读状态 | `post_read_marks` 表 | 在 SQL 里 join 进列表行，UI 不再单独查 |

**阶段二之后，SSOT 一律是 Room 表，网络层只负责往库里写。** 这不是"加个缓存"，而是把数据的所有权
从 ViewModel 搬到数据库：ViewModel 不再持有帖子列表，`PostListUiState` 里连 `posts` 字段都没有了。

**反面教材**（本项目真实发生过）：`CategoryRepository` 最初用 `private var cached: List<Board>?` 缓存。
那是一个不可观察、非线程安全的手工缓存——刷新之后谁拿到新值取决于调用顺序。先改成 `StateFlow` + `Mutex`，
阶段二再改成 Room 表。

> 设置类数据尤其要守死这条。设置被复制进 ViewModel 字段或 `object` 单例，是"改了设置有的地方生效有的地方不生效"的唯一成因。

### 1.2 单向数据流（UDF）

```
读：Room(SSOT) → Repository → ViewModel → 不可变 UiState → Compose
写：Compose 事件 → ViewModel 方法 → Repository → 网络 → 写入 Room → 触发上面的读
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

依赖走**构造器注入**，由 `AppContainer` 组装，`NodeSeekApp` 创建并向下传递。

**没有全局单例。** 早期版本用过 `object ServiceLocator`，后果是 ViewModel 无法测试——
没有任何办法塞进一个假的 Repository。现在 `AppContainer` 是接口，测试可以整体替换。

**为什么不用 Hilt**：不是技术上不能，是不值得。手工构造器注入同样满足"依赖显式、可替换、作用域正确"，
官方架构指南也明确接受；单模块、一个 `AppContainer`、四个 Repository 的规模下，Hilt 换来的主要是注解开销。

> **KSP 的阻塞条件已经解除。** 原先的判断是"KSP 最新版 `2.2.21-2.0.5` 不支持 Kotlin 2.3.20"，
> 这个判断当时对、现在不对了：KSP 已经**放弃了 `<kotlin 版本>-<ksp 版本>` 的坐标格式**，
> 改成独立版本号，`2.3.10` 就是针对 Kotlin 2.3.20 编译的。所以 Room 在阶段二直接用上了 KSP。
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
  ├ local/                  Room：实体、DAO、TypeConverter
  └ settings/               DataStore，SSOT

core/        无 Android 依赖的纯逻辑（parser、错误类型、URL 词表、时钟）
  ├ html/    jsoup 解析，选择器全部集中在 Selectors.kt
  └ net/     OkHttp 客户端、Cookie 桥、challenge 检测
```

**线程约定**：
- `AppDispatchers` 注入，任何地方不得直接引用 `Dispatchers.IO/Default`。
- 网络在 `dispatchers.io`，**解析在 `dispatchers.default`**（80 KB 页面的 jsoup 解析是真 CPU 活）。
- Repository 保证主线程安全，调用方不需要自己切线程。Room 的 suspend DAO 自带这个保证。

**时间约定**：
- **不许直接读 `System.currentTimeMillis()`。** 注入 `AppClock`。
  缓存新鲜度决定"打开这个屏幕要不要发请求"，那是真逻辑，必须能测；
  而需要真的 sleep 才能测的过期逻辑，等于没人会测。

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
| 4 | 数据、同步、后台任务 | 适用 | **3**（1） | 高 | Room + Paging 3 离线优先；仍无 WorkManager 后台同步 |
| 5 | UI、Compose、导航、设计系统 | 适用 | **2** | 高 | Compose + M3 + Nav3；视觉体系待设计稿（见 design-brief.md） |
| 6 | 自适应、无障碍、本地化 | 适用 | **1** | 中 | 字符串已外置、表情补了 contentDescription；未验证字号缩放/TalkBack/大屏 |
| 7 | 测试、静态质量、CI | 适用 | **4**（2） | 高 | 96 个 JVM 测试（含 Room、Paging、Compose）；CI + spotless + lint 门禁齐全 |
| 8 | 性能、可靠性、可观测性 | 适用 | **1** | 中 | 无 baseline profile、无 benchmark、无崩溃上报 |
| 9 | 工具链、构建、依赖治理 | 适用 | **3**（2） | 高 | version catalog + `gradle.lockfile` 锁定传递依赖 + CI 复现构建 |
| 10 | 安全、隐私、发布完整性 | 适用 | **3**（2） | 中 | 不存储凭据；备份已排除 WebView cookie |

### 已修复的问题

| 严重度 | 问题 | 修复 |
|---|---|---|
| **P1** | `runCatching` 吞掉 `CancellationException`，下拉刷新打断分页时会闪出假错误 | `runCatchingExceptCancellation` + 回归测试 |
| **P1** | `ServiceLocator` 全局单例导致 ViewModel 完全无法测试 | `AppContainer` 接口 + 构造器注入 |
| **P1** | 零持久化：离线全白，返回列表必重新请求且丢失位置 | Room 为 SSOT + Paging 3 `RemoteMediator`（阶段二） |
| **P2** | 数据层生产中文 UI 文案 | `NodeSeekError` 密封接口 + strings.xml |
| **P2** | `CategoryRepository` 用非线程安全的 `var` 手工缓存 | 先 `StateFlow` + `Mutex`，阶段二改为 `boards` 表 |
| **P2** | jsoup 解析跑在 IO 线程池 | 解析移到 `dispatchers.default` |
| **P2** | `allowBackup=true`，WebView 的会话 cookie 会进入云备份 | 备份规则排除 `app_webview` / `webview.db`（两个文件都改，minSdk 26 下旧规则仍生效） |
| **P2** | 无 CI、无格式化门禁 | GitHub Actions：锁文件 + spotless + 单测 + lint + assemble + schema 一致性 |
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
| **P3** | 未验证字号缩放 200% 与 TalkBack | 随设计稿落地一起做（阶段四） |
| **P3** | 无后台同步，未读数要打开应用才刷新 | 有需要再上 WorkManager，不预先做 |
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

- Room 7 张表：`boards` / `posts` / `feed_positions` / `feed_remote_keys` / `post_details` /
  `post_comments` / `post_read_marks`。schema 随代码入库（`app/schemas/`），CI 校验一致性。
- 列表改 Paging 3 + `FeedRemoteMediator`。mediator 只往 Room 写，Room 失效 PagingSource，UI 自己更新。
- 已读标记 + "N 条新回复"角标；已读帖子标题变灰。
- **原先写的阻塞条件（等 KSP）已不存在**：KSP 改用独立版本号，`2.3.10` 就是针对 Kotlin 2.3.20 的。
  Room 直接用 KSP，没引入 kapt。

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
- Compose UI 测试 17 条，覆盖列表→详情、各类错误态与恢复动作、已读角标。
  **跑在 Robolectric 上，所以 CI 不需要模拟器。**
- `gradle.lockfile` 用 STRICT 模式锁定 6 条类路径上的 272 个模块；
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

### 阶段四 — 视觉与写操作

按 `design-brief.md` 落地 M3 Expressive；之后接回复、点赞、收藏、签到（写操作要先补一次性事件通道）。
无障碍验证（字号 200%、TalkBack）随这一阶段一起做。

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
