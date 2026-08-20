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

> **2026-08-19 改定**：本节原文写的是「前端永远原生」，Apple 侧 SwiftUI。前端已改走 Compose
> Multiplatform，见 [`cmp-ui-decision.md`](cmp-ui-decision.md)。下图是改定后的形状；Repository
> 以下的判断一个没变。

```text
                        shared / commonMain
        ┌────────────────────┼────────────────────┐
        ▼                    ▼                    ▼
    Android                iOS                 macOS
   Kotlin/JVM         Kotlin/Native        Kotlin/Native
       ART               iosArm64             macosArm64
        │                    │                    │
        └──────── Compose Multiplatform ──────────┘
              一套 UI，各端只留很薄的平台外壳
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

### ~~不共享（永远 Android-only）~~ → 迁移目标（2026-08-19 改定）

```text
:designsys
app/ui
Navigation 3
Compose
```

**这四项已改定为走 Compose Multiplatform**，不再是永久 Android-only。依据与实测核对见
[`cmp-ui-decision.md`](cmp-ui-decision.md)。本节其余部分（Repository 以下的共享边界）不受影响。

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

> **2026-08-19**：本节论证针对 SwiftUI 路线。前端改定为 CMP 后不存在 Swift Export，下述顾虑不再
> 适用，边界**可以**上移到 ViewModel——是否上移留到真正开 Apple 端时决定。见
> [`cmp-ui-decision.md`](cmp-ui-decision.md) 第 3.2 节。

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

### 顺序（2026-08-19 整合版）

原来这里是一张只管后端的表，而 [`cmp-ui-decision.md`](cmp-ui-decision.md) §5 另有一张只管前端的
表，两张互不引用，其中一步还是同一件事的两个说法（那边的「继续拆 `:app`」= 这边的步骤 7）。
**本节现在是唯一的执行顺序**，前端步骤从那份文档并进来；那边只保留改定的理由和实测依据。

前端记作 **B**，后端记作 **A**，两条线汇合之后记作 **D**。

| 步 | 线 | 内容 | 前置 |
|---|---|---|---|
| ✅ 2 | 基建 | `plaza.kmp.library` convention plugin、`:shared` 模块、`appleMain` 层级 | — |
| ✅ 3 | A | model + 纯逻辑 | 2 |
| ✅ 4 | A | parser + Ksoup | 3 |
| ✅ **B1** | B | `:designsys` 去平台耦合。计划记的是 5 个文件，实际 **6 个**——见下 | 无 |
| ✅ **B2** | B | `core/image` 拆三份，`:designsys` 改为直接依赖 `:shared`。不是「下沉」——见下 | B1 |
| ✅ **B3** | B | `:designsys` 转 KMP 模块 + desktop target，单独跑起来。顺带把 B4 的一半做掉了——见下 | B2 |
| ✅ **A5** | A | 网络契约 + Apple transport。`:core` 就此不存在——见下 | B3 |
| ✅ **B4** | B | `:app` 的 `androidx.compose` → `org.jetbrains.compose`，adaptive 换 group。`:designsys` 那半已在 B3 完成。不是纯改名——见下 | B3 |
| ✅ **A6** | A | Room + DataStore。手写 migration 是 **10 个**不是 7 个——见下 | A5 |
| ✅ **A7** | A | Repository 全部下沉。**「拆 `:app` 的 `data/`」：74 个文件剩 16 个**——见下 | A6 |
| ✅ **D1** | D | `ui/` + ViewModel 进 `commonMain`（= 「拆 `:app` 的 `ui/`」），新模块 `:ui`。两笔债都还了，**D2 有一半是它的前置而不是后续**——见下 | A7 + B4 |
| ✅ **D2** | D | ~~1,059 条 strings~~ 1,056 条 + 5 个 drawable → Compose Resources。**在 D1 里做掉了**，理由见下；剩下的 res xml 是 manifest 和图标，本来就不该动 | D1 |
| ✅ **D3a** | D | iOS：`:designsys` 与 `:ui` 开 `iosArm64`，21 个 `expect` 补齐（四个是空壳），WKWebView 桥（`WKHTTPCookieStore` ↔ `NSHTTPCookieStorage` 双向 + observer）。**只到编译为止**——见下 | D1 |
| **D3b** | D | iOS：壳 + 门槛 A 复验 + 生命周期 + IME，以及 D3a 留下的四个图片能力 | D3a |
| **D4** | D | WorkManager → `BGTaskScheduler`（5 个文件） | A7 |

原表的步骤 1「Apple 平台 spike」并入 **D3** —— 门槛 A 已在 macOS 上通过，剩下的 iOS 复验和
WKWebView 桥是同一件活，没必要拆成两步排在两头。原表的步骤 8「Apple 前端」被 B1–B4 + D1 取代。

#### 与两份文档原表的三处差异，以及理由

**一、新增 B2，它是 B3 的真前置。**`cmp-ui-decision.md` §5 把「给 `:designsys` 加 desktop
target」标成「无前置、小赌注」。实际的依赖是：

```text
:designsys ──api──> :core ──api──> :shared
                    plaza.android.library
                    api(libs.okhttp)
```

`:core` 是 Android library 且把 OkHttp 摆在 api 面上，`:designsys` 不可能越过它变成多平台。

**但这个耦合是虚的。**实测 `:designsys` 从 `io.github.plaza.core.*` 只 import 了 4 个符号，
全部在 `core/image/`（422 行）——`ImageLoadFailure`、`diagnoseImageFailure`、`allowMeteredImage`、
`ImagesDeferredException`。richtext 和 ansi 已经在 `:shared` 里了。所以 B2 不是「等 `:core` 多平台
化」，只是把 `image/` 的中性部分下沉：错误分类的**类型**本来就是中性的，`diagnoseImageFailure` 认
`java.io.IOException` / `SocketTimeoutException` 的那半不是。

**二、B3 排在 A5 前面，而不是和后端并行到最后。**A5–A7 是无论如何都要做的活，但它的**形状**取决于
B3：§2 的「为什么不共享 ViewModel」在 CMP 下整体失效（见 [`cmp-ui-decision.md`](cmp-ui-decision.md)
§3.2），边界要不要从 Repository 上移到 ViewModel，依据就是 CMP 到底成不成。B3 是唯一还能证伪 CMP
改定的实验，也是最便宜的一个——不需要 Mac 工具链。先做 B3，A7 才不会按错的口径做一遍。

**三、Compose Resources（D2）从「机械活、早做」挪到汇合点之后。**`cmp-ui-decision.md` §5 把它排在
第 5 步。1,059 条 strings 的迁移在 `:app` 还没拆开时做，等于对着一个马上要整体搬走的目录做机械替换。

#### 顺带纠正：`:designsys` 的耦合面比 §4 数的大

`cmp-ui-decision.md` §4 结尾数的是 import `android.*` 的 4 个文件（`Clipboard.kt`、
`ExternalUriHandler.kt`、`Theme.kt`、`RichContent.kt`）。漏了两个 Android-only 的**依赖**：

| 依赖 | 用在 | 备注 |
|---|---|---|
| `androidx.activity.compose` | `MarkdownEditorBar.kt` 的 `BackHandler` | **第 5 个文件**，它 import 的是 `androidx.activity`，不在那 4 个里 |
| `androidx.browser` | `ExternalUriHandler.kt` 的 Custom Tabs | 与 4 个之一重合 |

`material-color-utilities` 不用担心：坐标是 `com.materialkolor:material-color-utilities`，本来就是
多平台 Kotlin 移植。

~~CMP 侧有没有 common 的 `BackHandler`、Custom Tabs 该换成什么，未验证。~~ → B1 动手前查掉了，
答案在下面的 B1 实测里。

#### 为什么 D1 不可能提前

实测 `app/src/main/.../ui` 的 124 个文件里，**73 个 import `io.github.nodyssey.data`**、77 个 import
`:designsys`，其中 37 个 `ViewModel`。UI 进 `commonMain` 的前置就是 Repository 先进去，没有绕路。

一个对 CMP 有利的现状：项目**没有 Hilt / Koin**，依赖装配是手写的 `di/AppContainer.kt`。
KMP 里最常见的那个 DI 障碍在这里不存在。

### ✅ B1 实测：`:designsys` 去平台耦合

平台耦合现在只出现在四个以平台命名的文件里，与 §4.1 给 `:core` 做的是同一件事、同一个理由——
KMP 里一个文件不能一半 `commonMain` 一半 `androidMain`，拆过之后 B3 是移动文件而不是改写。

| 文件 | 承担什么 |
|---|---|
| `component/AndroidClipboard.kt` | 把纯文本包成 `ClipEntry`；Android 13 以下补一个 Toast |
| `component/AndroidBackHandler.kt` | `PlazaBackHandler` 委托给 `androidx.activity.compose.BackHandler` |
| `component/AndroidCustomTabUriHandler.kt` | Custom Tab 的 `UriHandler`（原 `ExternalUriHandler.kt`，只改名） |
| `theme/AndroidSystemColorScheme.kt` | 动态取色，取不到时返回 null |

公开 API 一个没动，`:app` 侧零改动。门禁全绿：`testDebugUnitTest` 1,424 个测试 0 失败、
`:app:assembleDebug`、`spotlessCheck`、`:app:lintDebug`。测试一个字没改。

#### 动手前查掉的三个未知项（artifact 比对，不查文档）

| 问题 | 答案 |
|---|---|
| CMP 有没有 common 的 `BackHandler`？ | **有**，`org.jetbrains.compose.ui:ui-backhandler` 1.12.0-rc01 的 `commonMain` 里是 `expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)`，与 `androidx.activity.compose` 的签名一字不差 |
| androidx 有没有同一个 artifact，好让 B1 只改 import？ | **没有**。`androidx.compose.ui:ui-backhandler` 在 Google Maven 上是 HTTP 404。所以必须自己留一层 `PlazaBackHandler`，B4 时把它的实现换成 CMP 的 |
| 剪贴板在 common 能做到哪一步？ | `Clipboard.setClipEntry` 是 common 的，**但 `ClipEntry` 是 `expect class` 且 common 里没有任何纯文本工厂**（在 CMP 的 `ui` sources 里 grep 不到 `withPlainText` 之类）。造剪贴板内容天生是平台活，这一层拆不掉 |

顺带确认两件让 B1 变便宜的事：`coil3.compose.LocalPlatformContext` 是 `commonMain` 的 `expect val`，
所以三处 `LocalContext` 只是为了喂 `ImageRequest.Builder`，直接换成 Coil 自己的 common API 就没了；
`androidx.annotation` 有真 Apple 变体（`iosArm64` / `macosArm64` 都在），`@VisibleForTesting` 不用动。

#### 「数 `import android.*` 的文件」这个口径本身会漏

计划说 5 个文件，实际 6 个。多出来的是 `component/WrapTable.kt` —— 它用 `LocalContext`，而
`LocalContext` 的包名是 `androidx.compose.ui.platform`，不是 `android.*`。`RichContent.kt` 里同样的
两处也是这么藏着的。**统计平台耦合不能只 grep `^import android\.`**，Android-only 的 androidx 构件
和只在 Android 上存在的 CompositionLocal 都不长那个样子。

#### 一处刻意的行为差异

`android.util.LruCache` 每个方法都 synchronized，接替它的 `NaturalImageSizes`（`LinkedHashMap`，
读的时候重新插入，所以队首永远是最久没用的）**不是**。两个调用点都在 composition 里——给
`BlockImage` 打底的那个 `remember`，和 Coil 把结果送回来的 `onSuccess`。别处要用得自己带锁，
KDoc 里写了。

#### 留给 D2 的一处

`editor/EmojiPanel.kt` 的 `@StringRes`。它来自 `androidx.annotation`（多平台，本身不是问题），
但它标注的是 Android 资源 id ——这条随 Compose Resources 一起解决，不属于 B1。

### ✅ B2 实测：`core/image` 拆三份，不是下沉

计划这一步写的是「`core/image` 下沉」，实测下来它该拆开：包里四个文件的受众不是一拨人。

| 去处 | 内容 | 为什么 |
|---|---|---|
| `:designsys/image/` | `ImageLoadFailure` + `diagnoseImageFailure`；`AllowMeteredImage` / `allowMeteredImage` / `ImagesDeferredException` | 这是图片加载器与 UI 之间的**词汇**：占位图被点一下就设那个 extra，组件读那个异常才知道是「跳过了」而不是「坏了」 |
| `:app/image/` | `ImageNetworkPolicyInterceptor` | 执行偏好设置是 app 的事，而且全仓只有 `NodysseyApp` 构造它 |
| `:app/platform/` | `CompatSvgParser`、`hasValidatedUnmeteredNetwork` | 平台外壳，和 §4.1 把 `KeystoreSecretCipher` 搬进 `platform/` 是同一个做法 |

`:designsys` 的 `api(project(":core"))` 因此换成 `api(project(":shared"))`，B2 的目标达成——
`:designsys:dependencies` 的 `debugCompileClasspath` 里现在只有 `project ':shared'`。

#### 为什么没有真的下沉到 `:shared`

因为 `diagnoseImageFailure` 认的是 `java.net.SocketTimeoutException`、`UnknownHostException`、
`java.io.InterruptedIOException`——三个在 common 里没有对应物的 JVM 异常（`java.io.IOException`
有 `kotlin.io.IOException` 顶，另外三个没有）。下沉就得先做一层 expect/actual，还得让 `:shared`
带上 coil。

而 **B3 的闸是 desktop，desktop 就是 JVM**，`java.net.*` 在那儿根本不是问题；这三个异常真正拦路是
在 D3 开 Native 的时候。按「不赌的先做」和 §4.4「不要过度抽象」，那层 expect/actual 留到需要它的
那一步。反过来说也成立：**B3 若失败、CMP 不做了**，现在这个拆法仍然是净收益（`:core` 少了 422 行
和一个它不该有的图片库依赖），而下沉方案会给 `:shared` 留一个白挂的 coil 依赖。

#### `:core` 因此空掉的东西

`core/image` 是 `:core` 里唯一用 coil 的地方，也是唯一需要 Robolectric 的地方。搬完之后
`api(coil-core)` / `implementation(coil-network-core)` / `api(coil-svg)` 三个依赖、
`robolectric` + `androidx-test-core` + `androidx-test-ext-junit` 三个测试依赖，以及
`src/test/resources/robolectric.properties`，全部无人使用，一并删掉。**`:core` 现在一个
Robolectric 测试都没有了。**

#### 一个坑：搬测试要连资源一起搬

`CompatSvgParserTest` 搬进 `:app` 之后报 `Missing fixture: check-place-ip-report.svg`——
它通过 classloader 读 `fixtures/`，而那个 SVG 还在 `core/src/test/resources/`。编译期抓不到，
只有跑起来才知道。`:core` 剩下的三个 `update-*.json` 是 update 测试的，留在原地。

测试总数不变：`:app` 1,095 + `:designsys` 108 + `:core` 40 + `:shared` 181 = **1,424**，与 B1 后一致，
0 失败。

### ✅ B3 实测：`:designsys` 转 KMP + desktop，`:gallery` 跑起来了

**结论：CMP 成立，没有被证伪。**`:designsys` 6,600 行 Compose 在桌面 JVM 上编译、链接、开窗、
绘制，`:app` 的 Android 侧一行业务代码没改。这一步的目的就是「唯一还能证伪 CMP 改定的实验」，
它没证伪。

证据有三条，从弱到强：

| | 是什么 |
|---|---|
| `:designsys:compileKotlinJvm` | 编译得过 |
| `:designsys:jvmTest` 58 个测试 | `commonTest` 的那部分在桌面 JVM 上和在 Android host 上跑出同样的结果 |
| `:gallery:run` / `:gallery:jvmTest` | **画得出来**。新模块 `:gallery` 是个桌面窗口，`./gradlew :gallery:run` 打开它；三个 `runComposeUiTest` 测试是同一份内容的无头版本，CI 跑的是这个 |

`:gallery` 单列一个模块而不是在 `:designsys` 里塞一个 `main`：库的多平台变体是给**消费方**用的，
这个消费方解析变体的方式和将来的 Apple App 一模一样。它不发布、没人依赖，CI 只编译加跑测试。

#### 计划说「移动文件而不是改写」，对了一半

B1 拆出来的四个平台文件确实只是移动，一个字没改（`AndroidCustomTabUriHandler` 进 `androidMain`，
另外三个加 `actual`）。计划没算到的是**依赖侧**：

**一、`androidx.compose` → `org.jetbrains.compose` 是 B3 的必要条件，不是 B4 的独立步骤。**
`commonMain` 只能有一套 Compose artifact，而 androidx 的那套没有 JVM 变体。所以 B4 表述里
「`:designsys` 换 group」这半件事在这里就做完了，B4 剩下的是 `:app` 和 adaptive。

代价比预期小：CMP 的 Android artifact 是**指针**——`org.jetbrains.compose.ui:ui` 的 android
变体依赖 `androidx.compose.ui:ui`，同版本号。`:app` 因此从 `androidx.compose.ui:ui:1.12.0-beta01`
走到 `1.12.0-rc01`（它本来就不在 BOM 的 1.11.3 上，是 material3 1.5.0-alpha24 把它顶上去的），
material3 停在 1.5.0-alpha24 不动——CMP 的 material3 只要求 1.5.0-alpha22，输给了本仓库的 alpha24。

版本号也要更正 [`cmp-ui-decision.md`](cmp-ui-decision.md) §2 的口径：Maven Central 上
`org.jetbrains.compose` 主线已经到 **1.12.0-rc01**，而 material3 是单独一条版本线，最新仍是
**1.12.0-alpha03**——就是那份文档比对过的那一个。§2 的结论不用改，版本号本身要改。

**二、`:shared` 得跟着加 `jvm()`。**`:designsys` 的 jvm target 通过 `api(project(":shared"))`
解析 `:shared`，而一个依赖没有的 target 就是一个解析不出来的变体，报的是三十行
「No matching variant」。`commonMain` 一个字没改就通过了——这反过来是第一阶段那条「`:shared` 里
不许知道自己在 Android 上」的验收。顺带 `:shared:jvmTest` 也有了：175 个 common 测试现在在桌面
JVM 上再跑一遍。

**三、44 条 strings 要走 Compose Resources。**`src/main/res/values/strings.xml` 原样搬到
`src/commonMain/composeResources/values/`，`R.string.x` → `Res.string.x`，10 个文件。三处连带改动：

- `EmojiGroup.titleRes: Int` → `title: @Composable () -> String`。资源 id 是 Android 传文案的
  机制，而 `EmojiGroup` 是 `:app` 在 composition 外面构造的顶层 `val`。改成 `String` 会逼
  `NodeSeekEmojiGroups` 变成 composable 函数并牵动它的测试；lambda 保住了惰性又不用点名谁的资源系统。
- `EditorAction.labelRes: Int` → `label: StringResource`。只在 `:designsys` 内部用，纯改类型。
- `:app` 那四行 `EmojiGroup(R.string.x, …)` 加一层 `{ stringResource(...) }`。**这是 B3 对 `:app`
  的全部改动。**

好处是顺带的：`:designsys` 的字符串不再进资源合并表，`:app` 再也不可能悄悄覆盖它们——CI 里那条
「两个模块声明同名资源」的检查从此对这个模块无事可做。

**四、`jvmCommonMain` 这一层是必要的。**`java.net.SocketTimeoutException`（B2 留下的
`diagnoseImageFailure`）和 `java.text.BreakIterator`（emoji 面板的退格要按字素簇走）不是 Android
的东西，是 JVM 的东西，两个 target 的答案一模一样。不给它们一个中间 source set，两个 `actual`
就是同一个文件抄两遍。`applyDefaultHierarchyTemplate { common { group("jvmCommon") { withAndroid(); withJvm() } } }`——
手写 `dependsOn` 会把整个默认模板关掉，连 `androidHostTest` 一起。

**五、`ImagesDeferredException` 从 `java.io.IOException` 换成 `okio.IOException`。**JVM 上后者是前者
的 typealias，所以 Android 侧 catch 什么一点没变，而 `commonMain` 里能直接写。okio 本来就通过 coil
在依赖图里。

**六、`PlazaBackHandler` 变成 common 的了。**B1 那个文件的注释预言过两种结局，落地的是好的那个：
CMP 的 `androidx.compose.ui.backhandler.BackHandler` 是 commonMain 的，Android 上仍然接到同一个
`OnBackPressedDispatcher`。`androidx.activity.compose` 这个依赖因此从 `:designsys` 删掉了。

#### 六个坑，其中三个的症状都不指向原因

| 坑 | 症状 |
|---|---|
| KMP Android library 默认 **`androidResources.enable = false`** | Compose Resources 把 `.cvr` 当 **asset** 打包，方式是向 variant 要 `sources.assets`，而这个开关关着时它是 null。报出来的是 `copyAndroidMainComposeResourcesToAndroidAssets` 的配置校验失败——「property 'outputDirectory' doesn't have a configured value」。一个没人接收输出的任务，报的是它没有输出 |
| Robolectric + Compose Resources | 上一条不修，108 个测试里 21 个挂在 `MissingResourceException: … Android context is not initialized`，而问题跟 context 无关，是那个文件根本没打进 assets |
| `platform(...)` 在 KMP 的 `sourceSets.xxx.dependencies {}` 里不存在 | `Unresolved reference 'platform'`。要写 `project.dependencies.platform(...)` |
| `applyDefaultHierarchyTemplate {}` 的自定义重载仍是 experimental | 脚本编译失败，要 `@file:OptIn(ExperimentalKotlinGradlePluginApi::class)` |
| `:gallery` 不能用 `alias(libs.plugins...)` 应用 Kotlin multiplatform | `build-logic` 已经把 KGP 放在根构建的 classpath 上，再按版本请求一次会失败为重复请求。用 `id("org.jetbrains.kotlin.multiplatform")`，版本仍然出自版本目录 |
| `@Preview` 的 artifact 换了，包名没换 | 源码里的 `androidx.compose.ui.tooling.preview.Preview` 一个字不用改，换成 `org.jetbrains.compose.ui:ui-tooling-preview` 即可。**不要**换成 `org.jetbrains.compose.components:components-ui-tooling-preview`——它里面那个 `org.jetbrains.compose.ui.tooling.preview.Preview` 是前者的已弃用前身，换过去编译器会直接告诉你换回来 |

#### 两条留给后面的

- **Skiko 版本冲突。**Coil 3.5.0 要 skiko `0.144.6`，CMP 1.12 带的是 `0.150.1`，解析取高的那个，
  构建期有一行 warning。桌面端 Coil 的解码路径因此跑在一个它没编译过的 Skia 上。目前没有观察到
  问题（`:gallery` 的图片占位组件正常），但这是 desktop 独有的风险，Coil 跟上 CMP 之前一直在。
- **`BackHandler` 在 CMP 1.12 已弃用**，提示换 `NavigationEventHandler`（`androidx.navigationevent`
  已经在依赖图里）。那是 predictive back 的新 API，换过去是 Android 侧的行为变化，不属于这一步。
  归 B4 / D1。

#### 构建约定的一处调整

`plaza.kmp.library` 不再声明 target。**声明哪些 target 是模块自己的决定**——`:shared` 回答的是
Paging 没有 `macosX64`，`:designsys` 要 desktop 是因为 JVM 是证明「离开 Android」最便宜的地方——
所以两个 `iosArm64()` / `macosArm64()` 连同那段理由搬回 `shared/build.gradle.kts`，约定插件里留下
的是每个 KMP 模块都一样的东西：Android target、lint、锁定、toolchain。

`plaza.dependency-locking` 的锁定清单加了 `jvm{Compile,Runtime}Classpath` 和
`jvmTest{Compile,Runtime}Classpath` 四个。

#### 测试

`:designsys` 的 108 个测试一个没少，其中 58 个搬进了 `commonTest` / `jvmCommonTest`，于是在 Android
host 和桌面 JVM 上各跑一遍。搬的时候撞到的还是那条老坑：**JUnit 的 message 是第一个参数，
`kotlin.test` 的是最后一个**——`assertTrue(msg, cond)` → `assertTrue(cond, msg)`，
`assertEquals(msg, expected, actual, delta)` → `assertEquals(expected, actual, delta, msg)`。8 处。

留在 `androidHostTest` 的 50 个是 Robolectric + `createComposeRule` 的那批：`ui-test-manifest`
提供的是 `createComposeRule` 启动的那个 Activity，是一份 Android manifest，没有多平台对应物。
把它们改写成 `runComposeUiTest` 是可以的，但那是重写测试而不是搬运，不在这一步。

总数：`:app` 1,095 + `:designsys` 108（androidHostTest）+ `:core` 40 + `:shared` 181 = **1,424**，与 B2 后
一致；桌面端新增 `:designsys` 58 + `:shared` 175 + `:gallery` 3 = **236** 次执行，全部 0 失败。

### ✅ B4 实测：`:app` 换 group，以及三处会被悄悄降级的依赖

五行 `libs.androidx.compose.*` 换成 `libs.compose.*`，`adaptive-navigation3` 换到
`org.jetbrains.compose.material3.adaptive`，源码一个 import 没动——这半是预期内的。**预期外的是：
换 group 把「用哪个 androidx 版本」的决定权交给了指针，而这个仓库跑在比指针更新的版本上。**

#### 指针里是空的

`org.jetbrains.compose.ui:ui` 的 android 变体是个 6KB 的 aar，`classes.jar` **273 字节**，什么类都
没有；aar 里唯一有内容的是一条依赖 `androidx.compose.ui:ui`，和一份写着「本库可以安全 shrink」的
空 proguard 规则。所以换 group 不改变编译时看见的任何一个类——但它改变*看见哪个版本*的类。

（**一个例外，见下面的「`ui-tooling` 留在 androidx」**：空 `classes.jar` 不等于空 aar，manifest
是它们能带的另一样东西。）

三处指针落后于版本目录：

| 坐标 | 指针要 | 本仓库在 |
|---|---|---|
| `androidx.compose.material3:material3` | 1.5.0-alpha22 | 1.5.0-alpha24 |
| `androidx.compose.material3.adaptive:*` | 1.3.0-beta02 | 1.3.0-rc01 |
| `androidx.compose.material:material-icons-core` | 1.7.6 | 1.7.8 |

不写一句话，B4 就是三个库的静默降级——而 AGENTS.md 里「Material 3 停在 1.5 的 alpha，是因为这些
API 只在那儿是公开的」正是不能降的那条。

处置用 `constraints` 而不是 `dependencies`：模块**名字**上仍然只有多平台坐标。**一个 group 一行就
够**，因为 androidx 在每个 module 元数据里都发布了对同组兄弟的 constraint——实测
`material3-android-1.5.0-alpha24.module` 里有 `material3-adaptive-navigation-suite`、
`material3-ripple`、`material3-window-size-class`、`material3-lint` 四条，
`adaptive-navigation3-android-1.3.0-rc01.module` 里有 `adaptive`、`adaptive-layout`、
`adaptive-navigation` 三条。

#### `ui-tooling` 留在 androidx——空 `classes.jar` 不代表这个 aar 是空的

`org.jetbrains.compose.ui:ui-tooling` 的 `classes.jar` 同样是 275 字节的空壳，但它的 aar 里还有一份
**AndroidManifest**，声明 `org.jetbrains.androidx.compose.ui.tooling.PreviewActivity`——而这个类它
自己不提供，整个依赖图里也没有（grep 过打出来的 dex，0 处）。换过去的后果是 debug 的合并 manifest
里多一个 `android:exported="true"` 的 activity 指向一个不存在的类。

所以这一行改回了 androidx，理由归到和两个测试构件同一类：**带 AndroidManifest 的构件留在 androidx**，
因为 manifest 正是多平台构件没有对应物的那样东西。换过去也没有任何好处——类反正都从底下的 androidx
来，而 `ui-tooling` 是 `debugImplementation`，从来不进 release。

这一处是**先换了、比对产物时才发现、然后撤回**的。下一节那张「行为不变」的表是撤回之后测的。

#### 锁文件就是「Android 行为不变」的证明

`resolveAndLockAll --write-locks` 之后，`app/gradle.lockfile` 解析出的 316 个模块里**没有一个版本
变化，也没有一个消失**。新增 9 条：CMP 的 navigation-suite 与 adaptive 指针，加上 adaptive 拖进来的
三个 JetBrains 镜像，每一个都输给图里已有的 androidx：

| 新增 | 图里已有的 | 结果 |
|---|---|---|
| `org.jetbrains.androidx.navigation3:navigation3-ui:1.1.0` | `androidx.navigation3:navigation3-ui:1.1.4` | 1.1.4 |
| `org.jetbrains.androidx.navigationevent:navigationevent-compose:1.0.1` | `androidx.navigationevent:navigationevent-compose:1.1.2` | 1.1.2 |
| `org.jetbrains.androidx.window:window-core:1.5.0` | `androidx.window:window-core:1.5.0` | 同版本 |

唯一的减法是 `compose-bom` 从 `releaseCompileClasspath` / `releaseRuntimeClasspath` 上消失。

**但锁文件只证明图没变，不证明产物没变**，所以又比了一次装出来的东西——`git stash` 出一个 HEAD 的
debug APK，和改动后的逐项对：

| | 改前 | 改后 |
|---|---|---|
| zip 条目 | 238 | 238，无增无减 |
| 合并后的 AndroidManifest | | **零差异** |
| dex 里定义的类 | 30,199 | 30,200 |
| dex 里定义的方法 | 184,100 | 184,101 |

多出来的那**一个类**是 `org.jetbrains.androidx.compose.material3.adaptive.navigationsuite.R`，一个空
资源类加它的默认构造函数——指针 aar 带了自己的包名和一份空的 `res/values/values.xml`，AGP 就给它
生成一个 `R`。release 里 R8 把它去掉了（`base/dex` 里 0 处引用）。除此之外每一个类、每一个方法都
逐个对得上。

这一步的正确验证是**比类和 manifest**，不是比包大小：APK 总字节数改前改后恰好相同，但那是压缩碰巧
抵消，不构成证据。

#### BOM 还剩什么用

不再是 `implementation`。它留在 `debugImplementation` / `testImplementation` /
`androidTestImplementation` 上，只为 `ui-test-junit4` 和 `ui-test-manifest` 供版本——就是 B3 里
`:designsys` 留下的同两个，同一个理由：`ui-test-manifest` 提供的是 `createComposeRule` 启动的那个
Activity，是一份 Android manifest，没有多平台对应物。

#### CMP 的版本线现在是三条

`composeMultiplatform = 1.12.0-rc01` 主线、`composeMultiplatformMaterial3 = 1.12.0-alpha03`，再加新
的 `composeMultiplatformAdaptive = 1.3.0-beta02`。navigation-suite 不在第三条上，它跟 material3 走
（`org.jetbrains.compose.material3:material3-adaptive-navigation-suite`）。

#### 没做的两件事，以及为什么

- **Navigation 3 和 lifecycle 仍是 androidx。**它们不是 `androidx.compose.*`，而真正需要它们多平台
  的是 `ui/`，那是 D1。顺带一提这一步已经把 `org.jetbrains.androidx.navigation3` 的镜像拖进依赖图
  了（CMP adaptive 要它），D1 换过去时它不是新面孔。
- **B3 留下的 `BackHandler` 弃用没有在这里换。**`NavigationEventHandler` 是 predictive back 的行为
  变化，而 B4 全程没换过一个类。按 §7「同一个 PR 不要既搬代码又改行为」，整条归 D1。

顺带更正 B3 写下的一处理由：`PlazaBackHandler` 的 KDoc 说它还在，是因为「消费方还在 androidx 上，
点不到这个名字，而 `:app` 就是这样一个消费方」。B4 之后 `:app` 不在 androidx 上了，这条理由作废。
它现在存在的理由是 `ui-backhandler` 在 `:designsys` 是 `implementation`，不在任何别人的编译路径上；
而全仓库只有一个调用点，就在 `:designsys` 自己内部——真要删它，那是独立一步。

#### 门禁

`spotlessCheck`、`testDebugUnitTest testAndroidHostTest jvmTest`、`:app:lintDebug`、
`:app:assembleDebug`、`:app:bundleRelease` 全绿。测试数一个没变：`:app` 1,095 + `:designsys` 108 +
`:core` 40 + `:shared` 181 = **1,424**，桌面端 58 + 175 + 3 = **236**。

`:app:lintAnalyzeDebugUnitTest` 第一次跑挂在 `OutOfMemoryError: Metaspace`，报出来的是「lint 或它
依赖的库有 bug」。是 daemon 的事而不是这次改动的事：`./gradlew --stop` 之后重跑即过。

### ✅ A5 实测：`HttpTransport`，以及 `:core` 的消失

计划这一行写的是「网络契约 + Apple transport」。契约落成 `io.github.plaza.core.net.HttpTransport`：
一次请求、一个回答，签名里没有任何平台类型。`SiteHtmlClient` 和 `NodeSeekJsonClient` 都改成对着它
写，因此**整个进了 `commonMain`**；OkHttp 是它在 `androidMain` 的实现，`NSURLSession` 是它在
`appleMain` 的实现。

```text
                    commonMain
        HtmlSource / JsonApi  ← 站点问什么
              HttpTransport   ← 平台答什么
        ┌───────────┴───────────┐
   OkHttpTransport      NSUrlSessionTransport
     androidMain              appleMain
```

`:core` 因此空掉并**删除**。这不是顺手清理，是这一步的定义：`:core` 存在的理由就是「网络壳是
Android 的」，契约下沉之后那句话不再成立。包名一个没改（`io.github.plaza.core.*` 原样搬进
`:shared`），所以 `:app` 和 `:designsys` 的 import 一行没动。

| 去处 | 内容 |
|---|---|
| `commonMain` | `AppClock`、`AppDispatchers`、`Coroutines`、`AppVersion`；`net/` 的 `AcceptLanguage`、`UserAgent`、`ChallengeDetector`、`MinIntervalGate`、`SessionCookieStore`、`SiteHtmlClient`，新写的 `HttpTransport` 与 `SessionCookies`；`update/` 的三个纯文件；`:app` 搬来的 `NodeSeekJsonClient` |
| `androidMain` | `OkHttpTransport`（新）、`WebViewCookieJar`（只剩翻译）、`WebViewCookieStore`、`BrowserHeadersInterceptor`、`CrossOriginRefererInterceptor`、`DeviceAcceptLanguage`、`WebViewUserAgent`、`AndroidAppVersion`、`UpdateManifestSource` |
| `jvmCommonMain`（新） | `TimeFormat`（`java.time`）、`Platform.jvmCommon.kt` |
| `appleMain`（新） | `NSUrlSessionTransport`、`appleUrlSession`、`AppleCookieStore`、`Platform.apple.kt` |

#### 四处不是搬家的改动

**一、`WebViewCookieJar` 一分为二。**它原来同时是 OkHttp 的 `CookieJar` **和**「什么算登录」的
判断者，而后者才是值钱的部分——哪些 cookie 名算 session、哪些是 Cloudflare 噪声、指纹算不算它们。
那半变成 `commonMain` 的 `SessionCookies`，`SessionRepository` 收的是它；`WebViewCookieJar` 只剩
`Set-Cookie` 字符串和 OkHttp `Cookie` 之间的翻译，留在 `androidMain`。第一阶段 §4.4 记的
「`SessionCookieStore` 新写」是这条线的上一半，这是下一半。

**二、`acceptLanguage` 不再收 `java.util.Locale`。**改收语言标签字符串，bare language 取第一个
子标签——BCP 47 就是这么定义的。读设备偏好语言那半留在 `androidMain`（`LocaleList`）。一个坑：
`LocaleList` **自己就有** `toLanguageTags()`，返回逗号拼好的**一个** String，同名扩展会被它悄悄
吃掉，症状是「实参 String，形参 List<String>」——报在调用处而不是定义处。

**三、`Dispatchers.IO` 在 Kotlin/Native 是 `internal`。**coroutines 1.11 里只有 JVM 那份是 public。
`ioDispatcher()` 因此是 expect/actual，Apple 侧 actual 成 `Dispatchers.Default`——今天成立的理由是
Apple 这侧没有任何东西阻塞线程（`NSURLSession` 是回调式的）。**A6 把 Room 搬过来那天这条就不成立
了**，到时候要的是本模块自己的线程池，不是改这一行的名字。`System.currentTimeMillis()` 同样是
expect/actual，Apple 侧走 `NSDate`。

**四、`x-dynamic-sign` 没有跟着下沉。**`DynamicSignInterceptor` 是个 OkHttp interceptor，留在
`:app`。把它做成公共的意味着 commonMain 里要有 SHA-1——Kotlin 没有公共的 crypto API，两个平台的
C 函数都已 deprecated，剩下的选择是手写一份摘要算法。按「不要过度抽象」这条没做。**代价记在这里**：
Apple 侧今天调 `/api/vote/*` 会拿到 403，这是 D3 的活。

**五、Apple 的 cookie 桥没有做，而且它和 Android 不是同一个形状。**这条在 #101 的 review 里被点
出来，点得对——原来的 KDoc 写着「`WKWebView` 登录后 cookie 落进
`NSHTTPCookieStorage.sharedHTTPCookieStorage`，门槛 A 测的就是这个」，**这句是编的**。门槛 A 实际
测的（见 `docs/kmp-migration-decision.md` §4）是**手动**把 cookie 从 `WKHTTPCookieStore` 读出来交给
`URLSession`，证明的是「同一份 cookie 能用」，不是「两个 jar 自己会同步」。

Android 之所以简单，是因为 `CookieManager` **同时**是 WebView 写的地方和 OkHttp 读的地方，
`WebViewCookieJar` 因此纯粹是翻译。WebKit 没有这样一个共用 jar：`WKWebView` 写的是
`WKWebsiteDataStore.httpCookieStore`，`NSURLSession` 读的是 `NSHTTPCookieStorage`，两者是两个东西，
而且前者的 API 是异步的——`SessionCookieStore.cookieHeader()` 这个同步签名在 Apple 侧只有在「桥维护
一份镜像」的前提下才成立，不能改成直接问 WebKit。

因此 A5 交付的是**只有 `URLSession` 那一侧**。桥（双向复制 + `WKHTTPCookieStoreObserver` 跟住 live
challenge）是 **D3**，也就是 `WKWebView` 第一次存在的那一步。今天没有任何地方 new 出
`AppleCookieStore` 或 `appleUrlSession`，所以这不是一个线上缺陷，但它曾经是一句会被 D3 直接采信的
错话。`AppleCookieStore` 的 storage 参数顺手去掉了默认值，逼 D3 明确说自己要的是哪个 jar。

#### 测试

`:core` 的 40 个测试跟着走：`AcceptLanguageTest` 进 `commonTest`（因此两端各跑一遍），其余六个类
进 `androidHostTest`——`TimeFormat` 是 `java.time`，两个 interceptor 测试是 OkHttp，update 那三个
读 `resources/` 里的 JSON fixture。新增 3 个（`DeviceAcceptLanguageTest` 拆出 Locale→tag 那半，
外加一个 script 子标签的用例）。

总数：`:app` 1,095 + `:designsys` 108 + `:shared` 224 = **1,427**（此前 1,424）；
桌面端 58 + 182 + 3 = **243**，`macosArm64Test` **182**（此前 175）。

#### 门禁

`assembleDebug` / `testDebugUnitTest testAndroidHostTest jvmTest` / `:app:lintDebug` /
`spotlessCheck` / `:shared:macosArm64Test` / `:gallery:jvmTest` 全绿。锁文件重生成：`:core` 的删掉，
`:shared` 多了 coroutines 的四个平台变体和 okhttp 的 Android 那份。

---

### ✅ A6 实测：Room 整个进 commonMain，schema 一个字节没变

计划写的是「7 个手写 migration 要重写」。实际是 **10 个**（3→4 一直到 12→13），而重写的内容比
「重写」这个词轻：`migrate(db: SupportSQLiteDatabase)` 变 `migrate(connection: SQLiteConnection)`，
`db.execSQL` 变 `connection.execSQL`，**SQL 字符串一个字都没动**。

`data/local/` 的 13 个文件整体进 `commonMain`，一个都不用留在 Android 侧——因为 DAO 的每个函数本来
就是 `suspend`、`Flow` 或 `PagingSource`，没有一个是阻塞查询。这不是运气，是第一阶段那条
「不要在 data 层写阻塞调用」的规矩顺手买到的。

| 去处 | 内容 |
|---|---|
| `commonMain` | `NodeSeekDatabase` + 10 个 migration、9 个 DAO、两组 entity、`RichContentConverters`；从 `CategoryRepository.kt` 里拆出来的 `Board` |
| `androidMain` | `createNodeSeekDatabase(context)`——原 `platform/NodeSeekDatabaseFactory.kt` |
| `appleMain` | `createNodeSeekDatabase()`（Application Support 目录 + `BundledSQLiteDriver`）、`createPreferenceDataStore(name)` |
| `shared/schemas/` | 13 个 schema JSON，从 `app/schemas/` 整体搬来 |

**验证「Android 行为不变」的那一条是 schema JSON**：处理器在新模块里重新生成之后，
`13.json`、`12.json`、`11.json` 与搬家前逐字节相同——identityHash 没变，所以装机的库不会被当成
另一个 schema。10 个 migration 测试也全绿。

#### 五处必须知道的

**一、`@ConstructedBy` + `expect object`。**`@Database` 类在 `commonMain`，实现是每个 target 一份，
`commonMain` 只能指着一个 `expect object`。**四个 `actual` 全部由 Room 的 KSP 生成**，手写一个是
这里最容易犯的错；「expected object has no actual declaration」的真实含义是「那个 target 没跑
处理器」。

**二、`dependencies { add("kspAndroid", …) }` 必须写在 `kotlin { }` 之后。**这些配置名是声明 target
时创建的，写在前面直接报 `Configuration with name 'kspJvm' not found`——报的是配置名，不提 KMP，
也不提顺序。

**三、Android 侧故意不指定 driver。**`Room.databaseBuilder(context, …)` 默认走 Android 自带的
SQLite，也就是每台装机设备现在正在跑的那个。在这里写上 `BundledSQLiteDriver`（Apple 侧别无选择的
那个）等于把一个活着的数据库文件底下的 SQLite 实现换掉——那是行为变更，不是构建细节。

**四、`room-ktx` 没有跟着来。**它是 Android 专有的协程适配层，多平台 runtime 自带同样的 `suspend`
支持。**但 `androidx.room.withTransaction` 就在里面**，而 `PostRepository` 和 `FeedRemoteMediator`
还在用它——这两个文件因此还在 `:app`，归 A7。

**五、迁移测试留在 `:app`。**Room 的 Android `MigrationTestHelper` 通过 `AssetManager` 读 schema，
而 KMP 模块的 host test 没有配 assets 目录的地方。`:app` 的 debug assets 改成**指向**
`shared/schemas`（和 fixture 那处同样的理由：两份 100KB 的拷贝会 drift，而 drift 的总是失败的测试
没在读的那一份）。代价是 `MIGRATION_*` 与 `NODESEEK_MIGRATIONS` 从 `internal` 变 public——顺带也是
`appleMain` 那个 factory 需要的。

#### DataStore 这半

`androidx.datastore:datastore-preferences` 本来就是多平台的，`DataStore<Preferences>` 里没有一个
Android 类型，所以这一半不需要改写任何东西：加依赖，加一个 `appleMain` 的
`createPreferenceDataStore(name)`。Android 那半（`di/PreferenceStores.kt` 的 `Context` 扩展）留在
`:app`——`preferencesDataStore` 就是个 `Context` 扩展，「文件放哪」正是平台唯一贡献的东西。
两边用同一批文件名（`settings`、`proxy`、`offline`…），Android 那侧这些名字是装机文件名，改不得。

#### 一个 lint 门禁的松动

`:app:lintDebug` 开始报 `RestrictedApi`，位置在 `shared/build/generated/ksp/.../BoardDao_Impl.kt`：
Room 生成的代码调用 `@RestrictTo(LIBRARY_GROUP)` 类型，这是 Room 的设计。以前不报，是因为 AGP 会把
**被 lint 的那个工程自己**的 KSP 产物标成 generated 并跳过；KMP 模块通过 `checkDependencies` 贡献
的 lint model 不标，同样的生成代码就被当成普通源码读了。`lint { checkGeneratedSources = false }`
不管用（默认本来就是 false，问题在于 lint 不知道那是生成的）。处置是 `lint.xml` 里按**路径**豁免
`build/generated/ksp/`——人写的代码里出现 `@RestrictTo` 违规照样挂。

#### 测试与门禁

测试总数不变：`:app` 1,095（含 10 个迁移测试）+ `:designsys` 108 + `:shared` 224 = **1,427**。
`assembleDebug` / `testDebugUnitTest testAndroidHostTest jvmTest` / `:app:lintDebug` /
`spotlessCheck` / `:shared:macosArm64Test` / `:gallery:jvmTest` 全绿，Room 与 DataStore 在
`iosArm64`、`macosArm64` 上都编译通过。

---

### ✅ A7 实测：`data/` 74 个文件剩 16 个

计划写的是「由简到繁：Terms/Search → Profile/Community → Post/Vote → Feed/Paging」。实际没有按这个
顺序分批，因为**分不开**：`FeedRemoteMediator` 要 `PostRemoteDataSource`，后者要 `HtmlSource`，
`OfflineFirstPostRepository` 又同时要数据库、Paging 和离线库。一次搬完再按编译器报的错逐个处置，比
按主题切四刀省事。

搬完之后 `:app` 的 `data/` 只剩 **16 个**文件，每一个都是平台外壳或图床协议：

| 留下的 | 为什么 |
|---|---|
| `AppCacheStore.kt`（只剩实现）、`CoilImageCaches.kt` | Coil 的缓存 + `java.io.File` |
| `offline/AndroidOfflineFileStore.kt`、`OfflineImageInterceptor.kt` | 文件系统 + `StatFs`；后者给 Coil 递 `File` |
| `offline/OfflineWork.kt`、`OfflineWorkers.kt` | WorkManager，按 §4.4「不要过度抽象」原样留下 |
| `proxy/ProxyConnectionTester.kt` | 靠 `java.net` 的异常类型分类失败原因 |
| `imagehost/` 的 9 个 | 六家图床的协议，见下 |

`:shared` 这侧：`commonMain` 63 个，`androidMain` 3 个（OkHttp 取图、更新仓库的实现、数据库工厂）。

#### 一分为二的四个

不是整体搬走也不是整体留下，接口下去、实现留下：

| | `commonMain` | 留在原地 |
|---|---|---|
| `AppCacheStore` | 两个 suspend 函数的接口 | `ImageCaches` + `DefaultAppCacheStore`（`:app`） |
| `OfflineFileStore` | **新写的接口**，七个方法 | `AndroidOfflineFileStore`（`:app`，多一个 `fileOf(): File?` 给 Coil 拦截器） |
| `OfflineImageSource` | 接口 | `OkHttpOfflineImageSource`（`:shared/androidMain`） |
| `AppUpdateRepository` / `UpdateCheckStore` | 两个接口 | `DefaultAppUpdateRepository`（`:shared/androidMain`） |

后两个进 `androidMain` 而不是留 `:app`：它们要的是 OkHttp 和 `File`，而 OkHttp 从 A5 起就住在
`:shared/androidMain`。理由都写在文件头上——图片要的是**字节**，APK 要的是**流**，而
`HttpTransport` 交出来的是解码好的文本。

#### `imagehost/` 是唯一整片没走的

九个文件、六家图床的协议，全部还在 `:app`。原因只有一个：**上传进度**。`HttpTransport` 读完一整个
回答才返回，请求飞在半空时它什么都不说；而托盘上那个进度环要的正好是相反的东西，OkHttp 那侧是靠包
一层 `RequestBody` 拿到的。给 transport 加一个上传进度回调是改一个**所有调用方共享**的契约，那属于
真正需要 Apple 上传器的那一步，不属于这一步。

接口 `ImageHostRepository` 还是下去了（ViewModel 只认它），`ImageHost` / `ImageHostModels` /
`ImageHostSettings`（纯 DataStore）也下去了。

#### 六处不是搬家的改动

**一、`java.time` → kotlinx-datetime。**`AssetsRepository` 要「这条流水属于站点的哪一天」，
`TimeFormat` 要五种时间标签。stdlib 有 `Instant` 但没有时区也没有 civil date，所以加了
`kotlinx-datetime 0.8.0`。`TimeFormat` 因此从 `jvmCommonMain` 升到 `commonMain`——五个仓库调它的
`parseTimestamp`，五个都在 `commonMain` 了。它的测试跟着进 `commonTest`，于是三端各跑一遍。

**二、`@Immutable` 逼出一个 Compose 依赖。**`OfflineState`、`ImageUploadState` 这三个类带
`@Immutable`。Compose 编译器没跑过的模块，它的类在使用方一律按 unstable 处理——所以那个注解不是装饰，
删掉是一次**静默的性能退化**。`:shared` 因此 `api(libs.compose.runtime)`：只要注解，不要编译器插件。

**三、`androidx.room.withTransaction` 不存在了。**它在 `room-ktx` 里，A6 已经说过那个包没跟来。
多平台 runtime 给的是 `useWriterConnection { it.immediateTransaction { … } }`，两句话写成
`RoomDatabase.writeTransaction`。`immediateTransaction`（`BEGIN IMMEDIATE`）而不是 deferred：这里
每个调用方都在写。块里的 DAO 调用会**加入**同一个事务而不是自己开一个——Room 把连接放在协程上下文里，
这也是块内不能换 dispatcher 的原因。

**四、`java.io.IOException` → `okio.IOException`。**三个 DataStore 读取器都有
`catch { if (it is IOException) emit(emptyPreferences()) }`。okio 的那个在 JVM 上就是
`java.io.IOException` 的 typealias，行为一字不差。

**五、`java.util.UUID` → `kotlin.uuid.Uuid`。**两处 `Csrf-Token`（服务端只检查有值）和一处上传队列
的行 id。

**六、`@Volatile` 和 `String.toByteArray()`。**前者要显式 `import kotlin.concurrent.Volatile`，
后者是 JVM 扩展，换 `encodeToByteArray()`——UTF-8 从默认值变成定义，而那个数字是要和存下来的比的。

**顺带：jsoup 从 APK 里删掉了。**计划里点名的 `UserSpaceRepository` 那一行 `Jsoup.parse(raw).text()`
换成 Ksoup 之后，`:app` 再没有一个 `org.jsoup` import——依赖和开源许可页上的那一条一起删。

#### 智能转换在跨模块会失效

四处 `x != null -> f(x)` 编译不过了：`val` 来自另一个模块，编译器不做 smart cast。和步骤 3/4 记的
`VoteCard` 是同一条，处置也一样——先读进局部变量。这条在把一整个包搬出模块时会成片出现，值得当成
可预期成本而不是意外。

#### 八个 `internal` 变 public，以及为什么测试没跟着搬

`FRONT_PAGE_FEED_KEY`、`feedKeyFor`、`searchFeedKeyFor`、`SEARCH_FEED_KEY_PREFIX`、
`FEED_PAGING_CONFIG`、`markViewedBody`、`ImageHostKeys`、`ImageHostSecretKeys`——都是因为钉着它们的
测试还在 `:app`。

**测试没搬是权衡过的**，不是漏了。`app/src/test/.../data/` 的 fake 是**和 ViewModel 测试共用的一份**：
`FakePostRemoteDataSource` 被 UI 测试引用 51 次，`testSettingsRepository` 23 次，`inMemoryDatabase`
8 次。把 data 测试搬进 `:shared/androidHostTest` 就得把这些 fake 复制一份留给 `:app`——四百多行的
假实现两份，而**漂移的总是失败的那条测试没在读的那一份**（步骤 3/4 讲 fixture 时是同一个道理）。
D1 把 `ui/` 搬下来之后两半在同一个模块里，整棵测试树一次搬完，fake 仍然只有一份。

#### 测试与门禁

测试总数不变：`:app` 1,095 + `:designsys` 108 + `:shared` 224 = **1,427**。
`TimeFormatTest` 的 7 个从 `androidHostTest` 挪到 `commonTest`，所以桌面端 58 + 189 + 3 = **250**，
`macosArm64Test` **189**。

`assembleDebug` / `testDebugUnitTest testAndroidHostTest jvmTest` / `:app:lintDebug` /
`spotlessCheck` / `:shared:macosArm64Test` / `:gallery:jvmTest` 全绿。

---

### ✅ A5–A7 留下的两笔债：都在 D1 还了

两条都出自 #101 的 review，都不是缺陷，是这三步为了「一个 PR 只降低一个平台耦合」主动挂的账。
记在这里是因为它们各自有明确的还款点，过了那个点还不还就会变成永久的。

**两笔都在 D1 结清**，怎么还的记在下面各自那一节的末尾。

#### ✅ 一、八个 `internal` 变 public，还款点是 D1

上面那节讲了为什么测试没跟着搬（fake 和 ViewModel 测试共用一份）。要补的是**这笔债的另一头**：
`FEED_PAGING_CONFIG`、`markViewedBody`、两个 `ImageHost*Keys` 这些东西现在是 `:shared` 的公开
API，而它们本来是实现细节——分页窗口大小、一个请求体的拼法、图床的 key 名。公开 API 是会被人用的，
用一次就还不掉了。

**D1 把 `ui/` 搬进 `:shared` 之后，测试和被测在同一个模块里，这八个应当当场改回 `internal`。**
这是 D1 的验收项之一，不是「有空再说」——D1 的 PR 里如果没有这一步，债就永久了。

顺带：`SiteBootstrap`、`PostSourceParser` 两个 `internal object` 变 public 是同一类事（B2 记的），
它们的调用方在 `:app`，同样跟着 D1 回收。

> **✅ 已还（D1）。十个全部改回 `internal`。**
>
> 但**前提句是错的**：D1 没有把 `ui/` 搬进 `:shared`，搬进了新模块 `:ui`（为什么见「D1 实测」）。
> 于是「测试和被测在同一个模块里」并不自动成立——`data/` 的测试要进 `:shared`，`ui/` 的测试要进
> `:ui`，而那 44 个 data 测试和 4 个 ui 测试共用同一份 `TestDoubles.kt`，正是 A7 当初不搬测试的
> 那个理由。
>
> 解法不是复制，是**同一个文件编译两遍**：`TestDoubles.kt` 放在
> `shared/src/androidHostTest/kotlin/io/github/nodyssey/data/doubles/`，`:ui` 和 `:app` 的测试源集
> 各自 `srcDir` 指向那个目录。`internal` 是按编译单元解析的，所以每个模块拿到自己的一份，而磁盘上
> 只有一份——和 `app/build.gradle.kts` 里「指过去而不是拷过去」的抓包文件是同一个手法，理由也一样：
> 两份会漂，而漂掉的总是失败的那条测试没在读的那一份。

#### 二、`:designsys → :shared` 现在拖着整个数据层，还款点是 D1 的一个二选一

`:designsys` 需要 `:shared` 的只有 RichNode 那棵树和几个 parser。它拿到的是今天的 `:shared`：
网络层、Room、Paging、DataStore、站点业务，全部。`:gallery` 也跟着解析一整套数据库依赖——一个
组件画廊为了画一段富文本，classpath 上有 SQLite。锁文件的膨胀已经把这件事写在纸面上了。

**这是 A7 的直接后果，不是 B2 的**：B2 把 `core/image` 拆三份的时候 `:shared` 里还没有 Room。
真正的修法是从 `:shared` 里再切出一层「只有 model + richtext + parser」的模块，`:designsys` 和
`:gallery` 依赖那一层。

**没有现在做，理由是排序**：这一刀切在哪里取决于 D1 之后 `ui/` 需要什么，现在切等于猜。

但「D1 时重新评估」不是还款点——**重新评估最容易的结果就是无限期展期**。所以钉成一个二选一，
列为 D1 的验收项：

> D1 收尾时，要么把 model + richtext + parser 切成独立模块、`:designsys` 与 `:gallery` 改依赖
> 那一层，**要么在本文档里写下不拆的具体依据**——`ui/` 实际用到了 `:shared` 的哪些部分、因此
> 这一刀切不出来。两者必居其一，D1 的 PR 里两样都没有就是没做完。

> **✅ 已还（D1），选了第一条：新模块 `:richtext`。**
>
> 动手前先量了一遍，结果比这一节假设的还小：`:designsys` 和 `:gallery` 从下面**一共只 import 六个
> 符号**——`AnsiDecoder`、`AnsiSpan`、`RichNode`、`InlineNode`、`InlineStyle`、`parseMarkdown`——
> 全在 `plaza.core.richtext` 和 `plaza.core.ansi` 两个包里。`io.github.nodyssey.model` 一个都没用到,
> 所以这一刀不需要切「model + richtext + parser」，切四个文件就够：`RichNode.kt`、`Markdown.kt`、
> `AnsiDecoder.kt`，加上 `AnsiDecoder` 自己要的 `TerminalColumns.kt`。993 行，除了
> kotlinx-serialization 没有任何别的依赖，这是它值得切而不是值得讨论的原因。
>
> `:shared` 反过来依赖 `:richtext`（`QualityReportParser` 要 `TerminalColumns`），`:designsys` 与
> `:gallery` 改依赖 `:richtext`。锁文件是这件事的证据：`designsys/gradle.lockfile` 少 85 行，
> `gallery/gradle.lockfile` 少 46 行——一个组件画廊的 classpath 上不再有 SQLite。

---

### ✅ D1 实测：`ui/` 的 40,047 行进 `commonMain`，以及 `:app` 只剩 33 个文件

计划这一行写的是「`ui/` + ViewModel 进 `commonMain`」，没说是哪个模块的 `commonMain`。**不能是
`:shared`**：`:designsys` 在 `:shared` 下面，而一个屏幕同时要这两个，同一个模块装不下——那是个环。
所以 D1 的产物是新模块 `:ui`，124 个文件 + `Navigation.kt` / `NavigationKeys.kt` 全部进它的
`commonMain`，`:app` 从 160 个 Kotlin 文件剩 **33 个**。

`:ui` 的 target 是 android + jvm，**没有 Apple**：`:designsys` 只有这两个，而一个依赖没有的 target
是解析不出来的变体。给前端开 Apple target 是 D3 的事——那一步同时要回答 WKWebView 桥和 IME。**桥和
target 已在 D3a 做掉，IME 归 D3b。**桌面
JVM 已经足够回答 D1 要证明的那件事：四万行屏幕在没有 Android 的地方编译。

#### D2 的一半是 D1 的前置，不是后续

计划把 1,059 条 strings 排在 D1 之后，理由是「对着一个马上要整体搬走的目录做机械替换不划算」。
**这个排序是做不到的**：`ui/` 的 124 个文件里 77 个认 `R`，而 `R` 是 `:app` 的——它们进
`commonMain` 的前一刻必须已经不认了。所以字符串先搬，在 `ui/` 还在 `:app` 里的时候搬完，这样每一步
都有一个能编译的 `:app` 兜底；替换本身两种顺序一模一样，没有一条是白做的。

三处不是机械替换，前两处是单测抓到的：

- **`%%` 五处。**Android 的 formatter 把它折成一个 `%`，Compose Resources 自己实现 `%n$type` 替换，
  不认这个转义，屏幕上于是是 `62%%`。
- **`%1$,d` 一处。**同一个原因——它不认 flag，千分位得在进 formatter 之前加好。
- **`toPickedImages` 的兜底文件名改成参数。**它不是 composable，而 Compose Resources 在组合之外
  只有 suspend 访问器。

`res/values/strings.xml` 留下 7 条：manifest 读的 label、三个通知渠道名（`NotificationManager` 在
建渠道时把名字存下来）、三条只有轮询 worker 会拼的通知正文。前四条在 `:ui` 也有一份，是有意的副本。

**顺带发现一个 debug 构建的坑。**`about_app_name` 原来靠 `src/debug/res` 覆盖成「Nodyssey·D」，而
**Compose Resources 没有 build type**。搬过去之后 debug 包的关于页会悄悄说「Nodyssey」。改法是让它
不再是字符串资源：`AppVersion` 多一个 `label`，Android 侧从 `applicationInfo.loadLabel` 读——那正是
launcher 显示的同一个名字，debug 覆盖照样生效，而且从此只有一个答案而不是两个。

#### 平台耦合面：23 个文件，最后落成 9 个 `expect`

`ui/` 的 124 个文件里 23 个 import `android.*`。落地之后 `androidMain` 只有 6 个文件，其余的耦合
收成九个 `expect`：

| `expect` | 谁要它 | Android 侧 |
|---|---|---|
| `rememberImagePicker` | 两个编辑器 + 私信 | `PickMultipleVisualMedia` + 内容提供者查文件名 |
| `rememberAvatarPicker` | 个人信息 | 相册 + 相机，两条都要解码和缩放 |
| `rememberImageSamplePicker` | 自定义取色 | `ImageDecoder` 按目标尺寸采样 |
| `rememberImageGallerySaver` | 图片查看器 | `MediaStore` scoped storage |
| `rememberShareText` | 帖子详情、图片查看器 | `ACTION_SEND` |
| `rememberFileSizeLabel` | 关于、设置、更新提醒 | `Formatter.formatShortFileSize` |
| `rememberAppLinkHandlingEnabled` / `…SettingsLauncher` | 设置 | `DomainVerificationManager` |
| `rememberNotificationPermissionRequest` | 通知设置 | `POST_NOTIFICATIONS` |
| `rememberInstallPermissionRequest` | 关于 | 「允许安装未知应用」 |
| `rememberWallpaperPalette` / `osVersionName` / `supportsWallpaperColorSource` | 动态取色 | `WallpaperManager` + 系统强调色 |
| `WebViewRoute` | 登录 / 过盾 / 管理页 | 整个屏幕 |

**`WebViewRoute` 是唯一一处放弃拆缝的**，而且是想清楚了才放弃的：Android 的答案是一个 `WebView`，
它的 cookie 落在 OkHttp 读的同一个 `CookieManager` 里；Apple 的答案是 `WKWebView` 加一座
`WKHTTPCookieStore` ↔ `NSHTTPCookieStorage` 的桥——不是同一个对象藏在参数后面，生命周期也不一样。
对着一个实现设计缝，等于猜第二个。那座桥是 D3。

> **✅ D3a 已建桥**，工具栏和轮询也确实回头拆了——`webViewPolicy` 进 `commonMain`，两个 `actual`
> 共用。见下面的「D3a 实测」。

另外三处是白捡的：五个屏幕的 `BackHandler` 换成 `:designsys` 的 `PlazaBackHandler`（B1 已经把它变
common 了，当时还写着「没有别的调用方，也许该删」）；`LocalContext` 喂 Coil 的三处换成
`LocalPlatformContext`；`ProfileScreen` 的一个 `@Preview` 用了 `Configuration.UI_MODE_NIGHT_YES`。

#### `AppContainer` 一分为二，`ApkInstaller` 变接口

37 个 ViewModel 的工厂都要 `AppContainer`。它本来就是接口，36 个成员里 **34 个的类型已经在
`:shared`**——A7 干的。只有两个不是：`apkInstaller` 和 `proxyConnectionTester`。

- `ApkInstaller` 从 `:app` 的类变成 `:shared` 的接口（`install(apkPath: String)`，不是 `File`），
  实现改名 `AndroidApkInstaller`。「允许安装未知应用」那个 `Intent` **没有**上接口——那是一个设置页
  加一个 activity result，没有中性形状，走上面那个 `expect`。
- `ProxyConnectionTester` 多一个 `classify(Throwable)`。分类靠的是 `java.net` 的异常类型，原来是
  `:app` 的一个扩展函数，而看结果的屏幕现在没有 `java.net` 了；挂到接口上比 expect/actual 省事，
  因为有 HTTP 客户端的那一侧本来就知道自己会抛什么。

接口进 `:ui/commonMain`，`OkHttpClient` 和 `SessionCookies` 两个成员留在 `:app` 的
`AndroidAppContainer`——两个都不是从屏幕上读的：前者是 Coil 的 call factory，后者只在容器内部用。

`MainActivity` 从 163 行变成 76 行：`setContent { }` 里那一整块主题 + CompositionLocal + 导航搬进
`:ui` 的 `NodysseyRoot`。这不是顺手做的——不搬的话，`ui.settings.theme` 里那五个组配色的函数都得
从 `internal` 变 public，而那正是这一步在还的那类债。

#### 两个 androidx 的坑，都是桌面编译才暴露出来的

**一、`rememberNavBackStack(vararg)` 只有 Android 有。**多平台那份的签名是
`rememberNavBackStack(configuration: SavedStateConfiguration, vararg elements)`，而且**必须**传一个
注册了 `NavKey` 全部子类型的 `SerializersModule`——Android 那个重载是靠反射跳过这件事的，它在
`androidMain` 里。所以 38 个 key 现在写在 `NavKeySerialization.kt` 里一行一个。代价说清楚：新增一个
目的地要记得往这里加一笔，编译器不会提醒，症状是进程被杀之后那条返回栈恢复不了。没有加
`@SerialName`，判别符还是全限定类名，也就是反射那条路本来写的东西。

**二、`androidx.navigation3:navigation3-ui` 的 jvm 变体是 stub。**同一个 group 里
`navigation3-runtime` 是真多平台，`navigation3-ui`——`NavDisplay` 在里面——是 android 加一个
`jvmstubs`。`cmp-ui-decision.md` §2.4 那行「navigation3 androidx 官方已全平台 KMP」只对一半。
`:ui` 因此对 UI 那半换 `org.jetbrains.androidx.navigation3` 的镜像，并按 B4 的老规矩加一条
constraint——镜像 1.1.0 指向 androidx 1.1.0，而本仓库跑 1.1.4，不写就是一次静默降级。

**还有一个只有单测抓得到的：`:ui` 少了 `kotlin-serialization` 插件。**`@Serializable` 从 `:shared`
的 api 面上传下来，所以 38 个 key 编译得好好的，但**一个序列化器都没生成**——返回栈存档整个是坏的。
`PostDetailKeySavedStateTest` 是唯一会响的东西。

#### 整棵测试树一次搬完，1,427 个一个没少

| | 之前 | 之后 |
|---|---|---|
| `:app` | 1,095 | 109 |
| `:shared` `androidHostTest` | 32 | 583 |
| `:ui` `androidHostTest` | — | 608 |
| `:designsys` | 108 | 108 |
| `:richtext` | — | 19 |
| 合计（Android 侧） | **1,427** | **1,427** |

桌面侧 250 → 254，`macosArm64` 189 → 193，多的四个是 `LuckyDrawTest`：`LuckyDraw` 从 `:app` 下沉到
`:shared/commonMain`（`java.time` → kotlinx-datetime，`URLEncoder` → `NodeSeekSite` 那份手写的
`urlEncode`，现在是包内 `internal`），测试跟着进 `commonTest`，于是三端各跑一遍。

留在 `:app` 的 109 个都是主语是 Android 外壳的：六家图床的客户端、离线文件存储、图片缓存、
`toProxyConnectionFailure` 的异常分类、数据库迁移测试。

`:ui` 没有 `jvmTest`——那 608 个是 Robolectric + Compose 的 Android 测试。把它们搬进 `commonTest`
要换掉 `createComposeRule`（`ui-test-manifest` 是 Android artifact），那是另一件事，不是这一步的。

#### `:app` 的锁文件只动了两行

而且不是版本：`components-resources` 从「只在 runtime」变成「compile 也在」，因为 `:app` 现在
通过 `:ui` 的 api 面读 Compose Resources。**没有一个版本变，没有一个 artifact 增减。**这是「Android
行为不变」这句话能给出的最硬的一条证据。

---

### ✅ D3a 实测：两个模块开 `iosArm64`，以及那座桥

计划把 D3 写成一行四件事：门槛 A 复验、WKWebView 桥、生命周期、IME。**后两件要一个能启动的 iOS
壳**，前两件不要，所以这一步只做前两件加它们的前置——`:designsys` 和 `:ui` 各开一个 `iosArm64`，
把 21 个 `expect` 补齐，桥写在 `:shared/appleMain`。**没有跑过任何一行**：这一步交付的是「编译得
过」，不是「跑得起来」。

**只开 `iosArm64`，不开 `macosArm64`，也不开模拟器。**macOS 已经被 `jvm()` 回答了——`:gallery`
跑的就是那个 target——而 iOS 没有任何东西回答。模拟器 target 今天没有消费者，它的意义在 D3b 那步
才出现，现在加等于多一份锁文件里没人解析的图。

#### `:designsys`：6,600 行 Compose，六个 `actual`

加上 target 之后编译器报了六条，**一条不多一条不少**，六条全是 `expect` 没有 Native 实现——也就是
说这个模块的其余部分早就是中性的了。

| `expect` | iOS 侧 |
|---|---|
| `rememberPlainTextClip` | `ClipEntry.withPlainText`——这个平台上 `ClipEntry` 的构造函数是 internal，Compose 自己包 `NSItemProvider`。label 丢掉，`UIPasteboard` 没有逐项标签 |
| `rememberCopyConfirmation` | 空的，和桌面同一个答案：iOS 也不自己提示，但也没有 toast 形状的东西可用 |
| `rememberExternalUriHandler` | `LocalUriHandler.current`。`SFSafariViewController` 才是这条缝的名字，但呈现它要一个 view controller，而这个 App 没有 iOS 壳 |
| `platformSystemColorScheme` | null。iOS 根本不把壁纸交给 App |
| `previousGraphemeBoundary` | `rangeOfComposedCharacterSequenceAtIndex` |
| `platformImageFailure` | `Unknown`，故意不猜——见下 |

**字素边界这条要说清楚差在哪。**Foundation 的 composed character sequence 不等于
`BreakIterator` 的字素簇：基字符加组合标记它认（表情面板那个带变体选择符的 ❤️ 就在这一类，而且
面板里一个 ZWJ 序列都没有），**粘贴进来的 `👨‍👩‍👧` 它不认**——那是一个字素、三个 composed
sequence，iOS 上退格会一个人一个人地删。`NSString` 没有字素簇 API 可换，Swift 是在标准库里做这件
事的，不在 Foundation 里。

**图片失败分类没有写 `when`，这是有意的。**`Unreachable` / `Timeout` / `Connection` 三条是照异常
*类型*分的，而这个平台上还没有那些类型：`java.net` 不在，取而代之的是什么由 Coil 拿到哪个网络引擎
决定，而 iOS 侧一个引擎都还没有。现在写 `when` 就是在写没有任何代码能抛出的分支。

#### `:designsys` 的 commonTest 里有三样东西不是 common 的

`:designsys:build` **包含** `compileTestKotlinIosArm64`，所以这三样是硬报错而不是提醒：

- **`TestMonotonicFrameClock` 是 JVM artifact。**`org.jetbrains.compose.ui:ui-test` 的 iOS 变体里
  没有它——desktop jar 里连 `TestMonotonicFrameClock_jvmKt` 都在。它一直待在 `commonTest` 里而编
  译得过，只是因为这个模块以前只有两个 JVM target。换成文件末尾自己写的 `SteppedFrameClock`：一次
  `withFrameNanos` 走一帧，弹簧动画有时长所以循环会自己结束。那个测试断言的是动画停在哪，不是动画
  中途，所以虚拟时钟协调那部分本来就没用上。
- **四个反引号测试名里有逗号**，Kotlin/Native 不允许。按步骤 3/4 立的规矩改写句子而不是换标点。
- **`String.format`** 在一个失败信息的 hex 辅助函数里。

#### `:ui`：先是九个文件还在用 `java.*`，然后才是十五个 `expect`

D1 说的是「四万行屏幕在没有 Android 的地方编译」，那句话是真的。**但「没有 Android」不等于「没有
JVM」**——`commonMain` 里九个文件、65 处引用的是 `java.time` / `java.text.DateFormat` /
`String.format` / `java.util.Locale` / `System.currentTimeMillis`，android 和 jvm 两个 target 都
是 JVM，所以两边都编得过，直到来了第三个。

换成 kotlinx-datetime 和手写格式化，都是机械替换，**只有一处不是**：

- **草稿保存时间从 locale short form 变成 24 小时制。**`DateFormat.getTimeInstance(SHORT)` 没有
  多平台对应物，换成了 `TimeFormat.clock`——也就是私信气泡下面那个 `09:44`。全 App 从此只有一种
  时间戳写法，但这是一次行为改动，Android 上看得见。

补齐之后 15 个 `expect` 才浮出来。**其中十一个落了地，四个没有**：

| 落地的 | iOS 侧 |
|---|---|
| `rememberFileSizeLabel` | `NSByteCountFormatter`，`CountStyleFile`——十进制、标 MB/GB，和 Android API 26 起的口径一致 |
| `rememberGroupedNumber` | `NSNumberFormatter` 的 decimal style |
| `rememberShareText` | `UIActivityViewController`，宿主从前台 scene 的 key window 往上找到最顶层那个 |
| `rememberNotificationPermissionRequest` | `UNUserNotificationCenter`，要 alert/badge/sound 三项 |
| `rememberInstallPermissionRequest` | 空的。iOS 没有旁加载，`ApkInstaller` 也没有 Apple 实现，那一行设置根本不画 |
| `rememberAppLinkHandlingEnabled` / `…SettingsLauncher` | null + 空。iOS 有 Universal Links，**没有开关**——同样是拿不到 nodeseek.com 的关联文件，但连让用户手动打开的地方都没有 |
| `rememberWallpaperPalette` / `osVersionName` / `supportsWallpaperColorSource` | 空调色板 / `UIDevice.systemVersion` / false |
| `WebViewRoute` | 见下 |

**没落地的四个都是图片能力**：`rememberImagePicker`、`rememberAvatarPicker`、
`rememberImageSamplePicker`、`rememberImageGallerySaver`。不是缺 API——`PHPickerViewController`
和 `PHPhotoLibrary` 都在，呈现方式和 `rememberShareText` 找宿主是同一套。是每一个都要一屏的图片
管道（`NSItemProvider` 逐个加载、写到图片加载器读得回的地方、`UIImage` 经 Skia 变成
`ImageBitmap`、按边长采样、JPEG 编码），**而没有任何东西能跑其中一行**。写成空 lambda 而不是
`TODO()`：抛异常的 lambda 是一颗等着第一个壳踩上去的雷，而这是几个背后没东西的按钮，两件事不一样。
`rememberImageGallerySaver` 返回 `UNSUPPORTED_OS`——三个结果里最接近的一个，措辞是「这里做不了」
而不是「失败了，重试吧」，而重试恰好是没用的那件事。

#### 那座桥：三步，以及删除为什么要记账

`WebKitCookieBridge` 在 `:shared/appleMain`，`AppleCookieStore` 旁边。三步按顺序：

1. **`seed()`**——把 App 已有的 cookie 灌进 WebKit，**而且是 await 的**。调用方下一件事是 load
   一个 URL，cookie 还没到就开始的页面是以陌生人身份加载的页面，而以陌生人身份答的 Cloudflare
   挑战什么都过不了。
2. **`start()`**——注册 `WKHTTPCookieStoreObserver`，WebKit 每报一次变更就往外抄一次。这是
   `AppleCookieStore.cookieHeader` 在页面活着的时候还诚实的原因：屏幕每 500ms poll 一次读的是镜像，
   不是 WebKit——后者的取值器是回调式的。
3. **`drain()`**——最后再抄一次并等它完成。NodeSeek 的会话走 XHR 下发，没有导航可以挂这最后一抄。

**删除是镜像的，但只镜像「WebKit 名下」的那些。**站内登出会把 cookie 清掉，App 必须看得见，所以
曾在 WebKit 里、现在不在了的那些要从 storage 删掉。为什么要记一个集合而不是直接按「WebKit 没有就
删」：两个罐子形状不一样——`NSURLSession` 会把 WebKit 从没见过的 cookie 写进 storage，照那条规则
删，删掉的正好是一次普通 API 调用刚拿到的会话。

`stop()` 之后 `session.sync()` 在 `onDispose` 里，和 Android 同一个位置、同一个理由。**最后那次
`drain()` 挂在关闭动作上而不是挂在 `onDispose` 上**——`onDispose` 等不了任何东西，而「最后一瞬间」
恰好就是登录完成的那一瞬间。

#### `WebViewGoal` 的那套判断提到了 `commonMain`

D1 写「工具栏和轮询到时候值得回头再拆」，就是现在：两个平台各有一个内嵌浏览器，而「什么时候关」
「多久问一次」「哪些链接算自己的」「有没有出口去真浏览器」四件事一件都不关平台的事。抽成
`webViewPolicy(goal, session, baseline, isBound)`，两个 `actual` 共用。**动机是编译器**：抄两份的
`when` 是那种加了一个 goal 只改一处、而编译器一句话都不说的东西。

view 本身仍然是 `expect`。理由没变：Android 的答案是一个 `WebView`，cookie 落在 OkHttp 读的同一个
`CookieManager` 里；iOS 的答案是 `WKWebView` 加上面那座桥。

iOS 那份是照 Android 那份逐段翻的：`decidePolicyForNavigationAction` 对应
`shouldOverrideUrlLoading`，`createWebViewWithConfiguration` 对应 `onCreateWindow`（Telegram 的
登录挂件在 `window.open` 的子窗口里授权，再 post 回 opener），`javaScriptCanOpenWindowsAutomatically
= false` 对应 Android 那条「弹窗是因为用户点了什么，不是因为脚本想弹」。

#### 顺带修掉一处早就坏了的：`compileCommonMainKotlinMetadata`

`QualityReportParser` 里 `match.groups[2]!!.range` —— **`MatchGroup.range` 不在 common stdlib
里**。它在 android、jvm、iosArm64、macosArm64 上全都有，所以四个平台编译全绿，而
`:shared:compileCommonMainKotlinMetadata` 一直是红的。CI 抓不到：CI 跑的是
`testAndroidHostTest`，不跑 `build`。

**在把这次改动全部 stash 掉的干净树上照样复现**，和它一起复现的还有「`:designsys:build` 和
`:ui:build` 因此都进不去」——那正是这一步要用的那道闸，所以顺手改了：正则两头都锚定，group 2 一直
到行尾，起点就是行长减它自己的长度。

#### 门禁

`spotlessCheck`、`testDebugUnitTest testAndroidHostTest jvmTest`（1,683 个，0 失败）、
`:app:lintDebug`、`:app:assembleDebug :app:bundleRelease` 全绿；本机那道
`:shared:macosArm64Test` 也绿。iOS 侧是 `:designsys:compileKotlinIosArm64`、
`:designsys:compileTestKotlinIosArm64`、`:ui:compileKotlinIosArm64` ——**全部只是编译**。

`:app` 的锁文件一行没动，`:shared` 的也没有（WebKit 是平台库，不进依赖图）。变的是
`designsys/gradle.lockfile` 和 `ui/gradle.lockfile` 各多一份 `iosArm64CompileKlibraries`。

#### 动手前查掉的两个未知项

- **CMP 的 Apple 变体是齐的。**`material3`、`foundation`、`components-resources`、`ui-backhandler`
  都发 `iosarm64` / `iossimulatorarm64` / `macosarm64`。`material-icons-core` 1.7.3 的变体名是
  `uikitArm64` 而不是 `iosarm64`，**但属性里写的是 `target=ios_arm64`**——变体名是装饰，属性才是
  解析依据。
- **androidx lifecycle 2.11.0 是真多平台。**2.10.0 的 `lifecycle-viewmodel-compose` /
  `lifecycle-viewmodel-navigation3` 只有 android 加 `jvmstubs` 加 `linuxx64stubs`，正是 B4 记的那
  个坑；2.11.0（本仓库跑的这个）有 `iosarm64`。差一个版本就要多两条镜像坐标。

---

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
`:shared`，`SiteHtmlClient` 还在 `:core`。这是过渡态而不是终局：**A5** 之后 `:core` 的网络壳也进
`:shared/androidMain`，包就合回去了。**——A5 已做，`:core` 整个不存在了，见上。**

jsoup → Ksoup 确实是纯机械替换，只改 import，加上 spike 记过的 `TextNode.wholeText` 在 Ksoup 是函数
`getWholeText()`。**jsoup 没能从 `:app` 完全删掉**：`UserSpaceRepository` 还有一行
`Jsoup.parse(raw).text()` 在把 HTML 抽成纯文本做摘要。那是 repository，归 **A7**。

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

### ~~Apple 侧还能再共享一层~~ → CMP 下不再适用

本节原来规划的是一个 `AppleFrontendCore` Swift Package，让 iOS 与 macOS 的 Swift 代码不必各写一套，
形成「KMP Core + Apple Swift layer」两层共享。**前端改定为 CMP 后这一层没有了**：UI 本身就是
`commonMain` 里的一套 Kotlin，各端只剩启动壳和平台外壳。

```text
                  shared / commonMain
        model / parser / repository / database
                        │
              Compose Multiplatform UI
                        │
        ┌───────────────┼───────────────┐
     Android           iOS            macOS
   Activity 壳      UIScene 壳      NSWindow 壳
   WorkManager    BGTaskScheduler   平台外壳
```

Swift 只在真正的平台外壳里出现（生命周期、系统权限、后台任务），不承担 presentation。

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

**一个例外，`:gallery`。**`compose.desktop.currentOs` 按宿主机挑 Skiko 和 Compose desktop 的原生
件，是仓库里唯一「由机器而不是由 build 决定」的依赖——而锁文件的目的恰恰是消灭这件事。锁住的结果
是：Mac 上 `--write-locks` 写进 `macos-arm64`，CI 在 Ubuntu 上解析出 `linux-x64`，STRICT 同时报两
条（「解析出了不在锁里的」和「锁里的没解析出来」），构建在跑单元测试之前就挂。B4 合进 `main` 之后
CI 一直是红的就是这个原因，#101 只是继承了它。修法是把这两组坐标按通配符加进
`gallery/build.gradle.kts` 的 `dependencyLocking.ignoredDependencies`；该模块其余部分照锁。

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
