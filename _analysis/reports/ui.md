# tom·launcher UI 子系统逆向分析报告

## 0. 分析对象与引用口径

| 项目 | 取值 |
| --- | --- |
| APK | `tomo-launcher-0.5.0.apk` |
| 包名 | `com.astraabove.tomo` |
| Java 反编译根 | `D:\DSHStation\OctoberLauncher\_analysis\jadx-out\sources\com\astraabove\tomo\` |
| 资源根 | `D:\DSHStation\OctoberLauncher\_analysis\jadx-out\resources\res\` |
| 清单 | `D:\DSHStation\OctoberLauncher\_analysis\jadx-out\resources\AndroidManifest.xml` |

引用格式为 `文件路径:行号`。Java 文件省略 `sources\com\astraabove\tomo\` 前缀，资源文件省略 `resources\res\` 前缀。原程序以 Kotlin 编写，JADX 把 lambda 还原成 `LauncherActivity$onCreate$N`、`$$ExternalSyntheticLambdaN` 等独立类，同时保留 `onCreate$lambda$N` 静态桥接方法；报告中按 JADX 还原后的形态描述。

全部判断来自静态阅读，未运行 APK。文本中的“未见”“未找到”只表示在这份反编译产物里没有对应代码，与运行时是否存在无关。

---

## 1. 整体装配

### 1.1 视图层级

```
LauncherActivity（@layout/activity_launcher）
└─ FrameLayout @id/root                                    activity_launcher.xml:2-6
   ├─ ImageView @id/wallpaper（gone，centerCrop）           :7-13
   ├─ View @id/wallpaper_wash（@color/wallpaper_wash，gone）:14-19
   ├─ LinearLayout @id/desktop（vertical）                  :20-25
   │  ├─ include @id/status_bar → @layout/include_status_bar :26-28
   │  ├─ SwipePageLayout @id/swipe_page（clipChildren=false，weight=1）:29-34
   │  │  └─ LinearLayout @id/home_stack（vertical）        :35-40
   │  │     ├─ FrameLayout @id/desk_host（focusable+clickable，weight=1）:41-49
   │  │     │  ├─ include @id/koreader_desk → widget_koreader（默认可见）:50-54
   │  │     │  ├─ include @id/desk_weather → widget_weather（gone）:55-60
   │  │     │  ├─ include @id/desk_clock → widget_clock（gone）:61-66
   │  │     │  ├─ include @id/desk_calendar → widget_calendar（gone）:67-72
   │  │     │  ├─ include @id/desk_covers → widget_covers（gone）:73-78
   │  │     │  ├─ include @id/desk_weread_covers → widget_weread_shelf（gone）:79-84
   │  │     │  ├─ include @id/desk_weread_month → widget_weread_month（gone）:85-90
   │  │     │  ├─ include @id/desk_weread_mark → widget_weread_mark（gone）:91-96
   │  │     │  ├─ include @id/desk_koreader_month → widget_weread_month（gone）:97-102
   │  │     │  ├─ include @id/desk_usage → widget_usage（gone）:103-108
   │  │     │  ├─ include @id/desk_image → widget_image（gone）:109-114
   │  │     │  └─ include @id/desk_tip → widget_tip（gone）:115-122
   │  │     ├─ GridLayout @id/app_grid（2 行 × 3 列，高 @dimen/desktop_grid_height）:124-134
   │  │     ├─ GridLayout @id/app_grid_more（4 行 × 3 列，gone，weight=1）:135-147
   │  │     └─ LinearLayout @id/home_list（vertical，gone，weight=1）:148-159
   │  └─ LinearLayout @id/page_bar（gravity=center，longClickable）:162-169
   │     └─ PageDots @id/page_dots                         :170-173
   ├─ include @id/tray_panel → @layout/panel_quick_tray     :176-178
   └─ include @id/lock_pad → @layout/include_pin_pad（gone）:179-182
```

`desk_host` 的十二个组件容器中只有 `koreader_desk` 在 XML 里可见，其余十一个都是 `android:visibility="gone"`，最终由 `DeskWidgetsController.show` 按当前选中组件切换（`ui\DeskWidgetsController.java:2883-2913`）。

状态栏被 include 进来后成为 `statusBar` 命名空间下的子视图，其内部 id 为 `status_bar`、`status_chrome`、`refresh_view`、`time_view`、`status_tray_hit`、`wifi_view`、`battery_view`、`charge_view`、`status_arrange`（`layout\include_status_bar.xml:2-89`）。

### 1.2 控制器装配表

`LauncherActivity.onCreate` 以命令式方式逐个创建控制器并立刻接线，没有依赖注入框架。

| 控制器 | 作用对象 | 关键构造参数 | 行号 |
| --- | --- | --- | --- |
| `WallpaperController` | `@id/wallpaper`、`@id/wallpaper_wash` | `WallpaperStore` | `LauncherActivity.java:1545-1554` |
| `HomeGridController` | `@id/app_grid`、`@id/app_grid_more` | `IconCache`、`onAppClick`、`onAppMenu`、`onAppArrange`、`onSlotClick`、`onEmptyLongPress`、`LabelFontStore` | :1561-1589 |
| `HomeListController` | `@id/home_list` | 点击、菜单、整理、落位、长按空白、`LabelFontStore` | :1595-1609 |
| `KoreaderDeskController` | `@id/koreader_desk` 全部子 view | 钟面、日期、书名、百分比、进度条、阅读卡片、`onOpenBook` | :1615-1691 |
| `DeskWidgetsController` | `@id/desk_host` 下十二个容器 | `DesktopLayoutStore`、`DeskWidgetStore`、`WeatherStore`、`WidgetImageStore` 与八个回调 | :1697-1982 |
| `StatusBarController` | 状态栏时间、电量、WiFi、充电，以及组件区钟面 | 主钟面、组件区钟面、组件区副钟面列表、日期列表、两张图标 | :2014-2072 |
| `QuickTrayController` | `@id/tray_panel` | 关闭回调 | :2078-2091 |
| `PinPadController` | `@id/lock_pad` | 校验回调、忘记回调 | :2429-2482 |
| `TomoChrome` | 十二个二级页面各自装配 | 与主界面同名的状态栏 id | 见 §9.2 |

`DeskWidgetsController` 的构造签名完整列出为 `DeskWidgetsController(View host, View reading, View weather, View clock, View calendar, View covers, View wereadCovers, View wereadMonth, View wereadMark, View koreaderMonth, View usage, View image, View tip, DesktopLayoutStore layoutStore, DeskWidgetStore store, WeatherStore weatherStore, WidgetImageStore imageStore, Function1<KoreaderBook,Unit> onOpenBook, Function1<WereadBook,Unit> onOpenWeread, Function0<Unit> onNeedWereadKey, Function0<Unit> onPickImage, Function0<Unit> onNeedLocation, Function1<AppInfo,Unit> onOpenUsageApp, Function0<Unit> onNeedUsageAccess, Function0<Unit> onCloseWidget, Function0<Unit> onWidgetChanged)`（`ui\DeskWidgetsController.java:523`）。

### 1.3 onCreate 装配时序

1. `applyEinkWindow()`：`getWindow().setWindowAnimations(0)` 并 `clearFlags(2)`，从窗口层面关闭动画（`LauncherActivity.java:155-158`、调用点 :1509）。
2. 屏幕匹配检查：`SupportedScreen.matches` 为假则拉起原桌面并 `finish()`（:1511-1516）。
3. `ActivityLauncherBinding.inflate` 并 `setContentView`（:1517-1525）。
4. 建立各偏好存储：`HiddenAppsStore`、`AppOrderStore`、`DesktopLayoutStore`、`VolumeKeyStore`（:1526-1529）。
5. `AppRepository`、`IconCache`、`LabelFontStore`（:1537-1539）。
6. `WallpaperController`，随后记录 `themeStamp = ThemePack.appliedAt`（:1545-1555）。
7. `HomeGridController`（:1561-1589），再 `HomeListController`（:1595-1609）。
8. `KoreaderDeskController`（:1615-1691）。
9. `DeskWidgetsController`（:1697-1982），随后 `deskWidgets.attachCycle(View[]{koreaderClock, koreaderClockPeriod, koreaderCard})`，给组件区钟面与阅读卡片挂长按，长按打开组件库（:1983-2008；`bindCycle` 实现见 `ui\DeskWidgetsController.java:1330-1348`，长按回调即 `showPicker()`）。
10. `StatusBarController`（:2014-2072）。
11. `QuickTrayController`（:2078-2091），再注入三个授权请求回调 `setRequestBluetooth`、`setRequestLocation`、`setRequestWriteSettings`（:2092-2149），然后 `trayController.start()`（:2155）。
12. `PackageChangeReceiver(onCreate$25)`（:2156）。
13. `pageBar` 长按（:2162-2169），`time_view` 与 `refresh_view` 点击（:2170-2199）。
14. `swipe_page` 四个方向回调（:2205-2301），`tray_panel` 的关闭回调（:2307-2328）。
15. `status_tray_hit` 与 `status_arrange` 点击（:2334-2350）。
16. `disableClickFeedback` 对八个 view 关闭触感与音效：`pageBar`、`pageDots`、`statusBar.root`、`statusBar.statusTrayHit`、`statusBar.timeView`、`statusBar.refreshView`、`statusBar.statusArrange`、`root`（:2351-2416，实现 :310-315）。
17. `wallpaperController.refresh()`（:2417-2422）。
18. `LockStore`、`PinPadController`（:2423-2482）、`LockSession.watch(lockWatch)`（:2483）。
19. `LockGate.screenOffReceiver` 挂到 `screenReceiver` 并注册（:2484-2503），注册 `packageReceiver`（:2504-2512）。
20. `applyLockPad()`（:2513），最后 `reloadApps(true)`（:2514）。

### 1.4 生命周期与刷新触发

| 回调 | 行为 | 行号 |
| --- | --- | --- |
| `onResume` | 隐藏系统栏、可能启动 OOBE、布局键变化或应用列表脏则 `reloadApps(true)`、`statusController.start`、托盘关闭判定、按下态复原、`applyLockPad`、`applyThemeIfChanged`、壁纸刷新、锁屏壁纸刷新 | `LauncherActivity.java:2631-2714` |
| `onStart` | `TomoForeground.enter()`；阅读组件脏则 `koreaderDesk.refresh()`；`deskWidgets.refresh()`；托盘开着则 `trayController.refresh()` | :2717-2760 |
| `onPause` | 仅 `statusController.stop(this)` | :2592-2605 |
| `onStop` | 阅读组件标记脏、`TomoForeground.leave()` | :2763-2776 |
| `onDestroy` | 解绑对话框、撤回锁监听、注销两个 receiver、`koreaderDesk.destroy()`、`deskWidgets.destroy()`、`iconCache.clear()` | :2518-2580 |
| `onTrimMemory` | 按当前页组件名裁剪 `IconCache` 并清宫格 key | :2779-2811 |
| `onConfigurationChanged` | 锁屏重新排布、壁纸重新解码 | :1472-1499 |

布局键 `layoutKey()` 由 `kind|widgetEnabled|当前组件 key|iconOnly|tomoFilesEnabled|labelFonts.token()` 拼接（:392-431），任一项变化都会触发整页重建。

---

## 2. 桌面形态

### 2.1 枚举与默认值

`DesktopKind` 只有两个值：

```java
// data\DesktopKind.java:22-23
GRID,
LIST;
```

`getKey()` 返回 `name().toLowerCase(Locale.ROOT)`（`data\DesktopKind.java:79-83`）；`Companion.fromKey` 在键名无法识别时回落到 `DesktopKind.GRID`（:35-50），因此偏好文件损坏不会导致桌面空白。

默认形态由 `DesktopLayoutStore.kind()` 决定：`DesktopKind.fromKey(prefs.getString(KEY_KIND, DesktopKind.GRID.getKey()))`（`data\DesktopLayoutStore.java:50`），即首次安装为宫格。同类默认值还有 `iconOnly()` 默认 `false`（:45-46）、`widgetEnabled()` 默认 `true`（:98-99）、`trayEnabled()` 默认 `true`（:94-95）、`tomoFilesEnabled()` 默认 `true`（:90-91）、`overlayEnabled()` 默认 `false`（:57-58）、`oobeDone()` 默认 `false`（:53-54）。

`data\DesktopDefaults` 与形态选择无关，它保存的是“原桌面”的回落信息：`ACTIVITY = "com.wetao.elauncher.ui.main.MainActivity"`（`data\DesktopDefaults.java:15`）、`PACKAGE = "com.wetao.elauncher"`（:17）；`openOriginalDesktop` 依次尝试显式组件、`getLaunchIntentForPackage`、`HOME + setPackage`（:52-64）。

### 2.2 分页规格

`AppPaging` 用两张表描述“首屏/后续页”的容量差：

```java
// data\AppPaging.java:15-22
FIRST_PAGE_COMPACT = 9;
FIRST_PAGE_SIZE = 6;
LIST_FIRST_COMPACT = 8;
LIST_FIRST_WITH_WIDGET = 5;
LIST_PAGE_SIZE = 10;
MAX_PAGES = 15;
PAGE_SIZE = 12;
```

对应规格对象为 `GRID_WIDGET(6,12)`、`GRID_WIDGET_COMPACT(9,12)`、`GRID(12,12)`、`LIST_WIDGET(5,10)`、`LIST_WIDGET_COMPACT(8,10)`、`LIST(10,10)`、`DEFAULT(6,12)`（`data\AppPaging.java:79-88`），由 `spec(kind, widget, compact)` 按“有无组件 / 是否紧凑钟面”挑选（:240-250）。页数上限 15 页（:216-222）。

宫格的列数固定在控制器里：`private static final int COLUMNS = 3`（`ui\HomeGridController.java:32`）。

### 2.3 两种形态的布局与翻页语义

形态切换发生在 `showPage(page, arrange)` 里（`LauncherActivity.java:903-1070`），两种形态共用同一套 `swipe_page`、`page_bar`、组件区，只替换应用区。

**宫格形态**（`DesktopKind.GRID`）使用 `app_grid`（2 行 × 3 列）与 `app_grid_more`（4 行 × 3 列）两块 GridLayout。首屏且有组件时首屏只有 6 格，其余页 12 格：`bindSlot` 对 `firstGrid` 传入可见数 6、对 `moreGrid` 传入 `midFirst ? 9 : 12`（`ui\HomeGridController.java:392-402`），`bind()` 用 `z = firstPage && compactFirst && !midFirst` 选择显示哪块网格（:495-514）。`showPage` 的宫格分支给 `desk_host` 设 `weight=1.0f`（非紧凑）或固定高 `@dimen/desktop_desk_compact`（178px，`values\dimens.xml:55`，紧凑），随后 `gridController.bind(...)` 并 `listController.hide()`（`LauncherActivity.java:972-1057`）。

**列表形态**（`DesktopKind.LIST`）只使用 `home_list`，固定 10 行控件（`ui\HomeListController.java:64-96`），由 `rowCount` 决定显示几行，首屏有组件时 5 行、无组件时 8 行、其余页 10 行。`showPage` 的列表分支把 `desk_host` 高度改为 `@dimen/list_desk_height`（356px，`values\dimens.xml:138`；紧凑用 `@dimen/list_desk_height_compact`，178px，:139）并加 `bottomMargin = @dimen/list_desk_gap`（6px，:137），非首屏时高度置 0；`rowCount` 在“无组件且只有一页且非整理模式”时取 `coerceIn(应用数,0,10)`，否则取分页规格（`LauncherActivity.java:945-1024`）。

翻页语义的差别体现在 `SwipePageLayout` 的一个标志位：`swipePage.setVerticalPages(kind == DesktopKind.LIST)`（:936）。置真之后，纵向滑动也承担翻页，只有首页向下滑（`dy > 0`）才打开快捷托盘；置假时纵向滑动专门用于开关托盘，向下滑打开、向上滑收起（`ui\PageSwipe.java:15-26`）。水平方向在两形态下一致：左滑为下一页、右滑为上一页。

`PageSwipeDirection` 四个取值：

```java
// ui\PageSwipeDirection.java:16-19
PREV,
NEXT,
OPEN_TRAY,
CLOSE_TRAY
```

### 2.4 形态切换入口

桌面长按空白处或长按页指示条弹出壁纸菜单（`LauncherActivity.java:2162-2169` → `onCreate$lambda$1`，:450-457），菜单中 `wallpaper_desktop_prefs` 进入 `DesktopPrefsActivity`（`LauncherActivity.java:1296`）。`DesktopPrefsActivity` 再进入 `DesktopStyleActivity`（`DesktopPrefsActivity.java:99`），形态开关正是它的第一行：`setKind(kind() == DesktopKind.GRID ? DesktopKind.LIST : DesktopKind.GRID)`（`DesktopStyleActivity.java:195`），行标题取 `oobe_layout_list` / `oobe_layout_grid`（:73）。同一页面还提供组件开关（:215、:243）、纯图标开关（:266、:294）、钟面选择（:89 `clock_face_list`）与标签字体选择（:157 `LabelFontStore`）。

钟面候选取自 `ClockFace` 的七个值：`SYSTEM`、`LEXEND`、`BITTER`、`LITERATA`、`SPACE_GROTESK`、`FREDOKA_ONE`、`OXANIUM`（`data\ClockFace.java:25-31`），其中 `SYSTEM` 走粗体系统字体（`ui\KoreaderDeskController.java:124-128`）。

---

## 3. 首页层级逐项

| 层级 | 关键 view id | 驱动类 | 布局引用 |
| --- | --- | --- | --- |
| 状态栏 | `status_bar`、`status_chrome`、`refresh_view`、`time_view`、`status_tray_hit`、`wifi_view`、`battery_view`、`charge_view`、`status_arrange` | `StatusBarController`；`TomoChrome` 在二级页面复用同一套 id | `layout\include_status_bar.xml:2-89` |
| 日期与时钟 | `koreader_clock`、`koreader_clock_period`、`koreader_date`、`koreader_title`、`koreader_percent`、`koreader_bar` | `KoreaderDeskController`；时间文本由 `StatusBarController` 同时投递到组件区钟面 | `layout\widget_koreader.xml:10-115` |
| 组件区 | `desk_host` 与十二个 `desk_*` 容器 | `DeskWidgetsController` | `layout\activity_launcher.xml:41-123` |
| 应用区（宫格） | `app_grid`、`app_grid_more` | `HomeGridController` | :124-147 |
| 应用区（列表） | `home_list` | `HomeListController` | :148-159 |
| 翻页指示 | `page_bar`、`page_dots` | `PageDots` | :162-173 |
| 快捷托盘 | `quick_tray` 及 `tray_*` 系列 | `QuickTrayController` | `layout\panel_quick_tray.xml:3-345` |
| 托盘把手 | `tray_close` | `TrayHandleView` | `layout\panel_quick_tray.xml:340-344` |
| 锁屏 | `lock_pad`、`pin_*` 系列 | `PinPadController` + `LockGate` | `layout\include_pin_pad.xml:2-127` |

### 3.1 状态栏

状态栏由 `include_status_bar.xml` 构成，高度 `@dimen/status_bar_height`（54px，`values\dimens.xml:238`）。左端是刷新图标 `refresh_view`，紧随时间文本 `time_view`，中间是可点击的空白区 `status_tray_hit`（weight 1），右端依次是 `wifi_view`、`battery_view`、`charge_view`。时间与电量都用 `OutlineTextView`，字号 `@dimen/desktop_chrome_text`（22px）。

点击 `time_view` 或 `refresh_view` 触发整屏闪烁刷新 `EinkRefresh.flash(binding.root)`（`LauncherActivity.java:460-471`，接线 :2170-2199）；点击 `status_tray_hit` 打开快捷托盘（:474-482）。`status_arrange` 是盖住整条状态栏的黑底白字层，显示文案 `@string/arrange_mode`，仅在整理模式可见，点击退出整理（:485-488，可见性由 `bindArrangeBar()` 控制，:258-268）。

`StatusBarController` 用三个 `BroadcastReceiver` 维持刷新：时间（`ACTION_TIME_TICK`、`TIME_SET`、`TIMEZONE_CHANGED`、`DATE_CHANGED` 与自定义 `ClockTime.ACTION_CHANGED`）、电量（`ACTION_BATTERY_CHANGED`）、WiFi（`WIFI_STATE_CHANGED`、`STATE_CHANGE`、`RSSI_CHANGED`，其中 RSSI 带 `RSSI_DEBOUNCE_MS = 1500` 的节流，`ui\StatusBarController.java:33`、:105-124）。每次刷新都先与上一轮字符串比较，相同则跳过重绘（:184-234）。WiFi 图标按信号强度取 `status_wifi_1/2/3`，其余取 `status_wifi_0`（:236-260）；充电时才显示 `charge_view`（:151-182）。

### 3.2 日期与时钟

组件区的 koreader 面板承担“桌面主钟”。时钟行 `koreader_clock_row` 内是 `koreader_clock`（96px，`values\dimens.xml:129`）与 `koreader_clock_period`，下方 `koreader_date`（22px），再下方是阅读卡片 `koreader_card`（`AntiAliasRoundRectLayout`，圆角 `@dimen/koreader_card_radius`、描边 `@dimen/widget_tip_stroke`），卡片里是书名 `koreader_title`、百分比 `koreader_percent` 与细进度条 `koreader_bar`（`layout\widget_koreader.xml:64-115`）。

`KoreaderDeskController` 负责字体与对齐：`applyClockFace` 设置 typeface 后调用 `lockClockHeight()` 把所有钟面的最大行高锁为最小高，避免切换字体时布局跳动（`ui\KoreaderDeskController.java:167-196`）；`lockPeriod` 用 `@dimen/koreader_period_offset_y` 平移副钟面并单独锁定行高（:198-215）；对 `OutlineTextView` 还设置 `setInkOffsetY(ClockAlign.translationY(paint))`（:134-139）。数据来自 `KoreaderReader.loadSnapshot`，指纹相同则跳过（:229-231），主线程校验 generation 后再绑定（:241-249）。

点击阅读卡片打开当前书籍（:114-118）；长按钟面、副钟面或阅读卡片会打开组件库（`attachCycle` → `bindCycle` → `showPicker()`，`ui\DeskWidgetsController.java:3037-3042`、:1330-1348）。

### 3.3 组件区

`desk_host` 是一个 `FrameLayout`，十二个组件容器全部叠放其中，由 `DeskWidgetsController.show(widget)` 逐个设置可见性（`ui\DeskWidgetsController.java:2883-2913`）。细节见 §6。

### 3.4 应用区

宫格形态下 `app_grid` 固定高 `@dimen/desktop_grid_height`（356px，`values\dimens.xml:56`），`app_grid_more` 以 `weight=1` 吃掉剩余高度；两者左右各留 `@dimen/desktop_grid_side`（30px）。列表形态下 `home_list` 以 `weight=1` 占满，首行距顶额外留 `@dimen/list_page_padding_top`（42px，`values\dimens.xml:140`）。

单个图标由 `item_app_icon.xml` 描述：`FrameLayout` 内叠放 `select_frame`（整理模式选中框，`@dimen/arrange_select_box` = 100px）、`icon`（`@dimen/icon_frame_size` = 94px，`scaleType=center`）、`move_here`（整理模式落位徽标），下方是标签 `label`（`@dimen/desktop_label_size` = 22px，单行、`includeFontPadding=false`、四边 padding 为 0px，`layout\item_app_icon.xml:10-61`）。列表行由 `item_list_row.xml` 描述：行高 `@dimen/home_list_row_height`（64px），`list_name_box` 内是 `list_label`（`@dimen/home_list_text` = 28px，上下 padding `@dimen/home_list_underline_pad` = 10px，左右 8px，`layout\item_list_row.xml:2-41`）。

### 3.5 翻页指示

`page_bar` 是一条 34px 高的水平条（`@dimen/desktop_page_bar_height`），仅承载 `PageDots`。`PageDots` 的半径、间距、光晕、描边取自 `page_dot_radius`(4px)、`page_dot_gap`(9px)、`page_dot_halo`(2px)、`page_dot_stroke`(2px)（`values\dimens.xml:188-191`）；页数不大于 1 时整块隐藏（`setVisibility(8)`，`ui\PageDots.java:65-67`）；当前页画实心黑圆，其余为空心黑圆并带白色底圈（:101-113）。`page_bar` 本身可长按，长按等于长按桌面空白（`layout\activity_launcher.xml:167`）。

### 3.6 托盘把手与快捷托盘

快捷托盘是一个全屏 `SwipePageLayout`，根 id `quick_tray`，白底、初始 `gone`（`layout\panel_quick_tray.xml:3-8`）。内部自上而下为：日期与电量行（`tray_date`、`tray_battery`）、WiFi 与蓝牙两张卡片（`tray_wifi`、`tray_bluetooth`，各含标题与明细两行文本）、亮度行（`tray_brightness_value`、`tray_brightness_minus`、`tray_brightness_bar`、`tray_brightness_plus`）、色温行（`tray_color_value`、`tray_color_minus`、`tray_color_bar`、`tray_color_plus`）、通知区（`tray_notice_header`、`tray_notice_clear`、`tray_notice_list`、`tray_notice_empty`、`tray_notice_hint`）、无障碍浮层提示（`tray_overlay_hint`、`tray_overlay_hint_line`），底部是关闭把手 `tray_close`（`TrayHandleView`，高 32px）。

`TrayHandleView` 关闭抗锯齿、黑填充白底、按下时额外画外环（`ui\TrayHandleView.java:45-57`、:92-99）。`QuickTrayController` 在 `start()` 中把所有点击与长按接线，包括 WiFi/蓝牙切换与长按进系统设置、步进按钮与 `StepBar` 的档位选择（`ui\QuickTrayController.java:756-900`）。

---

## 4. 手势与输入语义

### 4.1 点击与长按：TwoStageHold 的两阶段

图标与列表行的长按由自定义触摸监听器承担，`View.OnLongClickListener` 只是备用通道。

常量与两个阈值：

```java
// ui\TwoStageHold.java:21-22
ARRANGE_MS = 2000;
HOLD_SLOP = 6.0f;
```

`firstMs = ViewConfiguration.getLongPressTimeout()`（:52），`secondMs = 2000L`（:53）。第一阶段到点只置 `reachedFirst` 并给一次按下态反馈（:85-88）；第二阶段（累计 2 秒）到点置 `reachedSecond` 并回调 `onArrange`（:91-95）。抬手时的判定为：已发生位移或已到第一阶段但未到第二阶段 → `onMenu`（弹出应用菜单）；两个阶段都未到达 → 视为点击 `onTap`（:119-135）。位移一超过 `HOLD_SLOP`（6px）立即中止长按（:149-153），超过阈值时还会请求父级放开拦截并置 `shifted`（:154-163）。

两阶段的实际语义是：短按打开应用；长按越过第一阶段后抬手弹菜单；不松手持续按满 2 秒进入整理模式。第一阶段的到点时刻取系统长按时长（`ViewConfiguration.getLongPressTimeout()`），菜单在抬手时才出现，因此不存在“按住即弹菜单、松手又触发点击”的歧义。宫格与列表都把该监听器挂在可点区域上（`ui\HomeGridController.java:288-358`、`ui\HomeListController.java:261-361`）。

### 4.2 滑动翻页与托盘手势

`SwipePageLayout` 是唯一的手势分发层，它把位移与速度交给 `PageSwipe.resolve` 判定方向，然后回调四个函数。

```java
// ui\PageSwipe.java:15-26（判定规则）
// 水平占优：dx < 0 → NEXT，否则 PREV
// 垂直占优且 verticalPages 为真：(dy<=0 || !firstPage)
//     ? (dy>0 ? PREV : NEXT)
//     : OPEN_TRAY
// 垂直占优且 verticalPages 为假：dy > 0 ? OPEN_TRAY : CLOSE_TRAY
```

阈值来自 `ViewConfiguration`：`touchSlop = getScaledTouchSlop()`、`minFlingVelocity = getScaledMinimumFlingVelocity()`、`minSwipeDistance = coerceAtLeast(touchSlop * 2, 32.0f)`（`ui\SwipePageLayout.java:78-81`）。`onInterceptTouchEvent` 在任一方向位移超过 `touchSlop` 时接管事件并尝试判定，`onTouchEvent` 在抬手前若尚未判定成功再补判一次（:196-260，其中拦截方法体未被 JADX 还原为 Java，见 §10）。

`showPage` 每次切页都写入 `setVerticalPages(kind == DesktopKind.LIST)` 与 `setFirstPage(page == 0)`（`LauncherActivity.java:936-942`），因此列表形态下上下滑也翻页，而宫格形态下上下滑专门开关托盘。

四个回调的落点：

| 回调 | 行为 | 行号 |
| --- | --- | --- |
| `setOnSwipeToPrev` | `showPage(currentPage - 1, false)` | `LauncherActivity.java:2205-2223` |
| `setOnSwipeToNext` | `showPage(currentPage + 1, false)` | :2229-2247 |
| `setOnSwipeOpenTray` | `trayController.open()` | :2253-2274 |
| `setOnSwipeCloseTray` | `trayController.close()` | :2280-2301 |

托盘自身也是一个 `SwipePageLayout`，额外挂了 `trayPanel.getRoot().setOnSwipeCloseTray(...)`，所以在托盘内继续滑动即可收起（:2307-2328）。

### 4.3 状态栏与托盘的点击

| 触发位置 | 行为 | 行号 |
| --- | --- | --- |
| 状态栏时间 / 刷新图标 | `EinkRefresh.flash(binding.root)` 整屏闪刷 | `LauncherActivity.java:460-471` |
| 状态栏空白区 `status_tray_hit` | 打开快捷托盘 | :474-482 |
| 整理模式遮罩 `status_arrange` | 退出整理模式 | :485-488 |
| 页指示条长按 | 非整理模式弹出壁纸菜单 | :450-457 |

托盘内部：WiFi/蓝牙卡片点击切换开关、长按进入系统设置面板，亮度与色温的加减键做步进，`StepBar` 点击按比例取档（`ui\QuickTrayController.java:756-900`）。步进通道的命名与字面相反，见 §10 第 6 项。

### 4.4 音量键

桌面自身不消费按键事件，`LauncherActivity` 里只有一处引用 `VolumeKeyStore.isInverted(set)` 用于切换菜单文案（`LauncherActivity.java:836`）。真正的按键处理在无障碍服务：

`TomoAccessibilityService.onKeyEvent` 先判断锁屏浮层是否附着，附着时只放行返回键；否则用 `VolumeKeys.opposite(keyCode)` 判定是否为音量键，再用 `VolumeKeys.shouldInvert(前台包名, 本包名, store.inverted())` 决定是否反转，反转通过 `VolumeKeyInjector.invert(this, event)` 注入（`overlay\TomoAccessibilityService.java:260-281`）。

### 4.5 返回键

`onBackPressed` 依次判断：锁屏浮层可见则直接忽略（锁屏吞掉返回键）；快捷托盘展开则收起托盘；否则退出整理模式（`LauncherActivity.java:1438-1469`）。桌面本身不会被返回键退出，这一点与普通 Activity 不同。

在无障碍服务侧，锁屏附着时仅放行 `KEYCODE_BACK`（`overlay\TomoAccessibilityService.java:263-266`），与上面一致。

### 4.6 图标点击与打开应用

点击应用由 `onDesktopAppClick` 转给 `launchApp`：设置类走 `SettingsActivity`、书架类走 `BookshelfActivity`、文件类走 `FileManagerActivity`，其余走 `app.getLaunchIntent()`；启动失败时把宫格与列表的按下态复原（`LauncherActivity.java:354-390`）。整理模式下点击被忽略（:491-496）。

### 4.7 整理模式

进入整理模式由图标长按满 2 秒触发（`onAppArrange`），`enterArrange(AppInfo)` 记录选中项并 `showPage(currentPage, true)`（`LauncherActivity.java:318-322`）；退出由 `exitArrange()` 完成（:324-330）。整理模式下：

- 状态栏整体被 `status_arrange` 黑色遮罩覆盖，显示 `@string/arrange_mode`，点击即退出（:258-268）。
- 未选中的格子显示 `move_here` 徽标，点击任意格子或徽标调用 `onSlotClick`，把选中应用移动到目标位置（`ui\HomeGridController.java:177-201`、`ui\HomeListController.java:364-383`）。
- 选中格显示 `select_frame`（100px 方框）。
- 落位计算为 `AppPaging.startIndex(currentPage, spec) + slot`，随后 `appOrder.moveToSlot(...)` 并触发整页重载（`LauncherActivity.java:519-551`）。

---

## 5. 组件（widget）体系

### 5.1 组件枚举

`DeskWidget` 共 13 个值，按声明顺序即 ordinal 顺序：

| ordinal | 值 | 性质 | 行号 |
| --- | --- | --- | --- |
| 0 | `READING` | 默认组件（koreader 阅读进度） | `data\DeskWidget.java:29` |
| 1 | `WEATHER` | 天气 | :30 |
| 2 | `CLOCK` | 大号时钟 | :31 |
| 3 | `CLOCK_COMPACT` | 紧凑时钟 | :32 |
| 4 | `CALENDAR` | 月历 | :33 |
| 5 | `COVERS` | 本地书架封面流 | :34 |
| 6 | `WEREAD_COVERS` | 微信读书书架 | :35 |
| 7 | `WEREAD_MONTH` | 微信读书月度统计 | :36 |
| 8 | `WEREAD_MARK` | 微信读书摘录 | :37 |
| 9 | `KOREADER_MONTH` | koreader 月热力图 | :38 |
| 10 | `USAGE` | 应用使用统计 | :39 |
| 11 | `IMAGE` | 自定义图片 | :40 |
| 12 | `PLACEHOLDER` | 占位 | :41 |

`getCompact()` 只在 `CLOCK_COMPACT` 时为真（:113-115）；`getKey()` 为小写名（:121-125）；`next()` 按 `PICKABLE` 列表循环（:131-138）。`PICKABLE` 由 `DeskWidgetCategory.getEntries()` 顺序拼接各分类的 `getWidgets()` 得到（:82-94）。`Companion.fromKey` 找不到时回落到 `CLOCK`，并把 `IMAGE` 强行映射为 `PLACEHOLDER`（:53-71）——偏好里残留的图片组件在重启后表现为占位组件。

### 5.2 组件分类

```java
// data\DeskWidgetCategory.java:22-26
TIME       (title = widget_cat_time,  widgets = [READING, CLOCK, CLOCK_COMPACT, CALENDAR])
LOCAL      (                          widgets = [COVERS, KOREADER_MONTH])
WEREAD     (                          widgets = [WEREAD_COVERS, WEREAD_MONTH, WEREAD_MARK])
MORE       (                          widgets = [USAGE, WEATHER])
PLACEHOLDER(                          widgets = [PLACEHOLDER])
```

`label()` 在分类内组件数大于 1 时套用 `widget_cat_count` 参数化文案（:63-73），因此组件库列表里 `TIME`、`LOCAL`、`WEREAD`、`MORE` 显示带数量的标题。

### 5.3 实例化与刷新分发

组件区不动态 inflate，而是十二个面板全部在布局里预置，控制器只切可见性并刷新数据。

`refresh()` 是唯一的数据入口：先按 `layoutStore.widgetTipDone()` 补一次提示面板判定，再 `applyClockFace()`，最后按 `store.current()` 分发（`ui\DeskWidgetsController.java:3091-3125`）：

| `store.current()` | 分发目标 | 行号 |
| --- | --- | --- |
| `WEATHER` | `refreshWeather(false)` | :3098 |
| `CALENDAR` | `bindCalendar()` | :3101 |
| `COVERS` | `refreshCovers()` | :3104 |
| `WEREAD_COVERS` | `refreshWereadCovers(false)` | :3107 |
| `WEREAD_MONTH` | `refreshWereadMonth(false)` | :3110 |
| `WEREAD_MARK` | `refreshWereadMark(false)` | :3113 |
| `KOREADER_MONTH` | `refreshKoreaderMonth(false)` | :3116 |
| `USAGE` | `refreshUsage(false)` | :3119 |
| `IMAGE` | `bindImage()` | :3122 |
| `READING` / `CLOCK` / `CLOCK_COMPACT` / `PLACEHOLDER` | 无额外分支（钟面样式由 `applyClockFace()` 统一处理） | — |

`show(widget)` 完成面板切换：若同一组件且提示面板未显示则只刷新；否则置 `shownWidget`、隐藏提示面板，逐个 `setVisibility(visibleIf(...))` 设置十二个面板，`CLOCK` 与 `CLOCK_COMPACT` 共用 `clock` 面板并由 `applyClockLayout(widget.getCompact())` 调整字号与上边距（:2883-2913、:1130-1148），最后 `host.setBackgroundColor(0)` 并 `refresh()`。

`select(widget)` = 写存储 + `show` + `onWidgetChanged`（:3127-3132）；`cycle()` = `show(store.cycle())` + `onWidgetChanged`（:3048-3051），按 `DeskWidgetStore` 的循环顺序切到下一个可选组件；`setHostVisible(visible)` 在面板变为可见时补一次 `applyVisiblePanes()`（:3134-3143）。`hideWidgets()` 把十二个面板全部隐藏并释放月历、摘录、koreader 月历的位图（:2221-2230）。`showPicker()` 打开组件库弹窗（:2915-2946）。

`LauncherActivity` 在 `onStart`、切页（`showPage`）与 `applyLockPad` 之外的若干位置触发 `deskWidgets.refresh()`（:2717-2760、:943、:1002），并在 `onRequestPermissionsResult` 中把定位授权结果转给 `onLocationGranted()`（:2608-2628 → `ui\DeskWidgetsController.java:3085-3089`，仅当当前组件为 `WEATHER` 时重取天气）。

### 5.4 数据来源

| 组件 | 数据类 | 权限或前置条件 |
| --- | --- | --- |
| `READING` | `KoreaderReader`、`KoreaderBook` | 由 `KoreaderDeskController` 单独驱动，读取 `KoreaderReader.loadSnapshot` |
| `WEATHER` | `WeatherStore`、`WeatherClient`、`WeatherSnapshot`、`WeatherDay`、`WmoWeather`、`DeviceLocation`、`GeoPoint` | 需定位授权，缺权限时回调 `onNeedLocation` |
| `CALENDAR` | `MonthCalendar`、`CalendarCell`、`R.array.weekdays` | 无 |
| `COVERS` | `FileLibrary`、`CoverCrop` | 本地书籍库 |
| `WEREAD_*` | `WereadStore`、`WereadClient`、`WereadBook`、`WereadMark`、`WereadMonth`、`WereadDuration` | 需微信读书密钥，缺密钥时回调 `onNeedWereadKey` |
| `KOREADER_MONTH` | `KoreaderReader`、`KoreaderMonth`、`KoreaderStats` | koreader 统计文件 |
| `USAGE` | `AppUsage`、`AppUsageRow`、`IconCache` | 需使用情况访问权限，缺权限时回调 `onNeedUsageAccess` |
| `IMAGE` | `WidgetImageStore` | 由 `ImagePickerActivity` 选择，回调 `onPickImage` |

以上导入见 `ui\DeskWidgetsController.java:29-62`；回调使用点见 :967-1081、:1600、:1848、:2538、:2572、:3050、:3131。

### 5.5 各组件实现要点

**月历**：`buildCalendar()` 先按 `R.array.weekdays` 建 7 个表头，再建 42 个日期格（6 行 × 7 列）（`ui\DeskWidgetsController.java:1966-1989`）；每格宽度取 `@dimen/calendar_cell`、间距取 `@dimen/calendar_cell_gap`（:1972-1973）。

**热力图**：微信读书月度与 koreader 月度都用 6 行 × 7 列的 `WereadHeatCell`，`applyKoreaderHeatRows()`（:1162-1202）与 `applyWereadHeatRows()`（:1214 起，循环结构相同）都从第 5 行倒序处理 `subList(i*7, i*7+7)`；`buildKoreaderMonth()`（:1991-2004）与 `buildWereadMonth()`（:2006 起）各建 42 格。格子尺寸取 `@dimen/weread_heat_width`、`@dimen/weread_heat_height`，间距 `@dimen/weread_heat_gap`（`heatParams`，:2190-2212）。

**封面流**：`widget_covers.xml` 内有三张卡 `cover_card_1`、`cover_card_2`、`cover_card_0` 与空态 `covers_empty`；`applyCoverflowLayout` 给三张卡分别设置中心/左右尺寸与 gravity `49`（中心）、`8388659`、`8388661`（左右），并施加 `inset` 与各自的 `centerTop`/`sideTop` 偏移（`ui\DeskWidgetsController.java:1150-1160`）。这是平面错位的三卡布局，没有真实的 3D 变换。

**使用统计**：`bindUsageRows`（:1527-1601）逐行填应用名与用量，点击行回调 `onOpenUsageApp`（:1600）；缺权限时 `bindUsageHint` 显示提示并回调 `onNeedUsageAccess`。

**图片**：`bindImage()`（:1387-1425）读取 `WidgetImageStore` 的位图，空态显示 `widget_image_empty`；`applyImageFrom(Uri)` 与 `applyImageFrom(File)` 提供两条写入通道（:3023-3035）。

**提示面板**：`desk_tip`（`widget_tip.xml`，含 `widget_tip_message` 与 `widget_tip_ok`）用于首次进入组件区时的说明，`layoutStore.widgetTipDone()` 为假时由 `applyVisiblePanes()` 决定显示（:1204-1212、:3091-3093）。

### 5.6 组件库弹窗

`WidgetLibraryController` 用 `Dialog` + `R.style.Theme_TomoLauncher_WidgetLibrary` 实现，窗口背景透明、`dimAmount = 0.0f`、无动画（`ui\WidgetLibraryController.java:735-763`）。

列表页按 `DeskWidgetCategory.getEntries()` 顺序生成分类行，行与行之间插入 1px 黑线（:263-268），每行用 `item_widget_lib_category.xml`，标题取 `cat.label(context)`（:269-279）。点分类进入详情页，详情页把页脚换成“返回 / 应用”，预览容器是可左右滑动的 `SwipePageLayout widget_lib_preview_swipe`（:466-511）。点击“应用”取 `cat.getWidgets()[index]`，关闭弹窗并回调 `onSelect`（:452-464），最终落到 `DeskWidgetsController.select`。

预览按组件类型分别构造（`bindPreview`，:302 起），覆盖时钟、月历、封面流、月度热力、摘录、书架、使用统计、天气、占位、图片与阅读卡片；预览钟面同样关闭抗锯齿与次像素（:154-165）。

---

## 6. 应用图标与文字渲染

### 6.1 图标来源与外形

`AppIcons` 只承载两个状态：`normal` 与 `pressed`（`data\AppIcons.java:10-19`）。

`IconCache.load` 的三级来源：图标覆盖（`IconOverrideStore` + `CustomIcon.decodeToFace`）→ 图标包（`IconPack.match(packageName, label)`，同时取 normal 与 pressed 两态）→ 系统图标（`getActivityIcon`，失败回落白色 `ColorDrawable(-1)`）（`data\IconCache.java:145-191`）。

外形由 `frameIcon` 统一裁剪：新建 94×94 的 ARGB_8888 位图，`setDensity(0)`，用 `Rect(3,3,91,91)` 与半径 22 的圆角矩形做 `clipPath`，先以白色填充做底，再画源图；`innerLine` 为真时额外用黑色 3px 描内线（半径 20.5），最后用白色 3px 描外框（半径 23.5）（`data\IconCache.java:98-136`）。也就是说图标的观感是“白色圆角胶囊 + 黑色内描边 + 白色外描边”，无论原图标是什么形状都会得到同一轮廓。

系统图标在进入 `frameIcon` 之前先被缩放：画进 `@dimen/icon_size`（88px，`values\dimens.xml:119`）见方的 RGB_565 位图、先填白底（:200-211）。图标包资源解码时使用 RGB_565、`inScaled=false`、`inDither=false`、密度归零（:64-96）。

缓存上限 `MAX_ICONS = 48`（:34），按访问顺序淘汰（:213-217）；`LauncherActivity.onTrimMemory` 会按当前页的组件名重新 `retain` 并清宫格 key（`LauncherActivity.java:2779-2811`）。

### 6.2 位图绘制约定

宫格与列表在设置图标后，如果得到的是 `BitmapDrawable` 就关闭 `setFilterBitmap`（`ui\HomeGridController.java:211-216`、:178-183；`ui\HomeListController.java` 同构），使图标以最近邻方式缩放，避免墨水屏上出现灰度边缘。这一约定在状态栏 WiFi 图标（`ui\StatusBarController.java:262-272`）、壁纸（`ui\WallpaperController.java:49-70`）、快照（`ui\HomeSnapshotController.java:202-215`）中重复出现。

### 6.3 标签字体与描边

标签字体由 `LabelFontStore` 提供，`typeface()` 在宫格与列表里都直接施加到标签上（`ui\HomeGridController.java:217-222`、`ui\HomeListController.java:250-252`）。字体选择界面在 `DesktopStyleActivity`（`DesktopStyleActivity.java:157`），字体 token 参与 `layoutKey()`，切换后整页重建（`LauncherActivity.java:392-431`）。

`OutlineTextView` 是全部正文文本的基类，作用为“白色描边 + 黑色正文”，让文字在壁纸上依然可读：

- `UNDERLINE_PX = 2.0f`（`ui\OutlineTextView.java:21`）。
- 构造时关闭抗锯齿、次像素与线性文本，连接方式设为 `MITER`（:45-49）。
- 描边宽度缺省为 `coerceIn(textSize * 0.2f, 3.0f, 12.0f)`（:100-103）。
- 绘制顺序为先 STROKE（`outlineColor` 缺省 -1 白）再 FILL（正文黑）（:56-74）。
- 按下时额外画 2px 黑色下划线（`pressUnderline`，:76-98），列表行的按压反馈正是这条下划线（`ui\HomeListController.java:211`、:261-345）。
- `setInkOffsetY` 用于按字体把基线整体平移（`ui\OutlineTextView.java:132-136`），组件区钟面依赖它对齐（`ui\KoreaderDeskController.java:134-139`）。

宫格标签在布局上被固定为单行、`includeFontPadding=false`、四边 padding 0px、`ellipsize=end`（`layout\item_app_icon.xml:42-61`）；控制器另外再关一次抗锯齿、次像素与线性文本（`sharpenLabel`，`ui\HomeGridController.java:489-493`）。列表标签同样处理（`ui\HomeListController.java:154-158`）。

### 6.4 每页数量

| 情形 | 宫格 | 列表 |
| --- | --- | --- |
| 首屏且有组件 | 6 | 5 |
| 首屏且有组件（紧凑钟面） | 9 | 8 |
| 首屏无组件 | 12 | 10 |
| 其余页 | 12 | 10 |

数值来自 `AppPaging` 的常量与 `Spec` 池（`data\AppPaging.java:15-22`、:79-88），页数上限 15（:216-222）。宫格每行固定 3 个（`ui\HomeGridController.java:32`），列表每屏最多 10 行（`ui\HomeListController.java:64-96`）。

---

## 7. 墨水屏渲染策略

### 7.1 刷新模式与全刷触发

`EinkRefresh` 不区分局刷/全刷两种 API，它只有一种“闪屏”动作，靠黑白交替两次走完墨水屏的整屏刷新：

```java
// ui\EinkRefresh.java:19-20
PHASE_MS = 500;
TAG = "tomo_eink_flash";
```

`flash(anchor)` 先尝试调用无障碍服务的 `flashScreen()`（:50 → `overlay\TomoAccessibilityService.java:79-82` → `OverlayHost.flashScreen()`）；失败时在 `decorView` 上覆盖一个全屏 `View`，`setBackgroundColor(ViewCompat.MEASURED_STATE_MASK)`（黑，-16777216，:69），500ms 后改成 `-1`（白，:29），再过 500ms 移除（:39-46）。同一时刻只允许一个闪烁层，靠 `findViewWithTag(TAG)` 去重（:63）。

触发点集中在两处：状态栏时间与刷新图标的点击（`LauncherActivity.java:460-471`），以及 `TomoChrome` 在二级页面上的同一组控件（`ui\TomoChrome.java:68-74`）。也就是说全刷由用户主动发起，切页、开托盘等交互不触发布局级重绘闪屏。

### 7.2 滚动与惯性

`NoFlingScrollView` 关掉一切惯性行为：`setOverScrollMode(2)`、隐藏滚动条、`setSmoothScrollingEnabled(false)`，并覆写 `fling(int)` 为空实现（`ui\NoFlingScrollView.java:28-43`）。墨水屏刷新率低，惯性滚动会产生连续残影，因此惯性被移除，抬手即停。

### 7.3 动画与反馈的全局处理

- 窗口动画：`applyEinkWindow()` 里 `setWindowAnimations(0)` 与 `clearFlags(2)`（`LauncherActivity.java:155-158`）。
- 主题层：`Theme.TomoLauncher` 与 `Theme.TomoLauncher.Dialog` 都把 `android:windowAnimationStyle` 指向 `@style/Animation.TomoLauncher.None`（`values\styles.xml:3`、:35、:49）。
- 触感与音效：桌面八个承载点击的 view 逐个 `setHapticFeedbackEnabled(false)` + `setSoundEffectsEnabled(false)`（`LauncherActivity.java:310-315`、接线 :2351-2416）；组件区、列表行、日历格、热力格在创建时也逐个关闭（`ui\DeskWidgetsController.java:1998-2000` 等）。
- 属性动画：整个 `com.astraabove.tomo` 包里未出现 `ValueAnimator`、`ObjectAnimator`、`startAnimation`、`AnimationUtils`、`ViewPropertyAnimator`、`TransitionManager` 的任何引用（全包检索为零命中），即没有补间动画与属性动画。

### 7.4 灰度抖动

包内确实存在一处显式的有序抖动实现，专用于阅读热力格：

```java
// ui\WereadHeatDither.java:11
{{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}}
```

`filled(x, y, level)` 在 `level < 4` 时按 `BAYER[y % 4][x % 4] >= level * 4` 判定该像素是否填黑（`ui\WereadHeatDither.java:16-28`）。`WereadHeatCell.onDraw` 先铺白底，再逐像素调用该函数画出 1px 黑方块，最后描 1px 黑边（`ui\WereadHeatCell.java:94-128`），`setLevel` 把档位夹在 `0..4`（:132-139）。这是全包唯一的抖动使用点，其他控件一律显式 `setDither(false)`。

### 7.5 逐像素圆角与反色按压

为了在墨水屏上得到锐利边缘，圆角矩形与胶囊用逐像素算法生成位图后直接 `drawBitmap`，不使用抗锯齿绘制：

- `EinkRoundRect.write(pixels, width, height, radius, stroke, fill, strokeColor, outside)`（`ui\EinkRoundRect.java:44-65`）与 `EinkCapsule.write(...)`（`ui\EinkCapsule.java:55-69`）都是“outside / stroke / fill 三区逐像素着色”，命中判定分别在 :17-42 与 :24-53。
- `EinkRoundRectDrawable` 缓存正反两张位图，`bitmapFor` 在尺寸变化时同时丢弃两张（`ui\EinkRoundRectDrawable.java:38-65`）；`Paint` 关闭 `antiAlias`、`filterBitmap`、`dither`（:31-35）。
- `WashRoundRectDrawable` 复用同一算法，提供 `attach(View)`（整理模式目标框，填充为 `@color/wallpaper_wash`）与 `attachSelect(View, radiusPx)`（选中框，填充 0）（`ui\WashRoundRectDrawable.java:43-52`、:66-81）。

少数需要平滑曲线的地方才启用抗锯齿：`AntiAliasRoundRectDrawable` 用 `Paint(1)` 双画笔绘制（`ui\AntiAliasRoundRectDrawable.java:34-44`、:52-64），供阅读卡片 `AntiAliasRoundRectLayout` 使用（`layout\widget_koreader.xml:64-76`）。

按压反馈采用“即时反色”，没有过渡：`EinkPressTextView` 按下变黑底白字、松开还原（`ui\EinkPressTextView.java:41-51`）；`EinkPressImageView` 按下变黑底并用 `SRC_IN` 白色滤色（`ui\EinkPressImageView.java:41-51`）；`EinkRoundRectLayout` 通过 `einkInvertPress` 与 `einkFilled` 两个自定义属性决定反色，并在 `dispatchDraw` 里用 `clipPath` 裁圆角，文本与图标颜色随反色一起切换（`ui\EinkRoundRectLayout.java:53-66`、:76-128）。

`PercentBar`、`StepBar`、`StepGlyphView`、`PinDotsView`、`PageDots`、`TrayHandleView` 同样全部走“关闭抗锯齿 + 纯黑白”的画法：

| 控件 | 关键点 | 行号 |
| --- | --- | --- |
| `PercentBar` | 填充 4px、底线 4px、轨道 1px，`setDither(false)` | `ui\PercentBar.java:21-23`、:44-45、:69-76 |
| `StepBar` | 默认 10 档，白底圆角 + 黑填充段 + 分界刻度（已填充侧白、未填充侧黑） | `ui\StepBar.java:53`、:101-132 |
| `StepGlyphView` | 一条粗横线，`stepPlus` 为真时补一条竖线 | `ui\StepGlyphView.java:47-50`、:80-83 |
| `PinDotsView` | 固定 4 个点，已填为白底黑实心，未填为双层描边 | `ui\PinDotsView.java:41-46`、:64-88 |
| `PageDots` | 页数不大于 1 时隐藏；当前页实心黑 | `ui\PageDots.java:65-67`、:101-113 |
| `TrayHandleView` | 黑填充白底，按下画外环 | `ui\TrayHandleView.java:45-57`、:92-99 |

---

## 8. 锁屏与 PIN 面板

### 8.1 判定条件

锁屏状态由两处共同决定：

```java
// data\LockSession.java:41-44
return store.hasPin() && !unlocked;
```

`LockSession` 是一个进程内单例，`lock()` 与 `unlock()` 只改内存标志并通知监听者（`data\LockSession.java:37-53`），`watch/unwatch` 维护监听列表（:55-63）。`LockStore` 持久化四位 PIN：`LENGTH = 4`、`PREFS = "tomo_lock"`、`SALT_BYTES = 16`，仅存 `pin_hash` 与 `pin_salt`（`data\LockStore.java:26-30`、:120-158）。

`LockGate` 是跨 Activity 的统一入口：`ensure(activity)` 对 `PinPadActivity` 与 `LauncherActivity` 直接返回，其余页面在“需要锁且无障碍服务未生效”时启动 `PinPadActivity` 的解锁模式；`forget()` 清空 PIN 存储并解锁会话；`screenOffReceiver` 监听 `ACTION_SCREEN_OFF`，在熄屏时调用 `LockSession.lock()` 并回调（`ui\LockGate.java:46-83`）。

### 8.2 UI 结构

锁屏是主界面布局内的一个覆盖层（`lock_pad` → `include_pin_pad.xml`），根为白底、`focusable` + `clickable` 的全屏 `FrameLayout`，与主界面共享同一个 Activity（`layout\activity_launcher.xml:179-182`、`layout\include_pin_pad.xml:2-7`）。

层次为：可选的锁屏壁纸 `pin_wallpaper` 与压白层 `pin_wallpaper_wash`（默认 gone）→ `pin_content` 垂直居中 → `pin_header`（提示文案 `pin_prompt`、四点指示 `pin_dots`、错误文案 `pin_error`，后者初始 invisible）→ 4 行 × 3 列键盘 `pin_keypad`（`pin_key_1`…`pin_key_9`、`pin_key_blank`、`pin_key_0`、`pin_key_backspace`）（`layout\include_pin_pad.xml:21-125`）。

### 8.3 交互流程

| 环节 | 实现 | 行号 |
| --- | --- | --- |
| 显示判定 | `applyLockPad()`：`needsLock` 为真则可见并刷新锁屏壁纸；由隐藏转可见时若托盘展开则先收起托盘并重置提示 | `LauncherActivity.java:161-225` |
| 会话监听 | `LockSession.watch(lockWatch)`，解锁状态变化时重新执行 `applyLockPad` | :2483 |
| 熄灭屏幕 | `LockGate.screenOffReceiver` 触发后 `applyLockPad()` | :2484-2503 |
| 数字输入 | `append(digit)`：满 4 位即回调校验，输入时清除错误提示并更新四个圆点 | `ui\PinPadController.java:153-167` |
| 回删 | `delete()`：删除末位并回退圆点 | :178-187 |
| 校验 | 构造时传入 `onComplete`：`LockStore.matches(pin)` 为真则 `LockSession.unlock()`；为假则把提示文案重置为 `R.string.pin_enter` 并保留错误行可见 | `LauncherActivity.java:2431-2482` |
| 忘记 PIN | 连点空白键 `FORGET_TAPS = 15` 次触发 `onForget`，走 `LockGate.forget()` | `ui\PinPadController.java:31`、:217-225 |
| 方向变化 | `applyOrientation()` 按 root 宽高（为 0 时退回 `DisplayMetrics`）判定横竖屏，分别切换 `pin_content` 方向、页头权重、键盘宽度与各段上边距 | :227-283 |
| 数字键字体 | `applyDigitFace` 使用 `ClockFace.SPACE_GROTESK` 并关闭抗锯齿/次像素/线性文本 | :169-176 |

锁屏可见时返回键被吞掉，这一点在 `onBackPressed`（`LauncherActivity.java:1438-1469`）与无障碍服务的按键过滤（`overlay\TomoAccessibilityService.java:263-266`）中各有一份实现。

### 8.4 无障碍浮层的第二套锁

无障碍服务内另有一个 `OverlayHost`，会在浮层上加载 `overlay_status_bar.xml` 并复用 `LockGate` 的判定：`applyOverlayVisibility()` 先看 `LockGate.needsLock`，为真则 `showLockPad()` 并直接返回，为假则 `hideLockPad()` 再继续状态栏与托盘的显示逻辑（`overlay\TomoAccessibilityService.java:135-171`）。因此第三方应用前台时，锁屏同样由浮层承担，无需回到桌面。

---

## 9. 彩蛋与次要装配

### 9.1 EasterEgg 触发条件

```java
// ui\EasterEgg.java:22-24
SHOW_MS = 2000;
TAG = "easter_egg";
TAP_THRESHOLD = 5;
```

`tap()` 逐次累加计数，前 5 次直接返回，第 6 次触发 `show()` 并把计数清零（`ui\EasterEgg.java:95-104`）。`show()` 在 `decorView` 上加一个全屏 `FrameLayout`，内部放 `R.drawable.easter_egg` 的 `ImageView`，同样关闭 `setFilterBitmap(false)`，2 秒后自动移除（:41-77）。

彩蛋只挂在“关于”页：`AboutPageActivity` 持有唯一实例（`AboutPageActivity.java:37`），触发点是版本行 `R.string.pref_version` 的点击回调（:258、:270-274），销毁时清理（:332）。主页面的任何控件都不触发彩蛋。

### 9.2 二级页面的状态栏复用

`TomoChrome` 是给二级页面用的轻量状态栏装配器：直接 `findViewById` 取 `time_view`、`battery_view`、`refresh_view`、`status_tray_hit`、`wifi_view`、`charge_view`、`status_bar`，把点击接到 `EinkRefresh.flash` 与 `TomoAccessibilityService.showTray()`，`onResume` 先 `LockGate.ensure` 再启动 `StatusBarController`，`onStart/onStop` 走 `TomoForeground.enter/leave`（`ui\TomoChrome.java:26-102`）。它在十二个页面中被实例化：`AboutPageActivity.java:203`、`BookshelfActivity.java:1290`、`DesktopPrefsActivity.java:57`、`DesktopStyleActivity.java:152`、`FileManagerActivity.java:2058`、`LabelFontPickerActivity.java:569`、`ImagePickerActivity.java:641`、`OobeActivity.java:573`、`LockSettingsActivity.java:69`、`PermissionsActivity.java:155`、`WereadSkillActivity.java:219`、`SettingsActivity.java:154`。

### 9.3 回到原桌面

`ReturnHome.confirm(activity)` 用 `R.layout.dialog_files_confirm` 弹确认框，文案 `R.string.pref_default_home_confirm`，确认后调用 `DesktopDefaults.openOriginalDesktop`，失败则 Toast `R.string.pref_default_home_missing`（`ui\ReturnHome.java:43-67`）。该入口只在 `DesktopPrefsActivity.java:221` 被调用。

首次启动引导由 `maybeStartOobe()` 控制：`oobeDone()` 为假且本次未启动过则打开 `OobeActivity`（`LauncherActivity.java:433-447`）。

---

## 10. 未确认点

1. `ui\HomeSnapshotController` 与 `data\HomePageSnapshot` 构成一整套“把整页渲染成位图再贴图”的方案（`ui\HomeSnapshotController.java:116-159`、`data\HomePageSnapshot.java:468-555`），但全包检索显示这两个类只被彼此引用，`LauncherActivity` 未实例化它们。它是否在某个未被反编译的入口或后续版本中启用，静态文本无法确认。
2. `LauncherActivity.showAppMenu` 方法体未被 JADX 还原（`LauncherActivity.java:811-828` 抛 `UnsupportedOperationException("Method not decompiled: ...showAppMenu...")`）。应用长按菜单可见的功能条目（应用信息、换图标、清除图标、隐藏、卸载、音量键方向）是从对应 lambda 与字符串推断的，条目顺序、是否还有取消项以外的其它项均无法确认。
3. `SwipePageLayout.onInterceptTouchEvent` 同样未被还原为 Java（`ui\SwipePageLayout.java:181-242`），阈值与拦截条件来自字段定义与字节码注释。实际拦截时机（例如是否要求主方向占优）需真机或字节码工具复核。
4. `WereadHeatCell` 有 `today` 字段与 `setToday`（`ui\WereadHeatCell.java:23`、:141-145），但 `onDraw` 未读取该标记（:67-130），“今天”这一格的额外标记在视觉上是否被省略无法确认。
5. `WidgetLibraryController` 的十个预览构造（`ui\WidgetLibraryController.java:514-713`）与 `res\layout\preview_widget_*.xml` 的一一对应关系未逐条核对，预览里显示的示例文案与真实组件文案是否完全一致无法确认。
6. 快捷托盘的通道命名反常：`tray_brightness_*` 绑定 `screen_brightness1`，`tray_color_*` 绑定 `screen_brightness`（`ui\QuickTrayController.java:344-357`、:188-204）。从字面看“亮度”应绑 `screen_brightness`，代码里恰好相反，究竟是固件定义如此还是历史遗留，需要真机调光确认。
7. 宫格图标同时挂了 `TwoStageHold` 与 `setOnLongClickListener`（`ui\HomeGridController.java:288-358`），列表行同样双注册（`ui\HomeListController.java:261-361`）。两套长按是否会在同一手势中同时触发菜单，静态阅读无法排除。
8. `TomoAccessibilityService` 的浮层状态栏（`layout\overlay_status_bar.xml`）与主界面状态栏（`layout\include_status_bar.xml`）结构相同而 id 前缀不同，两套是否在字体、间距与刷新时机上完全一致未逐项比对。
9. 需求中提到的“收藏”：包内未找到独立的收藏功能模块，语义最接近的是微信读书书架组件 `WEREAD_COVERS` 与本地书架页 `BookshelfActivity`（`LauncherActivity.java:354-390` 的跳转分支）。是否存在以“收藏”命名的其它入口，未在 UI 子系统范围内找到。
10. `IconPack`、`CustomIcon`、`IconOverrideStore` 的解码与匹配规则（图标包如何按包名或标签命中）未展开，本报告只覆盖 `IconCache` 的调用顺序。
11. `DeskWidgetsController` 各组件内部的重取节奏（天气缓存有效期、封面位图回收时机、使用统计的采样窗口）未逐行核对，只确认了入口方法与数据类。
12. `res\layout\preview_widget_label.xml`、`oobe_preview_grid/list.xml` 等引导与预览布局只确认了存在与文件位置，未逐字段比对与主界面布局的差异。

