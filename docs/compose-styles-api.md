# Compose Styles API 评估（未采用）

2026-08-18 评估 `androidx.compose.foundation.style` 能否接进 `:designsys`。**结论：不接。**
不是"以后再说"式的搁置——是这套 API 在当前代码结构下**找不到能下嘴的组件**，理由在下面第三条。

本文记录的是当时反编译核实到的事实，不是转述文档。核实对象是 Gradle 缓存里的
`foundation-android-1.12.0-beta01/foundation.aar`，工具是 `javap`。

## 版本前提

| 条件 | 实际 |
|---|---|
| `compileSdk` | 37（[`plaza.android.library.gradle.kts`](../build-logic/src/main/kotlin/plaza.android.library.gradle.kts)），满足要求 |
| `androidx.compose.foundation` | 1.12.0-beta01，`style` 包 84 个类齐全 |
| 这个版本从哪来 | **不是 BOM 给的**。`compose-bom` 2026.06.00 钉的是 foundation 1.11.3（没有 `style` 包）；是版本目录里显式声明的 `androidxMaterial3 = "1.5.0-alpha24"` 依赖 1.12.0-beta01 把它顶上去的 |

最后一行是这份评估的有效期：`material3` 一旦回退到不依赖 1.12 的版本，`style` 包会**无声消失**，
表现为编译期找不到包。要真用这套 API，前提是在版本目录里显式钉住 foundation，而不是搭便车。

## 三条不采用的理由

### 一、`Style` 里的文本属性默认不生效

`Modifier.styleable` 挂的 `StyleOuterNode` 确实实现了 `TextStyleProviderNode`，样式**发得出去**。
问题在收的一端：`BasicText` 用的 `TextStringSimpleNode` 只在这个条件成立时才去读继承来的样式——

```
if (ComposeFoundationFlags.isInheritedTextStyleEnabled) { ... inheritedTextStyle(...) }
```

而 `ComposeFoundationFlags` 的 `<clinit>` 里，旁边十几个 flag 全都 `iconst_1 putstatic` 显式置真，
**唯独 `isInheritedTextStyleEnabled` 从头到尾没有赋值**，也就是默认 `false`。

后果：`Style { fontSize(...); lineHeight(...); fontWeight(...); contentColor(...); textStyle(...) }`
**写了不报错，也不生效**。实际能生效的只有盒子级的四组：
`ShapeScope.shape`、`BackgroundScope.background`、`ContentPaddingScope.*`、`BorderScope.*`。

要让文本属性活过来，只能在 App 里把这个全局 flag 置真——那会改变全 App 每一个 `BasicText`
的样式继承行为，而 Google 自己默认关着。为一个组件的样式收纳去动它，不划算。

### 二、静态组件要为 `StyleState` 凭空造一个 `InteractionSource`

`Modifier.styleable(modifier, styleState, vararg styles)` 强制要一个 `StyleState`，
而 `MutableStyleState` 唯一的公开构造签名是 `MutableStyleState(InteractionSource)`。

`TonalTag` 是个连点击都没有的标签，为了用这套 API 得 `remember { MutableInteractionSource() }`
喂给一个永远不会有交互事件的状态对象。

### 三、根本原因：本项目没有"自绘 + 有状态"的组件

Styles API 的价值在 `pressed` / `hovered` / `checked` / `disabled` 这些状态变体上——
把一个组件在各状态下的外观集中成一份声明。技能同时明确**不支持**给 Material 组件套 `Style`。
两个条件求交集，`:designsys` 里剩下的是空集：

| 组件 | 自绘？ | 有状态？ | 结论 |
|---|---|---|---|
| [`TonalTag`](../designsys/src/main/java/io/github/plaza/designsys/component/Chips.kt) / `BadgeChip` | 是 | **否**，纯静态标签 | 只剩样式收纳，且文本属性还是哑的 |
| [`UserAvatar`](../designsys/src/main/java/io/github/plaza/designsys/component/UserAvatar.kt) | 是 | 否 | 同上 |
| [`StatusView`](../designsys/src/main/java/io/github/plaza/designsys/component/StatusView.kt) 色块 | 是 | 否 | 同上 |
| [`ToolbarKey`](../designsys/src/main/java/io/github/plaza/designsys/editor/EditorToolbar.kt) | **否**，M3 `IconToggleButton`/`IconButton` | 是（checked） | 不支持 |
| [`ChoiceRow`](../designsys/src/main/java/io/github/plaza/designsys/component/ChoiceRow.kt) | 否，M3 `RadioButton` + `selectable` | 是（selected） | 不支持 |
| `ViewModeSwitch` | 否，M3 `ToggleButton` | 是（checked） | 不支持 |
| `EditorToolbar` | 否，M3 `Surface` | — | 不支持 |

规律很干净：**有状态的一个不落全走 M3，自绘的一个不落全是无状态标签。**
这不是巧合，是"能用官方组件就不自己造"这条选择的结果，本身没问题。

## 什么时候值得回头看

任意一条成立即可重新评估：

- `ComposeFoundationFlags.isInheritedTextStyleEnabled` 在某个 foundation 版本里默认置真，
  或者该门禁被移除（复核方式：`javap -c -p androidx.compose.foundation.ComposeFoundationFlags`，
  看 `<clinit>` 里有没有对应的 `putstatic`）；
- `MutableStyleState` 有了不需要 `InteractionSource` 的公开构造；
- 本项目出现真正自绘且带状态的组件——例如为了绕开 MD3 的某个行为把某个 M3 按钮拆成手写实现。
  在此之前不要为了用上这套 API 而先去拆 M3：那是拿一个明确的收益（M3 的无障碍与状态语义）
  换一个尚未验证的收益。
