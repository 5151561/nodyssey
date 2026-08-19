# KMP 共享层与 macOS 端可行性评估

评估日期：2026-08-18 · 基线提交：`56c5b2a` · 范围：`:app` / `:core` / `:designsys` 全量

**既定路线（本文的前提，不再论证）：**

- 先补跨平台能力，**第一个新端是 macOS**，iOS 暂不做。
- 共享层走 **Kotlin/Native**，不走 JVM Desktop。
- **只支持 Apple Silicon**（`macosArm64`），不支持 Intel Mac。
- **macOS 前端重写**，不复用 Compose UI。

**结论：路线可行，生态已就位，三道门槛已于 2026-08-18 全部实测通过。**

- 门槛 A（macOS 会话链路，一票否决）：**7 项全绿**，浏览器会话可喂给原生 HTTP。
- 门槛 B（parser 换 Ksoup）：**41 个测试两端全绿**，jsoup → Ksoup 是纯机械替换。
- 门槛 C（Kotlin ↔ Swift 互操作）：**通过**，Kotlin 2.4.10 官方 Swift Export 在 `macosArm64`
  上导出 `StateFlow` / `Flow` / `suspend`，不需要第三方桥接库。

详见第 4 节。**三道门槛全部实测通过，成本判断从「估计」变成了「已知」**：真正的工作量不在
parser，而在测试资产（第 3.1 节）和一批零散的 JVM-only API（门槛 B 表格）。

---

## 0. 目标修订（2026-08-18 晚）

本文的评估结论（生态就位、三道门槛通过、成本分布）**继续有效**，但目标已修订，
以下几处需按新目标理解：

| 本文原本假设 | 现行目标 |
|---|---|
| 第一个新端是 macOS，iOS 暂不做 | **iOS 与 macOS 都做**，共用 `appleMain` source set |
| 门槛是开工前的前置条件 | **降级为已验证的资产**。当前目标是「KMP-ready 架构」，不需要先过门槛 |
| 共享层可能上探到 presentation（35 个 ViewModel） | **边界卡在 Repository**。ViewModel 以上各端自己写，理由见计划第 2 节 |
| 前端路线待定（SwiftUI vs CMP） | ~~**已定：永远原生**。`:designsys` 与 `app/ui` 永远 Android-only~~ → **2026-08-19 改定：CMP**，见 [`cmp-ui-decision.md`](cmp-ui-decision.md) |

因此第 4 节三道门槛请读作「已完成的可行性验证记录」，第 5 节执行顺序已被
[`kmp-migration-plan.md`](kmp-migration-plan.md) 取代。第 1、2、3 节（代码分布、生态核对、
必须计价的成本）不受影响。

前端改定只动前端归属：第 1 节的代码分布仍然成立，但其中 42,050 行「重写层」在 CMP 下是**搬迁**
而非重写，成本口径见 [`cmp-ui-decision.md`](cmp-ui-decision.md) 第 3.3 节。第 2、3 节与三道门槛
不受影响。

**「不共享 ViewModel」不缩小共享层**：第 1 节算出的 16,850 行本就不含 ViewModel，
它们在 `app/ui` 的 35,431 行里。

---

## 1. 代码分布

数字取自基线提交，`wc -l` 全量统计。

| 层 | 行数 | 占生产代码 | 走 Native + 前端重写后的归属 |
|---|---:|---:|---|
| `app/ui` | 35,431 | 57% | **重写**（已接受） |
| `:designsys` | 6,615 | 11% | **重写**（已接受） |
| `app/data` | 10,210 | 17% | 共享，需换底层库 |
| `app/core`（NodeSeek 业务） | 3,625 | 6% | 共享，含 parser 1,257 |
| `:core`（plaza 基础设施） | 2,650 | 4% | 共享，需拆 OkHttp |
| `app/di` | 511 | 1% | contract 共享，wiring 平台化 |
| `app/model` | 367 | 1% | 共享，几乎零成本 |
| `app/notifications` | 267 | 0.4% | Android-only，macOS 另写 |
| 其余（`NodysseyApp`、`Navigation` 等） | 1,948 | 3% | Android-only |
| **生产代码合计** | **61,624** | | |

**共享层约 16,850 行（27%），重写层约 42,050 行（68%）。**

前端重写是已定决策，所以 68% 那部分不再作为成本讨论。本文只关心 27% 那部分**能不能过去、要花多少**。

一个对本路线有利的事实：`app/data` 的 60 个文件中，**只有 9 个 `import android.*`**
（`ProxySettings`、`ApkInstaller`、`SecretCipher`、`AppUpdateRepository`、`ImagePreparer`、
`CommentComposerRepository`、`PostComposerRepository`、`NodeSeekDatabase`、`ImageHostSettings`）。
其余 51 个文件不含任何 Android 类型——它们的迁移障碍**不是 Android，而是 JVM 库**（jsoup、OkHttp）。

---

## 2. 生态核对

目标 target 为 `macosArm64` 单一 native target。实测 Google Maven 上项目当前依赖版本的构件：

| 依赖 | 版本 | `macosArm64` |
|---|---|:---:|
| `room-runtime` | 2.8.4 | OK |
| `room-paging` | 2.8.4 | OK |
| `room-testing` | 2.8.4 | OK |
| `paging-common` | 3.5.0 | OK |
| `paging-testing` | 3.5.0 | OK |
| `datastore-preferences` | 1.2.1 | OK |

三大件全部就位。

> 备注：`paging-common` 3.5.0 已经**没有** `macosX64` 构件（与 JetBrains 弃用该 target 的方向一致）。
> 由于本项目不支持 Intel Mac，这不构成约束——记录在此仅为说明「为什么不必考虑双 native target」。

单 target 带来一个附加好处：**不需要 `macosMain` 这类 intermediate source set**，
source set 层级就是 `commonMain` → `androidMain` / `macosArm64Main` 两支，
convention plugin 和 Gradle 图都比双 native target 的情况简单。

## 2.1 其余生态核对结果

| 能力 | 当前实现 | Native 可用性 | 处置 |
|---|---|---|---|
| 数据库 | Room 2.8.4 | 有构件 | 换 KMP SQLite driver，重写 7 个 migration |
| 偏好存储 | **Preferences** DataStore 1.2.1 | 有构件 | 幸运：KMP 只支持 Preferences 这一种，项目正好用的这种 |
| 分页 | Paging 3.5.0 | 有构件 | 直接可用 |
| HTML 解析 | **jsoup 1.22.2** | **不可用（JVM-only）** | 换 Ksoup（有 `macosarm64` 构件）→ 门槛 B |
| HTTP | **OkHttp 5.4.0** | **不可用（JVM-only）** | 换 Ktor Darwin engine 或 NSURLSession |
| 图片加载 | Coil 3.5.0 | KMP 库 | 前端重写，macOS 侧自行决定 |
| 登录 / 过 Cloudflare | Android WebView + `CookieManager` | WKWebView（macOS 同属 WebKit） | 重做 → 门槛 A |
| 安全存储 | Android Keystore | — | macOS Keychain，新写 |
| 后台轮询 | WorkManager | — | macOS 无对等物，但比 iOS 自由（见 2.2） |
| 应用内更新 | PackageInstaller + APK | — | macOS 可用 Sparkle（见 2.2） |

## 2.2 macOS 相对 iOS 的三个便宜之处

这三条是选 macOS 而不是 iOS 作为第一个新端的实际收益，值得记下来：

1. **不必过 App Store。** 可以直接分发（签名 + 公证），绕开第三方论坛客户端的上架审核不确定性。
2. **应用内更新仍然成立。** iOS 上 `data/update`（489 行）的语义完全不存在；macOS 上可以用
   Sparkle 保留「检查更新 → 下载 → 安装」的完整语义，`core/update` 的版本比较、channel、
   状态机逻辑（597 行）**可以共享**。
3. **后台轮询可行。** iOS 的 BGTask 限制严苛，macOS 上常驻应用可以自由调度，
   `NotificationPolling` 里的业务规则值得抽成 common policy。

---

## 3. 两笔必须计价的成本

### 3.1 测试资产有 45% 进不了 `commonTest`

当前工作区共 **1,260 个 `@Test`**（169 个测试文件）。其中：

| | `@Test` 数 | 占比 |
|---|---:|---:|
| 跑在 Robolectric 上 | 568 | 45% |
| 其中位于 `app/data`（要跟 Repository 一起进 common 的） | 173 | 14% |
| HTML parser 测试（纯 JVM，可直接迁） | 98 | 8% |

Robolectric 进不了 `commonTest`，走 native 更没有退路。`data` 层那 173 个测试依赖 Robolectric
提供的 `Context`、Room in-memory 构建和 `SupportSQLiteDatabase`，搬家时只有两条路：

- **留在 `androidUnitTest`**：macOS 侧 Repository 无测试覆盖。
- **重写成纯 fake 的 common 测试**：逐个改造。

**这是整个迁移里最大的一笔隐性成本，量级大于 parser 迁移。**建议按第 5 节的顺序，
在 Repository 迁移**之前**先做掉。

### 3.2 Room：7 个手写 migration 要重写

`NodeSeekDatabase` 当前 `version = 10`，带 `MIGRATION_3_4` 到 `MIGRATION_9_10` 共
**7 个手写 migration**，全部基于 `androidx.sqlite.db.SupportSQLiteDatabase`。

换 KMP SQLite driver 后这 7 个全要重写。而唯一能证明它们没写坏的
`NodeSeekDatabaseMigrationTest` 本身是 Robolectric 测试（回到 3.1）。

铁律不变：不改表名、不改列名、不清 schema history、不用 destructive migration 解决编译问题，
**Android 现有用户的数据库必须原地升级成功**。macOS 是全新安装，没有历史包袱，风险全在 Android 侧。

---

## 4. 三道门槛

**门槛没过之前不新建 module、不写 convention plugin、不动生产代码。**
三道互相独立，可并行，都不需要 KMP 基建。

### 门槛 A — macOS 会话链路（**已通过**，2026-08-18）

验证工程：`../../nodyssey-kmp-spike/gate-a-session`（`swiftc` 直编，不需要 Xcode 工程）

**7 项全部通过。整个 macOS 方向成立。**

- [x] WKWebView 打开 NodeSeek 并完成 Cloudflare challenge
- [x] 完成登录
- [x] 从 `WKHTTPCookieStore` 读到目标域 cookie（6 个）
- [x] 含会话 cookie
- [x] 含 `cf_clearance`
- [x] 同一份 cookie 交给 `URLSession`，返回 **HTTP 200，44,198 字节，判定为可用页面**
- [x] 同一会话二次请求仍然有效

#### 两条实测记录

**1. WKWebView 的默认 UA 能直接过 Cloudflare，不需要伪装成 Safari。**

```
Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko)
```

注意它**没有** `Safari/xxx` 后缀，也照样通过。这印证了 Android 侧 `UserAgent.kt` 的结论在 macOS
同样成立：把 WebView 报的身份原样转给原生请求即可，任何"美化"都是在制造矛盾。

**2. 站点实际下发的 cookie 与 `SiteConfig.sessionCookieNames` 不完全对应。**

实测 6 个：`colorscheme`、`fog`、**`session`**、**`pjwt`**、`smac`、`cf_clearance`。

当前配置是 `sessionCookieNames = listOf("session", "token")`，注释写的是「JWT 风格的 `token`
出现在某些部署上」。实测看到的 JWT 风格 cookie 名为 **`pjwt`**，没有 `token`。

由于 `session` 在场，登录判定不受影响，**这不是线上 bug**。但 `token` 这个名字是否还有效值得
在 Android 侧复核一次——如果站点已改名，那条分支就是死代码。

### 门槛 B — parser 换 Ksoup（**已通过**，2026-08-18）

验证工程：`../../nodyssey-kmp-spike/gate-b-parser`（独立于本仓库）

**41 个测试在 `jvmTest` 与 `macosArm64Test` 两端全绿。**

- [x] `RichContentParser`(463 行) + `PostDetailParser`(123 行) 及全部依赖闭包
- [x] 整个 `model` 目录、`Selectors`、`PostConfigParser`、`SiteBootstrap`、`AnsiParser`
- [x] 共 2,175 行生产代码 + 617 行测试
- [x] 主仓库原有 41 个 `@Test` 逻辑一字未改
- [x] fixture 加载问题已解决

**结论：parser 本身的 jsoup → Ksoup 是纯机械替换，只改 import。**全部 11 个 parser 只用到
12 个 jsoup 方法，Ksoup 全部有等价物。

**但真正的成本不在 parser，在周边。**迁移中撞到 6 个障碍，没有一个来自 parser 逻辑：

| # | 障碍 | 位置 | 处置 |
|---|---|---|---|
| 1 | `java.net.URLDecoder` / `StandardCharsets` | `StardustReceiveMarkup` | 自写 percent-decode，必须保留 `+`→空格 的表单语义 |
| 2 | `java.lang.Character.charCount` + internal `codePointAt` | `TerminalColumns` | 自写 UTF-16 代理对处理 |
| 3 | `classLoader.getResourceAsStream` | `Fixtures` | 生成 Kotlin 常量并**分块**（JVM 字面量上限 65535 字节，`post-705039-1.html` 有 10 万字符） |
| 4 | `java.util.Base64` | `SiteBootstrap` + 测试 | 换 `kotlin.io.encoding.Base64` |
| 5 | Ksoup 的 `wholeText` 是函数不是属性 | `AnsiParser` | 改调用点 |
| 6 | JUnit `assertTrue(msg, cond)` 与 kotlin.test `assertTrue(cond, msg)` 参数顺序相反 | 所有测试 | 逐处交换 |

第 6 条影响面最大：与 parser 无关，是**全部 1,260 个测试**迁移时都要过一遍的坑，
应与第 3.1 节的去 Robolectric 化一并计价（好在编译期能抓到，不会静默出错）。

第 3 条的分块脚本只是 spike 权宜之计，真正迁移时应改为 Gradle 任务从 resources 自动生成。

**工具链补充记录：** 当 active developer directory 指向 CommandLineTools 而不是完整 Xcode 时，
`:linkDebugTestMacosArm64` 会失败在 `CurrentXcode.xcrun`。用 `DEVELOPER_DIR` 环境变量指向
Xcode 即可，无须改全局 `xcode-select`。

> **2026-08-20 更正**：这条当初写成了「本机指向 CommandLineTools」，现在不成立了——`xcode-select -p`
> 已经指向一个完整 Xcode，`./gradlew :shared:macosArm64Test` 不加任何环境变量就能跑。反过来还有个
> 陷阱：给 `DEVELOPER_DIR` 一个**不存在**的路径（比如照抄 `/Applications/Xcode.app/...` 而机器上装的
> 是 beta），会盖掉本来正常的默认值，报出来的还是同一个 `CurrentXcode.xcrun`。先看 `xcode-select -p`
> 再决定要不要加这个变量。

**附带发现：** `NodeSeekSite.kt` 的 `import ...designsys.component.UserAvatar` 只服务于一句
KDoc 链接，不是运行时依赖，删 import 即可 —— 与第 6 节那处 `SettingsRepository` 的反向依赖
性质不同，不涉及架构调整。

### 门槛 C — Kotlin ↔ Swift 互操作（**已通过**，2026-08-18）

验证工程：`../../nodyssey-kmp-spike/gate-c-swift-interop`

用 Kotlin 2.4.10 官方的 **Swift Export**（Alpha）验证，target 只声明 `macosArm64`。

**结论：比官方文档写的更好，不需要 SKIE / KMP-NativeCoroutines / 手写桥接。**

| 开工前的未知数 | 实测结果 |
|---|---|
| Swift Export 覆盖 `macosArm64`？ | **是**。SPM 包完整生成，`swift build` 通过 |
| `StateFlow` 支持？（文档只写了 `Flow`） | **支持，且类型化**：`KotlinTypedStateFlow<T>`，带 `var value: T` |
| `Flow<List<T>>` 类型参数保留？ | **保留**：`KotlinTypedFlow<[Post]>`，带 `asAsyncSequence()` |
| `suspend` 函数？ | 导出为原生 `async throws`，含 `withTaskCancellationHandler` 取消传播 |

```swift
public var state: any KotlinTypedStateFlow<FeedUiState>
public func posts() -> any KotlinTypedFlow<Swift.Array<Post>>
public func refresh() async throws -> [Post]
```

这正是 SwiftUI 需要的形状：类型化的 `.value` 加 `AsyncSequence`。

**一个要留意的语义差异：** Kotlin 的 `data class` 导出成 Swift 的 **`final class`（引用类型）**
而非 `struct`。`copy` / `==` / `hashCode` / `toString` 都在，但值语义没了，而 SwiftUI 的 diffing
建立在值语义上。不是阻塞项，是设计 macOS 前端时要提前知道的事（在 Swift 侧包一层 struct，
或依赖导出的 `==`）。导出的类也不能在 Swift 侧继承。

**唯一的坑：** Gradle task `BuildSPMSwiftExportPackage` 失败于
`property 'deploymentTargetSettingName' doesn't have a configured value`。加 `iosSimulatorArm64`
做对照后确认 **iOS 同样失败**，属于非 Xcode 驱动时的构建集成问题，与导出能力无关。绕过方式是
只取前一步 `GenerateSPMPackage` 的产物再自行 `swift build`。真正做 macOS 端时有 Xcode 工程
驱动这一步，届时需重新评估。

**对第 5 节执行顺序的影响：** 原先「前端路线必须先于共享 presentation 决定」的理由之一是
Swift 侧消费 `StateFlow` 需要额外桥接层且方案未知。这条已经消解 —— 官方通路可用，
共享 presentation（35 个 ViewModel）在 Swift 侧是有形状可依的。顺序仍建议保持，
但风险等级从「未知」降为「已知可行」。

## 5. 门槛通过后的执行顺序

| 阶段 | 内容 | 说明 |
|---|---|---|
| 0 | Build infra：`plaza.kmp.library` convention plugin、空 `:shared`、`macosArm64` target | 不移动业务代码 |
| 1 | **`data` 层 173 个 Robolectric 测试去 Robolectric 化** | **提前做**，否则后面迁移没有回归网 |
| 2 | `app/model`(367) + `core/ansi` + `core/richtext` → common | 最便宜，验证管线 |
| 3 | 全部 HTML parser + fixture → common（jsoup → Ksoup） | 门槛 B 的正式落地 |
| 4 | 网络契约：`HtmlSource` / `SiteTransport` / cookie store / 动态签名 / proxy model | 目标：Repository 不再出现 OkHttp 类型 |
| 5 | macOS transport + WKWebView cookie 桥接 | 门槛 A 的正式落地 |
| 6 | Room + DataStore（含 7 个 migration 重写） | Android 侧原地升级必须验证 |
| 7 | Repository：由简到繁（Terms/Search → Profile/Community → Post/Vote → Feed/Paging） | 不要一次搬整个 `data/` |
| 8 | 共享 presentation（35 个 ViewModel）——**取决于门槛 C 的结论** | 门槛 C 结果差就跳过，只共享到 Repository 为止 |
| 9 | macOS 前端（SwiftUI/AppKit）+ Sparkle 更新 + 通知调度 | 重写，已接受 |

相对原迁移计划的三处改动：iOS spike 从 PR 5 提到门槛 A；parser PoC 从独立 PR 降为门槛 B；
**新增阶段 1（去 Robolectric 化）**，它是原计划完全没有的一项。

---

## 6. 与决策无关、无论如何都该做的

以下三项是净收益，不依赖 KMP 是否推进：

1. **拆掉 `SettingsRepository` → `:designsys` 的反向依赖。**
   [`SettingsRepository.kt:18`](../app/src/main/java/io/github/nodyssey/data/settings/SettingsRepository.kt)
   `import io.github.plaza.designsys.editor.EditorAction`——data 层不该依赖 UI 模块。
   把需要持久化的部分下沉成 `EditorActionId`，`:designsys` 负责映射回 UI action。
   这是 `data` 层唯一一处反向依赖。

2. **让 Repository 的公开签名不再出现 OkHttp 类型。**
   无论是否 KMP，Repository 返回 `okhttp3.Response` 都是层次泄漏；而走 native 时这是必做项。

3. **`data` 层测试去 Robolectric 化。**
   173 个 `@Test` 改用 fake 而非 Robolectric `Context`，本身就能显著缩短测试时间。

如果最终 macOS 端没做成，这三项仍然值得做——**这也是判断这次评估是否白做的标准。**

---

## 附 A：本文数字的复现方式

```bash
# 生产代码分层
for d in app/src/main/java/io/github/nodyssey/*/; do \
  echo "$(find "$d" -name '*.kt' | xargs cat | wc -l)  $(basename $d)"; done | sort -rn

# 测试总量与 Robolectric 占比
grep -rho "@Test" app/src/test core/src/test designsys/src/test | wc -l
grep -rl "RobolectricTestRunner\|@Config" app/src/test core/src/test designsys/src/test \
  | xargs grep -ho "@Test" | wc -l

# data 层的 Android 依赖面
grep -rln "^import android\." app/src/main/java/io/github/nodyssey/data | wc -l

# 某个依赖版本是否有 macOS native 构件
curl -s -o /dev/null -w "%{http_code}\n" \
  https://dl.google.com/dl/android/maven2/androidx/paging/paging-common-macosarm64/3.5.0/paging-common-macosarm64-3.5.0.pom
```

## 附 B：待验证项

本文中未能从官方文档确认、必须实测的事项：

- **Swift Export 对 `macosArm64` 与 `StateFlow` 的支持**。官方文档确认了 Alpha 状态与
  `Flow` → `AsyncSequence`，但示例只覆盖 iOS target，也未提及 `StateFlow`。列为门槛 C 首项。
- **SKIE / KMP-NativeCoroutines 对 macOS target 的支持**。两者文档均未明确列出 target 清单，
  搜索结果只覆盖 iOS 场景。作为 Swift Export 不可用时的退路，需一并实测。
- **Ktor Darwin engine 在 macOS 上的 cookie 与 `URLSession` 配置行为**。Darwin engine 名义上覆盖
  全部 Apple 平台，但门槛 A 必须用真实站点验证，不能靠文档推断。
