# 0.9.2 界面可用性与组件声明扩充报告

## 任务与范围

task-13「0.9.2 界面可用性与组件声明扩充」对应 `docs/15-V1.0开发计划.md` 任务 2.4。任务描述于 revision 1 读取，以 revision 2 认领，owner 为 dev-ui。

改动限定在任务写作用域：`app/src/main/java/com/shuiyu/octoberrain/MainActivity.kt`、`core/data/src/main/java/com/shuiyu/octoberrain/core/data/NotificationCenter.kt`、`feature/home/src/main/java/com/shuiyu/octoberrain/feature/home/HomeView.kt`、`feature/home/src/main/java/com/shuiyu/octoberrain/feature/home/HomeScreen.kt`、`core/widget-api/src/main/java/com/shuiyu/octoberrain/core/widget/WidgetApi.kt`、`feature/widgets/src/main/java`、`core/common/src/main/java/com/shuiyu/octoberrain/core/common/Contracts.kt`。`feature/settings` 与 `app/LockGateImpl.kt` 等锁屏文件由其他成员维护，本次未修改。

## 修改明细

### MainActivity 页面复用与锁状态同步

`MainActivity.kt:223-231`：`navigate` 增加目标判重。页面栈中已有相同 `Destination` 时调用 `popTo(existing)` 回退并复用该页；目标已在栈顶时仅请求局部刷新。原实现每次入口点击都会压入新页面，返回需要多次操作。

`MainActivity.kt:238-248`：新增 `popTo`，移除并销毁上层页面，恢复新栈顶的 `onResume`、状态栏底色与局部刷新。

`MainActivity.kt:256`：页面日志标签改用 `destination.displayLabel()`，与占位页文案一致。

`MainActivity.kt:89`、`:101`、`:288`、`:331-333`：锁状态判断改为读取契约 `AppServices.lock.isLocked`，去掉 `as? LockGateImpl` 具体实现依赖。`showLock` 在覆盖层已存在时恢复可见（`:309-328`）；新增 `syncLockState` 供启动、回到前台与触摸交互时校正（`:330-333`、`:365-369`、`:371-380`）。原实现通过具体类注册单回调，覆盖层自行校验成功后不回调 Activity，因此需要在交互时按契约状态校正。

`MainActivity.kt:499-510`：`onDestroy` 移除 `observe(null)` 的解除逻辑，其余清理顺序不变。

`MainActivity.kt:348-353`：补充 `onNewIntent`，应用以 `singleTask` 重复启动时回到首页根页面，与 HOME 键行为一致。

`MainActivity.kt:487-497`：`onKeyDown` 增加 `KEYCODE_BACK` 分支，三键导航与系统注入的返回键调用 `goBack()`。

`MainActivity.kt:355-363`：保留 `onBackPressed` 重写并补充说明，理由见「遗留问题」。

### 页面标签集中声明

`core/common/src/main/java/com/shuiyu/octoberrain/core/common/Contracts.kt:251-278`：新增私有映射 `destinationLabels`，覆盖全部单例页面目标；`:287-293` 新增 `Destination.displayLabel()`，带参数的页面在 `when` 中单独处理。`Destination.Negative` 标签由「书架」改为「负一屏」，`Destination.Recents` 为「最近任务」，原 `MainActivity.kt:327`、`:333` 的重复标签与 `:334` 的异常缩进随私有函数删除而消除。标签迁入 core:common 后可被状态栏、占位页与 JVM 用例共用。

### 通知列表原子更新

`NotificationCenter.kt:13` 引入 `kotlinx.coroutines.flow.update`。`dismiss`、`dismissAll`、`dismissGroup`（`:55-74`）、`push`（`:104-125`）、`remove`（`:127-130`）、`replaceAll`（`:138-150`）全部改用 `_items.update {}`。原实现读取 `_items.value` 后整体赋值，通知服务线程与界面线程并发写入时会覆盖同时到达的条目；`StateFlow.update` 以比较并交换重试，合并基于最新值。

### 组件契约声明扩充

`core/widget-api/src/main/java/com/shuiyu/octoberrain/core/widget/WidgetApi.kt:112-138`：新增 `WidgetCategory`（TIME、LOCAL、WEREAD、OTHER）与 `WidgetRefreshPolicy`（MINUTE、EVENT、MANUAL）。后者与 core:common 的整刷策略 `RefreshPolicy` 区分命名。

`WidgetApi.kt:140-157`：`WidgetProvider` 增加 `category`、`refreshPolicy`、`requiredCapabilities` 三个带默认值的属性，默认值分别为 OTHER、MANUAL、空集合。未声明扩展字段的组件保持扩充前的可观察行为。

`WidgetApi.kt:140-146`：新增扩展函数 `WidgetRefreshPolicy.triggersOnMinute()`，仅 MINUTE 返回 true。判定保持纯函数形式，宿主与回归用例共用同一规则。

### 组件清单声明同步

`feature/widgets/src/main/java/com/shuiyu/octoberrain/feature/widgets/WidgetCatalog.kt:24-34`：`Entry` 增加分类、刷新策略与能力依赖参数。`:36-158`：13 个条目逐条声明；`:178-184`：保持四组分类划分。

刷新策略：`calendar` 为 MINUTE；`clock`、`clock_compact`、`image`、`placeholder` 为 MANUAL；其余为 EVENT。

能力声明：`weather` 需要 WEATHER；`covers`、`image` 需要 STORAGE；三个微信读书组件需要 WEREAD；`koreader_month` 需要 KOREADER；`usage` 需要 USAGE_ACCESS。

### 桌面组件占位与手势修正

`HomeView.kt:75-79`：新增 `widgetPolicy` 字段。`:231-240`：新增 `refreshWidgetByMinute()`，仅当当前组件声明 MINUTE 时调用 `refreshCurrentWidget(true)`，策略判定调用 `triggersOnMinute()`。

`HomeView.kt:364-396`：重写 `ensureWidget`。先创建视图，创建成功后写入 `widgetId` 与刷新策略；组件未注册或创建失败时将 `widgetId` 置空，后续配置回调可重新尝试，原「未安装组件」占位固化的入口消除。

`HomeView.kt:772-782`：`handleDown` 每次按下重置 `editHoldTriggered`。`:811-814`：`handleMove` 在整理模式下仅要求 `dragIndex >= 0`，长按进入整理后继续移动即可拖动。`:1003-1011`：长按进入整理的回调保持 `editHoldTriggered = true`，区分长按与单击。

`HomeScreen.kt:44-55`：整分回调改为 `home.refreshWidgetByMinute()`，配置变化时仍调用 `refreshCurrentWidget(true)`。

### 回归用例

`core/common/src/test/java/com/shuiyu/octoberrain/core/common/DestinationLabelsTest.kt`：3 个用例，覆盖全部页面目标标签非空、负一屏与书架标签不同、带参数页面不同实例标签一致。

`feature/widgets/src/test/java/com/shuiyu/octoberrain/feature/widgets/WidgetCatalogTest.kt`：5 个用例，覆盖标识唯一、分类分组覆盖全部条目、声明分类与分组一致、整分刷新仅 `calendar`、关键能力声明。

`core/widget-api/src/test/java/com/shuiyu/octoberrain/core/widget/WidgetProviderDefaultsTest.kt`：7 个用例，覆盖未声明扩展字段时分类为 OTHER、刷新策略为 MANUAL、能力集合为空，以及三种刷新策略在整分刷新中的取舍。

## 验证

环境变量与命令：

```
$env:JAVA_HOME='D:\DSHStation\.toolchain\jdk\jdk17.0.20_12'
$env:ANDROID_HOME='D:\DSHStation\.toolchain\android-sdk'
$env:GRADLE_USER_HOME='D:\DSHStation\.toolchain\gradle-home'
& "D:\DSHStation\.toolchain\gradle\gradle-8.11.1\bin\gradle.bat" --offline :app:assembleDebug testDebugUnitTest detekt ktlintCheck
```

结果：`BUILD SUCCESSFUL`。19 个测试套件、149 个用例、0 失败。detekt 对本次改动文件 0 条告警（`Contracts.displayLabel` 初版触发 CyclomaticComplexMethod，改为映射表后消除）。ktlint 对本次新增代码与新增测试文件无告警；MainActivity 与 HomeView 等改动文件中的存量格式告警沿用仓库首版 `ignoreFailures` 配置，未在本次清理。

真机冒烟：`adb devices` 显示 `SA4B03CN2608081096X`，`adb install -r` 返回 Success，`am start -n com.shuiyu.octoberrain/.MainActivity` 后桌面正常显示时钟组件、状态栏时间与图标，截图保存在 `_analysis/team/smoke-0.9.2.png`。

![冒烟截图](smoke-0.9.2.png)

## 遗留问题

`requiredCapabilities` 已声明，能力缺失空态尚未接入渲染层。core:device 当前没有能力探测接口，HomeView 与 WidgetPickerScreen 仍展示全部条目并沿用组件自身降级；可在设备能力接口确定后按声明过滤或绘制空态。

`onBackPressed` 重写保留。真机为 Android 11，手势返回由系统调用平台回调，移除重写会丢失返回处理；Manifest 不在本次写作用域，未增加 `enableOnBackInvokedCallback`，API 33 及以上的 predictive back 需在后续任务注册 `OnBackInvokedCallback` 后移除重写。

页面判重与组件占位重试涉及 Activity 与 View 生命周期，未拆出纯 JVM 逻辑，回归依赖代码审查与真机冒烟；JVM 用例覆盖标签、分类、刷新策略与默认值。

WidgetPickerScreen 未按 `requiredCapabilities` 标注或过滤，`:37` 仍仅按分类分组展示。

全库 ktlint 存量告警未清理，`ignoreFailures` 保持首版配置；本次新增代码与新增测试文件未新增告警。
