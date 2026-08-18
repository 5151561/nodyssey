# KMP-ready 架构改造计划

制定日期：2026-08-18 · 修订：目标由「迁移到 macOS」改为「先做 KMP-ready」 ·
基线提交：`56c5b2a` · 评估依据：[`kmp-migration-decision.md`](kmp-migration-decision.md)

## 0. 目标的重新定义

**不是**「花几个月把 Nodyssey 跨平台化」。**是**「把业务核心整理成 platform-neutral Kotlin，
真正需要 Apple 平台时再打开 Native target」。

这两件事的区别决定了本计划的形状：

| | KMP-ready（第一阶段，现在做） | KMP migration（第二阶段，以后） |
|---|---|---|
| 产出 | 不认识 Android、不认识 JVM 库的 core/data | `:shared` KMP 模块 + Apple target |
| 前置条件 | **无** | 决定真的要开 iOS/macOS App |
| 若最终不做 KMP | **全部是净收益**，Android 架构本身变好 | — |
| 需要 KMP 基建 | 不需要 | 需要 |

**第一阶段的每一项改造，单独看都是 Android 架构改善。**这是它不需要赌注的原因，
也是本计划把 feasibility gate 降级的原因（见第 6 节）。

---

## 1. 最终架构

前端永远原生，KMP 只承担业务核心。

```text
                        shared / commonMain
        ┌────────────────────┼────────────────────┐
        ▼                    ▼                    ▼
    Android                iOS                 macOS
   Kotlin/JVM         Kotlin/Native        Kotlin/Native
       ART               iosArm64             macosArm64
        │                    │                    │
 Android Compose         SwiftUI              SwiftUI
                        / UIKit               / AppKit
```

source set 层级用 KMP 的 hierarchical source sets，`appleMain` 让两个 Apple 平台共用实现：

```text
                    commonMain
                        │
             ┌──────────┴──────────┐
        androidMain             appleMain
                                   │
                          ┌────────┴────────┐
                       iosMain           macosMain
```

```text
shared/
├── commonMain      model / parser / domain / repository / database
│                   settings / session semantics / network contracts
├── androidMain     OkHttp / WebView cookie / Keystore / Android DB bootstrap
├── appleMain       Apple networking / Keychain / 共用的 Apple 集成
├── iosMain         iOS 特有
└── macosMain       macOS 特有
```

**目标平台假设：Apple Silicon only。**`paging-common` 3.5.0 无 `macosX64` 构件（实测），
且 Feed 依赖 Paging。如果将来要支持 Intel Mac，需重新评估 Paging 的替代方案。

---

## 2. 边界：共享到哪一层为止

**Repository 以下共享，ViewModel 以上各写各的。**

### 共享

```text
Post / User / Comment / Category / Vote / SearchResult …   domain model
HTML parser                                                 站点「协议」
PostRepository / SearchRepository / SessionRepository …     业务规则
Room entity / DAO / database                                缓存
network contracts / session semantics
```

### 不共享（永远 Android-only）

```text
:designsys
app/ui
Navigation 3
Compose
```

### 不共享（各端自己写很薄的一层）

`PostListViewModel`、`PostDetailViewModel`、`UiState`、`NavigationState`。

Apple 侧：

```swift
@Observable
final class PostListModel {
    private let repository: PostRepository
    var posts: [Post] = []
    var loading = false
    func reload() async { ... }
}
```

Android 侧维持现有 `ViewModel` + `StateFlow` 不变。

### 为什么不共享 ViewModel

门槛 C 已实测：Kotlin 2.4.10 的 Swift Export 能把 `StateFlow` 导出成类型化的
`KotlinTypedStateFlow<T>`，`suspend` 导出成原生 `async throws`，**技术上可行**。

但它是 **Alpha**，官方明说不适合生产。把一个 Alpha 特性设成长期架构边界，
换来的只是省掉每端几十行很薄的 presentation —— 不划算。

把边界卡在 Repository，则 Swift 侧只需要消费 `suspend` 函数和 domain model，
即使 Swift Export 有变动，影响面也小得多。

### 这样不缩小共享层

共享层约 16,850 行（生产代码 27%）本来就不含 ViewModel —— 它们在 `app/ui` 的 35,431 行里。
划清边界不减少共享量，只是让边界不再模糊。

---

## 3. Parser 为什么是最值得共享的一块

不是因为代码量（1,257 行，占 2%），而是因为 **NodeSeek 客户端真正的「协议」就是它**：

```text
HTML structure / CSS selector / markup quirks / API quirks / Cloudflare behavior
```

站点改一个 `div.topic-item`，不共享就要 Android 改一次、Apple 再改一次；共享则三平台只改一次。
这个价值与行数无关。

同理，Room 进 common 的投入在三平台（Android / iPhone / iPad / Mac）摊薄后，
比只为一个 iOS 重构一次容易回本。

---

## 4. 第一阶段：KMP-ready

**全程不需要任何 KMP 基建，不新建模块，不动 frontend。**每一项都可独立合并。

> **执行记录（2026-08-18）**：4.1 / 4.2 / 4.4 / 4.5 已完成，4.3 完成 7/19（12 个 Room 测试类
> 在第一阶段做不到，见 4.3）。下面每一节里带 ✅ / ⚠️ 的段落是事后按实测补写的，未标注的部分
> 是当初的计划原文。

### 4.1 让 core 不再认识 Android

把这些从 `core` / `data` 往外推：

```text
android.content.Context / Intent
android.webkit.*
android.net.* / android.os.*
```

现状：`data` 层 60 个文件中只有 **9 个** `import android.*` ——
`ProxySettings`、`ApkInstaller`、`SecretCipher`、`AppUpdateRepository`、`ImagePreparer`、
`CommentComposerRepository`、`PostComposerRepository`、`NodeSeekDatabase`、`ImageHostSettings`。

其余 51 个的障碍不是 Android，是 JVM 库（见 4.2）。**这一项的工作量比想象中小。**

`NodeSeekSite.kt` 需要拆：它用了 `resolveUserAgent`（要 `Context`）、`java.net.URI` /
`URLEncoder`，还有一个只服务于 KDoc 链接的 `import ...designsys.component.UserAvatar`
（删 import、把 `[UserAvatar]` 改成普通文字即可，不涉及架构）。

**✅ 实际做法**：这 9 个分成两类，处理方式不同。

| 类别 | 文件 | 做法 |
|---|---|---|
| 只为了拿 `DataStore` 才收 `Context` | `ProxySettings`、`ImageHostSettings`、`PostComposerRepository`、`CommentComposerRepository` | 改收 `DataStore<Preferences>`；`preferencesDataStore` 委托集中到 `di/PreferenceStores.kt` |
| 本身就是平台外壳 | `KeystoreSecretCipher`、`DefaultImagePreparer`、`ApkInstaller` + `ApkInstallResultReceiver`、`NodeSeekDatabase.create` | 整个搬进新的 `app/.../platform/` 包，接口留在 `data` |

`AppUpdateRepository` 是第三种：它没有平台外壳可搬，只是 `onInstallStatus(status: Int)` 收的是
`PackageInstaller.STATUS_*`。改成 `onInstallOutcome(InstallOutcome)`，整数到语义的翻译放在广播
到达的地方。

`resolveUserAgent` 和 `UserAvatar` 两个 import 实测都没人用，只出现在 KDoc 散文里，直接删。

**✅ `:core` 也做了同一件事**：`AppVersion` / `UserAgent` / `AcceptLanguage` /
`ImageNetworkPolicyInterceptor` 四处形状一样——中性的值和逻辑，加边缘一个读设备的函数。把读取
函数各自拆成独立文件（`AndroidAppVersion.kt`、`WebViewUserAgent.kt`、`DeviceAcceptLanguage.kt`、
`AndroidNetworkMetering.kt`）。**拆开不是为了整洁**：KMP 里一个文件不能一半 `commonMain` 一半
`androidMain`，拆过之后第二阶段就是移动文件而不是改写。`:core` 里 `import android.*` 的现在只剩
5 个以平台命名的文件。

### 4.2 让 core 不再认识 JVM library 实现

**不是不能用 OkHttp，而是 `commonMain` 不应该知道 OkHttp。**

不得出现在 domain / repository 公开 API：

```text
okhttp3.Request / Response / Interceptor
org.jsoup.Element / Document
java.io.* / java.net.*
```

已知的具体泄漏点（spike 实测）：

| 文件 | 泄漏 | 处置 |
|---|---|---|
| `TerminalColumns.kt` | `java.lang.Character.charCount`、internal `codePointAt` | 自写 UTF-16 代理对处理 |
| `StardustReceiveMarkup.kt` | `java.net.URLDecoder` / `StandardCharsets` | 自写 percent-decode，**必须保留 `+`→空格 的表单语义**，否则历史帖子收款码解码错位 |
| `SiteBootstrap.kt` | `java.util.Base64` | 换 `kotlin.io.encoding.Base64`（同为 RFC 4648） |
| 全部 parser | `org.jsoup.*` | 第二阶段换 Ksoup；第一阶段先确保 jsoup 类型不出现在 parser 的**返回值**上 |

参考实现见 `nodyssey-kmp-spike/gate-b-parser/src/commonMain/`，已通过两端测试。

**✅ 实际做法**：三处按 spike 走，但 spike 的 `percentDecode` 有一个 bug 没照抄——它对未转义
字符逐 `Char` 转字节，会把一个 emoji 拆成两个替换字符。改成「未转义字符原样抄写」，并补了测试。

`java.util.Base64` → `kotlin.io.encoding.Base64` 时 padding 设成 `PRESENT_OPTIONAL`：spike 的
注释说两者「都要求正确 padding」，实测不对——Java 的 decoder 接受无 padding 的输入，Kotlin 默认
不接受。

**✅ 多出来的一项：`NodeSeekSite` 的 `java.net.URI`**。判断一个链接是不是本站、能不能进登录
WebView、该开原生页还是浏览器，是关于站点的规则，之前却是从 `java.net.URI` 上读出来的。新增
`:core` 的 `WebUrl` 承担这件事。

`WebUrlTest` 是**对 `java.net.URI` 的差分测试**而不是手写断言：要保住的不是规范，是那一个解析器
给出的答案，而 `https://www.nodeseek.com@evil.example/`、非 web scheme、下划线主机名、非数字端口
这些绕过主机校验的写法，恰恰是手写期望最容易写错的地方。实测确认了几条反直觉行为：`URI` 对
`https://例え.jp/` 是**解析成功、host 为 null**（不是抛异常），对 `foo_bar.example` 同样；对
`:abc` 端口也是 host 为 null。

一处有意的行为差异：查询参数里的 `+` 不再解成空格（`URLDecoder` 是表单解码器）。
`encodeURIComponent` 把字面加号写成 `%2B`，站点自己生成的链接碰不到这个差异。

### 4.3 测试变纯

**这一项无论 KMP 做不做都值，且它是后续一切的前提。**

全项目 1,260 个 `@Test` 中 568 个（45%）跑在 Robolectric 上，其中 **173 个在 `data` 层**。
把这 19 个测试类改成用 fake 而非 Robolectric `Context` / Room in-memory 构建。

涉及 `FeedRemoteMediatorTest`、`SessionRepositoryTest`、`ProfileRepositoryTest`、
`CategoryRepositoryTest`、`PostDetailCacheTest`、`PostCollectionTest`、`ReadHistoryTest`、
`SearchFeedTest`、`BlockedFeedTest`、`ReadMarkTest` 等。

**例外**：`NodeSeekDatabaseMigrationTest` 测的就是 Android schema 升级，永远留在 Android 侧。

即时收益：测试执行时间显著缩短，与 KMP 无关。

#### ⚠️ 实测：这一项在第一阶段只能做到 7/19

**已脱离 Robolectric（7 个）**：`ProxySettingsTest`、`ImageHostSettingsTest`、
`PostComposerRepositoryTest`、`CommentComposerRepositoryTest`（随 4.1 的 DataStore 改造一起）、
`SessionRepositoryTest`（随 `SessionCookieStore` 一起）、`AppCacheStoreTest`（把
`DefaultAppCacheStore` 收的整个 `ImageLoader` 收窄成它真正用到的 `ImageCaches`）。另有三个
`ui` 层测试（`MessageThreadViewModelTest`、`ProfileViewModelTest`、`NotificationsViewModelTest`）
顺带换掉了 `CookieManager`。

**做不到的 12 个**：全部靠 `inMemoryDatabase()`。`javap` 查过 `room-runtime-android` 2.8.4 的
`androidx.room.Room`，四个 builder 重载**每一个都要 `android.content.Context`**，没有 common
那套无 Context 的重载。所以「真 Room 跑在纯 JVM 单测」这条路在不新建 KMP 模块的前提下不存在，
而不新建模块正是第一阶段的约束。

**并且原来那条「即时收益」不成立**：实测整个 `:app` 单测 24.5s，其中 Robolectric 类占 23.1s，
但这 12 个 Room 类只占 **2.6s**——剩下 20s 在 41 个 Compose 测试类里，而 4.6 明说不许碰
frontend。Robolectric 的 sandbox 是按 SDK 缓存、跨类共享的，所以边际成本远比想象中小。

**决定（2026-08-18）**：这 12 个继续用 Robolectric，等第二阶段 Room 进 KMP 模块（那边有
`BundledSQLiteDriver` 和无 Context 的 builder）时一起解决。换 fake DAO 的选项被否掉了——
`TestDoubles.kt` 里写明「Robolectric 而不是 fake DAO 是刻意的：要测的就是 SQL」，三表 join、
upsert 不重排、级联删除这些覆盖丢了不划算。

### 4.4 把平台能力抽成很少几个 interface

```kotlin
interface SiteTransport
interface SessionCookieStore
interface SecretCipher        // 已存在
interface AppVersionProvider
interface DatabaseFactory
interface SettingsStoreFactory
```

**然后不要过度抽象。**不为「共享率」包装 WorkManager / PackageInstaller / 通知调度 ——
那些本来就该是平台 shell。

继续用 constructor injection，不为 KMP 引入 DI framework。

**✅ 落地情况**——六个里只有一个真的需要新写接口，其余要么已存在，要么一个函数就够：

| 计划里的名字 | 实际 |
|---|---|
| `SiteTransport` | 已存在，叫 `HtmlSource`；`SiteHtmlClient` 是它的 OkHttp 实现 |
| `SessionCookieStore` | **新写**。`WebViewCookieJar` 原来和 `android.webkit.CookieManager` 绑死，而它真正值钱的是站点知识（哪些 cookie 名算登录、哪些是 Cloudflare 噪声）。接口留 `:core`，`CookieManager` 实现是 `WebViewCookieStore` |
| `SecretCipher` | 已存在；这次只是把 `KeystoreSecretCipher` 挪进 `platform/` |
| `AppVersionProvider` | 不需要接口——`AppVersion` 本来就是中性 data class，拆出 `readAppVersion` 即可 |
| `DatabaseFactory` | 不需要接口——一个 `createNodeSeekDatabase(context)` 函数 |
| `SettingsStoreFactory` | 不需要接口——`di/PreferenceStores.kt` 里一组 `Context` 扩展 |

按「不要过度抽象」这条，后三个都没做成接口。另外顺手收窄了一个不在计划里的依赖：
`DefaultAppCacheStore` 原来收整个 `ImageLoader`，实际只用两个缓存，改成 `ImageCaches`。

### 4.5 拆掉 data → designsys 反向依赖

[`SettingsRepository.kt:18`](../app/src/main/java/io/github/nodyssey/data/settings/SettingsRepository.kt)
`import io.github.plaza.designsys.editor.EditorAction` —— data 层不该依赖 UI 模块。

把需要持久化的部分下沉成 `EditorActionId`，`:designsys` 负责映射回 UI action。
这是 `data` 层唯一一处反向依赖，**单独一个 PR**。

**✅ 实测比这简单得多**：存进 DataStore 的本来就是 `List<String>`，`setComposerToolbar` 收的也是
`List<String>`——那个 import 只出现在 KDoc 散文里，没有一行代码用它。不需要 `EditorActionId`，
删掉 import 就断了。`NodeSeekSite` 的 `UserAvatar` 是同一种情况。

### 4.6 不碰 frontend

```text
app/ui
:designsys
Navigation 3
Compose
```

**一个字都不要为了 KMP 改。**

### 第一阶段验收

```bash
./gradlew testDebugUnitTest :app:assembleDebug spotlessCheck :app:lintDebug
```

- `data` / `core` 的公开 API 不出现 `android.*`、`okhttp3.*`、`org.jsoup.*`、`java.*`
- ~~`data` 包下不再出现 `RobolectricTestRunner`（迁移测试除外）~~ →
  **改为**：`data` 包下 `RobolectricTestRunner` 只剩靠 `inMemoryDatabase()` 的 12 个类，
  加上迁移测试。理由见 4.3；这一条移交第二阶段
- 测试总数不减，Android 行为不变

**✅ 2026-08-18 全绿**。`import android.*` 在 `app/.../data`、`app/.../core`、`app/.../model`
下为 0；`:core` 只剩 5 个以平台命名的文件（`AndroidAppVersion`、`DeviceAcceptLanguage`、
`WebViewUserAgent`、`WebViewCookieStore`、`AndroidNetworkMetering`）。jsoup 类型只出现在 parser
的**参数**上，没有一个出现在返回值上——第一阶段这条要求达成，换 Ksoup 留给第二阶段。

测试总数 1,269 → 1,280（`WebUrlTest` 5 个差分测试、`urlEncode` 的差分测试、
`ApkInstallOutcomeTest` 3 个、`percentDecode` 的代理对测试、`ProxySettings` 的空 store 默认值）。

---

## 5. 第二阶段：真正 KMP

**触发条件：决定真的要开 iOS/macOS App。**在此之前不必开始。

> **执行记录（2026-08-18）**：步骤 2 / 3 / 4 已完成。下面每一节里带 ✅ / ⚠️ 的段落是事后按实测补写
> 的，未标注的部分是当初的计划原文。

大致顺序（细节到时按当时的工具链状态重定）：

| 步骤 | 内容 | 说明 |
|---|---|---|
| 1 | Apple 平台 spike | WKWebView / `WKHTTPCookieStore` / `URLSession` / Keychain。**门槛 A 已在 macOS 上验证通过**，iOS 需复验 |
| ✅ 2 | 构建基础设施 | `plaza.kmp.library` convention plugin、`:shared` 模块、`appleMain` 层级 |
| ✅ 3 | model + 纯逻辑 | 第一阶段做完后基本是搬文件 |
| ✅ 4 | parser + Ksoup | 门槛 B 已验证是纯机械替换 |
| 5 | 网络契约 + Apple transport | |
| 6 | Room + DataStore | 7 个手写 migration 要重写 |
| 7 | Repository 由简到繁 | Terms/Search → Profile/Community → Post/Vote → Feed/Paging |
| 8 | Apple 前端 | SwiftUI，见下 |

### ✅ 步骤 2 实测：构建基础设施

`:shared` 是 KMP 模块，target 三个：android、`iosArm64`、`macosArm64`。**`appleMain` 不需要自己搭**
—— 默认的 hierarchy template 见到这两个 Native target 就会生成它，`compileAppleMainKotlinMetadata`
是它存在的证据。

Android target 走 **`com.android.kotlin.multiplatform.library`**，不是在 KMP 插件旁边再应用一次
`com.android.library`：AGP 9 不再支持后者那种组合。DSL 入口在 AGP 9.2.1 里是 `kotlin { android { } }`，
文档里常见的 `androidLibrary { }` 已经打上 deprecated，编译时会告警。

四处不写就不通的地方，**其中两处的症状都是「静默地什么都不跑」而不是报错**：

| | 症状 |
|---|---|
| `build-logic` 要加 `kotlin-gradle-plugin` 依赖 | 约定插件里 `id("org.jetbrains.kotlin.multiplatform")` 找不到 |
| `gradle.properties` 加 `kotlin.native.ignoreDisabledTargets=true` | Apple target 在 Linux 上不可构建，不写这条则 **configure 阶段就失败**，CI 连 Android 的门禁都跑不到 |
| CI 的测试步骤补 `testAndroidHostTest` | `testDebugUnitTest` 在 KMP 模块里根本不存在（没有 build type），`commonTest` 会一个都不跑而 CI 全绿 |
| 约定插件里还要应用 **`com.android.lint`** | 不写则 `commonMain` 完全不进 lint，见下 |

#### KMP 模块的 lint 是有条件注册的

`com.android.kotlin.multiplatform.library` 单独用的时候，模块只会得到
`lintAnalyzeAndroidHostTest`，**主组件一个 lint 任务都没有** —— 没有 `lint`、没有
`lintAnalyzeAndroidMain`、没有 `generateAndroidMainLintModel`。于是 `:app:lintDebug` 的
`checkDependencies` 里 `:core`、`:designsys` 都在，`:shared` 只有个 host test，搬进去的 4,600 行生产
代码一行都没被检查过，而约定插件里的 `warningsAsErrors = true` 守着一间空屋子。

根因在 AGP 的 `KmpTaskManager` 里，反编译可见：

```text
1116: ldc_w  "com.android.lint"
1119: PluginContainer.hasPlugin
1124: ifeq   1181                  ← 没有这个插件就整段跳过
...
1172: LintTaskManager.createLintTasks(KMP_ANDROID, ...)
```

所以 `plaza.kmp.library` 里 `com.android.lint` 和 `com.android.kotlin.multiplatform.library` 要一起
应用。补上之后 `lintAnalyzeAndroidMain` 出现并进入 `:app:lintDebug` 的任务图，lint model 里也能看到
`javaDirectories="src/androidMain/kotlin:src/commonMain/kotlin"`。

**验过覆盖是真的**：在 `commonMain` 和 `androidMain` 各放一句 `// STOPSHIP`（这条检查默认只在 release
变体开，所以探针里临时 `enable += "StopShip"`），lint 两处都报了出来。

**CI 只跑 JVM/Android**（2026-08-18 拍板）。`commonMain` 在 CI 里只按 Android 编一次，只有 Native
编不过的写法 CI 抓不到，本机 `./gradlew :shared:macosArm64Test` 才是那道闸。

#### dependency locking：计划里的警告是对的

`:shared` 声明了三十多个可解析配置（compiler plugin classpath、commonizer classpath、Swift export
…），绝大多数不产生 lock state，而 STRICT 下「没有 lock state」就是失败。实测**只有这 7 个**有：

```text
androidCompileClasspath / androidRuntimeClasspath
androidHostTestCompileClasspath / androidHostTestRuntimeClasspath
metadataCommonMainCompileClasspath
iosArm64CompileKlibraries / macosArm64CompileKlibraries
```

Apple 两个照锁不误，虽然 CI 永远不解析它们——锁文件的意义是图由仓库定，而不是由哪台机器跑的构建定。

**并且验过这道闸是活的**：临时给 `:shared` 加一个 `okio` 依赖，构建立刻报
`Resolved 'com.squareup.okio:okio:3.9.0' which is not part of the dependency lock state`。

### ✅ 步骤 3 / 4 实测：搬了什么

| 从 | 内容 |
|---|---|
| `:app` | `io.github.nodyssey.model` 全部；`NodeSeekSite`、`StardustReceiveMarkup`、`VoteMarkup`；`core/html` 的 11 个 parser；`core/report` 的 `QualityReport` 与 `QualityReportParser` |
| `:core` | `TerminalColumns`、`ansi/AnsiDecoder`、`richtext/RichNode`、`richtext/Markdown`、`net/SiteError`、`net/SiteConfig`、`net/WebUrl` |

`:core` 对 `:shared` 是 `api`，所以 `:designsys` 和 `:app` 里那些 `io.github.plaza.core.*` 的 import
一行没改。代价是 `io.github.plaza.core.net` 这个包**同时存在于两个模块**里——`SiteConfig` 在
`:shared`，`SiteHtmlClient` 还在 `:core`。这是过渡态而不是终局：步骤 5 之后 `:core` 的网络壳也进
`:shared/androidMain`，包就合回去了。

jsoup → Ksoup 确实是纯机械替换，只改 import，加上 spike 记过的 `TextNode.wholeText` 在 Ksoup 是函数
`getWholeText()`。**jsoup 没能从 `:app` 完全删掉**：`UserSpaceRepository` 还有一行
`Jsoup.parse(raw).text()` 在把 HTML 抽成纯文本做摘要。那是 repository，归步骤 7。

模块边界逼出来的三处改动，都不是清理：

- `SiteBootstrap`、`PostSourceParser` 两个 `internal object` 变 public —— 它们的调用方在 `:app`。
- `VoteCard` 里 `item.count` 要先读进局部变量：`val` 来自另一个模块，编译器不做 smart cast。
- 8 个测试函数名里的逗号要去掉，见下。

### ⚠️ 测试迁移：spike 那张单子不全

21 个测试类进 `commonTest`，175 个测试在 Android host JVM 和 `macosArm64` 上各跑一遍。

**两个故意留在 JVM**（`androidHostTest`）：`WebUrlTest` 对着 `java.net.URI`、`NodeSeekSiteEncodingTest`
对着 `java.net.URLEncoder`，都是差分测试——要保住的不是规范而是那一个实现给出的答案，所以 JVM 必须在场。
后者是从 `NodeSeekSiteTest` 里拆出来的一个测试，类名换了，测试内容一字未改。

fixture 按计划改成 Gradle 任务（`generateFixtureSources`）从 `src/commonTest/resources/fixtures`
生成 Kotlin 常量，按 6000 字符分块且不在代理对中间断开。留在 `:app` 的那几个测试（challenge detector、
两个 repository、回复编辑器）读的还是同一批文件，`app/build.gradle.kts` 把那个 resources 目录加成了
自己的测试资源根——一份文件两个读法，而不是两份文件。

spike 只记了 `assertTrue` 参数顺序。实际还撞到四条：

| 坑 | 处置 |
|---|---|
| **Kotlin/Native 不允许反引号函数名里有 `,`，JVM 允许** | 8 个测试名重写。这条 spike 完全没有——它迁的 41 个测试里恰好没有带逗号的名字 |
| `@Test(expected = X::class)` 是 JUnit 的 | 换 `assertFailsWith<X> { }` |
| `String.toByteArray()` 是 JVM 扩展 | 换 `encodeToByteArray()`，UTF-8 是定义而不是默认值 |
| lint 的 `lintAnalyzeAndroidHostTest` / `generateAndroidHostTestLintModel` 直接读源码目录 | 它们不认 Kotlin source set 上的生成依赖，要按任务名手工 `dependsOn` |

### ⚠️ 顺带发现：`main` 上的单测编译是坏的

`ProxySettingsTest` 在 `5e053fd`（第一阶段）里引用了一个已经不存在的 `context` ——
`DataStoreProxySettings(context, ReversingCipher)`。第一阶段把 `ProxySettings` 改成收 `DataStore`，
而这个测试是 `fabc543`（代理主开关）新加的，两边合到一起之后没有人再跑过
`:app:compileDebugUnitTestKotlin`。已顺手改成 `store.dataStore`。

**这不是这次改动引入的**：在把本次全部改动 stash 掉的干净树上照样复现。

### 构建基础设施的已知障碍

`plaza.dependency-locking` 锁的是 6 个 **Android 专有配置名**
（`debugCompileClasspath` 等），而 KMP 模块的配置名完全不同
（`macosArm64CompileKlibraries`、`commonMainApiElements` …），且 `lockMode` 是 **STRICT**
（「没有 lock state 也算失败」）。直接套用会卡住。

处置二选一，需在该步骤开始时就定：给 KMP 模块补一套配置名（推荐，但必须**实测**哪些真的产生
lock state，照抄猜测的清单在 STRICT 下会失败），或让 `:shared` 不应用这个插件。

**✅ 已按前者做，实测的 7 个配置名见上面的执行记录。**

另：convention plugin 里只用 `//` 注释 —— Kotlin 会嵌套块注释，散文里一个 `/*`
会静默吞掉文件剩余部分，唯一症状是「插件找不到」。

### Apple 侧还能再共享一层

iOS 与 macOS 的 Swift 代码不必各写一套：

```text
AppleFrontendCore/     (Swift Package)
├── model adapters / view models / formatting
└── 共用 SwiftUI 组件

iOSApp/     iOS navigation / lifecycle / iOS 特有 UI
macOSApp/   菜单命令 / 窗口管理 / macOS 特有 UI
```

最终形成两层共享：

```text
                     KMP Core
        Android / iOS / macOS 共享
                       │
           ┌───────────┴───────────┐
      Android UI              Apple Swift layer
       Compose                     │
                             ┌─────┴─────┐
                           iOS         macOS
```

---

## 6. 已验证的资产

三道门槛已于 2026-08-18 实测通过，工程在 `nodyssey-kmp-spike/`。

**在新目标下它们不再是前置条件**，但结论继续有效，第二阶段可直接复用：

| | 结论 | 第二阶段的用途 |
|---|---|---|
| A 会话链路 | macOS WKWebView cookie → `URLSession` 拿到帖子 HTML（HTTP 200，44KB） | Apple transport 有已验证路径；iOS 需复验 |
| B parser | 41 测试两端全绿，jsoup→Ksoup 纯机械替换 | 直接照搬 |
| C Swift 互操作 | Swift Export 导出类型化 `StateFlow`/`Flow`/`suspend` | 证明边界即使上移也可行；当前不依赖 |

生态核对（实测 Google Maven，`macosArm64` 全部就位）：
Room 2.8.4、Paging 3.5.0、DataStore 1.2.1（项目用的是 Preferences，正是 KMP 唯一支持的那种）。

### 门槛 A 顺带发现的一件事，值得现在就复核

站点实际下发 6 个 cookie：`colorscheme`、`fog`、`session`、`pjwt`、`smac`、`cf_clearance`。
而 `SiteConfig.sessionCookieNames = listOf("session", "token")` —— JWT 风格的实际名是 `pjwt`，
没有 `token`。`session` 在场所以登录判定不受影响，但 `token` 可能已是死代码。

---

## 7. 贯穿全程的约束

### 铁律

- 不改表名、不改列名、不清 schema history、不用 destructive migration 解决编译问题
- 不改 `@SerialName` discriminator 或任何持久化过的序列化名 —— 旧缓存要能读
- 迁移 PR 不顺手做命名清理
- 每个 PR 只降低一个平台耦合；同一个 PR 不要既搬代码又改行为

### 依赖有变动时

```bash
./gradlew resolveAndLockAll --write-locks
```

lockfile 是 STRICT 模式，不更新就直接失败。

### 第二阶段的测试迁移坑

JUnit `assertTrue(msg, cond)` 与 kotlin.test `assertTrue(cond, msg)` **参数顺序相反**，
影响每一个被搬进 `commonTest` 的断言。编译期能抓到，不会静默出错，但要计入工期。

fixture 加载要做成 **Gradle 任务**从 resources 生成 Kotlin 常量（且必须分块，JVM 字面量
上限 65535 字节），不要沿用 spike 里的一次性脚本。

### 成功标准

第一阶段：

> `core` / `data` 的公开 API 不出现平台类型；Android 行为与测试数量不变。

第二阶段：

> 同一份 fixture，Android 与 Apple target 解析结果一致；
> Android 现有用户升级后数据完好；每个阶段合并后 Android 行为不变。
