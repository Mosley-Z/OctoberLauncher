# 0.9.4 P2 浏览页与缩略图移植方案

本文件为 task-22 的只读前置分析。分析对象为墨盘 `GridPage.java`、`ListPage.java`、`BrowseActivity.java` 三个文件与 P1 已交付的 `feature:files` 模块；本轮不修改工程源码。文中的行号取自墨盘仓库 `D:\DSHStation\MoPan\inkdisk\app\src\main\java\com\inkread\inkdisk` 与当前工作区源码。

## 一、总体判定

P2 的工作可以把墨盘三个 Activity 时代的页面组装方式拆成两层：一层是新的 `FilesScreen`，承担原 `BrowseActivity` 的宿主职责；另一层是 `BrowsePage` 与 `GridBrowsePage`、`ListBrowsePage` 三个自绘类，承担原 `GridPage`、`ListPage` 的绘制与手势。P1 的 `FilePager` 已经提供分页、翻页、点击与长按骨架，P2 只在顶栏区、内容绘制与浮层三处扩展，重写 pager 的代价可以避免。

三份源码合计 2,366 行，其中直接可映射 1,180 行左右，需要改写 620 行左右，删除约 560 行（厂商壁纸通道、准备页跳转、自选文件夹页、Activity 生命周期样板）。

## 二、P1 可复用接口核对

| P1 接口 | 位置 | P2 使用方式 |
| --- | --- | --- |
| `FileItem` | `feature/files/.../data/FileItem.kt:15-101` | 直接复用，绘制只读字段 `name/path/dir/size/mtime/mime` 与 `isImage` |
| `FileListing` | `.../data/FileListing.kt:17-213` | `list(path)` 载入目录、`lastError` 区分错误态与空态、`nameOf(parentOf)` 生成标题与上级 |
| `FilePrefs` | `.../data/FilePrefs.kt` | `grid/showHidden/sortKey/sortAscending/lastPath` 直接读写 |
| `FileSortKey` | `.../data/FileSortKey.kt:33-52` | 面板排序切换写入 `FilePrefs`，排序动作由 `FileListing` 完成 |
| `StorageAccess` | `.../data/StorageAccess.kt` | `hasAccess()/externalRoot()/isReadable()` 决定起始目录与权限空态 |
| `FileTokens` | `.../ui/FileTokens.kt:15-80` | 全部尺寸与字号来源，P2 不再出现 dp/sp 字面量 |
| `FileMetrics` | `.../ui/FileMetrics.kt:12-54` | 内容区、行高、单元格几何；P2 增加按高度压缩的单元格高度 |
| `FilePager` | `.../ui/FilePager.kt:148-293` | 分页、翻页、点击长按分发、底栏绘制；P2 增加顶栏动作按钮与浮层钩子 |
| `FilePaging/FilePagerState/Hit` | `FilePager.kt:18-146` | 页码换算、越界收敛、矩形命中直接复用 |
| `ThumbCacheRegistry/ThumbCache` | `core/render/.../ThumbCacheRegistry.kt`、`ThumbCache.kt` | 网格与列表各取一个目标长边档位，`get/put/warmUp` 接入 |
| `BitmapPipe` | `core/render/.../BitmapPipe.kt:19-28` | `decodeFile(file, edge, edge)` 作为 `warmUp` 的解码函数 |

P1 预留的占位类 `FilesScreen`（61 行）与测试文件 `FilePagerTest.kt`、`FileMetricsTest.kt` 需要随 P2 修订。

## 三、逐文件移植映射

### 3.1 GridPage.java（814 行）→ GridBrowsePage.kt + BrowsePage.kt + FilePager.kt 增量

| 墨盘位置与方法 | Kotlin 目标 | 处理 |
| --- | --- | --- |
| 字段 `items/title/path/error`（102-111） | `BrowsePage` 同名属性 | 直译 |
| `Callbacks`（33-71） | `BrowseCallbacks`（`BrowsePage.kt`） | 删除 `wallpaperAssistState/onToggleWallpaperAssist`；`onManageFolders` 保留签名，P2 不挂入口 |
| 面板常量（72-131） | `BrowsePanel` 行模型 | 八行减为七行，删除壁纸全自动行；选中态改为行模型字段 |
| 画笔（122-131） | `FileTokens` 与 `InkColors` | 删除本地 Paint 构造，改用 `InkColors.PAPER/INK/INK_4` 与 `DesignRuntime` 画笔 |
| `setData/setError/isPanelOpen/closePanel`（179-219） | `BrowsePage.setData/setError`、`isPanelOpen/closePanel` | 直译 |
| `itemCount/pageSize`（222-232） | `GridBrowsePage.itemCount/pageSize` | `pageSize` 取 `FileMetrics.pageSize`（16） |
| `onDrawTopBar`（234-250） | `FilePager.pageTitle`、`pageSubtitle` 与 `drawTopAction` | 标题与路径两行改由 pager 顶栏统一绘制，右侧按钮改由 pager 提供命中 |
| `onDrawContent`（254-269） | `GridBrowsePage.onDrawContent` | 保留四列布局，行高改用 `FileMetrics` 压缩后的 `cellH` |
| `onDrawOverlay`（273-283） | `BrowsePage.drawOverlay` | 面板命中块每次绘制重建 |
| `drawActionPanel`（292-315） | `BrowsePage.drawPanel` | 七行，行高取 `tokens.rowH` |
| `panelRowCount/isPanelRowSelected`（325-359） | `BrowsePanel.rows(prefs)` | 行选择态由 `FilePrefs` 计算 |
| `drawPressedPanel`（368-396） | `BrowsePage.drawPressedPanel` | 保留打开、详情两行；详情行内容用 `FileListing.formatSize/formatTime` |
| `openItem`（405-418） | `FilesScreen.openItem` | 目录进入下级；图片在 P2 提示查看器待接入；普通文件无动作 |
| `onTapContent`（422-458） | `FilePager.onContentGesture` + `BrowsePage.handleTap` | 面板优先、顶栏按钮、条目三级顺序保持 |
| `onLongPressContent`（462-475） | `BrowsePage.handleLongPress` | 记录选中条目并展开两项面板 |
| `actionLabel/onAction`（479-498） | `FilePager.actionLabel/onAction` | 文案取新资源「菜单」 |
| `topButtonRect/toggleView`（501-513） | `FilePager` 新增 `topActionRect/topActionLabel/onTopAction` | pager 统一处理，网格显示「列表」、列表显示「网格」 |
| `gridIndexAt`（520-550） | `GridBrowsePage.indexAt` | 以内容区 `area.top` 为基准，间距不计入命中 |
| `drawCell/drawFolder/drawCropped`（551-609） | `GridBrowsePage.drawCell/drawFolderIcon/drawCropped` | 缩略图改查 `ThumbCache.get`，空结果绘制占位框 |
| `drawEmpty`（610-633） | `BrowsePage.drawEmpty` | 空态与错误态两行居中 |
| `metaOf`（634-646） | `BrowsePage.metaOf` | 复用 `FileListing` 格式化函数 |
| `drawRight/ellipsize`（647-679） | `BrowsePage.drawRight/ellipsize` | 直译，画笔取自 `FileTokens` 字号 |
| `panelLabel/panelState`（680-743） | `BrowsePanel` 行文案与状态 | 文案迁入 `res/values/strings_files.xml` |
| `sortByOf/handlePanelRow`（757-808） | `BrowsePanel.onRow` | 写偏好后回调 `onPrefsChanged` |
| `prefsChanged`（809-814） | `BrowseCallbacks.onPrefsChanged` | 由 `FilesScreen` 重载当前目录并保持起始条目 |
| 壁纸全自动相关（337-339、744-756） | 删除 | D4 决策不接厂商通道 |

### 3.2 ListPage.java（819 行）→ ListBrowsePage.kt + BrowsePage.kt

| 墨盘位置与方法 | Kotlin 目标 | 处理 |
| --- | --- | --- |
| `Callbacks`（37-75） | 共用 `BrowseCallbacks` | 同 3.1 |
| 面板与画笔（83-147） | 共用 `BrowsePanel` 与令牌 | 不重复实现 |
| `setData/setError/isPanelOpen/closePanel`（196-242） | `BrowsePage` 共用 | 直译 |
| `itemCount/pageSize`（245-255） | `ListBrowsePage` | `pageSize` 取 `FileMetrics.listRows` |
| `onDrawTopBar`（257-275） | `FilePager` 顶栏 | 与网格一致，按钮文案为「网格」 |
| `onDrawContent`（279-288） | `ListBrowsePage.onDrawContent` | 按 `area.top + i * rowH` 逐行绘制 |
| `drawRow`（560-589） | `ListBrowsePage.drawRow` | 缩略图框取 `tokens.rowThumb`，图片按等比缩放居中，目录绘制文件夹图形 |
| `rowIndexAt`（548-559） | `ListBrowsePage.indexAt` | 行高判定，包含整行命中 |
| 剩余方法与 3.1 同名项 | 共用 | 面板、点击、长按、动作按钮逻辑合并到 `BrowsePage` |

### 3.3 BrowseActivity.java（733 行）→ FilesScreen.kt

| 墨盘位置与方法 | Kotlin 目标 | 处理 |
| --- | --- | --- |
| `onCreate`（78-91） | `FilesScreen.createView` | 权限判断改为页面内空态，移除 `SetupActivity` 跳转 |
| `resolveStartPath`（101-111） | `FilesScreen.resolveStartPath` | 路由路径 → `FilePrefs.lastPath` → `StorageAccess.externalRoot()`，逐级校验可读 |
| `load`（120-139） | `FilesScreen.load` | 单实例 pager 复用，注入条目、标题、错误并设置页码 |
| `prefetchThumbs`（149-161） | `FilesScreen.prefetch` | 后台协程 `warmUp`，完成后主线程 `invalidate` |
| `targetPxOf`（172-177） | `FilePager.thumbEdge` | 网格取 `min(cellW, cellH)`，列表取 `tokens.rowThumb` |
| `invalidateDirtyThumbs/consumeDirtyThumbs`（184-236） | P2 删除，P3 写路径调用 `ThumbCacheRegistry.invalidate` | 缓存键已含修改时间，写入后自然失效 |
| `buildPage`（239-248） | `FilesScreen.buildPager` | 按 `FilePrefs.grid` 创建网格或列表页，回调统一为 `BrowseCallbacks` |
| `applyData/applyError`（251-266） | pager 的 `setData/setError` | 直译 |
| `pageSizeOf`（269-274） | `FilePager.pageSize` | 由选中子类给出 |
| `isPanelOpen/closePanel`（277-294） | `BrowsePage.isPanelOpen/closePanel` | 直译 |
| `onEnterDir`（298-309） | `FilesScreen.openItem` | `load(item.path, 0, true)` |
| `onOpenImage`（312-337） | `FilesScreen.onOpenImage` | P2 提示查看器下一阶段接入；P3 改为 `Destination.PhotoViewer` |
| `onToggleView`（339-345） | `FilesScreen.toggleView` | 写 `FilePrefs.grid` 后重建 pager 并保持当前起始条目 |
| `onPrefsChanged`（347-358） | `FilesScreen.reloadKeepingIndex` | 排序或隐藏文件变化后重新载入 |
| `onManageFolders/onFolderPickDone`（360-388） | P3 `FolderPickPage` | P2 面板不提供入口 |
| 壁纸全自动回调（390-424） | 删除 | D4 决策 |
| `onBack/goUp`（426-440） | `FilesScreen.onBack/goUp` | 面板开优先关闭，否则上级目录，卷根返回桌面 |
| `onBackPressed`（443-461） | `Screen.onBack` | 由宿主统一分发 |
| `onKeyDown`（463-482） | `VolumeKeySink.onVolumeKey` | 音量键翻页仅由宿主开关控制 |
| `onResume/onPause`（484-505） | `FilesScreen.onResume/onPause` | 恢复当前页；暂停时写 `lastPath` |
| `FolderPickPage`（506-732） | P3 独立文件 | P2 不移植 |

### 3.4 其余墨盘文件

`PagerView.java` 由 P1 `FilePager` 覆盖，P2 只补顶栏按钮与浮层绘制顺序；`Metrics.java` 由 `FileTokens` 与 `FileMetrics` 覆盖；`Ink.java`、`Hit.java` 由 `InkColors`、`InkCanvas`、`FilePager.Hit` 覆盖；`FileItem`、`Sort`、`Prefs` 已由 P1 对应类覆盖。四个文件不进入 P2 移植清单。

## 四、需要改写而非直译的部分

| 改写点 | 原因 |
| --- | --- |
| Activity 壳体改为 `Screen` | 宿主统一管理页面栈、返回、状态栏与刷新，Activity 的 `setContentView` 与 `startActivity` 自跳转不再适用 |
| 每个目录重建 PagerView 改为单实例复用 | 减少视图创建与首次绘制抖动，页大小变化由 `onSizeChanged` 重建几何 |
| 顶栏绘制与视图切换按钮上移到 `FilePager` | 两种页面只差按钮文案与内容绘制，避免两份重复实现 |
| 面板行模型化 | 八行改七行后行文案、状态、选中判定与动作需要单一来源，同时便于 JVM 测试 |
| 缩略图由 `Thumbs.peek` 改为 `ThumbCache.get` | 0.9.3 统一两级缓存，键含修改时间与目标长边，磁盘目录由渲染层管理 |
| 权限准备页改为页面内空态 | 桌面不新增独立 Activity，权限引导走 `Permissions.openStorageSettings()` |
| 自选文件夹页与壁纸全自动行删除 | 分属 P3 与 D4 决策范围 |
| 480×800 的 `dp(44)` 等散落常量改为令牌 | 令牌按 `DeviceProfile` 派生，三档设备与字号缩放统一生效 |
| 四行网格按内容区高度压缩单元 | 真机内容区高度小于整屏，S4 上四行按原比例合计 648 像素，超出内容区 605 像素 |
| `getString(R.string.*)` 改为新资源文件 | 模块独立命名空间，避免与桌面资源冲突 |

## 五、三档几何与 FileMetrics 对应关系

### 5.1 字段对应

| 墨盘 `Metrics` 字段 | P1 来源 | 说明 |
| --- | --- | --- |
| `screenW/screenH` | `FileMetrics.widthPx/heightPx` | 取宿主内容区实测宽高，不含状态栏与导航栏 |
| `density` | `FileTokens.density` | 来自 `DeviceProfile` |
| `stroke` | `FileTokens.stroke` | 桌面取 `max(1, dp1.2)`，不再按密度取整 |
| `padH/topBarH/bottomBarH/rowH/rowThumbDp/gap/cellLabelH/slop/touchMin` | `FileTokens` 同名属性 | 数值一致 |
| `titlePx/bodyPx/smallPx` | `titleText 17sp/bodyText 14sp/smallText 12sp` | 决策文档统一字号，行数与信息密度按新字号重算 |
| `contentTop/contentBottom/contentH` | `FileMetrics` 同名属性 | 内容区坐标系原点为顶栏底边 |
| `listRows` | `FileMetrics.listRows` | 内容区高度整除行高 |
| `cellW/cellH/cellTotalH` | `FileMetrics` 同名属性 | `cellH` 在 P2 增加按高度压缩规则 |
| `applyBottomInset` | 删除 | 导航栏由宿主处理，页面不再自行扣除 |

### 5.2 S4 实算

设备 480×800、219 dpi、密度 1.36875，状态栏 47 像素（34dp），宿主内容区 480×753；顶栏 77、底栏 71、内容区 605 像素。

| 项目 | 数值 |
| --- | --- |
| 列表行高与行数 | 60 像素，10 行（600 ≤ 605） |
| 列表缩略图 | 41 像素 |
| 网格左右留白、间距 | 25、8 像素 |
| 单元格宽度 | 101 像素 |
| 单元标签高度 | 22 像素 |
| 四行按 3:4 合计 | 4×156 + 3×8 = 648 像素，超出 43 像素 |
| 压缩后单元格高度 | 123 像素（比例约 0.82） |
| 压缩后四行合计 | 4×145 + 3×8 = 604 像素 |
| 网格每页 | 16 项，四列四行 |

压扁而非减少行数的理由：`docs/16-墨盘移植决策.md` 第二十八行明确网格固定 4 列 4 行、每页 16 项；P1 `FileMetricsTest.kt:62` 也把「四行合计超过内容区时由 P2 缩放或裁剪」写为预期。压缩只作用于缩略图高度，标签带宽不变，缩略图按居中裁剪绘制，不拉伸原图。

### 5.3 其他两档

下表按宿主内容区高度推算（状态栏取 34dp，与令牌一致），与 P1 单测直接注入整屏高度的用例不同：

| 设备 | 内容区高度 | 行高 | 列表行数 | 单元宽 | 3:4 高度 | 压缩后高度 |
| --- | --- | --- | --- | --- | --- | --- |
| 552×768、213 dpi | 579 | 59 | 9 | 120 | 160 | 117 |
| 824×1648、320 dpi | 1364 | 88 | 15 | 179 | 238 | 238（无需压缩） |

P1 单测以整屏高度注入 `FileMetrics`（480×800 时 `contentH=652`、552×768 时 `contentH=624`），真机内容区分别少一个状态栏高度；P2 需要增加一例以宿主内容区高度为输入的用例，避免几何只在整屏假设下成立。552×768 用例的 `cellH/cellTotalH/gridContentH` 期望值需要按压缩规则更新。

## 六、缩略图接入

绘制阶段按条目路径调用 `ThumbCache.get(File(path))`：内存命中直接绘制，磁盘命中读取 PNG，全部未命中返回空并由页面绘制灰度占位框。目标长边取值与墨盘一致：网格取压缩后单元宽与单元高的较小者（S4 为 101），列表取行内缩略图边长（S4 为 41）。两个档位分别对应 `ThumbCacheRegistry.get(context, edge)` 的两个实例，视图切换后两个实例并存。

预取在 `FilesScreen` 的 IO 协程中执行：`cache.warmUp(paths) { file -> BitmapPipe.decodeFile(file, edge, edge) }`，完成后主线程请求重绘。`warmUp` 以 `@Synchronized` 串行执行，同一时刻只保留一个预热任务；目录切换时旧任务允许自然结束，重绘请求不区分代际，重复绘制无害。缓存写入在 `put` 内完成四档抖动量化，与 0.9.3 的抖动接入一致。

## 七、分页与选择态交互约定

翻页由底栏上一页、下一页按钮、上下滑超过 48dp 与音量键三处触发；翻页后关闭浮层，页码显示为「当前页/总页数」。点击目录进入下级并回到第一页；点击图片在 P2 提示查看器下一阶段接入；点击普通文件无动作。长按超过 350 毫秒进入选择态：该条目以浅灰底色高亮，底部展开「打开」「详情」两行面板；点击面板外关闭面板，再次长按其他条目切换选中对象。选择态为单条目，多选与批量操作留给 P3 文件操作任务。顶栏右侧按钮在对侧视图间切换并写入 `FilePrefs.grid`，切换后按原起始条目的绝对下标换算页码。底栏「菜单」按钮展开七行设置面板：排序依据四行、升降序、显示隐藏文件、视图；选择排序后重载当前目录并保持起始条目。返回键先关闭面板，其次返回上级目录，卷根处返回桌面。进入新目录重置页码；视图切换与排序变化保留当前起始条目；`FilePrefs.lastPath` 在暂停时写入，下次进入无路由参数时回到该目录。

## 八、与 WidgetApi 和 RefreshScheduler 的衔接

P2 不引用 `WidgetApi`。图片组件的选图入口按 0.9.4 任务 4.4 改为模块内选择器，届时复用 P3 的选择页；P2 保持 `FilePicker` 契约与 `HostFilePicker` 现状不变。`RefreshScheduler` 在浏览页只承担写入后通知：P3 的文件操作与编辑在完成后调用 `RefreshScheduler.notifyContentChanged()` 并对目标文件调用 `ThumbCacheRegistry.invalidate`；P2 的浏览流程为只读，不参与全局刷新调度，重绘仅调用视图自身 `invalidate`。目录内容被外部应用修改时，下次进入或 `onResume` 重新列举即可反映。

## 九、拆分方案与行数预估

`FilePager.kt` 当前 558 行，P2 需增加顶栏按钮、路径副标题与浮层顺序，按单文件不超过 600 行的约束拆为四个文件：

| 目标文件 | 内容 | 预估行数 |
| --- | --- | --- |
| `ui/FilePaging.kt` | `FilePaging`、`FilePagerState`、`Hit` | 130 |
| `ui/FilePagerChrome.kt` | `PagerChrome`，含顶栏动作按钮与副标题 | 190 |
| `ui/FilePagerTouch.kt` | `PagerTouch` | 150 |
| `ui/FilePager.kt` | 视图、顶栏与内容钩子、翻页入口 | 210 |
| `ui/BrowsePage.kt` | 数据、回调、面板模型与绘制、文本工具 | 330 |
| `ui/GridBrowsePage.kt` | 网格内容、命中、缩略图 | 250 |
| `ui/ListBrowsePage.kt` | 列表内容、命中、缩略图 | 220 |
| `ui/FilesScreen.kt` | Screen 实现、权限空态、载入、预取、返回与音量键 | 280 |
| `ui/FileMetrics.kt` | 增加压缩后单元格高度与网格高度校验 | 增加 20 |
| 合计 Kotlin | 其中迁移既有 558 行，净新增约 1,000 行 | 1,780 |
| `res/values/strings_files.xml` | 约 40 条文案 | 80 |

P2 新增测试：`BrowsePanelTest`（行模型七行、选中态、排序写回映射）、`FileMetricsTest` 增补宿主内容区高度与压缩规则、`FilePagingTest` 保留；`FilesScreen` 的手势与预取不做 JVM 测试，由真机截图核对列表、网格、面板三种状态。

## 十、需要确认的事项

1. S4 上网格压缩单元格高度（101×123，比例约 0.82）与保持 3:4 三行 12 项之间的取舍。按 `docs/16-墨盘移植决策.md` 采用前者。
2. `FilePager` 在 P2 内拆分为四个文件属于结构性重构，改动会触及 P1 测试的导入路径。
3. 长按选择态首版按单条目处理，批量操作留待 P3。
4. 点击图片在 P2 期间的降级行为采用提示文案，查看器接入后替换。
5. 排序面板首版隐藏「自选文件夹」行，P3 与 `FolderPickPage` 一并接入。
6. `ThumbCache.get` 在内存未命中时会同步读取磁盘 PNG，首帧绘制存在 IO。若渲染团队可提供仅内存查询入口，P2 改为「内存命中绘制、未命中占位」，否则接受首帧一次磁盘读取。
