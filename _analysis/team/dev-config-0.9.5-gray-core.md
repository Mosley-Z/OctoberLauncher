# 0.9.5 P4-A 灰阶滤镜、裁剪变换与导出内核

## 一、交付文件

| 文件 | 行数 | 职责 |
| --- | --- | --- |
| `feature/files/src/main/java/com/shuiyu/octoberrain/feature/files/ui/Gray.kt` | 157 | 五档滤镜与对比度入口，对像素数组运算 |
| `feature/files/src/main/java/com/shuiyu/octoberrain/feature/files/ui/GrayKernels.kt` | 212 | 权重表、对比度表、Bayer 矩阵与误差扩散 |
| `feature/files/src/main/java/com/shuiyu/octoberrain/feature/files/data/CropMath.kt` | 169 | 归一化矩形、旋转镜像、像素换算、最小边长 |
| `feature/files/src/main/java/com/shuiyu/octoberrain/feature/files/data/EditSession.kt` | 312 | 编辑操作栈、撤销重做、位图重放与旋转计数持久化 |
| `feature/files/src/main/java/com/shuiyu/octoberrain/feature/files/data/EditExport.kt` | 258 | 长边选择、命名、重名处理、MediaStore 与文件回退写入 |

测试文件位于 `feature/files/src/test/java/com/shuiyu/octoberrain/feature/files/`：
`ui/GrayTest.kt`、`data/CropMathTest.kt`、`data/EditSessionTest.kt`、`data/EditExportTest.kt`。

## 二、接口

### 滤镜

`GrayParams(level: Int = 0, contrast: Int = 1, keepAlpha: Boolean = true)`，属性 `safeLevel` 与 `safeContrast` 在计算前夹取到合法区间。

`Gray.apply(pixels: IntArray, width: Int, height: Int, params: GrayParams): IntArray` 返回同尺寸新数组，不修改输入；尺寸非正或像素数量与尺寸不一致时返回输入副本。

`GrayKernels` 为 internal 对象，提供 `gray`、`contrast`、`soft`、`highContrast`、`bayerThreshold`、`quantize16`、`spread` 与常量 `ORIGINAL/BANDED/HIGH_CONTRAST/ORDERED/SOFT`，档位总数 `LEVELS = 5`、对比度总数 `CONTRASTS = 3`。

### 裁剪

`CropRect(left, top, right, bottom)` 为 0 至 1 的归一化矩形，提供 `width`、`height`、`isValid` 与伴生 `FULL`、`of(...)`。`PixelRect(left, top, width, height)` 承载像素结果。

`CropMath` 提供 `clamp`、`rotate90`、`rotate(rect, quarters)`、`mirror`、`pixels(rect, width, height)`、`minEdge(widthPx)`、`isLargeEnough(rect, width, height, minEdge)`。旋转按顺时针定义，点 `(x, y)` 映射为 `(1 - y, x)`；镜像按水平翻转，点映射为 `(1 - x, y)`；`rotate` 先对 4 取模归一，负值按逆时针处理。

### 编辑会话

`sealed interface EditOp` 含 `Rotate(quarters)`、`Mirror`、`Crop(rect)`、`Filter(level)`、`Contrast(level)`。

`EditSession(sourcePath, rotationStore = InMemoryRotationStore(), maxDepth = 20)` 维护操作栈与重做栈，公开 `push`、`undo`、`redo`、`reset`、`exportOps`、`operationList`、`size`、`canUndo`、`canRedo`、`rotationCount`，以及 `applyTo(source: Bitmap): Bitmap`。

`RotationStore` 接口提供 `count(path)` 与 `save(path, count)`；`InMemoryRotationStore` 为测试用内存实现；`PrefsRotationStore` 使用 `FilePrefs.FILE_NAME` 的 SharedPreferences，键为 `edited_rotations:<path>`。

### 导出

`EditExport` 公开 `MEMORY_THRESHOLD_MB = 192`、`TARGET_EDGE_LOW = 1600`、`TARGET_EDGE_HIGH = 2400`、`targetEdge(memoryClassMb)`、`displayName(sourceName, timestamp, timeZone)`、`stamp(timestamp, timeZone)`、`uniqueName(dir, sourceName, timestamp, timeZone)`、`export(context, sourcePath, ops, timestamp): ExportResult`。`EXPORT_DIRECTORY = "十雨桌面"` 为公共图片目录下的子目录名。

## 三、算法参数

五档滤镜：

| 档位 | 公式 |
| --- | --- |
| 0 原图灰阶 | `Y = (299R + 587G + 114B) / 1000`，整数权重表 |
| 1 十六级分级抖动 | `quantize16(Y) = clamp(((Y + 8) / 17) * 17, 0, 255)`，Floyd–Steinberg 误差扩散权重 7/16、3/16、5/16、1/16 |
| 2 高反差 | `clamp((Y - 128) * 1.5 + 128, 0, 255)`，再按 128 二值化 |
| 3 有序抖动 | 8×8 Bayer 矩阵，阈值 `value * 4 + 2`，与亮度比较后二值化 |
| 4 柔和 | `255 * (Y / 255) ^ 0.85`，再 `clamp((Y' - 128) * 0.9 + 128, 0, 255)` |

柔和档位按参数定义对端点做对比度收缩，0 与 255 分别映射为 13 与 242，中灰 128 为 141；该结果由公式直接得出，测试按参数值断言。

对比度三档在滤镜结果上围绕 128 缩放，系数 0.85、1.00、1.15，使用 256 项查表。所有逐像素运算只做整数查表与加法，Bayer 阈值、对比度表在类加载时构建。

亮度与打包：`pack(value, alpha) = (alpha shl 24) or (value shl 16) or (value shl 8) or value`；`keepAlpha` 为假时 alpha 固定 255。

裁剪：

- 旋转 90 度：`left' = 1 - bottom`、`top' = left`、`right' = 1 - top`、`bottom' = right`。
- 镜像：`left' = 1 - right`、`top' = top`、`right' = 1 - left`、`bottom' = bottom`。
- 像素换算：左与上向下取整，右与下向上取整，宽高至少 1，并夹取在图片范围内。
- 最小裁剪边：宽度不超过 480px 取 32，其余取 48。

导出：

- 长边按 `ActivityManager.getMemoryClass()` 选择，低于 192MB 取 1600，其余取 2400。
- 解码复用 `ImageDecoder.size` 与 `ImageDecoder.decodeWithSample`，采样目标为 `targetEdge / 2`，随后按长边缩放；像素上限判断交给 `ImageSizing.withinLimit`（上限 8000 万像素，每像素 2 字节，堆预算双倍判断），本任务不另建尺寸决策。
- 写入优先级：Android 10 及以上使用 `MediaStore.Images.Media`，`RELATIVE_PATH` 为 `Pictures/十雨桌面`，`IS_PENDING` 置 1 写入后置 0；插入、写入任一步失败则删除记录并回退到目录文件写入，再经 `MediaScannerConnection.scanFile` 通知媒体库。
- 文件名 `原名-编-yyyyMMdd-HHmmss.png`，重名沿用 `FileOps.uniqueName` 的 `_2` 至 `_999` 规则。
- 导出失败返回 `ExportResult.Failure`，附带中文说明；成功返回 `Success(displayName, path)`。

编辑重放：先按操作顺序执行旋转、镜像与裁剪，最后统一执行一次滤镜与对比度；`applyTo` 复用输入位图，新生成的中间位图与最终位图由调用方按返回值管理，内部仅回收未被引用的中间结果。

## 四、与既有实现的关系

`data/Edits.kt` 由 dev-core 在 P3 编写，本任务未改动。`EditSession` 是面向编辑页的操作栈与重放入口，`Edits` 是面向查看器的旋转另存最小实现；两者共享 `edited_rotations` 键与 `Pictures/十雨桌面` 输出目录约定。P4-B 统一时，建议把 `Edits` 的旋转另存改为调用 `EditExport.export`，并由 `EditSession` 提供当前操作栈快照，避免维护两套旋转计数逻辑。

## 五、测试

纯 JVM 用例共 44 例，全部通过：

- `GrayTest` 11 例：权重计算、透明通道策略、对比度三档与越界、十六级量化单点与调色板、高反差边界、Bayer 首行图案、柔和查表、非法尺寸副本、输入不变。
- `CropMathTest` 13 例：整幅保持、旋转分量映射、四次旋转、镜像分量、两次镜像、负数与四次归一、越界夹取、像素换算、退化矩形、非法尺寸、最小边长、尺寸判定、面积合法性。
- `EditSessionTest` 12 例：追加与快照、撤销重做、新增清空重做、栈深上限、旋转计数持久化、四次旋转、负数旋转、重置、裁剪入栈夹取、快照独立、空栈、初始计数。
- `EditExportTest` 8 例：低与高内存档位、时间戳、命名、重名追加、无冲突、无目录、常量与目录名。

失败与修正记录：

1. `两次镜像回到原矩形` 使用浮点精确比较时出现末位误差 `0.100000024`，改为 1e-5 精度比较，属浮点减法不可逆的固有误差。
2. `柔和档位查表` 最初按端点保持断言 0 与 255，与公式实际的 13 与 242 不符；以参数定义为准修正断言，并在用例注释中说明端点收缩。

## 六、静态检查

本任务新增五个生产文件与四个测试文件在 `:feature:files:detekt` 报告中为 0 条，在 `ktlintMainSourceSetCheck` 与 `ktlintTestSourceSetCheck` 报告中为 0 条。全仓超 120 列行 0。

模块门禁当前受并行任务 task-22 影响，`feature/files/src/main/java/.../ui/` 下的 `BrowsePane.kt`、`GridPane.kt`、`ListPane.kt`、`BrowsePanel.kt`、`FilePagerChrome.kt`、`ThumbPrefetcher.kt` 存在 detekt 与 ktlint 告警，且 `ListPane.kt:172` 引用尚未加入 `FileTokens` 的 `gapLarge`，导致 `:feature:files:compileDebugKotlin` 失败。上述文件不在本任务写作用域，未作修改。

## 七、P4-B 接入点

1. 编辑页载入图片后按预览长边 1024 生成位图，取 `IntArray` 经 `Gray.apply` 生成预览；档位切换只重跑滤镜，不重新解码。
2. 裁剪框状态使用 `CropRect`；拖动时只更新矩形，松手时按 `CropMath.pixels` 计算像素结果并进入 `EditSession.push(EditOp.Crop(...))`。
3. 旋转与镜像把次数与标志写入 `EditSession`，重放顺序与导出一致。
4. 完成操作调用 `EditExport.export(context, sourcePath, session.exportOps())`，在 IO 线程执行，成功后刷新相册并跳转新图。
5. 预览层可复用 `GrayKernels` 的 Bayer 矩阵与误差扩散边界处理，避免预览与导出两套参数。
