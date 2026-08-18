# 文档导航

文档按“当前事实 → 架构约束 → 设计输入 → 历史记录”分层。出现冲突时，优先级按下表从上到下。

| 文档 | 用途 | 是否当前事实 |
|---|---|---|
| [`implementation-status.md`](implementation-status.md) | 已接入、仅有界面、WebView 边界与验证快照 | 是，功能状态唯一入口 |
| [`architecture.md`](architecture.md) | SSOT/UDF、依赖、线程、会话、安全和技术债 | 是，工程约束 |
| [`compose-styles-api.md`](compose-styles-api.md) | Compose Styles API 为什么没接进 `:designsys` | 是，评估结论（随 foundation 版本失效） |
| [`design-implementation.md`](design-implementation.md) | 已核对画板到 Compose/Repository 的映射 | 是，设计交付对照 |
| [`design-requirements.md`](design-requirements.md) | 新版设计总纲、信息架构与视觉约束 | 是，设计目标 |
| [`design-requirements-remaining.md`](design-requirements-remaining.md) | 登录态站点实测词典与批次 A–E 完成记录 | 是，真实数据样本 |
| [`design-requirements-additions.md`](design-requirements-additions.md) | 边角功能复核与批次 F | 是，补充数据和画板要求 |
| [`design-brief.md`](design-brief.md) | 第一轮设计输入 | 否，仅供历史追溯 |
| [`../CHANGELOG.md`](../CHANGELOG.md) | 各版本的用户可见变化，未发布的先记在「Unreleased」 | 是，发布记录 |
| [`../README.md`](../README.md) | 项目介绍、可用能力概览、构建与 roadmap | 是，对外入口 |

`design/` 是本地画板工作区，当前由仓库 `.gitignore` 排除；其中 `boards.json.status` 只代表出稿状态，
不能代替实现状态。涉及 UI 的代码提交必须同步更新实现状态和设计对照，不能只修改画板或只修改代码。

## 更新规则

- 功能真正接入或降级策略变化：更新 `implementation-status.md`。
- 架构边界、依赖方向、后台任务或安全策略变化：更新 `architecture.md`。
- UI 按画板落地或有明确偏差：更新 `design-implementation.md`。
- 用户可见能力变化：更新根目录 `CHANGELOG.md`；正式发布时再建立版本章节。
- 测试数量和提交号只能写成带日期的快照，必须从本地报告和 Git 实际读取。
