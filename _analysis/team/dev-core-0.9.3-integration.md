# 0.9.3 收尾：缩略图缓存与抖动接入现有调用点

## 一、目标与结果

本任务把 0.9.3 新增的解码入口（`ImageDecoder`）、抖动量化（`Ditherer`）与两级缩略图缓存（`ThumbCache`）接入四处既有图片解码调用点，补齐图片目录、图标选择与桌面组件三条展示路径，并完成真机命中率与内存测量。

结果：四处调用点全部改走统一解码与缓存，`BitmapFactory` 在四个业务模块内无直接引用；门禁命令通过；真机两轮浏览分别记录 200 次未命中与 200 次命中，第二轮流式读取全部命中内存缓存。

## 二、新增接口与改动

### `core/render/ThumbCacheRegistry.kt`（新增）

进程内缓存入口。首次调用按目标长边与抖动开关建立 `ThumbCache`；磁盘目录为 `context.cacheDir/thumbs`。

```kotlin
object ThumbCacheRegistry {
    fun get(
        context: Context,
        targetEdge: Int = ThumbCache.DEFAULT_TARGET_EDGE,
        dither: Boolean = true,
    ): ThumbCache

    fun invalidate(context: Context, path: File)
}
```

缓存实例以 `CacheKey(targetEdge, dither)` 区分，同一目标尺寸与开关组合共用一个实例；`invalidate` 遍历全部实例，命中路径的条目被移除，磁盘文件与索引同步清理。`context` 参数在失效中不参与定位，保留形参避免后续签名漂移。

### `core/render/ThumbCacheLoader.kt`（新增）

```kotlin
fun ThumbCache.loadOrDecode(path: File, decode: () -> Bitmap?): Bitmap? =
    get(path) ?: decode()?.let { bitmap -> put(path, bitmap) }
```

把「命中直接返回、未命中毒化写入」集中在扩展函数内，四个调用点各保留一次文件校验与一次 `loadOrDecode` 调用，返回语句数量满足 detekt 的 `ReturnCount` 上限。

### `core/render/ThumbCache.kt`（修改）

`put` 由返回 `Unit` 改为返回量化后的 `Bitmap`，调用方无需再取一次缓存即可绘制：

```kotlin
fun put(path: File, bitmap: Bitmap): Bitmap {
    val quantized = if (ditherEnabled) Ditherer.dither(bitmap, DitherCore.DEFAULT_LEVELS) else bitmap
    ...
    if (!written) return quantized
    return quantized
}
```

构造新增 `ditherEnabled: Boolean = true`，作为 0.9.8「缩略图抖动」设置项的预留开关；`get`、`put`、`invalidate`、`warmUp`、`stats` 均加 `@Synchronized`，绘制线程与缓存写入线程共享实例时保持串行。

### 未改动项

`Contracts.AppServices` 保持原样，`core:common` 不反向依赖 `core:render`；缓存实例由调用点通过 `ThumbCacheRegistry.get(context, edge)` 取得。

## 三、调用点清单

| 模块 | 文件与行号 | 目标尺寸 | 说明 |
| --- | --- | --- | --- |
| feature:content | `ui/ContentUi.kt` `decodeCover`（原 :499-513） | 传入 `sidePx` | 书封网格；`decodeCover` 增加 `context` 首参，两处调用点同步 |
| feature:settings | `ImageDirectoryStore.kt` `decodeSampled`（原 :85-100） | 传入 `maxSizePx` | 图片目录与图标选择共用；增加 `context` 首参 |
| feature:widgets | `CoversWidget.kt` `decode`（原 :157-176） | 组件实测宽高较大值 | 书架封面组件 |
| feature:widgets | `ImageWidget.kt` `decode`（原 :147-166） | 组件实测宽高较大值 | 单图组件 |

设置侧 `decodeSampled` 的四处调用点改为传控件上下文：`BeautifyImagePanel.kt:236`、`IconPickerScreen.kt:141`、`IconPickerScreen.kt:260`、`PreviewScreen.kt:555`。

失效与重绘接入：

- `BeautifyImagePanel.kt` 两处目录变更（`renderNow()` 之后）调用 `RefreshScheduler.notifyContentChanged()`。
- `IconPickerScreen.kt` 两处目录重建（`rebuild()` 之后）同上；`applyImage` 成功分支对旧文件调用 `ThumbCacheRegistry.invalidate` 后再通知刷新。
- `AppearanceScreen.applyWallpaper` 记录旧壁纸路径，写入成功后失效旧路径并通知刷新。
- `ImageWidget.chooseImage` 记录旧图路径，写入成功后失效并通知刷新，保留原有 `AppServices.refresh.requestPartial()`。
- `CoversWidget.onWidgetDestroy` 不再回收位图，只清空渲染引用；`ImageWidget.recycleBitmap` 更名为 `releaseBitmap`，仅置空引用。位图归缓存持有，回收会导致后续命中返回已回收位图。

## 四、参数与算法

缓存参数沿用任务要求：磁盘上限 32 MB、内存上限 8 MB、默认目标长边 256；调用点按实际显示尺寸传入长边，取样由 `ImageDecoder` 的两遍流程完成。

解码后统一调用 `Ditherer.dither(bitmap, DitherCore.DEFAULT_LEVELS)`（四档），Floyd–Steinberg 误差扩散，再以 PNG 写入磁盘并作为内存缓存值返回；封面与图片组件因此呈现灰阶二值观感。EXIF 方向在 `ImageDecoder` 内归一，缓存键包含文件路径、修改时间与目标长边，方向处理不会造成键冲突。

## 五、验证记录

命令与结果（工作目录 `D:\DSHStation\OctoberLauncher`，`--offline`）：

```text
gradle :feature:content:testDebugUnitTest :feature:widgets:testDebugUnitTest \
    :feature:settings:testDebugUnitTest check :app:assembleDebug
> BUILD SUCCESSFUL in 8s
```

- 单元测试：content 1 套件 5 例、settings 3 套件 27 例、widgets 1 套件 5 例，合计 5 套件 37 例，0 失败。
- `check` 包含 ktlint、detekt、Android Lint 基线检查，全部通过；detekt 未新增告警。
- 首次执行时 `:feature:files:detekt` 因并行模块的中间态报 1 条告警失败，单独重跑该模块任务已通过，随后完整门禁重跑通过。
- 行宽：本次改动涉及的 12 个文件超 120 列 0 行。
- 产物：`app/build/outputs/apk/debug/app-debug.apk` 3733864 字节。

## 六、真机基准

设备 SA4B03CN2608081096X（Android 11，API 30），图片目录指向 `/sdcard/Pictures/thumbs-bench`，目录内 500 张灰度渐变图与 1 张 EXIF 方向图（`exif-orient-6.jpg`）。

测量使用临时日志统计命中与未命中，测量结束后日志已移除，仓库内代码不含基准日志。

第一轮（冷启动进入美化模式，图片目录 200 项上限内全部绑定）：

```text
未命中 200，命中 0
TOTAL PSS   48185 KB -> 76344 KB（+28159）
Dalvik Heap  1844 KB ->  3584 KB（+1740）
Native Heap 16300 KB -> 43212 KB（+26912）
```

第二轮（返回设置首页后再次进入，滚动到底）：

```text
未命中 0，命中 200
TOTAL PSS   76344 KB -> 75673 KB
Dalvik Heap  3584 KB ->  3580 KB
Native Heap 43212 KB -> 41812 KB
```

磁盘缓存：200 张 PNG 与一份 `index.txt`，第二轮回看时目录占用 872 KB；最终安装后重新构建缓存时占用 772 KB、178 个文件（仅首屏预取，未滚动到底）。

图片目录单次最多列出 200 张（`ImageDirectoryStore.MAX_IMAGES`），因此 500 张素材只覆盖前 200 张，命中率口径按 200 项计算。

截图：`_analysis/verify/shots/`

- `t20-pass1-top.png`：第一轮进入时的图片目录与 EXIF 缩略图；
- `t20-pass1-bottom.png`：第一轮滚动到底；
- `t20-pass2.png`：第二轮滚动到底，内容与第一轮一致；
- `t20-final-beautify.png`：最终安装包下的图片目录，抖动后的灰阶纹理与 EXIF 缩略图；
- `t20-home.png`、`t20-home2.png`、`t20-settings.png`：进入路径。

## 七、方向一致性

`exif-orient-6.jpg` 的像素尺寸为 320×240（横向），EXIF Orientation 为 6，正确显示应顺时针旋转 90 度，即 240×320 纵向。

测量日志记录：`decode exif-orient-6.jpg orient=6 160x120 -> 120x160`（按 2 倍采样）。原始 320×240 解码为 160×120，方向归一后变为 120×160，与正确显示的纵横比一致。

截图中该缩略图为纵向，文字方向与主机端按 EXIF 渲染的结果一致（“BOTTOM”沿左侧竖排、“TOP”位于右侧旋转摆放），可见方向归一已生效。缩略图缓存键包含文件路径与修改时间，同一文件重复进入不会重复旋转。

## 八、判断与取舍

1. `ThumbCache.put` 改为返回位图，避免调用方二次读取缓存造成计数偏移与一次多余查找。
2. 位图归缓存持有后，控件侧不再回收，避免命中时返回已回收位图；`ImageWidget` 的原 `recycleBitmap` 改名以体现语义变化。
3. 缓存实例按目标长边分别创建，每个实例内存上限 8 MB，同时使用多种目标尺寸时进程内存上限会高于 8 MB。当前实际尺寸组合为书封、设置列表、组件三档，观测峰值约 76 MB PSS，未触及 OOM，故保留现结构，后续如需可改为全局按字节配额。
4. 真机验证覆盖设置图片目录路径；桌面组件的同一 `loadOrDecode` 路径由编译与静态检查保证，未在桌面上逐个添加组件截图，列入遗留。
5. 基准日志属于测量手段，测量后已删除，最终包与门禁结果基于无日志代码。

## 九、遗留

- 桌面组件（`CoversWidget`、`ImageWidget`）的真机展示未单独截图验证，路径与设置页共用同一缓存入口。
- 多目标尺寸下缓存实例的内存上限累加问题，待后续统一配额。
- 「缩略图抖动」设置项开关尚未接入设置界面（计划任务 8.3），当前 `ThumbCacheRegistry.get` 的 `dither` 参数默认 `true`。
