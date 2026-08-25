# :benchmark — 性能测量与 Baseline Profile

这个模块不进 CI。它做两件事，都以 `:app` 的 `minified`（R8）构建为对象、从进程外驱动：

## 重新生成 Baseline Profile

`app/src/main/baseline-prof.txt` 是提交进仓库的产物，记录冷启动到首页、滚动前几屏这条路径上实际执行过的方法。装机时 `profileinstaller` 把它编译成 AOT，用户第一次冷启动就不再解释执行这条路径。

启动路径的形状变了（启动旅程多了屏、换了分页库）才需要重录，小改动不用——profile 是提示清单，略旧的清单仍然覆盖绝大多数正确的方法。重录步骤：

1. 起一个 API 33+ 的模拟器（无 root 的 shell 从 33 才被允许读取其他进程的 profile）。
2. 对 **nonMinified** 变体生成——对 R8 构建生成会录到那一次构建的混淆名，提交了也对不上任何未来的构建；提交的 profile 必须说源码名，构建时由当次的 R8 自己翻译：
   ```bash
   ./gradlew :benchmark:connectedNonMinifiedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.nodyssey.benchmark.StartupProfileGenerator
   ```
3. 把 `benchmark/build/outputs/connected_android_test_additional_output/` 下拉回的 `*-baseline-prof.txt` 覆盖到 `app/src/main/baseline-prof.txt`，review diff 后提交。

## 冷启动基准

```bash
./gradlew :benchmark:connectedMinifiedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=io.github.nodyssey.benchmark.StartupBenchmark
```

同一台设备上跑两组：无 profile（纯 JIT）与带提交的 profile。**读两组的差值**，那是 profile 买到的东西；模拟器的绝对毫秒数不是手机的毫秒数，不作数。结果在模块 build 输出的 JSON 和 logcat 的 `Benchmark` 行里。
