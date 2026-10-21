# 十雨桌面 开发交接说明

> 2026-10-22 整理说明：本文保留早期设计/交接语境。当前协作、Git、构建和验收流程以 [35开发流程](35-development-workflow.md) 为准；版本、模块数、测试数和设备状态须从源码/当轮证据复查。

本文件面向功能模块开发，说明公共契约、构建方式、分工边界与协作规则。阅读顺序：本文件 → `03-target-architecture.md` → `01-feature-inventory.md`。

## 一、工程与构建

工程根目录 `D:\DSHStation\OctoberLauncher`，Kotlin + View 体系，不使用 Compose、Hilt、Room、DataStore、RecyclerView。

构建命令（在工程根目录执行，约 3 秒增量）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build.ps1
```

单模块编译：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\build.ps1 -Variant debug
# 或直接调用 Gradle
D:\DSHStation\.toolchain\gradle\gradle-8.11.1\bin\gradle.bat --offline :feature:home:compileDebugKotlin
```

- 工具链路径固定：JDK 17、Android SDK、Gradle 8.11.1 均在 `D:\DSHStation\.toolchain`，由 `scripts\build.ps1` 自动注入环境变量。
- 禁止新增未经确认的依赖。当前可用：kotlinx-coroutines 1.7.3、kotlinx-serialization-json 1.7.3、androidx.core-ktx 1.15.0。
- 真机安装由 Lead 统一执行，单个模块开发者只做编译验证，避免多路安装互相覆盖。真机型号 S4，480×800，density 219，Android 11。

## 二、模块边界

```
app  ──►  feature/home  feature/widgets  feature/tray  feature/settings  feature/content  service
 │
 └────►  core/common  core/device  core/design  core/render  core/data  core/widget-api
```

- `core/*` 与 `app` 由 Lead 维护，功能开发不修改其中文件；需要新增公共能力时在任务中说明，由 Lead 补充。
- 功能模块之间不互相依赖，跨模块通信一律经过 `core/common/Contracts.kt` 中的接口与 `AppServices`。
- 每个功能模块提供一个 `LauncherModule` 实现，类名固定为 `com.shuiyu.octoberrain.feature.<模块名>.<XxxModule`，由 `app/OctoberRainApp.kt` 反射加载：

| 模块 | 入口类 |
| --- | --- |
| feature:home | `com.shuiyu.octoberrain.feature.home.HomeModule` |
| feature:widgets | `com.shuiyu.octoberrain.feature.widgets.WidgetsModule` |
| feature:tray | `com.shuiyu.octoberrain.feature.tray.TrayModule` |
| feature:settings | `com.shuiyu.octoberrain.feature.settings.SettingsModule` |
| feature:content | `com.shuiyu.octoberrain.feature.content.ContentModule` |

模块缺失时应用仍可启动并显示占位页，因此各模块可以独立提交。

## 三、公共契约（core/common）

### 配置

`AppServices.config: ConfigStore`

- `state: StateFlow<LauncherConfig>` 读取当前配置。
- `edit { current -> current.copy(...) }` 以事务方式写入，变换函数必须是纯函数。

`LauncherConfig` 分区：`desktop`、`appearance`、`tray`、`lock`、`integrations`、`onboarding`。

- `DesktopConfig`：`kind`（GRID/LIST）、`widgetId`、`iconOnly`、`sortMode`、`hiddenPackages`、`order`、`pageLimit`、`columns`、`rows`、`showPageIndicator`、`longPressEditMs`、`swipeMinDp`、`widgetImagePath`。
- `AppearanceConfig`：`wallpaperPath`、`labelFontPath`、`clockFont`、`themeId`、`iconPackId`、`textScale`、`showLabels`、`highContrast`、`statusBarHeightDp` 等。
- `TrayConfig`：`enabled`、`notificationLimit`、`brightnessCold/Warm`、`mediaEnabled`、`overlayEnabled`、`showWifi/Bluetooth/BatteryPercent`、`autoRefreshMinutes`、`volumeKeyFlip`。
- `LockConfig`：`pinHash`、`salt`、`enabled`、`autoLock`。
- `IntegrationsConfig`：微信读书密钥、KOReader 开关、书架目录、天气开关与城市、使用统计开关、更新地址。

### 应用目录

`AppServices.catalog: AppCatalog`

- `apps: StateFlow<List<InstalledApp>>`、`refresh()`、`loadIcon(packageName, sizePx)`、`loadSpecialIcon(entry, sizePx)`、`launch(app)`、`launchSpecial(entry)`。

### 内容

`AppServices.content: ContentSource`（`readingSnapshot`、`weather`、`usageToday`、`bookshelf`、`wereadShelf`、`wereadMonth`、`wereadHighlights`、`koreaderHeat`、`searchBooks`、`refreshAll`）。

### 系统能力

`AppServices.system: SystemControls`：`wifi`、`bluetooth`、`frontLight`、`media`、`battery` 五个 `StateFlow`，以及 `setWifi`、`setBluetooth`、`setFrontLight(cold, warm)`、`hasFrontLight`、`openWifiSettings` 等方法。

### 通知

`AppServices.notifications: NotificationFeed`：`items`、`canRead()`、`dismiss(key)`、`dismissAll()`、`openAccessSettings()`。

### 路由与页面

`AppServices.router: Router`：`navigate(Destination)`、`back()`、`openApp(packageName)`、`openUrl(url)`、`toast(message)`。

页面统一实现 `Screen`：

```kotlin
interface Screen {
    fun createView(context: Context): View
    fun onResume() {}
    fun onPause() {}
    fun onBack(): Boolean = false
    fun onDestroy() {}
}
```

模块在 `LauncherModule.install` 中注册 `ScreenProvider`：

```kotlin
ScreenRegistry.register(object : ScreenProvider {
    override fun create(destination: Destination): Screen? = when (destination) {
        is Destination.Settings -> SettingsScreen()
        else -> null
    }
})
```

宿主维护页面栈，返回键优先交给当前页面的 `onBack()`。

### 锁屏、托盘、刷新

- `AppServices.lock: LockGate`：`isLocked`、`hasPin()`、`lock()`、`unlock(pin)`、`setPin(pin)`、`clearPin()`、`createOverlay(context)`、`onScreenOff()`。宿主已实现加盐 PIN 与覆盖层，设置页只需调用。
- `AppServices.tray: TrayController`：`isOpen`、`open()`、`close()`、`toggle()`、`createOverlay(context)`、`createStatusBar(context)`。托盘模块在安装时用自身实现替换该服务。
- `AppServices.refresh: RefreshRequester`：`requestFullRefresh()` 黑白闪烁两次，`requestPartial()` 局部重绘。
- `AppServices.permissions: Permissions`：权限快照与七个跳转方法。
- `AppServices.appearance: AppearanceRepository`：壁纸、主题导入导出、图标包与主题列表。
- `AppServices.updates: UpdateService`：检查、下载、安装、更新日志。
- `AppServices.fonts: FontProvider`：标签字体与钟面字体。

## 四、设计层（core/design）

`DesignRuntime.tokens: InkTokens` 提供全部尺寸，单位换算一律走令牌：

- `dp(Float)`、`dpF(Float)`、`textSize(Float)`（已含密度与字号缩放）。
- 间距：`pageGutter`、`pageTop`、`pageBottom`、`gap`、`gapLarge`。
- 文本：`textTitle`、`textHeading`、`textBody`、`textCaption`、`textSmall`、`textStatus`。
- 网格：`cellMinWidth`、`iconMax`、`iconFrame(cellWidth)`、`labelHeight`。
- 列表：`listRowHeight`、`listIcon`。
- 栏位：`statusBarHeight`、`topBarHeight`、`trayRow`、`trayBar`、`buttonHeight`、`radius`、`stroke`。

`DesignRuntime` 还提供 `typeface()`、`typeface(id)`、`textPaint(size, color, typeface, outline)`、`fillPaint(color)`、`strokePaint(color, width)`。

颜色：`InkColors.PAPER`、`INK`、`INK_2`、`INK_3`、`INK_4`。

构件：`InkWidgets`（`textView/title/heading/body/caption/button/row/labelRow/divider/spacer/background`）、`InkPage`（带返回栏的二级页面容器）、`InkGlyphs`（线稿图标枚举与视图）、`InkDialogs`（`confirm/input/choose/toast`）。

墨水量优先：白底黑字、1px 级描边、无阴影、无动画、无渐变。整页内容变化后调用 `AppServices.refresh.requestPartial()`，跨页切换或大量重绘后调用 `requestFullRefresh()`。

## 五、组件协议（core/widget-api）

```kotlin
interface WidgetProvider {
    val id: String
    val label: String
    val description: String
    fun create(context: Context): WidgetView
}

abstract class BaseWidgetView(context: Context) : WidgetView {
    var host: WidgetHost?
    // 实现 onDraw(canvas: Canvas)，尺寸由宿主通过 onSizeChanged 传入
}
```

注册：`WidgetRegistry.register(provider)`，宿主按 `desktop.widgetId` 创建组件。组件自行处理 `onConfigChanged`、`onRefresh`、`onClick(x, y)`、`onLongPress`。新增组件不需要修改宿主。

## 六、设备适配

- `DeviceRuntime.profile`：`widthPx/heightPx/widthDp/heightDp/density/sizeClass/heightClass/display/hasFrontLight/statusBarInsetPx/navigationInsetPx`。
- 断点：`SizeClass.COMPACT`（<600dp）、`MEDIUM`（600–840dp）、`EXPANDED`（>840dp）。
- 高度档：`HeightClass.SHORT`（<500dp）、`NORMAL`（<720dp）、`TALL`。
- 目标机型 552×768 竖屏：160dpi 时 552×768dp 属 `COMPACT` + `TALL`，4 列；213dpi 时 414×577dp 属 `COMPACT` + `NORMAL`，3 列。两种密度都要验证，见 `docs/design/11-552x768-adaptation.md`。
- 布局禁止写死像素，全部通过 `InkTokens` 与 `gridColumns(minCellDp)` 计算。

## 七、协作规则

1. 开工前在任务板领取任务并确认写作用域；只写自己模块目录。
2. 每次提交前运行单模块编译或整体构建，把编译错误清零。
3. 完成任务时在任务板写入完成说明：改动文件、对外接口、验证方式、遗留问题。
4. 遇到契约缺口（需要新增接口、修改模型字段）先给 Lead 发消息说明用途与签名，由 Lead 修改 `core/common`，避免多人同时改同一文件。
5. 不修改 `app`、`core/*`、构建脚本、清单与资源；需要时走 Lead。
6. 汇报使用中文，包含可复现命令与结果。

## 0.3.0 新增契约与约定

- `Destination.SystemSettings`：系统设置分组页，由 feature:settings 注册。桌面设置首页的「系统设置」卡片进入本页，不再启动 `Settings.ACTION_SETTINGS`，以规避定制系统设置首页的排版缺陷。
- `SpecialEntry.WEREAD`：桌面「微信读书」特殊入口。新增特殊入口需要同步四处：枚举、`DesktopEntries` 的 specials 列表、`AppCatalogImpl` 的图标与目标映射、`HomeView` 的占位图标分支。
- `FilePicker` 三个方法返回 Boolean，表示是否成功发起请求；返回 false 时必须回退到模块内扫描。`HostFilePicker` 用 `resolveActivity` 判断系统选择器是否存在。
- `VolumeKeySink`：页面实现 `onVolumeKey(up: Boolean): Boolean` 后，在 `tray.volumeKeyFlip` 开启时由宿主转发音量键；返回 true 表示已消费。
- 桌面布局自定义字段位于 `DesktopConfig`：`columns`、`rows`、`columnSpacingDp`、`rowSpacingDp`、`iconSizeDp`、`gridAlign`、`firstPageRows`、`pageJump`、`negativePage` 等，0 表示自动。设备默认值仍由 `DeviceProfile.gridColumns(minCellDp)` 与 `DesktopMetrics` 计算。
- 壁纸：`AppearanceRepository.loadWallpaper(width, height)` 供桌面背景解码；`HomeView` 使用设备像素尺寸加载，配置变化时按 `wallpaperPath` 重新加载。
- 图标：默认外框取单元格宽度 0.68 倍，圆角按 `tokens.radius`（22dp 比例），描边 3dp；新增图标入口时沿用 `InkGlyphs` 线稿风格。

## 0.4.0 新增契约与约定

- `Destination.BookshelfSettings` 与 `Destination.Beautify`：书架设置与美化模式页面，由 feature:settings 注册。
- `IconOverrideStore`（`AppServices.iconOverrides`）：单应用图标覆盖。`setOverride(packageName, uri)` 把图片复制到 `filesDir/icon-overrides/<包名>.png` 并写入 `AppearanceConfig.iconOverrides`；`AppCatalogImpl.loadIcon` 在读取图标包与系统图标之前先取覆盖图。
- `ThemeRepository`（`AppServices.themes`）：美化主题。`BeautifyTheme` 保存 `order` 与 `iconOverrides`，主题目录为 `filesDir/beautify-themes/<id>`；`exportTheme` 输出含 Base64 图片的 JSON，`importTheme` 反向解析。
- `NegativeModule`、`NegativeHost`、`NegativeModuleRegistry`：负一屏模块协议。模块实现 `createView(context, host)`、`onResume/onPause`，在模块安装时注册；`NegativePageConfig.modules` 决定显示顺序。
- `DesktopConfig.hiddenSpecials` 与 `DEFAULT_HIDDEN_PACKAGES`、`DEFAULT_HIDDEN_SPECIALS`：控制桌面隐藏的系统应用与内置入口；迁移时写入默认值，用户可在设置的隐藏应用中恢复。
- `IntegrationsConfig.bookshelfLocalEnabled` 与 `bookshelfWereadEnabled`：书架与负一屏的数据来源开关，内容层据此过滤扫描与网关请求。
- 壁纸改为宿主全屏绘制：app 的 `WallpaperView` 位于内容层下方并覆盖状态栏区域；`HomeView` 保持透明背景，不再解码或绘制壁纸。
- 图标绘制不再添加外框、白底与内描边，直接按图标矩形绘制原图；`InkGlyphs.Kind.BOLT` 供状态栏充电标记使用；`InkPage` 的标题可为空，用于无标题设置页。

## 0.6.0 新增契约与约定

- `WidgetView.transparentBackground`：组件声明是否需要透明背景。`InkWidgetView` 在绘制前按该属性决定白底与边框；时钟、紧凑时钟返回 true，并由各自 `onDraw` 直接绘制文字与日期。
- `Destination.IconPicker(packageName)`：单应用图标选择页，由 feature:settings 的 `IconPickerScreen` 注册。桌面长按应用菜单的「更换图标」进入本页；页面顶部提供当前图标与恢复默认，目录行可更换并回退手输路径，图片以两列缩略图呈现，点击即写入覆盖并返回。
- `IconOverrideStore.version: StateFlow<Int>`：图标覆盖版本号，设置、清除、清空后递增。桌面收集该流并按 generation 清空 `IconStore` 缓存后局部重绘，因此换图标不需要整屏刷新。
- `ImageDirectoryStore`（feature:settings）：解析图片目录、枚举图片、采样解码缩略图，默认目录为 `Pictures`，持久化用户选择的目录；读取依赖所有文件访问权限。
- `NegativeModule` 扩展：书架模块自行提供数量 −/+、齿轮入口与「本地 / 微信读书」来源行；模块仍通过 `NegativeHost` 打开页面，宿主只负责顺序与生命周期。
- 清空美化：`ThemeRepository.resetDesktop()` 清除图标覆盖目录、`AppearanceConfig.iconOverrides`、`desktop.order` 与全部布局覆盖，并把 `sortMode` 复位为 `INSTALL_TIME`；已保存主题不受影响。
- 状态栏背景：`MainActivity` 维护底部壁纸层与内容层，根页面为 `Home` 时状态栏容器透明，进入其他页面切换为纸白，避免设置页透出壁纸花纹。
- 耗时埋点：`MainActivity` 记录每个页面的 `createView` 毫秒数，`DesktopPrefsScreen` 记录构建耗时与行数，统一使用 `OctoberRain` 与 `DesktopPrefs` 标签，便于后续回归。

## 0.7.0 新增契约与约定

- `Destination.IconPicker(packageName)`：长按桌面应用更换图标进入的缩略图选择页，注册于 `SettingsModule`。
- `IconOverrideStore.version: StateFlow<Int>`：桌面监听该版本流清理图标缓存，换图后只做局部刷新。
- `WidgetView.transparentBackground`：组件自行声明是否透明；`ClockWidget` 与紧凑时钟返回 true，预览页与桌面按此决定底板。
- `DEFAULT_HIDDEN_SPECIALS` 与 `CURRENT_VERSION = 5`：默认隐藏 KOReader 特殊入口，旧配置由迁移补写。
- 负一屏宿主：`SwipeBackFrame` 在 `dispatchTouchEvent` 判定左滑，判定阈值复用 `desktop.swipeMinDp`；新增横向手势模块时需要在判定中避让。
- 书架缓存：`ContentSourceImpl.cachedShelfData(local, weread)` 与 `refreshShelfData(local, weread)`，TTL 30 分钟；负一屏模块以 `lastEntries` 兜底进程内再次进入。
- 状态栏：`MainActivity` 的页面栈项记录 `destination`，根页面为 `Home` 时状态栏透明，其余页面纸白，`onResume` 重新校正。

## 0.8.0 新增契约与约定

- `DesktopPreviewProvider.createPreviewView(context, pageIndex, order)`：由 feature/home 提供，返回按设备像素测量与排布的 HomeView；预览模式不写回布局快照、不接收触摸、不绘制整理提示条。
- `AppServices.previewProvider` 与 `hasPreviewProvider()`；提供方未装配时设置侧回退三列图标网格。
- `DesktopConfig.installOrderInitialized: Boolean`：首次排序只执行一次；`CURRENT_VERSION = 6`，迁移为旧配置补写 true。
- `MainActivity.goBack` 出栈后必须调用 `updateStatusBarBackground()`；新增页面栈操作时保持该约定。
- 美化排序使用完整 `workingPages` 内存结构，保存时才写 `desktop.order`；清空仅调用 `iconOverrides.clearAll()`。
