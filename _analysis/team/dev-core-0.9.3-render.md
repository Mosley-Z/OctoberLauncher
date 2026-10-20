# 0.9.3 渲染管线核心

## 目标与结果

任务目标是把 `core/data` 内分散的图片解码收敛到统一入口，补齐抖动档位与两级缩略图缓存，并以墨盘 `ImageDecode.java` 作为行为参考。四项均已完成：

- `core/render` 新增 `ImageDecoder.kt`、`ImageSizing.kt`、`ThumbKey.kt`、`ThumbCache.kt`，扩充 `Ditherer.kt` 与 `DitherCore.kt`。
- `BitmapPipe.decodeFile` 改为委托 `ImageDecoder`，模块内保留原有调用签名。
- `core/data` 四处解码调用改走统一入口，解码结果按用途区分 RGB_565 与 ARGB_8888。
- 新增 28 例本地 JVM 测试，`check` 与 `:app:assembleDebug` 均通过。

## 新增 API 签名

`ImageDecoder.kt`（210 行）位于 `core/render/src/main/java/com/shuiyu/octoberrain/core/render/`：

- `object ImageDecoder`
- `fun size(path: String): IntArray?`，返回宽高数组，越界或不可解码返回 null。
- `fun decode(path: String, minPx: Int, keepAlpha: Boolean = false): Bitmap?`
- `fun decodeWithSample(path: String, sample: Int, keepAlpha: Boolean = false): Bitmap?`，供已完成尺寸决策的调用点使用。
- `fun decodeStream(input: InputStream, minPx: Int, keepAlpha: Boolean = false): Bitmap?`，用于压缩包内只有输入流的图标资源。

`ImageSizing.kt`（113 行）为纯计算对象，不引用 Android 类型：

- 常量 `MAX_EDGE = 3200`、`MAX_TARGET = 1600`、`LIMIT_PIXELS = 80_000_000L`、`BYTES_PER_PIXEL = 2`、`ICON_MAX_PIXELS = 4_000_000`。
- `fun withinLimit(width: Int, height: Int): Boolean`
- `fun targetEdge(minPx: Int): Int`
- `fun sampleFor(longEdge: Int, minPx: Int): Int`
- `fun requestSample(rawWidth: Int, rawHeight: Int, reqWidth: Int, reqHeight: Int): Int`
- `fun sampledPixels(width: Int, height: Int, sample: Int): Long`
- `fun fitsHeap(width: Int, height: Int, sample: Int, availableBytes: Long): Boolean`

`ThumbKey.kt`（62 行）为纯计算对象：

- `fun of(path: String, modifiedMillis: Long, targetEdge: Int): String`，返回 64 位十六进制摘要。
- `fun pathDigest(path: String): String`，仅按来源路径计算，供索引分组。
- `fun sha256Hex(text: String): String`

`ThumbCache.kt`（234 行）：

- `class ThumbCache(diskDir: File, maxDiskBytes: Long = 32 MB, memoryBytes: Int = 8 MB, targetEdge: Int = 256)`
- `fun get(path: File): Bitmap?`
- `fun put(path: File, bitmap: Bitmap)`
- `fun invalidate(path: File)`
- `fun warmUp(paths: List<File>, decode: (File) -> Bitmap?): Int`
- `fun stats(): Stats`，返回内存命中、磁盘命中、未命中、写入次数、磁盘条目数与字节数。

`Ditherer.kt` 新增：

- `fun dither(source: Bitmap, levels: Int): Bitmap`，档位支持 2、4、8，非法值按 4 档处理。
- `fun grayscale(source: Bitmap): Bitmap`。
- 既有 `dither(source)` 保留，固定四档，误差扩散权重不变。

`DitherCore.kt` 抽出纯计算，供 JVM 测试：`normalizeLevels`、`paletteOf`、`luma`、`nearest`、`grayscale`、`opaque`，以及常量 `DEFAULT_LEVELS = 4`。

## 调用点迁移清单

| 文件 | 行号 | 迁移前 | 迁移后 |
| --- | --- | --- | --- |
| `core/data/.../AppCatalogImpl.kt` | 87 | `BitmapFactory.decodeFile` | `ImageDecoder.decode(..., sizePx, keepAlpha = true)` |
| `core/data/.../AppearanceRepositoryImpl.kt` | 231 | 边界解码 | `ImageDecoder.size(path)` |
| `core/data/.../AppearanceRepositoryImpl.kt` | 241 至 243 | 自行计算采样倍率后解码 | `ImageDecoder.size` + `ImageSizing.requestSample` + `ImageDecoder.decodeWithSample(..., keepAlpha = true)` |
| `core/data/.../IconOverrideStoreImpl.kt` | 58 | 边界解码校验 | `ImageDecoder.size(target.absolutePath) == null` |
| `core/data/.../IconOverrideStoreImpl.kt` | 119 至 121 | 自行计算采样倍率后解码 | `ImageDecoder.size` + `ImageSizing.requestSample` + `ImageDecoder.decodeWithSample(..., keepAlpha = true)` |
| `core/data/.../IconPackResolver.kt` | 222 | `BitmapFactory.decodeStream` | `ImageDecoder.decodeStream(input, ICON_ASSET_EDGE, keepAlpha = true)`，常量 `ICON_ASSET_EDGE = 256` 位于 274 行 |
| `core/render/.../BitmapPipe.kt` | 27 | 两遍解码与自行限幅 | `ImageDecoder.decode(file.absolutePath, minPx)`，`sampleSize` 委托 `ImageSizing.requestSample` |

`core/data/src/main/java` 内已无 `BitmapFactory` 直接引用。图标与壁纸保留透明通道，使用 ARGB_8888；其余场景默认 RGB_565。

## 算法参数

- 长边上限 3200 像素，采样目标上限 1600 像素；采样倍率为 2 的幂，条件为长边除以倍率仍大于目标的两倍。
- 像素总量上限 8000 万，超出视为不可解码；堆预算要求可用堆达到目标位图字节数的两倍，为旋转中间位图留一份余量。
- EXIF 方向按映射表处理：水平翻转、180 度旋转、垂直翻转、转置、顺时针 90 度、反向转置、逆时针 90 度；无方向标记时不复制位图。
- 抖动调色板：四档沿用 255、172、96、0，保证接入前后观感一致；二档与八档按等距灰阶生成。灰度权值为 0.299、0.587、0.114，透明通道统一置为不透明。
- 缩略图缓存写入前按四档量化并保存为 PNG；默认磁盘上限 32 MB，内存上限 8 MB，目标长边 256 像素；清理按文件修改时间从旧到新，直到不超过上限。

## 验证记录

环境：Gradle 8.11.1，JDK 17.0.20.1，AGP 8.7.3，Kotlin 2.0.21，离线模式。

- `gradle :core:render:testDebugUnitTest :core:data:testDebugUnitTest --offline`：BUILD SUCCESSFUL，共 7 个套件 60 例 0 失败。其中 `ImageSizingTest` 12 例、`ThumbKeyTest` 8 例、`DitherCoreTest` 8 例为本次新增；`core:data` 既有 4 个套件 32 例。
- `gradle :core:render:detekt :core:render:ktlintCheck :core:data:detekt :core:data:ktlintCheck --offline`：BUILD SUCCESSFUL，新增代码未进入基线，全仓超 120 列行数为 0。
- `gradle check :app:assembleDebug --offline`：BUILD SUCCESSFUL，932 个任务；`app/build/outputs/apk/debug/app-debug.apk` 3581735 字节。
- 真机验证方式：安装调试包后依次打开桌面图标、图标包主题、壁纸导入与最近使用页面，观察解码与缩略图呈现；`adb shell dumpsys meminfo com.shuiyu.octoberrain` 观察堆占用。本次未执行真机走查，交由门禁阶段统一安排。

## 判断与取舍

1. `ImageDecoder` 保留 `decodeWithSample` 与 `decodeStream` 两个入口。前者服务于已按目标尺寸算好采样倍率的调用点，避免重复探测边界；后者服务于图标包 assets，输入流不可回绕，因此一次读入字节数组，上限 32 MB。
2. 图标与壁纸解码使用 `keepAlpha = true`。图标需要透明边缘，壁纸随后绘制到白色底图上，透明通道不影响结果；任务要求以 RGB_565 为默认，仅在需要透明的入口显式提升。
3. `BitmapPipe.decodeFile` 在两个方向都不限制时，把期望长边定为 `MAX_TARGET`，使长边超过 3200 的图片仍被限幅；原实现按 400 万像素兜底，行为差异记录在此。
4. detekt 在 0.9.2 起失败即中止，新增代码未写入基线。首轮告警 6 条，处理方式为：`ImageDecoder` 删除未被引用的公开方法与 `configOf`，函数数由 13 减至 10；`Ditherer` 把误差扩散拆为 `diffuse` 与 `spread`；`ThumbCache` 移除 `clear` 并合并索引写入，函数数减至 10 个；`orientationMatrix` 改用方向映射表，圈复杂度降到阈值以内。
5. `ThumbKey` 摘要包含版本标记 `thumb-v1`，缓存格式变化时递增版本即可整体失效；摘要与索引均不含明文路径。

## 遗留事项

- `ThumbCache` 已完成 API 与容量控制，尚未接入调用方；接入点与命中率数据留待下一任务验证。
- 抖动与灰度的位图路径依赖 Android 图形栈，本次只验证纯算法部分，位图输出未做仪器化测试。
- 真机走查与内存数据待在门禁阶段补充；API 26 至 28 设备未实测，版本分支由代码覆盖。
