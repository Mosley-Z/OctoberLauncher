# T4 组件契约扩展与组件清单核对（tenrain-widget）

记录范围：task-4，B3 组件契约扩展、B6 十八条组件声明核对。仓库 `D:\DSHStation\OctoberLauncher`，版本 V1.0.1。改动前快照 tag `1.0.1-t4pre`，目标目录 `D:\DSHStation\_snapshots\OctoberLauncher-1.0.1-t4pre`。

## 一 B3 契约实现

### 1 命中区域类型

`core/widget-api/src/main/java/com/shuiyu/octoberrain/core/widget/WidgetApi.kt` 新增三处公共符号。

`data class WidgetHitRegion(val x: Int, val y: Int, val width: Int, val height: Int, val action: String)`，坐标为组件局部像素，原点位于组件左上角；`width` 与 `height` 取正值，构造时校验，非正值抛出 `IllegalArgumentException`。成员函数 `contains(px, py)` 按左闭右开判定，相邻区域在公共边界上不重复命中。

`interface WidgetHitRegionSource { fun hitRegions(): List<WidgetHitRegion> = emptyList() }`，默认空表的实现保证未改造组件照常编译，宿主取不到区域时退化为整卡事件。

扩展函数 `fun WidgetHitRegionSource.hitRegionAt(x: Float, y: Float): WidgetHitRegion?`，按列表顺序返回首个命中区域，列表顺序即 z 序，前项覆盖后项。

### 2 WidgetView 扩展

`interface WidgetView : WidgetHitRegionSource`，新增坐标版长按：

```kotlin
fun onLongPress() {
    onLongPress(Float.NaN, Float.NaN)
}

fun onLongPress(
    x: Float,
    y: Float,
) {}
```

无参重载保留并转调坐标版本，坐标为 NaN 表示按下点未知；既有宿主与组件不需要改动。`onClick(x, y)` 与 `hitRegions()` 使用同一局部坐标系，宿主在组件矩形内按下时以 `event.x - rect.left`、`event.y - rect.top` 传入。

### 3 InkWidgetView 钩子

`feature/widgets/src/main/java/com/shuiyu/octoberrain/feature/widgets/InkWidgetView.kt` 把原有的无参长按改为坐标版，并新增可覆写钩子：

```kotlin
override fun onLongPress(x: Float, y: Float) {
    if (handleLongPress(x, y)) return
    navigateTo(Destination.WidgetPicker)
}

protected open fun handleLongPress(x: Float, y: Float): Boolean = false
```

未覆写 `handleLongPress` 的既有组件维持打开组件选择页的行为。

### 4 整体卡片组件

新增 `feature/widgets/src/main/java/com/shuiyu/octoberrain/feature/widgets/WereadBoardWidget.kt`，类 `WereadBoardWidget(context) : WereadCardWidget(context)`，内部形态枚举 `WereadBoardForm`（`WEEK` 本周、`MONTH` 本月、`BOOK` 本书、`NOTE` 本记、`TODO` 待办，`of(index)` 对越界与空值取本周）。

布局按 `scale = width / 368f` 计算，限制在 0.6 到 2.0；字号以 `round(基准字号 × scale)` 换算，页签高度、菜单行高、热力格与进度条同乘缩放系数。

命中区域按 z 序返回：菜单展开时依次为三行菜单（`menu:hide`、`menu:refresh`、`menu:open`），随后为五个形态页签（`form:week`、`form:month`、`form:book`、`form:note`、`form:todo`），最后为正文区域（`card:open`）。菜单展开时正文与页签暂不对外暴露，点击菜单外空白关闭菜单。

形态写回 `LauncherConfig.desktop.negativePage.forms[WEREAD_BOARD_WIDGET_ID]`，值为 `WereadBoardForm.ordinal`；读取时同一键，缺省取本周。数据经 `AppServices.content` 一次取本周周期统计、月度统计、排行首条与笔记首条，切换形态不重复请求。

长按三项菜单：隐藏组件清空 `desktop.widgetId`，刷新数据调用 `AppServices.refresh.requestFullRefresh()`，打开墨台跳转 `Destination.Negative`。正文轻点按形态跳转：本周 `WereadTab(STATS)`、本月 `WereadCalendar`、本书 `Book`、本记 `Note`、待办 `Todo`。

### 5 组件声明

`WidgetCatalog.kt` 追加 `wereadBoard` 条目：`id = WEREAD_BOARD_WIDGET_ID`（`"weread_board"`）、名称「微信读书」、说明「本周、本月、本书、本记与待办五形态」、分类 `WEREAD`、刷新策略 `EVENT`、能力 `Capability.WEREAD`、构造 `WereadBoardWidget(it)`。`providers` 与 `byCategory` 的 `WEREAD` 分组同步追加，组件选择页自动出现。

### 6 宿主对接要求

`feature/home` 的组件触摸分发需要按新契约改造，属于 tenrain-negative 写作用域：

- 按下时记录局部坐标，长按触发点由 `widget?.onLongPress()` 改为 `widget?.onLongPress(localX, localY)`。
- 点击时先以 `hitRegionAt(localX, localY)` 取命中区域，命中后转发 `onClick(x, y)`，组件自行按坐标判定页签与菜单行。
- 组件矩形原点由宿主在绘制前 `canvas.translate(rect.left, rect.top)` 平移，触摸坐标换算保持一致。
- 判定顺序与组件返回列表一致，前项优先，重叠区域取第一个命中项。

`HomeView.kt:124` 为当前长按调用点，`HomeView.kt:968` 为当前点击坐标换算点，可作为改造参照。

## 二 B6 十八条声明核对

核对对象：`WidgetCatalog.kt` 的 `providers` 与 `byCategory`，`feature/widgets` 源文件，`WidgetCatalogTest.kt`、`WereadWidgetLogicTest.kt`，`Models.kt` 的迁移清单。追加整体卡片后声明数为十九，建议删除十三项，最终保留六项。

### 1 逐条核对表

| 声明 | 数据来源 | 其他引用点 | 判断 |
| --- | --- | --- | --- |
| reading 阅读进度 | `content.readingSnapshot` | Catalog | 删除，本地阅读进度入口在书架与内容页 |
| weather 天气 | `content.weather`，能力 WEATHER | Catalog、测试能力断言 | 删除，依赖网络与定位授权，桌面清单收缩到时间与阅读 |
| clock 时钟 | 本地时间 | Catalog、`ClockStyleScreen` | 保留，吸收紧凑钟面 |
| clock_compact 紧凑时钟 | 本地时间 | Catalog | 删除，与 clock 共用 `ClockWidget`，仅 compact 参数不同 |
| calendar 日历 | 本地日期 | Catalog，唯一 MINUTE 刷新 | 保留 |
| covers 书架封面 | `bookshelf`，能力 STORAGE | Catalog | 删除，封面在书架页与负一屏书架已完整呈现 |
| weread_covers 微信读书封面 | `wereadShelf` | Catalog | 删除，由整体卡片「本书」形态与负一屏书架承载 |
| weread_month 月报 | `wereadMonth`，`HeatMonthWidget` | Catalog | 删除，由「本月」形态承载 |
| weread_mark 摘录 | `wereadHighlights` | Catalog、测试能力断言 | 删除，由「本记」形态与笔记页承载 |
| weread_note 笔记 | `wereadNotes` | Catalog | 删除，由「本记」形态承载 |
| weread_stats 统计 | `wereadPeriod(WEEKLY)` | Catalog | 删除，由「本周」形态承载 |
| weread_bill 账单 | `wereadBill` | Catalog | 删除，由「本月」形态的环比与逐日格承载 |
| weread_rank 排行 | `wereadRank` | Catalog | 删除，由「本书」形态与负一屏排行模块承载 |
| weread_fingerprint 阅读指纹 | `wereadFingerprint` | Catalog | 删除，由负一屏统计模块与「本月」热力格承载 |
| koreader_month KOReader 月热力 | `koreaderHeat`，`HeatMonthWidget` | Catalog | 删除，与 weread_month 共用绘制类，KOReader 入口在内容页 |
| usage 使用统计 | `usageToday`，能力 USAGE_ACCESS | Catalog、测试能力断言 | 保留 |
| image 图片 | 桌面配置 `widgetImagePath` | Catalog | 保留 |
| placeholder 占位 | 无 | Catalog | 保留，未注册组件的占位渲染在 `HomeView` 另有独立实现 |
| weread_board 整体卡片 | `wereadPeriod`、`wereadMonth`、`wereadRank`、`wereadNotes` | Catalog、新增 | 保留，本次新增 |

核对结果与交接文档第七节 B6、第十节的保留清单一致；`Models.kt` 的 `REMOVED_WEREAD_WIDGET_IDS`（8 项）与 `REMOVED_OTHER_WIDGET_IDS`（5 项）同被删清单逐项对应，`rewriteRemovedWidgetIds` 的改写方向为微读类改 `weread_board`、其余改 `clock`。

### 2 建议删除清单

删除十三项：`clock_compact`、`covers`、`weread_covers`、`weread_month`、`koreader_month`、`weread_mark`、`weread_note`、`weread_stats`、`weread_bill`、`weread_rank`、`weread_fingerprint`、`reading`、`weather`。

保留六项：`clock`、`calendar`、`usage`、`image`、`placeholder`、`weread_board`。

### 3 清理点

代码层面：

- `WidgetCatalog.kt`：删除十三个 `Entry` 声明块，`providers` 同步移除；`byCategory` 调整为 `TIME to listOf(clock, calendar)`、`LOCAL to listOf(usage, image)`、`WEREAD to listOf(wereadBoard)`、`OTHER to listOf(placeholder)`。
- 删除源文件十个：`ReadingWidget.kt`、`WeatherWidget.kt`、`CoversWidget.kt`、`HeatMonthWidget.kt`、`WereadMarkWidget.kt`、`WereadNoteWidget.kt`、`WereadStatsWidget.kt`、`WereadBillWidget.kt`、`WereadRankWidget.kt`、`WereadFingerprintWidget.kt`。
- 保留 `WereadCardWidget.kt`（整体卡片基类）、`WereadWidgetData.kt`、`WereadWidgetLogic.kt`。
- `ClockWidget.kt` 的 compact 参数继续保留，避免改动既有签名；`clock_compact` 删除后不再有 `compact = true` 构造点，紧凑分支的取舍由 lead 决定。

测试层面：

- `WidgetCatalogTest.kt` 的 `requiredCapabilitiesAreDeclared` 移除 `weather` 与 `weread_mark` 断言，补充 `weread_board` 为 `setOf(Capability.WEREAD)`；其余三个用例随 `providers` 变化自动覆盖。
- `WereadWidgetLogicTest.kt:183-184` 使用 `HeatMonthWidget.ViewMode.toggled()`，删除该组件时需同步删除这两个用例，或把 `ViewMode` 迁到保留位置。

文档层面：

- `docs/15-V1.0开发计划.md`、`docs/08-engineering-conventions.md` 的组件清单段落，`CHANGELOG` 的组件变更记录，按交接文档在删除确认后同步。
- `app/proguard-rules.pro` 现有两条接口级 keep 规则（`WidgetProvider`、`WidgetView`）不含具体类名，删除组件不需要改动。
- `WidgetPickerScreen` 按 `byCategory` 自动展示，无硬编码组件标识。

## 三 验证

```powershell
$tc = (Get-Content toolchain.path).Trim()
$env:JAVA_HOME = (Get-ChildItem "$tc\jdk" -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'bin\javac.exe') } |
    Sort-Object Name -Descending | Select-Object -First 1).FullName
$env:ANDROID_HOME = "$tc\android-sdk"
$env:GRADLE_USER_HOME = "$tc\gradle-home"
& "$tc\gradle\gradle-8.11.1\bin\gradle.bat" --offline :core:widget-api:assemble :feature:widgets:assemble
& "$tc\gradle\gradle-8.11.1\bin\gradle.bat" --offline :core:widget-api:testDebugUnitTest :feature:widgets:testDebugUnitTest
```

结果：assemble 与 testDebugUnitTest 均 BUILD SUCCESSFUL；单测覆盖 `WidgetProviderDefaultsTest`、`WidgetCatalogTest`、`WereadWidgetLogicTest`、`WereadWidgetData` 相关用例。

## 四 遗留与偏差

- 内核 jar `weread-kernel-1.3.2-k1.jar` 尚未出现在 `app/libs`，`feature/widgets/build.gradle.kts` 也无该依赖（属 T6 写作用域）。整体卡片当前以 `WereadCardWidget` 直接绘制五形态，与内核 `CardKernel` 的绑定在 jar 到位后接入，接口形态保持可替换：形态枚举与 `bind/render` 参数一一对应。
- `feature/home` 的长按与点击分发仍走旧调用点，整体卡片的新命中区域与坐标长按需 tenrain-negative 按第一节第 6 条对接后生效；对未对接宿主，组件退化为整卡长按（打开组件选择页）与整卡点击（跳转当前形态页面）。
- 点击形态页签会写回配置，配置变化触发宿主重绘与数据刷新；形态切换本身不额外请求数据。刷新带来的重复加载由宿主刷新节流承担，若出现闪烁，可在后续把形态切换改为只重绘。
- 图片组件多选与桌面点击轮播按任务说明只记录、未实现。
- 待办形态没有独立数据源，当前展示墨单卡片说明与跳转入口，待负一屏墨单模块提供计数后接入。

## 五 B6 执行结果（lead 批准后）

批准范围：`feature/widgets` 写域内删除十三项声明、清理 `WidgetCatalog`、删除被删组件源文件与测试残留；`clock_compact` 并入 `clock`；`image` 实现多选与桌面点击轮播；整体卡片预留内核宿主与数据源入口。`docs`、`app`、`build.gradle.kts` 未改动。

### 1 代码改动

`WidgetCatalog.kt` 重写为六条声明，顺序为 `clock`、`calendar`、`usage`、`image`、`placeholder`、`weread_board`；`byCategory` 相应调整为 `TIME to listOf(clock, calendar)`、`LOCAL to listOf(usage, image)`、`WEREAD to listOf(wereadBoard)`、`OTHER to listOf(placeholder)`。`Entry` 增加 `forms: List<WidgetForm>` 字段与 `data class WidgetForm(id, label, hint)`，供选择页说明形态；`clock` 声明 `stacked` 与 `compact` 两种形态，`image` 声明 `single` 与 `carousel`，`weread_board` 声明五形态。

`ClockWidget.kt` 吸收紧凑时钟：新增内部枚举 `ClockLayout`（`AUTO`、`STACKED`、`COMPACT`），主构造接收布局，公开无参构造按高度自动选择，`constructor(context, compact: Boolean)` 保留兼容既有调用点；高度低于 `150dp` 时自动采用单行布局，原紧凑条目的两种形态由同一视图承担。

`ImageWidget.kt` 重写：图片来源取配置路径所在目录，目录内图片按文件名升序组成轮播列表，多于一张时按 `DEFAULT_CAROUSEL_SECONDS = 15` 定时切换；单击在多张时切到下一张，单张时打开系统图片选择器；选择结果复制到 `filesDir/widget-image/` 并写回 `desktop.widgetImagePath`。轮播间隔当前为常量，接入设置项 `desktop.widgetCarouselSeconds` 后改为读取配置，参数契约由 lead 在 `SettingsSpecs` 落地。

新增 `WereadBoardKernelBridge.kt`（91 行）：定义 `WereadBoardKernelHost`（`context`、`requestDraw()`）、`WereadBoardDataSource`（`content: ContentSource?`）、`WereadBoardKernel`（`bind`、`render(canvas, width, height, formId)`、`setForm(formId)`、`onAction(action)`、`release()`）与 `object WereadBoardKernelBridge { var factory }`。`WereadBoardWidget` 首次绘制时若工厂已注入则创建内核并转交绘制与动作，未注入时使用内置五形态绘制；`hitRegions()` 与动作标识保持同一套，内核接管前后宿主行为一致。

`WereadBoardWidget.kt` 同时补充整体卡片形态循环方法 `WereadBoardForm.next()`，供形态顺序回归用例使用。

### 2 删除文件

删除十个源文件：`CoversWidget.kt`、`HeatMonthWidget.kt`、`ReadingWidget.kt`、`WeatherWidget.kt`、`WereadBillWidget.kt`、`WereadFingerprintWidget.kt`、`WereadMarkWidget.kt`、`WereadNoteWidget.kt`、`WereadRankWidget.kt`、`WereadStatsWidget.kt`。保留 `WereadCardWidget.kt`（整体卡片基类）、`WereadWidgetData.kt`、`WereadWidgetLogic.kt`、`ClockFaces.kt`（时钟与钟面设置页共用）。

### 3 测试调整

`WidgetCatalogTest.kt` 增至七个用例：标识唯一、分组覆盖、分类一致、整分刷新仅 `calendar`、能力声明（`usage`、`weread_board`、`image`、`clock`）、条目总数为六、被删十三项 `find` 返回空、时钟声明 `stacked` 与 `compact` 两形态。`WereadWidgetLogicTest.kt` 移除对已删组件 `HeatMonthWidget.ViewMode` 的引用，改为校验整体卡片五形态数量与首尾循环。

### 4 验证结果

```powershell
& "$tc\gradle\gradle-8.11.1\bin\gradle.bat" --offline :core:widget-api:assemble :feature:widgets:testDebugUnitTest
```

结果 `BUILD SUCCESSFUL`，`WidgetCatalogTest`、`WereadWidgetLogicTest`、`WidgetProviderDefaultsTest` 全部通过。

残留引用检查：全部 `.kt` 文件已无被删组件类名引用；被删标识的剩余出现位置均为预期，包括 `WidgetCatalogTest` 的缺失断言、`Models.kt` 的迁移清单、`feature/content` 负一屏模块 id（`NegativeModuleRegistry` 使用，与组件标识同名但不互引）、`feature/settings` 的负一屏模块名称表、`core/data` 的配置迁移用例。

### 5 遗留与偏差

- 内核 jar 仍未出现在 `app/libs`，`feature/widgets/build.gradle.kts` 未加依赖（T6 写作用域）。内核接缝已按 `CardKernel` 的 `bind`、`render`、`setForm`、`onAction`、`release` 对齐，jar 到位后由应用层注册 `WereadBoardKernelBridge.factory` 即可接管绘制。
- 图片轮播间隔为常量十五秒，设置项参数契约与默认值需 lead 在 `SettingsSpecs` 与配置模型中确定后接入。
- `feature/home` 的触摸分发仍走旧调用点，命中区域与坐标长按需 tenrain-negative 对接后生效。

## 六 lead 决定与收尾

- 图片轮播间隔读取 `DesktopConfig.effectiveImageCarouselSeconds`：`ImageWidget.intervalMillis()` 在每次排程时从 `AppServices.config.state.value.desktop` 读取，配置项 `imageCarouselSeconds` 取值集合 5、10、15、30、60，非法值由 `effectiveImageCarouselSeconds` 回退；配置服务未就绪时使用常量 `FALLBACK_CAROUSEL_SECONDS = 15`。该派生属性为 `core.common` 包内的扩展属性，`ImageWidget` 已导入 `effectiveImageCarouselSeconds`。字段与设置项由 lead 在 `Models.kt` 与 `DesktopPrefsScreen` 落地，`feature/widgets` 未新增依赖。
- 内核 jar 接入由 T6 承担：`WereadBoardKernelBridge` 接缝保持现状，应用层注入工厂后由注入实例接管整体卡片的绘制与动作，本模块结构不再调整。
- task-4 状态已在 revision 6 置为 completed。



## 七 task-8 P0P1 组件一比一

### 1 P0 删除自绘

重写 `WereadBoardWidget.kt`（738 行降至约 380 行），删除的自绘与菜单成员：`computeLayout`、`drawTabs`、`drawWeek`、`drawMonth`、`drawBook`、`drawNote`、`drawTodo`、`drawBars`、`drawHeatGrid`、`levelColor`、`drawProgress`、`drawFooter`、`drawMenu`、`handleMenuClick`、`handleContentClick`、`regionsOf`、`RectF.toHitRegion`、`BoardLayout`、`BoardData` 与页签命中组装。保留：形态枚举 `WereadBoardForm`、内核实例、宿主与数据源、形态持久化（`desktop.negativePage.forms["weread_board"]`）、刷新、抬头三区与正文区命中分发、长按回归组件选择器。绘制只剩内核渲染与统一占位「微读组件不可用」。源码范围检索 `drawWeek|drawMonth|drawBook|drawNote|drawTodo|drawBars|drawHeatGrid|computeLayout` 为 0 处。

画布按 `scale = width / 368` 钳制 0.6–2.0 缩放，`canvas.scale(scale)` 后以逻辑尺寸调用内核，`CardState.unit` 置 1.0，避免字号与几何二次缩放。重写后接口新增 `preload(): Boolean`、`hitTest(x, y): Int`、`handleHit(hit: Int): Boolean`，`render` 只接收逻辑尺寸。

### 2 P1 注入字段

`KernelCardFiller.apply` 每帧补写：`setApiKey`、`setRefreshing(false)`、`setUnit(1.0f)`、`setPadXRatio(0.05f)`、`setFullscreen(false)`、`setNoteCardSize(17)`、`setNoteFullSize(17)`、`setNoteSlot(0)`、`setMonthHeatmap(true)`、`setAchievementText(null)`、`setEmptyNote(null)`；形态切换时 `setNoteExpanded(false)` 与 `resetScroll()`。`setNoteHint` 三态：无密钥「还没填 API Key」、读取失败「同步失败，请检查网络」、无笔记「还没有同步到笔记」；`setErrorText` 在周/月数据未填充且数据源失败时写「同步失败，请检查网络」。`setCoverBitmap` 由 `KernelHostImpl.coverBitmap(bookId, path)` 提供，按 256 像素上限采样解码并按书目编号做位图缓存。`setDeskEnabled`/`setDeskOpenLabel` 由宿主覆写为负一屏开关状态与「打开墨台」。本记滚动度量（`noteLineH`、`noteViewH`、`noteContentH`、`noteScrollMax`、`monthScrollMax`）由内核渲染器每帧回写，宿主只维护手势产生的滚动量。

### 3 数据源与宿主

`KernelDataSourceImpl` 读取超时置 4000 毫秒并记录失败状态（`hasFailure()`），新增 `invalidate()` 清缓存与失败标记，缓存有效期由 2 秒改为 30 秒。`bill`、`assets` 仍返回空表，`fingerprint` 返回空值，账单页与资产页在内核数据源换代为 k2 之前显示内核空态。`KernelHostImpl.scale()` 返回 1.0，新增书封解码与 `deskEnabled`、`deskOpenLabel` 覆写，页面映射维持负一屏、书架、账单、本书、本记、待办。

### 4 验证命令与结果

```powershell
& "$tc\gradle\gradle-8.11.1\bin\gradle.bat" --offline :feature:widgets:detekt :feature:widgets:ktlintCheck :feature:widgets:assemble :feature:widgets:testDebugUnitTest
& "$tc\gradle\gradle-8.11.1\bin\gradle.bat" --offline ktlintCheck detekt
```

两条命令均 `BUILD SUCCESSFUL`，`WidgetCatalogTest`、`WereadWidgetLogicTest`、`WidgetProviderDefaultsTest` 通过。detekt 期间处理的告警：`TooManyFunctions`（组件与宿主类）、`ReturnCount`（点击分发与书封读取）、`TooGenericExceptionCaught` 与 `SwallowedException`（数据源读取，已记录异常并注明各读取路径异常类型未统一）。

### 5 已知偏差与遗留

- 宿主 `HomeView` 只转发按下、抬起与长按坐标，触摸移动事件不转发，本记滚动、月历滚动与展开手势在本轮无法实现；内核侧已按 `HitRegions` 预留 `hitTest`/`handleHit` 接缝，宿主补 MOVE 转发后可直接接管。
- 书封优先读取 `filesDir/weread/assets.json`，该文件当前不存在（全盘检索无样本，格式待导入流程确定），实现回退到内容服务书架项的本地封面文件路径；两条路径均不可用时内核画无封面形态。
- 内核 jar 仍在 `app/libs` 之外，T6 完成依赖与工厂注册后内核渲染生效；此前组件显示统一占位。
- 未做真机验证：五形态切换、书封显示、本记展开与错误文案需在应用层接入后由装配流程验证。

## 八 组件内滚动与展开手势

### 1 契约对接

`core/widget-api` 中的 `WidgetTouchAction`（DOWN、MOVE、UP、CANCEL）与 `WidgetTouchTarget.onTouch(action, x, y)` 由 lead 增加，`WidgetView` 继承该接口，默认实现返回 false。`feature/widgets` 按此契约覆写组件行为，未改动契约文件。

### 2 内核滚动接缝

`WereadBoardKernel` 增加 `scrollBy(delta: Int): Boolean`、`scrollMax: Int`、`scrollViewHeight: Int`、`resetScroll()`。`KernelWereadBoard` 在每帧渲染后从 `CardState` 读回 `noteScrollMax`、`noteViewH`、`monthScrollMax` 与逻辑高度并缓存，按最近渲染形态选择滚动目标：本记形态滚动 `noteScrollY` 并按 `noteLineH` 吸附；月形态滚动月历；其余形态返回 false。滚动量由组件按缩放系数折算为逻辑像素。

内核 1.3.2-k1 的 `CardState` 只有 `monthScrollMax` 设置方法与 `monthScrollY` 读取方法，没有设置月历滚动位置的公开方法（`javap` 核验）。月形态滚动经反射写入 `monthScrollY` 字段，并在反射前优先查找 `setMonthScrollY` 方法，后续内核提供该方法时自动切换；反射均失败时月形态不消费手势。反射目标为应用内库类，release 构建若对内核类名与成员名做混淆，该路径会退化为不滚动，其余形态不受影响。建议 k2 内核补公开设置方法。

### 3 组件触摸处理

`WereadBoardWidget` 覆写 `onTouch`：DOWN 记录起点并消费；MOVE 在越过八 dp 阈值后按垂直位移调用 `scrollBy`，内核消费返回 true，到达边界或形态不可滚动返回 false，由宿主翻页手势接管；UP 与 CANCEL 复位状态并返回 true。点击与长按路径未改动，仍由 `onClick` 与 `onLongPress(x, y)` 处理。

### 4 验证

```powershell
& "$tc\gradle\gradle-8.11.1\bin\gradle.bat" --offline :feature:widgets:detekt :feature:widgets:ktlintCheck :feature:widgets:assemble :feature:widgets:testDebugUnitTest
```

结果 `BUILD SUCCESSFUL`。全仓 `ktlintCheck detekt` 在 `:core:widget-api:ktlintMainSourceSetCheck` 失败，报 `WidgetApi.kt:123:24` 与 `123:47` 的 `Super type should start on a newline`，该文件属 lead 写作用域，未改动。真机验证待 T6 接线后进行：本记上下滚动、月形态滚动、边界后桌面翻页。

## 九 真机验证记录（2026-10-20）

### 1 环境与产物

设备 S4（SA4B03CN2608081096X，Android 11，480×800，219dpi）。验证前备份配置到 `_analysis/verify/20261020-134342/backup/octoberrain_config.xml`，验证后未改动用户数据。构建命令按任务板第三节，产物为 `app/build/outputs/apk/debug/app-debug.apk` 与 `app/build/outputs/apk/release/app-release.apk`（release 使用 debug 签名配置），两次安装均为 `adb install -r` 覆盖安装。

截图目录 `_analysis/verify/20261020-134342/widget/`：`form-01-week.png` 至 `form-05-todo.png` 为直接改写 `forms["weread_board"]` 后重启所得的五形态画面；`tap-01-after.png` 至 `tap-05-week.png` 为点击抬头标题区逐次切换所得；`release-00-week.png`、`release-01-month.png` 为 release 包装机后的画面。

### 2 五形态结果

本周：抬头「本周 / 更新于 13:44 / 打开桌面」，正文「19分钟」、单日柱、底部「阅读 1/7 天 · 日均 9 分」。本月：抬头「本月」，正文「22小时15分 / 日均阅读 1小时14分」、热力格、底部「阅读 18/31 天」。本书：书封、书名《罪与罚》、作者、进度 23%、进度条与「选书 / 打开」按钮。本记：摘录正文、书名《卡拉马佐夫兄弟（套装上下册）》、章节与时间、「上一条 / 换一条」按钮。待办：显示组件占位「暂无阅读数据 / 在书架设置中填写密钥后显示」，设备无 todo_list 数据，属预期空态。

五形态均按内核 CardRenderer 绘制，未出现自绘残留。release 包装机后点击标题区可正常切到本月，书封与热力格渲染一致。

### 3 点击与长按

点击抬头左侧标题区触发形态循环切换，debug 与 release 均验证通过（`tap-01-after.png` 为本周切到本月）。按压约 600 毫秒触发长按，打开组件选择器（可添加与移除组件），长按坐标由宿主转发。修正 `onTouch` 的 UP 与 CANCEL 分支：返回复位前的消费状态，避免宿主把点击序列判为未消费。

### 4 滚动

内核 1.3.2-k1 的滚动上限由渲染器回写：本记在正文溢出时写 `noteScrollMax`，月形态仅在内容总高超过卡片高度时写 `monthScrollMax`（源码注释「装得下就不滚」）。当前设备的本记摘录未溢出正文可视区，月卡热力格与排名区也未溢出，两处上限均为 0，因此快速滑动不会产生位移；月形态另需全屏档才进入整页滚动分支。滚动实现已按内核回写值钳制并吸附行高，待有溢出样本（更长摘录或全屏月历数据）时复测。

### 5 已知偏差

上一条与换一条按钮命中内核 `OPEN` 区，经 `openPage("note")` 跳转 `Destination.Note` 页面；原版为切换本地笔记条目，当前数据层只提供最新一条笔记，笔记切换未实现。月历滚动经反射写入 `monthScrollY`，release 构建若对内核类成员混淆会退化为不滚动，其余形态不受影响，建议 k2 内核补公开设置方法。

数据源侧 `KernelDataSourceImpl` 已按 lead 修改为原盘优先（shelf、period、bill、assets、fingerprint），笔记与待办仍走内容服务；本轮未改动该文件。

### 6 旧结论更正

第五节的「宿主不转发 MOVE」与 8.4 节的 ktlint 失败均已被后续版本修正：`HomeView` 现转发 `WidgetTouchAction` 的按下、移动、抬起与取消，并在抬起时按命中区域判定点击；全仓 `ktlintCheck detekt` 现已通过。第八节关于月历滚动反射的退化风险仍成立。

## 十 k3 内核回归（2026-10-20）

### 1 版本与依赖

`libs/weread-kernel-1.3.2-k3.jar`，180979 字节，SHA-256 `cfcdc05f4a9fe4ac76b98f8ce11e14136a5092b59f788c4b25f36c96eb8e4621`，类文件 62 个。`feature/widgets/build.gradle.kts:38` 依赖为 `implementation(rootProject.files("libs/weread-kernel-1.3.2-k3.jar"))`。组件侧 `CardKernel`、`CardState`、`CardView`、`CardSurface` 公开接口与 k2 一致，`KernelWereadBoard`、`WereadBoardWidget`、`KernelCardFiller` 未改动即可编译。

### 2 构建与单测

```powershell
& "$tc\gradle\gradle-8.11.1\bin\gradle.bat" --offline :feature:widgets:assemble :feature:widgets:testDebugUnitTest :app:assembleDebug
```

BUILD SUCCESSFUL，18 秒，374 个任务。随后 `:app:assembleRelease` 同样通过，用于覆盖安装回归。

### 3 真机结果

设备覆盖安装 debug 与 release（versionCode 23、versionName 1.0.3，同签名保留数据），`files/weread` 未清理。截图目录 `_analysis/verify/20261020-1539-k3/widget/`：

`form-01-week.png`：本周，更新于 14:03，19分钟，单日柱，阅读 1/7 天、日均 9 分、较上周 ↓85%。
`form-02-month.png`：本月，更新于 08:32，13小时35分，日均 1小时21分、较上月 ↑7%，热力格，阅读 9/31 天。
`form-03-book.png`：本书，更新于 13:58，书封（WereadCoverStore）、书名《卡拉马佐夫兄弟（套装上下册）（陀思妥耶夫斯基文集2015）》、作者、28小时48分、进度 35%、选书与打开按钮。
`form-04-note.png`：本记，摘录正文、书名、章节与时间、上一条与换一条按钮。
`form-05-todo.png`：待办，内核空态「暂无待办 / 下拉刷新或稍后再试」，`files/weread` 内无 todo_list。
`tap-01-week.png`、`tap-02-month.png`、`tap-03-book.png`：点击抬头标题区循环切换形态，k3 下正常。
`release-01-book.png`：release 包装机后的本书形态，书封为《万有引力之虹（托马斯·品钦作品）》，进度 0%，封面与进度数值正常刷新。
`state-02-restored.png`：配置恢复后的原始画面。

安装前设备上残留一次「Application Not Responding: com.shuiyu.octoberrain」对话框（release 1.0.3 实例，非本轮 k3 构建）。本轮 debug 与 release 启动后 logcat 无 `ANR in com.shuiyu`、无 `FATAL EXCEPTION`、无 `Fatal signal`。

### 4 配置处理

验证前备份到 `_analysis/verify/20261020-1539-k3/widget/backup/octoberrain_config.xml`（5542 字节，version 10），切换形态只改 `forms["weread_board"]` 的 digit，验证后已原样写回并重启确认画面一致。`files/weread` 未做任何清理。

### 5 k3 公开 setter 替换反射

`javap -classpath libs/weread-kernel-1.3.2-k3.jar com.inkread.weekread.kernel.CardState` 显示 k3 已提供 `public void setMonthScrollY(float)`。据此修改 `feature/widgets/.../kernel/KernelWereadBoard.kt`：`scrollMonth` 直接调用 `cardState.setMonthScrollY(target)`，删除 `MonthScrollBridge` 反射对象及其 KDoc，`scrollBy` 的注释同步改写为公开设置方法。改动后 `:feature:widgets:ktlintCheck`、`:feature:widgets:detekt`、`:feature:widgets:assemble`、`:feature:widgets:testDebugUnitTest` 均 BUILD SUCCESSFUL，`:app:assembleRelease` 通过。

### 6 最终 release 包复验

安装最终 release 包（versionCode 23、versionName 1.0.3）后截图 `release-02-book-final.png`：本书形态，书封《万有引力之虹（托马斯·品钦作品）》、作者、时长 0秒、进度 0%，选书与打开按钮；本次在读书目进度为零，与数据一致。点击抬头标题区后切到本记（`release-03-tap-note.png`），形态切换正常。启动后 logcat 无 `ANR in com.shuiyu`、无 `FATAL EXCEPTION`、无 `Fatal signal`。配置已按验证前备份原样写回（`state-03-restored-final.png` 与备份后的本书形态一致），`files/weread` 未清理。

## 十一 task-23 卡片交互完整实现

### 1 改动文件

`feature/widgets/src/main/java/com/shuiyu/octoberrain/feature/widgets/WereadBoardKernelBridge.kt`：`WereadBoardKernel` 增加 `todoCount()`，供组件判断待办抬头是否由内核绘制。

`feature/widgets/.../kernel/KernelWereadBoard.kt`：`handleHit` 按 `HitRegions.TODO_ITEM_BASE` 至 `TODO_ITEM_BASE + TODO_MAX` 区间换算条目下标，其余编号走 `CardState.onAction`；新增 `todoCount()`；`currentBookId` 拆为两行以符合链式换行规则。

`feature/widgets/.../kernel/KernelDataSourceImpl.kt`：`reorder` 改为单出口，手动选定书目后仍把该书排到书架首位。

`feature/widgets/.../WereadBoardWidget.kt`：`drawWidget` 拆出 `renderKernel`，按缩放绘制内核并在失败时画占位；`onClick` 改为单出口 `when`；`handleMove` 改为单出口表达式，位移超过阈值后按缩放折算滚动量；`ensureKernel` 拆出 `createKernel`，工厂缺失或绑定失败时释放实例；`toggleTodo` 改为单出口并在成功后刷新数据与重绘；`hideMenuLayout` 的文案与动作配对修正为「文案 到 动作」，此前行文案显示为 `hide:15` 等动作标识；删除与内核重复的待办抬头补画。

### 2 门禁

`:feature:widgets:testDebugUnitTest`、`:feature:widgets:ktlintCheck`、`:feature:widgets:detekt`、`:app:assembleDebug` 均 BUILD SUCCESSFUL。

### 3 真机验证

截图目录 `_analysis/verify/20261020-1900-task23/widget/`，设备 SA4B03CN2608081096X。

五形态切换：待办点抬头回到本周（`step-02-week.png`），其后依次本月、本书、本记、待办，切换正常。刷新：点「更新于」区域后右下角出现「刷新中」（`step-12-refresh-mark.png`）。本书：点「打开」跳转 `com.tencent.weread.eink/ReaderFragmentActivity`；点「选书」弹出候选菜单（`step-08-book-pick.png`），选中「万有引力之虹」后本书形态随之切换（`step-11-book-auto.png`）。本记：点「换一条」内容变化（`step-16-note-next.png`），点「上一条」回到原条目（`step-17-note-prev.png`）。长按抬头弹出隐藏菜单，四行文案为「隐藏 15 秒」「隐藏 1 分钟」「隐藏 5 分钟」「取消」（`step-20-hide-menu.png`）；选「隐藏 15 秒」后卡片消失（`step-22-hidden.png`），约 15 秒后恢复（`step-23-restored.png`）。配置按验证前备份写回（`step-24-restored-final.png`），`files/weread` 未清理。

### 4 已知偏差

待办无条目数据，逐条勾选只验证到空态；本记内容未超出可视高度，滑动不产生位移；卡片抬头仍绘出「打开桌面」按钮，`KernelHostImpl.deskEnabled()` 已返回 false，该按钮由内核渲染器绘制，组件侧无法移除；隐藏期间卡片区域显示「加载中」而非完全空白。

## 十二 组件外观设置即时生效与重绘循环修复

### 1 任务与写域

lead 要求组件侧八项外观设置在 `files/weread/kernel_prefs.json` 改动后立即生效，写域限定 `feature/widgets/src/main/java/com/shuiyu/octoberrain/feature/widgets/**`，`core/common/WereadAppearance.kt` 由 lead 维护。

### 2 改动文件

`kernel/KernelWereadBoard.kt`：新增 `readAppearance(root: File)` 与渲染期 `rendering` 标记；`KernelCardFiller.apply(cardState, form, appearance)` 注入 `setNoteCardSize(appearance.effectiveNoteSize)`、`setNoteFullSize(appearance.effectiveBodySize)`、`setMonthHeatmap(appearance.monthHeatmap)`、`setAchievementText(...)`（周/月目标分钟数大于零时输出「本周目标 X 分钟」「本月目标 X 分钟」）；渲染主体包在 try/finally 中维护 `rendering`。
`kernel/KernelDataSourceImpl.kt`：新增 `appearance()` 与 `filteredRank(period)`，`rankShelfOnly` 为真时按 `shelf().ids` 过滤榜单。
`kernel/KernelHostImpl.kt`：构造函数增加 `onRedraw: () -> Unit` 参数，`requestRedraw()` 转调该回调；`deskEnabled()` 返回 false。
`WereadBoardWidget.kt`：绘制前按 `appearance().paperArgb` 填底色；非白纸色时先把内核绘制到复用位图，再以正片叠底合成到画布；`loadData` 负责创建并绑定内核（IO 线程），`drawWidget` 只使用已存在的内核，避免绘制线程同步读盘；`onWidgetDestroy` 回收位图。
新增 `kernel/WereadAppearanceCache.kt`：按文件长度与修改时间缓存外观快照，避免逐帧解析 500 KB 偏好文件。

### 3 真机 ANR 根因与修复

现象：`ANR in com.shuiyu.octoberrain`，`Reason: Input dispatching timed out`，主线程持续 89% 以上，`dumpsys gfxinfo` 帧数为 0。

定位手段与结论：
一、`top -H` 显示主线程与 HeapTaskDaemon 持续占用；把桌面配置 `widgetId` 置空后主线程占用回落到 15% 量级，确认空转来自组件绘制链路。
二、将 `KernelHostImpl.requestRedraw()` 改为空实现后主线程回落且不再 ANR，确认内核在每帧渲染期间调用 `requestRedraw()`，经组件 `requestDraw()` 触发 `invalidate`，形成无限重绘。
三、将内核创建与绑定从 `drawWidget` 移入 `loadData`（IO 线程）后，启动阶段主线程不再阻塞数秒。该处原先由 `ensureKernel()` 在绘制线程同步调用内核绑定，绑定内部按 4 秒超时同步读取数据源，直接造成输入超时。

修复后实测：稳态主线程 15%–18%，`Total frames rendered` 正常增长，连续运行未再出现 ANR。

### 4 门禁与真机截图

门禁命令：`gradle --offline :core:common:compileDebugKotlin :feature:widgets:compileDebugKotlin :app:ktlintCheck :app:detekt :app:assembleDebug`，结果 BUILD SUCCESSFUL。

截图目录 `_analysis/verify/20261020-appearance/`：
`item-01-paper-gray.png` 纸色改灰后卡片底色变化；`item-02-note-size-17.png` 与 `item-03-note-size-24.png` 本记字号的正文行数与「展开」按钮随之变化；`item-04-month-checkin.png` 月历打卡样式；`item-05-month-heat.png` 月历热力样式；`item-06-signature-on.png` 与 `item-07-signature-off.png` 署名开关；`item-08-rank-shelf-only.png` 榜单范围开关；`item-09-restored.png` 恢复备份后的本书形态。

### 5 已知偏差与遗留

一、署名与榜单两项设置已进入组件读取路径，但 k5 渲染器在卡片上不绘制对应元素，真机截图无可见差异；两项的实际呈现位置在墨单页面。
二、卡片尺寸档 `card_pool_mask` 与切换动画样式 `switch_mode` 无组件侧改动位置，本轮未处理。
三、纸色为白纸以外颜色时，每帧使用一块与组件同尺寸的复用位图做正片叠底，尺寸变化时重建并回收旧位图。
四、验证用配置与 `kernel_prefs.json` 已按验证前备份写回，`files/weread` 未清理；设备为 S4 SA4B03CN2608081096X，安装包 versionCode 26、versionName 1.0.6。

## 十三 实验室页三段页签补齐（2026-10-20）

### 1 依据与写域

按 `docs/21` 与微读墨记原版 `settings_layout.xml` 的 `page_lab` 结构、`LabPageController.bindLabTabs()` 与 `lab_strings.txt`（108 条原文文案）实现。写域只含 `feature/weread-app/src/main/java/com/shuiyu/octoberrain/feature/wereadapp/ui/` 下四个文件，未改动 `WereadReadingPage.kt`、`WereadAppScreen.kt`、`WereadMicroBridge.kt` 与 `feature/content`。

### 2 改动文件

`WereadLabPage.kt` 重写为外壳：标题「实验室」、固定 `SegmentBar`（文案「翻页」「锁屏密码」「续航优化」）、`FrameLayout` 页面容器；对外保持 `interface Listener { fun onLabDisabled() }`、`var listener` 与 `fun refresh()`，与 `WereadAppScreen` 的既有调用方式一致。同文件新增 `LabPageView` 基类（`ScrollView` + 内容 `LinearLayout`，`refresh()` 清空后 `render()`）与顶层构件函数 `labSectionTitle`、`labCaption`、`labBody`、`labSpacer`、`labDivider`、`labToast`、`labCheckRow`、`labRadioGroup`、`labFieldRow`、`labWideButton`、`labButtonRow`、`labPill`、`labFold`。

`WereadLabFlipPage.kt` 新增：连接方式三选一三项禁用灰显（关闭（默认）/ 热点连接 · Wi-Fi 直连（手机开热点）/ 蓝牙连接 · 手机当蓝牙键盘（免热点））、说明「由微读墨记 App 维护；十雨桌面只展示分组形态。」、状态胶囊「当前：关闭」、角色三选一（关闭/墨水屏/手机，写偏好键 `remote_role`）、折叠「注意事项」、实验内容门禁与展开内容（数据目录、文件清单、十六行偏好快照、重开卡片与收起实验内容）、页尾整宽「打开系统无障碍设置」。

`WereadLabLockPage.kt` 新增：简介、启用锁屏勾选（键 `lock_enabled`）、四位数字密码输入与保存/清除（键 `lock_pin_salt` 为 8 字节随机盐、`lock_pin_hash` 为 SHA-256(salt‖pin)，正则 `^\d{4}$`，非法时提示「请输入四位数字密码」）、锁屏背景路径与载入/恢复默认（键 `lock_bg_path`，相对路径补 `/sdcard/Pictures/`）、状态行与无障碍提示。

`WereadLabPowerPage.kt` 新增：简介、写入权限状态、授权命令「adb shell pm grant ${packageName} android.permission.WRITE_SECURE_SETTINGS」与复制按钮（`ClipboardManager`）、三项系统开关（Global `wifi_networks_available_notification_on`、Global `window_animation_scale`/`transition_animation_scale`/`animator_duration_scale`、Secure `screensaver_enabled`，关闭写 0，首次关闭把原值记录到 SharedPreferences `power_cfg` 的 `cap_<键名>`，恢复时写回原值）、「打开系统电池优化页」与「Shizuku 入口（系统应用信息）」。

### 3 门禁

`:feature:weread-app:compileDebugKotlin`、`:feature:weread-app:testDebugUnitTest`、`:feature:weread-app:ktlintCheck`、`:feature:weread-app:detekt`、`:app:assembleDebug` 均 BUILD SUCCESSFUL。期间 `:feature:weread-app:detekt` 一次被并发修改的 `WereadMicroBridge.kt`（11 个函数，属其他成员写域）阻断，重试通过。detekt 处置：三个页面类各 12–14 个函数，按类加 `@Suppress("TooManyFunctions")`，`WereadLabPage.kt` 用 `@file:Suppress("TooManyFunctions")`；授权命令字符串按 `MaxLineLength` 拆两行；删除未用 import。

### 4 真机验证（P4L202610130006，versionCode 30、versionName 1.0.10）

截图目录 `_analysis/verify/20261020-parity/`：`p4-lab2-00-menu.png`（桌面长按菜单）、`p4-lab2-00-settings.png`（设置首页）、`p4-lab2-00-weread.png`（微读墨记页）、`p4-lab2-00-flip.png`（翻页段首屏：三段页签带下划线、连接方式三项灰显、状态胶囊、角色单选）、`p4-lab2-01-flip-scroll.png`（翻页段滚动：角色「手机」选中、注意事项折叠、实验内容门禁、版本 1.0.10）、`p4-lab2-02-lock.png` 与 `p4-lab2-03-lock-scroll.png`（锁屏密码段两屏）、`p4-lab2-04-power.png` 与 `p4-lab2-05-power-scroll.png`（续航优化段两屏）。

验证结论：三段页签常显可切，切换后页签下划线跟随；每页可纵向滚动（慢速拖动 `input swipe 500 640 500 340 900` 生效，快速滑动会被外层分页手势截获并切回「本周」）。

### 5 已知偏差与遗留

翻页段的连接方式与角色在十雨侧只作展示与本地偏好记录（键 `remote_role`），实际连接与遥控由微读墨记 App 维护；验证时点击角色单选已将 `remote_role` 记为 2（手机），未恢复。锁屏与续航两页的开关只写入内核偏好与系统设置，锁屏界面与电源开关的实际效果依赖微读墨记 App 的既有实现。

## 十三 k6 BookPicker 迁移

### 改动文件

`feature/widgets/src/main/java/com/shuiyu/octoberrain/feature/widgets/WereadBoardWidget.kt`：引入 `com.inkread.weekread.kernel.pick.BookPicker`，字段 `bookPicker`；`showBookPicker()` 构造候选（首项空编号「自动（最近在读）」+「最近在读」，随后 `kernel.bookOptions()`），设置标题「选一本书」、关闭「关闭」、遮罩「点空白处关闭」，`bind(items, kernel?.selectedBookId().orEmpty())` 后 `resetScroll()`；新增 `handlePickerClick`（`hitTest` 三分支：`HIT_DISMISS` 收起、`HIT_NONE` 忽略、其余按 `bookIdAt` 切书）、`handlePickerScroll`、`drawBookPicker`、`applyPickedBook`；删除自绘浮层的 `pickerLayout`、`isSelectedRow`、`selectBook(action)`、`ACTION_PICK_*`、`OVERLAY_SELECTED_BAR`、`OVERLAY_BAR_INSET`、`PICKER_BOOK_LIMIT` 等。

`feature/widgets/.../kernel/KernelWereadBoard.kt`：本书形态 zones 顺序改为 `pickBox` 在 `openBox` 之前。

### 关键结论

`BookPicker.render` 必须画在已按 `scale` 缩放的逻辑画布内，且参数为逻辑宽高与 `unit=1f`。最初在 `restore()` 之后用组件像素宽高与 `unit=scale` 调用，弹层完全不显示；改为在缩放画布内调用、命中与滚动同步使用 `x/scale`、`y/scale` 与逻辑宽高后显示正常。原因在版式常量全部以 unit 为单位计算，外层未缩放时两者叠加导致弹层落在可视区之外。

### 验证

门禁：`gradle --offline :feature:widgets:ktlintCheck :feature:widgets:detekt :feature:widgets:testDebugUnitTest :feature:widgets:assemble :app:assembleDebug` BUILD SUCCESSFUL。

真机截图（`_analysis/verify/20261021-user-bugs/`）：P4（P4L202610130006，552×768）`p4-bookpick2-00-book.png`、`p4-bookpick2-01-picker.png`；S4（SA4B03CN2608081096X，480×800）`s4-bookpick2-00-book.png`、`s4-bookpick2-01-picker.png`。弹层含遮罩与「点空白处关闭」、白色底板与四边墨框、标题行「选一本书 / 关闭」、浅灰行分隔线、当前行左侧黑竖条、行文字垂直居中。点击候选后卡片切换到所选书目（`p4-bookpick2-05-row1.png` 万有引力之虹），再次打开时选中条落在该书目行（`p4-bookpick2-06-reopen.png`）。「关闭」与点遮罩收起正常。

### 偏差

列表拖动未能在 P4 上完成截图确认：设备在滑动时反复被系统通知面板与第三方应用抢占前台，`p4-bookpick2-08-scrolled.png` 记录到通知面板。代码路径为 `hitTest` 与 `scrollBy` 同一套逻辑坐标，`scrollBy` 上限由内核 `maxScroll` 计算，待后续无干扰时补一张滚动截图。
