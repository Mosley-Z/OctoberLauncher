# 0.9.4 P4 与 P5 只读分析与实施规划

编写者 dev-config，日期 2026-10-20。本文件只做规划，不修改工程源码。依据材料为 `docs/16-墨盘移植决策.md`、墨盘设计文档 `docs/16_墨盘_文件管理器与相册_评估与设计.md`、`docs/19_墨画借鉴_壁纸设置与图片管理_实施方案.md`、墨盘源码 `D:\DSHStation\MoPan\inkdisk\app\src\main\java\com\inkread\inkdisk`、已交付的 `feature/files` 模块、`core/render` 现有解码与抖动能力、宿主壁纸与锁屏实现。

## 一、P4 与 P5 的范围

P4 覆盖 0.9.5 的任务 5.1 与 5.2：图片编辑页与相册页。编辑能力按决策 D5 固定为旋转、镜像、裁剪、五档黑白滤镜与撤销，输出规则按 `docs/16` 第六节，目录 `Pictures/十雨桌面`，文件名 `原名-编-yyyyMMdd-HHmmss.png`，长边 1600 或 2400。

P5 覆盖 0.9.8 任务 8.2 的壁纸部分：桌面壁纸接入现有自绘壁纸层并增加量化档位；新增锁屏密码界面壁纸，复用图片选择与量化管线，按三档量化做观感验收。系统 `SET_WALLPAPER` 与厂商通道不接入，依据决策 D4。

## 二、墨盘类逐项映射

| 墨盘文件 | 行数 | 处理方式 | 目标位置与说明 |
| --- | --- | --- | --- |
| `core/Edits.java` | 218 | 重写 | `feature/files/.../data/EditSession.kt`、`data/EditExport.kt`：编辑状态机、旋转计数、缓存文件、另存命名 |
| `core/ImageDecode.java` | 230 | 复用 | `core/render/.../ImageDecoder.kt` 与 `ImageSizing.kt` 已覆盖尺寸读取、EXIF 方向、采样、像素上限与堆预算 |
| `core/Thumbs.java` | 89 | 复用 | `core/render/.../ThumbCache.kt`、`ThumbIndex.kt`、`ThumbCacheRegistry.kt` 已覆盖内存与磁盘两级缓存、失效通知、四档量化 |
| `core/Ops.java` | 326 | 已移植 | `feature/files/.../data/FileOps.kt` 与 `FileStreams.kt` |
| `core/Sort.java` | 77 | 已移植 | `data/FileSortKey.kt` |
| `core/Prefs.java` | 168 | 已移植并扩展 | `data/FilePrefs.kt` 现有五项偏好；P4 增加旋转计数键 |
| `core/Intents.java` | 56 | 已移植 | `data/FileIntents.kt`，`EXTRA_PATH` 改为 `com.shuiyu.octoberrain.extra.FILE_PATH` |
| `core/OutProvider.java` | 90 | 不移植 | 分享复用宿主 `FileProvider`，authority `${applicationId}.files`，`file_paths.xml` 已加 `files_share` |
| `ui/ImageView.java` | 1380 | 部分复用 | P3 由 dev-core 实现查看器；P4 只消费其动作面板的「编辑」入口，解码与预取循环不重复移植 |
| `ui/EditPanel.java` | 缺失 | 新写 | 墨盘无独立编辑面板源码，编辑交互按设计文档第四节自绘 |
| `ui/GridPage.java` | 378 | 部分复用 | 相册网格沿用 `FilePager` 的 4 列 4 行骨架，单元格内容与命中逻辑在相册页重写 |
| `ui/ListPage.java` | 280 | 部分复用 | 相册分桶列表沿用行式绘制，行高与缩略图取 `FileTokens` |
| `ui/PickPage.java` | 332 | 不移植 | 选图走宿主 `Destination.FilePicker` 与两个入口 Activity |
| `shell/ViewerActivity.java` | 355 | 部分复用 | 外部 Uri 先落缓存副本、显示名查询、64 MiB 上限三条规则并入 P3 查看页；编辑页继承「先归一化再解码」的做法 |
| `shell/PickActivity.java` | 395 | 不移植 | 原因同 `PickPage`；`OutProvider` 的 content 地址回传方式与宿主 `FileProvider` 冲突 |
| `shell/AssistService.java` | 545 | 不移植 | 仅供厂商壁纸通道使用，决策 D4 已排除 |
| `core/Wallpaper.java` | 458 | 不移植 | 以宿主 `WallpaperView` 与 `AppearanceRepositoryImpl` 为基础扩展，见第五节 |

可直接复用的接口：`ImageDecoder`、`ImageSizing`、`Ditherer`、`DitherCore`、`ThumbCache`、`ThumbCacheRegistry`、`FileOps`、`FileStreams`、`FileListing`、`FilePrefs`、`StorageAccess`、`FilePager`、`FileTokens`、`FileMetrics`、`FileIntents`、`SettingsUi.pickFile`。

需要扩展的接口：`FilePrefs` 增加旋转计数键；`FilePager` 增加相册页所需的单元格绘制钩子，或由相册页继承后覆盖 `onDrawContent` 与 `onContentGesture`；`AppearanceRepository` 增加锁屏壁纸三项方法与量化参数。

需要新写的接口：`EditSession` 与 `EditOp`、`CropRect`、`Gray` 滤镜组、`CropOverlay`、`EditView`、`EditScreen`、`EditExport`、`MediaIndex`、`MediaBucket`、`AlbumView`、`AlbumScreen`、`AlbumActions`、`LockWallpaperView`、`WallpaperQuantize`。

## 三、相册与编辑面板的页面结构

编辑页 `EditScreen` 为自绘 View，结构与墨盘 `PagerView` 一致的三段式：

顶栏高 56dp：左侧返回、中部文件名按可用宽度省略、右侧重置。重置回到进入时的状态。

画布：图片按可用区域等比适配居中，四周留 18dp。裁剪框覆盖有效裁剪区，框外叠 `#D8D8D8` 六成透明遮罩，框线 1px 实线，四角与四边共 8 个手柄。手柄视觉 28dp，命中半径按短边 48dp 判定。拖动仅移动框线，松手后整幅重绘。音量键上下微调 1px，长按连续调整。长按画布 400ms 显示原图，松手恢复。

底栏高 52dp，两行操作：第一行比例、旋转、镜像、撤销；第二行滤镜、对比度、完成。两行均按 `FileTokens.rowH` 44dp 排布，按钮间距 8dp。比例面板含自由、1:1、4:3、3:4、16:9、9:16、3:2、2:3。滤镜面板五档横向排布，选中项加内框。导出期间覆盖进度文案，完成后面包屑返回文件页并提示保存路径。

相册页 `AlbumScreen` 同样为自绘 View：

顶栏：返回、标题「相册」、右侧排序入口，排序项为名称、时间、大小、类型，复用 `FileSortKey`。

分桶层：首项「全部」，其余按父目录聚合，行式显示缩略图、目录名、图片数量、修改时间，复用 `FileTokens.rowH`。进入桶后按 4 列 4 行、每页 16 项显示图片，单元格宽高与文件页网格一致，标签显示日期。

底栏：上一页、页码、下一页；图片计数显示在顶栏副标题。

长按图片呼出动作面板，行高 44dp：编辑、删除、分享、信息、在文件管理器中打开。删除走确认对话框并刷新 MediaStore 索引。空相册显示「未找到图片」与权限缺失时的跳转提示。

索引 `MediaIndex` 基于 `MediaStore.Images.Media` 查询 `_ID`、`DATA`、`DATE_MODIFIED`、`WIDTH`、`HEIGHT`、`MIME_TYPE`、`BUCKET_ID`、`BUCKET_DISPLAY_NAME`，首屏只取当前桶的前两页，滚动时增量补齐。索引在启动后异步建立，写文件后通过 `ContentObserver` 标脏并重建。两千张图片的首屏耗时与索引耗时写入验收记录。

## 四、滤镜与裁剪的参数化设计

滤镜统一为纯函数 `Gray.apply(pixels: IntArray, width: Int, height: Int, params: GrayParams): IntArray`，`GrayParams` 含档位、对比度与是否保留 Alpha。五档定义如下：

0 原始灰阶：`Y = (299R + 587G + 114B) / 1000`，输出 0 到 255 连续灰阶。

1 分级抖动：先算 Y，再按 16 级取整 `q = round(Y / 17) * 17`，按 Floyd–Steinberg 分配误差，权重 7/16、3/16、5/16、1/16。

2 高反差：`Y' = clamp((Y - 128) * 1.5 + 128, 0, 255)`，再按 128 阈值二值化。

3 有序抖动：8×8 Bayer 阈值矩阵，每个元素映射到 0 到 255 后与 Y 比较，输出 0 或 255。

4 柔和：`Y' = 255 * (Y / 255) ^ 0.85`，再按 `q = clamp((Y' - 128) * 0.9 + 128, 0, 255)` 输出连续灰阶。

对比度三档作用在滤镜之后，系数 0.85、1.00、1.15，围绕 128 缩放。滤镜与对比度都在预览位图上计算，预览位图长边按 1024 采样，屏幕刷新前只重算受影响的图像数据。

裁剪与旋转统一使用归一化矩形 `CropRect(left, top, right, bottom)`，分量范围 0 到 1，相对当前方向后的位图。旋转 90 度时宽高互换，矩形按 `(1 - top, left, 1 - bottom, right)` 变换；镜像按 `(1 - right, top, 1 - left, bottom)` 变换。最小裁剪边长按像素判定：S4 档 32px，其余档 48px。导出时用同一 `EditOp` 列表在原始分辨率重放，避免预览与成品方向不一致。

操作栈元素为 `EditOp` 密封类型：`Rotate(quarters)`、`Mirror`、`Crop(rect)`、`Filter(level)`、`Contrast(level)`。栈深 20，撤销弹出末项重放，重做栈清空。旋转计数按路径写入 `FilePrefs`，键加 `edited_rotations:` 前缀，供查看器与重新进入编辑页时恢复方向。

导出 `EditExport` 按 `ActivityManager.getMemoryClass()` 选择长边：低于 192MB 取 1600，否则取 2400。解码加运算加编码的峰值预算按每像素 2 字节估算，超过 80MP 直接拒绝并提示图片过大。写入优先 `MediaStore.Images.Media` 的 `RELATIVE_PATH = Pictures/十雨桌面`，失败回退到文件写入后扫描。文件名主名为原名加 `-编-yyyyMMdd-HHmmss`，同名时按 `_2` 到 `_999` 追加，复用 `FileOps.uniqueName` 的后缀规则。

## 五、壁纸双通道的管线改造点

桌面部件的现状：`core/common/src/main/java/com/shuiyu/octoberrain/core/common/Models.kt:175` 的 `AppearanceConfig` 含 `wallpaperPath`、`wallpaperWhiteOverlay`、`wallpaperDim`；`app/src/main/java/com/shuiyu/octoberrain/WallpaperView.kt:70` 按配置变化在 IO 线程调用 `loadWallpaper(width, height)`，`AppearanceRepositoryImpl.kt:68` 采样解码后填白并拉伸，未做量化；设置入口在 `feature/settings/.../AppearanceSpecs.kt:72` 与 `AppearanceScreen.kt:38`。

改造点一，配置字段。`AppearanceConfig` 增加 `lockWallpaperPath: String? = null`、`wallpaperQuantize: Int = 0`、`lockWallpaperQuantize: Int = 2`。量化档位 0 为关闭，1 为两级，2 为四级，3 为八级，对应 `DitherCore.normalizeLevels` 支持的范围。字段带默认值，旧配置与 `ConfigMigration` 迁移链不需要新增版本。

改造点二，接口与实现。`core/common/src/main/java/com/shuiyu/octoberrain/core/common/Contracts.kt:729` 的 `AppearanceRepository` 增加 `setLockWallpaper(uri)`、`clearLockWallpaper()`、`loadLockWallpaper(width, height, levels)`，并给 `loadWallpaper` 增加量化档位参数，保留旧签名作为默认参数调用。`AppearanceRepositoryImpl` 把 `decodeSampled` 拆成 `decodeWallpaper(path, width, height, levels)`：采样解码、白底填充、按档位调用 `Ditherer.dither(bitmap, levels)`，锁屏壁纸存 `files/wallpaper/lock_*.png`，替换时删除旧文件。

改造点三，桌面渲染。`WallpaperView` 读取 `wallpaperQuantize` 并参与重载判定，解码与量化在 IO 线程完成，白色压层与变暗层在量化之后叠加，保持现有语义。

改造点四，锁屏渲染。新增 `app/src/main/java/com/shuiyu/octoberrain/LockWallpaperView.kt`，在 `MainActivity.kt:191` 之后的 `lockHolder` 内添加，位于 `LockOverlayView` 之下。`LockOverlayView` 的背景由纯纸色改为带透明度的纸色压层，透明度建议 0xB0，保证键盘与提示可读。`MainActivity.showLock()` 依次加入壁纸层与覆盖层，`hideLock()` 一并移除。量化后的位图由 `loadLockWallpaper` 返回，解码线程与桌面壁纸一致。

改造点五，设置入口。`AppearanceSpecs` 增加四项：选择锁屏壁纸、清除锁屏壁纸、桌面壁纸量化、锁屏壁纸量化。量化项用 `choiceAppearance` 三选项「关闭、四级、八级」，锁屏默认四级。`AppearanceScreen` 增加对应动作，选择流程复用 `SettingsUi.pickFile`，成功后 `ThumbCacheRegistry.invalidate` 旧文件、`RefreshScheduler.notifyContentChanged` 与 `requestFullRefresh`，与现有壁纸动作一致。

改造点六，备份与主题。壁纸路径写进配置，导出备份带绝对路径；跨设备导入后路径失效，现有 `WallpaperView` 加载失败时静默保持纯白。P5 只记录该行为，不做路径清洗，避免与凭据剔除逻辑交叉。

## 六、预估行数与拆分方案

P4 生产代码预估 2,800 至 3,300 行，测试 700 至 900 行，按四个子任务拆分，单文件不超过 600 行：

P4-A 编辑核心：`data/EditSession.kt` 约 200 行，`data/CropMath.kt` 约 140 行，`data/EditExport.kt` 约 220 行，`ui/Gray.kt` 约 280 行，`ui/GrayKernels.kt` 约 140 行。

P4-B 编辑界面：`ui/CropOverlay.kt` 约 300 行，`ui/EditView.kt` 约 460 行，`ui/EditScreen.kt` 约 130 行，`ui/EditPanel.kt` 约 260 行。

P4-C 相册：`data/MediaIndex.kt` 约 260 行，`data/MediaBucket.kt` 约 70 行，`ui/AlbumView.kt` 约 430 行，`ui/AlbumScreen.kt` 约 180 行，`ui/AlbumActions.kt` 约 180 行。

P4-D 测试与验收：`EditSessionTest` 约 160 行，`CropMathTest` 约 130 行，`GrayTest` 约 190 行，`EditExportTest` 约 110 行，`MediaIndexTest` 约 120 行，`AlbumPagingTest` 约 100 行；真机按三档设备记录编辑与相册耗时。

P5 生产代码预估 500 至 600 行，测试约 200 行：

`core/common/.../Models.kt` 增加三个字段，约 20 行；`Contracts.kt` 增加壁纸方法，约 18 行；`AppearanceRepositoryImpl.kt` 重构解码与量化，约 130 行；`ui/WallpaperQuantize.kt` 约 80 行；`WallpaperView.kt` 约 30 行；`LockWallpaperView.kt` 约 110 行；`LockOverlayView.kt` 背景调整约 15 行；`MainActivity.kt` 约 20 行；`LockGateImpl.kt` 约 12 行；`AppearanceSpecs.kt` 约 90 行；`AppearanceScreen.kt` 约 60 行。测试集中在 `WallpaperQuantizeTest`，覆盖三档映射与边界像素。

## 七、依赖与并行边界

P4 依赖 P3 交付的查看器与路由：`Destination.PhotoViewer(paths, index)`、`Destination.PhotoEditor(path)`，以及 `feature/files` 的 `FilesModule` 注册扩展。P4 只在编辑动作处调用路由，不修改 P3 的查看器视图文件。

P5 依赖 P4 的图片写入目录与 `MediaStore` 刷新约定，避免锁屏壁纸与编辑输出互相覆盖；锁屏壁纸放在应用私有目录 `files/wallpaper`，编辑输出放在公共图片目录，两者不共用路径。

P4 与 P5 写作用域需与 dev-ui、dev-core 分离：P4 只写 `feature/files` 的编辑与相册文件及对应测试；P5 写 `feature/settings` 的外观声明与页面、`core/common` 配置字段、`core/data` 外观实现、`app` 的锁屏壁纸视图。`core/common/Models.kt` 与 `Contracts.kt` 的改动需要与 dev-core 的阅读数据、组件改动按文件内区段划分，动手前互相通报行号范围。
