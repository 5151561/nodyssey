# 设计稿与实现对照

更新日期：2026-07-28

`design/boards.json` 的 `status` 表示**画板制作状态**，不表示 Android 功能已经可用。
App 的总体真实状态以 [`implementation-status.md`](implementation-status.md) 为准；
本文记录已完成核对的画板与代码入口，防止实现脱离设计稿。

## 批次 C

| 画板 | 实现状态 | 主要代码 | 仍需注意 |
|---|---|---|---|
| c7 我的 · 未登录 v2 | 已按稿实现 | `ui/profile/ProfileScreen.kt`、`ui/common/NodysseyIcons.kt` | 登录继续使用受限 WebView 并共享 Cookie；设置与社区工具是未登录可用入口，页面不伪造账号数据 |

## 批次 F

| 画板 | 实现状态 | 主要代码 | 仍需注意 |
|---|---|---|---|
| f1 关于与社区 | 已按稿重做并拆页 | `ui/settings/AboutAppScreen.kt`、`AboutCommunityScreen.kt`、`ChangelogScreen.kt`、`Navigation.kt` | 软件关于仅从设置进入，社区关于仅从社区工具进入；当前更新按钮打开 GitHub Releases，不伪造检查结果 |
| f2 隐私协议 | 已按稿重做 | `ui/settings/PrivacyScreen.kt`、`PrivacyViewModel.kt`、`core/html/TermsParser.kt`、`data/TermsRepository.kt` | 正常路径为原生长文；站点请求或解析失败时才显示 WebView 降级 |
| f3 Telegram 绑定 | 已接入账号联系方式流程 | `ui/account/ContactScreen.kt`、`ContactViewModel.kt`、`data/account/AccountSettingsRepository.kt` | 绑定本身在网页完成（站点用 telegram.org 登录挂件），确认弹窗打开 `/setting#contact`，返回后轮询状态；绑定状态读取与解绑是原生请求 |
| f4 App 通知设置 | 已接入 | `ui/settings/NotificationSettingsScreen.kt`、`NotificationSettingsViewModel.kt`、`notifications/` | WorkManager 是周期轮询，不承诺即时推送；系统省电策略可能延后执行 |

以上代码路径均相对于 `app/src/main/java/io/github/nodyssey/`。

## f1 验收对照

- “关于 Nodyssey”和“关于 · 社区”是两个独立页面，分别位于软件设置与社区工具。
- App 身份区包含非对称圆角 NS 标识、版本名与 version code、检查更新入口。
- 非官方声明使用 tonal card；项目主页、问题反馈为系统外链，更新日志与开源许可为 App 内页面。
- 社区段包含关于本站、隐私协议、RSS 复制、电报频道、电报群组、邮箱与 DeepFlood。
- 论坛统计卡暂时隐藏；解析、Repository、ViewModel 和卡片代码保留，等待可靠数据源后再接回，不展示静态人数或失败占位。
- LowEndTalk、LowEndSpirit、HostLoc、ServerHunter 使用 chips；没有设计稿之外的 Telegram 客服行。
- 颜色、字阶与明暗模式来自 `MaterialTheme` token，不把画板 hex 复制进 Compose。

## f2 验收对照

- 顶栏包含返回、标题和打开原文；顶栏与正文之间有分隔线。
- 文档标题、生效日期、来源、H2/H3、段落、有序/无序列表按原生 Compose 连续排版。
- 正文可继续滚动时显示底部渐隐提示，到达末尾后提示消失。
- 页面内容来自 NodeSeek 原文，不把约 6.9k 字复制进资源文件；标题、日期、段落和列表解析由 JVM 测试覆盖。
- 原文外链交给浏览器（默认 Custom Tab，设置里可改成系统浏览器）；受限 WebView 只在原生加载或解析失败后由用户主动选择。

## 搜索

| 画板 | 实现状态 | 主要代码 | 仍需注意 |
|---|---|---|---|
| 6e 搜索 · 先指定再搜索 | 已按稿重做 | `ui/search/SearchScreen.kt` | 输入框常驻顶部，帖子 Tab 下方是版块单选 chip 组，排序只在结果页 |
| 6f 搜索 · 帖子结果 | 已按稿对齐 | `ui/search/SearchScreen.kt`、`ui/postlist/PostListScreen.kt`、`model/SearchModels.kt` | 版块与排序都是服务端参数；「最后回复人」还没有数据来源 |

## 6e 验收对照

- 输入框固定在最顶部并始终可编辑：直接使用 `SearchBarDefaults.InputField`，不再是
  `AppBarWithSearch` + `ExpandedFullScreenSearchBar` 的折叠/展开对。折叠态的搜索栏会用
  `DisableSoftKeyboard` 包住输入框（那套交互里打字发生在覆盖其上的展开副本中），本页没有折叠态，
  所以输入框必须自己接键盘；容器色也要自己上，因为折叠态的容器本来由外层 Surface 绘制。
- 帖子 Tab：`版块` 标题 + 「单选 · 只能指定一个」+ `FlowRow` 里的 `FilterChip` 单选组，
  服务端 `category` 只接受一个。placeholder 跟随所选版块变为「在 X 版块搜帖子」。
- chip **不带选中勾**（画板上有）：勾让选中的 chip 宽约 26dp，折行组里它会把后面所有 chip 重排，
  窄屏上点一次版块整组在三行和四行之间跳。填充容器对描边空心已经说清哪个是选中，
  `FilterChip` 的 selected 语义照样让 TalkBack 报「已选中」，勾在 Material 里本就是可选装饰。
- 用户 Tab：无版块，也不放说明卡（那是设计稿给人看的注解），直接进搜索历史。
- 搜索历史用 `ListItem` 两行：关键词 + 「类型 · 版块」，同词不同版块是两条记录。
- 与画板的差异：**没有返回键**——本 App 的搜索是底部导航的一级 Tab，退出靠切 Tab，
  画板是按二级页面画的；帖子 Tab **不显示计数**，`/search` 不返回总数，已加载行数不是总数。

## 6f 验收对照

- 排序就是站点的两档「新评论 / 新帖子」= `sortBy=replyTime|postTime`，文案与首页统一为
  「按回复时间 / 按发帖时间」。原来的 `SearchSort.RELEVANCE`（显示「相关度」）是假的：它发出去的
  就是 `replyTime`，站点没有相关度排序。`SearchSort` 已删除，搜索直接用 `FeedSort`，默认
  `POST_TIME`——`/search` 缺省即 `postTime`。旧记录里的 `RELEVANCE`/`TIME` 在读取时折回这两档。
- 版块 chip 用 `FilterChip`：指定了版块才是选中态（带勾），点开仍是 6g 的版块范围 Sheet。
- 结果标题里的关键词高亮：`PostRow(highlight = )`，字面、忽略大小写；其余列表传 null，不受影响。
- 结果列表支持下拉刷新（`PullToRefreshBox`）与追加分页，行间有分隔线。
- 与画板的差异：结果页顶部**仍是可编辑的输入框**，不是「返回 + 关键词 + ×」的标题栏——本 App
  的 6e/6f 是同一个页面的两个状态，× 清空关键词即回到 6e；行内**没有「最后回复人」**，
  `PostSummary` 目前不解析这个字段，补它要动解析器和 Room schema，超出本次范围。

## 修改流程

1. 先读 `design/src/<id>.html`、对应 dark 稿和 `design/meta/<id>.json`。
2. 实现时保留 Screen 纯状态 + callback 边界，并使用现有主题 token、间距和语义组件。
3. 更新本文件与 `implementation-status.md`，明确“视觉已完成”和“数据/写操作已接入”的差别。
4. UI 改动至少补 360×800 Compose/Robolectric 用例；提交前执行仓库完整门禁。
