# tenrain-negative 工作记录

仓库 `D:\DSHStation\OctoberLauncher`（非 git，回溯依赖 `scripts\snapshot.ps1 -Tag 1.0.1-negative`）。写作用域：`feature/home/.../feature/home/`、`feature/content/.../feature/content/ui/`、`feature/settings/.../feature/settings/`（不含 `qr/`）。

## 第一部分 B2 负一屏五模块

本节记录的五个自写模块与基类已在 task-10 第一段删除，改为微读原版墨台标识与内核分区渲染，详见文末 task-10 章节。

新增于 `feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/ui/negative/`：

- `NegativeCardModule.kt`：模块基类，统一卡片外壳、异步读取、取消与空态；提供 `TITLE`、`EMPTY_TEXT`、`detailTarget()` 等挂钩，子类只实现数据读取与正文渲染。
- `WereadBillNegativeModule.kt`（id `weread_bill`，orderHint 30）：账单卡片，月总时长、上月对比、逐日柱状分布，详情入口 `Destination.WereadTab(WereadSection.BILL)`。
- `WereadAssetsNegativeModule.kt`（id `weread_assets`，orderHint 40，`defaultEnabled = false`）：资产卡片，承接旧 v7 配置残留，分项与累计时长。
- `WereadRankNegativeModule.kt`（id `weread_rank`，orderHint 50）：排行卡片，按进度降序前五，详情入口 `WereadTab(RANK)`。
- `WereadFingerprintNegativeModule.kt`（id `weread_fingerprint`，orderHint 60）：指纹卡片，累计时长、有效天数、日均与热力格。

`feature/home/.../NegativeScreen.kt`：

- 顶部悬浮齿轮按钮，经 `NegativeHost.open(Destination.NegativeSettings)` 进入设置页。
- `onDestroy` 依次转发模块 `onDestroy`，再清理协程与容器。
- `effectiveModuleIds()`：`cardOrderInitialized` 为真按配置顺序；否则配置顺序后追加已注册、默认启用且未在配置中的模块，按 `orderHint` 排序。

## 第二部分 B4 页面补齐

`feature/content/.../ui/`：

- `BookScreen.kt`（`Destination.Book`）：书架概览、在读与读完数量、平均进度与划线总数，两列网格，点击进入微信读书。
- `NoteScreen.kt`（`Destination.Note`）：划线总数与当前展示数，按书分组列出划线、想法与时间。
- `TodoScreen.kt`（`Destination.Todo`）：待办合计与完成数，未完成为主、已完成置灰，展示更新时间。

`ContentModule.kt` 注册：四个新模块进 `NegativeModuleRegistry`；`ContentScreenProvider` 增 `Book`、`Note`、`Todo` 三个分支。

## 第三部分 设置页与宿主对接

- `feature/settings/.../NegativeSettingsLogic.kt`：纯逻辑，`moduleOrder`、`toggle`、`move`、`label`、`hint`、`enabled`。
- `feature/settings/.../NegativeSettingsScreen.kt`：卡片开关与顺序分组（逐行开关、上移下移，边界置灰）、墨单外观分组（十三项：标题、最短时长、单位、榜单条数、过滤下架、笔记来源、摘录模式、作者、时长、进度、页脚、块显示位、视图形态）、查看效果分组。写入统一走 `SettingsUi.edit`，顺序与开关变更后调 `RefreshSchedulerBridge.notifyContentChanged()`，墨单外观写入后调 `AppServices.refresh.requestPartial()`。
- `feature/settings/.../NegativeSpecs.kt`：桌面偏好页只保留一条入口声明 `negative.settings`，配 `SettingControl.Action`，跳转由 `DesktopPrefsScreen` 提供；保留 `applyNegativeModule`、`moduleEnabled` 与模块常量。
- `feature/settings/.../DesktopPrefsScreen.kt`：入口项渲染传入 `actions` 映射；图片组件分支内新增「轮播间隔」选择行，候选 `IMAGE_CAROUSEL_OPTIONS`，写 `desktop.imageCarouselSeconds`。
- `feature/settings/.../SettingsModule.kt`：分派表新增 `Destination.NegativeSettings -> NegativeSettingsScreen()`。
- `feature/home/.../HomeView.kt`：`DOWN` 时把按下点换算为组件局部坐标缓存；长按到点调 `widget?.onLongPress(widgetLocalX, widgetLocalY)`；抬起时声明了命中区域的组件经 `hitRegionAt(localX, localY)` 判定，命中或未声明区域才转发 `onClick(localX, localY)`，未声明区域组件保持整卡转发。

## 验证

```
:feature:settings:assemble :feature:home:assemble :feature:content:assemble
:feature:settings:testDebugUnitTest :feature:content:testDebugUnitTest
BUILD SUCCESSFUL
```

单测：`NegativeSpecsTest` 覆盖默认五模块、增删去重、展示顺序补入、开关写入、上移越界不变、模块名回退、入口声明唯一、分组顺序。

## 已知偏差与遗留

- 模块 label 使用字面量，未集中映射；Lead 已确认保留。
- 墨单十三项只提供编辑入口与写入路径，消费端渲染由组件侧读取；负一屏墨单卡片当前展示账单数据，不渲染排版偏好。
- 墨单外观分组中「块显示」用步进控件展示 0 至 7 与位含义文本，「视图形态」用两档步进；输入对话框用于标题与最短时长，均含校验。
- `weread_assets` 默认关闭，注册后仅在旧配置残留或手动开启时出现。

## task-10 第一段 负一屏接入墨台三模块

按 `docs\21-weread-native-adaptation.md` 第四节 4.2，负一屏模块标识收敛为微读原版 `PagePrefs` 常量，渲染改为内核 `DeskPage` 分区。

删除于 `feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/ui/negative/`：

- `NegativeCardModule.kt`（自写卡片基类）
- `WereadBillNegativeModule.kt`、`WereadRankNegativeModule.kt`、`WereadFingerprintNegativeModule.kt`（三个自写模块）
- `WereadAssetsNegativeModule.kt`（原版墨台无该模块，v9 迁移把旧 `weread_assets` 并入 `rank`，不丢弃用户数据）

新增于同目录：

- `NegativeModuleCardView.kt`：卡片外壳视图，标题行带详情入口与箭头，正文容器供模块填充，点击标题行或详情文字进入 `Destination.WereadTab(section)`。
- `BillNegativeModule.kt`（id `bill`，label 墨单，`defaultEnabled = true`，`orderHint = 30`，详情 `WereadSection.BILL`）
- `RankNegativeModule.kt`（id `rank`，label 阅读资产，`defaultEnabled = true`，`orderHint = 50`，详情 `WereadSection.STATS`）
- `FingerprintNegativeModule.kt`（id `fp`，label 阅读指纹，`defaultEnabled = false`，`orderHint = 60`，详情 `WereadSection.STATS`）

三个模块当前为外壳态：卡片标题、说明文字（内核墨台接入后由 `DeskPage` 绘制分区）与详情入口可用，正文留待第二段接内核。

其他改动：

- `ContentModule.kt`：注册 `bill`、`rank`、`fp` 三个模块，移除旧三个模块的注册与 import。
- `feature/settings/.../NegativeSettingsLogic.kt`：`LABELS` 与 `HINTS` 改为五个标识（bookshelf、negative_files、bill、rank、fp）；`DEFAULT_NEGATIVE_MODULES` 由 core/common 提供，顺序为 bookshelf、negative_files、bill、rank，`fp` 注册后以关闭状态追加。
- `feature/settings/src/test/.../NegativeSpecsTest.kt`：断言改为四项默认启用、`fp` 关闭，并新增「关闭的阅读指纹追加到展示顺序末尾」用例。
- `feature/content/src/test/.../ui/negative/NegativeModulesTest.kt`：三条模块的标识、显示名、默认启用状态与 `orderHint` 递增顺序。

验证：

```
:feature:settings:assemble :feature:home:assemble :feature:content:assemble
:feature:settings:testDebugUnitTest（64 用例） :feature:content:testDebugUnitTest
ktlintCheck detekt
BUILD SUCCESSFUL
```

## task-10 第二段 待 k2 jar

已确认契约（`docs\21` 第 77 至 98 行）：

- `DeskHost`：`filesDir()`、`scale()`、`requestRedraw()`、`openPage(String)`、`menuPrefs(String, String)`、`menuBool(String, boolean)`、`menuInt(String, int)`。
- `DeskPage`：常量 `SEC_BILL`、`SEC_ASSET`、`SEC_RANK`；构造 `DeskPage(DeskHost, DataSource)`；方法 `bind()`、`render(canvas, width, height, offset)`、`height(width)`、`hitTest(x, y)`、`onAction(int)`、`release()`。
- 菜单取值从 `LauncherConfig.integrations.wereadMenu` 读取，开关掩码从 `integrations.wereadDesk.effectiveOnMask` 读取，`scale = width / 368f`，钳制 0.6 至 2.0，`filesDir = context.filesDir/weread`。
- `libs` 下当前只有 `weread-kernel-1.3.2-k1.jar`（67810 字节），k1 无 `desk` 包；`feature/content/build.gradle.kts` 待换成 k2 引用。

## task-10 第二段 已完成（k2 内核接入）

三个墨台模块已从卡片外壳改为内核 `DeskPage` 分区渲染。

文件（`feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/ui/negative/`）：

- `WereadDeskHost.kt`：宿主对象，持有应用上下文、墨单选项读取器与内核实现。
- `WereadDeskHostImpl.kt`：`DeskHost` 十一个方法的实现。掩码 `DeskPrefs.bitOf(moduleId)`（bill=1、rank=2、fp=8），顺序只含当前模块；`filesDir = context.filesDir/weread`；`scale = max(displayMetrics.widthPixels, heightPixels) * 0.0015f`（与微读 InkTheme.UNIT_RATIO 一致，480×800 面板为 1.2）；`fpFontPx = 48`；`styleColor` 映射 INK、INK_2、PAPER、INK_4、INK_3；`openPage` 映射 bill 到账单、rank 与 assets 到排行、fp 到统计、settings 到 NegativeSettings；`requestRedraw` 转负一屏宿主。
- `WereadDeskMenu.kt`：DeskPrefs 键映射到 `integrations.wereadMenu` 的有效值，字符串选项仅认 cfg 分组。
- `WereadDeskSectionView.kt`：Canvas 视图，每个模块一个独立 `DeskPage`；`headerHeight = renderer.headerHeight(unit)`，高度为 `page.height(width) - headerHeight`；绘制时 `clipRect` 到分区高度并 `translate(0, -headerHeight)` 跳过墨台顶栏；点击 `hitTest(x, y + headerHeight)`，命中后 `onAction` 并请求重绘；模块销毁时 `page.release()`。
- `BillNegativeModule.kt`、`RankNegativeModule.kt`、`FingerprintNegativeModule.kt`：正文挂 `WereadDeskSectionView`，`onResume` 重载、`onDestroy` 释放。

验证：

```
:feature:content:compileDebugKotlin :feature:content:ktlintCheck :feature:content:detekt :feature:content:testDebugUnitTest
:feature:settings:assemble :feature:home:assemble :app:assembleDebug
BUILD SUCCESSFUL
```

真机（480×800、219dpi、SA4B03CN2608081096X）：负一屏依次显示书架、最近图片、墨单、阅读资产；阅读指纹默认关闭，与默认四项一致。墨单与阅读资产已进入内核分区渲染，因 `filesDir/weread` 无原盘数据，分区显示内核的「暂无内容」。截图存 `_analysis/verify/20261020-135848/negative-desk/`。

数据缺口：十雨侧只有 `KernelWereadReader` 读取 `filesDir/weread`，仓内无写入方；桌面 weread_board 有数据是 `KernelDataSourceImpl` 回退 ContentSource 的接口结果。

## task-10 第二段 补充：空态导入引导

原盘数据未落盘时，三个分区显示内核的空态文案。`WereadDeskSectionView` 增加引导区域：`onMeasure` 内按模块判断数据为空（墨单按 `DeskData.bill()?.items`，阅读资产按 `DeskData.assets()`，阅读指纹按 `DeskData.fpBooks()`），或整页高度不足以容纳分区时，隐藏分区绘制区域并显示「导入微读墨记数据」按钮与结果文案。按钮为真实 `TextView` 子视图，与空态判定同属 `WereadDeskSectionView`，不经过 `Canvas` 绘制，点击事件走视图层级而不是 `hitTest`。

按钮点击调用 `AppServices.wereadImport?.requestImport`：通道缺失时显示「当前无法发起导入」；回调文案显示在按钮下方，随后 `reload()`、`requestRedraw()`、`invalidate()`；通道返回未发起且没有回调时显示「已存在迁移数据，无需重复导入」。真机日志确认拉起 `com.inkread.weekread/.bridge.ExportActivity`，导出组件返回「缺少 target_uri」，说明 `HostWereadImport` 与微读墨记 1.3.2 导出组件的 extra 键约定不一致，该文件位于 app 层。

## 验证记录（第二段）

`:feature:content:compileDebugKotlin`、`:feature:content:ktlintCheck`、`:feature:content:detekt`、`:feature:content:testDebugUnitTest` 均 BUILD SUCCESSFUL；改动范围内超 120 列 0 行。真机截图存 `_analysis/verify/20261020-135848/negative-desk/`，其中 `08-negative-top.png` 为书架与最近图片，`09-import-guide.png` 为墨单与阅读资产空态及导入按钮，`13-button-import2.png` 为点击按钮后的回调文案。`uiautomator dump` 记录按钮 bounds 为 `[30,549][450,606]`，中心点击（130,577）正常发起导入。

## W2 长按提示条规格

参照微读墨记原版 `feature/DeskPageView.java:141-153` 与 `774-801` 的实测数据，负一屏分区内的长按提示条按下列参数自绘。

显示时长 `TIP_MS = 4000` 毫秒。黑底白字反白：填充色 `0xFF000000`，文字色 `0xFFFFFFFF`。几何以排版单位 `unit` 为基准，480×800 面板上 `unit` 为 1.2：内边距 `padding = 分区宽 × 0.05`；条高 `boxH = unit × 22`；底边 `bot = 分区高 − unit × 74`；矩形范围为 `[padding, bot − boxH, 分区宽 − padding, bot]`，横向跨分区宽减两侧内边距。

文字字号 `13 × unit`，超宽时以 `0.3` 为步长降到下限 `9 × unit`，仍超宽则截断并补省略号；文本左边距 `padding + unit × 4`，在矩形内垂直居中。

层级上提示条画在分区最上层，覆盖正文与录价键盘，不受分区内滚动影响；文本只显示一行，新提示替换旧提示并重置计时。

## W2 触摸与内滚约定

`onMeasure` 中 `padOpen()` 为真时不再另加键盘高度，`page.height(width)` 已包含键盘区域；开合键盘后调用 `requestLayout()`、`page.data().invalidateAll()` 与 `invalidate()`。

长按由 `longActionAt(x, y)` 直接执行三类动作，宿主按返回码 `LONG_BILL_PRICE`、`LONG_ASSET_PRICE`、`LONG_ASSET_TITLE` 刷新缓存与布局；返回 `LONG_ASSET_TITLE` 时以 `pendingTitle()` 取书名并显示上述提示条。

内滚与外层滚动的区分：进入内滚的 MOVE 与键盘开启时的 DOWN 调用 `requestDisallowInterceptTouchEvent(true)`，UP 与 CANCEL 复位为 `false`，导入按钮所在行不设置。

## W3 壁纸预览与选书（task-17）

壁纸生成与保存链路由三个新增文件组成。`WereadWallpaperStore.kt` 负责落盘与分享文件：保存走 `MediaStore`，`RELATIVE_PATH` 为 `Pictures/TenRain`，API 29 及以上用 `insert` 与 `IS_PENDING` 写入，API 26 至 28 退回直接写入外部存储的 `Pictures/TenRain` 并交 `MediaScannerConnection` 登记；分享前把位图写入 `cacheDir/share/`，再经 `FileProvider`（authority 为 `${applicationId}.files`，路径条目为 `app/src/main/res/xml/file_paths.xml` 中的 `files_share_cache`）授权读取。`WereadWallpaperDialog.kt` 提供全屏预览与底栏「保存到相册」「分享」「关闭」，位图为 1080×1920。`FingerprintBookDialog.kt` 提供标题「选择指纹书目」、搜索框与候选列表，选中后写指纹书目并重载分区。

入口与内核动作对应：`DeskPage.onAction(150)` 经 `host.openPage("wallpaper")` 到 `WereadDeskHostImpl` 的回调，再调 `renderWallpaper(section, 1080, 1920)` 取位图后弹层；`onAction(140)` 经 `host.openPage("fingerprint")` 打开选书对话框，选中后调 `setFingerprintBook`、`data().invalidateAll()` 与 `reload()`。壁纸入口同时挂在模块标题行的「壁纸」按钮上。

真机验证（2026-10-20 16:28 至 16:29，480×800、219dpi）：负一屏墨单标题行点「壁纸」弹出「墨台壁纸」，预览为墨单票据竖版图；点「保存到相册」后 `/sdcard/Pictures/TenRain/tenrain-desk-20261020-162855.png` 落盘；点「分享」弹出系统分享面板，候选含打印、蓝牙、ntfy。指纹选书对话框正确列出候选书目，点击后关闭并回到负一屏。截图存 `_analysis/verify/20261020-162500/negative-desk/`：`04-fp-picker-wait.png`、`05-fp-selected.png`、`11-wallpaper-dialog.png`、`12-wallpaper-saved.png`、`13-wallpaper-share.png`。

## W4 模块布局与交互收口（task-18）

三个模块标题行改为动作按钮：墨单与阅读资产为「壁纸」「设置」，阅读指纹为「壁纸」「选书」「设置」；原「详情」跳转保留在分区内点按，由内核动作分派。`NegativeModuleCardView.build` 的参数由单一详情目标改为动作列表，模块各自声明动作与回调。

分区高度按 `height(width)` 自适应，超过 `MAX_SECTION_HEIGHT_DP = 320` 的部分由分区内滚承担，`renderHeightPx()` 只覆盖视口与顶栏，位图缓存大小随视口回落。`DeskTouchDispatcher` 增加 `onScrollFinished()`，内滚结束后才刷新缓存位图，避免拖动期间生成大位图。

`:feature:content:ktlintCheck`、`:detekt`、`:testDebugUnitTest`、`:feature:content:assemble`、`:feature:home:assemble`、`:feature:settings:assemble`、`:app:assembleDebug` 全部通过。真机确认三个模块标题行动作渲染正确（`_analysis/verify/20261020-162500/negative-desk/02-modules.png`、`10-bill-header.png`）。

待复验：阅读资产分区的可见内滚位移。当前设备数据里 `assets.json` 尚未生成，分区显示「暂无数据」，长列表内滚需在 areal 完成同步验收、资产数据就绪后确认。

## W8 负一屏墨台化（task-24）

负一屏改为墨台列表态：`ContentModule` 只注册 `DeskNegativeModule`（id `desk`），`NegativeScreen` 检测到该模块后以其视图铺满页面，不再按卡片列表渲染。容器 `DeskContainerView` 由三单位高顶栏、ScrollView 分区列表、设置态、页内浮层组成；顶栏左侧「⚙ 设置」进设置态，中部衬线「墨台」，右侧「更新于 HH:mm」与「返回 ›」，返回与左滑退出调用路由返回桌面。顶栏高度取 `DeskSectionStyle.headerHeightPx`，与内核 `DeskStyle.secHeadH` 同式（17×unit×2.2），此前误用 `HEADER_UNITS` 常量导致顶栏被压成三像素，已修正。

分区顺序取 `desktop.negativePage.modules`：书架、最近图片、墨单、阅读资产、阅读指纹。书架与最近图片为宿主分区（`ShelfDeskSection`、`RecentDeskSection`），带抬头行与「刷新」动作；墨单、阅读资产、阅读指纹为内核分区（`DeskKernelSection` 内嵌 `WereadDeskSectionView` 与 `DeskPage`），抬头行右侧提供「壁纸」入口。全部分区之间以 `DeskStyle.DIVIDER_UNITS` 分隔线连接，无卡片外壳、圆角与间距。

设置态 `DeskSettingsView` 含三组：模块与顺序（五行开关与上移下移，内核三项按 `wereadDesk.onMask` 判定开关、写作时只改掩码且五项始终保留在顺序列表）、阅读资产过滤（只统计书架、只统计读完、恢复已移出书目）、阅读指纹（选择指纹书目、字号）。开关改动后重建分区并刷新顶栏时间。此前设置态把内核三项按顺序列表判断，导致阅读指纹默认显示为开，已改为按掩码判断。

负一屏内不再发生任何 Destination 跳转：`NegativeScreen.open()` 改为空操作并只请求重绘；书籍展开、图片预览走 `DeskOverlayView` 页内浮层；壁纸预览与指纹选书对话框在容器内完成；外部应用打开由浮层按钮单独启动。旧卡片模块 `BookshelfNegativeModule`、`BillNegativeModule`、`RankNegativeModule`、`FingerprintNegativeModule` 与 `NegativeModuleCardView` 已删除，书架页与详情页不再从负一屏进入。

验证：`:feature:content:ktlintCheck`、`:detekt`、`:testDebugUnitTest`、`:feature:content:assemble`、`:feature:home:assemble`、`:feature:settings:assemble`、`:app:assembleDebug` 全部 BUILD SUCCESSFUL；改动范围内超 120 列 0 行。真机（SA4B03CN2608081096X，480×800、219dpi）截图存 `_analysis/verify/20261020-185000/negative-desk/`：`28-desk.png` 与 `35-desk.png` 为正一屏顶栏与书架、最近图片、墨单分区，`29-settings.png` 为设置态（阅读指纹显示为关），`41-fp-toggle.png` 为切换后显示为开，`43-back-home.png` 为顶栏返回回到桌面，`42-fp-section.png` 为关闭设置态后的列表。设备配置已复原为 `onMask=3`、`assetFinishedOnly=false`、模块顺序五项，JSON 校验通过。

真机操作说明：该设备为电子墨水屏，右滑进入负一屏偶发被系统手势或侧边返回截获，需从屏幕内侧（x≈12）起滑并等待约六秒；截图哈希相同的次数较多，判断页面前应先核对 `dumpsys window` 的 `mCurrentFocus`。
