# 角色预设 Token 补稿需求(j2 后续)

> 交付对象:Claude Design。前置:j2 画板「主题预设 · 角色配色」与 `tokens/presets.css`
> (五套 × 亮暗 × 17 项,已定稿,本文**不动**)。
>
> 产出:`tokens/presets.css` 的增补 —— 每套每态再加 **11 个变量**。不新增画板,不改已定稿的值;
> 如果顺手,五张样卡上多画一个 Snackbar 和一个底部弹层用来验反色三件套。
>
> 对应代码:`designsys/…/theme/CharacterPalette.kt`。
>
> **已交付**:画板补齐了 110 个值,五张样卡各加了一条 Snackbar 验反色三件套;
> 代码侧 `CharacterTokens` 从 17 项扩到 28 项,§5 逐条核过(见文末)。

## 0. 为什么要补

Compose 的 `ColorScheme` 有 **47 个角色**,j2 给了 17 个。剩下 30 个在代码里必须有值,
而 `lightColorScheme()` 的默认值是 **Material 基线紫**(暗色基线 surface 是 #141218 那种带紫的黑)。

现在的兜底做法:拿该套**亮色 primary** 当种子跑一遍生成器铺底,再把 17 个手工 token 盖上去。
这挡住了基线紫,但留下一道接缝 —— 生成器铺出来的中性面,和稿子手写的
`slo / sco / shi / shst` 那条阶梯**不在同一条色阶上**,挨着放会差半档。

本文把剩下 30 个分成「必补」和「可以不补」。补完之后,这条路上的生成器只兜 error 和
本 App 一处都没画到的角色,中性面和次/第三色全部由稿子说了算。

## 1. j2 已给(17 项 · 不用重做)

| CSS var | M3 角色 |
|---|---|
| `--p` `--onp` `--pc` `--onpc` | primary / onPrimary / primaryContainer / onPrimaryContainer |
| `--sc` `--onsc` | secondaryContainer / onSecondaryContainer |
| `--tc` `--ontc` | tertiaryContainer / onTertiaryContainer |
| `--bg` `--onbg` | surface / onSurface(background / onBackground 代码里同值,画板不用单列) |
| `--slo` `--sco` `--shi` `--shst` | surfaceContainerLow / Container / High / Highest |
| `--osv` `--ol` `--ov` | onSurfaceVariant / outline / outlineVariant |

`--sig` `--sigalt` `--acc` `--accalt` 是 signature 层,不属于 M3,现在代码里没有消费者,
本文不涉及(要接的话另开一单,先定谁用)。

## 2. 必补(11 项 × 五套 × 亮暗)

### 2.1 中性面阶梯的另外四档

j2 给了阶梯中间四档,两头和一个变体没给。这四个是接缝的来源,优先级最高。

| 建议 var | M3 角色 | 是什么 | 参考色阶(N=中性,NV=中性变体) | App 里谁画到 |
|---|---|---|---|---|
| `--slst` | surfaceContainerLowest | 阶梯最外一档,比 `--slo` 更「浮」 | 亮 N-100 / 暗 N-4 | 6 处直接引用 |
| `--sdim` | surfaceDim | 阶梯的暗端,大面积压底用 | 亮 N-87 / 暗 N-6 | M3 组件内部 |
| `--sbri` | surfaceBright | 阶梯的亮端 | 亮 N-98 / 暗 N-24 | M3 组件内部 |
| `--sv` | surfaceVariant | 中性**变体**面,和 `--osv` 成对 | 亮 NV-90 / 暗 NV-30 | 1 处 + 组件内部 |

**关系约束**(验收看这个,不看具体色值):

- 亮色:`--slst`(最浅,通常纯白)→ `--slo` → `--sco` → `--shi` → `--shst`(最深),单调不回头;
  `--sbri` ≈ `--bg`,`--sdim` 比 `--bg` 深一档但比 `--shst` 浅。
- 暗色:方向反过来 —— `--slst`(最深,比 `--bg` 还深一点)→ `--slo` → … → `--shst`(最浅);
  `--sdim` ≈ `--bg`,`--sbri` 是整条阶梯最浅的。
- `--sv` 跟 `--osv` 是一对,`--osv` 写在 `--sv` 上要 ≥4.5:1。

### 2.2 次色与第三色的**本色**

j2 只给了 container,没给本色。现在代码里是从 container 按 HCT 同色相搬到
tone 40(亮)/ 80(暗)推出来的 —— 能用,但**这是设计判断,不该由代码替设计做**。

| 建议 var | M3 角色 | 现在的临时推法 |
|---|---|---|
| `--sec` `--onsec` | secondary / onSecondary | 从 `--sc` 同色相搬到 tone 40 / 100(亮),80 / 20(暗) |
| `--ter` `--onter` | tertiary / onTertiary | 从 `--tc` 同理 |

**镜音双子这套要特别定一下**:稿子写的是「次级容器=橙,强调=黑灰」,
`--sc` 是橙 #FFDCC8、`--tc` 是黑灰 #DEE3EA。按同色相推,`--sec` 就是橙本色、`--ter` 是灰蓝本色。
但「黄×橙×黑灰」里黄才是本体,而黄现在整个在 primary 一侧 —— 次色本体到底跟不跟 container
同色相,是这套配色的辨识度问题,请在稿子上明确。其余四套同色相推没什么争议,但也请过一遍眼。

### 2.3 反色三件套

Snackbar 用它反着画(浅色主题里弹深色条)。本 App 有 **16 个文件**用 Snackbar,
所以这三个是实打实会出现在屏幕上的。

| 建议 var | M3 角色 | 是什么 |
|---|---|---|
| `--isf` | inverseSurface | 和 `--bg` 明暗相反的那块面 |
| `--ionsf` | inverseOnSurface | 写在 `--isf` 上的字 |
| `--ip` | inversePrimary | 写在 `--isf` 上的**动作**字(Snackbar 右边那个按钮) |

参考色阶:`--isf` 亮 N-20 / 暗 N-90;`--ionsf` 亮 N-95 / 暗 N-20;`--ip` 亮 P-80 / 暗 P-40。
验收:`--ionsf` 和 `--ip` 都写在 `--isf` 上,各自 ≥4.5:1。

## 3. 可以不补(理由附上)

| 角色 | 为什么不补 |
|---|---|
| error / onError / errorContainer / onErrorContainer | 红就是红。「操作失败」不该因为换了角色主题而变色,五套共用生成器给的那套即可 |
| scrim | M3 基线在亮暗两态都是纯黑 #000000,靠 alpha 调深浅,没有角色发挥余地 |
| surfaceTint | M3 默认就等于 primary,`--p` 已给 |
| background / onBackground | 代码里直接等于 surface / onSurface,画板单列反而多一处会走样的地方 |
| 12 个 `*Fixed` 角色 | 本 App 一处都没画到(全仓 grep 为 0),补了是死值 |

## 4. 交付格式

沿用 `tokens/presets.css` 现在的写法,在每套的 `.theme-<id>` / `.theme-<id>-dark` 里追加这 11 个。
以初音未来亮色为例(色值是占位,等稿子定):

```css
.theme-miku {
  /* …已有 17 项不动… */
  --slst: #FFFFFF; --sdim: #D8E4E3; --sbri: #F8FCFC; --sv: #DBE4E4;
  --sec: #4A6069; --onsec: #FFFFFF; --ter: #8C4A63; --onter: #FFFFFF;
  --isf: #2E3838; --ionsf: #EFF5F4; --ip: #53D7CE;
}
```

## 5. 验收清单

- [x] 五套 × 亮暗 × 11 项 = 110 个值齐全,没有沿用另一套的
- [x] 对比度沿用 j2 标准:所有 `on-` 配对 ≥4.5:1,正文 ≥7:1
- [x] 中性面阶梯 §2.1 的单调性成立,亮暗方向相反
- [x] `--ionsf` / `--ip` 落在 `--isf` 上都 ≥4.5:1
- [x] 镜音双子的 `--sec` / `--ter` 有一句明确说明(跟不跟 container 同色相)
- [x] 比例原则不破:这 11 个基本都在中性面和反色上,不应该把角色色再往外铺

## 6. 交付结果(核对记录)

画板产出在 `tokens/presets.css`,110 个值全部齐备且互不重复;代码侧 `CharacterTokens`
由 17 个字段扩到 28 个,`toColorScheme` 直接写这 28 项,生成器只剩 error 一族、`scrim`
和 12 个 `*Fixed` 兜底。原先从 container 按 HCT 推 `secondary` / `tertiary` 的那段连同
`atTone` 一起删了 —— 稿子给了本色,代码不再替设计做判断。

按 §5 核对(数值取自画板样卡,与 `presets.css` 一致):

| 项 | 结果 |
|---|---|
| 110 个值齐全、无跨套沿用 | 通过,11 项组合五套十态两两不同 |
| `on-` 配对 ≥4.5:1 | 通过,最低一档也在 4.5 以上;正文 `onbg`/`bg` 亮色 16.5–17.2、暗色 14.3–14.7,远超 7:1 |
| 中性面阶梯单调、亮暗反向 | 通过,亮色 `slst`→`shst` 由浅到深、暗色反向;亮色最浅是 `slst` 最深是 `sdim`,暗色最浅是 `sbri` 最深是 `slst` |
| `--ionsf` / `--ip` 落在 `--isf` 上 ≥4.5:1 | 通过 |
| 镜音双子 `--sec` / `--ter` 的说明 | 已给:`--sec` 与 `--sc` 同色相取橙本色,`--ter` 取黑灰蓝本色,黄不下放到次色,保持「黄只在 primary 一侧」 |
| 比例原则不破 | 11 项都落在中性面、次/第三本色和反色上,没有把角色色再往外铺 |
