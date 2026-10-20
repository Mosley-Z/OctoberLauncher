# 墨盘并入十月桌面的评估

评估日期 2026-10-19。评估对象为两处工程：墨盘（`D:\DSHStation\MoPan`，Android 工程位于 `inkdisk\`）与十月桌面（`D:\DSHStation\OctoberLauncher`）。全程只读，未改动两侧源码、文档与构建产物。文中行号对应评估时的文件版本。缺证据的条目一律标注「待确认」，不做推断性陈述。

## 一、评估材料

| 材料 | 规模 | 用途 |
| --- | --- | --- |
| `MoPan\docs\16_墨盘_文件管理器与相册_评估与设计.md` | 563 行 | 目标形态、模块估算、编辑管线、分期 |
| `MoPan\inkdisk\CONTRACT.md` | 221 行 | 模块边界、界面规范、硬性约束、0.2.0 增补 |
| `MoPan\inkdisk\app\src\main\AndroidManifest.xml` | 109 行 | 组件、权限、SDK 版本 |
| `MoPan\inkdisk\app\src\main\java\com\inkread\inkdisk\` | 26 个 Java 文件，9320 行 | core 12、shell 6、ui 8 |
| `MoPan\docs\03_墨水屏UI规范.md`、`docs\01_设备_S4.md`、`docs\14_多屏适配评估_卡片与墨台.md`、`docs\DECISIONS\ADR-008_EPD接口.md` | — | 设备前提、几何、刷新约束 |
| `OctoberLauncher` 的 `docs\03-target-architecture.md`、`settings.gradle.kts`、`app\src\main\AndroidManifest.xml`、`gradle\libs.versions.toml` | — | 架构、模块、构建、权限 |
| `OctoberLauncher` 的 `feature\content`、`core\render`、`core\common`、`app` 相关源码 | 见索引 | 现有文件与图片能力 |
| `OctoberLauncher\docs\13-inkOS对照评估.md` | 158 行 | 报告章节结构参照 |

## 二、十月桌面现有文件与图片能力

### 2.1 文件页与数据层

`feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/data/FileLibrary.kt`（419 行）承担全部文件读写。类注释自述四项职责：入口统计、目录浏览、最近文件、重命名与复制移动删除（L37-44）。实有方法：`booksDirectory` L59、`picturesDirectory` L62、`fontsDirectory` L65、`roots` L71、`overview` L79、`list` L100、`recentFiles` L117、`ancestors` L143、`readText` L160、`rename` L169、`copyOrMove` L189、`createDirectory` L214、`delete` L227、`openIntent` L248、`copyToOpenCache` L265、`mimeTypeOf` L289、`uniqueDestination` L326、`copyFile` L341、`copyDirectory` L349、`countFiles` L368、`directorySize` L381、`capacityBytes` L404、`usedBytes` L412。常量：每页 9 项 `PAGE_SIZE=9` L49、最近 5 项 `RECENT_LIMIT=5` L50、递归统计深度 5 `MAX_COUNT_DEPTH=5` L51、打开缓存 6 份 `MAX_OPEN_CACHE=6` L52、图片扩展名六种 L54、字体扩展名三种 L55。

`ui/FilesScreen.kt`（467 行）是文件页的唯一界面。类注释说明两种模式：总览模式显示四入口卡与存储占用、最近修改；目录模式每页九项，支持进入目录、上级返回、打开文件、长按重命名/复制/移动/删除、新建目录（L21-27）。常量与动作码见 L31-36；`render` L59；总览 L83-137；目录行 `fileRow` L318-334；长按操作菜单 L349-361；重命名对话框 L363-379；复制与移动对话框 L380-397；删除确认 L398-413；新建目录 L414-426；分页按钮「上一页/页码/下一页」L290-310。图标使用 `InkGlyphs` 矢量（L333、L341-347），无任何图片缩略。

评估结果：目录浏览、打开文件、重命名、复制、移动、新建目录、删除、最近文件、存储占用统计均已实现，已超出墨盘 P0 列出的范围（`CONTRACT.md` L15 明确 P0 不做复制移动删除）。缺缩略图、图片查看器、图片编辑、相册时间轴、壁纸入口；`grep Bitmap|thumbnail|Viewer|preview` 在 `FilesScreen.kt` 无命中。

### 2.2 文件访问模型

`app/src/main/AndroidManifest.xml` L23-32 同时声明 `READ_EXTERNAL_STORAGE`（maxSdk 32）、`WRITE_EXTERNAL_STORAGE`（maxSdk 28）、`MANAGE_EXTERNAL_STORAGE`、`QUERY_ALL_PACKAGES`。`core/device/src/main/java/com/shuiyu/octoberrain/core/device/PermissionCenter.kt` L34 以 `hasAllFilesAccess()` 写入存储键，L56 调 `Environment.isExternalStorageManager()`，L197 定义 `KEY_STORAGE="storage"`。文件读写一律经 `java.io.File`（`FileLibrary.kt` L100、L169-232），未使用 MediaStore，未使用 SAF 树授权。

系统文件选择器独立存在：`app/src/main/java/com/shuiyu/octoberrain/HostFilePicker.kt`（126 行）实现 `FilePicker` 接口，`pick` L51-59 用 `ACTION_OPEN_DOCUMENT`，`save` L69-77 用 `ACTION_CREATE_DOCUMENT`，`pickDirectory` L85-91 用 `ACTION_OPEN_DOCUMENT_TREE`；`onActivityResult` L94-103 只回传 `data.data`，未调用 `takePersistableUriPermission`。

### 2.3 图片相关实现

| 位置 | 行为 |
| --- | --- |
| `core/render/.../BitmapPipe.kt` L13、L24-36、L45、L61 | 统一采样解码：目标尺寸、`inSampleSize` 按 2 的幂、`RGB_565`、`inScaled=false` |
| `core/render/.../Ditherer.kt` L12、L22、L85 | 有序抖动与阈值二值化；全工程无调用方（检索 `Ditherer` 仅命中声明行 L12） |
| `core/render/.../InkCanvas.kt` L13、L21-61 | 矩形、圆角矩形、胶囊、细线、反色填充 |
| `feature/widgets/.../ImageWidget.kt` L26、L51、L70、L82-107、L108-136、L137-150 | 图片组件：经 `FilePicker` 选图、复制到内部目录、按目标尺寸解码、绘制 |
| `feature/widgets/.../CoversWidget.kt` L112、L141-152 | 封面绘制与采样解码 |
| `app/.../WallpaperView.kt` L27、L42-54、L69、L107 | 桌面自绘壁纸层，按视图尺寸拉伸绘制，支持白色压层与变暗，配置变化后在 IO 线程重新解码 |
| `feature/settings/.../ImageDirectoryStore.kt` L16、L25、L28、L31-32、L51-58、L61-73、L76-88 | 美化模式的图片目录选择与采样解码：扩展名五种、单次上限 200、默认 `Pictures`、`OPEN_DOCUMENT_TREE` 的 Uri 转目录 |
| `feature/content/.../data/CoverResolver.kt` L14-15、L18-49、L53-66 | 书封解析四级：`Books/cover` 词干匹配、同目录同名图片、`.sdr` 内封面、EPUB 内嵌 |
| `feature/content/.../data/EpubCover.kt` L17-22、L43-179 | EPUB 内嵌封面抽取：容器定位、OPF 解析、封面优先级、4MB 上限 |
| `feature/content/.../ui/BookshelfNegativeModule.kt` L419-445、L484 | 负一屏书架仅书目条目，打开本地书时经 `content.files.openIntent`；无独立文件区 |

核实结果：十月桌面没有图片查看器、没有相册、没有系统壁纸设置（检索 `SET_WALLPAPER|WallpaperManager|wallpaper` 无命中），壁纸是桌面自绘层（`WallpaperView.kt` L18-26）。图片解码实现分散在七处（`BitmapPipe.kt` L24-61、`ImageWidget.kt` L137-150、`CoversWidget.kt` L141-152、`ImageDirectoryStore.kt` L76-88、`ContentUi.kt` L451-457、`IconOverrideStoreImpl.kt` L54-115、`AppearanceRepositoryImpl.kt` L210-234），均无 EXIF 方向归一。

### 2.4 刷新与几何

`core/render/.../RefreshScheduler.kt` L21 默认整屏刷新间隔 10 分钟，L46 `attach`，L58 `updateInterval`，L74 `notifyContentChanged` 合并局部重绘，L87 `requestFullRefresh`，L103 `onFullRefreshPerformed`。`docs/03-target-architecture.md` L112-124 说明四个渲染构件；L76-89 要求 feature 层经 `DeviceProfile` 取尺寸，禁止读 `DisplayMetrics`；L128 说明单 Activity 与页面注册；L171-173 要求自有包名、资源、FileProvider authority。EPD 节点在 App 内不可达（`ADR-008_EPD接口.md` L46-53），两台设备均依赖框架层刷新。

## 三、墨盘源码实际能力

26 个文件：core 12 个 3011 行、shell 6 个 2427 行、ui 8 个 3882 行。下表的「实有操作」按方法名与行号核对，设计文档中尚未编码的部分单独列出。

### 3.1 core 包

| 类 | 行数 | 职责与实有操作 |
| --- | --- | --- |
| `Edits.java` | 245 | 唯一编辑算子：`rotate90` L57 解码→`Matrix.postRotate` L74-83→写副本；角度 `((n%4)+1)%4*90` L65；副本名 `nextTarget` L155（扩展名前 `_edited`，重名 `_edited_2` 起）；PNG 源写 PNG、其余 JPEG 90 L92；只读原图（类注释 L15-19）；成功后累计旋转次数 `Prefs.setEditedRotations` L100 |
| `FileItem.java` | 72 | 目录项与文件项模型；`isImage` L52、`isEditableImage` L57、`extension` L62、`baseName` L68 |
| `ImageDecode.java` | 454 | 两遍采样解码：长边上限 3200（L32）、采样目标 1600（L35）、8000 万像素上限（L38）、`RGB_565`（L125）、堆预算判断 `heapAllows` L426；EXIF 方向归一 `orientationOf` L281/L303 与 `applyOrientation` L334；Uri 解码 L152；MIME 判定 L195（jpg/png/webp/gif/bmp/heic） |
| `Intents.java` | 56 | 进程内静态交接 `setPendingViewer` L42、`takePendingViewer` L51，避免经 Intent 附项传递文件位置 |
| `Ops.java` | 326 | 文件操作语义：`sameContent` L41 8KB 逐块比对、`uniqueName` L93、`copy` L126 临时文件后替换、`move` L181 rename 失败回退复制、`writeStream` L257；无界面调用方（检索 `Ops.` 仅命中 `Wallpaper.java` L35 注释） |
| `OutProvider.java` | 271 | 只读 ContentProvider：`uriFor` L47、`pathOf` L65、`getType` L130；写操作抛 `UnsupportedOperationException("只读通道")` L177-191；可读判定 `readableFile` L199、允许根 `isUnderAllowedRoot` L230（外置存储根与各卷根）、`isUnder` L256 |
| `PathModel.java` | 341 | 目录列举与命名：`list` L65（不可读返回 `br_error_read` L74-76、SecurityException L81、隐藏文件过滤 L96、目录优先排序 L108）、`listImageDirs` L123（仅根下一级）、`parentOf` L164（卷根与 `/storage`、`/mnt` 返回 null）、`nameOf` L190（主存储根显示「内部存储」）、`formatSize` L274、`formatTime` L294 |
| `Prefs.java` | 168 | SharedPreferences 偏好：网格/隐藏/排序/升序/上次目录 L41-81、自选文件夹 L94-116、编辑旋转次数 L137-157 |
| `Sort.java` | 77 | 四档排序（名称/时间/大小/类型）加升降序与目录优先；`label` L64 |
| `Storage.java` | 142 | 权限与卷：`hasAccess` L36、`manageAccessIntent` L50、`appDetailsIntent` L67、`requestLegacy` L73、`externalRoot` L81、`volumes` L91、`isReadable` L122、`volumeName` L134 |
| `Thumbs.java` | 401 | 内存缩略图缓存：`LruCache` 上限取 6MB 与堆上限/16 的较小值（L61-63，以 KB 计量、`sizeOf` 按位图字节数 L68-72）、`get` L96、`peek` L117、批量预取 `prefetch` L136、`clear` L177、`decodeAndStore` L195、`invalidate` L231、失败表上限 256（L30、`markFailed` L328）；静态脏表 `registerDirty` L350、`takeDirtyPaths` L367、计数 L387-398（无调用方，检索 `Thumbs.registerDirty` 无命中）；目标像素取调用方给定的显示边长，一页 16 项、每 16 张触发一次重绘（L33） |
| `Wallpaper.java` | 458 | 厂商通道壁纸：类注释 L35 明确不申请 `SET_WALLPAPER`；`exportForWetao` L326 解码→缩放到长边上限→输出 `/sdcard/Wallpaper/mopan_<时间>.jpg`（JPEG 90）→拉起 `com.wetao.wallpaper`；`scaleToLongEdge` L372 不放大；`nextFileName` L397；无障碍开关 `isAssistEnabled` L104、`ensureAssistComponent` L297 |

### 3.2 shell 包

| 类 | 行数 | 职责与实有操作 |
| --- | --- | --- |
| `BrowseActivity.java` | 733 | 目录浏览页装配：`onCreate` L78、起始目录 `resolveStartPath` L101、`load` L120、缩略图预取 L149、目标像素 L172、脏缩略图 L184-237、分页 `buildPage` L239、进入目录 L298、打开图片 L312（收集当前目录图片与下标，经 `Intents.setPendingViewer` 交接后启动查看器，L308-309 注释说明不经 Intent 附项）、视图切换 L339、自选文件夹 L360-373（内嵌 `FolderPickPage` L506）、壁纸全自动开关 L408-420、按键与返回 L426-483 |
| `PickActivity.java` | 395 | 对外选择器：`onCreate` L46（无权限提示后结束）、MIME 收集 L96、`loadDir` L122、`finishWith` L248（`OutProvider.uriFor` + `FLAG_GRANT_READ_URI_PERMISSION` + `grantUriPermission`，不做持久化）、`accepts` L279、`mimeFor` L317（图片类型经 `ImageDecode.mimeOf`，补 pdf/txt/html/epub 等） |
| `SetupActivity.java` | 126 | 权限准备页：`onCreate` L34、`onResume` L44、进入浏览 L54、主按钮 L64、设置 L80、重试 L85、无障碍 L97、结果回传 L110 |
| `SetupView.java` | 273 | 准备页自绘：按钮布局 L94、绘制 L110-244、触摸 L248 |
| `ViewerActivity.java` | 355 | 查看器宿主：受外部 `ACTION_VIEW` 拉起；Uri 转缓存副本 `copyToCache` L180（16KB 缓冲、超限返回 tooLarge）、显示名 `displayName` L245、`showViewer` L271（构造 `ImageView`）、返回处理 L289、按键 L299、销毁清理 L315-325 |
| `AssistService.java` | 545 | 无障碍服务，仅处理 `com.wetao.wallpaper` 窗口：会话 `request` L115、`begin` L206、`tick` L251、`finish` L332、窗口判定 L342、点击 L355-509、节点文本 L530；动作上限与超时见 `CONTRACT.md` L209-221 |

### 3.3 ui 包

| 类 | 行数 | 职责与实有操作 |
| --- | --- | --- |
| `GridPage.java` | 814 | 4×4 网格页：绘制 L254、动作面板 L292-325（八行：四档排序、升降序、显示隐藏、视图、自选文件夹；厂商通道第九行壁纸全自动 L337）、面板状态 L705-735、面板动作 L772-806、单元格绘制 L551-610、长按面板「打开」「详情」L368、排序与偏好回写 L757-809 |
| `ListPage.java` | 819 | 列表页：一屏十行（S4）或十六行（第二台，`CONTRACT.md` L71-84、`docs\16` L266），结构与网格页对应，行绘制 L560-665、面板 L311-390 |
| `PagerView.java` | 318 | 自绘分页容器：每次绘制先 `drawColor(Ink.PAPER)` 整屏铺纸 L224-231；底栏上一页/下一页/页码 L234-249；长按 350ms 出面板、位移超 slop 取消 L252-289；上下滑 48dp 翻页 L289 |
| `ImageView.java` | 1380 | 图片查看器：三档缩放 适应/100%/200% `cycleZoom` L596、`minPxForZoom` L679（适应取短边、100% 取长边、200% 取长边两倍）、双线程解码与预取 L693-713、预取邻页 L890、缓存回收 L1023、动作面板 L1050-1060（共享/详情/旋转，厂商通道加导出壁纸）、详情面板五项 L1082-1105、分享 L1108（`ACTION_SEND` 经 `OutProvider`）、旋转 `startRotate` L1143、导出 `startExport` L1211、左右滑翻页 L515 |
| `PickPage.java` | 274 | 选择器页面：目录与文件行绘制 L139-180、时间与大小格式化 L268 |
| `Ink.java` | 95 | 绘制工具：四档灰与纸白、`fill` L31、`stroke` L41、`text` L57（内部关闭抗锯齿与抖动）、`typeface` L70 |
| `Hit.java` | 37 | 命中矩形模型 |
| `Metrics.java` | 145 | 几何与字号：构造 L77 读 `DisplayMetrics`、`of(Context)` L111、`dp` L137、`sp` L142；4 列 4 行、每页 16 项等常量见 `CONTRACT.md` L71-84 |

### 3.4 设计文档中尚未编码的部分

`docs\16` 列出的裁剪、镜像、五档黑白滤镜、操作栈撤销、相册分桶、MediaStore 索引、编辑导出（L334-406、L304-310、L518-522）在源码中没有对应类：目录内不存在 `CropView`、`Editor`、`Gray`、`EditExport`、`MediaIndex`、`GalleryActivity`。文件操作 `Ops` 有实现但无界面调用；缩略图脏表有接口但无登记方；未使用 MediaStore；未接管 `ACTION_EDIT`；未调用 `SET_WALLPAPER`（检索 `Ops.`、`Thumbs.registerDirty`、`MediaStore`、`ACTION_EDIT`、`SET_WALLPAPER` 仅命中 `Wallpaper.java` L35 注释行）。`CONTRACT.md` L209-221 亦将部分能力标为未接入。

核实结果：墨盘当前可实现的能力上限为「目录浏览（网格与列表两种视图、四档排序与升降序）、缩略图、只读查看器（翻页、三档缩放、详情、分享、旋转另存副本）、对外只读选择器、厂商通道壁纸导出」。图片编辑只有旋转，无裁剪、镜像、滤镜；无相册；无文件操作界面。

## 四、能力对照

| 能力 | 十月桌面 | 墨盘 | 关系 |
| --- | --- | --- | --- |
| 目录浏览与分页 | `FilesScreen.kt` L59-137、L290-334，每页 9 项 | `GridPage`/`ListPage`/`PagerView`，两种视图 | 重复建设，墨盘多网格视图与缩略图 |
| 文件操作 | `FileLibrary.kt` L169-232 五项齐全 | `CONTRACT.md` L15 明确 P0 不做；`Ops.java` 无界面 | 十月桌面更完整 |
| 缩略图 | 无 | `Thumbs.java` 全类 | 墨盘独有 |
| 图片查看器 | 无 | `ImageView.java` 1380 行 | 墨盘独有 |
| 三档缩放 | 无 | `ImageView.cycleZoom` L596 | 墨盘独有 |
| 图片编辑 | 无 | 仅旋转另存（`Edits.rotate90` L57） | 两侧均缺裁剪、镜像、滤镜 |
| 相册 | 无 | 无（`docs\16` L304-310 属设计） | 两侧均缺 |
| 系统壁纸 | 无 | 无（不申请 `SET_WALLPAPER`，`Wallpaper.java` L35） | 两侧均缺 |
| 厂商壁纸通道 | 无 | `Wallpaper.exportForWetao` L326、`AssistService` | 墨盘独有，仅 XTEINK 设备 |
| 对外选择器 | `HostFilePicker` 走系统文档选择器 | `PickActivity` 自绘选择器 | 重复，墨盘覆盖 GET_CONTENT/OPEN_DOCUMENT，十月桌面另有 save 与目录选择 |
| 外部 VIEW/EDIT 接管 | 无（`FileLibrary.openIntent` 只作发起方 L248） | 接管 VIEW（manifest L55-65），未接管 EDIT | 互补 |
| EXIF 方向归一 | 无 | `ImageDecode.orientationOf` L281、`applyOrientation` L334 | 墨盘独有 |
| 解码内存保护 | 无堆预算判断 | `heapAllows` L426、8000 万像素上限 L38 | 墨盘独有 |

按重复行数计：墨盘浏览与基础绘制层 `GridPage` 814 + `ListPage` 819 + `PagerView` 318 + `Ink` 95 + `Hit` 37 + `Metrics` 145 = 2228 行，与 `FilesScreen` 467 行加 `FileLibrary` 419 行在功能上重叠，重叠部分约 2000 行。

## 五、集成方式候选

### 5.1 候选甲：整包并入 `feature:content`

改造点：Java 源文件复制进 `feature/content/src/main/java/`；包名改为 `com.shuiyu.octoberrain.feature.content.*`；`Ink`/`Metrics`/`Prefs` 改为 `InkColors`/`DeviceProfile`/DataStore；四个 Activity 改为 `Screen`（`Contracts.kt` L268-283 的 `createView`）；`OutProvider` 改为模块内 FileProvider 或删除（authority 冲突见 §6.1）；资源约 190 条字符串迁入 `feature/content/src/main/res/`。

代码量：引入 9320 行；Activity 壳 1882 行中约 700 行装配逻辑需重写为 Screen；改写 2200-3400 行；新增桥接 200-400 行。模块由约 6100 行增至约 15400 行。

构建代价：AGP 8.7.3 支持同模块 Java 与 Kotlin 混编，Java 17 即可编译（`gradle/libs.versions.toml` L2-9、`app/build.gradle.kts` L30-35），无需新增依赖；资源合并与 authority 改名是主要工作量。

能力上限：同进程，可嵌入负一屏与桌面组件，共享 `Thumbs` 缓存与 `RefreshScheduler`，可替换 `HostFilePicker` 的 `pick`。代价是文件页与相册、阅读页混在同一模块，与 `docs/03-target-architecture.md` L48 的职责划分不符。

### 5.2 候选乙：新建 `feature:files` 模块源码依赖

改造点：新增 `feature/files` 模块（`build.gradle.kts`、`AndroidManifest.xml`、`FilesModule`），源码与资源迁入；`app` 侧在 `OctoberRainApp.kt` L86-92 的模块名单增加一行（反射加载 `LauncherModule`）；`Contracts.kt` 的 `Destination` 增加图片与相册项（现有枚举 L147-243）；`FilesScreen` 增加入口按钮。

代码量：同候选甲，另加模块骨架与注册 150-250 行、Screen 包装 300-500 行。

构建代价：新增一个 Gradle 模块，依赖 `core:common`、`core:render`、`core:device`，符合依赖方向；不引入新第三方库。

能力上限：与候选甲相同（同进程、可嵌入负一屏、共享缓存与刷新），模块边界清晰，便于与 WereadMoji 并入时隔离。

### 5.3 候选丙：独立 APK，经 Intent 与 ContentProvider 互调

改造点：十月桌面侧增加外部入口 80-200 行（`HostFilePicker` 或新增 `MopanBridge` 转向墨盘 `PickActivity`；`ImageWidget.chooseImage` 改为调用外部选择器；`LauncherRouter` 增加打开墨盘浏览页）；墨盘侧如需 `ACTION_EDIT` 与设置入口，补充 0-100 行。墨盘已有 exported 组件：`ViewerActivity` 接管 `ACTION_VIEW`（manifest L55-65）、`PickActivity` 接管 GET_CONTENT 与 OPEN_DOCUMENT（L68-85）、`OutProvider` 只读通道（L103-107）。

代码量：十月桌面侧约 100-250 行，墨盘侧 0-100 行。

构建代价：两套构建体系并行（`inkdisk\tools\build.sh` 254 行手工链与 Gradle 13 模块），两个 APK 安装，两套界面规范维护。`docs\16` L129-145 对同类形态的估算是代码重复约 2200 行、安装 4 处。

能力上限：跨进程无法共享 `Thumbs` 的 `LruCache`，无法嵌入负一屏与桌面组件，无法复用 `RefreshScheduler` 与桌面几何；可覆盖查看、选择、分享三类入口。缩略图与图片缓存需各自维护。

### 5.4 候选丁：选件移植（新模块内只接查看器与缩略图）

改造点：新建 `feature:files`，只迁 `ImageDecode`、`Thumbs`、`PagerView`、`ImageView`、`GridPage`/`ListPage`、`FileItem`/`Sort`/`PathModel`/`Storage`/`Prefs`、`Ink`/`Hit`/`Metrics`（合计 5263 行），文件页沿用 `FileLibrary` 与 `FilesScreen`；编辑与相册按 `docs\16` 在模块内新建，不复用墨盘不存在的能力。

代码量：引入 5263 行，适配改写 1500-2000 行（几何、偏好、颜色、包名、资源），模块骨架与包装 500-800 行。

构建代价：新增一个模块，无新依赖；不需要迁移墨盘四个 Activity，只需要把 `ImageView` 包装为 `Screen` 的 View。

能力上限：查看器、缩略图、浏览三种视图与外部选择器；无编辑与相册（按阶段二、三补齐）。文件页与文件操作保留十月桌面更完整的语义。

### 5.5 比较与建议

| 维度 | 甲 | 乙 | 丙 | 丁 |
| --- | --- | --- | --- | --- |
| 引入代码 | 9320 | 9320 | 100-350 | 5263 |
| 同期改写 | 2200-3400 | 2400-3600 | 0 | 1500-2000 |
| 新模块/组件 | 0 | 1 | 0 | 1 |
| 进程内共享缓存 | 是 | 是 | 否 | 是 |
| 嵌入负一屏 | 是 | 是 | 否 | 是 |
| 模块职责 | 混入 content | 清晰 | 分离 | 清晰 |
| 需改名与迁移资源 | 是 | 是 | 否 | 是 |

建议采用候选丁作为第一阶段形态，目标形态按候选乙扩展：`feature:files` 独立模块，先只接查看器与缩略图，文件页保留现有实现，编辑与相册按阶段新建。判断依据三条：

1. 文件页已具备重命名、复制、移动、删除、新建目录（`FileLibrary.kt` L169-232），墨盘 P0 明确不做这些（`CONTRACT.md` L15）且 `Ops` 无界面调用，整包并入会重复约 2000 行浏览与绘制代码。
2. 编辑与相册在墨盘源码中不存在，`docs\16` L518-522 将编辑器列为 P1、相册列为 P1，移植量只涉及现有 5263 行绘图与解码代码。
3. 同进程是嵌入负一屏与桌面图片组件的前提（`BookshelfNegativeModule.kt` 507 行现有结构，检索无文件区），候选丙无法满足这一条。

候选丙保留作为不合并时的替代，适用于「先尽快用上查看器与选图」的短周期安排。

## 六、技术衔接点

### 6.1 包名、资源与 authority

墨盘包名 `com.inkread.inkdisk`（墨盘 manifest L4），资源主题 `InkTheme`（墨盘 manifest L27-33），`OutProvider` authority `com.inkread.inkdisk.files`（墨盘 manifest L103-107）。十月桌面要求自有包名与资源（`docs/03-target-architecture.md` L171-173），已有 FileProvider authority `${applicationId}.files`（`app/src/main/AndroidManifest.xml` L90-98）。并入时 authority 必须改名，`R` 资源引用全部改到新模块，主题改用十月桌面设计令牌；`Ink` 的四档灰（`CONTRACT.md` L54-66）与 `InkColors` 需映射，避免同色不同值。

### 6.2 文件访问模型

两侧均采用全文件访问：墨盘声明 `MANAGE_EXTERNAL_STORAGE`（墨盘 manifest L7）并以 `java.io.File` 直读（`Storage.java` L36-134）；十月桌面同样声明（十月桌面 manifest L29）并以 `File` 直读（`FileLibrary.kt` L100）。并入后不需要引入 SAF 树授权，`HostFilePicker` 仅继续服务系统选择场景。墨盘 `requestLegacyExternalStorage`（墨盘 manifest L31）在 targetSdk 35 下不再生效，独立运行时依赖的 legacy 行为需重测（待确认）。SAF 与全文件访问并存时，`/sdcard/Android/data` 的不可读边界按 `docs\16` L512 保持提示，不做绕过。

### 6.3 Uri 授权

墨盘选择器只做临时授权：`finishWith` L248-263 设置 `FLAG_GRANT_READ_URI_PERMISSION` 并 `grantUriPermission`，未调用 `takePersistableUriPermission`；`CONTRACT.md` L182-188 要求调用方自行持久化，实现与约定不一致（待确认是否补齐）。十月桌面 `HostFilePicker.onActivityResult` L94-103 同样未持久化，跨应用复用时每次需重新选择。并入同进程后，内部页面可用 `File` 直读，Uri 授权只影响对外分享与外部应用调用，`OutProvider` 的只读语义（L177-191）可以保留。

### 6.4 缩略图缓存与失效

墨盘为纯内存 `LruCache`：上限取 6MB 与堆上限/16 的较小值（`Thumbs.java` L61-63），目标像素由调用方按显示尺寸的 1.5 倍给出（`docs\16` L290），失败表记忆（`isFailed` L283），脏表接口存在但无登记方（`registerDirty` L350）。十月桌面无缩略图缓存，只有 `BitmapPipe` 采样。并入后建议：缓存放 `context.cacheDir/thumbs`，键为 `hash(绝对位置 + 修改时间 + 目标像素)`；失效经 `RefreshScheduler.notifyContentChanged`（L74）与文件写操作后的主动清理触发；查看器同时只保留当前图与预取邻图（墨盘 `ImageView` L890 已有预取逻辑，可以沿用）。

### 6.5 解码与抖动

墨盘 `ImageDecode` 与十月桌面 `BitmapPipe` 功能重叠，前者多出 EXIF 归一（L281-334）、8000 万像素上限（L38）、堆预算判断（L426）、Uri 解码（L152）。建议把 `ImageDecode` 移入 `core:render` 作为统一解码入口，`BitmapPipe` 改为委托，避免两套采样策略。抖动归属需要决定：墨盘把二值化交给系统（`CONTRACT.md` L150-157，`docs\16` L452-454），十月桌面 `Ditherer` 已实现两种量化但全工程无调用方（`core/render/Ditherer.kt` L12 为唯一命中）。图片查看器是否应用 `Ditherer` 影响观感与耗时，属于需要提前确定的事项。

### 6.6 刷新策略

墨盘每个自绘页面每次绘制先铺纸白（`PagerView.java` L224），应用不主动触发整屏闪烁（`CONTRACT.md` L150-157）。十月桌面有局部合并与整屏闪烁两档（`RefreshScheduler.kt` L74、L87），EPD 节点不可达（`ADR-008` L46-53）。并入后查看器翻页只需 `notifyContentChanged`，整屏闪烁交给现有定时器（L21 默认 10 分钟）与手动入口，避免每页一次全屏闪烁。

### 6.7 桌面组件与入口

- 壁纸：十月桌面为自绘层（`WallpaperView.kt` L27-69），无系统壁纸调用；墨盘的厂商导出（`Wallpaper.exportForWetao` L326）只对 XTEINK 设备有意义。两条通道的取舍需要决定。
- 图片组件：`ImageWidget` 现有选图、复制、解码三条行为（L82-153），可改为调用模块内查看器与选择器，省去复制到内部目录的步骤。
- 文件选择器：`FilePicker` 接口含 `pick`、`save`、`pickDirectory`（`Contracts.kt` L602-616），墨盘选择器只有读（`PickActivity`；`OutProvider` 写操作抛异常 L177-191），无法覆盖 `save` 与目录选择，替换范围只限 `pick`。
- 负一屏：`BookshelfNegativeModule.kt` 现有 507 行只有书目条目（L419-445），文件区需要新增模块或扩展该模块，并复用 `Destination.Files`（`Contracts.kt` L222）。
- 入口：`Destination` 需增加图片查看与相册两项（现枚举 L147-243），`ScreenRegistry` 按注册顺序创建（L292-307）。

## 七、分期与工作量

| 阶段 | 内容 | 新增文件 | 移植/引入 | 净新增与改写 |
| --- | --- | --- | --- | --- |
| 一 只读查看器与缩略图 | `feature:files` 骨架、`PhotoScreen`、查看器、缩略图、列表与网格、入口 | `build.gradle.kts` 30、`AndroidManifest.xml` 15、`FilesModule` 60、`PhotoScreen` 150、`ThumbCache` 200、`FilesScreen` 入口 40、`Contracts` 增项 10 | `ImageDecode` 454、`Thumbs` 401、`PagerView` 318、`ImageView` 1380、`GridPage` 814、`ListPage` 819、`FileItem` 72、`Sort` 77、`PathModel` 341、`Storage` 142、`Prefs` 168、`Ink` 95、`Hit` 37、`Metrics` 145，合计 5263 | 改写 1500-2000（几何、颜色、偏好、包名、资源）；净新增 505-700 |
| 二 编辑与保存 | 编辑页、裁剪、滤镜、导出、入口三处（`docs\16` L326、L334-406） | `EditScreen` 400、`CropView` 400、`Gray` 180、`EditExport` 180、查看器动作面板扩展 80 | `Edits` 245 | 净新增 1240-1500；改写 60-100 |
| 三 相册与壁纸 | MediaStore 索引与分桶、相册页、壁纸通道 | `MediaIndex` 350、`AlbumScreen` 250、壁纸设置 100 | 若走厂商通道再引入 `Wallpaper` 458、`AssistService` 545（仅 XTEINK） | 净新增 700-900；厂商通道另需改写 300 |
| 四 桌面联动 | 图片组件改造、负一屏文件区、选择器扩展、缓存共享、设置项 | `ImageWidget` 改造 120、`FilesNegativeModule` 400、`HostFilePicker` 扩展 200、设置项 120、缓存与刷新 120 | — | 净新增 700-1100 |

合计：净新增 3145-4200 行，改写 1560-2100 行，移植 5508 行（不含厂商通道）。若把厂商通道计入，另加移植 1003 行与改写约 300 行。对照 `docs\16` L518-522 对独立应用 P0 与 P1 的合计估算 4600-5400 行，并入形态的总改动量（净新增加改写）约 4700-6300 行，与独立应用估算处于同一数量级；移植部分属于复制与适配，不构成新设计。

工作量判断：阶段一与阶段二是主体，阶段三依赖 MediaStore 与设备实测（`docs\16` L545-552 列出的未知项：32 位设备解码与滤镜耗时、MediaStore 删除同步、中文二值可读性），阶段四依赖桌面侧组件改造。按行数与阶段依赖，整体工作量约相当于 `docs\16` P0 加 P1 的 1.0-1.2 倍。

## 八、主要障碍

1. 几何与设计令牌。`Metrics.of` 读取 `DisplayMetrics`（`Metrics.java` L111），十月桌面要求 feature 层经 `DeviceProfile` 取尺寸（`docs\03-target-architecture.md` L76-89）。墨盘另有固定 4 列 4 行、每页 16 项的常量（`CONTRACT.md` L71-84），第二台 824×1648 与 S4 480×800 的 dp 基准见 `docs\16` L245-264、L462-471；第三台宽度超出 350-430dp 时需按 `Build.MODEL` 覆盖（`docs\16` L473-475）。适配触及 ui 全部 8 类与 shell 5 类（四个 Activity 与 `SetupView`）。
2. Java 与 Kotlin 混编。AGP 8.7.3 与 Java 17 支持同模块混编（`gradle/libs.versions.toml` L2-9、`app/build.gradle.kts` L30-35），编译层面无障碍；主要成本在资源与主题迁移，以及墨盘「无 androidx、无 Kotlin、无第三方依赖」的自检约束（`CONTRACT.md` L192-197）在并入后失效，需要重新界定模块约束。
3. 两套构建体系。墨盘用 `inkdisk\tools\build.sh` 的 aapt2/javac/d8/zipalign/apksigner 链（L1-36），十月桌面用 13 模块 Gradle。若墨盘独立 APK 保留，界面代码双份维护（`docs\16` L554 已指出复制而非共用）；若停用，外部调用方需改（待确认）。
4. minSdk 与 targetSdk 差异。墨盘 minSdk 23、targetSdk 30（manifest L15-17），十月桌面 minSdk 26、targetSdk 35（`gradle/libs.versions.toml` L5-6）。墨盘的 `requestLegacy`（`Storage.java` L73）与 Uri EXIF 的 API 24 分支（`ImageDecode.java` L152、L296）在 26 下成为冗余但无害；targetSdk 35 下 legacy 存储行为需要实测。
5. 权限与渠道。两侧都声明 `MANAGE_EXTERNAL_STORAGE`（墨盘 manifest L7、十月桌面 manifest L29），上架渠道对该权限的限制需要确认（待确认）。
6. 编辑与相册没有现成实现。`docs\16` L518-522 将编辑器列为 P1、相册列为 P1，均需新建；`docs\16` L545-552 的未知项尚未实测，32 位设备的解码与滤镜耗时可能改变设计（例如预览尺寸与滤镜档位）。
7. 来源与许可。墨盘工程目录内未检索到 LICENSE 文件（待确认）；`README.md` L3 说明工作区「独立于微读墨记开发」，L5 说明 `docs/` 前六份文档原样复制自微读墨记，代码为独立工程。并入前需要明确授权与署名方式。
8. 与 WereadMoji 同时并入的边界。文件与图片能力应入 `feature:files`，阅读能力入 `feature:content`，共享件（解码、抖动、刷新）下沉 `core:render`，避免 feature 之间互相引用（`docs\03-target-architecture.md` L48）。

## 九、需要先决定的事项

1. 目标设备集合。S4（480×800，density 219）与第二台（824×1648，density 320）是否都在支持范围，决定 `Metrics` 适配方式与两套真值表（`docs\16` L245-264、L462-471）。
2. 墨盘独立 APK 是否保留。保留则双份界面维护；停用则微读墨记等外部调用方需要改（待确认调用方清单）。
3. 文件访问模型。继续 `MANAGE_EXTERNAL_STORAGE` 全文件访问，或改为 SAF 树授权；前者已有两处声明，后者需要重写 `FileLibrary` 的目录模型。
4. 壁纸通道。系统 `SET_WALLPAPER`（两侧均未申请，墨盘 `Wallpaper.java` L35 明确不做）或厂商导出加无障碍（`Wallpaper.exportForWetao` L326、`AssistService`）。
5. 抖动归属。沿用系统抖动，或在查看器与缩略图应用 `Ditherer`（`core/render/Ditherer.kt` L22、L85，当前无调用方）。
6. 编辑范围。先只做旋转另存（`Edits.rotate90` L57 已有），或按 `docs\16` L366-398 一次做到裁剪加五档滤镜。
7. 文件操作归属。保留 `FileLibrary` 的语义，或改用 `Ops` 的 `sameContent`/`uniqueName`（`Ops.java` L41、L93）以复用内容比对与重名规则。
8. 包名与资源迁移后墨盘原工程的处置。同步改造、冻结，或抽出共享子集双向同步。

## 十、索引

| 简称 | 完整位置 |
| --- | --- |
| `CONTRACT.md` | `D:\DSHStation\MoPan\inkdisk\CONTRACT.md` |
| manifest（墨盘） | `D:\DSHStation\MoPan\inkdisk\app\src\main\AndroidManifest.xml` |
| `Edits` 等 core 类 | `D:\DSHStation\MoPan\inkdisk\app\src\main\java\com\inkread\inkdisk\core\*.java` |
| shell 类 | `D:\DSHStation\MoPan\inkdisk\app\src\main\java\com\inkread\inkdisk\shell\*.java` |
| ui 类 | `D:\DSHStation\MoPan\inkdisk\app\src\main\java\com\inkread\inkdisk\ui\*.java` |
| `docs\16` | `D:\DSHStation\MoPan\docs\16_墨盘_文件管理器与相册_评估与设计.md` |
| `docs\03`（墨盘） | `D:\DSHStation\MoPan\docs\03_墨水屏UI规范.md` |
| `docs\14` | `D:\DSHStation\MoPan\docs\14_多屏适配评估_卡片与墨台.md` |
| `ADR-008` | `D:\DSHStation\MoPan\docs\DECISIONS\ADR-008_EPD接口.md` |
| `FileLibrary.kt` | `D:\DSHStation\OctoberLauncher\feature\content\src\main\java\com\shuiyu\octoberrain\feature\content\data\FileLibrary.kt` |
| `FilesScreen.kt` | `D:\DSHStation\OctoberLauncher\feature\content\src\main\java\com\shuiyu\octoberrain\feature\content\ui\FilesScreen.kt` |
| `ContentPaths.kt` | `D:\DSHStation\OctoberLauncher\feature\content\src\main\java\com\shuiyu\octoberrain\feature\content\data\ContentPaths.kt` |
| `CoverResolver.kt` | `D:\DSHStation\OctoberLauncher\feature\content\src\main\java\com\shuiyu\octoberrain\feature\content\data\CoverResolver.kt` |
| `EpubCover.kt` | `D:\DSHStation\OctoberLauncher\feature\content\src\main\java\com\shuiyu\octoberrain\feature\content\data\EpubCover.kt` |
| `BookshelfNegativeModule.kt` | `D:\DSHStation\OctoberLauncher\feature\content\src\main\java\com\shuiyu\octoberrain\feature\content\ui\BookshelfNegativeModule.kt` |
| `HostFilePicker.kt` | `D:\DSHStation\OctoberLauncher\app\src\main\java\com\shuiyu\octoberrain\HostFilePicker.kt` |
| `WallpaperView.kt` | `D:\DSHStation\OctoberLauncher\app\src\main\java\com\shuiyu\octoberrain\WallpaperView.kt` |
| `ImageWidget.kt` | `D:\DSHStation\OctoberLauncher\feature\widgets\src\main\java\com\shuiyu\octoberrain\feature\widgets\ImageWidget.kt` |
| `ImageDirectoryStore.kt` | `D:\DSHStation\OctoberLauncher\feature\settings\src\main\java\com\shuiyu\octoberrain\feature\settings\ImageDirectoryStore.kt` |
| `PermissionCenter.kt` | `D:\DSHStation\OctoberLauncher\core\device\src\main\java\com\shuiyu\octoberrain\core\device\PermissionCenter.kt` |
| `Contracts.kt` | `D:\DSHStation\OctoberLauncher\core\common\src\main\java\com\shuiyu\octoberrain\core\common\Contracts.kt` |
| `BitmapPipe.kt`、`Ditherer.kt`、`InkCanvas.kt`、`RefreshScheduler.kt` | `D:\DSHStation\OctoberLauncher\core\render\src\main\java\com\shuiyu\octoberrain\core\render\` |
| `docs\03`（十月桌面） | `D:\DSHStation\OctoberLauncher\docs\03-target-architecture.md` |

## 附：待确认事项

- 墨盘工程的 LICENSE 与来源声明（工程目录内未检索到）。
- 墨盘独立 APK 的外部调用方清单（微读墨记等）。
- targetSdk 35 下墨盘 legacy 存储分支的实际行为。
- `docs\16` L545-552 列出的设备实测项：32 位设备解码与滤镜耗时、MediaStore 删除同步、中文二值可读性、第二台自带图库对选图入口的抢占。
- 上架渠道对 `MANAGE_EXTERNAL_STORAGE` 的限制。
