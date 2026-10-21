# 功能清单

本文按当前实现记录各模块承担的功能与主要文件，路径相对仓库根。功能以 0.9.7 已合入的代码为准，未完成项在各节末尾单列并标明对应计划条目。

## 应用壳

`app` 负责进程启动、服务装配、页面栈与系统集成。

`app/src/main/java/com/shuiyu/octoberrain/OctoberRainApp.kt:44` 在 `onCreate` 中安装设备画像与设计运行时，装配 `AppServices` 的 19 个服务，反射加载 home、widgets、tray、settings、content、files 六个模块，最后挂接刷新调度。`MainActivity.kt` 维护页面栈、返回处理、音量键分派与锁屏覆盖层的挂载时机。`LauncherRouter.kt` 实现 `Router`，把 `Destination` 映射到屏幕提供者。`InkRefreshController.kt` 把局部刷新与整页闪烁的节奏统一到桌面视图。锁屏由 `LockGateImpl.kt`、`LockOverlayView.kt`、`LockWallpaperView.kt` 组成，PIN 校验委托 `core/common` 的 `PinHasher` 与 `PinLockPolicy`。`OnboardingScreen.kt` 为首次启动的三步引导，权限状态取自 `core/device` 的 `PermissionCenter`。`HostFilePicker.kt` 提供文件选择回调的宿主实现，`WallpaperView.kt` 负责壁纸绘制与量化结果呈现。

## 公共层

`core/common` 声明所有跨模块契约与序列化模型。

`Contracts.kt` 定义 `Destination`、`Screen`、`ScreenProvider`、`Router`、`AppServices`（:726）、`LockGate`、`SystemControls`、`NegativeModule`（:1089）与 `NegativeModuleRegistry`（:1119）。`Models.kt` 的 `LauncherConfig` 到 0.9.7 为版本 7（:374），涵盖桌面、外观、托盘、锁屏、通知、集成、引导、负一屏与美化主题。`Json.kt` 已更名为 `AppJson.kt`，提供忽略未知字段的宽松序列化。`PinHasher.kt` 实现 PBKDF2-HMAC-SHA256 摘要与旧格式判定，`PinLockPolicy.kt` 计算剩余次数与退避时长，`HexCodec.kt` 负责十六进制转换，`InkLog.kt` 统一日志标签，`TimeUtils.kt` 提供日期与时长格式化。

## 设计层

`core/design` 提供令牌、配色、控件与主题编解码。

`InkTokens.kt` 以 `DeviceProfile` 换算间距、字号、描边与圆角，`InkColors.kt` 声明纸面、墨色与分隔线色值，`InkWidgets.kt` 提供 `InkPage`、标题、按钮、说明文字与卡片背景，`InkDialogs.kt` 提供确认与输入对话框，`InkGlyphs.kt` 绘制矢量图标。`ThemePalette.kt` 定义调色板，`ThemeTokensCodec.kt` 负责主题令牌的序列化与校验，`DesignRuntime.kt` 保存当前令牌与配色供全应用读取。

## 设备层

`core/device` 负责设备画像、尺寸分级、主屏角色与权限。

`DeviceProfileDetector.kt:75` 探测显示类型与前光可用性，画像字段声明在 `DeviceProfile.kt`；`DisplayClass.kt` 区分墨水屏与液晶屏，`SizeClass.kt` 与 `HeightClass` 提供窗口分级，`DeviceRuntime` 保存启动时画像。`HomeRole.kt` 查询并请求默认主屏角色。`PermissionCenter.kt` 汇总七项权限并打开系统设置页，使用统计的版本分支集中在 `UsageAccessChecker.kt`。

## 配置存储

`core/data` 实现配置、应用目录、壁纸、图标、主题、通知与系统控制。

`JsonConfigStore.kt:40` 以单键 JSON 保存 `LauncherConfig`，写入成功后发布状态；`ConfigPersistence.kt:11` 的接口允许替换持久化实现，默认实现基于 `SharedPreferences`；`ConfigMigration.kt:61` 负责旧版本补齐与字段保留，迁移结果由 `JsonConfigStore` 回写。`AppCatalogImpl.kt` 枚举可启动应用并按图标缓存绘制，`IconOverrideStoreImpl.kt` 与 `IconPackResolver.kt` 管理单应用图标与图标包。`AppearanceRepositoryImpl.kt` 保存壁纸、主题与外观配置，`ThemeRepositoryImpl.kt` 解析主题文件。`WallpaperGeometry.kt`、`WallpaperQuantize.kt`、`WallpaperSwap.kt` 完成壁纸裁切、量化与替换。`FontsRepository.kt` 枚举字体，`BackupServiceImpl.kt` 负责配置整包导出与恢复，`UpdateServiceImpl.kt` 检查更新。`NotificationCenter.kt` 维护通知快照，`SystemControlsImpl.kt` 控制亮度前光与无线状态，`MediaBridge.kt` 向组件层暴露媒体会话摘要。

## 渲染层

`core/render` 统一解码、抖动、缩略图缓存与刷新。

`ImageDecoder.kt:33` 读取尺寸、按目标像素解码并归一 EXIF 方向，`ImageSizing.kt` 计算采样倍数与内存预算。`Ditherer.kt` 提供四档量化、限定档位量化、灰阶与阈值二值化，`DitherCore.kt` 保存调色板与亮度公式。`ThumbCache.kt` 以内存 8 MB、磁盘 32 MB 缓存缩略图，写入前完成抖动，`ThumbIndex.kt` 按目标长边与抖动开关分别维护索引，`ThumbKey.kt` 生成 SHA-256 键，`ThumbCacheLoader.kt` 与 `ThumbCacheRegistry.kt` 提供进程内共享入口与按需失效。`RefreshScheduler.kt` 提供局部刷新、内容变更通知与定时全刷，默认十分钟。`InkCanvas.kt` 封装矩形、发丝线与反色绘制，`BitmapPipe.kt` 保留旧调用入口。

## 组件协议

`core/widget-api` 定义组件契约。

`WidgetApi.kt:18` 的 `WidgetView` 约定尺寸变更、配置变更、刷新、绘制、点击、长按与销毁；`:61` 的 `WidgetHost` 承载红点与配置入口；`:131` 的 `WidgetCategory` 分四类，`:145` 的 `WidgetRefreshPolicy` 分分钟、事件与手动三档；`WidgetProvider` 在 `:171`，`WidgetRegistry` 在 `:184`。声明表 `feature/widgets/WidgetCatalog.kt` 共 18 条，`providers` 在 `:229`：时间类 3 条、本地类 5 条、微信读书类 8 条、其他 2 条。

## 系统服务

`service` 承载进程外的系统对接。

`NotificationRelayService.kt` 监听通知并写入 `NotificationCenter`，供托盘与桌面组件读取；`OverlayAccessibilityService.kt` 提供返回键与托盘手势的辅助功能通道，`OverlayStatusBarView.kt` 绘制悬浮状态栏。

## 桌面

`feature/home` 负责桌面视图、分页、布局度量、负一屏与模块装配。

`HomeView.kt` 绘制桌面并分发手势，`:253` 消费 `WidgetRefreshPolicy.triggersOnMinute`。`DesktopMetrics.kt:210` 的 `compute` 产出图标尺寸、组件槽与每页容量，行数换算在 `:75` 至 `:87`。`DesktopEntries.kt` 与 `IconStore.kt` 管理入口列表与图标位图。`HomeDesktopLayoutProvider.kt` 向设置页提供列数、首屏行数与页数，`HomeDesktopPreviewProvider.kt` 提供缩略预览。`NegativeScreen.kt` 按配置顺序装配负一屏模块，`AppInfoScreen.kt` 展示应用详情。`HomeModule.kt` 注册 `Destination.Home` 与 `Destination.Negative`。

## 组件

`feature/widgets` 提供 6 个组件的实现、选择器与数据逻辑：`clock`、`calendar`、`usage`、`image`、`placeholder` 与 `weread_board`。

时间类为 `ClockWidget.kt` 与 `CalendarWidget.kt`，表盘样式在 `ClockFaces.kt`，选择页为 `ClockStyleScreen.kt`；`ClockWidget` 按组件高度选择常规布局与单行紧凑布局，并保留 `constructor(context, compact)` 兼容既有调用点。`UsageWidget.kt` 与 `PlaceholderWidget.kt` 为本地与其他类。`ImageWidget.kt` 以配置路径所在目录为图片来源，按文件名升序轮播，间隔取 `DesktopConfig.effectiveImageCarouselSeconds`。`WereadBoardWidget.kt` 是微信读书唯一组件，单张卡片承载本周、本月、本书、本记与待办五种形态，形态编号存于 `negativePage.forms["weread_board"]`，绘制与命中判定在接入 `feature/widgets/src/main/java/com/shuiyu/octoberrain/feature/widgets/kernel/` 的五个适配文件后转交内核，未注册时按内置绘制。`WidgetCatalog.kt` 汇总声明，`InkWidgetView.kt` 实现基类，`WidgetPickerScreen.kt` 提供添加入口，`WidgetsModule.kt` 完成注册。

## 托盘

`feature/tray` 提供状态栏、下拉托盘与托盘偏好。

`StatusBarView.kt` 绘制时间、电量与通知计数，`TrayPanelView.kt` 绘制快捷开关与通知列表，`TrayControllerImpl.kt` 实现 `TrayController`，`TrayPrefsScreen.kt` 与 `NotificationPrefsScreen.kt` 承载托盘与通知设置，`TrayModule.kt` 完成装配。

## 设置

`feature/settings` 提供设置首页、分组页、美化页、各类选择器与声明表渲染。

`SettingsSpec.kt` 是设置项声明表：`SettingsCatalog` 在 `:13`，20 个分组常量分属外观、托盘、锁屏、系统与书架，`specs` 在 `:98` 聚合五份声明，`SettingSpec` 在 `:133`，八类控件 `SettingControl` 在 `:155`，`SettingEffect` 在 `:210`，`RefreshSchedulerBridge` 在 `:234`；实际登记 72 条。声明由 `SettingsSpecBuilders.kt` 构造，`SettingsSpecRenderer.kt` 渲染，`SettingEffects.kt` 把效果映射到刷新与重建。页面包括 `SettingsHomeScreen.kt`、`DesktopPrefsScreen.kt`、`LauncherPrefsScreen.kt`、`AppearanceScreen.kt`、`LockSettingsScreen.kt`、`PermissionsScreen.kt`、`SettingsSystemScreen.kt`、`BookshelfSettingsScreen.kt`、`AboutScreen.kt`、`UpdateScreen.kt`、`BackupScreen.kt` 与 `ThemePickerScreen`、`FontPickerScreen.kt`、`IconPackPickerScreen.kt`、`IconPickerScreen.kt` 等选择器。美化页拆为 `BeautifyScreen.kt` 与四个面板 `BeautifyThemePanel.kt`、`BeautifyImagePanel.kt`、`BeautifyIconPanel.kt`、`BeautifyPreviewPanel.kt`，分页状态在 `BeautifyPreviewState.kt`，分页换算在 `DesktopPagination.kt`。`ImageDirectoryStore.kt` 保存图片目录选择，`WereadKeyValidator.kt` 校验微信读书密钥，`ThirdPartyLicenses.kt` 汇总许可文本，`SettingsModule.kt` 注册全部页面。

## 内容与阅读

`feature/content` 提供书架、微信读书、阅读统计、最近使用与负一屏书架卡片。

`ContentModule.kt` 注册 `Destination.Content` 等页面与 `BookshelfNegativeModule`。数据层包括 `BookshelfRepository.kt`、`KoreaderRepository.kt`、`UsageRepository.kt`、`FileLibrary.kt`、`ContentPaths.kt`、`CoverResolver.kt`、`EpubCover.kt`；微信读书的请求、缓存、解析、统计与分桶分别在 `WereadTransport.kt`、`WereadClient.kt`、`WereadCache.kt`、`WereadParsing.kt`、`WereadBucketParsing.kt`、`WereadStats.kt`。页面层包括 `BookshelfScreen.kt`、`WereadScreen.kt`、`WereadCalendarScreen.kt`、`WereadSections.kt`、`WereadSectionViews.kt`、`KoreaderScreen.kt`、`SearchScreen.kt`、`RecentsScreen.kt`、`ContentScreen.kt` 与通用绘制 `ContentUi.kt`。

## 文件与图片

`feature/files` 提供文件浏览、相册、图片查看、图片编辑、外部查看与选图入口，以及负一屏最近图片卡片。

数据层在 `feature/files/.../data/`：`FileListing.kt` 枚举目录与排序，`FileItem.kt` 描述条目，`FileOps.kt` 与 `FileStreams.kt` 完成复制、移动与比较，`FilePrefs.kt` 保存视图与排序偏好，`StorageAccess.kt` 解析存储卷与输出目录，`MediaIndex.kt` 维护媒体索引并提供 `recent(limit)`，`CropMath.kt`、`EditSession.kt`、`EditExport.kt`、`Edits.kt` 负责编辑状态与导出。

界面层在 `feature/files/.../ui/`：`BrowseScreen.kt` 为浏览页，`GridPane.kt`、`ListPane.kt`、`BrowsePane.kt`、`PaneDraw.kt` 实现两种视图，`FilePager.kt`、`FilePagerChrome.kt`、`FilePagerTouch.kt`、`FilePaging.kt` 提供翻页容器与手势，`FileMetrics.kt`、`FileTokens.kt`、`FileText.kt` 负责几何与文案，`ThumbPrefetcher.kt` 预取缩略图。`PickPane.kt` 与 `PickEntryActivity.kt` 为选图入口，`PhotoEntryActivity.kt` 为外部查看入口。`ui/album/` 下 `AlbumScreen.kt`、`AlbumPane.kt`、`AlbumPaging.kt` 实现相册。`ui/viewer/` 下 `PhotoScreen.kt` 组合查看器，`ViewerChrome.kt` 绘制顶栏底栏与命中区，`ViewerDecoder.kt` 负责解码与量化，`ViewerState.kt`、`ViewerMetrics.kt`、`ViewerText.kt` 提供状态、几何与格式化。`ui/edit/` 下 `EditScreen.kt` 组合编辑页，`EditSurface.kt`、`EditState.kt`、`CropView.kt`、`CropFrame.kt`、`CropPanel.kt`、`RotatePanel.kt`、`FilterPanel.kt`、`SavePanel.kt`、`EditChip.kt`、`PreviewSizing.kt`、`EditExportPlan.kt` 分工渲染与导出，灰阶滤镜在 `Gray.kt` 与 `GrayKernels.kt`。`ui/negative/FilesNegativeModule.kt` 实现负一屏卡片，`FilesModule.kt` 注册全部页面与卡片。

## 未完成项

壁纸设置的计划条目 8.2 只完成裁切、量化与替换的代码路径，桌面壁纸替换后的复核与旧文件删除未完成。

组件 `refreshPolicy` 的 EVENT 一档依赖宿主按事件触发，当前仅分钟档由 `HomeView` 消费；`requiredCapabilities` 已有字段与声明，宿主未做能力过滤。

窗口适配只验证过 480×800 与 600×800，计划中的 552×768 与 824×1648 未在真机复核。

权限与系统分支只在 API 30 与 API 35 设备上验证，`UsageAccessChecker` 的 API 26 至 28 旧接口分支未实测。

调度入口未抽 `IoDispatcher`，全仓 56 处 `Dispatchers.IO` 各自使用；覆盖率门槛、截图测试与 UIAutomator 冒烟未接入；模块级 `README.md` 未创建。`app/proguard-rules.pro` 只保留五个模块类，`FilesModule` 未列入，反射加载在 release 包中依赖其未被裁剪，需补规则。
