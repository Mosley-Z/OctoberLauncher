# 0.9.5 锁屏壁纸与图片量化（task-29）交付报告

## 一、结论

桌面壁纸量化、锁屏壁纸、锁屏量化三项能力已按 P5 计划落地。配置字段、仓库接口、解码裁剪与量化、锁屏层叠、设置项全部完成；新增 15 例纯 JVM 测试通过；全仓门禁 `testDebugUnitTest check` 通过；真机完成四档量化对比、锁屏壁纸渲染、进程重启生效、无壁纸回退与配置恢复。

## 二、配置与接口

### 2.1 配置字段

`core/common/src/main/java/com/shuiyu/octoberrain/core/common/Models.kt:178` 的 `AppearanceConfig` 新增三项：

- `wallpaperQuantize: Int = 0`（:197），桌面壁纸量化档位，0 关闭、1 二级灰、2 四级灰、3 八级灰。
- `lockWallpaperPath: String = ""`（:199），锁屏壁纸文件路径，空串表示未设置。
- `lockWallpaperQuantize: Int = 2`（:201），锁屏壁纸量化档位，默认四级灰。

KDoc 同步说明档位语义与空串语义（:173-176）。

### 2.2 仓库接口

`core/common/src/main/java/com/shuiyu/octoberrain/core/common/Contracts.kt:780-800` 的 `AppearanceRepository`：

- `loadWallpaper(width, height, quantize: Int = 0)`，新增带默认值的量化参数，既有调用点零改动。
- `setLockWallpaper(uri): Boolean`（:787）、`clearLockWallpaper()`（:790）、`loadLockWallpaper(width, height)`（:793）。

接口方法数由 10 增至 13，触发 detekt `TooManyFunctions`（接口阈值 11）。在接口声明处加 `@Suppress("TooManyFunctions")`，KDoc 说明桌面与锁屏成对方法同属一份外观状态、拆分接口会让调用方同时持有多个服务引用。

## 三、仓库实现

`core/data/src/main/java/com/shuiyu/octoberrain/core/data/AppearanceRepositoryImpl.kt`（365 行）：

- `setWallpaper` 复用 `copyWallpaperFile`（:266），复制前删除配置中的旧文件，复制后 `decodeBounds` 校验，失败返回 false 并保留原配置。
- `setLockWallpaper`（:73）、`clearLockWallpaper`（:85）、`loadLockWallpaper`（:93）与桌面方法对称；锁屏文件前缀 `lock`，桌面前缀 `wallpaper`（:350-353）。
- `decodeWallpaper`（:297）拆为尺寸读取与 `renderWallpaper` 两段：`ImageDecoder.size` → `ImageSizing.requestSample` → `decodeWithSample(keepAlpha = true)` → 白底画布上按 `WallpaperGeometry.centerCrop` 源矩形绘制到目标尺寸 → `WallpaperQuantize.apply`。
- 任一环节失败返回 null，视图回退纸色，不阻断启动。

### 3.1 壁纸几何

`core/data/src/main/java/com/shuiyu/octoberrain/core/data/WallpaperGeometry.kt`（67 行）：

- `CropWindow(left, top, right, bottom)`（:9），右、下边界为开区间，`width`、`height` 由边界差派生。
- `centerCrop(srcWidth, srcHeight, dstWidth, dstHeight)`（:42）按 `scale = max(dstW/srcW, dstH/srcH)` 计算覆盖窗口，宽高四舍五入后夹取到源图范围，窗口居中。
- 尺寸非法返回 null。既有行为为整幅拉伸，改为居中裁剪后保持源图比例，避免人脸与文字变形。

### 3.2 量化

`core/data/src/main/java/com/shuiyu/octoberrain/core/data/WallpaperQuantize.kt`（58 行）：

- `levelsFor(quantize)`（:31）把 0/1/2/3 映射为 0/2/4/8 级，非法值按关闭处理。
- `apply(source, quantize)`（:46）直接调用 `Ditherer.dither(bitmap, levels)`，未新增第三套抖动实现；档位 0 或位图空时原样返回；抖动异常回退原图。

`Ditherer` 现有档位为 2/4/8，与四档量化的二级灰、四级灰、八级灰一一对应，无需修改 core/render。

## 四、视图与层序

### 4.1 桌面壁纸

`app/src/main/java/com/shuiyu/octoberrain/WallpaperView.kt`（137 行）：

- 增加 `loadedQuantize` 记录已加载档位，配置变化时同时比较路径与档位（:54）。
- `load(path, quantize)`（:73）只做状态更新与分支，解码下沉到 `request(path, quantize)`（:89），两者均保持 ReturnCount ≤ 2。
- 解码完成时再次校验路径与档位，快速切换不会回填错图。

### 4.2 锁屏壁纸

新增 `app/src/main/java/com/shuiyu/octoberrain/LockWallpaperView.kt`（137 行）：

- `class LockWallpaperView(context) : View`（:28）监听配置中的 `lockWallpaperPath` 与 `lockWallpaperQuantize`，在 IO 线程调用 `loadLockWallpaper`。
- `onDraw`（:117）无壁纸时填 `InkColors.PAPER`；有壁纸时按视图边界绘制并叠加 `0xB8` 白色压层（:127、:135），约 72% 白，保证黑色键线与提示文字在照片背景上的对比度。
- 白色压层覆盖状态栏区域，锁屏时状态栏底色透明后仍可辨识。

`app/src/main/java/com/shuiyu/octoberrain/LockOverlayView.kt` 背景由 `InkColors.PAPER` 改为 `Color.TRANSPARENT`，纸色由壁纸层提供，PIN 面板不再整屏遮挡壁纸。

### 4.3 宿主层序

`app/src/main/java/com/shuiyu/octoberrain/MainActivity.kt`（598 行）：

- 新增字段 `lockWallpaperLayer`（:61），在 `lockHolder` 创建后固定加入第 0 层（:194、:202）。
- `showLock`（:353）改为叠加 PIN 覆盖层，不再 `removeAllViews`，壁纸层始终保留；`hideLock` 只移除覆盖层。
- `updateStatusBarBackground`（:321）在锁屏期间与桌面一致使用透明底色，其余页面保持纸白。

## 五、设置项

`feature/settings/src/main/java/com/shuiyu/octoberrain/feature/settings/AppearanceSpecs.kt` 的壁纸分组新增四项：

- `appearance.wallpaper.quantize`（:129），桌面壁纸量化，选项「关闭/二级灰/四级灰/八级灰」。
- `appearance.lockWallpaper.pick`（:142），选择锁屏壁纸，值列显示文件名或「纸色」。
- `appearance.lockWallpaper.clear`（:158），清除锁屏壁纸。
- `appearance.lockWallpaper.quantize`（:168），锁屏量化，默认档位 2。

`AppearanceScreen.kt`（177 行）动作表增加两项（:40-41），新增 `pickLockWallpaper`（:90）、`applyLockWallpaper`（:98）、`clearLockWallpaper`（:117）；清除前经 `InkDialogs.confirm` 确认，完成后请求整屏重绘。

## 六、测试

新增 `core/data/src/test/java/com/shuiyu/octoberrain/core/data/WallpaperGeometryTest.kt`（95 行，9 例）与 `WallpaperQuantizeTest.kt`（45 行，6 例）：

- 几何：竖屏裁上下、横屏裁左右、方形源图、窗口比例等于目标比例、窗口不越界、比例一致时覆盖整幅、源尺寸非法、目标尺寸非法、窗口宽高派生。
- 量化：关闭、二级灰、四级灰、八级灰映射，非法档位按关闭处理，空位图返回空。

`core:data` 47 例 0 失败；`feature:settings` 27 例 0 失败；`app` 无单元测试；三模块 detekt 与 ktlint 通过。

## 七、门禁

- `gradle --offline :core:data:testDebugUnitTest :feature:settings:testDebugUnitTest :core:data:detekt :core:data:ktlintCheck :feature:settings:detekt :feature:settings:ktlintCheck :app:detekt :app:ktlintCheck`：BUILD SUCCESSFUL。
- `gradle --offline testDebugUnitTest check`：BUILD SUCCESSFUL，962 tasks。
- 本次改动 12 个文件逐行统计，超过 120 列 0 行。

## 八、真机验收

设备 S4 480×800、density 219、Android 11（API 30）。

- 功能验证使用 02:10:35 构建的 `app-debug.apk`（3,974,943 字节，含壁纸三字段、锁屏层、四项设置与量化链路）。
- 构建期间 `feature/widgets` 并行改动导致编译中断，已由负责方修复；最终 APK 于 02:28:05 重新生成（4,267,185 字节），安装成功并启动正常，全仓门禁在其后重跑通过。
- 四档肉眼差异：关闭为原图彩色；二级灰呈黑白两色颗粒，文字边缘明显锯齿；四级灰出现四层灰度，块面过渡变粗但图标可辨；八级灰纹理最细腻，照片层次接近原始观感，是锁屏默认档位的选择依据。截图见 `_analysis/team/shots/dev-config-0.9.5-wallpaper-off.png` 至 `-level8.png`。
- 锁屏壁纸：设置锁屏壁纸并启用四位 PIN 后，锁屏界面显示照片背景、白色压层与 PIN 键盘，解锁后回到桌面，桌面壁纸与量化档位保持不变。截图 `_analysis/team/shots/dev-config-0.9.5-lock-wallpaper.png`。
- 进程重启：`force-stop` 后多次重新启动，桌面壁纸与量化档位保持生效；关闭档位重启与八级灰运行态各有截图留档。
- 无壁纸回退：恢复默认配置后桌面为纯纸色，锁屏无壁纸时 `LockWallpaperView` 以 `InkColors.PAPER` 填充；截图 `dev-config-0.9.5-restored-default.png`。
- 文件替换：`copyWallpaperFile` 在复制新文件前删除配置中的旧路径文件（:271），真机上设置新壁纸时旧桌面文件被删除的行为在锁屏路径上得到验证；桌面路径的完整替换因文件选择器返回阶段应用被系统回收未能完成二次操作，列为待补测项。
- 收尾：已从 `_analysis/team/device-0.9.5-config-before.xml` 备份恢复设备 `shared_prefs/octoberrain_config.xml`，删除测试产生的两张壁纸文件与设备临时截图，HOME 恢复为 `com.wetao.elauncher`。

## 九、遗留

1. 桌面壁纸替换的二次真机操作未完成，建议 0.9.5 整体验收时补测，重点确认旧文件删除与新文件生成。
2. 应用私有 `files/wallpaper/` 下存在 2026-10-19 10:38 的历史遗留文件 `wallpaper_1792377509385.png`，非本次产生，未清理。
3. 主题导出仅内嵌桌面壁纸（`ThemePayload.wallpaper`），锁屏壁纸与量化档位不随主题导出，0.9.6 主题往返验收时需明确是否纳入。
4. `ThumbCacheRegistry` 的缓存键不区分量化档位；壁纸不走缩略图缓存，当前无交叉影响，文件浏览缩略图若后续引入量化需单独核对。
5. 552×768 真机不在手边，本次截图仅覆盖 S4 480×800；552×768 的几何实算已在 P1 的 `FileMetricsTest` 中覆盖，壁纸侧沿用同一 `ImageSizing` 预算，建议合入前由持有该设备的成员补一组截图。
6. `Contracts.kt` 在任务修订前已由本任务加入桌面与锁屏壁纸方法及 `TooManyFunctions` 抑制；当前文件与 `AppearanceRepositoryImpl` 编译一致，后续由 dev-ui 的 task-30 统一维护该文件。
7. 新增的 15 例测试位于 `core/data/src/test`，未放入 `feature/settings/src/test`，原因是几何与量化内核属于 data 层；该目录在 P5 早期任务修订中属本任务写作用域，当前任务修订未列出，作为偏差记录。
