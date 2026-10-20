# 新工程架构设计

本文档记录 0.9.7 时点的实际架构。原设计中的部分技术选型在实现阶段调整，调整原因与替代实现逐项写在第一节。模块边界、组件协议、设置声明与配置模型沿用原设计，下文按实现状态描述。

## 一、技术选型与取舍

| 维度 | 原计划 | 实际实现 | 说明 |
| --- | --- | --- | --- |
| 语言 | Kotlin 2.1，K2 编译 | Kotlin 2.0.21 | 版本目录 `gradle/libs.versions.toml` 统一固定 |
| UI 体系 | View 加 ViewBinding，单 Activity 加 Fragment | 单 Activity 加自绘 View | `app/.../MainActivity.kt` 为唯一 Activity，页面实现 `Screen` 并由 `ScreenRegistry` 装配；墨水屏逐帧控制与重绘时机可预测，Fragment 与 ViewBinding 未引入 |
| 依赖注入 | Hilt | `AppServices` 服务定位器 | `core/common/.../Contracts.kt:726` 声明 19 个 `lateinit var` 服务，应用启动时由 `OctoberRainApp` 装配；单人开发下 Hilt 的编译期开销与生成代码不划算 |
| 状态管理 | ViewModel 加 StateFlow | StateFlow 加页面自持状态 | `ConfigStore.state` 为全局配置流，页面通过回调与 `requestPartial` 重绘；未引入 ViewModel |
| 持久化 | DataStore 用于配置，Room 用于缓存 | 单键 JSON 配置文件加文件缓存 | `JsonConfigStore` 整体序列化 `LauncherConfig`，`SharedPreferencesConfigPersistence` 负责读写，书架索引、媒体索引、缩略图与壁纸均以文件保存；Room 与 DataStore 未引入 |
| 序列化 | kotlinx.serialization | kotlinx.serialization | 配置类标注 `@Serializable`，`AppJson` 配置宽松解析与默认值 |
| 构建 | AGP 8.7、Gradle 8.11.1、JDK 17、compileSdk 35、minSdk 26、targetSdk 35 | 同名同版本 | `app/build.gradle.kts` 中 `versionCode = 15`、`versionName = "0.9.7"` |
| 测试 | JUnit4、Roborazzi、UIAutomator | 仅 JUnit4 纯 JVM 用例 | 52 个测试类共 469 例，1 例跳过；截图测试与 UIAutomator 冒烟未接入 |

`docs/00-overview.md` 第三节列出的三条主线中，Hilt、Room、DataStore、KSP 注册表、RecyclerView 与 ViewPager2 六项在实现阶段换用替代方案：服务定位器、单键 JSON 配置与文件缓存、运行时 `WidgetRegistry`、自绘分页容器。替换后界面层与数据层的行为边界保持不变，模块划分与协议未受影响。

## 二、模块划分

工程含 14 个 Gradle 工程：壳工程 `app`，13 个库模块。

```
app                       壳工程：MainActivity、启动装配、锁屏、路由、壁纸层、首次引导
core
  common                  契约、配置模型、JSON、日志、PIN 哈希与退避策略、时间工具
  design                  尺寸令牌、配色、字体、通用控件、主题模型与令牌编解码
  device                  设备画像、显示类型、权限门面、使用情况访问判定、HOME 角色
  render                  解码、尺寸计算、抖动、缩略图缓存、刷新调度、画布工具
  data                    配置存储与迁移、应用与外观仓库、通知中心、系统控制、备份与更新
  widget-api              组件协议、运行时注册表、占位绘制
feature
  home                    桌面、分页、图标、负一屏宿主、桌面布局与预览入口
  widgets                 18 个组件实现、组件选择页、钟面样式页
  tray                    状态栏、托盘面板、通知与托盘偏好页
  settings                设置首页与各偏好页、设置声明表、渲染器、美化与图标选择
  content                 书架、微信读书、KOReader、搜索、最近使用、负一屏书架卡片
  files                   文件浏览、相册、图片查看、图片编辑、选图入口
service                   通知监听服务、无障碍服务与悬浮状态栏视图
```

依赖方向为 `app → feature → core`，`feature` 模块之间不互相引用，跨域能力经 `core/common` 的契约与 `AppServices` 取得。`feature:widgets` 依赖 `core:widget-api`，`feature:files` 与 `feature:content` 共用 `core:render` 的解码与缓存。模块清单见 `settings.gradle.kts:1-14`。

## 三、组件协议

组件协议位于 `core/widget-api/src/main/java/com/shuiyu/octoberrain/core/widget/WidgetApi.kt`。

```kotlin
interface WidgetProvider {
    val id: String
    val label: String
    val description: String
    val category: WidgetCategory get() = WidgetCategory.OTHER
    val refreshPolicy: WidgetRefreshPolicy get() = WidgetRefreshPolicy.MANUAL
    val requiredCapabilities: Set<Capability> get() = emptySet()
    fun create(context: Context): WidgetView
}

interface WidgetView : WidgetHitRegionSource {
    fun onSizeChanged(width: Int, height: Int) {}
    fun onConfigChanged(config: LauncherConfig) {}
    fun onRefresh(force: Boolean = false) {}
    fun draw(canvas: Canvas, width: Int, height: Int)
    fun onClick(x: Float, y: Float): Boolean = false
    val transparentBackground: Boolean get() = false
    fun onLongPress() {}
    fun onLongPress(x: Float, y: Float) {}
    fun onDestroy() {}
}
```

`WidgetApi.kt:81` 声明 `WidgetView`，`:255` 声明 `WidgetProvider`，`:145` 声明宿主协议 `WidgetHost`（`requestWidgetDraw` 与 `openDestination`），`:159` 提供基类 `BaseWidgetView`（缓存尺寸、提供画笔与重绘入口），`:268` 提供运行时注册表 `WidgetRegistry`（`register`、`get`、`all`、`create`）。注册在模块安装时完成，未使用 KSP 生成注册表。

命中区在本版加入协议：`WidgetHitRegion`（`:24`）以组件内局部像素描述矩形与动作标识，`WidgetHitRegionSource`（`:52`）的 `hitRegions()` 默认返回空表，`hitRegionAt(x, y)`（`:69`）按列表顺序取首个命中。宿主先判定命中区，再由触控类型决定调用 `onClick(x, y)` 或 `onLongPress(x, y)`；无参 `onLongPress()` 保留并转调坐标版本，坐标未知时传 `NaN`。

`feature/widgets/.../WidgetCatalog.kt` 以声明方式集中 6 个条目：

| 分类 | 条目 |
| --- | --- |
| 时间 `TIME` | 时钟 `clock`、日历 `calendar` |
| 本地内容 `LOCAL` | 使用统计 `usage`、图片 `image` |
| 微信读书 `WEREAD` | 整体卡片 `weread_board` |
| 其他 `OTHER` | 占位 `placeholder` |

刷新策略三值：`MINUTE` 仅日历一条，`EVENT` 为使用统计与微信读书整体卡片，`MANUAL` 为时钟、图片与占位。`WidgetRefreshPolicy.triggersOnMinute()` 由 `feature/home/.../HomeView.kt` 消费，整分刷新只处理 `MINUTE` 条目；`EVENT` 与 `MANUAL` 由数据通知与组件内部定时器驱动。能力依赖声明于 `requiredCapabilities`（使用情况访问、存储、微信读书），宿主侧的能力过滤尚未接入，声明目前只由 `WidgetCatalogTest` 校验，见第九节。

## 四、设备适配层

`core/device/.../DeviceProfile.kt` 定义运行时设备画像：

```kotlin
data class DeviceProfile(
    val widthPx: Int, val heightPx: Int,
    val widthDp: Int, val heightDp: Int,
    val density: Float,
    val sizeClass: SizeClass,
    val heightClass: HeightClass,
    val display: DisplayClass,
    val hasFrontLight: Boolean,
    val statusBarInsetPx: Int,
    val navigationInsetPx: Int,
    val longEdgePx: Int, val shortEdgePx: Int,
)
```

派生属性 `aspectRatio`、`isLandscape`、`preferPartialRefresh`。画像由 `DeviceProfileDetector.detect(context)` 在应用启动时计算一次，`DeviceRuntime.install` 挂载；显示类型按 `DisplayClass`（`EinkFast`、`EinkFull`、`Lcd`）区分，前光能力经系统设置键探测，探测失败降级为不支持（`DeviceProfileDetector.resolveFrontLight`）。`feature` 层不读取 `DisplayMetrics`，尺寸经 `core:design` 的令牌换算。

## 五、数据层

配置存储在 `core/data`：`JsonConfigStore`（`JsonConfigStore.kt:40`）持有 `MutableStateFlow<LauncherConfig>`，`edit`（`:73`）先写盘成功再发布新状态，`load`（`:98`）读取后执行 `ConfigMigration.migrate`；持久化细节由 `ConfigPersistence`（`ConfigPersistence.kt:11`）隔离，默认实现为 `SharedPreferencesConfigPersistence`（`:37`，单键 `octoberrain_config`）。`LauncherConfig.CURRENT_VERSION = 7`（`core/common/.../Models.kt:374`），`ConfigMigration.kt` 按版本逐级补默认字段，读到更高版本配置时保留原版本并在内存按可读字段使用。

`LauncherConfig` 按域分组：`desktop`、`appearance`、`tray`、`lock`、`notifications`、`integrations`、`onboarding`、`negativePage`。序列化配置见 `core/common/.../AppJson.kt`：忽略未知键、编码默认值、宽松解析、省略显式 null。远程数据与本地索引以文件保存：`filesDir/media-index.json`、`books` 扫描结果、`thumbs` 缩略图缓存与索引文件、壁纸文件。备份由 `core/data/.../BackupServiceImpl.kt` 打包为 zip，包含配置 JSON 与媒体索引，敏感字段写入前剔除。

## 六、渲染层

`core/render` 提供解码、缩放、量化、缓存与刷新五组能力，共 13 个文件。

`ImageDecoder`（210 行）：`size(path)` 读边界，`decode(path, minPx, keepAlpha)` 按目标长边取样解码，`decodeWithSample(path, sample, keepAlpha)` 指定采样倍数，`decodeStream(input, minPx, keepAlpha)` 处理流式来源（32 MB 上限）；解码后按 EXIF 方向矩阵归一，异常方向按原图返回。

`ImageSizing`（113 行）：`MAX_EDGE = 3200`、`LIMIT_PIXELS = 80_000_000`、`targetEdge`、`sampleFor`、`requestSample`、`sampledPixels`、`fitsHeap`，目标尺寸与内存预算集中在此。

`ThumbCache`（约 240 行）：两级缓存，内存 `LruCache` 默认 8 MB，磁盘 PNG 默认 32 MB，`put` 在抖动开启时先量化再写入，索引按目标长边与抖动开关分文件（`ThumbIndex.fileName(targetEdge, ditherEnabled)`），`get`、`peekMemory`、`invalidate`、`warmUp`、`stats`、`clear`。`ThumbCacheRegistry` 按 `(targetEdge, dither)` 维护进程内实例，并提供质量档与抖动开关的全局设置。

`Ditherer` 与 `DitherCore`：`dither(source)` 四档、`dither(source, levels)` 支持 2、4、8 档，误差扩散按 Floyd–Steinberg 权重 7/3/5/1；`grayscale`、`threshold` 供查看器与缩略图使用。

`RefreshScheduler`（121 行）：`attach(requester, intervalMinutes)`、`notifyContentChanged()`（合并同一帧内的局部重绘请求）、`requestFullRefresh(reason)`、`updateInterval(minutes)`、`onFullRefreshPerformed()`、`millisSinceFullRefresh()`；定时整屏刷新默认 10 分钟，由设置项写入。

`InkCanvas` 提供逐像素矩形、圆角矩形、胶囊、细线与反色绘制，`BitmapPipe` 保留为旧解码入口的兼容封装。

## 七、界面层

桌面为单 Activity 加自绘视图：`MainActivity` 持有页面栈，`LauncherRouter` 负责 `navigate`、`back`、`openApp`、`openUrl`、`toast`；页面实现 `Screen`（`core/common/.../Contracts.kt:550`）并由 `ScreenRegistry`（`:574`）按 `Destination` 匹配工厂。内置页面包括桌面、文件、相册、图片查看、图片编辑、设置各页、书架、微信读书、KOReader、搜索、最近使用与负一屏。

桌面绘制由 `HomeView` 完成，分页几何由 `DesktopMetrics` 计算：列数由设备宽度与图标边长推导，行数在 2 至 12（宫格）或 10（列表）之间按可用高度夹取，首页在启用组件时按 `MIN_FIRST_ROWS` 保底，组件占用区高度由 `resolveWidgetBlock` 与 `heightFor` 反算。横向滑动翻页，阈值 48 dp；图标长按 350 ms 进入整理或菜单；托盘由顶部状态栏区域下拉打开。

通用控件在 `core/design`：`InkWidgets` 提供标题、正文、按钮、卡片背景与分隔线，`InkPage` 提供带返回栏的滚动页面骨架，`InkDialogs` 提供确认与输入对话框，`InkGlyphs` 提供矢量图标。`feature/files` 与 `feature/content` 的分页容器各自以 `View` 子类实现，未使用 `RecyclerView` 与 `ViewPager2`。

## 八、设置体系

设置声明表位于 `feature/settings/.../SettingsSpec.kt`：`SettingsCatalog`（`:13`）汇总 20 个分组（外观 6、托盘 3、锁屏 2、系统 4、书架 5）与 72 条 `SettingSpec`（外观 27、托盘 15、锁屏 5、系统 14、书架 11），`specs` 聚合在 `:98`。

```kotlin
internal data class SettingSpec(
    val id: String, val group: String, val title: String,
    val hint: String? = null,
    val control: SettingControl,
    val effect: SettingEffect = SettingEffect.PARTIAL,
    val visible: (LauncherConfig) -> Boolean = { true },
    val write: ((LauncherConfig) -> LauncherConfig)? = null,
    val afterWrite: (LauncherConfig) -> Unit = {},
)
```

控件八类：`Toggle`、`Choice`、`Stepper`、`Slider`、`Info`、`Action`、`Button`、`Custom`（`SettingSpec.kt:155`）。生效方式七种：`NONE`、`PARTIAL`、`FULL`、`APPEARANCE`、`APPEARANCE_REBUILD`、`REBUILD`、`CONTENT_REFRESH`（`:210`），由 `SettingEffects.kt` 统一分发。页面只提供动作与自定义行，行控件由 `SettingsSpecRenderer` 按声明生成；新增一项设置只需在对应 `*Specs.kt` 追加一条声明。声明按页面拆分：`AppearanceSpecs.kt`、`TraySpecs.kt`、`LockSpecs.kt`、`SystemSpecs.kt`、`BookshelfSpecs.kt`。

## 九、系统能力与服务

通知监听与无障碍服务位于 `service` 模块（3 个文件、891 行）：`NotificationRelayService` 订阅通知发布与移除事件，向 `NotificationCenter` 推送；`OverlayAccessibilityService` 维护悬浮状态栏与锁屏覆盖层；`OverlayStatusBarView` 绘制悬浮状态栏。界面在 `feature:tray` 与 `app`。

`core/data` 的系统控制 `SystemControlsImpl` 在 `start()` 中注册广播接收器并读取粘性电量，`dispose()` 对称注销；WiFi 状态读取包在 `runCatching` 中，异常时降级为未知状态。前光通道优先使用 `core/device` 探测到的能力标志，未探测到前光时不写亮度键。使用情况访问由 `core/device/.../UsageAccessChecker.kt` 统一判定，Android 10 及以上用 `unsafeCheckOpNoThrow`，Android 8 至 9 用 `checkOpNoThrow`。

模块装载由 `OctoberRainApp.installModules`（`OctoberRainApp.kt:92-100`）以固定类名字符串执行 `Class.forName`，无参构造实例化后调用 `LauncherModule.install`。混淆规则 `app/proguard-rules.pro` 为反射入口、序列化生成物与组件实现三类保留：5 个模块类保留无参构造，`core/common` 的 `Companion` 与 `*$$serializer` 保留，`WidgetProvider` 与 `WidgetView` 实现类整体保留。注意当前反射入口已有 6 个模块（新增 `FilesModule`），而规则只覆盖 5 个，`FilesModule` 缺少保留规则，属待修正项。

## 十、工程实践

版本目录集中在 `gradle/libs.versions.toml`：AGP 8.7.3、Kotlin 2.0.21、compileSdk 35、minSdk 26、targetSdk 35、coroutines 1.7.3、serialization 1.7.3、JUnit 4.13.2、detekt 1.23.7、ktlint 插件 12.1.2。

静态检查由根 `build.gradle.kts` 统一接入：`ktlintCheck` 与 `detekt` 对所有源码集生效，失败即中止；detekt 按模块维护 12 份基线 `config/detekt/baseline-<模块>.xml`，累计 237 条存量问题；Android Lint 由 `check` 触发，基线 14 份、合计 72 条。行宽上限 120，由 `.editorconfig` 与 detekt 的 `MaxLineLength` 共同约束。`check` 依赖格式、缺陷扫描、单元测试与 Lint。

持续集成在 `.github/workflows/build.yml`：构建检查（`assembleDebug` 与 `assembleRelease`）、测试检查（`testDebugUnitTest --continue`）、静态检查（`ktlintCheck detekt --continue`）三步，随后上传测试报告、静态检查报告、调试包与发布包及混淆映射。

性能：0.9.7 的设备记录保存在 `_analysis/verify/`，包含查看器首帧解码耗时（18 至 242 ms）、图片查看页创建耗时（18 至 41 ms）与 debug 包体积（约 4 MB）。冷启动 800 ms、常驻内存 120 MB、APK 8 MB 三项预算保留为后续版本复核基准，本版未新增阈值。

安全：PIN 采用 PBKDF2-HMAC-SHA256，默认 12 万轮、16 字节盐，摘要串 `pbkdf2$轮数$十六进制`，旧单次 SHA-256 摘要在首次校验成功后升级；连续失败按 `PinLockPolicy` 指数退避。release 构建沿用调试签名（D7），正式发布前替换。

## 十一、与上游的隔离

包名与 `applicationId` 为 `com.shuiyu.octoberrain`，类名、资源名、字符串、域名、FileProvider authority（`${applicationId}.files`）与无障碍配置全部自有。构建开启 R8 混淆与资源压缩，保留规则只覆盖运行期必需的反射、序列化与组件协议三类。逐项清单见 [04-identity-removal.md](04-identity-removal.md)。
