# 设计稿与实现对照

更新日期：2026-08-19

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

## 批次 J

| 画板 | 实现状态 | 主要代码 | 仍需注意 |
|---|---|---|---|
| j1 主题设置 · 配色 | 已按稿实现（四处替代，见下） | `ui/settings/theme/`（`ThemeSettingsScreen.kt`、`DynamicColorScreen.kt`、`SeedColorSheet.kt`、`ThemePreviewCard.kt`、`ThemeSwatch.kt`、`ThemePresets.kt`、`WallpaperPalette.kt`）、`designsys/theme/SeedColor.kt`、`Theme.kt` | 全部为 App 增强，站点无主题接口；六个预设、壁纸候选和自定义种子色都走同一个生成器，手调的品牌配色已退役 |

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
| i1 收藏 · 独立页与离线下载 | 已按稿实现，离线下载引擎已接入 | `ui/bookmarks/`、`data/offline/`、`data/local/Offline*.kt`、`Navigation.kt`、`designsys/component/ThreadRow.kt` | 列表、筛选、排序、多选、移出收藏走真实接口；离线下载、离线阅读、占用统计、保留期限清理、自动补新回复都是真的落盘与真的请求 |

## i1 组件映射

先映射再实现。左列是画板上的东西，中列是最终用的 Compose / Material 3 / designsys 组件，
右列写为什么是它——特别是那几处**没有**照抄画板的地方。

### 板一 · 收藏独立页

| 画板元素 | Compose 组件 | 说明 |
|---|---|---|
| 顶栏 返回 / 收藏 / 搜索 / ⋮ | `OneHandTopAppBar`（designsys）+ `IconButton` | 见下方偏离项 9：换成单手模式的可拉大标题栏，副标题写「已离线 N 篇 · 占用 X」；⋮ 换成直接的「管理」按钮，排序在筛选行自己那一个 |
| 筛选 chip 全部 12 / 已下载 5 / 有新回复 3 | `FilterChip` | 选中态画板是 primary 实心，M3 默认是 secondaryContainer，用 `FilterChipDefaults.filterChipColors` 覆盖两个颜色保真；未选中沿用 `surfaceContainerLow` |
| 排序 `swap_vert` | `IconButton` + `PlazaIcons.SwapVert` | 画板 44dp，实现给满 48dp 触摸目标 |
| 离线状态条 | 不画，见偏离项 9 | 「已离线 N 篇 · 占用 X」搬到顶栏副标题；「仅 Wi-Fi 下载」和「管理」按钮都去掉 |
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

5. **~~离线状态条长到 48dp~~ —— 整条删了**，见偏离项 9。

6. **多选工具栏不重复写「已选 N 项」**。画板那行是「3 项 · 约 4.6 MB」，体积由离线引擎估算；
   引擎给不出估算时这行只剩计数，而顶栏隔着一屏已经写了同一个数字，于是这行直接不画。

7. **「全部下载」胶囊用 `ExtendedFloatingActionButton` 的 content 重载**，不用 `icon` / `text` 重载。
   后者把文字包在一个不向语义树暴露内容的动画容器里，读屏软件念到的是一个没有名字的按钮——
   这是写 Compose 测试时才发现的，不是设计取舍。

8. **多了一个画板上没有的搜索态**。画板给了搜索图标但没给搜索面板；因为整份收藏都在内存里，
   按标题和作者本地过滤是真的即时，就做在顶栏里，没有单开一个目的地。

9. **顶栏换成 `OneHandTopAppBar`，离线状态条整条删掉。** 三件事一起做的，因为它们是同一件：

   - 画板那条独立的离线状态条（「已离线 5 篇 · 占用 12.4 MB · 仅 Wi-Fi 下载」+「管理」）在筛选 chip
     和列表之间占一整行，说的却是一句关于整个页面的**陈述**而不是一个控件。搬到顶栏副标题里就不占
     行了——这正是副标题的用途。「仅 Wi-Fi 下载」跟着去掉：它是离线管理面板里的一个开关，不需要在
     列表上常驻复述。已离线 0 篇时副标题整个不画，理由和阅读历史一样：一行说不出东西的字仍然占一行。
   - ⋮ 换成直接的「管理」图标按钮。画板往 ⋮ 里放了「离线管理」和「排序」两项，而排序在实现里有自己
     的 `SwapVert`（就在筛选行末尾），于是 ⋮ 里只剩一项——一次点击换一个只有一行的菜单。
   - 页面本身接上单手模式（批次 #92 那套）。可拉的大标题栏只在**普通态**用：多选有自己的工具栏，
     搜索开着键盘、要的是尽可能多的行，两者都保留 64dp 的普通栏。`nestedScroll` 连接跟着同一个条件
     挂——一条不在屏幕上的 bar 不能继续吃掉列表最前面那两百多 dp 的滚动。

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

### 收藏行的 meta（后补，已用本地记忆补上）

真机上收藏行只有标题：板块 tag、作者、回复数、时间全都不画。首页同样的 `ThreadRow` 这些都在，
所以是 `UserSpaceRepository.toSpacePost` 在**收藏接口**的 payload 上没命中字段名——
`category_title` / `member_name` / `comments` 那几个候选名都是按其它端点猜的。

根治要抓一次 `/api/…/collection` 的真实返回，那个仍然没做。这次走的是另一条路：
**站点说过的话，App 自己记下来。** 新表 `collected_post_meta`（schema v12），每一列可空，
每次写入只补空缺、不覆盖已知，三处写入：

- **打开一篇已收藏的帖子时**（`rememberFromPage`）。走得最多的一条，也是让列表在使用中自己长
  完整的那条：点开一行空标题，打开的正是那个写着板块、作者、头像和发帖时间的页面，等读者退回来
  时这一行已经补齐了。条件是页面自己的 `collected`，不是请求——它对任何人打开的任何帖子都会跑，
  而这张表只关心这个账号收藏的那些。`collected` 为 null（页面没带 `__config__`，即未登录的读取）
  不当作「是」，未登录的读者也没有收藏可言。
- **按下星标时**（`rememberCollectedThread`）。这是 App 手上信息最全的一刻，也是最后一刻：
  `list-collection` 不会再说一遍，而带着这些字段的 `post_read_marks` 会被「浏览历史保留条数」
  修掉。三个来源合并而不是排序取一，因为它们知道的东西不一样——feed 行有板块和站点的回复数，
  帖子的楼主楼有作者、头像和发帖时间，读标记是这两者在帖子离开 feed 缓存之后剩下的部分。
- **离线下载完成时**。这是**唯一**能补上「早年在网页端收藏、App 从没打开过」那批帖子的路径：
  本机从没见过它，而下载抓的正是那几页。写在整篇下完之后而不是每页之后，免得一次半途失败的下载
  让列表拿一页它随即又丢掉的内容去描述这篇帖子。
- **收藏列表每次加载时**，把这一趟接口**确实**给了的字段存下来。

头像存两列：`avatarUrl` 是页面实际渲染的那个地址，`authorUid` 是所有来源都带的持久事实。
取的时候页面地址优先、uid 推导兜底——`/avatar/<uid>.png` 不是猜的，是本站所有账号头像（上传的和
生成的）的地址，App 另外七处也是这么拼的；但离线下载是按页面给的那个 URL 存的文件，所以断网时
只有问同一个字符串才拿得到图。

读的时候站点的答案永远优先，本地只填空。所有值都是站点在别处说过的，没有一个是推出来的。

一处没解决：`OfflineLibrary.noteReplyCounts` 只收站点这一趟给的回复数。接口不给数，
「离线版落后 N 条回复」就一直安静——宁可不说，不拿本地存的旧数字冒充「站点现在说的」。

### 离线下载引擎（后补，已实现）

画板 note 里「App 增强」那一整段现在是真的：下载队列与进度、正文与图片落盘、落后回复的增量同步、
占用统计、保留期限的清理任务、仅 Wi-Fi 条件。实现在 `data/offline/`，落盘在 Room 的三张新表
（`offline_threads` / `offline_comments` / `offline_images`，schema v11）。

几处关键选择：

1. **不复用 `post_details`。** 那是个窗口缓存：`saveThreadPage(replacesWindow = true)` 会把窗口以外
   的页全删掉，而「在线打开一篇已下载的帖子」正好会触发它。给那些行加一个 pin 位，等于许一个下次
   刷新就会毁约的诺。所以离线内容自己三张表，只由下载引擎写、只由用户/保留期限/清空删。

2. **队列在 Room 里，不在内存里。** WorkManager 跑的是「把队列排干」这件事，进程被杀、Wi-Fi 明天才
   来都不丢东西；丢的只是时机。`nextQueued` 把 `DOWNLOADING` 也算进去，就是为了让被系统掐掉的那一
   篇下次能被重新捡起来。

3. **离线阅读不需要详情页知道任何事。** `OfflineFirstPostRepository.loadThreadPage` 在请求失败且
   失败原因是「连不上站点」时，从 `OfflineThreadReader` 取同一页，按**下载当时的时间戳**写进
   `post_details`。写 now 会让缓存把上周的页当成刚拉的，刷新就此不会发生。站点明确拒绝
   （未登录、等级不够、帖子没了）不走这条路——那是回答，不是连不上。

4. **图片按 URL 的 SHA-256 命名，存 `filesDir/offline/images`。** 内容寻址的是 *URL* 而不是字节，
   因为 Coil 的拦截器要在每一次画图片时同步回答「这张有没有」，一次数据库往返不能待在那个位置。
   拦截器排在流量策略拦截器**前面**：已经在本机的图片没有流量可省，「仅 Wi-Fi 加载图片」不该让
   专门下下来在路上看的帖子开天窗。两篇帖子引同一张图共用一个文件，`offline_images` 各存一行，
   所以体积统计按 *文件* 去重（`SELECT DISTINCT fileName, bytes`），删帖只在没人再引用时删文件。

5. **正文体积用 SQL 量，不累加。** `LENGTH(CAST(content AS BLOB))`——`LENGTH` 对 TEXT 数的是字符，
   中文帖子会按实际占用的三分之一记账。不累加是因为增量同步会重写最后一页，加法会把那页记两遍。

6. **「离线版落后 N 条回复」必须被告知。** 存下来的副本知道自己有几条回复，完全不知道站点后来多了
   几条。收藏列表每次加载时手上正好两个数都有，所以由它调 `noteReplyCounts` 递过去，而不是让引擎
   再发一轮请求去问 App 刚拿到的东西。

7. **翻页之间有 400ms 间隔。** 一次排干十几篇收藏就是上百个请求，这是全 App 唯一一处会用站点回答
   的最快速度连续要页面的地方。没人在看这个过程，间隔不花任何人的时间。

8. **剩余空间读到 0 当作「读不出来」。** `StatFs` 对一个它建模不了的路径就返回 0，和真的满了分不
   出来。于是不猜，让写入本身当裁判——它本来就得是裁判，检查和写入之间没法预留空间。

`OfflineLibrary.isAvailable` 保留：它仍然是 UI 唯一的分支点，画板上那套「引擎不在时整块不画」的
行为一行没改。删掉的是 `UnavailableOfflineLibrary`，接完之后没有任何地方再引用它。

### 仍未做的部分

- 收藏接口的真实字段名仍然没抓过。上一节那套是本地记忆，能补上 App 见过的帖子；
  一篇在网页端收藏、在 App 里既没打开过也没下载过的帖子，行里依然只有标题。
- 「离线版落后 N 条回复」依赖收藏接口给回复数，接口不给就一直安静。
- 详情页没有「你正在看离线副本」的提示。画板没有这个元素，本次也不自己加。

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

## j1 验收对照

- **替代 0：明暗没有搬走。** 画板把「跟随系统 / 浅色 / 深色」放在主题页开头，实现里它留在
  「设置 › 外观」第一格，主题入口排在它下面。它是全套主题里唯一天天要动的——屋里光线变了就翻一次——
  把它挪到两层之下换取画板完整，是把稿子里不该守的那一半守住了。主题页那一节整个不画。
- 「设置 › 外观」是「明暗」加一个「主题」入口，配色相关的其余控件不再直接摆在设置列表里。
- 配色来源是三块 96dp 瓦片（预设 / 动态取色 / 自定义），各自记住上次的种子色；
  已选中的瓦片再点一次才打开它背后的东西（动态取色的页面、自定义的底部弹层），
  这样「把旧颜色换回来」和「我要改颜色」是两次不同的点击。
- 预设是 3×2 双色圆点，整格可点（圆点、名称、色值都是同一个目标）；
  选中态是离开圆点的两层描环加勾。
- 预设这一节只在「预设」是当前来源时展开，选了动态取色或自定义就收起来：两行 56dp 圆点占掉这页大半，
  而那时六个里没有一个是生效的颜色。收起的是控件不是答案——瓦片上仍然写着它会还原成哪个预设。
  其余各节不跟着收，「我的主题」尤其不能收：新建是颜色的来路，收起来就从它所属的来源够不着了。
- 我的主题是保存过的自定义种子色，长按出重命名 / 删除；上限 12 条，超出从最早的丢。
- 色彩风格五档对应 MaterialKolor 的 TonalSpot / Vibrant / Expressive / Neutral / Monochrome，
  对三种配色来源一律生效；`SeedColorSchemeTest` 逐档验证文字对比度不低于 4.5:1。
- 预览卡读当前主题的 token 重绘，字号写死不跟正文字号走——它是一张 App 的缩略图，不是 App 的一部分。
- **替代 1：壁纸缩略图未画。** targetSdk 36 下 `WallpaperManager.getDrawable()` 需要
  `MANAGE_EXTERNAL_STORAGE`，为一张缩略图申请全文件访问不划算；壁纸配色本身不需要权限，
  所以候选色以下的内容原样保留，候选色直接顶到页面开头。
- **替代 2：吸管改成从相册取色。** Android 没有系统级屏幕取色 API，点吸管走 PhotoPicker（免权限）
  选一张图，再点图上任意位置采样。
- 自定义种子色的调色面板画的是 HCT 的鲜艳度 × 明度，不是 HSV 的饱和度 × 明度：
  生成器只读种子的色相和鲜艳度，HSV 面板上竖直拖半屏会几乎不改变 App 的观感。
  外观与设计稿一致，但面板上每个位置都对应一套能分辨出来的配色。
