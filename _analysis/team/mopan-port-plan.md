# 墨盘 26 个 Java 文件移植清单与 0.9.4 实施计划

本报告对应 task-19「0.9.4 前置：墨盘 26 个 Java 文件移植清单」，分析对象为
`D:\DSHStation\MoPan\inkdisk\app\src\main\java\com\inkread\inkdisk` 下的全部 Java 源文件。
分析阶段只读源码与文档，未修改 OctoberLauncher 工程源码，也未触碰 `core/render`、`core/data`
（task-18 由 dev-core 独占）与 `feature/settings`。

## 一、总体判定

### 1.1 最小批次

最小可独立验收的移植批次为 **P1 数据与容器 + P2 浏览与缩略图**，合计 4,048 行 Java
（8,320 行中扣除非目标模块），折算约 3,200 行 Kotlin，单人 16 至 19 人日。
该批次完成后可交付：目录浏览、图片缩略图网格、列表/网格切换、排序与隐藏文件偏好、
分页、图片打开入口（首版可回退系统查看器）。该批次不依赖编辑与相册，适合作为 0.9.4 的
第一个可安装里程碑。

查看器（P3）是图片能力的必要组成，与浏览批次合计约 6,723 行 Java、27 至 32 人日，
构成 0.9.4 的完整交付。编辑与相册属于 0.9.5，合计 2,600 至 3,100 行新代码，
需要额外 12 至 14 人日。

### 1.2 最大风险点

按影响程度排序，前三项均超出单个文件移植的范围：

1. **编辑与相册的缺口**：26 个文件中只有 `Edits.java`（245 行，仅旋转另存）与编辑、相册相关。
   裁剪、五档黑白滤镜、编辑输出、撤销栈、MediaStore 索引与时间轴相册合计 2,600 至 3,100 行
   属于新写代码，依赖 `core/render` 的灰度与抖动能力，工期与实现质量的不确定性最高。
2. **导出组件与权限边界**：浏览页与查看器可作为 Screen 自绘，但 `ViewerActivity`（外部
   `ACTION_VIEW`）与 `PickActivity`（`GET_CONTENT`/`OPEN_DOCUMENT`）必须保留为可导出组件。
   桌面工程现有 `androidx.core.content.FileProvider` 已占用 `${applicationId}.files`
   authority，与墨盘 `OutProvider` 的分享 authority 存在冲突，分享方案需要先定。
3. **尺寸与排版迁移**：墨盘全部几何基于整屏 `480×800` 与固定 Metrics，桌面 Screen 位于
   宿主内容区内（状态栏之下），可用高度约少 38 px；两端字号体系差异明显（正文 11sp 对 14sp）。
   按字面换算会同时改变每页行数与信息密度，需要按 DeviceProfile 重算并以三档真机对照。

## 二、源码盘点

### 2.1 规模与分布

| 目录 | 文件数 | 行数 | 主要职责 |
| --- | --- | --- | --- |
| `core/` | 12 | 3,011 | 文件模型、路径、排序、偏好、文件操作、缩略图、解码、编辑旋转、壁纸、分享 Provider |
| `shell/` | 6 | 2,427 | 四个 Activity、权限引导视图、无障碍自动壁纸服务 |
| `ui/` | 8 | 3,882 | 分页容器、网格页、列表页、查看器、选图页、几何尺、命中区、绘制基元 |
| 合计 | 26 | 9,320 | 其中注释与空行占比约两成，有效语句约 7,300 行 |

### 2.2 Android 平台依赖

| 依赖 | 出现位置 | 移植处置 |
| --- | --- | --- |
| `android.media.ExifInterface` | `core/ImageDecode.java`，InputStream 构造在 API 24 起可用，`ImageDecode.java:304` 有低版本回退 | 已由 `core/render/ImageDecoder` 覆盖，0.9.3 核对其方向与尺寸接口 |
| `android.util.Base64` | `core/OutProvider.java`，`URL_SAFE \| NO_WRAP` | 分享方案确定后随 Provider 一并处置 |
| `ContentProvider`、`ParcelFileDescriptor`、`MatrixCursor`、`OpenableColumns` | `core/OutProvider.java` | 建议合并进现有 FileProvider，删除该类 |
| `SharedPreferences` | `core/Prefs.java` | 改用模块内 SharedPreferences，或并入 LauncherConfig |
| `android.util.LruCache` | `core/Thumbs.java` | 已由 `core/render/ThumbCache` 覆盖 |
| `AccessibilityService`、`AccessibilityNodeInfo`、`AccessibilityManager` | `core/Wallpaper.java`、`shell/AssistService.java` | 按 D4 不移植 |
| `WindowInsets.getSystemWindowInsetBottom` | `ui/PagerView.java:110-112` | 改为 View 内边距与窗口嵌入分发，宿主内容区已扣除系统栏 |

未出现 `MediaStore`、`RecyclerView`、`Fragment`、`ViewModel`、`AndroidX` 依赖，
26 个文件整体是自绘 View 加 Activity 的形态，向 Screen 契约迁移的阻碍集中在 Activity 入口，
不在绘制代码。

## 三、逐文件对照表

批次代号：P0 前置（0.9.3 `core:render` 已有能力，仅接入）；P1 数据与容器；P2 浏览与缩略图；
P3 查看与选图；P4 编辑（0.9.5）；D 不移植。

| 序号 | 源文件 | 行数 | 目标文件（`feature/files` 或 `core/render`） | 批次 | 处置要点 |
| --- | --- | --- | --- | --- | --- |
| 1 | `core/Edits.java` | 245 | `edit/Edits.kt` | P4 | 保留 `rotate90` 与 `edited_rotations:<path>` 键；输出名 `_edited` 后缀规则进入 `EditExport` |
| 2 | `core/FileItem.java` | 72 | `data/FileItem.kt` | P1 | 数据类直译；`dir/file/isImage/isEditableImage/extension/baseName` 保留 |
| 3 | `core/ImageDecode.java` | 454 | `core/render/ImageDecoder.kt`（已存在） | P0 | 不重复移植；核对 `size/decode/mimeOf/sizeOf/overLimit` 与 EXIF 方向 |
| 4 | `core/Intents.java` | 56 | `data/FileIntents.kt` | P1 | `EXTRA_PATH` 改 `com.shuiyu.octoberrain.extra.FILE_PATH`；静态交接槽改为路由参数 |
| 5 | `core/Ops.java` | 326 | `data/FileOps.kt` | P1 | `sameContent/uniqueName/copy/move` 直译，缓冲 8 KiB 与后缀上限 999 保留 |
| 6 | `core/OutProvider.java` | 271 | `data/FileShareProvider.kt` 或删除 | P3 | authority 冲突，见 6.3；建议合并 FileProvider |
| 7 | `core/PathModel.java` | 341 | `data/FileListing.kt` | P1 | 目录列举、图片过滤、父路径、名称与 MIME、大小与时间格式化 |
| 8 | `core/Prefs.java` | 168 | `data/FilesPrefs.kt` | P1 | 视图、隐藏文件、排序键与方向、最后路径四项偏好 |
| 9 | `core/Sort.java` | 77 | `data/FileSort.kt` | P1 | 常量与比较器改为枚举与 `Comparator` |
| 10 | `core/Storage.java` | 142 | `data/StorageAccess.kt` | P1 | 委托 `PermissionCenter` 与 `ContentPaths`，保留卷枚举与可读判断 |
| 11 | `core/Thumbs.java` | 401 | `core/render/ThumbCache.kt`（已存在）+ `ui/ThumbPrefetcher.kt` | P2 | 缓存层复用；预取、脏路径队列在模块内实现 |
| 12 | `core/Wallpaper.java` | 458 | 不移植 | D | 依 D4，壁纸由桌面自绘；厂商通道与锁屏壁纸另立任务 |
| 13 | `shell/AssistService.java` | 545 | 不移植 | D | 依赖无障碍与厂商应用，随 D4 删除相关配置 |
| 14 | `shell/BrowseActivity.java` | 733 | `ui/BrowseScreen.kt` + `ui/BrowseHost.kt` | P2 | 宿主职责改为 Screen；页面装配、缩略图预取、返回键与音量翻页保留 |
| 15 | `shell/PickActivity.java` | 395 | `ui/PickEntryActivity.kt` | P3 | 保留可导出 Activity；返回 content Uri |
| 16 | `shell/SetupActivity.java` | 126 | 不移植 | D | 权限引导并入现有 Onboarding 与权限页 |
| 17 | `shell/SetupView.java` | 273 | 不移植 | D | 同上；壁纸全自动开关随 D4 取消 |
| 18 | `shell/ViewerActivity.java` | 355 | `ui/PhotoScreen.kt` + `ui/PhotoEntryActivity.kt` | P3 | 进程内查看改为 Screen 路由；外部 `ACTION_VIEW` 保留薄 Activity |
| 19 | `ui/GridPage.java` | 814 | `ui/GridPane.kt` | P2 | 4 列 4 行、每页 16 项、动作面板 8 行、长按面板 2 行 |
| 20 | `ui/Hit.java` | 37 | `ui/Hit.kt` | P1 | 命中矩形工具，可内联到 FilePager |
| 21 | `ui/ImageView.java` | 1,380 | `ui/PhotoViewer.kt` | P3 | 三档缩放、邻图预取、动作面板、详情五行 |
| 22 | `ui/Ink.java` | 95 | 删除 | D | 颜色与绘制改用 `DesignRuntime` 与 `InkCanvas` |
| 23 | `ui/ListPage.java` | 819 | `ui/ListPane.kt` | P2 | 行高与每页行数由 FileMetrics 计算 |
| 24 | `ui/Metrics.java` | 145 | `ui/FileMetrics.kt` + `ui/FileTokens.kt` | P1 | 由 DeviceProfile 与令牌派生，见第五节 |
| 25 | `ui/PagerView.java` | 318 | `ui/FilePager.kt` | P1 | 底栏三命中区、长按 350 ms、滑动 48 dp、页码与动作按钮 |
| 26 | `ui/PickPage.java` | 274 | `ui/PickPane.kt` | P3 | 上一级、取消、目录优先排序 |

### 3.1 逐文件职责、判定依据与依赖明细

上表给出批次与目标，本表补齐判定理由、内部依赖、平台差异与行数估算。`minSdk` 差异一栏
只记录墨盘 23 与工程 26 之间的影响；除 `ImageDecode` 的 API 24 回退分支外，其余文件在
minSdk 26 下不存在需要兼容的更低版本分支。

| 序号 | 文件 | 职责 | 判定与理由 | 依赖墨盘内部类 | Android API 与 minSdk 差异 | 移植/改写行数 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `Edits.java` | 图片旋转 90° 并另存，记录编辑标记 | 保留：编辑功能基础，`core/render` 无旋转另存 | `ImageDecode`、`Prefs` | 无差异 | 200 / 45 |
| 2 | `FileItem.java` | 目录项模型与图片判定 | 保留：作为列表与网格数据单元 | 无 | 无差异 | 72 / 0 |
| 3 | `ImageDecode.java` | 解码、采样、EXIF 方向、尺寸与超限判断 | 替代：`ImageDecoder/ImageSizing` 已覆盖，避免两套解码 | 无 | API 24 的 ExifInterface 流构造回退可删除 | 0 / 适配 80 |
| 4 | `Intents.java` | 查看器路径集合的进程内交接槽 | 改写：交接槽改为 `Destination.PhotoViewer` 参数 | 无 | 无差异 | 30 / 26 |
| 5 | `Ops.java` | 同内容判断、重名生成、复制、移动 | 保留：文件操作语义与桌面无重合 | `PathModel`、`Intents` | 无差异 | 300 / 26 |
| 6 | `OutProvider.java` | 路径到 content Uri 的只读 Provider | 改写：与现有 FileProvider authority 冲突，按方案 A 合并 | 无 | `ContentProvider`、`ParcelFileDescriptor`，API 1 起可用 | 120 / 151 |
| 7 | `PathModel.java` | 目录列举、图片过滤、格式化、最后错误 | 保留：浏览页数据基础 | `FileItem` | 无差异 | 300 / 41 |
| 8 | `Prefs.java` | 视图、隐藏文件、排序、最后路径偏好 | 改写：接入模块偏好或 `LauncherConfig`，见 6.4 | 无 | `SharedPreferences` | 140 / 28 |
| 9 | `Sort.java` | 排序键与比较器 | 保留：枚举化后无行为变化 | `FileItem`、`Prefs` | 无差异 | 77 / 0 |
| 10 | `Storage.java` | 外部存储根、卷枚举、可读判断、权限入口 | 改写：委托 `PermissionCenter` 与 `ContentPaths` | 无 | `Environment`、`StatFs`，API 18 起 | 90 / 52 |
| 11 | `Thumbs.java` | 缩略图生成、LRU 缓存、脏路径刷新 | 替代+改写：缓存由 `ThumbCache` 承接，模块只写预取器 | `PathModel`、`ImageDecode` | `LruCache`，API 12 起 | 0 / 新增 120 |
| 12 | `Wallpaper.java` | 厂商壁纸导出与自动设置 | 放弃：D4 改为桌面自绘，不接厂商通道 | `ImageDecode`、`Prefs` | 依赖厂商应用与锁屏接口 | 0 / 0 |
| 13 | `AssistService.java` | 无障碍代替点击完成壁纸设置 | 放弃：随 D4；现有无障碍服务职责不同 | `Wallpaper` | `AccessibilityService` | 0 / 0 |
| 14 | `BrowseActivity.java` | 浏览宿主：装配页面、预取、返回、音量翻页 | 改写：宿主职责并入 Screen，页面装配逻辑保留 | `Metrics/PathModel/Prefs/Thumbs/Intents` | `Activity`、`KeyEvent` | 650 / 83 |
| 15 | `PickActivity.java` | 外部选图入口，返回 content Uri | 保留：仅改为薄 Activity 与模块 Uri 生成 | `PathModel`、`OutProvider` | `GET_CONTENT`/`OPEN_DOCUMENT` | 300 / 95 |
| 16 | `SetupActivity.java` | 权限引导与壁纸全自动入口 | 放弃：并入现有 Onboarding 与权限页 | `Storage`、`Wallpaper` | `MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`（API 30） | 0 / 0 |
| 17 | `SetupView.java` | 权限引导自绘界面 | 放弃：同上；壁纸开关随 D4 删除 | `Ink`、`Metrics`、`Wallpaper` | 无 | 0 / 0 |
| 18 | `ViewerActivity.java` | 进程内查看交接与外部 Uri 临时复制 | 改写：内部改 Screen，外部保留薄 Activity | `Intents`、`ImageView` | `ACTION_VIEW`、`ContentResolver` | 250 / 105 |
| 19 | `GridPage.java` | 网格分页、缩略图、动作面板、长按菜单 | 保留：4 列 4 行与面板语义完整迁移 | `PagerView/Metrics/Thumbs/Ink/Wallpaper` | 无差异 | 780 / 34 |
| 20 | `Hit.java` | 命中矩形与坐标工具 | 保留：可内联，减少一个文件 | 无 | 无差异 | 37 / 0 |
| 21 | `ImageView.java` | 查看器绘制、缩放、预取、面板 | 保留：交互语义是图片能力主体 | `PagerView/Metrics/Thumbs/ImageDecode/Intents` | 无差异 | 1350 / 30 |
| 22 | `Ink.java` | 颜色与绘制基元 | 放弃：改用 `DesignRuntime`、`InkCanvas` | `Metrics` | 无差异 | 0 / 0 |
| 23 | `ListPage.java` | 列表分页、行布局、动作面板 | 保留：与网格页并列的两种视图 | `PagerView/Metrics/Thumbs/Ink` | 无差异 | 785 / 34 |
| 24 | `Metrics.java` | 一次算出全部版面像素 | 改写：由 `DeviceProfile` 与 `FileTokens` 派生 | 无 | `DisplayMetrics` | 120 / 25 |
| 25 | `PagerView.java` | 顶栏、底栏、翻页、长按、点击分发 | 保留：分页骨架与手势规则 | `Metrics/Ink` | `WindowInsets`（API 21 起） | 300 / 18 |
| 26 | `PickPage.java` | 模块内选图分页界面 | 保留：可选内置选择器 | `PagerView/Metrics/PathModel` | 无差异 | 250 / 24 |

移植与改写合计：移植 6,151 行、改写 817 行，合计 6,968 行；`ImageDecode` 与 `Thumbs`
共 855 行由 `core/render` 承接，另加适配 80 行与预取器 120 行；放弃 1,497 行。

各批次行数合计：

| 批次 | 文件数 | 行数 | 说明 |
| --- | --- | --- | --- |
| P0 | 2 | 855 | 0.9.3 已实现，0.9.4 只做接入与接口核对 |
| P1 | 10 | 1,682 | 数据层与分页容器 |
| P2 | 3 | 2,366 | 浏览页、网格页、列表页（缩略图预取另计） |
| P3 | 5 | 2,675 | 查看器、外部入口、选图页、分享 Provider |
| P4 | 1 | 245 | 旋转另存；裁剪与滤镜另写 |
| D | 5 | 1,497 | 壁纸、无障碍、权限引导页、绘制基元 |
| 合计 | 26 | 9,320 | 需要移植 6,968 行 |

## 四、依赖批次与四阶段工作量

### 4.1 依赖关系

```
P0 解码与缩略图缓存（0.9.3，core:render）
  └─ P1 数据与容器
       ├─ P2 浏览与缩略图
       │    └─ P3 查看与选图
       │         └─ P4 编辑（0.9.5 5.1）
       └─ 相册索引（0.9.5 5.2，可与 P3 并行）
```

P1 不依赖浏览页面即可先行实施，`FileMetrics`、`FilePager` 可先写纯 JVM 几何测试；
P2 依赖 P1 的数据模型与 `core/render/ThumbCache`；P3 依赖 P2 提供的有序路径集合；
P4 依赖 P3 的查看器动作入口与 `Ditherer`；相册只依赖 `FileListing` 与 MediaStore，
与 P4 并行不会争用文件。

### 4.2 四阶段安排

| 阶段 | 内容 | 对应 docs/15 | 源文件规模 | 移植 / 改写 | 新增编写 | 人日 | 验收 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| S1 前置与数据 | 4.1 模块骨架；`Destination` 增加图片与相册；P0 接入；P1 全部 | 0.9.3 3.1-3.3；0.9.4 4.1 | 2,537（其中 P0 855） | 1,466 / 296 | 200（预取器 120、解码适配 80） | 10 至 12 | `:feature:files` 可独立编译；几何纯 JVM 用例覆盖三档；分页容器单测 |
| S2 浏览 | `BrowseScreen`、`GridPane`、`ListPane`、缩略图预取、目录选中与自选文件夹 | 0.9.4 4.2 | 2,366 | 2,215 / 151 | 120 | 9 至 11 | 真机浏览、缩略图、排序、隐藏文件、翻页、返回上级通过 |
| S3 查看与选图 | `PhotoScreen`、`PhotoViewer`、外部 `ACTION_VIEW`、`PickEntryActivity`、分享 | 0.9.4 4.3-4.4 | 2,675 | 2,270 / 405 | 100（外部入口胶合） | 11 至 13 | 三档缩放、邻图预取、分享、旋转另存、选图返回 Uri 通过 |
| S4 编辑与相册 | 裁剪、五档滤镜、编辑输出、撤销；MediaIndex、AlbumScreen | 0.9.5 5.1-5.2 | 245 | 200 / 45 | 2,550 至 3,720 | 12 至 14 | 编辑输出命名与目录正确；万张级目录索引与时间轴滚动通过 |
| 合计 | — | — | 9,320 | 6,151 / 897 | 2,970 至 4,140 | 42 至 50 | 含单测、三档真机与评审修复余量 |

估算依据：单人有效产出约 200 至 250 行 Java/人日（含测试与真机验证），新写代码按 120 至
180 行/人日计。S1 中的 P0 已由 0.9.3 完成，接入与接口核对按 2 至 3 人日计入。

### 4.3 与现有模块的协作边界

- `core/render`：`ImageDecoder`、`ImageSizing`、`ThumbCache`、`Ditherer`、`InkCanvas`
  已具备，task-18 由 dev-core 修改，本模块只调用，不改实现。
- `feature/content`：现有 `FileLibrary.kt`（458 行）与 `FilesScreen.kt` 承担文件页与
  书籍/图片分类。移植后 `Destination.Files` 的提供者归属需要裁决，见第八节第一条。
- `app`：`MainActivity`、`FilePicker` 宿主、`AndroidManifest.xml`（Provider、Activity 声明）
  由 dev-core 维护，本任务只提出改动清单。
- `feature/settings`：权限引导入口由 review-settings 维护，Setup 页面不新增。

## 五、写死 480×800 的几何换算清单

### 5.1 原尺字段与目标来源

`ui/Metrics.java` 以 `DisplayMetrics.widthPixels/heightPixels` 为基准，构造时一次性算出全部
像素值。移植后基准改为**宿主内容区 View 的宽高**，密度取自 `DeviceProfile.density`。

| 原字段 | 原值（dp/sp） | 来源行 | 目标来源 | 备注 |
| --- | --- | --- | --- | --- |
| `screenW/screenH` | 显示宽高 | 79-80 | `View.onSizeChanged` | 内容区高度比整屏少状态栏高度 |
| `density` | `dm.density` | 81 | `DeviceProfile.density` | S4 为 1.36875 |
| `stroke` | `max(1, round(density))` | 84 | 独立规则 | S4 得到 1 px，桌面 `tokens.stroke` 为 2 px，不能按同名替换 |
| `padH` | 18 dp | 85 | `FileTokens.padH` 或 `pageGutter` | 桌面页边距为 14/20 dp |
| `topBarH` | 56 dp | 86 | `FileTokens.topBarH` 或 `topBarHeight` | 桌面顶栏 40/46 dp |
| `bottomBarH` | 52 dp | 87 | `FileTokens.bottomBarH` | 桌面无对应项 |
| `rowH` | 44 dp | 88 | `FileTokens.rowH` 或 `listRowHeight` | 桌面行高 40/46 dp |
| `rowThumbDp` | 30 dp | 89 | `FileTokens.rowThumb` | 列表行缩略图 |
| `gap` | 6 dp | 90 | `FileTokens.gap` 或 `InkTokens.gap` | 桌面 8/10 dp |
| `cellLabelH` | 16 dp | 91 | `FileTokens.cellLabelH` 或 `labelHeight` | 桌面 22/24 dp |
| `slop` | 12 dp | 92 | `FileTokens.slop` | 触摸容差 |
| `touchMin` | 44 dp | 93 | `FileTokens.touchMin` | 最小命中区 |
| `titlePx` | 15 sp | 95 | `textHeading`（17sp）或独立 | 标题 |
| `bodyPx` | 11 sp | 96 | `textBody`（14sp）或独立 | 正文差 3 sp |
| `smallPx` | 9 sp | 97 | `textCaption`（12sp）或独立 | 辅助文字 |
| `contentTop` | `topBarH` | 99 | `viewHeight` 计算 | 自绘顶栏占位 |
| `contentBottom` | `screenH - bottomBarH` | 101 | `viewHeight - bottomBarH - inset` | 宿主已处理导航栏 |
| `contentH` | 差 | 102 | 差 | — |
| `listRows` | `contentH / rowH` | 103 | 同公式 | 结果可能减少 1 行 |
| `cellW` | `(screenW - 2*padH - 3*gap) / 4` | 105 | 同公式，4 列固定 | — |
| `cellH` | `cellW * 4 / 3` | 106 | 同公式 | 3:4 缩略图 |
| `cellTotalH` | `cellH + cellLabelH` | 107 | 同公式 | — |
| `GRID_COLS/ROWS/PAGE` | 4/4/16 | 15-19 | 固定 4/4/16 | 824 宽可考虑 5 列，见第八节 |

### 5.2 三档设备实算值

三档取值：S4 为 480×800、219 dpi、密度 1.36875、字体缩放 1.0（真机读取确认）；
552×768 按 213 dpi、密度 1.33125；824×1648 按 xhdpi、密度 2.0。

| 字段 | S4 480×800@219 | 552×768@213 | 824×1648@320 |
| --- | --- | --- | --- |
| `padH` | 25 px | 24 px | 36 px |
| `topBarH` | 77 px | 75 px | 112 px |
| `bottomBarH` | 71 px | 69 px | 104 px |
| `rowH` | 60 px | 59 px | 88 px |
| `rowThumb` | 41 px | 40 px | 60 px |
| `gap` | 8 px | 8 px | 12 px |
| `cellLabelH` | 22 px | 21 px | 32 px |
| `slop` | 16 px | 16 px | 24 px |
| `touchMin` | 60 px | 59 px | 88 px |
| `title/body/small` | 20.5/15.1/12.3 px | 20.0/14.6/12.0 px | 30/22/18 px |
| `contentTop` | 77 px | 75 px | 112 px |
| `contentBottom`（整屏） | 729 px | 699 px | 1,544 px |
| `contentH`（整屏） | 652 px | 624 px | 1,432 px |
| `listRows` | 10 | 10 | 16 |
| `cellW` | 101 px | 120 px | 179 px |
| `cellH` | 134 px | 160 px | 238 px |
| `cellTotalH` | 156 px | 181 px | 270 px |

以上行数与墨盘在 S4 上的实际观感一致（10 行列表、4×4 网格）。宿主内实际可用高度需再减
状态栏：S4 状态栏 `tokens.statusBarHeight` 为 28 dp（38 px），内容区高度约 762 px，
`contentH` 约 614 px，`listRows` 仍为 10；552×768 状态栏 34 dp（45 px），内容区 723 px，
`listRows` 仍为 10。分页行数不变，但末行余量变小，需要真机截图核对底栏与最后一行间距。

### 5.3 其余写死尺寸与常量

| 位置 | 数值 | 含义 | 目标处理 |
| --- | --- | --- | --- |
| `ui/PagerView.java:76-77` | 84 dp / 72 dp | 上一页与下一页宽 / 动作按钮宽 | 保留为 `FileTokens.navWidth/actionWidth` |
| `ui/PagerView.java:264` | 350 ms | 长按出面板 | 保留 `FilePager.longPressMs` |
| `ui/PagerView.java:305` | 48 dp | 上下滑翻页阈值 | 保留 |
| `shell/BrowseActivity.java:509,511` | 44 dp / 48 dp | 列表行高 / 标记缩进 | 前者改用 `FileTokens.rowH`，后者保留 |
| `ui/GridPage.java:293,369` | 44 dp | 动作面板行高 | 保留 |
| `ui/GridPage.java:502` | 60 dp | 面板缩略图宽 | 保留 |
| `ui/GridPage.java:620` | 22 dp | 面板行文字留白 | 保留 |
| `ui/ImageView.java:238-240` | 64 dp | 返回与页码区宽 | 保留 |
| `ui/ImageView.java:323` | 44 dp | 缩放档位按钮半宽 | 保留 |
| `ui/ImageView.java:527` | 48 dp | 左右滑换图阈值 | 保留 |
| `ui/ImageView.java:1070` | 44 dp | 详情面板行高 | 保留 |
| `ui/PickPage.java:78` | `max(56 dp, touchMin)` | 上一级按钮宽 | 保留 |
| `ui/PagerView.java:110-112` | 系统底部 inset | 安全区 | 改为 View 内边距；宿主已扣导航栏 |
| `core/ImageDecode.java:304` | API 24 | ExifInterface 流构造分支 | 已由 ImageDecoder 覆盖 |
| `shell/ViewerActivity.java:45` | 64 MiB | 外部 Uri 临时复制上限 | 保留 `PhotoEntryActivity` |
| `ui/ImageView.java:61,64` | 2 张 / 15000 ms | 邻图缓存与自动设置超时 | 缓存保留，超时随 D4 删除 |

长边与像素上限统一使用 `core/render`：`ImageSizing.MAX_EDGE = 3200`、
`MAX_TARGET = 1600`、`LIMIT_PIXELS = 80_000_000L`、`BYTES_PER_PIXEL = 2`，
墨盘侧 1600/2400/1024 的长边常量不再重复定义；预览 1024 由调用方传入目标边。

## 六、Activity 与 Manifest 转换

### 6.1 Activity 到 Screen 的对应

| 原组件 | 目标形态 | 路由 | 说明 |
| --- | --- | --- | --- |
| `BrowseActivity` | `BrowseScreen : Screen` | `Destination.Files(path)` | 无导出，完全由宿主栈托管 |
| `ViewerActivity`（进程内） | `PhotoScreen : Screen` | 新增 `Destination.PhotoViewer(paths, index)` | 静态交接槽改为路由参数 |
| `ViewerActivity`（外部 `ACTION_VIEW`） | `PhotoEntryActivity`（薄 Activity） | 无 | 解析 Uri、复制到 cache、承载 `PhotoViewer`，返回键退出 |
| `PickActivity` | `PickEntryActivity`（薄 Activity） | 无 | 保留 `GET_CONTENT`/`OPEN_DOCUMENT` 过滤器与结果回传 |
| `PickPage`（模块内选择） | `PickPane`，可托管为 Screen 或嵌入 Activity | `Destination.FilePicker(mime)` | 0.9.4 先保留系统选择器，内置选择器随 4.4 评估 |
| `SetupActivity/SetupView` | 不新增 | — | 权限引导并入现有 Onboarding 与权限页 |
| `AssistService` | 不新增 | — | 随 D4 删除无障碍配置与 `Wallpaper` |
| `OutProvider` | 合并进现有 FileProvider | — | 见 6.3 |

`Screen.createView` 返回的必须是自绘 `View`，`FilePager` 置于宿主 `contentFrame`，
不使用 `InkPage` 的 `ScrollView` 结构；`docs/03_墨水屏UI规范.md` 要求墨水屏不做惯性滚动，
墨盘分页语义本身与之一致。宿主顶栏与模块内顶栏的关系需要定：建议 Screen 内保留墨盘顶栏（标题、返回、
视图切换、菜单），宿主状态栏继续显示时间与电量，页面高度按内容区计算。

### 6.2 Manifest 条目对照

| 墨盘条目 | 桌面处置 |
| --- | --- |
| `SetupActivity`（LAUNCHER，导出） | 不新增；桌面入口已有 HOME 与 LAUNCHER |
| `BrowseActivity`（不导出） | 不声明 Activity，改为 Screen |
| `ViewerActivity`（导出，`VIEW` + `image/*`） | 声明 `PhotoEntryActivity`，导出，过滤器 `image/*` |
| `PickActivity`（导出，`GET_CONTENT` + `OPEN_DOCUMENT`） | 声明 `PickEntryActivity`，导出，过滤器沿用 `image/*` 与 `*/*` 两组 |
| `AssistService`（`BIND_ACCESSIBILITY_SERVICE`，`assist_config` 限定 `com.wetao.wallpaper`） | 不声明；现有 `OverlayAccessibilityService` 保持原职责 |
| `OutProvider`（`com.inkread.inkdisk.files`，不导出，授予 Uri 权限） | 合并到现有 FileProvider，新增分享子路径；或新增独立 authority，见 6.3 |
| `MANAGE_EXTERNAL_STORAGE` | 已声明，保留（D3） |
| `READ_EXTERNAL_STORAGE`（maxSdk 32） | 已由现有权限页管理，按需保留 |
| `READ_MEDIA_IMAGES` | 相册阶段评估；有 MANAGE_EXTERNAL_STORAGE 时 MediaStore 查询已可用 |
| `requestLegacyExternalStorage` | 桌面 targetSdk 35，不适用 |
| `largeHeap` | 桌面未开启，保持；编辑阶段用采样与分块控制内存 |
| `queries`（`ACTION_SEND` `image/*`） | 分享动作需要时再声明 |

### 6.3 authority 与 Intent 改名

| 原值 | 目标值 | 处理 |
| --- | --- | --- |
| 包名 `com.inkread.inkdisk` | `com.shuiyu.octoberrain.feature.files` | 模块命名空间；应用 ID 不变 |
| authority `com.inkread.inkdisk.files` | `${applicationId}.files`（`com.shuiyu.octoberrain.files`） | **冲突**：桌面 FileProvider 已占用该 authority |
| `EXTRA_PATH = "com.inkread.inkdisk.extra.PATH"` | `com.shuiyu.octoberrain.extra.FILE_PATH` | 仅保留外部契约需要时使用；模块内改数据类 |
| `WETAO_PACKAGE`、`WETAO_ACTIVITY` | 删除 | 随 D4 |
| `edited_rotations:<path>` 键 | 保留，迁移时增加命名空间前缀 | 见 `Edits` |

authority 冲突的可行方案：

- **方案 A（建议）**：复用现有 `androidx.core.content.FileProvider`，在
  `res/xml/file_paths.xml` 增加 `<external-path name="files_share" path="." />`，用
  `FileProvider.getUriForFile` 生成分享地址。改动最小，仍不导出、临时授予读权限。
  局限是 Uri 暴露编码后的文件路径，且仅覆盖主外部存储；二次存储卷的分享需先复制到 cache。
- **方案 B**：新增独立 authority `${applicationId}.files.share` 的 Provider，
  保留墨盘的 Base64 不透明路径与按需打开语义，但需要新增 Manifest 条目与 Provider 类，
  与现有 FileProvider 并存。
- **方案 C**：保留 `OutProvider` 类并把 authority 改为 `${applicationId}.files.share`，
  代码改动少，但维护两套路径编码，长期成本高。

### 6.4 偏好接入

`Prefs.java` 的四个键（`isGrid`、`isShowHidden`、`sortBy`、`sortAsc`）与 `lastPath`
属于文件页局部状态，改动频率低于桌面配置。建议：

- 首版写入模块内 `SharedPreferences`（文件 `files_prefs`），不扩展 `LauncherConfig`
  的序列化结构，避免配置版本升级与迁移逻辑扩散到 `core:data`。
- 如果产品要求跨设备或随备份迁移，再在 `LauncherConfig` 增加 `files` 节点，由
  `FilesPrefs` 读写该节点；此时需要同步 `Backup` 的字段白名单。
- `lastPath` 在不同入口（浏览页、选图页）应各自保存，选图页退出后恢复上次目录。

## 七、编辑与相册的缺口

26 个文件中与 0.9.5 相关的只有 `core/Edits.java` 的旋转另存。以下能力需要新写：

| 缺口 | 内容 | 依赖 | 估计行数 |
| --- | --- | --- | --- |
| 裁剪交互 | 拖动裁剪框、四角缩放、手势约束 | 自绘 View、命中测试 | 450 至 650 |
| 裁剪比例 | 自由、原图、1:1、4:3、3:4、3:2、2:3、16:9、9:16、屏幕 | 比例约束 | 80 至 120 |
| 黑白滤镜 | 原始灰阶、16 级、高反差、抖动二值、柔和，另加三档对比度 | `Ditherer`、`DitherCore`（已有 2/4/8 级） | 350 至 500 |
| 编辑输出 | 解码采样、旋转合成、PNG 编码、`Pictures/墨盘编辑`、命名 `原名-编-yyyyMMdd-HHmmss.png`、长边按内存档位取 1600/2400 | `ImageSizing`、`BitmapPipe` | 300 至 450 |
| 编辑页 | 顶栏、预览、工具条、应用与取消、忙碌态 | Screen 契约 | 250 至 350 |
| 撤销栈 | 20 步操作快照与回收 | 内存控制 | 120 至 200 |
| 相册索引 | MediaStore 查询（`_id/_data/date_added/size/width/height/mime_type/bucket_display_name`）、按父目录分桶、"全部"桶、写后与启动异步建索引 | 权限、协程 | 500 至 700 |
| 相册页 | 桶列表、时间轴、分页 16、查看器复用 | `PhotoViewer` | 400 至 600 |
| 入口接线 | 查看器编辑动作、写后失效、`RefreshScheduler.notifyContentChanged` | 现有服务 | 100 至 150 |

合计 2,550 至 3,720 行，与 docs/16 的 2,600 至 3,100 行估计一致，取中值约 3,000 行。
相册依赖 MediaStore 与权限分支（API 33 起 `READ_MEDIA_IMAGES`，低版本 `READ_EXTERNAL_STORAGE`），
在仅有 `MANAGE_EXTERNAL_STORAGE` 的设备上需确认查询结果；没有媒体权限时按 docs/16
降级为只读媒体模式，仍可浏览目录。

## 八、需要 Lead 或 Doow 决定的事项

1. **`Destination.Files` 提供者归属**：现有 `ContentModule` 注册 `FilesScreen(path)`，
   新模块 `FilesModule` 若再注册同一目标会冲突。建议 4.1 将 `Destination.Files` 迁到
   `feature:files`，旧 `FilesScreen.kt` 删除，`FileLibrary` 的图片目录能力迁入新模块或
   `core:data`。涉及 `feature/content` 与 `app` 的负责人，需要裁决。
2. **导出组件方案**：建议保留 `PhotoEntryActivity` 与 `PickEntryActivity` 两个薄 Activity，
   声明在 `feature/files` 的 Manifest；如果要求全部收进 `MainActivity` 路由，需要处理
   外部 Intent 与 `startActivityForResult` 语义，改动面更大。
3. **分享 authority 方案**：建议方案 A（合并现有 FileProvider，新增 `files_share` 根），
   需要修改 `app/src/main/res/xml/file_paths.xml`，该文件不在本任务写作用域。
4. **内置选图时机**：4.4 要求 `FilePicker.pick` 优先模块内选择器。建议 0.9.4 先保持系统
   选择器，待浏览与查看稳定后单独切换，避免同时改动 `AppServices.picker` 契约。
5. **字号与几何保真**：墨盘正文 11 sp、辅助 9 sp，桌面正文 14 sp、辅助 11 sp。建议统一到
   桌面令牌，并在三档真机截图对照信息密度；如果要求像素级复刻，需要为文件页单独定义一套
   字号，且接受与桌面其他页面不一致。
6. **网格列数**：S4 与 552×768 固定 4 列。824×1648 若按 `gridColumns` 计算可得 5 列数，
   缩略图更大；建议文件网格固定 4 列，与墨盘观感一致，把宽度余量用于图片预览。
7. **相册数据源**：建议 MediaStore 主、目录扫描兜底；如果只做目录扫描，则无法获得媒体库
   排序与系统新增文件，需要确认可接受。
8. **编辑输出目录**：建议 `Pictures/墨盘编辑`（沿用墨盘习惯），是否改名为 `Pictures/十雨桌面`
   需要产品确认；文件命名模板不含包名，重名风险由时间戳与序号控制。
9. **壁纸导出的去留**：按 D4 删除查看器的「导出壁纸」与壁纸自动设置行；如果保留入口，
   需要另立 0.9.8 任务，不在文件模块范围。
10. **权限与分发**：继续依赖 `MANAGE_EXTERNAL_STORAGE`（D3），Android 11 及以上采用
    「所有文件访问」引导；1.0 若上架应用商店，需要另评 `MANAGE_EXTERNAL_STORAGE` 政策风险。
11. **入口位置与应用商店入口**：墨盘按 D2 停用，不保留独立应用入口与 LAUNCHER Activity。
    文件能力从桌面文件页进入，图片能力另设入口（文件页图片分类或图片组件）；是否在桌面
    增加独立「图片」入口，需要 Doow 定。外部 `ACTION_VIEW` 与选图入口作为系统契约保留。

## 九、风险与验证

### 9.1 风险

| 风险 | 触发条件 | 缓解 |
| --- | --- | --- |
| 大图内存 | 高像素照片解码、邻图预取 2 张、编辑预览 | 采样与长边限制、`ImageSizing` 堆预算、编辑分块、`largeHeap` 不开启 |
| EXIF 方向 | 部分厂商 JPEG 方向标记异常 | 统一使用 `ImageDecoder.applyOrientation`，缩略图与查看器同源 |
| 缓存失效 | 文件改名、删除、编辑另存、外部写入 | 写后 `ThumbCache.invalidate` 与 `RefreshScheduler.notifyContentChanged`，启动轻量校验 |
| 外部入口 | `ACTION_VIEW` 传入非图片或超大文件 | MIME 与大小校验、64 MiB 复制上限、失败提示 |
| 权限降级 | 未获所有文件访问 | 现有权限页引导；只读媒体降级 |
| 手势回归 | 长按面板、滑动翻页、返回上级互相抢占 | 复用墨盘命中顺序与 350 ms 长按，补纯 JVM 命中测试 |
| 布局差异 | 宿主内容区高度与系统栏 | 以 View 实测宽高计算，三档截图对照 |

### 9.2 验证建议

- 纯 JVM：`FileMetrics` 三档几何、`FileSort` 排序、`FileItem` 派生属性、页码与越界夹取、
  `PhotoViewer` 缩放档位状态机。
- 真机：S4 480×800 必测，552×768 与 824×1648 至少各一轮截图；覆盖浏览、缩略图、排序、
  分页、查看、分享、选图、外部 `ACTION_VIEW`、编辑输出与相册时间轴。
- 性能：万张图片目录首屏时间、缩略图缓存命中率、翻页掉帧（墨水屏以局部刷新次数衡量）、
  编辑大图内存峰值。

## 十、核对记录

- 源文件清单与行数：`Get-ChildItem -Recurse -Filter *.java` 统计 26 个文件、9,320 行。
- 真机参数：`adb shell wm size` 返回 `480x800`，`wm density` 返回 `219`，
  `settings get system font_scale` 返回 `1.0`。
- 设备依赖：全仓 import 检索，未出现 `MediaStore`、`RecyclerView`、`Fragment`、`AndroidX`。
- 桌面契约：`core/common/.../Contracts.kt` 的 `Screen`、`ScreenProvider`、`ScreenRegistry`、
  `Router`、`FilePicker`、`AppServices`；`feature/content` 的 `FilesScreen`、`ContentModule`。
- 渲染能力：`core/render` 的 `ImageDecoder`、`ImageSizing`、`ThumbCache`、`Ditherer`、
  `BitmapPipe`、`InkCanvas`、`RefreshScheduler`。
- 计划依据：`docs/15-V1.0开发计划.md` 的 0.9.3、0.9.4、0.9.5 任务与 D1 至 D5 决策；
  几何与编辑参数参考 `docs/16_墨盘_文件管理器与相册_评估与设计.md` 与
  `inkdisk/CONTRACT.md`。
- 本报告路径：`_analysis/team/mopan-port-plan.md`；未修改任何工程源码。
