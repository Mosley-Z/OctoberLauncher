# 文件模块实现说明

本文记录 `feature:files` 的模块结构、数据流、页面几何与外部接口，供后续维护与二次开发查阅。文中路径与行号以当前工作区源码为准；括号内数字为对应文件的当前行数或行号。

## 一、定位与范围

文件模块承担四类能力：文件浏览、图片查看、系统选图，以及图片编辑与相册。模块取代了 `feature/content` 中的旧文件页实现，`ContentModule` 不再处理 `Destination.Files`。提供者归属与入口决策记录在 [`docs/design/16-墨盘移植决策.md`](16-墨盘移植决策.md)。

模块边界：

内置文件入口与已安装资源管理器分别由特殊入口显隐和应用目录管理，两者身份独立，不因名称或图标相同互相替换。新装默认显示内置文件入口；既有“桌面偏好→桌面与布局→管理隐藏应用”顶部“内置入口”提供文件开关，开为显示、关为隐藏，父级隐藏数量包含文件。安装应用仍由`hiddenPackages`独立管理，开关同样开为显示、关为隐藏；不新增独立页面或其他内置入口开关。

配置结构版本16对版本15及以前的配置仅一次移除`hiddenSpecials`中的`special:files`，保留其他入口、应用和布局；版本16之后用户重新隐藏文件的选择保留。此兼容边界不改变APK版本。

- 依赖 `core:common`、`core:device`、`core:design`、`core:data`、`core:render`，构建脚本见 `feature/files/build.gradle.kts`（37 行）。
- 在 `settings.gradle.kts:32` 注册为 `:feature:files`。
- 由宿主 `app/build.gradle.kts:63` 依赖，并在 `OctoberRainApp.kt:100` 以反射按类名安装。

## 二、模块装配与外部接口

`FilesModule.kt`（60 行）实现 `LauncherModule`，安装时完成两项注册：

1. `ScreenRegistry.register(FileScreenProvider)`。
2. `NegativeModuleRegistry.register(FilesNegativeModule())`，按标识幂等，见 `FilesModule.kt:36-39`。

页面提供者覆盖四个目的地，见 `FilesModule.kt:47-54`：

| 目的地 | 页面 | 定义位置 |
| --- | --- | --- |
| `Destination.Files(path)` | `FilesScreen` → `BrowseScreen` | `core/common/.../Contracts.kt:395` |
| `Destination.PhotoViewer(paths, index)` | `PhotoScreen` | `Contracts.kt:408` |
| `Destination.PhotoEditor(path, source)` | `EditScreen` | `Contracts.kt:422` |
| `Destination.Album` | `AlbumScreen` | `Contracts.kt` 相册目标 |

模块清单 `feature/files/src/main/AndroidManifest.xml`（41 行）声明两个导出 Activity：

- `PhotoEntryActivity`：`ACTION_VIEW` + `image/*`，承接外部单图查看。
- `PickEntryActivity`：`GET_CONTENT` 与 `OPEN_DOCUMENT` 两组过滤器，承接系统选图请求。

选图回传使用宿主 `FileProvider`，authority 取 `${applicationId}.files`，见 `app/src/main/AndroidManifest.xml:91-92` 与 `PickEntryActivity.kt:110`。`app/src/main/res/xml/file_paths.xml` 为分享与选图提供四个路径：

- `cache-path name="updates" path="updates/"`
- `files-path name="themes" path="themes/"`
- `external-path name="files_share" path="."`
- `cache-path name="files_share_cache" path="share/"`

## 三、目录结构与分层

数据层（`feature/files/src/main/java/com/shuiyu/octoberrain/feature/files/data/`）：

| 文件 | 行数 | 职责 |
| --- | --- | --- |
| `FileItem.kt` | 102 | 目录条目模型与派生属性 |
| `FileListing.kt` | 214 | 目录列举、父路径、显示名与 MIME |
| `FileSortKey.kt` | 58 | 名称、时间、大小、类型四种排序 |
| `FilePrefs.kt` | 65 | 视图、隐藏文件、排序、最后路径偏好 |
| `StorageAccess.kt` | 95 | 存储权限、卷、书籍与图片目录 |
| `FileOps.kt` | 187 | 同名生成、同内容比较、复制与移动 |
| `FileStreams.kt` | 116 | 流式复制与同内容判断 |
| `FileIntents.kt` | 44 | 图片查看与外部文件意图 |
| `MediaIndex.kt` | 643 | 相册索引、编解码、增量合并与目录扫描 |
| `Edits.kt` | 200 | 单次旋转与旋转计数 |
| `EditSession.kt` | 312 | 编辑操作栈与位图应用 |
| `EditExport.kt` | 258 | 导出命名、缩放与写入 |

视图层分为四组：

- 浏览：`BrowseScreen.kt`（294）、`BrowsePane.kt`（502）、`BrowsePanel.kt`（152）、`GridPane.kt`（196）、`ListPane.kt`（184）。
- 分页容器：`FilePager.kt`（204）、`FilePagerChrome.kt`（191）、`FilePagerTouch.kt`（143）、`FilePaging.kt`（130）。
- 查看与选图：`PhotoScreen.kt`（651）、`ViewerChrome.kt`（234）、`ViewerState.kt`（214）、`PickPane.kt`（191）、`PickEntryActivity.kt`（150）、`PhotoEntryActivity.kt`（120）。
- 相册与编辑：`album/AlbumScreen.kt`（436）、`album/AlbumPane.kt`（418）、`edit/EditScreen.kt`（72）、`edit/EditSurface.kt`（693）。

几何与绘制工具为 `FileTokens.kt`（83）、`FileMetrics.kt`（72）、`PaneDraw.kt`（117）、`ThumbPrefetcher.kt`（150）。

## 四、数据层行为

### 4.1 条目与目录列举

`FileItem` 字段为 `name/path/dir/size/mtime/mime/width/height`，见 `FileItem.kt:15-23`。派生属性：

- `isImage`：非目录且 MIME 以 `image/` 开头（`FileItem.kt:26`）。
- `isEditableImage`：在图片基础上排除 GIF 等不可编辑格式（`FileItem.kt:29`）。
- `parentPath`、`extension()`、`baseName()`（`FileItem.kt:36-56`）。

`FileListing.list(dirPath)` 负责读取目录并应用隐藏文件与排序偏好，见 `FileListing.kt:34`。同文件的辅助方法：

- `listImageDirs()`：外置存储根下一级非隐藏目录（`:60`）。
- `parentOf(path)`：卷根或 `/storage`、`/mnt` 返回 null（`:78`）。
- `nameOf(path)`：主存储根显示“内部存储”（`:90`）。

读取失败写入 `lastError` 并返回空列表，界面据此显示错误提示，不向调用方抛出异常。

### 4.2 排序与偏好

`FileSortKey` 提供 `NAME/TIME/SIZE/TYPE` 与 `label`，比较器入口为 `comparator(ascending, dirFirst)`，目录优先且同类回退名称比较，见 `FileSortKey.kt:33-52`。

`FilePrefs` 使用独立偏好文件 `files_prefs`，键为 `view_grid/show_hidden/sort_by/sort_asc/last_path`，见 `FilePrefs.kt:14-53`。默认值依次为列表视图、隐藏隐藏文件、按名称、升序、空路径。

### 4.3 存储访问

`StorageAccess.hasAccess()` 从权限快照读取 `PermissionCenter.KEY_STORAGE`，见 `StorageAccess.kt:25`。其余方法提供：

- `externalRoot()`（`:34`）与 `volumes()`（`:42`）。
- `isReadable(path)`（`:56`）与 `volumeName(root)`（`:63`）。
- `booksRoot()`（`:71`）、`picturesRoot()`（`:84`）、`editOutputRoot()`（`:87`）。

编辑输出根为 `Pictures/十雨桌面`。

### 4.4 复制与移动

`FileOps`（187 行）提供 `sameContent`、`uniqueName`、`copy`、`move`，见 `FileOps.kt:29-84`。复制采用临时文件落盘后替换，重名上限为 `MAX_SUFFIX = 999`；移动优先使用 `renameTo`，跨卷时退化为复制后删除。流缓冲大小为 8 KiB，定义在 `FileStreams.kt:10`。

### 4.5 外部意图

文件页依据后缀或APK MIME识别安装包，不限定下载目录。APK优先于图片及普通阅读分流，先由文件模块提供“安装/查看结构”选择，取消不执行动作。安装通过现有FileProvider交给Android系统安装界面，用户自行决定安装及未知来源授权；项目不自动确认或修改授权。查看结构交原阅读器的ZIP引擎，共享Reader契约保持。

普通归档默认查看仅适用于常见压缩后缀或ZIP MIME；JAR、AAB及未知二进制文件不再仅凭ZIP签名进入归档查看。EPUB与Office专用识别保持优先，APK结构查看由文件页明确选择触发；不新增RAR等解码引擎。

`FileIntents.EXTRA_PATH = "com.shuiyu.octoberrain.extra.FILE_PATH"`，见 `FileIntents.kt:16`。`viewImage` 与 `openFile` 使用 `FLAG_GRANT_READ_URI_PERMISSION`，`photoEntry` 直接构造 `PhotoEntryActivity` 组件意图。

## 五、媒体索引

`MediaIndex.kt`（643 行）为相册与负一屏卡片提供只读索引。

数据类型（均为模块内部类型）：

- `MediaEntry`：`id/path/name/takenAt/addedAt/width/height/orientation/mime`，`time` 优先取拍摄时间，见 `MediaIndex.kt:37-52`。
- `MediaDay`：按日分桶，含标题、时间与条目列表（`:60`）。
- `MediaMonth`：按月分桶，含条目数与封面路径（`:78`）。
- `MediaIndexData`：版本号、生成时间与条目表（`:92`）。

索引流水线：

1. `MediaIndexCodec`（`:246`）负责 JSON 编解码，版本常量 `VERSION = 1`（`:248`）。
2. `MediaIndexMerge`（`:335`）按路径合并新旧条目，保留拍摄时间与尺寸信息。
3. `MediaIndex`（`:374`）负责缓存读取、重建与增量刷新。

对外方法：

| 方法 | 行号 | 行为 |
| --- | --- | --- |
| `peek()` | 385 | 返回内存中的只读快照，未加载时为 null |
| `indexSize()` | 388 | 返回索引文件字节数 |
| `recent(limit)` | 396 | 按时间倒序取最近图片 |
| `loadCached()` | 408 | 读取磁盘缓存 |
| `rebuild()` | 433 | 全量扫描并重建 |
| `refresh(sinceMillis)` | 463 | 增量扫描并合并 |

扫描约束见 `MediaIndex.kt:636-639`：索引文件 `media-index.json`，忽略 `.nomedia`，最大深度 6，单次最多 5000 个文件。

设计文档曾使用“媒体日志”描述增量机制；当前实现没有独立的 `MediaJournal` 类型，增量合并由 `MediaIndexMerge` 与索引版本承担。两者职责等价，文件命名以源码为准。

## 六、浏览层与几何

一次完整的数据流为：`FilesModule` 注册路由 → `BrowseScreen` 按入口参数或 `lastPath` 列举目录 → `ThumbPrefetcher` 在 IO 线程预取缩略图并写入 `ThumbCacheRegistry` → 点击图片构造 `Destination.PhotoViewer` 由 `PhotoScreen` 打开，编辑入口构造 `Destination.PhotoEditor` 由 `EditScreen` 打开 → `EditExport` 写回 `Pictures/十雨桌面` 后失效缩略图并通知刷新 → `AlbumScreen` 与 `FilesNegativeModule` 读取 `MediaIndex` 与缩略图缓存，不重复扫描目录。

`BrowseScreen`（294 行）是 `Screen` 实现，生命周期与加载顺序：

- `createView` 检查存储权限，缺失时显示说明与“打开存储设置”按钮（`BrowseScreen.kt:60`、`:237`）。
- `buildPane` 依据偏好创建网格或列表页并绑定回调（`:141`）；`rebuildPane` 保留 `firstIndex`（`:192`）。
- `load(path, firstIndex, resetPage)` 在 IO 线程列举后提交数据与分页位置（`:207`）。
- `resolveStartPath` 依次使用入口参数、`lastPath`、外置存储根（`:231`）。
- `onBack` 先关闭面板，再返回上级，卷根返回 false（`:91`）。
- `notifyFileChanged` 失效缩略图、通知内容变更并请求局部刷新（`:124`）。

分页容器拆分为四个文件：状态与纯函数（`FilePaging.kt`）、顶底栏绘制（`FilePagerChrome.kt`）、触摸分发（`FilePagerTouch.kt`）、容器（`FilePager.kt`）。长按阈值为 350 毫秒，纵向翻页阈值为 48dp，定义在 `FilePaging.kt`。

几何令牌 `FileTokens` 全部由 `dp` 与 `textSize` 派生，见 `FileTokens.kt:37-82`：

| 令牌 | 值 |
| --- | --- |
| `padH` | 18dp |
| `topBarH` | 56dp |
| `bottomBarH` | 52dp |
| `rowH` | 44dp |
| `rowThumb` | 30dp |
| `gap` / `gapLarge` | 6dp / 10dp |
| `cellLabelH` | 16dp |
| `slop` / `touchMin` | 12dp / 44dp |
| `titleText` / `bodyText` / `smallText` | 17sp / 14sp / 12sp |

文件宫格以方形缩略图为主，竖屏3列、横屏5列，行数及每页容量按宿主可用空间动态确定。浏览与Picker共用几何，绘制和命中使用同一布局边界；缩略图居中并尽量填充格子，名称为较小的单行短标签，视觉文件省略冗余后缀、过长名称省略，完整名称仍由原详情提供。整格均可命中，旋转后按原首项所在页重新分页。图片不显示“图”角标；列表布局及图片/非图片的打开语义保持独立。

## 七、查看、选图与编辑

查看器 `PhotoScreen`（651 行）按 `onSizeChanged` 构建度量，`PhotoSurface` 在后台解码并做代际校验，见 `PhotoScreen.kt:47-83`、`:207-241`。

选图入口 `PickEntryActivity`（150 行）列出目录与可接收文件，选中后经 `FileProvider` 回传 Uri（`:110`）；取消返回 `RESULT_CANCELED`。选择页主体为 `PickPane.kt`（191 行）：构造入口（`:23`）、取消动作（`:59`）、返回上级（`:67`）、顶部切换（`:84`）、目录加载（`:137`）与条目转换（`:160`）。选择范围按请求的 MIME 类型过滤，目录可逐级进入，文件大小经本地格式化显示。

外部单图入口 `PhotoEntryActivity`（120 行）在 content Uri 场景下先复制到缓存，单文件上限 64 MiB（`:117`）。

编辑能力由数据层与界面层分工：

- `EditSession`（312 行）维护最多 20 步操作栈，支持旋转、镜像、裁剪、滤镜与对比度，入口见 `EditSession.kt:125-206`。
- `EditExport`（258 行）按内存档位选择长边 1600 或 2400，输出 PNG，目录 `十雨桌面`，命名规则为 `原名-编-yyyyMMdd-HHmmss.png`，相关常量见 `EditExport.kt:21-39`。
- `Gray.kt`（157 行）与 `GrayKernels.kt`（212 行）提供五档灰度滤镜与三档对比度。
- `CropMath.kt`（169 行）提供裁剪矩形夹取、旋转与最小边校验。

## 八、负一屏「最近图片」

`FilesNegativeModule.kt`（360 行）的纯逻辑集中在 `RecentImages`（`:35`）：

- 最多 6 张，两行三列，标题“最近图片”，空态“暂无图片”，见 `:37-49`。
- `recentPaths(entries, limit)` 过滤图片并按时间倒序（`:57`）。
- `viewerTarget(entries, index)` 构造查看器目标，下标越界时夹取（`:73`）。
- `tileSize(contentWidth, gap)` 按列等分并保证至少 1 像素（`:94`）。

卡片视图 `createView`（`:130`）在索引未就绪时展示占位文案，底部固定保留“全部图片”入口；缩略图统一经 `ThumbCacheRegistry` 读取，卡片自身不扫描目录。

## 九、刷新与缓存协作

- `ThumbCacheRegistry.get(context, targetEdge)` 提供按目标长边分档的共享缓存（`core/render/.../ThumbCacheRegistry.kt:72`）。
- `peekMemory(path, targetEdge)` 供绘制线程只读查询（`:98`）。
- `invalidate(context, path)` 在写后失效（`:115`）。
- `RefreshScheduler.notifyContentChanged()` 与 `requestFullRefresh(reason)` 负责刷新调度，见 `core/render/.../RefreshScheduler.kt:77`、`:90`。
- 浏览页切页使用 `AppServices.refresh.requestPartial()`，避免整屏刷新。

缩略图预取由文件模块在后台完成，绘制期只查内存缓存。宫格的`GridPane`与`PickPane`开启EPUB缩略图，列表不启用该扩展；图片原缩略图链路保留。

EPUB封面提取由[core:render的EpubCover](../../core/render/src/main/java/com/shuiyu/octoberrain/core/render/EpubCover.kt)共有，提供解码和提取能力；内容模块原入口为薄转发，文件模块不复制提取算法，也不横向依赖content。文件宫格以原EPUB路径及修改时间复用既有缩略图缓存；无封面、损坏或无法解码时保持EPUB占位。封面仅改变宫格预览，不把EPUB当成图片，不改变文件类型、打开行为或列表展示。

## 十、与计划差异和已知限制

与 0.9.4 决策文档的差异：

1. 旧 `feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/ui/FilesScreen.kt` 仍在仓库中，但 `ContentModule` 已不再注册该目标，页面亦不再被路由引用，属待清理文件。
2. 相册索引没有独立媒体日志类型，增量语义由 `MediaIndexMerge` 承担，见第五节。
3. 桌面图片入口仍使用 `HostFilePicker`（`OctoberRainApp.kt:65`、`ImageWidget.kt:102`），模块内 `PickEntryActivity` 与 `PickPane` 只作为系统 `GET_CONTENT`/`OPEN_DOCUMENT` 请求的提供方，与决策中“选图仍由系统选择器承担”一致。

已知限制：

1. `FileProvider` 的 `${applicationId}.files` 同时服务于选图与分享，路径授权范围较大，后续可按用途拆分 authority。
2. 全盘能力依赖 `MANAGE_EXTERNAL_STORAGE`（`app/src/main/AndroidManifest.xml:29`），上架政策需单独评估。
3. 宫格行数与页容量随宿主空间变化，不承诺固定每页项数；缩略图保持居中方形，名称和整格命中仍按共享布局。
4. 文件页的图片打开能力以模块内查看器实现，不依赖系统图库。
5. 相册首刷在 5000 个文件规模内完成，超过上限的目录不进入索引。
6. `MediaIndex.kt` 当前 643 行，编解码、合并、扫描与缓存读写集中在一个文件，后续可按编解码、合并、扫描三部分拆分。
7. 负一屏卡片在页面恢复时刷新（`FilesNegativeModule.kt:145`），创建后的首次恢复跳过；索引与缩略图异步完成，卡片首次出现时可能短暂显示占位文案。
8. 三档几何中仅 480×800 做过真机走查；552×768 与 824×1648 目前只有 `FileMetricsTest` 的度量断言，尚未在实机核对。

## 十一、行号索引

| 主题 | 位置 |
| --- | --- |
| 模块注册 | `FilesModule.kt:25-59` |
| 页面目的地 | `core/common/.../Contracts.kt:395`、`:408`、`:422` |
| 文件条目 | `data/FileItem.kt:15-56` |
| 目录列举 | `data/FileListing.kt:34-90` |
| 排序 | `data/FileSortKey.kt:33-52` |
| 偏好 | `data/FilePrefs.kt:14-53` |
| 索引 | `data/MediaIndex.kt:374-463` |
| 浏览宿主 | `ui/BrowseScreen.kt:60-231` |
| 几何 | `ui/FileTokens.kt:37-82`、`ui/FileMetrics.kt:18-60` |
| 分页 | `ui/FilePager.kt`、`ui/FilePagerChrome.kt`、`ui/FilePagerTouch.kt` |
| 查看 | `ui/viewer/PhotoScreen.kt:47-241` |
| 选图 | `ui/PickEntryActivity.kt:26-131` |
| 编辑 | `data/EditSession.kt:125-206`、`data/EditExport.kt:70-140` |
| 负一屏 | `ui/negative/FilesNegativeModule.kt:35-107` |
