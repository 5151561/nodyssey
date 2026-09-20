# 贡献指南

Nodyssey 欢迎 issue 和 PR。这份文档说清楚两件事：怎么把问题说明白，以及一个能被合并的 PR 长什么样。

面向 AI agent 的详细约定在 [AGENTS.md](AGENTS.md)，架构规则的完整版在
[docs/architecture.md](docs/architecture.md)。

## 反馈问题

到 [Issues](https://github.com/5151561/nodyssey/issues/new/choose) 按模板提。问题反馈里有三项是必填的：

- **App 版本** —— 设置 → 关于 Nodyssey 里那行「版本 1.2.21 (43)」，整行抄过来
- **手机型号**
- **Android 版本** —— 用定制系统的话把系统版本一起写上

这三项不是走流程，是真的会决定往哪查：同一个毛病在不同机型、不同系统上表现经常不一样。

界面问题请附截图或录屏。和具体帖子有关的请附帖子链接 —— 首页置顶帖会轮换，只说「第一个帖子」的话，
照着复现打开的多半是另一个帖子。

用法提问、不确定算不算 bug，可以先去
[Telegram 群组](https://t.me/+97ANIwVaCYk1MjQ1)。

## 动手之前

- 改 bug、补测试、补翻译、改文案：直接提 PR。
- 加功能、动架构、加依赖、改数据库 schema：先开个 issue 说一下想怎么做。省得写完了才发现方向不对。

## 环境与构建

需要 **JDK 21** 和 **Android SDK**（compileSdk 37、minSdk 26、targetSdk 36）。一律用仓库里的 Gradle
wrapper。

```bash
./gradlew :app:assembleDebug
./gradlew --stop
```

debug 和 release 的 applicationId 与名称不同，可以同时装在一台机器上。

常用命令：

| 命令 | 做什么 |
| --- | --- |
| `./gradlew spotlessCheck` / `spotlessApply` | 检查 / 修格式 |
| `./gradlew testDebugUnitTest testAndroidHostTest jvmTest` | 跑全部 JVM 测试。三个名字都要写：不写模块前缀是故意的，KMP 模块没有 build type，只写 `testDebugUnitTest` 会把它们的测试静默跳过 |
| `./gradlew :app:lintDebug` | Android lint，warning 当 error |
| `./gradlew :app:assembleDebug` | 打 debug 包 |
| `./gradlew :gallery:run` | 在桌面窗口里看 `:designsys` 的组件，不用开模拟器 |
| `./gradlew :shared:macosArm64Test` | 在 Kotlin/Native 上再跑一遍 common 测试。只有 Mac 能跑，CI 的日常 job 不跑它 —— 它专抓「只有 JVM 才接受」的 common 代码 |
| `./gradlew resolveAndLockAll --write-locks` | 改完依赖刷新全部 `gradle.lockfile`，要全部提交 |

提 PR 前跑一遍和 CI 相同的门禁：

```bash
./gradlew spotlessCheck testDebugUnitTest testAndroidHostTest jvmTest :app:lintDebug :app:assembleDebug
./gradlew --stop
```

跑完记得 `--stop`，不然 Gradle 守护进程会一直占着内存。

## 代码约定

**四条不可协商的规则**：

1. **SSOT** —— 一份数据一个所有者，其他人只观察，不留副本
2. **UDF** —— `Repository → ViewModel → 不可变 UiState → Compose`，反向只走事件
3. **依赖显式** —— 构造器注入 + `AppContainer`，没有全局单例
4. **数据层不产生用户文案** —— 数据层抛 `SiteError`，文案在 `strings.xml` 里

还有几条：

- 格式以 spotless + ktlint 为准（4 空格、LF、UTF-8、多行 Kotlin 带尾逗号）。类和 Composable 用
  `PascalCase`，函数和属性用 `camelCase`，测试名写成反引号里的一句人话。
- 站点的选择器知识集中在 `:shared` 的 `core/html/Selectors.kt`，不要散进 UI 或数据层。
- dispatcher 和 clock 靠注入传进来，不要现场 `Dispatchers.IO`。
- 永远不要吞掉协程的 cancellation。
- 版本号只写在 `gradle/libs.versions.toml`，不要在 build 脚本里内联。

## 文案与三种语言

App 出三种语言：简体中文、繁體中文、English。**简体是原件** —— 新文案先写简体，再补另外两份。

三套资源系统各自有自己的三份：

```text
ui/src/commonMain/composeResources/values{,-en,-zh-rTW}/       界面
designsys/src/commonMain/composeResources/values{,-en,-zh-rTW}/ 共用组件
app/src/main/res/values{,-en,-zh-rTW}/                          启动器名称与通知
```

繁中的目录名是 `values-zh-rTW`，按地区而不是按字形。别改成 `values-b+zh+Hant`：那样所有中文环境都会
优先拿到繁中，简体用户看到的会是繁体。

只在无后缀目录里出现的 key 不算漏翻 —— 纯标点的格式串和专有名词本来就该走 fallback。

## 测试

- 测试文件叫 `*Test.kt`，放在被测代码同包旁边。
- **解析器测试用提交进仓库的 fixture，永远不访问线上 NodeSeek。**
- 修 bug 要补回归测试，而且这个测试得能挂在修之前的代码上 —— 挂不上的测试证明不了什么。
- 改了 Room 实体或迁移，要把生成的 schema JSON 一起提交，并补迁移测试。
- 没有覆盖率指标，但改了的行为要有对着它写的测试。

## 界面改动的渲染图

**改了界面的 PR 要附 Robolectric 渲染图。** 手机截图受机型、字体缩放、系统主题、输入法的影响，同一段
代码换台机器就不一样；渲染图跑在固定窗口和固定字号上，两张图的差别只会是这个 PR 造成的。

### 改屏幕（`:ui`）

照着 [`NetworkCheckScreenRenderTest`](ui/src/androidHostTest/kotlin/io/github/nodyssey/render/NetworkCheckScreenRenderTest.kt)
给你改的屏幕写一个 render 测试 —— 和普通的屏幕测试一样组合界面，最后一步不是断言而是
`onRoot().captureRender("名字")`。有几个值得看的状态就写几个测试，一个 compose rule 只能 `setContent`
一次。

```bash
./gradlew :ui:testAndroidHostTest -PrenderUi --tests '*XxxScreenRenderTest'
```

PNG 落在 `ui/build/outputs/renders/`。**渲染图不进仓库** —— 它是构建产物，贴进 PR 就行。

改前那张这样拿，只回退屏幕代码，留着刚写的 render 测试：

```bash
git stash push -- ui/src/commonMain/kotlin/.../XxxScreen.kt
./gradlew :ui:testAndroidHostTest -PrenderUi --tests '*XxxScreenRenderTest'
git stash pop
```

出图默认是关的：不带 `-PrenderUi` 的普通测试运行会把 render 测试整类跳过，一张图都不写，CI 因此不为
出图付时间。

### 改设计系统（`:designsys`）

这个模块的组件钉着 golden 基线（`designsys/src/androidHostTest/snapshots/`），每次 `testAndroidHostTest`
都会比对，画面漂移就是红的。改动是有意的话，重录基线：

```bash
./gradlew :designsys:testAndroidHostTest -ProborazziRecord --rerun-tasks
```

基线 PNG 本来就在仓库里，重录后 PR 的 diff 自带前后对比，不用再额外贴图。

### 两条补充

- 屏幕不设 golden 门禁是有意的：屏幕比组件多得多、改得也勤，钉基线会让每次界面改动都要 review 一批
  PNG。这里要的是让人看的图，不是判定。
- 真机截图可以补充，但不能替代渲染图。只有真机才看得到的东西 —— 输入法、系统分享面板、手势返回、
  折叠屏铰链 —— 是例外。

## 提交信息与 CHANGELOG

提交信息沿用历史风格：短的祈使句，中英文都行。

```text
修复正文图片排版
游客打开内版提示需要登录
Add offline cache
```

一个提交只做一件事。

用户能感觉到的变化写进 [CHANGELOG.md](CHANGELOG.md) 的 `Unreleased` 一节，规矩有两条：

- **一条不超过 20 个汉字**
- **一个功能只写一条**

纯重构、改测试、调 CI 不用写。

## 提 PR

提之前先 `git diff main...HEAD` 把自己的改动从头到尾读一遍，当成在 review 别人的代码 —— 调试代码、
注释掉的旧实现、多余日志、跑偏的改动，大多数都能在这一步自己发现。

然后按 [PR 模板](.github/PULL_REQUEST_TEMPLATE.md) 填：做了什么、关联 issue、自查清单、实际跑过的验证
命令、界面改动的前后截图。**验证那一栏只写真跑过的命令**，没跑就留空，别照抄模板。

下面这些沾上就要在 PR 里明说：

- Room 实体或迁移变了
- 依赖变了（lockfile 是否全部刷新并提交）
- 登录态、Cookie、WebView 允许域名有改动
- 抓取用的选择器有改动

## 安全与隐私

- 永远不要提交 cookie、凭据、本机 SDK 路径，或任何已登录页面的抓取样本。
- 登录态由系统 `CookieManager` 持有，只通过现有的 WebView / OkHttp cookie 桥共享，不要另起一套。
- WebView 允许域名的改动按安全变更对待。
- 请求频率保持克制，不做任何自动化刷分行为；测试不访问线上站点。

## 许可

本项目是 GPL-3.0。提交贡献即表示同意以该许可发布你的代码。
