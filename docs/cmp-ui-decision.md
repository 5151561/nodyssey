# 前端路线改定：Compose Multiplatform

## 0. 这份文档改了什么

[`kmp-migration-decision.md`](kmp-migration-decision.md) 第 0 节（2026-08-18 晚）曾把前端路线定为
**「永远原生」**：`:designsys` 与 `app/ui` 永远 Android-only，Apple 侧写 SwiftUI。

**2026-08-19 改定：前端走 Compose Multiplatform。** 受影响的三处：

| 文档 | 原文 | 现行 |
|---|---|---|
| `kmp-migration-decision.md` §0 表格末行 | 前端「已定：永远原生」 | 改为 CMP，见本文 |
| [`kmp-migration-plan.md`](kmp-migration-plan.md) §2「不共享（永远 Android-only）」 | `:designsys` / `app/ui` / Navigation 3 / Compose | 均为迁移目标 |
| [`kmp-migration-plan.md`](kmp-migration-plan.md) §5 步骤 8 | 「Apple 前端：SwiftUI」 | 「Apple 前端：CMP，与 Android 同一套」 |

**没有**受影响的：第一阶段 KMP-ready 的全部内容（2026-08-18 已验收全绿）、第二阶段步骤 1–7、
三道门槛的实测结论、共享层的成本计价。改定只动前端归属，不动 Repository 以下的任何判断。

---

## 1. 为什么反转

触发点是一份外部提出的「Nodyssey → Rust + Slint 迁移」评估。该方案**已否决**：它要求重写全部
UI 并把 Core 逐步重写成 Rust，而相对 CMP 的唯一增量是「桌面端不带 JVM」——代价是丢掉已经跑通
`iosArm64` / `macosArm64` 的 KMP Core，换一个用户量最小的平台上的运行时偏好。

但评估过程暴露了一件「永远原生」路线自己没有计价的事：

`kmp-migration-decision.md` §1 把 `app/ui` 35,431 行 + `:designsys` 6,615 行 = **42,050 行（生产代码
68%）**记作「**重写**（已接受）」。这个「已接受」是按**一个** Apple 前端算的。每多一个端就再重写
一次，而这正是多端维护成本的全部来源。

CMP 让这 68% 从「重写 N 次」变成「搬迁一次」。这是改定的唯一理由，也是全部理由。

---

## 2. 实测核对（2026-08-19）

**方法：下载 artifact 比对符号，不查文档。** 网上关于「CMP 的 Material 3 Expressive 跟不上
androidx」的资料停留在 CMP 1.9 只有实验性 `MaterialExpressiveTheme` 的阶段，实际已发到
**1.12.0-alpha03**。凡结论依赖版本能力，一律以 artifact 为准。

### 2.1 material3：102 个符号，100 个在

`app/src/main` + `designsys/src/main` 实际 import 的 `androidx.compose.material3.*` 符号共 102 个
（不含 `adaptive.*`），逐个比对 `org.jetbrains.compose.material3:material3:1.12.0-alpha03` 的
sources jar（357 个 kt 文件，含 `commonMain` / `darwinMain` / `skikoMain`）。

**Expressive 大件全部存在**：`MaterialExpressiveTheme`、`MotionScheme`、`ToggleButton` /
`ToggleButtonDefaults`、`ButtonGroupDefaults`、`LoadingIndicator`、
`FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll`、`rememberSearchBarState` /
`SearchBarValue`、`TimePickerDialog` / `TimePickerDisplayMode`、`SecureTextField`、`SegmentedButton`。

### 2.2 唯二缺的

```text
dynamicLightColorScheme / dynamicDarkColorScheme
```

在 CMP 里只出现于文档 md，无实现——Material You 取色是 Android 12+ 专有，其他平台没有对应概念。
用在 `designsys/src/main/java/io/github/plaza/designsys/theme/Theme.kt:70-72`，已经被
`Build.VERSION.SDK_INT >= S` 守卫着。一层 expect/actual 解决：非 Android 平台回退到石墨青固定配色。

**这与「系统取色」本身就是 Android 特性的事实一致，不是 CMP 的欠缺。**

### 2.3 基线落差：一个 alpha 版

CMP 1.12.0-alpha03 里有 `ScrollField.kt` 和 `SegmentedListItem`（androidx material3 1.5.0-alpha23
引入），但 `TimePickerDisplayMode` 只有 `Picker` / `Input`，没有 1.5.0-alpha24 新增的 scroll variant。

**判定：CMP 基线 ≈ androidx material3 1.5.0-alpha23。** 项目用 1.5.0-alpha24（2026-07-15 发布），
落后一个 alpha 版，约两周。且 `LuckyScreen.kt:383` 实际只用了 `Picker` 和 `Input`，未触及差异。

### 2.4 其余依赖

判据是 `.module` 文件里有没有**真** Apple 变体——`jvmStubs` / `linuxx64Stubs` 是编译占位，不是实现。

| 依赖 | 版本 | 结论 |
|---|---|---|
| Coil3 | 3.5.0 | ✅ 真 Apple 变体 |
| Paging（common + compose） | 3.5.1 | ✅ |
| Room | 2.8.4 | ✅ 5 个 Apple 变体 |
| DataStore | 1.3.0-alpha10 | ✅ |
| lifecycle-viewmodel-compose | 2.12.0-alpha01 | ✅（35 个 ViewModel 的 import 不用动） |
| **navigation3** | 1.2.0-alpha07 | ✅ **androidx 官方已全平台 KMP**，无需换 group |
| material3-adaptive / navigation-suite / navigation3 集成 | — | ⚠️ androidx 侧**只有 android + stub**；换到 `org.jetbrains.compose.material3.adaptive` 1.3.0-beta02 与 `org.jetbrains.compose.material3:material3-adaptive-navigation-suite` 1.12.0-alpha03，均有 `iosArm64` |
| **WorkManager** | 2.12.0-rc01 | ❌ **纯 Android，零 Apple 变体** |

依赖侧只有两处要动手：换 adaptive 的 group、给 WorkManager 找 Apple 对应（`BGTaskScheduler`）。
后者影响 5 个文件。

---

## 3. 对现有计划的影响

### 3.1 第一阶段：不受影响，继续照原样

[`kmp-migration-plan.md`](kmp-migration-plan.md) §4.6「不碰 frontend —— 一个字都不要为了 KMP 改」**在 CMP 下依然正确**。
理由变了但结论没变：原先是因为前端要重写所以不值得动，现在是因为前端要整体搬迁、逐个文件改反而
碍事。第一阶段的验收标准（2026-08-18 全绿）不需要复核。

### 3.2 「不共享 ViewModel」的理由消失了

[`kmp-migration-plan.md`](kmp-migration-plan.md) §2 把边界卡在 Repository，理由是 Swift Export 仍是 Alpha，不该拿它做长期
架构边界。**CMP 下不存在 Swift Export**——UI 和 ViewModel 都是 Kotlin，直接消费 `StateFlow`，那条
顾虑整体不适用。

因此边界**可以**上移到 ViewModel。但本文不改边界，只记录理由失效：ViewModel 是否共享应在真正开
Apple 端时按当时情况决定，不必现在定。§2 那段「为什么不共享 ViewModel」应读作「针对 SwiftUI 路线
的历史论证」。

### 3.3 成本结构反转

| | 「永远原生」口径 | CMP 口径 |
|---|---|---|
| `app/ui` 35,431 行 | 每个新端重写一次 | 搬迁一次 |
| `:designsys` 6,615 行 | 每个新端重写一次 | 搬迁一次，去掉 4 处 `android.*` |
| 合计 42,050 行（68%） | 重写 | 搬迁 |

---

## 4. 剩下的真问题

按风险从高到低。**注意其中两条已被昨天的实测降级**，不要按旧印象排期。

1. **`:app` 尚未拆分。** `ui/` 40,047 行、`data/` 12,117 行仍在 Android-only 的 `:app` 里，而
   `:core` 1,842 行、`:shared` 4,618 行（2026-08-19 `wc -l` 快照，口径为各模块 `src/main` +
   `src/commonMain`，与 `kmp-migration-decision.md` §1 的基线口径不同）。这是工作量主体，**但它与
   第一阶段的方向完全一致，不是 CMP 带来的新成本。**
2. **iOS 的 WebView 会话链路待复验。** `WebViewCookieJar` / `WebViewCookieStore` /
   `WebViewUserAgent` 在 `:core`，`SiteHtmlClient` 依赖它们，且是 Cloudflare 过检的关键路径。
   **门槛 A 已在 macOS 上全链路通过**（WKWebView → `WKHTTPCookieStore` → `URLSession`，HTTP 200），
   iOS 需按 [`kmp-migration-plan.md`](kmp-migration-plan.md) §5 步骤 1 复验。风险因此低于初判。
3. **1,059 条 strings + 16 个 res xml + 9 个 drawable** 要搬到 Compose Resources。机械活，量大，
   可脚本化。
4. **动态取色**一层 expect/actual（§2.2）。
5. **WorkManager** 无 Apple 变体（§2.4）。

前端平台耦合本身**不是**问题：`:designsys` 57 个文件里只有 4 个 import `android.*`
（`Clipboard.kt`、`ExternalUriHandler.kt`、`Theme.kt`、`RichContent.kt` 的 `LruCache`），
`app/src/main/.../ui` 124 个文件里只有 18 个。

---

## 5. 执行顺序

原则与第一阶段相同：**不赌的先做，赌注推到最后。**

| 步 | 内容 | 赌注 |
|---|---|---|
| 1 | `:designsys` 去掉 4 处 `android.*`（剪贴板 / 外链 / Toast 抽 interface，`LruCache` 换 Kotlin 实现） | 无，本身是架构改善 |
| 2 | 按 `kmp-migration-plan.md` 第一阶段的方式继续拆 `:app` | 无，Android 侧净收益 |
| 3 | 给 `:designsys` 加 desktop target 单独跑起来 | 小，且是最便宜的「真的脱离 Android 了」验证——不需要 Mac 工具链 |
| 4 | `androidx.compose` → `org.jetbrains.compose`，adaptive 换 group | **这一步才是赌注** |
| 5 | strings / res → Compose Resources | 机械 |
| 6 | iOS：门槛 A 复验 + WKWebView 桥 + 生命周期 + IME | 最后 |

第 3 步之前的任何一步失败，都不损失已完成的工作——与第一阶段「若最终不做 KMP 全部是净收益」
同构。

---

## 附：本文数字的复现方式

```bash
# material3 实际使用的符号
grep -rhoE "^import androidx\.compose\.material3\.[a-zA-Z0-9_.]+" --include="*.kt" \
  app/src/main designsys/src/main | sed 's/^import androidx\.compose\.material3\.//' | sort -u

# CMP material3 的 common 源码（比对符号是否存在）
curl -sLO https://repo1.maven.org/maven2/org/jetbrains/compose/material3/material3/\
1.12.0-alpha03/material3-1.12.0-alpha03-sources.jar

# 某依赖有没有真 Apple 变体（Stubs 不算）
curl -s https://dl.google.com/dl/android/maven2/androidx/room/room-runtime/2.8.4/\
room-runtime-2.8.4.module | grep -oE '"name" *: *"[a-zA-Z0-9-]*ApiElements[^"]*"'

# 前端的平台耦合面
grep -rlE "^import android\." --include="*.kt" designsys/src/main | wc -l
grep -rlE "^import android\." --include="*.kt" app/src/main/java/io/github/nodyssey/ui | wc -l
```

## 附 B：待验证项

- CMP 基线随版本前移，§2.3 的「落后一个 alpha」是 2026-08-19 快照，动手前重测。
- iOS 的 WKWebView 会话链路（门槛 A 只覆盖 macOS）。
- Compose Resources 对 1,059 条 strings 的迁移是否有可用的自动化路径，未调研。
- CMP 在 iOS 上的中文 IME 组合输入、文本选择、滚动手感，未实测。
