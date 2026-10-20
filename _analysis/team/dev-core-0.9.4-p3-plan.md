# 0.9.4 P3 查看器、外部入口与选图：只读准备与实施设计

本文件为 task-23 的只读准备结论，覆盖墨盘 `ImageView.java` 的类与方法映射、缩放状态机、换图与预取、动作面板与详情、路由与页面装配、旋转另存、外部入口与分享、文件拆分与行数预估，以及与 P2 浏览页的接口边界。

阅读材料：`docs/16-墨盘移植决策.md` 全文；墨盘 `ui/ImageView.java`（1380 行）、`core/Edits.java`（245 行）、`shell/ViewerActivity.java`（355 行）、`shell/PickActivity.java`（395 行）、`shell/AssistService.java`（545 行）；`feature/files` 当前源码与测试；`core/render` 的 `ImageDecoder`、`InkCanvas`、`Ditherer`、`ThumbCacheRegistry`、`RefreshScheduler`；`core/common` 的 `Destination`、`Screen`、`Router`、`VolumeKeySink`、`FilePicker`；`app` 的 `MainActivity` 页面栈、`OctoberRainApp` 装配流程、`AndroidManifest.xml` 与 `res/xml/file_paths.xml`。

## 一、范围与批次

`docs/15` 任务 4.3 要求移植查看器并保留三档缩放、邻图预取、分享、旋转另存，刷新改走 `RefreshScheduler`，`Edits.rotate90` 移植为 `Edits.kt`；任务 4.4 的选图部分由外部入口 `PickEntryActivity` 承担。`docs/16` 第二、三、六、七、八节给出导出组件、分享 authority、编辑输出、壁纸能力删除与权限的裁决。`AssistService` 属于壁纸自动设置，按第七节不移植。

本轮交付：查看器 `PhotoViewer`、查看页 `PhotoScreen`、路由 `Destination.PhotoViewer`、外部图片入口 `PhotoEntryActivity`、选图入口 `PickEntryActivity`、旋转另存 `data/Edits.kt`、分享、纯 JVM 测试与报告。相册、编辑器、选择器内部化属 P4，不在本轮。

## 二、ImageView 类与方法映射

`ImageView` 继承墨盘 `PagerView`，Kotlin 侧不保留 Pager 基类，改为自绘 `View` 加 `Screen` 生命周期；页码语义由「图片集合下标」承载，每页一张。映射如下。

| 墨盘成员 | Kotlin 目标 | 说明 |
| --- | --- | --- |
| 构造 `ImageView(Context, Metrics, String[], int)` | `PhotoViewer(context, tokens, paths, startIndex, onBack)` | 参数改为路径数组与起始下标；返回按钮走 `onBack` |
| `onDrawContent` 位图绘制 | `PhotoViewer.onDraw` + `drawBitmap` | 按档位缩放并居中，超出内容区裁剪 |
| `onDrawTopBar` | `ViewerChrome.drawTopBar` | 返回按钮、文件名省略、页码「当前/总数」 |
| `onDrawOverlay` | `ViewerChrome.drawPanel` | 动作面板与详情面板，含分隔线与提示行 |
| `layoutRows`、`panelRows` | `ViewerChrome.layout` | 行高 `max(dp(44), rowH)`，面板贴内容区底边 |
| `detailLabels`、`detailValues` | `ViewerChrome.detailRows` | 五行：尺寸、大小、时间、路径、格式 |
| `actionLabel`、`onAction`、`onTapContent` | `PhotoViewer.onAction`、`onTap` | 面板命中优先于缩放循环 |
| `onLongPressContent` | `PhotoViewer.onLongPress` | 打开动作面板 |
| `onTouchEvent` | `PhotoViewer.onTouchEvent` | 点击、长按、左右滑阈值 48 dp |
| `cycleZoom`、`minPxForZoom`、`fitMinPx` | `ViewerState.cycleZoom`、`ViewerState.targetPx` | 纯逻辑，进单元测试 |
| `load` | `PhotoViewer.load` + `ViewerState` | 递增代际、命中缓存直接显示、否则投递请求 |
| `startThreads`、`runDecodeLoop`、`runPrefetchLoop` | `ViewerDecoder` | 单解码线程加单预取线程，均为守护线程 |
| `postDecoded`、`postPrefetched` | `ViewerDecoder` 回调 | 主线程校验代际、页码与档位 |
| `prefetchNeighbors`、`addPrefetchLocked` | `ViewerDecoder.prefetchNeighbors` | 前一张与后一张，跳过越界与已缓存 |
| `cachePut`、`cacheTake`、`hasCached`、`recycle` | `ViewerDecoder` 内部两槽缓存 | 容量 2，淘汰离当前下标最远项 |
| `doShare` | `ViewerShare.share` | `ACTION_SEND` 加临时读权限 |
| `startRotate`、`finishRotate` | `ViewerRotate.start` + `Edits.rotate90` | 代际校验，结果提示 |
| `startExport`、`finishExport`、`finishAssist` | 不移植 | 第七节删除壁纸导出与自动设置 |
| `ellipsize`、`formatTime` | `ViewerText` | 纯函数，进单元测试 |
| `onDetachedFromWindow` | `PhotoViewer.detach` | 置关闭标记、递增代际、中断线程、回收位图 |
| `setOnBackListener`、`isPanelVisible`、`closePanel` | `PhotoViewer` 同名方法 | `Screen.onBack` 先关面板再返回 |

尺寸与命中区集中到 `ViewerMetrics`：顶栏高 64 dp，返回按钮与页码区的可点击范围不小于 44 dp，内容区为顶栏以下、整屏以上；面板行高 44 dp，动作面板 4 行加 1 行提示，详情面板固定 5 行。

## 三、三档缩放状态机

任务文字给出「适配、填充、原尺寸」，墨盘原实现为「适应、100%、200%」。两者并不等价，按下列定义固定，并在报告中记录理由：

| 档位 | 语义 | 目标像素 |
| --- | --- | --- |
| `FIT` 适配 | 等比缩放，整图可见，留白居中 | 内容区短边 |
| `FILL` 填充 | 等比放大到覆盖内容区，溢出部分裁剪，对应墨盘 `ZOOM_100` 的长边贴合内容区长边 | 内容区长边 |
| `ORIGINAL` 原尺寸 | 按原始像素 1:1 绘制，不缩放 | 内容区长边，且不低于原始长边的一半 |

循环顺序 `FIT → FILL → ORIGINAL → FIT`，动作入口为单击内容区；面板打开时单击只关闭面板。`ViewerState` 保存 `zoom`、`index`、`paths` 与 `tone`，暴露 `cycleZoom()`、`targetPx(contentWidth, contentHeight, imageSize)`、`next()`、`previous()`，全部可在 JVM 断言。

解码目标由 `ViewerState.targetPx` 给出，实际采样交给 `ImageDecoder.decode(path, minPx)`；`ImageDecoder` 已含 EXIF 方向归一与像素上限判定，超过 `ImageSizing.LIMIT_PIXELS` 或堆预算不足时返回 null，查看器显示「图片过大」占位。

## 四、换图与预取

换图入口有三处：左右滑（横向位移超过 48 dp 且大于纵向位移）、音量键（`VolumeKeySink`）、面板无翻页按钮。换图只改 `index` 并重新 `load()`，不改页面栈。

`ViewerDecoder` 持有解码线程、预取线程、两槽缓存与代际计数：

1. `request(path, minPx, index, zoom, generation)` 写主请求槽并唤醒解码线程；
2. 解码线程取槽内参数解码，主线程回调校验 `generation`、当前下标与档位，任一不符即回收结果；
3. 命中缓存时直接显示并立即预取相邻两张；
4. 预取线程按队列解码相邻两张，结果进入缓存槽，淘汰离当前下标最远项；
5. `detach` 置关闭标记、递增代际、中断两个线程并回收缓存位图。

位图所有权：缓存槽持有的位图由 `ViewerDecoder` 回收，显示用位图是缓存槽引用，`PhotoViewer` 不回收；每次换图或换档前释放旧显示引用，避免与缓存重复回收。

## 五、动作面板与详情五行

动作面板 4 行：共享、详情、旋转另存、量化开关；第 5 行为提示行（例如「已保存到…」「分享失败」「旋转失败」）。面板贴内容区底边，白底加分隔线，行高 44 dp，点击空白处或再次长按关闭。

量化开关对应 D6：查看器默认灰度显示，开关打开后按 `Ditherer.dither(bitmap, 4)` 量化，关闭时只做 `Ditherer.grayscale`。开关只影响当前查看会话，不写配置；0.9.8 的设置项接入后由 `ThumbCacheRegistry` 一类入口参数承接。开关切换后按当前档位重新加载，使用代际校验丢弃旧结果。

详情面板 5 行固定顺序：尺寸（宽×高）、大小（`Formatter.formatFileSize`）、时间（`yyyy-MM-dd HH:mm`）、路径、格式（扩展名大写，未知写「未知」）。尺寸取解码回传的宽高，未完成解码时显示「--」；大小与时间在 IO 线程读取 `File`，主线程回填。

## 六、路由与页面装配

### 6.1 契约追加

`core/common/Contracts.kt` 只追加两处，不修改既有分支：

1. 在 `Destination.Files`（第 233 至 235 行）之后追加

```kotlin
    data class PhotoViewer(
        val paths: List<String>,
        val index: Int,
    ) : Destination
```

2. 在 `displayLabel()`（第 303 至 309 行）的 `is Destination.Files -> "文件"` 之后追加 `is Destination.PhotoViewer -> "图片"`。

`Destination` 为 `sealed interface`，追加条目后 `else` 分支仍覆盖标签查询，`when` 无需改动其他位置。

### 6.2 页面工厂

`FilesModule` 的 `FileScreenProvider` 增加一个分支：

```kotlin
is Destination.PhotoViewer -> PhotoScreen(destination.paths, destination.index)
```

`FilesModule.kt` 位于模块根目录，不在 task-23 声明的写作用域内，实施前需与 lead 确认扩展，或由 task-22 负责人在浏览页交付时一并加入。

### 6.3 查看页

`PhotoScreen(paths: List<String>, index: Int)` 实现 `Screen` 与 `VolumeKeySink`：

- `createView` 返回 `PhotoViewer` 并保留引用；
- `onBack` 先调 `viewer.closePanel()`，面板已关闭时返回 false 交宿主出栈；
- `onVolumeKey(up, down)` 转给 `viewer.previous()` / `next()`；
- `onDestroy` 调 `viewer.detach()`；
- `onResume`/`onPause` 只请求局部重绘，不重建位图。

音量键转发受 `LauncherConfig.tray.volumeKeyFlip` 控制，这是宿主既有行为（`MainActivity.onKeyDown`），查看页不自带开关。

### 6.4 进入与返回

网格与列表点击图片时，取当前目录内全部可查看图片的绝对路径列表与点击项下标，调用 `AppServices.router.navigate(Destination.PhotoViewer(paths, index))`。返回由宿主出栈，浏览页视图保留在栈内，页码与选中项自然恢复；进程被回收后的恢复不在本轮范围，报告中记录。

路由参数是 `data class`，`MainActivity.navigate` 按相等性去重：同一路径集合与同一下标不会重复入栈；不同下标会新增一层，因此查看器内换图必须只改内部下标，不回写路由。

## 七、旋转另存最小实现

`data/Edits.kt` 对应 `Edits.java` 的 `rotate90` 与命名规则，不引入编辑器操作栈（0.9.5 `EditSession` 已由 task-25 建立，本轮不改其文件，接口留给后续接入）。

```
object Edits {
    fun rotate90(context: Context, path: String): File?   // 主入口，IO 线程调用
    fun nextTarget(context: Context, source: File): File  // 输出路径与命名，纯函数供测试
    fun nextRotation(context: Context, path: String): Int // 计数 0..3，供测试
}
```

行为：

1. 读旋转计数 `edited_rotations` 键，键名为 `edited_rotations:<绝对路径>`，写入 `files_prefs`；每成功保存一次计数加一并取模 4，与 `EditSession` 的持久化键保持一致；
2. 角度 `((count % 4) + 1) % 4 * 90`，为 0 度时按 `Bitmap.createBitmap` 原样导出，保证旋转四次回到原图；
3. 解码用 `ImageDecoder.decode(path, 1600)`（长边 1600，与决策第六节一致；内存档位切换 2400 归 0.9.5 的 `EditExport`）；
4. 输出目录：源文件在 `cacheDir` 下（外部入口复制件）时写 `StorageAccess.editOutputRoot()`，其余写源文件同目录；目录不存在时创建；
5. 文件名 `原名-编-yyyyMMdd-HHmmss.png`，冲突经 `FileOps.uniqueName` 追加序号；
6. 编码 PNG 质量参数不影响无损输出，写入失败删除残缺文件并返回 null；
7. 成功后 `ThumbCacheRegistry.invalidate(newFile)`、`RefreshScheduler.notifyContentChanged()`，返回新文件供查看器提示。

面板提示：成功显示「已保存：文件名」，失败显示「旋转失败」。写入后不自动跳到新文件，保持当前查看位置。

## 八、外部入口 Intent 契约

### 8.1 PhotoEntryActivity

- 过滤器：`ACTION_VIEW` + `image/*`，`exported=true`；
- 入参：仅 `intent.data`（`content:` 或 `file:`），不读取任何附加项，避免外部注入路径；
- 显示名：查询 `OpenableColumns.DISPLAY_NAME`，失败回退 `uri.lastPathSegment`，再失败用 `image`；末段截断到 64 字符并替换路径分隔符；
- 复制：`cacheDir/photo-entry/`，文件名 `viewer_<毫秒>_<显示名>`；缓冲 16 KB；累计超过 64 MiB 即中止并删除，提示「图片过大」；
- 上限判定先读 `OpenableColumns.SIZE`（可得时）再按实际字节计数，两者任一超限即中止；
- 显示：`PhotoScreen` 的查看器直接挂到 Activity 内容视图，路径集合为单张；返回按钮 `finish()`；
- 失败：Uri 缺失、读取失败、空文件时提示「无法打开图片」并结束；
- 清理：`onDestroy` 删除本次临时文件，避免缓存累积。

### 8.2 PickEntryActivity

- 过滤器：`GET_CONTENT` 与 `OPEN_DOCUMENT` 两组，`image/*` 与 `*/*` 分别声明，`exported=true`；
- 类型合并：`intent.type` 与 `EXTRA_MIME_TYPES` 合并去空，形成 `types`；
- 界面：`PickPane` 继承既有 `FilePager`，复用顶栏、底栏、分页、手势；内容区按 `FileListing` 列目录，目录可进入，文件按 `accepts(mime, types)` 过滤后可选；
- 起始目录：`FilePrefs.lastPath` 可用时使用，否则 `StorageAccess.externalRoot()`；
- 返回结果：选中文件后 `FileProvider.getUriForFile(context, "${packageName}.files", file)`，`setData(uri)`、`FLAG_GRANT_READ_URI_PERMISSION`、`setClipData(ClipData.newRawUri(...))`；`callingPackage` 非空时再 `grantUriPermission(packageName, uri, FLAG_GRANT_READ_URI_PERMISSION)`；
- 二次存储卷：`FileProvider` 的 `external-path` 只映射主共享存储，卷外文件先复制到 `cacheDir/updates/pick/`（复用既有 `updates/` 路径），上限 64 MiB，超限提示无法返回；
- 取消：返回键或取消动作 `setResult(RESULT_CANCELED)`；无存储权限时提示并取消；
- 结果携带 `OpenableColumns`：返回的 `content:` Uri 由 `FileProvider` 提供，`DISPLAY_NAME` 与 `SIZE` 可查询，不额外打包附加项。

### 8.3 分享

- 主共享存储文件：`FileProvider.getUriForFile` 走 `files_share`（`external-path path="."`）；
- 二次存储卷文件：复制到 `cacheDir/updates/share/`，上限 64 MiB，走既有 `updates/` 缓存路径；
- `Intent.ACTION_SEND`、`type` 取 `FileListing.mimeOf(name)`、`putExtra(EXTRA_STREAM, uri)`、`FLAG_GRANT_READ_URI_PERMISSION`；
- 无接收方（`resolveActivity` 为 null）或抛异常时提示「没有可分享的应用」并保留面板；
- `FileProvider` 的 `files_share` 暴露整个外部存储根，安全约束：只对用户当前查看的单张图片取 Uri，不对目录批量授权；临时读权限只授予分享目标，不持久化；不把 Uri 写入配置或日志。

## 九、文件拆分与行数预估

| 文件 | 预估行数 | 内容 |
| --- | --- | --- |
| `ui/viewer/ViewerState.kt` | 150 | 缩放档位、页码夹取、目标像素、量化开关；纯 JVM |
| `ui/viewer/ViewerText.kt` | 70 | 省略号截断、时间与大小格式化、页码文案；纯 JVM |
| `ui/viewer/ViewerMetrics.kt` | 90 | 顶栏、内容区、面板行命中区 |
| `ui/viewer/ViewerChrome.kt` | 240 | 顶栏、动作面板、详情面板绘制 |
| `ui/viewer/ViewerDecoder.kt` | 260 | 解码线程、预取线程、两槽缓存、代际 |
| `ui/viewer/PhotoViewer.kt` | 500 | 绘制、触摸、加载、生命周期 |
| `ui/viewer/ViewerActions.kt` | 130 | 分享、旋转另存、提示 |
| `ui/PhotoScreen.kt` | 110 | Screen、音量键、返回与销毁 |
| `ui/PhotoEntryActivity.kt` | 130 | 外部入口、复制与清理 |
| `ui/PickPane.kt` | 220 | 选图列表、过滤、进入目录 |
| `ui/PickEntryActivity.kt` | 130 | 选图入口、结果与授权 |
| `data/Edits.kt` | 170 | 旋转、命名、计数、写后通知 |
| 合计 | 约 2,200 | 单文件均不超过 600 行 |

`ViewerChrome` 与 `ViewerMetrics` 可在实施中合并为 300 行左右，`PhotoViewer` 上限 550 行；如果 `PhotoEntryActivity` 与 `PickEntryActivity` 共用临时文件工具，抽出 `ui/EntryFiles.kt`（约 80 行）。

## 十、纯 JVM 测试

新增 `feature/files/src/test/java/.../ui/ViewerStateTest.kt` 与 `data/EditsTest.kt`，不少于 14 例：

1. 缩放循环顺序为适配、填充、原尺寸、适配；
2. 三档目标像素：短边、长边、原尺寸下限；
3. 首张上滑、末张下滑的页码夹取；
4. 邻图预取计划跳过越界；
5. 换图后旧代际结果被丢弃的判定（用纯计数模拟）；
6. 量化开关切换后档位保持不变；
7. 面板命中优先级：返回、面板行、关闭面板、缩放；
8. 滑动与点击阈值判定（48 dp、点击容差）；
9. 省略号截断保留末尾；
10. 时间格式化对 0 与非正值的处理；
11. 旋转计数 0 至 3 循环；
12. 输出文件名含「-编-」与时间戳，扩展名 `.png`；
13. 输出目录规则：缓存目录来源写 `Pictures/十雨桌面`，其余写源目录；
14. 命名冲突追加序号，序号耗尽时回落原名。

`Edits` 的 `nextTarget` 与 `nextRotation` 设计为可注入源文件的纯函数，避免测试依赖 Android 环境；`rotate90` 内部只做解码、矩阵与写盘，由真机验收覆盖。

## 十一、与 P2 浏览页的接口边界

并行任务与当前状态：task-22（dev-ui）正在重写 `ui/` 下的 `BrowseScreen`、`GridPane`、`ListPane`、`ThumbPrefetcher`，并把 `FilePager` 拆为 `FilePaging`、`FilePagerChrome`、`FilePagerTouch` 三个文件；task-25（dev-config）在 `data/` 新增 `EditSession`、`CropMath`、`EditExport`，在 `ui/` 新增 `Gray`、`GrayKernels`，并在 `src/test` 下新增测试。task-23 在这些文件交付后开始写代码，顺序为：先只添加新文件（viewer、pick、Edits），再追加 `Contracts.kt` 条目、`FilesModule` 分支与网格、列表的点击入口。

边界约定：

1. 网格与列表只把「图片路径列表 + 下标」交给路由，不传 `FileItem`，查看器不依赖浏览页类型；
2. 查看器不修改浏览页状态；返回后浏览页由宿主栈恢复；
3. 旋转另存只写文件与缩略图缓存，不刷新浏览页列表，由 `RefreshScheduler.notifyContentChanged` 通知重绘，浏览页在下一次可见时重新列目录；
4. `FilePager` 的子类只实现绘制与手势，不修改基类；选图页复用基类公开方法 `setPage`、`turnPage`、`submitItemCount`；
5. `data/Edits.kt` 与 `EditSession` 的旋转计数键保持同一格式（`edited_rotations:<绝对路径>`），由 `EditSession` 读取时可直接复用，避免两套计数。

## 十二、写作用域缺口

task-23 声明的作用域为 `ui/**`（仅查看与选图相关文件）、`data/Edits.kt`、`app/src/main/AndroidManifest.xml`、`_analysis/team`。以下改动在任务正文里被要求，但落在声明之外，实施前需要 lead 明确：

1. `core/common/Contracts.kt` 追加 `Destination.PhotoViewer` 与 `displayLabel` 分支，属共享契约；
2. `feature/files/.../FilesModule.kt` 注册 `PhotoScreen`；
3. `feature/files/src/test/**` 新增 `ViewerStateTest.kt`、`EditsTest.kt`，该目录同时是 task-22 与 task-25 的测试目录，文件名不冲突；
4. `feature/files/.../ui/GridPane.kt` 与 `ListPane.kt`（task-22 负责）需要加入图片点击入口，或者由 task-22 暴露回调；
5. 分享与选图复用 `app/src/main/res/xml/file_paths.xml` 既有路径，无需新增资源；如果需要新增缓存路径，再申请资源目录写入。

建议处理：`Contracts.kt`、`FilesModule.kt` 由本任务追加（单行改动，与其他任务不重叠），`GridPane`/`ListPane` 的点击入口等 task-22 交付后由本任务补一行，测试文件按唯一文件名新增。以上均需 lead 确认后执行。

## 十三、待确认事项

1. 三档缩放的档位名称与语义：任务文字为「适配、填充、原尺寸」，墨盘为「适应、100%、200%」。本文件按适配（contain）、填充（cover）、原尺寸（1:1）定义，需要确认。
2. 旋转输出目录：任务文字同时出现「源文件同目录」与「写入 `Pictures/十雨桌面` 时按决策第六节」。本文件按墨盘规则处理：缓存来源写 `Pictures/十雨桌面`，其余写源目录，需要确认。
3. `PickEntryActivity` 的界面归属：复用 `FilePager` 自建 `PickPane`，不依赖 `BrowseScreen` 的构造参数；如果 task-22 后续提供选图复用接口，可改接。
4. 音量键翻页沿用宿主 `volumeKeyFlip` 开关，查看页不自带开关。

## 十四、门禁与验收步骤

1. 单元测试与静态检查：`gradle --offline :feature:files:testDebugUnitTest check`，超 120 列 0，detekt 无新增告警；
2. 构建：`gradle --offline :app:assembleDebug`；
3. 真机（SA4B03CN2608081096X，API 30）：网格进入查看器，三档缩放与左右换图，分享到系统应用，旋转另存后返回列表见新文件，灰度与量化开关对照截图；用文件管理器发 `ACTION_VIEW` 打开单张图片；在微信读书触发选图并确认返回 Uri 可读；
4. 截图与数据写入 `_analysis/team/dev-core-0.9.4-photo.md`，含路由表、外部 Intent 契约、缩放与量化对照、遗留事项。

以上为只读准备结论，等待「P3 启动」后按本文件实施。
