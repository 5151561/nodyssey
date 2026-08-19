# 设计稿与实现对照

更新日期：2026-08-02

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
| f1 关于与社区 | 已按稿重做并拆页 | `ui/settings/AboutAppScreen.kt`、`AboutCommunityScreen.kt`、`ChangelogScreen.kt`、`Navigation.kt` | 软件关于仅从设置进入，社区关于仅从社区工具进入；更新在 App 内完成（查、下、装），失败时明说原因，不伪造检查结果 |
| f2 隐私协议 | 已按稿重做 | `ui/settings/PrivacyScreen.kt`、`PrivacyViewModel.kt`、`:shared` 的 `core/html/TermsParser.kt`、`data/TermsRepository.kt` | 正常路径为原生长文；站点请求或解析失败时才显示 WebView 降级 |
| f3 Telegram 绑定 | 已接入账号联系方式流程 | `ui/account/ContactScreen.kt`、`ContactViewModel.kt`、`data/account/AccountSettingsRepository.kt` | 绑定本身在网页完成（站点用 telegram.org 登录挂件），确认弹窗打开 `/setting#contact`，返回后轮询状态；绑定状态读取与解绑是原生请求 |
| f4 App 通知设置 | 已接入 | `ui/settings/NotificationSettingsScreen.kt`、`NotificationSettingsViewModel.kt`、`notifications/` | WorkManager 是周期轮询，不承诺即时推送；系统省电策略可能延后执行 |

以上代码路径均相对于 `app/src/main/java/io/github/nodyssey/`。

## f1 验收对照

- “关于 Nodyssey”和“关于 · 社区”是两个独立页面，分别位于软件设置与社区工具。
- App 身份区包含非对称圆角 NS 标识、版本名与 version code、检查更新入口。
- 检查结果是「检查更新」按钮旁的一行字：未检查 / 正在检查 / 已是最新 / 发现某版本；失败时同一行写清
  是连不上、GitHub 返回了几、读不懂回答还是写不进缓存，不留一个看不出发生了什么的“未知”。
- 更新卡**只在真有新版时才画**，不占一块地方说“没事可做”：版本号、包大小、更新说明
  （Release body，即该版本的 CHANGELOG 段落，`releaseNotesText` 只去掉 `###` 和 Full Changelog 行）、
  「下载并安装」，下载中换成百分比进度和取消，下完换成「立即安装」。
  系统还没给安装权限时先出一条说明和「去开启」；安装被系统拒绝时按状态码给出对应原因。
  卡片右下角的「在 GitHub 查看 ↗」是画板上那个“自己去下载”的出口。
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

## 批次 I

| 画板 | 实现状态 | 主要代码 | 仍需注意 |
|---|---|---|---|
| i1 收藏 · 独立页与离线下载 | 视觉与交互已按稿实现；离线下载引擎未实现 | `ui/bookmarks/`、`Navigation.kt`、`designsys/component/ThreadRow.kt`、`PlazaIcons.kt` | 列表、筛选、排序、多选、移出收藏走真实接口；离线那一整套由 `OfflineLibrary` 供给，当前实现返回“不可用”，整块离线 chrome 不画 |

## i1 组件映射

先映射再实现。左列是画板上的东西，中列是最终用的 Compose / Material 3 / designsys 组件，
右列写为什么是它——特别是那几处**没有**照抄画板的地方。

### 板一 · 收藏独立页

| 画板元素 | Compose 组件 | 说明 |
|---|---|---|
| 顶栏 返回 / 收藏 / 搜索 / ⋮ | `TopAppBar` + `IconButton` + `DropdownMenu` | 画板的 60px 高与 M3 small top app bar 的 64dp 同一档；⋮ 里放「离线管理」和「排序」 |
| 筛选 chip 全部 12 / 已下载 5 / 有新回复 3 | `FilterChip` | 选中态画板是 primary 实心，M3 默认是 secondaryContainer，用 `FilterChipDefaults.filterChipColors` 覆盖两个颜色保真；未选中沿用 `surfaceContainerLow` |
| 排序 `swap_vert` | `IconButton` + `PlazaIcons.SwapVert` | 画板 44dp，实现给满 48dp 触摸目标 |
| 离线状态条 | `Surface` + `Row` + `TextButton`「管理」 | M3 没有这个条；上下 1dp `outlineVariant` 发丝线自绘，底色 `surfaceContainerLow` |
| 列表行 | `ThreadRow`（designsys，本次加了两个槽） | 复用首页 PostRow 的几何：14/16 gutter、10dp 上下、10dp 间距、15/21 标题两行、12sp meta |
| 行内「离线版落后 3 条回复」/「下载失败 · …」 | `ThreadRow` 新增的 `supporting` 槽 | 原来只有 title 和 meta 两层，这行是独立的第三层，塞进 meta 的 `FlowRow` 会和板块 tag 抢同一行 |
| 右侧下载态列（48dp，图标 + 10sp 标签） | `ThreadRow` 新增的 `trailing` 槽 + `OfflineStateAction` | 五态一个组件：已离线 / 下载中 / 未下载 / 待同步 / 失败 |
| 下载中的进度环 | `CircularProgressIndicator`（determinate）+ 中心 `Stop` | 画板用 conic-gradient 画的环，M3 的 determinate indicator 就是它 |
| 底部浮动胶囊「全部下载 · 7 篇」 | `ExtendedFloatingActionButton` + `FabPosition.Center` | 画板 52dp 高、26dp 圆角、`primaryContainer` 底——正是 M3 extended FAB 的 tonal 形态 |

### 板二 · 多选态

| 画板元素 | Compose 组件 | 说明 |
|---|---|---|
| 顶栏「已选 3 项」+ 全选 | 同一个 `TopAppBar`，换 `colors` 与内容 | 底色换 `surfaceContainer`，导航图标换 `Close`，动作换文字按钮 |
| 长按任一行进入多选 | `ThreadRow` 新增的 `onLongClick` + `LocalHapticFeedback` | `combinedClickable`；进入多选后单击变成勾选 |
| 行首 Checkbox | `Checkbox` | 画板是 22dp / 圆角 6dp 的自绘方块，实现用 M3 原生 Checkbox（20dp / 圆角 2dp），见下方偏离项 |
| 选中行底色 | `ThreadRow(containerColor = surfaceContainerLow)` | 槽位早就在，直接用 |
| 底部工具栏（64dp，左文右按钮 + 移出） | `Surface`（`surfaceContainerHigh`，26dp，`shadowElevation`）+ `Button` + `IconButton` | 不是 M3 `BottomAppBar`：它贴边通栏，画板这个是浮起来的圆角条，两侧留 16dp |
| 移出收藏 | `IconButton(tint = error)` + `Snackbar` 撤销 | 按稿不做二次确认，撤销走 Snackbar |

### 板三 · 离线管理 bottom sheet

| 画板元素 | Compose 组件 | 说明 |
|---|---|---|
| sheet 本体 + 拖拽把手 | `ModalBottomSheet` | 28dp 顶角与 32×4 把手都是 M3 默认值，不用改 |
| 12.4 MB / 5 篇 · 可用 3.2 GB | `Row(Alignment.Bottom)` + 两个 `Text` | 数字用 `TABULAR_FIGURES` |
| 占用分段条（正文 / 图片两段） | 自绘 `Row` + `RoundedCornerShape(6dp)` | M3 的 `LinearProgressIndicator` 只有「一段 + 轨道」，画不出两段带间隙的分段 |
| 图例（8dp 方块 + 文字） | `Row` + `Spacer(background)` | — |
| 四行设置组 | `GroupedColumn` + `GroupedRow`（designsys） | 图标 + 标题 + 副标题 + 尾部值/开关，正是设置页那套行 |
| 三个开关 | `Switch`，挂在 `GroupedRow` 的 `trailing` 上 | — |
| 「离线内容保留 30 天」 | `GroupedRow(value = …, onClick = …)` + `AlertDialog` + `ChoiceRow` | 二级选择沿用浏览历史的保留条数弹窗写法 |
| 清空离线内容 | `TextButton(error)` + `AlertDialog` 二次确认 | 危险动作，按稿走确认 |
| 完成 | `Button` | — |

### 偏离画板的四处，以及为什么

1. **板块 tag 的配色沿用 App 现有的四族分组**，不采用画板里「技术 = #DAE2FF、交易 = #BDEAF0」这套。
   `BoardTag` 是首页、历史、搜索、空间页共用的一个组件，十五个板块归四色是它的整个设计；
   为一块画板给它第二套色，代价是全 App 的列表从此有两种 tag。
2. **头像用 `AvatarShape`（15% 圆角）与 `listAvatarSize()` 实测尺寸**，不用画板的 34px 正圆。
   同上：一个 App 里头像换了形状比差 2dp 显眼得多，而那个尺寸是量出来的，跟着系统字号走。
3. **设置组用 `GroupedColumn` 的 2dp 接缝**，不用画板的 1px 分隔线。设置页、社区工具页都是接缝，
   这里画分隔线就成了第二种分组写法。
4. **Checkbox 用 M3 原生**（20dp / 圆角 2dp / 48dp 触摸目标），不复刻画板的 22dp / 圆角 6dp 方块。
   本次明确要走 Compose 原生，而勾选框是无障碍与触摸目标都由框架兜底的那类控件。

5. **离线状态条长到 48dp**，画板是 34dp。那一行右端的「管理」是要点的，34dp 不是任何人能点中的目标，
   而往 34dp 的条里塞一个 48dp 的按钮只会把条撑高。于是整条都可点，高度按触摸目标来。

6. **多选工具栏不重复写「已选 N 项」**。画板那行是「3 项 · 约 4.6 MB」，体积由离线引擎估算；
   引擎给不出估算时这行只剩计数，而顶栏隔着一屏已经写了同一个数字，于是这行直接不画。

7. **「全部下载」胶囊用 `ExtendedFloatingActionButton` 的 content 重载**，不用 `icon` / `text` 重载。
   后者把文字包在一个不向语义树暴露内容的动画容器里，读屏软件念到的是一个没有名字的按钮——
   这是写 Compose 测试时才发现的，不是设计取舍。

8. **多了一个画板上没有的搜索态**。画板给了搜索图标但没给搜索面板；因为整份收藏都在内存里，
   按标题和作者本地过滤是真的即时，就做在顶栏里，没有单开一个目的地。

### 真机上发现并修掉的两处

- `GroupedRow` 的标题列和 `value` 都写了 `weight(1f)`，剩余宽度被对半分：「30 天」停在行中间，
  副标题只剩半行宽度就折行。改成 `value` 不参与权重、只加一个 120dp 上限。这是 `:designsys` 的
  组件，「账户与成长」「社区工具」那几页跟着一起正。
- 离线管理面板加了 `verticalScroll`。bottom sheet 是半高打开的，字号大一点时「清空 / 完成」
  落在屏幕外，而且没有任何提示说要先把 sheet 拖上来。

### 一处没查清的

进入多选的那一帧，选中行的底色没盖住 meta 行的最后一行文字。只在一张截图里出现过一次，
之后复现不出来，怀疑是 `animateItem()` 的中间帧——但没查实，不写结论。

### 顺手修的一处既有问题

`app/src/test/java/io/github/nodyssey/data/proxy/ProxySettingsTest.kt:56` 引用了一个不存在的
`context`，`:app:compileDebugUnitTestKotlin` 因此在本次改动之前就编不过（Kotlin 2.2 把 `context`
当软关键字，报的是 `Function invocation 'context(...)' expected`，不是「未解析的引用」，所以不好认）。
改成和同文件其余用法一致的 `store.dataStore`。与 i1 无关，只是不修就跑不了测试门禁。

`ProfileViewModelTest` 里私有的 `NoOpPostRepository` 提升到了 `data/NoOpPostRepository.kt`，
改成 internal open class，本次的 `BookmarksViewModelTest` 继承它只覆盖 `setCollected`。

### 收藏行的 meta 是空的（既有问题，未修）

真机上收藏行只有标题：板块 tag、作者、回复数、时间全都不画。首页同样的 `ThreadRow` 这些都在，
所以是 `UserSpaceRepository.toSpacePost` 在**收藏接口**的 payload 上没命中字段名——
`category_title` / `member_name` / `comments` 那几个候选名都是按其它端点猜的。
空间页的收藏 tab 一样是空的，所以不是本次改出来的。要修得先抓一次 `/api/…/collection` 的真实返回。

### 本次没有实现的部分

画板 note 里「App 增强」那一整段——真正的离线下载引擎——不在本次范围：
下载队列与进度、正文与图片落盘、落后回复的增量同步、占用统计、保留期限的清理任务、仅 Wi-Fi 条件。
现有的 `post_details` 缓存是**读到哪缓存到哪的窗口缓存**，且完全不存图片，
拿它冒充「已离线」会是一个站不住的说法。

因此离线相关的一切都从 `OfflineLibrary` 取值，它有一个 `isAvailable`：
为 false 时状态条、每行的下载态列、「全部下载」胶囊、离线管理入口整块不画，
收藏页就是一个干净的列表 + 筛选 + 多选。引擎落地后把实现换掉，UI 一行不用改。

## i1 验收对照

- 收藏是从「我的」进入的**独立页**，不再是空间页里的一个 tab（空间页的 tab 保留，供从他人主页横切）。
- 筛选 chip 三个：全部 / 已下载 / 有新回复，计数跟着列表走；后两个在 `OfflineLibrary` 不可用时不画。
- 每行右侧一个下载态，五种：已离线 ✓ / 下载中进度环 + 百分比 / 未下载 ↓ / 待同步 / 失败可重试。
- 「离线版落后 N 条回复」用 primary，「下载失败 · 原因」用 error，各占标题下的独立一行。
- 长按进入多选：顶栏变计数 + 全选，行首出 Checkbox，下载态列让位，底部浮起工具栏。
- 移出收藏不做二次确认，走 Snackbar 撤销；清空离线内容走 `AlertDialog` 二次确认。
- 「全部下载 · N 篇」只统计未下载项（未下载 + 失败），已离线和下载中的不计。
- 多选工具栏的体积是本地估算，文案里写明「约」；估算拿不到时那一行不画（见偏离项 6）。

## 修改流程

1. 先读 `design/src/<id>.html`、对应 dark 稿和 `design/meta/<id>.json`。
2. 实现时保留 Screen 纯状态 + callback 边界，并使用现有主题 token、间距和语义组件。
3. 更新本文件与 `implementation-status.md`，明确“视觉已完成”和“数据/写操作已接入”的差别。
4. UI 改动至少补 360×800 Compose/Robolectric 用例；提交前执行仓库完整门禁。
