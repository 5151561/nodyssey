# 实现状态

更新日期：2026-08-01 · 代码基线：当前工作区（基于 `99b3cd6`，版本 1.0.1）

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
| 表态 | 点赞 / 投喂鸡腿 / 点踩三个动作接入站点接口；真实计数与「已操作」状态随帖子页一并解析；消耗鸡腿的两个先确认并说明代价，被拒时显示站点原话 |
| 搜索 | 帖子与用户远程搜索、最近记录、按需分页、追加失败重试；缓存结果可离线检索。搜索与论坛列表共用同一条分页与缓存管线，结果行因此有一致的已读态与新回复角标；为避免触发站点限流，空页即终止分页、版块筛选为单选、搜索请求之间有 2 秒最小间隔 |
| 通知与私信 | @我 / 回复主题 / 私信三组、未读 badge、全部已读、楼层跳转；私信列表、会话、Markdown 与发送 |
| 发帖 | 原生编辑器、Markdown 工具栏、表情、预览、权限与本地草稿；发帖与回复接口均已接入，配图经 NodeImage 上传（最长边 2048 转 WebP，API Key 只存本机） |
| 用户与账号 | “我的”包含 c7 未登录引导、登录 WebView 与游客可用入口；登录后提供我的主页、公开用户空间、主题/评论/收藏；账号设置二级页全部接入站点 `/setting` 契约——资料字段读写、头像上传、修改密码、2FA 状态与开启、邮箱与 Telegram 绑定状态、解绑、远程偏好、屏蔽列表与解除屏蔽 |
| 资产与工具 | 等级进度（按站点公式分级显示本级区间）、今日四项额度、鸡腿与星辰余额，鸡腿流水与星辰流水两条真实流水（游标/页码分页），签到及签到榜、推荐阅读、抽奖与社区工具入口；部分消费操作见下方未接入及网页降级清单 |
| 图片 | 原生查看器、缩放、保存与系统分享 |
| 通知轮询 | WorkManager 周期轮询、15/30/60 分钟、仅 Wi-Fi、免打扰与 Android 通知渠道 |
| 关于 f1 | 设置中的软件关于承载 App 身份、版本、更新入口、非官方声明、项目/反馈/日志/许可；社区工具中的社区关于承载 RSS、联络和友站 chips。注册人数因暂无可靠数据源暂时隐藏，相关接入代码保留 |
| 隐私 f2 | `/termsofservice` 原生解析为标题、段落和列表；原文外链；加载或解析失败才提供受限 WebView 降级 |

## 已有界面，但数据或写操作未接入（1 项）

按用户可感知能力去重；同一业务内的读写方法不重复计数。生产实现会明确显示未接入、禁用操作或保留未知值，不伪造成功和实时数据。

| 领域 | 数量 | 能力 | 当前情况 |
|---|---:|---|---|
| 社区与资产 | 1 | 管理记录 | 分页列表与真实字段契约完整；生产 Repository 固定返回 `NotWired` |
| **合计** | **1** |  |  |

关注 / 粉丝于 2026-08-02 接入，因此不在表里了。契约同样来自站点自己的
`/static/js/fans.*.js`：两份列表是 `GET /api/fans/follow`（我的关注）与 `GET /api/fans/fans`
（我的粉丝），返回 `{success, memberList}`，**都不分页**——站点一次取全量自己渲染，所以
Repository 上没有 `page` 参数。写操作是 `POST /api/fans/{add,del}`，body `{followed_member_id}`，
按钮做在公开用户页上，关注状态取自 `/api/account/getInfo/{uid}` 的 `followed` 字段。
一个坑记在这里：**未登录时列表接口回的是 200 加空 `memberList`**，不是 401，所以
`NetworkFollowRepository` 必须在发请求之前先看会话，否则未登录会显示成「还没有关注任何人」。

今日额度四项与等级进度于同日接入，因此不在表里了。`/progress` 一直被当成
「网页端渲染、后面没有端点」，实际上 `GET /api/progress/today` **不带 scope 就一次返回全部**
（发帖、评论、免费投喂三项），第四项「今日签到」和站点一样取自
`/api/attendance/board?page=1` 的 `record` 字段；等级门槛也在同一份前端代码里，是
`rank² × 100` 的公式（Lv2 起点 400、Lv3 起点 900……站点在 Lv5 封顶），不是此前以为的
「只有 400 这一档公开」。读不到时四项仍显示为未知，不伪造成 0。

点赞 / 反对 / 投喂鸡腿于 2026-08-01 接入，因此不在表里了。契约来自站点自己的
`/static/js/index.*.js`（读源码，没有发过写请求）：三者都是
`POST /api/statistics/{upvote,like,dislike}`，body `{commentId, action:"add"}`，
返回 `{success, current, coin, message}`。**站点的命名与直觉相反**——它的 `like` 是「加鸡腿」
（消耗 1 个鸡腿），`dislike` 是「反对」（消耗 2 个），免费且给对方星辰的那个叫 `upvote`；
映射写在 `ReactionAction` 里。三个动作都只有 add 没有 remove，站点自己也是在前端记住已操作过就不再发请求。

计数和「我点没点过」不额外请求：它们在帖子页 `<script id="temp-script">` 的 base64 `__config__` 里，
由 `PostConfigParser` 按 commentId 合并进每个楼层（主楼也是一条 comment）。页面没带这个 blob 时
`PostContent.reactions` 为 null，按钮禁用且不显示数字——不拿 0 冒充「还没有人点过」。

评论发布与 NodeImage 上传也不在表里了：两者都接了真实端点，契约取自 2026-07-28 在沙盒贴
（`post-841108`）和 nodeimage.com 上抓到的真实请求，不是猜的。评论走 `/api/content/new-comment`，
楼层号只在响应的 `redirectHash` 里；图床是**站外**服务 `api.nodeimage.com`，用用户自己的 API Key
认证（论坛网页版靠的是浏览器扩展，站点本身没有图床），入口在 账号设置 › 图床。

账号设置不再出现在这张表里：`NetworkAccountSettingsRepository` 的 15 个方法全部接的是站点自己的请求，
契约取自 `/setting` 的前端分块而不是猜测，逐条记录在 `docs/private/api-notes.md`（不提交）。
随之删除的还有全套“尚未接入”横幅——生产代码里已经没有会触发它的路径，留着就是一段永远不会显示的界面。

## 已有入口或表单，最终转网页完成（5 项）

这些能力没有原生接口闭环，但会把用户带到真实站点完成，不属于“假成功”。

| 能力 | 当前降级 |
|---|---|
| 修改邮箱 | 站点发验证码要 Cloudflare Turnstile 令牌，原生拿不到；联系方式页说明原因并打开 `/setting#contact` |
| 绑定 Telegram | 站点用 telegram.org 的登录挂件完成绑定，必须有浏览器环境；确认弹窗打开 `/setting#contact`，返回后自动轮询绑定状态。**解绑和状态读取是原生的** |
| 星辰转账 | App 内完成收款人、数额和确认界面，最终打开当前用户的站点星辰页提交；已填表单不会自动带入网页。站点其实有 `payment-prepare` / `send` 两个 POST 可以原生化（含 UID→昵称回显），见 `docs/private/api-notes.md`，尚未采用 |
| 邀请码购买 | App 显示真实鸡腿余额并二次确认，最终打开站点邀请页购买 |
| 检查更新 | 支持“未知/最新/发现新版”界面状态，但生产环境始终传入“未知”，按钮打开 GitHub Releases 核对 |

## 非接口缺口

| 能力 | 当前情况 |
|---|---|
| 站内贴图 | 编辑器已有五组面板，其中三组图片贴图因素材 URL 未确认而为空；两组 Unicode emoji 可用。此项是素材缺失，不计入上述 10 项接口能力 |

当前详情页没有“收藏”操作控件，因此不再把收藏计入“已有界面但接口未接入”；用户空间的收藏列表读取已接入真实接口。
收藏的写接口已经和三个表态一起定位到（`POST /api/statistics/collection`，`{postId, action:"add"|"remove"}`，
可撤销），但详情页没有控件就不算缺口，等要做的时候直接用。

站点本身没有的功能，App 也不再假装有：**移除头像**（设置页只有上传）和**邮箱已验证标记**（站点无此字段，
App 按“地址只能靠收验证码设置”推导）。前者的按钮与确认弹窗已删除。

## WebView 边界

- 登录与 Cloudflare 验证必须使用 WebView，以取得与 OkHttp 共用的 Cookie。
- NodeSeek 站内尚未原生化的页面，以及上方 3 项原生接口未闭环能力，可作为明确降级打开。
- 帖子、评论、搜索结果和隐私协议正常路径均为原生 Compose。
- 普通站外链接交给系统浏览器；内置 WebView 只允许 HTTPS 的 `nodeseek.com` 域名。

## 验证基线

提交前执行：

```bash
./gradlew spotlessCheck :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew --stop
```

2026-08-01 当前工作区快照为 575 个 JVM/Robolectric 测试（0 失败 0 跳过）；spotless、单测、lint、assemble
四项门禁全部通过，Room schema 无变化（表态状态写在既有的 `PostContent` blob 里，没有加列）。每次后续功能提交都应重新生成实际数字，不要沿用本快照冒充当前结果。
