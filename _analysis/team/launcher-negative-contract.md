# 十雨桌面负一屏与组件宿主接入契约核对

核对范围：`core/common`、`feature/home`、`feature/files`、`feature/widgets`、`feature/settings`、`core/data`、`app`。
核对性质：只读。本文只列现状、改动面与接口建议，不含代码改动。

## 一、负一屏现状与模块协议

### 1.1 页面结构

`feature/home/src/main/java/com/shuiyu/octoberrain/feature/home/NegativeScreen.kt`（298 行）是负一屏的唯一宿主。

- 类声明：`internal class NegativeScreen : Screen, NegativeHost`（:38-40）。
- 视图结构：`SwipeBackFrame`（:260-297）为整屏不透明根容器，`ScrollView`（:81-85）内放纵向 `LinearLayout`（:86-90），模块之间以 `InkWidgets.spacer(tokens.gapLarge)` 分隔（:179-181）。
- 模块绑定：`applyConfig(cfg)`（:161-194）读取 `cfg.desktop.negativePage.modules`，按列表顺序从注册表取模块并调用 `createView(context, this)`；视图创建失败或模块未注册时跳过；全部不可用时进入空态（:197-239）。
- 配置监听：`createView` 内收集 `AppServices.config.state`（:108-111），模块 id 列表变化才重建（:163-164）。
- 后注册补建：`onResume` 比较 `boundAvailable` 与 `expectedAvailable()`（:116-125、:242），模块注册晚于页面创建时重建。
- 生命周期：`onResume` 转发模块（:116-125）、`onPause` 转发模块（:128-131）、`onDestroy` 只调用模块 `onPause` 后清空引用（:134-143）。
- 左滑返回：`SwipeBackFrame.dispatchTouchEvent`（:269-279）记录按下点，抬起时水平位移超过 `desktop.swipeMinDp` 且水平占优则 `AppServices.router.back()`（:287-296）。
- 入口：`HomeScreen.kt:42-43` 设置 `onNegativePageRequested` 后跳 `Destination.Negative`；`HomeModule.kt:35` 创建 `NegativeScreen()`；`HomeView.kt:993` 在首页右滑且 `negativePageEnabled()` 为真时触发；`HomeView.kt:720` 读取 `desktop.negativePage.enabled`。

### 1.2 模块契约

`core/common/src/main/java/com/shuiyu/octoberrain/core/common/Contracts.kt`：

- `interface NegativeModule`（:1089-1107）：`val id: String`（:1091）、`val label: String`（:1094）、`fun createView(context: Context, host: NegativeHost): View?`（:1097-1100）、`fun onResume()`（:1103）、`fun onPause()`（:1106）。
- `interface NegativeHost`（:1110-1116）：`fun requestRedraw()`（:1112）、`fun open(destination: Destination)`（:1115）。
- `object NegativeModuleRegistry`（:1119-1132）：`LinkedHashMap<String, NegativeModule>`，`register(module)` 重复 id 覆盖（:1123-1125）、`all()`（:1128）、`get(id)`（:1131）。

现有注册点：

- `feature/content/.../ContentModule.kt:34` 注册 `BookshelfNegativeModule()`（id `bookshelf`，类见 `feature/content/.../ui/BookshelfNegativeModule.kt:47`）。
- `feature/files/.../FilesModule.kt:38` 注册 `FilesNegativeModule()`（id 定义见 `feature/files/.../ui/negative/FilesNegativeModule.kt:356-358`，`ID = "negative_files"`）。

现有模块行为（供墨台三卡照抄范式）：

- `FilesNegativeModule`（:107-360）：`createView` 内构建卡片、创建 `MediaIndex`、首次读取后置 `skipNextResume`（:130-142）；`onResume` 跳过首次后按令牌重载（:145-158）；`onPause` 取消加载任务；点击缩略图经 `RecentImages.viewerTarget` 生成 `Destination.PhotoViewer`（:73-80），底部「全部图片」入口生成 `Destination.Files()`（:83）。
- `BookshelfNegativeModule`（:47-572）：卡片标题行右侧「全部」入口跳 `Destination.Bookshelf`；齿轮入口跳 `Destination.BookshelfSettings`；行数在 1 至 6 之间调整并写 `integrations.bookshelfRows`；数据经 `ContentRegistry.source` 的缓存快照读取，进入页面后触发异步刷新。

### 1.3 排序、开关与配置存储

- 排序：`NegativePageConfig.modules` 列表顺序即上下顺序（`core/common/.../Models.kt:86-98`，字段 :97），设置页只提供增删，没有上移下移控件。
- 开关：`feature/settings/.../NegativeSpecs.kt`。模块 id 常量 :14、:17；分组 `GROUP_NEGATIVE_CARDS` :19-27；两条开关 `negative.bookshelf`、`negative.files` :29-47；写入函数 `applyNegativeModule`（:82-91）在开启时追加到末尾并去重、关闭时移除；`moduleEnabled`（:94-100）。
- 配置渲染：`feature/settings/.../DesktopPrefsScreen.kt:69-75` 用 `SettingsSpecRenderer.renderPage(groups = SettingsCatalog.negativeGroups, ...)` 渲染在「桌面偏好」页。
- 版本字段：`LauncherConfig.CURRENT_VERSION = 7`（`Models.kt:374`）；`NegativePageConfig` 已有 `enabled`、`kind`、`limit`、`showProgress`、`showCover`、`modules` 六项，其中 `kind/limit/showProgress/showCover` 属旧整体式负一屏遗留字段，模块化后没有读取点（全仓检索仅 `Models.kt` 命中），可在下一版迁移中保留字段并停止使用。

## 二、墨台并入负一屏的改动面

### 2.1 目标结构

按 `_analysis/plans/weread-embed-and-refactor-plan.md` 第六节与 `_analysis/team/weread-whole-and-moyu-plan.md` 第五节，墨台三个模块各成一张负一屏卡片：

| 模块 id | 名称 | 数据来源 |
| --- | --- | --- |
| `weread_bill` | 阅读账单 | `WereadContent.wereadPeriod(WEEKLY/MONTHLY)`、`wereadBill()` |
| `weread_assets` | 阅读资产 | `WereadContent.wereadShelf(limit)` 与逐本进度 |
| `weread_fingerprint` | 阅读指纹 | `WereadContent.wereadFingerprint()`、`wereadPeriod(MONTHLY)` |

首版数据缺口：逐本资产依赖全量书架与批量进度，现有 `WereadClient.shelf()` 只取前 40 本并补 8 本进度；价格链无存储与录入界面。两项按计划列为后续数据任务，卡片首版只显示时长、条目与进度。

### 2.2 需要新增的类

| 类或接口 | 位置 | 建议签名 |
| --- | --- | --- |
| `WereadBillNegativeModule` | `feature/content/.../ui/negative/` | `class WereadBillNegativeModule : NegativeModule`，`id = "weread_bill"`、`label = "阅读账单"`、`createView(context, host): View`、`onResume()`、`onPause()` |
| `WereadAssetsNegativeModule` | 同上 | 同上，`id = "weread_assets"` |
| `WereadFingerprintNegativeModule` | 同上 | 同上，`id = "weread_fingerprint"` |
| `WereadNegativeLogic` | `feature/content/.../ui/negative/` | 纯函数集合：`billRows(stats: WereadStats?, bill: WereadBillSnapshot?): List<Row>`、`assetRows(shelf: List<WereadShelfItem>, progress: Map<String, Int>): List<Row>`、`fingerprintCells(snapshot: WereadFingerprintSnapshot?): List<Int>` |
| `NegativeSettingsScreen` | `feature/settings/.../` | `class NegativeSettingsScreen : SettingsBaseScreen("负一屏")`，渲染 `SettingsCatalog.negativeGroups` |
| `NegativeEntrySettings`（可选） | `feature/settings/.../NegativeSpecs.kt` | 在现有分组内追加三条 toggle 与三条 `moveUp/moveDown` Action |

### 2.3 需要修改的类与方法

| 文件与位置 | 改动 |
| --- | --- |
| `Contracts.kt:1089-1107` | `NegativeModule` 增加 `fun onDestroy() {}` 默认方法（模块协程与位图在页面销毁时释放）；增加 `val defaultEnabled: Boolean get() = false` |
| `Contracts.kt:1110-1116` | `NegativeHost` 增加 `val context: Context` 或 `fun widthDp(): Int`，供模块按屏宽计算卡片几何 |
| `Contracts.kt:1119-1132` | `NegativeModuleRegistry` 增加 `fun ids(): List<String>` 与 `fun contains(id: String): Boolean` |
| `NegativeScreen.kt:74-113` | `createView` 在滚动容器上方增加固定顶栏：标题「负一屏」与右上角齿轮按钮；齿轮跳转 `Destination.NegativeSettings` |
| `NegativeScreen.kt:134-143` | `onDestroy` 改为依次调用模块 `onDestroy()` 再清空 |
| `NegativeScreen.kt:161-194` | `applyConfig` 中模块 id 列表含 `weread_*` 时按三卡顺序渲染；墨台三卡默认开启由默认配置与迁移保证，不写死在宿主 |
| `HomeModule.kt:35` | 无需改动，`Destination.Negative` 仍由 `NegativeScreen` 承接 |
| `HomeView.kt:720`、`:993` | 保持现状；负一屏顶栏齿轮是新增入口，不改变右滑进入方式 |
| `Models.kt:86-98` | `NegativePageConfig` 增加 `val forms: Map<String, Int> = emptyMap()`（卡片形态）与 `val cardOrderInitialized: Boolean = false`；`CURRENT_VERSION` 升 8 |
| `NegativeSpecs.kt:19-47` | 分组副标题改为「选择与排序负一屏卡片」；追加 `weread_bill`、`weread_assets`、`weread_fingerprint` 三条 toggle，默认开启 |
| `DesktopPrefsScreen.kt:69-75` | 保持渲染入口；顶栏齿轮可跳到独立 `NegativeSettingsScreen`，也可复用本页，二者取其一 |

### 2.4 墨台本体与卡片的边界

墨台本体（阅读账单、阅读资产、阅读指纹三块内容）不再保留独立全屏页，三块内容分别由三张负一屏卡片承载。卡片内部的「全部」入口按原版落点跳 `Destination.WereadTab(section)`，不新增页面级 Destination；设置入口由负一屏右上角齿轮进入 `NegativeSettingsScreen`，墨台自身的模块顺序、壁纸与导出项并入微读墨记设置分组（`BookshelfSpecs` 扩展）。

## 三、组件协议对整体卡片的承载能力

### 3.1 现有协议

`core/widget-api/src/main/java/com/shuiyu/octoberrain/core/widget/WidgetApi.kt`（225 行）：

- `interface WidgetView`（:18-58）：`onSizeChanged(width, height)`（:20-23）、`onConfigChanged(config)`（:26）、`onRefresh(force)`（:29）、`draw(canvas, width, height)`（:32-36）、`onClick(x, y): Boolean`（:39-42）、`transparentBackground`（:50-51）、`onLongPress()`（:54）、`onDestroy()`（:57）。
- `interface WidgetHost`（:61-67）：`requestWidgetDraw(widget)`、`openDestination(destination)`。
- `abstract class BaseWidgetView`（:75-124）：尺寸缓存（:92-103）、`requestDraw()`（:109-111）、文本与描边画笔（:114-123）。
- `WidgetCategory`（:131-138）、`WidgetRefreshPolicy`（:145-154，`MINUTE/EVENT/MANUAL`）、`triggersOnMinute()`（:162）。
- `interface WidgetProvider`（:171-181）：`id/label/description/category/refreshPolicy/requiredCapabilities/create(context)`。
- `WidgetRegistry`（:184-203）：`register/get/all/create`。

宿主侧：`HomeView.kt` 只持有一个组件实例与一个矩形（:79-83 字段、:391-416 `ensureWidget`、:487-526 `drawWidget`），点击按相对坐标转发 `onClick(event.x - rect.left, event.y - rect.top)`（:964-969），长按由 `widgetLongPressRunnable` 调 `widget?.onLongPress()`（:121-125）。`WidgetCatalog.kt` 共 18 个组件（:228-249），分类表 :251-268。

### 3.2 缺口清单

| 能力 | 现状 | 缺口 |
| --- | --- | --- |
| 整体卡片五形态切换 | 无形态概念；组件只按宽高绘制 | 需要卡片形态枚举与持久化字段（`NegativePageConfig.forms`），以及 `WidgetView.setForm(form)` 或等价回调 |
| 卡内多热区 | `onClick(x, y)` 可按坐标自行分区，宿主只转发点击 | 缺少声明式热区协议，菜单与提示无法由宿主统一绘制；建议增加 `fun hitTest(x, y): WidgetHit?` |
| 长按菜单 | `onLongPress()` 无坐标，`InkWidgetView.onLongPress()` 直接打开组件选择页（`InkWidgetView.kt:189-190`） | 需要带坐标的长按（`onLongPress(x, y)`）与宿主侧菜单容器；卡片内长按应打开卡片菜单而非组件选择页 |
| 纯自绘 | `InkWidgetView` 的 `drawWidget(canvas)` 与 `InkDraw` 工具满足 | 基本满足；需要补位图与灰度纹理缓存的生命周期管理 |
| 每卡独立刷新 | 刷新策略按 Provider 全局声明（`WidgetRefreshPolicy`），桌面整分刷新统一调度（`HomeView.kt:235-255`） | 需要按卡片实例的 `refreshPolicy` 与刷新间隔；事件刷新需要内容层广播到指定卡片 |
| 多实例与持久化 | `desktop.widgetId` 单值（`Models.kt:133`），全局只有一个组件槽 | 负一屏卡片需要多实例容器与每卡配置存储（`forms`、顺序、隐藏状态） |

### 3.3 建议的最小扩展

1. `WidgetView` 增加默认方法 `fun onLongPress(x: Float, y: Float) { onLongPress() }`，`HomeView` 长按回调传相对坐标。
2. 新增 `data class WidgetHitRegion(val id: String, val left: Float, val top: Float, val right: Float, val bottom: Float)`，`WidgetView` 增加 `fun hitRegions(): List<WidgetHitRegion> = emptyList()`；宿主点击先查热区再回落 `onClick`。
3. 新增 `enum class NegativeCardForm { LARGE, MEDIUM, SMALL, LIST, TEXT }` 与 `NegativePageConfig.forms: Map<String, NegativeCardForm>`；卡片视图按形态选版式。
4. 新增 `interface NegativeCardHost : WidgetHost`：`fun openMenu(anchor: View)`、`fun requestCardReload(moduleId: String)`，供卡片菜单与刷新使用。
5. 每卡刷新策略写入 `NegativePageConfig`（`Map<String, Long>` 记录下次刷新时间或间隔），避免复用 `WidgetRefreshPolicy` 的全局语义。

## 四、设置声明表的最小改法

- 分组：沿用 `SettingsCatalog.GROUP_NEGATIVE_CARDS`（`SettingsSpec.kt:77` 附近常量），不新建分组。
- 追加项：在 `NegativeSpecs.specs` 追加三条 `SettingSpec`，`control = toggleModule("weread_bill")` 等，`afterWrite = { RefreshSchedulerBridge.notifyContentChanged() }`。
- 排序项：新增 `SettingControl.Action` 两枚（上移、下移），读当前 `modules` 顺序并在写入时交换相邻项；`NegativeSpecs.kt` 追加 `moveModule(modules, moduleId, delta)` 纯函数与对应测试。
- 入口：`NegativeScreen` 顶栏齿轮跳 `Destination.NegativeSettings`，在 `SettingsModule.kt:34-52` 的分派表加一行；`SettingsHomeScreen` 不新增卡片，保持「桌面偏好」入口。
- 生效：开关写入 `modules` 后由配置流驱动 `NegativeScreen.applyConfig` 重建，无需额外事件。

## 五、与内置微读墨记的数据交换

推荐进程内直调，不做文件导入。

- 现有 `ContentSource : WereadContent`（`Contracts.kt:188-249`）已包含 `wereadShelf`、`wereadPeriod`、`wereadNotes`、`wereadBill`、`wereadRank`、`wereadFingerprint`；负一屏三卡直接经 `AppServices.content` 读取，不建立第二套 HTTP 与缓存。
- 内置微读墨记作为同一进程内的功能模块时，把上述方法实现到 `ContentSourceImpl` 或新增 `WereadService`（`contracts` 扩展 `AppServices.weread`），负一屏卡片只依赖 `WereadContent` 接口，便于 JVM 单测注入假实现。
- 密钥维护：密钥唯一存放在 `integrations.wereadApiKey`（v7 起），负一屏与组件只读取状态，不复制；密钥变更后调用 `WereadClient.invalidate()` 清缓存并广播刷新。
- 刷新广播：沿用 `RefreshSchedulerBridge.notifyContentChanged()`（设置写入后已使用）与 `AppServices.refresh.requestPartial()`；卡片在 `onResume` 校验缓存时间戳后重载。

## 六、旧配置与旧模块 id 的迁移

- 版本：`CURRENT_VERSION = 7`（`Models.kt:374`）升为 8，`ConfigMigration.migrate`（`ConfigMigration.kt:61-88`）追加 `if (migrated.version < VERSION_V8) migrated = migrateV7ToV8(migrated)`。
- 默认值：`NegativePageConfig.modules` 默认改为 `listOf("weread_bill", "weread_assets", "weread_fingerprint")`；`bookshelf` 与 `negative_files` 变为可选，默认关闭。
- 迁移规则（`migrateV7ToV8`）：
  1. 读取旧 `modules`，过滤掉已删除的旧 id（`bookshelf`、`negative_files` 保留，其余未知 id 原样保留以避免误删第三方模块）。
  2. 若旧列表含 `bookshelf` 或 `negative_files`，说明用户此前启用过；迁移后顺序为「墨台三卡、原用户列表」，保证升级后新内容在前、用户旧选择不丢。
  3. 暗含三个墨台 id 已在列表中则不重复追加。
- 组件标识迁移：旧 `desktop.widgetId` 命中被删除的 12 个组件时改写（微信读书类改写为 `weread_board`，`reading/weather/koreader_month/usage` 与 `clock_compact` 改写为 `clock`）；`WidgetRegistry.create` 返回 null 时宿主已绘制「未安装组件」占位（`HomeView.kt:523`），迁移后不应再出现。
- 兼容：`NegativeModuleRegistry.register` 重复 id 覆盖（`Contracts.kt:1123-1125`）；旧 id `bookshelf`、`negative_files` 不变，旧配置无需改写；`NegativePageConfig` 旧字段 `kind/limit/showProgress/showCover` 保留默认值，不做删除。

## 七、删除组件的清理点

按 `_analysis/plans/weread-embed-and-refactor-plan.md` 第二节 2.6 的清单，删除 12 项、保留 5 项（`clock`、`calendar`、`image`、`placeholder`、新增 `weread_board`）。

清理位置：

1. `feature/widgets/.../WidgetCatalog.kt`：删除对应 `private val`（:36-226 中的 12 段）、`providers` 列表项（:228-249）、`byCategory` 列表项（:251-268）；保留项与新增项同步登记。
2. 删除组件源文件：`ReadingWidget.kt`、`WeatherWidget.kt`、`HeatMonthWidget.kt`、`UsageWidget.kt`、`CoversWidget.kt`、`WereadMarkWidget.kt`、`WereadNoteWidget.kt`、`WereadStatsWidget.kt`、`WereadBillWidget.kt`、`WereadRankWidget.kt`、`WereadFingerprintWidget.kt`；`WereadCardWidget.kt` 与 `WereadWidgetLogic.kt`、`WereadWidgetData.kt` 由新整体卡片评估复用，暂不删除。
3. 默认布局：`DesktopConfig.widgetId` 默认值 `clock`（`Models.kt:133`）不变；迁移改写已保存的旧 id；`HomeView.kt:523` 的占位分支保留。
4. 测试：`feature/widgets/src/test/.../WidgetCatalogTest.kt`（:15-67）的 id 集合、分组一致性、能力断言全部需要按新清单更新；`core/common` 的 `DestinationLabelsTest` 不涉及组件；新增 `weread_board` 的逻辑测试（形态段高、裁段顺序、空态）。
5. 设置：`DesktopPrefsScreen.kt:268-269` 的「当前组件」信息行保留，`widgetId == "image"` 分支（:269-276）保留；组件选择页 `WidgetPickerScreen.kt` 遍历 `WidgetCatalog.byCategory`（:37-39）无需改代码。
6. 文档：`docs/15-V1.0开发计划.md` 组件清单、`docs/08-engineering-conventions.md` 中组件相关条目、`CHANGELOG` 与版本说明需要同步；`app/proguard-rules.pro` 若新增 `weread_board` 的反射入口需要补保留规则。
7. 代码引用核查：全仓 `widgetId`、`widgetImagePath`、旧组件 id 的引用点已核（`Models.kt`、`WidgetPickerScreen.kt`、`WidgetCatalog.kt`、`ImageWidget.kt`、`DesktopPrefsScreen.kt`、`HomeView.kt`、`DesktopMetrics.kt`、`WidgetCatalogTest.kt`），删除后需重跑 `:feature:widgets:testDebugUnitTest` 与 `check`。

## 八、核对中发现的其他事实

- 负一屏当前没有顶栏、标题或齿轮入口，`NegativeScreen` 整页由滚动容器占据（:74-113），新增顶栏会改变模块内容区高度与 `pageTop` 留白，需要在 `createView` 内改为「顶栏 + 滚动容器」两层布局。
- 负一屏模块没有 `onDestroy`，`FilesNegativeModule` 的协程作用域在页面销毁时只经 `onPause` 取消加载任务，作用域本身未取消；墨台三卡接入前应补 `onDestroy`。
- `NegativeModuleRegistry` 没有清空入口，测试之间注册表会串状态；若新增注册表测试需要 `clear()`（仅测试可见，或用内部构造函数）。
- `BookshelfNegativeModule` 与 `FilesNegativeModule` 都实现了自己的缓存读取与视图刷新，墨台三卡沿用同一范式即可，不需要改动其代码。
- 组件宿主只有单实例，负一屏卡片不能直接复用 `HomeView` 的组件槽；建议新增 `NegativeCardHost`（见 3.3），不修改 `HomeView`。
- `CURRENT_VERSION` 与备份导入使用同一迁移函数（`BackupServiceImpl` 导入后调用 `ConfigMigration.migrate`），版本升 8 后旧备份同样走 `migrateV7ToV8`，无需单独改备份。
