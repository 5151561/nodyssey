# 实现状态

更新日期：2026-07-28 · 代码基线：`dffdc7e`（包含 f1/f2 重做提交 `d820a32`）

这份文档只描述 **Android App 当前真实实现**。视觉目标与站点实测数据分别见
[`design-requirements.md`](design-requirements.md) 和
[`design-requirements-remaining.md`](design-requirements-remaining.md)。设计稿“已出稿”不等于功能“已接入”；
判断某项能否使用，以本页和代码为准。

相关入口：

- 用户可见变化：[`../CHANGELOG.md`](../CHANGELOG.md)
- 画板到代码映射：[`design-implementation.md`](design-implementation.md)
- 架构约束与技术债：[`architecture.md`](architecture.md)

## 已接入

| 能力 | 当前实现 |
|---|---|
| 首页与阅读 | 版块、排序、Paging 追加、下拉刷新、Room 离线缓存、已读标记；帖子正文与评论原生 Compose 渲染 |
| 富文本 | 段落、链接、图片、表情、代码、引用、列表、表格；引用楼层跳转 |
| 搜索 | 帖子与用户远程搜索、最近记录、按需分页、追加失败重试；缓存结果可离线检索 |
| 通知与私信 | @我 / 回复主题 / 私信三组、未读 badge、全部已读、楼层跳转；私信列表、会话、Markdown 与发送 |
| 发帖 | 原生编辑器、Markdown 工具栏、表情、预览、权限与本地草稿；发帖接口已接入 |
| 用户与账号 | 我的主页、公开用户空间、主题/评论/收藏；资料、安全、联系方式、Telegram、屏蔽与首页偏好二级页 |
| 资产与工具 | 成长摘要、签到及签到榜、鸡腿相关页面、推荐阅读、抽奖、邀请码、社区工具入口 |
| 图片 | 原生查看器、缩放、保存与系统分享 |
| 通知轮询 | WorkManager 周期轮询、15/30/60 分钟、仅 Wi-Fi、免打扰与 Android 通知渠道 |
| 关于 f1 | App 身份、版本、更新入口、非官方声明、项目/反馈/日志/许可、社区统计、RSS、联络和友站 chips |
| 隐私 f2 | `/termsofservice` 原生解析为标题、段落和列表；原文外链；加载或解析失败才提供受限 WebView 降级 |

## 已有界面，但数据或写操作未接入

| 能力 | 原因与当前降级 |
|---|---|
| 评论发布 | 编辑器与草稿完整，真实评论发布端点尚未接入；失败时明确提示，不伪造成功 |
| NodeImage 上传 | 选择、队列与失败态已完成，上传端点尚未接入 |
| 关注/粉丝列表 | 站点页面由浏览器脚本动态生成，缺少已确认的数据端点；显示“尚未接入”，可转网页 |
| 星辰流水 | 同上；转账操作交给站点页面 |
| 管理记录 | 同上；原生页面保留真实字段契约，不用空列表冒充无数据 |
| 点赞、反对、投喂、收藏 | 详情页保留设计状态，真实写接口尚未统一接入 |
| 检查更新 | f1 支持“最新/发现新版”两种状态样式；当前按钮打开 GitHub Releases 核对，不在本地谎报“已是最新” |

## WebView 边界

- 登录与 Cloudflare 验证必须使用 WebView，以取得与 OkHttp 共用的 Cookie。
- NodeSeek 站内尚未原生化的页面可作为明确降级打开。
- 帖子、评论、搜索结果和隐私协议正常路径均为原生 Compose。
- 普通站外链接交给系统浏览器；内置 WebView 只允许 HTTPS 的 `nodeseek.com` 域名。

## 验证基线

提交前执行：

```bash
./gradlew spotlessCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew --stop
```

2026-07-28（`dffdc7e`）快照为 371 个 JVM/Robolectric 测试；spotless、单测、lint、assemble
四项门禁全部通过，Room schema 无变化。每次后续功能提交都应重新生成实际数字，不要沿用本快照冒充当前结果。
