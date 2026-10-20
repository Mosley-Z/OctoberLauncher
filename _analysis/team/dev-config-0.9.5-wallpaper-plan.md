# 0.9.5 P5 壁纸双通道只读规划

编写者 dev-config，日期 2026-10-20。本文件只做规划，未修改任何源码。依据为第五节计划、`docs/15-V1.0开发计划.md` 第 122 与 166 行、`docs/16-墨盘移植决策.md`、现有壁纸与锁屏实现。

## 一、现状核对与计划差异

### 桌面壁纸

`core/common/src/main/java/com/shuiyu/octoberrain/core/common/Models.kt:175-200` 的 `AppearanceConfig` 已有 `wallpaperPath: String? = null`、`wallpaperWhiteOverlay: Boolean = false`、`wallpaperDim: Float = 0f`，默认值与计划一致。

`core/common/src/main/java/com/shuiyu/octoberrain/core/common/Contracts.kt:729-740` 的 `AppearanceRepository` 只有 `setWallpaper`、`clearWallpaper`、`loadWallpaper(width, height)` 三项，无量化参数。

`core/data/src/main/java/com/shuiyu/octoberrain/core/data/AppearanceRepositoryImpl.kt:68-76` 的 `loadWallpaper` 直接调用 `decodeSampled`（同文件 `:236-251`）：`ImageDecoder.size` 取边界、`ImageSizing.requestSample` 采样、`decodeWithSample(keepAlpha = true)` 解码，再创建目标尺寸 ARGB_8888 位图，`drawColor(Color.WHITE)` 后把整幅位图拉伸到目标矩形。当前实现为拉伸填充，未保持比例，也未量化。

`app/src/main/java/com/shuiyu/octoberrain/WallpaperView.kt:70-96` 只按配置中的 `wallpaperPath` 判断是否重载（`loadedPath`），未观察量化档位；`:104-120` 的 `onDraw` 先画位图，再按需叠 `0xE0` 白色层与按比例的黑色变暗层。

`feature/settings/src/main/java/com/shuiyu/octoberrain/feature/settings/AppearanceSpecs.kt:33-38` 的壁纸分组 `GROUP_APPEARANCE_WALLPAPER` 现有三项：`appearance.wallpaper.pick`（:71-86）、`appearance.wallpaper.overlay`（:87-98）、`appearance.wallpaper.clear`（:99-108），`appearance.wallpaper.dim`（:109-124）位于清除项之后。`AppearanceScreen.kt:36-44` 的动作表仅有 `wallpaper.pick` 与 `wallpaper.clear`。

### 锁屏

`app/src/main/java/com/shuiyu/octoberrain/MainActivity.kt:140-199` 的 `buildLayout` 创建 `wallpaperLayer`（:146-153）、`statusHolder`（:154-163）、`contentFrame`（:164-172）、`trayHolder`（:173-180）、`lockHolder`（:181-189），并依次 `addView`（:191-195），`lockHolder` 位于最上层之下、状态栏之下。

`:336-354` 的 `showLock` 通过 `AppServices.lock.createOverlay(this)` 取得覆盖层并加入 `lockHolder`；`:362-366` 的 `hideLock` 清空 `lockHolder`；`:307-310` 的 `updateStatusBarBackground` 仅在桌面根页透明，其余页面为纸白，锁屏期间状态栏会被纸白覆盖。

`app/src/main/java/com/shuiyu/octoberrain/LockOverlayView.kt:32-35` 在 `init` 中 `setBackgroundColor(InkColors.PAPER)`，即锁屏背景为不透明纸色，`:37-96` 自绘标题、圆点与键盘，`:98-109` 处理触摸。背景在 `:33`。

`app/src/main/java/com/shuiyu/octoberrain/LockGateImpl.kt:108-125` 的 `createOverlay` 只构造 `LockOverlayView` 并注入校验、失败次数与锁定秒数回调，与壁纸无关，本阶段无需改动。

### 与计划的差异

计划第五节提出的字段 `lockWallpaperPath`、`wallpaperQuantize`、`lockWallpaperQuantize` 均未落地；`loadWallpaper` 无量量化参数；`decodeSampled` 为拉伸而非裁剪；`WallpaperView` 未观察量化档位；锁屏无壁纸层，`LockOverlayView` 背景不透明；设置项与动作未声明。其余结论与计划一致。

## 二、配置字段

在 `AppearanceConfig` 末尾追加：

```kotlin
val lockWallpaperPath: String? = null,
val wallpaperQuantize: Int = 0,
val lockWallpaperQuantize: Int = 2,
```

三项均带默认值。`AppJson` 已开启 `ignoreUnknownKeys = true`，旧配置解码不受影响，新字段按默认值补齐；`ConfigMigration` 当前版本 7 不需要新增迁移步骤，`LauncherConfig.CURRENT_VERSION` 不变。字段注释说明量化档位 0 为关闭、1 为二级灰、2 为四级灰、3 为八级灰，锁屏默认四级。

## 三、仓库接口与实现

### 接口签名

`AppearanceRepository` 追加：

```kotlin
suspend fun setLockWallpaper(uri: android.net.Uri): Boolean
fun clearLockWallpaper()
fun loadLockWallpaper(width: Int, height: Int): android.graphics.Bitmap?
fun loadWallpaper(width: Int, height: Int, quantize: Int = 0): android.graphics.Bitmap?
```

`loadWallpaper` 的量化参数带默认值 0，现有调用方无需修改。`loadLockWallpaper` 从配置读取 `lockWallpaperQuantize`，与桌面壁纸解耦。

### 实现改造步骤

1. 壁纸目录仍为 `filesDir/wallpaper`。桌面文件命名保持 `wallpaper_<毫秒>.png`，锁屏文件命名 `lock_<毫秒>.png`。
2. `setWallpaper` 现有逻辑保留，仅把解码校验后的保存目标与配置写入维持原样；`setLockWallpaper` 复制同构逻辑，替换前删除旧锁屏文件，写入后 `config.edit` 更新 `lockWallpaperPath`。
3. `clearWallpaper` 已删除旧文件并置空路径；新增 `clearLockWallpaper` 做同样处理。
4. 把 `decodeSampled(path, width, height)` 改为 `decodeWallpaper(path, width, height, quantize)`：
   - 取边界与采样，解码得到源位图；
   - 计算居中裁剪源矩形：源宽高比大于目标宽高比时裁左右，否则裁上下，保持比例填充目标区域；
   - 目标 ARGB_8888 位图先 `drawColor(Color.WHITE)`，再按裁剪矩形绘制；
   - 量化档位非 0 时按档位映射到 `Ditherer.dither(output, levels)`，结果替换输出并回收中间位图；
   - 全部在调用方已有的 IO 线程执行，函数本身不切换线程。
5. 旧文件清理：替换桌面或锁屏壁纸时删除同类的旧文件；清除时删除当前文件。主题导入产生的 `theme_<毫秒>.png` 不在本阶段清理，属既有行为。
6. `loadWallpaper` 调用 `decodeWallpaper(path, width, height, appearance.wallpaperQuantize)`；`loadLockWallpaper` 调用 `decodeWallpaper(path, width, height, appearance.lockWallpaperQuantize)`，路径为空返回 null。

### 量化档位对齐

新建纯逻辑映射（建议置于 `core/render`，便于 JVM 测试）：

```kotlin
object WallpaperLevels {
    fun ditherLevels(quantize: Int): Int = when (quantize) {
        1 -> 2
        2 -> 4
        3 -> 8
        else -> 0
    }
}
```

`DitherCore.normalizeLevels` 只接受 2、4、8，其余值回退 4，因此只能把映射后的 2、4、8 传入 `Ditherer.dither`；档位 0 不调用抖动，档位 1 不使用 `normalizeLevels` 的默认回退，避免「关闭」被意外量化为四级。

`Ditherer.dither` 当前对整幅位图分配 `FloatArray`，480×800 约 1.5MB，824×1648 约 5.4MB，加输出位图峰值可控；必须在 IO 线程执行，且量化在目标尺寸位图上进行，不先放大原图。

## 四、锁屏壁纸视图与层序

新增 `app/src/main/java/com/shuiyu/octoberrain/LockWallpaperView.kt`，与 `WallpaperView` 同构：

- 尺寸为 `MATCH_PARENT` 全屏，覆盖状态栏区域；
- 观察 `AppServices.config.state` 的 `lockWallpaperPath` 与 `lockWallpaperQuantize`，变化时在 IO 线程调用 `loadLockWallpaper(width, height)`；
- 无壁纸或解码失败时绘制 `InkColors.PAPER`，保持纯纸色回退；
- 有壁纸时先拉伸绘制位图，再叠加白色压层，透明度取 `0xB8`（184），即约 72% 白；深色壁纸下键盘黑线与其对比约 3:1，若真机键盘边缘辨识不足再上调到 `0xC0`；
- 不使用 `wallpaperWhiteOverlay` 与 `wallpaperDim`，锁屏压层独立，避免与桌面设置互相牵制。

`MainActivity` 改动：

1. 新增字段 `lockWallpaperLayer` 与 `lockOverlay` 并列；
2. `buildLayout` 在 `lockHolder` 创建后先加入 `LockWallpaperView`（`MATCH_PARENT`），再加入空占位，保证 `lockHolder` 的 0 号子视图为壁纸层；
3. `showLock`（:336-354）在取得覆盖层时先确保壁纸层在 `lockHolder` 内，再把覆盖层加到壁纸层之上；已有覆盖层时恢复两者可见性；
4. `hideLock`（:362-366）只移除并置空覆盖层，保留壁纸层，避免下次上锁重新解码；
5. `updateStatusBarBackground`（:307-310）增加锁屏条件：`AppServices.lock.isLocked` 时使用 `Color.TRANSPARENT`，让壁纸贯通状态栏；`showLock` 与 `hideLock` 结束后调用一次；
6. `LockOverlayView.kt:33` 的 `setBackgroundColor(InkColors.PAPER)` 改为 `Color.TRANSPARENT`，纸色兜底由壁纸层承担。

层序自下而上：`wallpaperLayer`（桌面）→ `contentFrame` → `trayHolder` → `lockHolder`（壁纸层、覆盖层）→ `statusHolder`。锁屏壁纸只存在于 `lockHolder`，桌面壁纸不受影响。

## 五、设置项与动作

`AppearanceSpecs` 壁纸分组副标题改为说明桌面与锁屏壁纸分别设置。在 `appearance.wallpaper.dim`（:124）之后追加四项：

1. `appearance.lockWallpaper.pick`，标题「选择锁屏壁纸」，动作型；
2. `appearance.lockWallpaper.clear`，标题「清除锁屏壁纸」，动作型；
3. `appearance.wallpaper.quantize`，标题「桌面壁纸量化」，`choiceAppearance` 四选项「关闭、二级灰、四级灰、八级灰」，读写 `wallpaperQuantize`，`SettingEffect.APPEARANCE`；
4. `appearance.lockWallpaper.quantize`，标题「锁屏壁纸量化」，同四选项，默认四级，`SettingEffect.APPEARANCE`。

`AppearanceScreen` 动作表增加两项：`appearance.lockWallpaper.pick` 复用 `SettingsUi.pickFile(context, "选择锁屏壁纸", IMAGE_MIME_TYPES, IMAGE_EXTENSIONS)`，成功后调用 `setLockWallpaper`、`ThumbCacheRegistry.invalidate` 旧文件、`RefreshScheduler.notifyContentChanged` 与 `requestFullRefresh`，与现有壁纸动作一致；`appearance.lockWallpaper.clear` 经 `InkDialogs.confirm` 确认后调用 `clearLockWallpaper`。

`WallpaperView` 的重载判定加入量化档位：记录 `loadedQuantize`，路径或档位变化时重新调用 `loadWallpaper(width, height, quantize)`；白色压层与变暗层仍在量化之后叠加。

## 六、备份与主题影响

备份导出编码完整配置，`wallpaperPath` 与 `lockWallpaperPath` 均为本机绝对路径，随备份写入 `config.json`；导入后目标设备路径可能失效。`WallpaperView` 与 `LockWallpaperView` 在解码失败时静默回退纯纸色，不崩溃。P5 不增加路径清洗，避免与凭据剔除逻辑交叉，只在报告中记录该行为。

主题导出结构 `ThemePayload` 位于 `AppearanceRepositoryImpl.kt:257-265`，只内嵌桌面壁纸的 Base64 与图标、字体字段，不包含锁屏壁纸与量化档位。主题导入沿用既定字段覆盖方式，新字段保持本机值，因此导入主题不会清除锁屏壁纸。若后续需要在主题中携带锁屏壁纸，应提升 `ThemePayload.version` 并追加字段，本阶段不做。

备份恢复以本机锁配置与微信读书凭据覆盖备份值，外观配置整体取自备份，新字段一并恢复；跨设备时锁屏壁纸回退纯纸色，桌面壁纸同理。

## 七、预估行数与拆分

| 文件 | 预估行数 | 说明 |
| --- | --- | --- |
| `core/common/.../Models.kt` | +6 | 三个字段与注释 |
| `core/common/.../Contracts.kt` | +12 | 两个方法加签名注释 |
| `core/render/.../WallpaperLevels.kt` | 新增 24 | 纯逻辑映射 |
| `core/render/src/test/.../WallpaperLevelsTest.kt` | 新增 60 | 档位映射与非法值 |
| `core/data/.../AppearanceRepositoryImpl.kt` | +150 | 解码重构、锁屏三方法、裁剪与量化 |
| `app/.../WallpaperView.kt` | +25 | 量化档位重载 |
| `app/.../LockWallpaperView.kt` | 新增 110 | 锁屏壁纸层 |
| `app/.../LockOverlayView.kt` | +3 | 背景透明 |
| `app/.../MainActivity.kt` | +28 | 字段、层序、显隐、状态栏 |
| `feature/settings/.../AppearanceSpecs.kt` | +95 | 四项声明与选项 |
| `feature/settings/.../AppearanceScreen.kt` | +65 | 两个动作与文件选择 |
| `LockGateImpl.kt` | 0 | 计划中的 12 行无需改动 |

生产代码合计约 470 行，单文件均低于 600 行。`AppearanceRepositoryImpl` 改造后约 420 行，接近上限，若继续膨胀应把裁剪与量化拆到 `core/render/.../WallpaperFitter.kt`（约 70 行），由仓库调用。

## 八、真机验收与截图清单

验收步骤：

1. 用 480×800 设备进入设置→外观→壁纸，选择一张 3:4 竖图作为锁屏壁纸，息屏后亮屏或冷启动，确认锁屏壁纸、键盘与提示文字可读，四位密码可解锁。
2. 桌面壁纸依次切换关闭、二级、四级、八级，确认每次立即重绘且量化档位变化触发重新解码。
3. 选取一张 16:9 横图重复步骤 1，确认锁屏为居中裁剪、无拉伸变形、无上下白边。
4. 更换与清除锁屏壁纸，检查 `files/wallpaper` 目录只保留当前文件。
5. 切换桌面与锁屏量化档位，观察 logcat 无 OOM、无解码失败、切换无 ANR。
6. 导出备份后在同一设备导入，壁纸路径有效；跨设备导入只记录回退纯纸色的表现。
7. 824×1648 设备重复步骤 1 与 2，确认大屏裁切与键盘布局正常。

截图清单：`s4-lock.png`、`s4-desktop-off.png`、`s4-desktop-4.png`、`s4-desktop-8.png`、`s4-settings-wallpaper.png`、`824-lock.png`、`824-desktop.png`，以及对应 logcat 片段。

## 九、特别核对结论

### 透明度压层

`LockOverlayView` 背景改为透明后，纸色由 `LockWallpaperView` 的白色压层与兜底色提供，压层取 `0xB8`。压层过淡会削弱键盘线条，过浓会掩盖壁纸，`0xB8` 对应 72% 白，适合墨水屏 16 级灰阶的中间调；真机若文字对比不足，按 0xB8→0xC0 调整并记录截图。

### 两种分辨率的裁切

目标宽高比分别为 0.600（480×800）与 0.500（824×1648）。3:4 竖图在 0.600 目标下左右各裁约 10%，在 0.500 目标下左右各裁约 17%；16:9 横图在 0.600 目标下保留约 45% 宽度，在 0.500 目标下保留约 37.5% 宽度。采用居中覆盖裁剪，保持比例，不使用拉伸与留白；主体不在中央时需要用户自行选择构图合适的图片，本阶段不提供构图调整。

### 缓存键

壁纸加载不经过 `ThumbCacheRegistry`，直接由 `AppearanceRepositoryImpl` 解码，因此量化档位不同不会读到彼此缓存。`ThumbCacheRegistry.invalidate` 只在壁纸文件被替换时清理旧文件对应的缩略图条目，与量化无关。`ThumbKey` 的摘要只含路径、修改时间与目标长边，`ThumbCacheRegistry.CacheKey` 只含目标长边与抖动布尔值，不区分 2、4、8 档；当前无影响，若将来把壁纸预览接入缩略图缓存，需要把档位加入缓存键，否则会互相污染。
