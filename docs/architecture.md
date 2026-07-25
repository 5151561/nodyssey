# 架构与 MAD 现代化

评估日期：2026-07-25 · 范围：整个 `:app` 模块 · 基线：Android 官方架构指南 + Now in Android

这份文档记录**架构约定**和**为什么这么定**。改架构前先读这里；新增代码违反这里的约定，要么改代码，要么先改这份文档。

---

## 1. 核心约定（不可协商的四条）

### 1.1 单一数据源（SSOT）

**任何一份数据只有一个所有者，其他人只能观察，不能持有副本。**

| 数据 | 所有者 | 消费方式 |
|---|---|---|
| 用户设置 | `SettingsRepository`（DataStore） | `collectAsState(settings)` |
| 版块列表 | `CategoryRepository.boards`（StateFlow） | ViewModel `onEach` 镜像进 UiState |
| 帖子列表 / 详情 | 暂时由 ViewModel 持有（见 3.2 待办） | — |

**反面教材**（本项目真实发生过）：`CategoryRepository` 最初用 `private var cached: List<Board>?` 缓存。
那是一个不可观察、非线程安全的手工缓存——刷新之后谁拿到新值取决于调用顺序。现在改成 `StateFlow` + `Mutex`。

> 设置类数据尤其要守死这条。设置被复制进 ViewModel 字段或 `object` 单例，是"改了设置有的地方生效有的地方不生效"的唯一成因。

### 1.2 单向数据流（UDF）

```
读：Repository(SSOT) → ViewModel → 不可变 UiState → Compose
写：Compose 事件 → ViewModel 方法 → Repository → SSOT → 新 UiState
```

- `UiState` 是 `data class`，全部字段不可变。
- ViewModel 只通过 `_uiState.update { }` 改状态，不暴露 `MutableStateFlow`。
- Composable 不持有业务状态，不直接调 Repository。

### 1.3 依赖显式且可替换

依赖走**构造器注入**，由 `AppContainer` 组装，`NodeSeekApp` 创建并向下传递。

**没有全局单例。** 早期版本用过 `object ServiceLocator`，后果是 ViewModel 无法测试——
没有任何办法塞进一个假的 Repository。现在 `AppContainer` 是接口，测试可以整体替换。

**为什么不用 Hilt**：KSP 目前最新版本是 `2.2.21-2.0.5`，**不支持本项目使用的 Kotlin 2.3.20**。
唯一的替代是 kapt，而 kapt 是官方不再推荐的路径。手工构造器注入同样满足"依赖显式、可替换、作用域正确"，
官方架构指南也明确接受。

> **迁移触发条件**：KSP 发布支持 Kotlin 2.3.x 的版本后，把 `AppContainer` 的各 `by lazy` 换成
> `@Module @Provides`，ViewModel 加 `@HiltViewModel`。因为已经是构造器注入，改动是机械的。
> Room 同样在等这个条件。

### 1.4 数据层不产生用户可见文案

`NodeSeekError` 是密封接口，**不带任何字符串**。文案在 `res/values/strings.xml`，
由 `NodeSeekError.message()` 一个地方翻译。

早期版本把中文错误消息写在 `NodeSeekException` 里，等于把 UI 语言硬编码进了网络层，且无法本地化。

---

## 2. 分层

```
ui/          Compose。Route(有状态) + Screen(无状态) 分离，Screen 可 @Preview 可测
  └ 只依赖 UiState 和回调，不认识 Repository

ViewModel    状态容器。持有 UiState，调用 Repository，处理取消与竞态

data/        Repository。隐藏数据源，暴露领域模型
  ├ PostRepository        HTML 抓取 → 领域模型
  ├ CategoryRepository    JSON 接口，SSOT
  └ settings/             DataStore，SSOT

core/        无 Android 依赖的纯逻辑（parser、错误类型、URL 词表）
  ├ html/    jsoup 解析，选择器全部集中在 Selectors.kt
  └ net/     OkHttp 客户端、Cookie 桥、challenge 检测
```

**线程约定**：
- `AppDispatchers` 注入，任何地方不得直接引用 `Dispatchers.IO/Default`。
- 网络在 `dispatchers.io`，**解析在 `dispatchers.default`**（80 KB 页面的 jsoup 解析是真 CPU 活）。
- Repository 保证主线程安全，调用方不需要自己切线程。

**取消约定**：
- **禁止用 `runCatching` 包裹挂起函数。** 用 `runCatchingExceptCancellation`。
  `runCatching` 捕获 `Throwable`，而协程取消就是抛异常——包裹挂起调用会把"用户切走了"
  变成"请求失败了"，然后渲染成一个用户没触发过的错误。这个 bug 在本项目真实存在过，
  由 `PostListViewModelTest.cancelling an in-flight load does not surface an error` 覆盖。
- 每个异步结果落回 UiState 前，校验请求参数是否仍然是当前参数（见 `requestedSlug` 守卫）。

---

## 3. 现状评估

按官方十个维度。`适用性 / 成熟度(0-4) / 置信度`。

| # | 维度 | 适用性 | 成熟度 | 置信度 | 说明 |
|---|---|---|---|---|---|
| 1 | 架构、状态、职责边界 | 适用 | **3** | 高 | UDF 闭环完整，UiState 不可变，生命周期感知收集；帖子数据尚无持久化 SSOT |
| 2 | 模块化与依赖边界 | 部分适用 | **2** | 高 | 单模块。包边界清晰，但没有编译期约束防止 ui→core/net 直连 |
| 3 | Kotlin、协程、生命周期、DI | 适用 | **3** | 高 | 构造器注入、dispatcher 可替换、取消语义正确 |
| 4 | 数据、同步、后台任务 | 适用 | **1** | 高 | **最大短板**：零持久化。返回列表必重新请求，离线全白 |
| 5 | UI、Compose、导航、设计系统 | 适用 | **2** | 高 | Compose + M3 + Nav3；视觉体系待设计稿（见 design-brief.md） |
| 6 | 自适应、无障碍、本地化 | 适用 | **1** | 中 | 字符串已外置；未验证字号缩放/TalkBack/大屏 |
| 7 | 测试、静态质量、CI | 适用 | **2** | 高 | 26 个 JVM 测试（解析 + ViewModel + 错误分类）；**无 CI、无 lint 配置、无 UI 测试** |
| 8 | 性能、可靠性、可观测性 | 适用 | **1** | 中 | 无 baseline profile、无 benchmark、无崩溃上报 |
| 9 | 工具链、构建、依赖治理 | 适用 | **2** | 高 | version catalog 已用；无依赖锁定、无 CI 复现构建 |
| 10 | 安全、隐私、发布完整性 | 适用 | **2** | 中 | 不存储凭据；`allowBackup=true` 会把 WebView 会话 cookie 纳入备份（见下） |

### 已修复的问题

| 严重度 | 问题 | 修复 |
|---|---|---|
| **P1** | `runCatching` 吞掉 `CancellationException`，下拉刷新打断分页时会闪出假错误 | `runCatchingExceptCancellation` + 回归测试 |
| **P1** | `ServiceLocator` 全局单例导致 ViewModel 完全无法测试 | `AppContainer` 接口 + 构造器注入 |
| **P2** | 数据层生产中文 UI 文案 | `NodeSeekError` 密封接口 + strings.xml |
| **P2** | `CategoryRepository` 用非线程安全的 `var` 手工缓存 | `StateFlow` + `Mutex` |
| **P2** | jsoup 解析跑在 IO 线程池 | 解析移到 `dispatchers.default` |
| **P3** | 切换版块时旧响应可能污染新列表 | `requestedSlug` 守卫 |

### 待办（按优先级）

| 严重度 | 问题 | 计划 |
|---|---|---|
| **P1** | 零持久化：离线不可用，返回列表丢失位置并重新请求 | 阶段二：Room + offline-first（等 KSP 支持 Kotlin 2.3.x） |
| **P2** | `allowBackup=true`，WebView 的会话 cookie 会进入云备份 | 改 `false`，或用 `dataExtractionRules` 排除 `app_webview` |
| **P2** | 无 CI，回归全靠本地 | 阶段二：GitHub Actions 跑 `testDebugUnitTest` + `lint` |
| **P2** | 无 lint / 格式化门禁 | 接 ktlint 或 spotless |
| **P3** | 未验证字号缩放 200% 与 TalkBack | 随设计稿落地一起做 |
| **P3** | 无 baseline profile / macrobenchmark | 有真实卡顿反馈后再做，不预先优化 |

---

## 4. 分阶段路线图

### 阶段一 ✅ 已完成（本次）

移除全局单例；构造器注入 + `AppContainer`；`AppDispatchers` 可替换；类型化错误 + 字符串外置；
设置 SSOT（DataStore）；`CategoryRepository` 改为可观察 SSOT；修正取消语义；
Route/Screen 拆分 + Preview；ViewModel 测试（含两个回归用例）。

**验收**：26 个 JVM 测试通过；模拟器实跑正常；无全局可变状态。

### 阶段二 — 离线优先

- Room：`posts` / `comments` / `boards` 表，Repository 从"网络直出"改为"数据库为 SSOT，网络只写库"
- 列表改 Paging 3 + `RemoteMediator`
- 已读标记、返回列表恢复滚动位置
- **阻塞条件**：KSP 支持 Kotlin 2.3.x（Room 编译器需要）。在此之前不要为了绕开而引入 kapt。

**验收**：飞行模式下能打开最近浏览过的帖子；返回列表不重新请求也不丢位置。

### 阶段三 — 工程护栏

- GitHub Actions：PR 跑单测 + lint + assembleDebug
- ktlint/spotless
- Compose UI 测试覆盖列表→详情、错误态恢复
- 依赖版本锁定

### 阶段四 — 视觉与写操作

按 `design-brief.md` 落地 M3 Expressive；之后接回复、点赞、收藏、签到（写操作要先补一次性事件通道）。

---

## 5. 常见修改的正确姿势

**加一个设置项** → 只改 `SettingsRepository`（加 key、加 `UserSettings` 字段、加 setter）。
消费方 collect 就行。**不要**在任何 ViewModel 里缓存它。

**加一个屏幕** → `XxxUiState` + `XxxViewModel`（带 `factory(container)`）+ `XxxRoute`（有状态）
+ `XxxScreen`（无状态、可 Preview）。

**加一个接口调用** → 优先找 JSON 端点（本地笔记 `docs/private/api-notes.md`），没有才抓 HTML。
选择器进 `Selectors.kt`，配 fixture 测试。

**站点改版导致解析失败** → 只改 `Selectors.kt`，跑 `./gradlew :app:testDebugUnitTest`。
