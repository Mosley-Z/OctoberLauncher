# 上游代码索引

按功能域给出类与文件位置，供移植时对照。文件位置省略公共前缀 `_analysis\jadx-out\sources\com\astraabove\tomo\`。行号为静态阅读时记录的位置，仅作定位起点。

## 一、桌面骨架与装配

| 类 | 文件 | 规模 | 要点 |
| --- | --- | --- | --- |
| `LauncherActivity` | `LauncherActivity.java` | 2688 行 | 装配九个控制器；`:1511-1515` 屏幕门禁；`:155-158` 墨水屏窗口；`:392-431` 布局键；`:1079-1194` 壁纸菜单；`:2717-2760` `onStart` 刷新 |
| 桌面形态 | `data\DesktopKind.java` | 小 | `GRID` 与 `LIST` |
| 布局存储 | `data\DesktopLayoutStore.java` | 中 | 合并 `tomo_desktop` 的六个键 |
| 分页换算 | `data\AppPaging.java` | 中 | `:15-22` 六种规格常量，`:216-222` 页数上限 15 |
| 首屏快照 | `data\HomePageSnapshot.java` | 494 行 | 快照序列化，主界面未接线 |
| 桌面默认值 | `data\DesktopDefaults.java` | 小 | `:15,17` 原桌面包名与组件 |

## 二、应用区

| 类 | 文件 | 规模 | 要点 |
| --- | --- | --- | --- |
| `AppRepository` | `data\AppRepository.java` | 中 | `:88-185` 应用枚举与 HIDE 过滤 |
| `AppInfo`、`AppIcons` | `data\AppInfo.java`、`data\AppIcons.java` | 中 | 应用元数据与图标取用 |
| 排序 | `data\AppOrderStore.java` | 中 | 换行分隔的组件串 |
| 隐藏 | `data\HiddenAppsStore.java` | 小 | 字符串集合 |
| `HomeGridController` | `ui\HomeGridController.java` | 518 行 | 宫格绑定、按压态、长按 |
| `HomeListController` | `ui\HomeListController.java` | 中 | 列表形态 |
| 图标缓存 | `data\IconCache.java` | 中 | `:34` 缓存上限 48，`:98-136` 取图，`:145-191` 裁剪 |
| 图标覆盖 | `data\IconOverrideStore.java`、`data\CustomIcon.java` | 中 | `icon_overrides/` 下的 PNG |
| 图标包 | `data\IconPack.java` | 中 | `:22-27` 圆角 22 与线宽 3，`:99-105` 资源名错拼 |
| 标签字体 | `data\LabelFontStore.java` | 小 | `tomo_label_font/path` |

## 三、组件体系

| 类 | 文件 | 规模 | 要点 |
| --- | --- | --- | --- |
| 组件枚举 | `data\DeskWidget.java` | 小 | `:29-41` 十三个 ordinal |
| 组件分类 | `data\DeskWidgetCategory.java` | 小 | `:22-26` 五类 |
| 组件宿主 | `ui\DeskWidgetsController.java` | 2971 行 | `:523` 25 参构造，`:2883-2913` 切换可见性，`:3091-3125` 九分支刷新 |
| 组件库 | `ui\WidgetLibraryController.java` | 720 行 | 分类、详情、十种预览 |
| 图片组件 | `data\WidgetImageStore.java` | 小 | `desk_widget_image.img` |
| 组件日期 | `data\DeskDate.java` | 小 | 日期格式化 |
| 月历 | `data\MonthCalendar.java`、`data\CalendarCell.java` | 中 | 6×7 共 42 格 |
| 使用统计 | `data\AppUsage.java` | 432 行 | `UsageStatsManager` 取前 3 |
| KOReader 桌面 | `ui\KoreaderDeskController.java` | 中 | 阅读进度与月热力 |

## 四、状态栏与托盘

| 类 | 文件 | 规模 | 要点 |
| --- | --- | --- | --- |
| `StatusBarController` | `ui\StatusBarController.java` | 中 | `:33` 广播集合，`:105-124` WiFi 节流，`:184-234` 变化比对，`:296-302` 反注册 |
| `QuickTrayController` | `ui\QuickTrayController.java` | 848 行 | 托盘全部控件，`:708-742` 注册与反注册 |
| 托盘外壳 | `ui\TomoChrome.java`、`ui\TrayHandleView.java` | 中 | 覆盖层与把手 |
| 电量与步骤 | `ui\PercentBar.java`、`ui\StepBar.java`、`ui\StepGlyphView.java` | 小 | 十档显示 |
| WiFi | `data\WifiController.java`、`data\WifiNetwork.java` | 中 | 扫描与连接 |
| 蓝牙 | `data\BluetoothController.java` | 中 | 开关与设备列表 |
| 通知门面 | `notify\NotificationGate.java`、`notify\TrayNotice.java` | 小 | 过滤与数据模型 |
| 托盘服务 | `notify\TomoNotificationService.java` | 中 | `:29` 常量，`:78-116` 拉取前 4 条 |

## 五、悬浮层与按键

| 类 | 文件 | 规模 | 要点 |
| --- | --- | --- | --- |
| `OverlayHost` | `overlay\OverlayHost.java` | 603 行 | `:413` 写死 800 高度，`:388-414` 三个窗口的布局参数，`:446-474` 更新 |
| 无障碍服务 | `overlay\TomoAccessibilityService.java` | 中 | `:260-281` 窗口事件与去抖 |
| 策略 | `overlay\OverlayPolicy.java`、`overlay\AccessibilityGate.java` | 中 | 显隐判定 |
| 可见性计数 | `overlay\TomoForeground.java` | 小 | 进程内 enter 与 leave 计数 |
| 按键注入 | `overlay\VolumeKeyInjector.java` | 中 | 反射 `injectInputEvent`，失败降级调音量 |

## 六、前光

| 类 | 文件 | 规模 | 要点 |
| --- | --- | --- | --- |
| `FrontLight` | `data\FrontLight.java` | 中 | `:36-49` 通道常量，`:236-345` 写入与渐变 |
| 提供者 | 无独立类，`FrontLight` 内构造 `content://com.astraabove.tomo.frontlight/` 地址 | 小 | 两个键，十档 |

## 七、微信读书

| 类 | 文件 | 规模 | 要点 |
| --- | --- | --- | --- |
| `WereadClient` | `data\WereadClient.java` | 729 行 | `:42` 网关地址，`:599-603` 鉴权头，`:706-767` 五个接口 |
| 状态 | `data\WereadStore.java` | 中 | `tomo_weread` 的十个键 |
| 封面 | `data\WereadCoverStore.java` | 小 | `weread_covers/` |
| 界面 | `WereadSkillActivity.java`、`ui\WereadHeatCell.java`、`ui\WereadHeatDither.java` | 中 | 密钥填写页与 4×4 Bayer 抖动 |

## 八、KOReader 与本地书架

| 类 | 文件 | 规模 | 要点 |
| --- | --- | --- | --- |
| 扫描 | `data\KoreaderApps.java` | 中 | `:28-31` 包名清单，`:132-396` 目录发现 |
| 读取 | `data\KoreaderReader.java` | 434 行 | 进度与书名 |
| 统计 | `data\KoreaderStats.java` | 中 | `:20-47` 复制统计库后只读查询 |
| 电子书 | `data\KoreaderBook.java`、`data\KoreaderLua.java`、`data\KoreaderSnapshot.java`、`data\KoreaderMonth.java` | 中 | Lua 元数据解析与月度汇总 |
| 书架界面 | `BookshelfActivity.java`、`data\BookshelfApp.java`、`data\BookshelfLibrary.java` | 1549 行 | 网格与列表 |
| 合集 | `data\ShelfCollectionStore.java`、`data\ShelfIndex.java`、`data\ShelfKind.java` | 中 | `shelf_collections.json` 与 `shelf_index.json` |
| 封面提取 | `data\EpubCover.java`、`data\CoverCrop.java` | 中 | EPUB 内封面提取与裁切 |

## 九、文件管理

| 类 | 文件 | 规模 | 要点 |
| --- | --- | --- | --- |
| 界面 | `FileManagerActivity.java` | 2455 行 | 浏览与全部文件操作 |
| 库 | `data\FileLibrary.java` | 831 行 | 目录扫描与索引 |
| 模型 | `data\FileEntry.java`、`data\FileKind.java`、`data\FilesApp.java`、`data\LibraryFile.java` | 中 | 类型判定与元数据 |
| 图片选择 | `ImagePickerActivity.java`、`data\ImageBrowser.java` | 939 行 | 壁纸与组件图片 |

## 十、锁屏与安全

| 类 | 文件 | 规模 | 要点 |
| --- | --- | --- | --- |
| 会话 | `data\LockSession.java`、`data\LockStore.java` | 中 | `:43-54` 四位 PIN、单轮 SHA-256 加盐 |
| 界面 | `ui\LockGate.java`、`PinPadActivity.java`、`ui\PinPadController.java`、`ui\PinDotsView.java`、`LockSettingsActivity.java` | 中 | 锁屏与设置 |
| 重置后门 | `ui\LockGate.java` | 小 | 空白键 15 连击重置 |

## 十一、设置与主题

| 类 | 文件 | 规模 | 要点 |
| --- | --- | --- | --- |
| 入口 | `SettingsActivity.java`、`data\SettingsEntry.java`、`data\SettingsPage.java` | 中 | 八个条目，七个系统跳转 |
| 系统跳转 | `data\SystemSettings.java` | 453 行 | 二十余个 `Intent` 工厂 |
| 桌面偏好 | `DesktopPrefsActivity.java` | 中 | 七行 |
| 桌面美化 | `DesktopStyleActivity.java` | 496 行 | `:73-157` 七行 |
| 权限页 | `PermissionsActivity.java`、`data\TomoPermission.java`、`data\TomoPermissions.java` | 中 | `:25-32` 八类权限，`:185-274` 授权映射 |
| 首次引导 | `OobeActivity.java` | 606 行 | `:139-250` 四步 |
| 关于与彩蛋 | `AboutPageActivity.java`、`ui\EasterEgg.java` | 中 | 版本行第 6 击 |
| 主题包 | `data\ThemePack.java` | 中 | `.ttp` 的打包与解包 |
| 壁纸 | `data\WallpaperStore.java`、`ui\WallpaperController.java`、`ui\WashRoundRectDrawable.java` | 中 | `desktop_wallpaper.img` 与色调叠加 |
| 字体 | `data\ClockFace.java`、`data\ClockFaceStore.java`、`data\ClockTime.java`、`LabelFontPickerActivity.java` | 中 | 七种字面与字体导入 |

## 十二、网络、更新与基础设施

| 类 | 文件 | 规模 | 要点 |
| --- | --- | --- | --- |
| 请求 | `data\TomoHttp.java` | 中 | 单例 GET 与 POST，UA `TomoLauncher/0.4.2` |
| 更新链 | `data\UpdateCheck.java`、`UpdateClient.java`、`UpdateFeed.java`、`UpdateFetch.java`、`UpdateManifest.java`、`UpdateRunner.java` | 中 | 清单、下载、SHA-256 校验、安装 |
| 天气 | `data\WeatherClient.java`、`data\WmoWeather.java` | 中 | `:75-103` Open-Meteo 解析 |
| 定位 | `data\DeviceLocation.java`、`data\GeoPoint.java` | 中 | IP 三级回退 |
| 网络探测 | `data\NetworkAccess.java` | 小 | 连通性 |
| 线程 | `data\TomoWorkers.java` | 小 | `:19-31` 单线程守护池 |
| 存储信息 | `data\DeviceStorage.java`、`data\StorageCapacityStore.java`、`data\StorageUsage.java` | 中 | 容量与占用 |
| 包变更 | `receiver\PackageChangeReceiver.java`、`ui\ReceiverCompat.java` | 小 | 安装与卸载后刷新 |
| 更新日志 | `data\ChangelogEntry.java`、`ChangelogFeed.java`、`ChangelogStore.java` | 小 | 远程日志 |
| 屏幕判定 | `data\SupportedScreen.java` | 小 | `:13-16` 与 `:85` 的 480×800±2 |

## 十三、渲染与通用控件

| 类 | 文件 | 要点 |
| --- | --- | --- |
| 刷新 | `ui\EinkRefresh.java` | `:19-89` 黑白各 500ms |
| 圆角与胶囊 | `ui\EinkRoundRect.java`、`ui\EinkCapsule.java`、`ui\EinkRoundRectDrawable.java`、`ui\AntiAliasRoundRectDrawable.java`、`ui\AntiAliasRoundRectLayout.java` | 逐像素生成与缓存 |
| 按压态 | `ui\EinkPressTextView.java`、`ui\EinkPressImageView.java`、`ui\EinkRoundRectLayout.java`、`ui\EinkRoundRectView.java` | 即时反色 |
| 文字描边 | `ui\OutlineTextView.java` | 白描边黑正文与基线校正 |
| 滚动 | `ui\NoFlingScrollView.java` | 禁用惯性 |
| 翻页 | `ui\PageSwipe.java`、`ui\SwipePageLayout.java`、`ui\PageDots.java`、`ui\PageSwipeDirection.java` | 方向判定与指示 |
| 长按 | `ui\TwoStageHold.java` | 两阶段阈值 |
| 列表项 | `ui\PrefPage.java`、`ui\PrefSwitchView.java` | 设置行样式 |
