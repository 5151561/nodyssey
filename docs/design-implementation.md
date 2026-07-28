# 设计稿与实现对照

更新日期：2026-07-28

`design/boards.json` 的 `status` 表示**画板制作状态**，不表示 Android 功能已经可用。
App 的总体真实状态以 [`implementation-status.md`](implementation-status.md) 为准；
本文记录已完成核对的画板与代码入口，防止实现脱离设计稿。

## 批次 F

| 画板 | 实现状态 | 主要代码 | 仍需注意 |
|---|---|---|---|
| f1 关于与社区 | 已按稿重做并拆页 | `ui/settings/AboutAppScreen.kt`、`AboutCommunityScreen.kt`、`ChangelogScreen.kt`、`Navigation.kt` | 软件关于仅从设置进入，社区关于仅从社区工具进入；当前更新按钮打开 GitHub Releases，不伪造检查结果 |
| f2 隐私协议 | 已按稿重做 | `ui/settings/PrivacyScreen.kt`、`PrivacyViewModel.kt`、`core/html/TermsParser.kt`、`data/TermsRepository.kt` | 正常路径为原生长文；站点请求或解析失败时才显示 WebView 降级 |
| f3 Telegram 绑定 | 已接入账号联系方式流程 | `ui/account/ContactScreen.kt`、`ContactViewModel.kt`、`data/account/AccountSettingsRepository.kt` | 绑定本身在网页完成（站点用 telegram.org 登录挂件），确认弹窗打开 `/setting#contact`，返回后轮询状态；绑定状态读取与解绑是原生请求 |
| f4 App 通知设置 | 已接入 | `ui/settings/NotificationSettingsScreen.kt`、`NotificationSettingsViewModel.kt`、`notifications/` | WorkManager 是周期轮询，不承诺即时推送；系统省电策略可能延后执行 |

以上代码路径均相对于 `app/src/main/java/io/github/nsreader/`。

## f1 验收对照

- “关于 NSReader”和“关于 · 社区”是两个独立页面，分别位于软件设置与社区工具。
- App 身份区包含非对称圆角 NS 标识、版本名与 version code、检查更新入口。
- 非官方声明使用 tonal card；项目主页、问题反馈为系统外链，更新日志与开源许可为 App 内页面。
- 社区段包含统计卡、关于本站、隐私协议、RSS 复制、电报频道、电报群组、邮箱与 DeepFlood。
- 统计卡每次进入页面时请求 NodeSeek 首页并解析服务端“用户数目”面板；加载失败只显示重试，不回退到静态人数。
- LowEndTalk、LowEndSpirit、HostLoc、ServerHunter 使用 chips；没有设计稿之外的 Telegram 客服行。
- 颜色、字阶与明暗模式来自 `MaterialTheme` token，不把画板 hex 复制进 Compose。

## f2 验收对照

- 顶栏包含返回、标题和打开原文；顶栏与正文之间有分隔线。
- 文档标题、生效日期、来源、H2/H3、段落、有序/无序列表按原生 Compose 连续排版。
- 正文可继续滚动时显示底部渐隐提示，到达末尾后提示消失。
- 页面内容来自 NodeSeek 原文，不把约 6.9k 字复制进资源文件；标题、日期、段落和列表解析由 JVM 测试覆盖。
- 原文外链交给系统浏览器；受限 WebView 只在原生加载或解析失败后由用户主动选择。

## 修改流程

1. 先读 `design/src/<id>.html`、对应 dark 稿和 `design/meta/<id>.json`。
2. 实现时保留 Screen 纯状态 + callback 边界，并使用现有主题 token、间距和语义组件。
3. 更新本文件与 `implementation-status.md`，明确“视觉已完成”和“数据/写操作已接入”的差别。
4. UI 改动至少补 360×800 Compose/Robolectric 用例；提交前执行仓库完整门禁。
