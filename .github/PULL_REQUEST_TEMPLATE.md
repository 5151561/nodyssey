<!-- 贡献指南：https://github.com/5151561/nodyssey/blob/main/CONTRIBUTING.md -->

## 做了什么

<!-- 解决的问题是什么，为什么这么解决。改动大的话顺带说说取舍。 -->

## 关联 issue

<!-- 比如 Closes #123；没有就写「无」。 -->

## 自查

提 PR 之前，请先 `git diff main...HEAD` 把自己的改动从头到尾读一遍 —— 当成是在 review 别人的代码。
不适用的条目划掉或写「不涉及」，不要直接删。

- [ ] 我完整读过自己的 diff，没有调试代码、注释掉的旧代码、多余的日志或 `TODO`
- [ ] 这个 PR 只做一件事；顺手发现的别的问题另开 issue 或另开 PR
- [ ] 遵守四条架构规则：SSOT、UDF、构造器注入、数据层不产生用户文案（详见 [docs/architecture.md](https://github.com/5151561/nodyssey/blob/main/docs/architecture.md)）
- [ ] 新增或改动的用户可见文案，简中 / 繁中 / 英文三份都更新了
- [ ] 站点选择器的改动集中在 `:shared` 的 `core/html/Selectors.kt`，没有散进 UI 或数据层
- [ ] 改了行为就补了测试；修 bug 就补了能挂在旧代码上的回归测试
- [ ] 改了界面就附了渲染图（见下面「截图」一节）
- [ ] 用户能感觉到的变化写进了 CHANGELOG 的 Unreleased（一个功能一条，一条不超过 20 个汉字）
- [ ] 没有提交 cookie、凭据、本机 SDK 路径，或任何已登录页面的抓取样本

## 验证

<!-- 贴实际跑过的命令和结果，没跑的别写。 -->

```
./gradlew spotlessCheck testDebugUnitTest testAndroidHostTest jvmTest :app:lintDebug :app:assembleDebug
```

- 真机 / 模拟器：<!-- 机型 + Android 版本，或「没上设备」 -->

## 截图

改了界面就必须有，而且要 **Robolectric 渲染图** —— 手机截图受机型、字体缩放、系统主题影响，同一段代码
换台机器就不一样；渲染图是固定窗口、固定字号跑出来的，两张图的差别只会是这个 PR 造成的。

- 改屏幕（`:ui`）：照着 `ui/src/androidHostTest/.../render/NetworkCheckScreenRenderTest.kt` 给你改的屏幕写个
  render 测试，然后出图，PNG 在 `ui/build/outputs/renders/`：

  ```
  ./gradlew :ui:testAndroidHostTest -PrenderUi --tests '*XxxScreenRenderTest'
  ```

  改前那张这样拿（只回退屏幕，留着刚写的 render 测试）：

  ```
  git stash push -- ui/src/commonMain/kotlin/.../XxxScreen.kt
  ./gradlew :ui:testAndroidHostTest -PrenderUi --tests '*XxxScreenRenderTest'
  git stash pop
  ```

- 改设计系统（`:designsys`）：goldens 本来就在仓库里，重录后 PR 的 diff 自带前后对比，不用另外贴图：

  ```
  ./gradlew :designsys:testAndroidHostTest -ProborazziRecord --rerun-tasks
  ```

- 真机截图可以补充，但不能替代渲染图 —— 只有真机能看的东西（输入法、系统分享面板、手势）除外。

<!-- 改前 / 改后两张图放这里 -->

## 需要特别说明

<!-- 下面这些只要沾上就要写清楚，没有就删掉这一节：
     - Room 实体或迁移变了（schema 是否已更新、迁移测试在哪）
     - 依赖变了（是否跑过 ./gradlew resolveAndLockAll --write-locks，lockfile 是否全部提交）
     - 登录态 / Cookie / WebView 允许域名有改动
     - 抓取用的选择器有改动（站点改模板时哪里会先坏） -->
