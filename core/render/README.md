# core:render

图像解码、抖动、缩略图缓存与刷新调度。所有位图入口集中在本层，避免各模块各写一套解码。

## 关键类

`ImageDecoder.kt`：读取尺寸、按目标像素采样解码、归一 EXIF 方向、限制流式解码大小。

`ImageSizing.kt`：采样倍数、目标长边、像素上限与堆内存预算。

`Ditherer.kt` 与 `DitherCore.kt`：四档 Floyd–Steinberg 量化、指定档位量化、灰阶与阈值二值化，调色板与亮度公式集中在 `DitherCore`。

`ThumbCache.kt`：内存 8 MB、磁盘 32 MB 的两级缓存，写入前量化；`ThumbIndex.kt` 按目标长边与抖动开关分别维护索引，`ThumbKey.kt` 以 SHA-256 生成键，`ThumbCacheLoader.kt` 提供命中或解码的入口，`ThumbCacheRegistry.kt` 负责进程内共享与失效。

`RefreshScheduler.kt`：局部刷新、内容变更通知、定时全刷与刷新间隔更新，默认十分钟。

`InkCanvas.kt` 封装矩形、发丝线与反色绘制，`BitmapPipe.kt` 保留旧调用入口。

## 依赖

`api(project(":core:common"))`、`api(project(":core:device"))`、`api(project(":core:design"))`。

## 测试

`src/test` 四个文件、32 例：采样与内存预算、缓存键、索引隔离、抖动调色板与灰阶。

## 已知限制

缓存类依赖 `Bitmap`，只在模块内做索引与纯计算测试，位图正确性依靠真机截图核对。磁盘清理按整个缓存目录累计，多个实例共享目录时以索引文件区分。
