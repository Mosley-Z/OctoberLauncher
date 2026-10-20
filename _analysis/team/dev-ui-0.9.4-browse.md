# 0.9.4 P2 浏览页与缩略图实施报告

## 一、任务与范围

task-22 要求把墨盘三个 Java 文件（`GridPage.java` 814 行、`ListPage.java` 819 行、`BrowseActivity.java` 733 行，合计 2366 行）移植为 `feature:files` 的 Kotlin 浏览页，覆盖三档几何、网格 4×4 与列表 10 行、分页翻页、缩略图接入 `ThumbCacheRegistry`、排序与筛选、长按选择态与位置恢复。

写作用域为 `feature/files/src/main/java/com/shuiyu/octoberrain/feature/files/ui/**`、`feature/files/src/test/**`、`_analysis/team`。数据层八个文件由 dev-config 交付，本次未修改；`ui/viewer/**` 与编辑相关文件由 dev-core 并行开发，本次未触碰。

## 二、文件结构

原 `ui/` 目录仅有占位 `FilesScreen.kt`（61 行）。本次建立浏览页完整结构，新增与改写如下。

| 文件 | 行数 | 职责 |
| --- | --- | --- |
| `FilePaging.kt` | 112 | 分页纯逻辑：`FilePaging`、`FilePagerState`、`Hit`，从旧 `FilePager.kt` 迁出 |
| `FilePagerChrome.kt` | 171 | 顶栏与底栏绘制、命中矩形、返回图标、标题与副标题省略 |
| `FilePagerTouch.kt` | 127 | 触摸分发：350ms 长按、48dp 纵向翻页、按钮优先 |
| `FilePager.kt` | 172 | 分页容器基类，页面尺寸、当前页、钩子与重绘 |
| `BrowseStrings.kt` | 64 | 界面文案常量 |
| `BrowsePanel.kt` | 133 | 排序面板纯逻辑：状态、八行定义、选中判定、行命中 |
| `PaneDraw.kt` | 109 | 缩略图与文字的居中、等比、裁剪绘制工具 |
| `BrowsePane.kt` | 448 | 浏览面板基类：数据、面板、长按选择态、点击分发、预取接线 |
| `GridPane.kt` | 183 | 网格形态绘制与逐格命中 |
| `ListPane.kt` | 172 | 列表形态绘制与逐行命中 |
| `ThumbPrefetcher.kt` | 140 | 当前页与邻页缩略图预取、内存查询、失效 |
| `BrowseScreen.kt` | 264 | `Screen` 宿主：权限、加载、回调、按键、位置恢复 |
| `FilesScreen.kt` | 37 | 委托 `BrowseScreen` 的薄适配层 |
| `FileText.kt` | 22 | 大小与时间格式化 |
| `FileMetrics.kt` | 58 | 新增网格绘制高度派生值（原文件基础上追加） |
| `FileTokens.kt` | 60 | 新增 `gapLarge` 字段（原文件基础上追加） |

拆分标准为单文件不超过 600 行，实际最大文件 448 行。

## 三、墨盘源码到 Kotlin 的映射

### 3.1 GridPage.java

| 墨盘成员 | Kotlin 目标 | 处理方式 |
| --- | --- | --- |
| 类字段 `items/title/path/error/panelOpen/panelHits/pressedItem` | `BrowsePane` 字段 | 保留语义，命中矩形改由 `PaneDraw` 与面板纯函数计算 |
| `Callbacks` 接口 | `BrowsePane.Callbacks` | 合并 Grid 与 List 的同构接口，增加 `onPageChanged` |
| `ASSIST_STATE_*` 与壁纸回调 | 删除 | 按 docs/16 第 7 节移除厂商壁纸通道 |
| `setData/setError/isPanelOpen/closePanel` | 同名方法 | 直接移植 |
| `onDrawTopBar` | `FilePagerChrome.drawTitle` + `FilePager` 的 `pageTitle/pageSubtitle/topActionLabel` | 两行标题与右侧动作统一到基类 |
| `onDrawContent` 四列布局 | `GridPane.onDrawContent` | 保持 `padH + col*(cellW+gap)` 与行距，行高改用 `gridCellDrawH` |
| `drawActionPanel` 八行面板 | `BrowsePane.drawPanel` + `BrowsePanel` | 面板数据与绘制分离，行定义用 `PanelRow` 枚举 |
| `drawPressedPanel` 两行面板 | `BrowsePane.drawPressedPanel` | 保留打开与详情，详情右半区两行小字 |
| `onTapContent` 分发顺序 | `BrowsePane.handleTap` | 面板行、面板外关闭、顶栏动作、条目命中 |
| `onLongPressContent` | `BrowsePane.handleLongPress` | 设置 `pressedItem` 与 `pressedPanelOpen`，保持单条选择态 |
| `handlePanelRow` 与偏好写入 | `BrowsePane.applyPanelRow` + 回调 `onPrefsChanged` | 排序键、升降序、隐藏文件、视图切换逐项写入 `FilePrefs` |
| `gridIndexAt` | `GridPane.indexAt` | 按绘制高度与标签区反算，越界返回 -1 |
| `drawCell/drawFolder/drawCropped/drawEmpty` | `GridPane.drawCell` + `PaneDraw` | 目录改用 `InkGlyphs.Kind.FOLDER`，图片按目标框居中裁剪 |
| `metaOf/ellipsize/drawRight` | `FileText` + `PaneDraw.ellipsize` | 大小与时间格式化抽为纯函数，便于测试 |
| `toggleView` | `BrowsePane.onTopAction` + `BrowseScreen.onToggleView` | 顶栏按钮文案随形态切换 |

### 3.2 ListPage.java

与 `GridPage.java` 同构，映射到 `ListPane.kt`。差异点如下。

行高由 `FileTokens.rowH` 给出，每页行数取 `FileMetrics.listRows`（S4 为 10）。缩略图框为 30dp，图片等比内接居中，非图片只画外框。文件名与大小时间分两行排布，状态文字由 `PaneDraw` 绘制。`rowIndexAt` 按 `contentTop + index*rowH` 反算。面板行与绘制复用 `BrowsePane`，不再复制两份实现。

### 3.3 BrowseActivity.java

| 墨盘成员 | Kotlin 目标 | 处理方式 |
| --- | --- | --- |
| `onCreate` 权限检查 | `BrowseScreen.createView` + `showPermissionView` | 无权限时绘制说明与设置入口，不再跳转独立 Activity |
| `resolveStartPath` | `BrowseScreen.resolveStartPath` | 意图路径、`lastPath`、外部存储根依次回退，并校验可读 |
| `load(path,firstIndex,resetPage)` | `BrowseScreen.load` | 列目录、失效脏缩略图、重建面板、写入数据与错误、恢复页首下标 |
| `prefetchThumbs/targetPxOf` | `BrowsePane.onGeometryChanged` + `ThumbPrefetcher` | 目标长边由 `GridPane/ListPane.thumbEdge` 给出 |
| `invalidateDirtyThumbs/consumeDirtyThumbs` | `BrowseScreen.onResume` 重载与 `ThumbCacheRegistry.invalidate` | 编辑后失效入口保留，刷新计数简化为整页重绘 |
| `buildPage` | `BrowseScreen.buildPane` | 按 `FilePrefs.grid` 创建网格或列表面板 |
| `onEnterDir` | 回调同名 | 进入目录并重置分页 |
| `onOpenImage` | `onOpenItem` | 查看器由 P3 接入，当前提示「图片查看将在下一阶段接入」 |
| `onToggleView/onPrefsChanged` | 回调同名 | 保留当前页首下标后重载 |
| `onManageFolders` | 回调同名 | 自选文件夹页留待 P3，当前提示占位 |
| `goUp/onBackPressed` | `BrowseScreen.onBack` | 依次关闭长按面板、关闭排序面板、返回上级、退出页面 |
| `onKeyDown` 音量键 | `BrowseScreen.onVolumeKey` | 上下键翻页 |
| `onResume/onPause` | 同名 | 恢复时重载并请求局部刷新；暂停时写入 `lastPath` |
| `FolderPickPage` | 暂缓 | 属于 P3 范围，不纳入本阶段拆分 |

## 四、三档几何

`FileTokens` 以 `InkTokens` 为源，`FileMetrics` 以宿主内容区实测宽高计算。三档设备的状态栏与宿主内容区高度由 `MainActivity` 的 `contentFrame` 决定，实机验证 S4 内容区为 753px。

| 设备 | 内容区高 | contentH | 行高 | 列表行数 | 单元宽 | 名义单元高 | 网格绘制高 | 页数 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 480×800@219 | 753 | 605 | 60 | 10 | 101 | 134 | 123 | 16/页 |
| 552×768@213 | 723 | 579 | 59 | 9 | 120 | 160 | 117 | 16/页 |
| 824×1648@320 | 1580 | 1432 | 88 | 16 | 179 | 238 | 238 | 16/页 |

`FileMetrics.gridCellDrawH` 的算式为：

```kotlin
val gridCellDrawH: Int =
    maxOf(1, minOf(cellH, (contentH - GRID_ROWS * tokens.cellLabelH - (GRID_ROWS - 1) * tokens.gap) / GRID_ROWS))
```

S4 与 552×768 的单元格在保持四行的前提下略低于 3:4，标签区完整可见；824×1648 无需压缩。`gridContentH` 保留名义值，既有 `FileMetricsTest` 不受影响。`gridDrawH` 供绘制与 `indexAt` 共用，避免绘制与命中不一致。

字号取标题 17sp、正文 14sp、辅助 12sp，与 docs/16 第 4 节一致。

## 五、缩略图接入

绘制阶段只查内存：`BrowsePane.bitmapOf` 调用 `ThumbPrefetcher.bitmapOf`，后者经 `ThumbCacheRegistry.peekMemory(path, targetEdge)` 查询已创建实例的内存缓存，未命中时绘制占位，不读磁盘。

后台预取由 `ThumbPrefetcher.request` 按当前页、上一页、下一页顺序执行，协程调度到 `Dispatchers.IO`，先 `ThumbCache.get` 读磁盘缓存，未命中再 `ImageDecoder.decode(path, targetEdge)` 并 `ThumbCache.put` 写入。写入时按默认四档灰抖动。当前页处理完毕后回调 `onReady`，由面板整页重绘一次，避免逐张刷新。

目标长边：网格取 `min(cellW, gridCellDrawH)`，S4 为 101；列表取 `tokens.rowThumb`，S4 为 41。缓存实例按目标长边与抖动开关由 `ThumbCacheRegistry` 共享，模块内不新建实例。

文件写入、删除、旋转后调用 `BrowseScreen.notifyFileChanged(path)`，内部执行 `pane.invalidateThumb(path)`、`ThumbCacheRegistry.invalidate` 与 `RefreshScheduler.notifyContentChanged()`。翻页时通过 `AppServices.refresh.requestPartial()` 请求局部刷新。

## 六、交互约定

翻页有三种入口：底栏上一页与下一页、内容区纵向滑动超过 48dp、音量键。翻页统一走 `FilePager.turnPage`，状态集中在 `FilePagerState`。

长按 350ms 进入单条选择态：条目底色改为 `INK_4`，并弹出两行面板（打开、详情）。点击面板行执行对应动作；点击面板外关闭面板；再次长按其他条目切换选中项。批量选择与多选操作留待 P3 与 P4，本阶段按单条选择实现，已在报告第九节列为与任务描述的差异。

顶栏左侧返回进入上级目录，右侧动作切换网格与列表；底栏右侧动作打开排序面板。排序面板八行依次为按名称、按时间、按大小、按类型、升降序、隐藏文件、视图、自选文件夹。前四行与升降序、隐藏文件、视图修改后写 `FilePrefs` 并重载当前目录，保留页首下标。自选文件夹行显示 P3 占位提示。

返回键顺序为：关闭长按面板、关闭排序面板、返回上级目录、退出文件页。页面暂停时写入 `lastPath`，再次进入时恢复到上次目录与页首下标。

视图切换与排序重载均保留 `firstIndex`，避免回到第一页。

## 七、验证

### 7.1 构建与单测

命令：

```powershell
$env:JAVA_HOME='D:\DSHStation\.toolchain\jdk\jdk17.0.20_12'
$env:ANDROID_HOME='D:\DSHStation\.toolchain\android-sdk'
$env:GRADLE_USER_HOME='D:\DSHStation\.toolchain\gradle-home'
gradle.bat --offline :feature:files:testDebugUnitTest :app:assembleDebug check --console=plain
```

结果：`BUILD SUCCESSFUL`。`:feature:files:testDebugUnitTest` 通过，模块共 16 个测试套件、130 例、0 失败；其中本次新增 20 例：

| 测试类 | 例数 | 覆盖内容 |
| --- | --- | --- |
| `P2MetricsTest` | 5 | 三档设备网格绘制高度、四行可容纳、压缩规则 |
| `ThumbPrefetchPlanTest` | 8 | 邻页计划、页边界、越界下标过滤 |
| `BrowsePanelTest` | 4 | 面板行枚举、选中判定、状态文案、坐标命中 |
| `FileTextTest` | 3 | 大小与时间格式化边界 |

既有 `FileMetricsTest`、`FilePagerTest`、`FileItemTest`、`FileSortTest` 全部保持通过。

`:app:assembleDebug` 通过，产物 `app/build/outputs/apk/debug/app-debug.apk` 为 3,824,171 字节。`feature:files` 的 detekt 报告为 0 条，ktlint 主源集与测试源集均为 0 条。全量 `check` 通过，其余模块的 lint 与单测同时通过。

### 7.2 真机走查

设备 `SA4B03CN2608081096X`（S4，480×800，density 219），安装调试包后进入文件页。

- 列表形态：标题「内部存储」、路径 `/storage/emulated/0`、每页 10 行、底栏上一页、页码、下一页、菜单，顶栏右侧「网格」，截图 `shots/p2-files-list.png`。
- 网格形态：4 列 4 行每页 16 项，标签完整，底栏 1/2，截图 `shots/p2-grid3.png`；第二页含 13 项，截图 `shots/p2-grid4.png`。
- 目录进入：进入 `Pictures` 后图片显示四档灰缩略图，截图 `shots/p2-pics2.png`。
- 缩略图基准：`/sdcard/Pictures/thumbs-bench` 共 501 项、32 页，全部显示缩略图，EXIF Orientation 样张方向正确，截图 `shots/p2-bench.png`。
- 长按选择态：条目高亮并弹出「打开」「详情」两行面板，截图 `shots/p2-longpress.png`。
- 排序面板：八行完整显示，当前排序「按名称」带「已选」标记，升降序「升序」、隐藏文件「关」、视图「网格」，截图 `shots/p2-menu2.png`。
- 排序生效：切换「按时间」后图片顺序变化，`files_prefs.xml` 写入 `sort_by=1`、`view_grid=true`、`last_path`，截图 `shots/p2-sort.png`。
- 翻页与音量键：底栏翻页与上下滑均可用；音量键翻页通过 `VolumeKeySink` 接线，未单独截图。
- 隐藏文件开关：关闭时仅显示 `visible.txt`；打开后 `.hidden.txt` 出现并按名称排在前面，`files_prefs.xml` 写入 `show_hidden=true`，截图 `shots/p2-hidden-off.png`、`shots/p2-hidden-on.png`。
- 批量翻页：在 500 张基准目录的网格形态下，以 500ms 间隔连续翻 20 页，页码由 `1/32` 变为 `21/32`，条目顺序正确、无错位，截图 `shots/p2-bench-before.png`、`shots/p2-bench-after.png`。含 adb 输入注入与固定间隔的墙钟总耗时为 40.37 秒，翻页后的稳定页（`22/32`、`23/32`）缩略图完整，截图 `shots/p2-bench-page22.png`、`shots/p2-bench-page23.png`。
- 内存观测：翻页前应用 `TOTAL PSS` 为 54.8MB（Java 堆 8.1MB、Native 堆 19.5MB），20 页后为 57.7MB（Java 堆 7.6MB、Native 堆 23.7MB），23 页后为 58.3MB（Java 堆 8.1MB、Native 堆 23.7MB），随页数增加趋于平稳，未见持续增长。
- 缩略图就绪时间：冷启动进入新页时 16 张图片约 8 秒内整页重绘完成，等待期间绘制「图片」占位；就绪后一次整页刷新，符合 `ThumbPrefetcher` 中「当前页全部就绪后回调一次」的节流设计。快速翻页时上一页请求被取消，停止翻页后末页完成预取并重绘。
- 最终包复验：修复后重新构建并安装，进入文件页网格形态，四行完整、页码 1/2，截图 `shots/p2-final.png`。走查结束后删除 `files_prefs.xml`、恢复 `hiddenSpecials` 中隐藏的 `special:files`，并清理 `/sdcard` 下的临时截图。

### 7.3 走查中发现并修复的问题

顶栏动作与底栏动作在匿名 `FilePagerTouch.Handler` 中调用同名方法时，触发对自身接口方法的递归调用，导致 `StackOverflowError`，应用在点击「网格」时崩溃。修正为 `this@FilePager.onTopAction()` 与 `this@FilePager.onAction()`（`FilePager.kt:67`、`:93`）。该问题在编译期与 JVM 单测中无法发现，真机走查后确认修复。随后重新安装并完成上述全部走查项。

## 八、遗留问题

1. 查看器与自选文件夹属于 P3，本阶段图片点击与详情、自选文件夹行均以提示文案占位。
2. 长按为单条选择态，未实现多选；任务描述中的「选择态」按单条高亮与两行面板实现，批量操作留给 P3 与 P4。
3. `ThumbCache.get` 在绘制线程之外的预取线程读取磁盘；绘制线程使用 `peekMemory`，不触发磁盘读取。
4. 编辑后的缩略图失效依赖 `notifyFileChanged` 被编辑页调用，编辑页由 dev-core 并行开发，接线点在 P4 验收时确认。
5. 552×768 与 824×1648 仅按几何公式核对，未在真机截图；当前设备为 480×800。
6. `FilePager` 由单文件拆为四个文件，属于本阶段内部重构，公开类名与方法保持不变，`FilePagerTest` 未改动。
7. 快速连续翻页时，被跳过页面的预取任务随页码变化取消，末页在停止翻页后约数秒才整页重绘；当前采用「整页就绪后一次刷新」，若后续要求翻页即显，可改为按页增量刷新，需先评估墨水屏残影。

## 九、待裁决事项

「长按进入选择态」的定义。当前实现为单条选中高亮加两行面板，与墨盘原行为一致；若需要多选与批量操作，则需在 P3 或 P4 增加选择集合、批量菜单与全选逻辑，工作量约 1 至 1.5 人日。

## 十、核对记录

- 墨盘源码行数：`GridPage.java` 814、`ListPage.java` 819、`BrowseActivity.java` 733，合计 2366 行。
- Kotlin 新增与改写合计约 1900 行，其中可复用 P1 的分页容器、几何、偏好、排序与数据读取约 700 行。
- 本任务未修改数据层、`ui/viewer`、编辑与相册相关文件。
