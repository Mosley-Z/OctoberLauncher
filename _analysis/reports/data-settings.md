# 图墨桌面（com.astraabove.tomo v0.5.0）数据层与设置体系逆向报告

分析对象：`tomo-launcher-0.5.0.apk`，versionCode 13，versionName 0.5.0，minSdk 24，targetSdk 34，原程序 Kotlin。
分析材料：JADX 反编译 Java 源码（`jadx-out\sources\com\astraabove\tomo\`）与反编译资源（`jadx-out\resources\`）。全部为只读静态分析，未运行程序。
引用约定：一律使用包内相对位置，例如 `data/WallpaperStore.java:28` 指 `...\tomo\data\WallpaperStore.java` 第 28 行；资源文件以 `res/...` 指 `...\resources\res\...`。

---

## 一、总览

应用的设置体系分成两条互不相通的支路，这一点直接决定了「设置项清单」的读法。

第一条支路是系统设置跳转路由。`SettingsActivity` 冒充系统设置入口，列出 8 个条目，其中 7 个只是转调 Android 系统设置页，第 8 个才进入应用自身设置。`data/SettingsPage.java:15-22` 的 8 个枚举值与 8 个可见条目一一对应：

| 序号 | 枚举 | 中文标题 | 字符串资源 | 图标资源 | 点击行为 |
| --- | --- | --- | --- | --- | --- |
| 1 | NETWORK | 网络与互联网 | settings_network | ic_settings_network | 跳系统 WLAN 设置 |
| 2 | CONNECTED | 已连接设备 | settings_connected | ic_settings_connected | 跳系统蓝牙设置 |
| 3 | APPS | 应用管理 | settings_apps | ic_settings_apps | 跳系统应用设置 |
| 4 | POWER | 电源管理 | settings_power | ic_settings_power | 跳系统显示与电池设置 |
| 5 | STORAGE | 存储空间 | settings_storage | ic_settings_storage | 跳系统存储设置 |
| 6 | DEVICE | 设备信息 | settings_device | ic_settings_device | 跳系统设备信息 |
| 7 | SYSTEM | 系统设置 | settings_system | ic_settings_system | 跳系统设置子页 |
| 8 | DESKTOP | 桌面偏好 | settings_desktop | ic_settings_system | 进入 DesktopPrefsActivity |

条目定义集中在 `data/SystemSettings.java:29`。第 8 项的图标复用了 `ic_settings_system`，与前一项重复，属于源码原样。

第二条支路是应用自身设置，由四个页面与若干对话框构成：`DesktopPrefsActivity`（桌面偏好，7 行）、`DesktopStyleActivity`（桌面美化，7 行）、`LockSettingsActivity`（开机密码，2 行）、`PermissionsActivity`（权限管理，8 行），另有 `AboutPageActivity`（关于，3 行）、`OobeActivity`（首启引导，2 步）、`dialog_wallpaper` 长按菜单（7 行）。页面全部由代码动态构建，`res/xml/` 下没有 Preference 屏幕 XML，因此设置项只能从 Activity 的构建代码逐行读取。

---

## 二、设置项全清单

### 2.1 设置页的组织方式

`SettingsEntry` 是唯一的数据载体，字段只有三个（`data/SettingsEntry.java:9-19`）：

```java
private final int icon;              // drawable 资源 ID
private final SettingsPage page;     // 页面枚举，决定点击去向
private final int title;             // string 资源 ID
```

没有存储键、没有默认值、没有分组信息。`SettingsEntry(page, title, icon)` 的构造顺序与字段声明顺序不同，阅读时容易误判。

列表渲染在 `SettingsActivity.java:147-226`：`R.layout.activity_settings` 内是两列网格，条目用 `CollectionsKt.chunked(entries, 2)` 切分，行布局 `item_settings_row`，标题控件 `settings_row_title`，图标控件 `settings_row_icon`；图标统一 `BitmapDrawable.setFilterBitmap(false)` 关闭双线性插值以适配墨水屏；同时禁用触感与点击音效。

页内导航集中在 `SettingsActivity.java:133-139` 的 `openPage`：`page == DESKTOP` 打开 `DesktopPrefsActivity`，其余交给 `SystemSettings.INSTANCE.open(this, page, sourcePackage)`。

隐藏交互：`SettingsActivity.java:30-31` 定义 `LOGO_TAP_WINDOW_MS = 2000`、`LOGO_UNLOCK_TAPS = 3`；`onLogoClick`（:121-131）在 2 秒窗口内累计点击 logo 三次触发 `confirmUnlock()`（:65-102），确认后打开 `DarkThemeUnlockActivity`。

### 2.2 桌面偏好页 DesktopPrefsActivity

页面标题取自 `R.string.settings_desktop`，头部右侧动作是「关于」（`pref_about_short`，`DesktopPrefsActivity.java:65`），进入 `AboutPageActivity.Page.ABOUT`。列表容器 `R.id.desktop_prefs_list`，行由 `ui/PrefPage` 的 `addRow` / `addSwitchRow` / `addHint` 生成。

| 顺序 | 中文标题 | 资源 | 行号 | 控件 | 右值 | 动作与存储 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 桌面美化 | pref_desktop_style | 84-101 | 行 | 无 | 打开 DesktopStyleActivity |
| 2 | 图墨文件管理 | pref_tomo_files | 103-147 | 开关 | 开/关 | `DesktopLayoutStore.setTomoFilesEnabled`，键 `tomo_desktop/tomo_files`，默认 true |
| 3 | 开机密码 | pref_lock | 149-166 | 行 | 无 | 打开 LockSettingsActivity |
| 4 | 微信读书 | pref_weread_skill | 168-185 | 行 | 已填写/未填写 | `WereadStore.hasKey()`；点击打开 WereadSkillActivity |
| 5 | 权限管理 | pref_permissions | 187-204 | 行 | 无 | 打开 PermissionsActivity |
| 6 | 返回原桌面 | pref_default_home | 206-223 | 行 | 无 | `ReturnHome.confirm()`，实际跳转见 `data/DesktopDefaults.java` |
| 7 | 联网检查更新 | pref_update | 225-248 | 行 | 动态状态文字 | `UpdateRunner.click()`，状态由回调 `setText` 写入 |
| — | 版本提示 | update_manual_hint | 267-270 | 提示 | — | 文案含当前版本号，指向 astraabove.com/usetomo |

第 4 行的右值由 `wereadStatus()`（:45）生成，`hasKey()` 为真显示 `pref_weread_key_set`「已填写」，否则 `pref_weread_key_empty`「未填写」。`onResume` 中会刷新该值。

### 2.3 桌面美化页 DesktopStyleActivity

页面标题沿用 `R.string.pref_desktop_style`（`DesktopStyleActivity.java:159`），布局复用 `R.layout.activity_desktop_prefs`，头部无右侧动作。

| 顺序 | 中文标题 | 资源 | 行号 | 控件 | 右值 | 动作与存储 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 桌面布局 | pref_desktop_layout | 165-205 | 行 | 网格/列表 | `setKind`，键 `tomo_desktop/kind`，值域 grid、list，默认 grid |
| 2 | 桌面小组件 | pref_desktop_widget | 207-257 | 开关 | 开/关 | 键 `tomo_desktop/widget`，默认 true |
| 3 | 仅展示图标 | pref_icon_only | 259-308 | 开关 | 开/关 | 键 `tomo_desktop/icon_only`，默认 false |
| 4 | 使用图墨下拉栏 | pref_tomo_tray | 310-359 | 开关 | 开/关 | 键 `tomo_desktop/tray`，默认 true |
| 5 | 12小时制时间 | pref_twelve_hour | 361-411 | 开关 | 开/关 | 键 `tomo_clock/twelve_hour`，默认 false；写后发 `ClockTime.ACTION_CHANGED` 广播 |
| 6 | 大时钟字体 | pref_clock_face | 413-438 | 行（带箭头） | 当前字体名 | 弹 `dialog_clock_face`，遍历 `ClockFace.getEntries()`，写入 `tomo_clock/clock_face` |
| 7 | 应用名称字体 | pref_label_font | 440-457 | 行（带箭头） | 字体名或「系统字体」 | 打开 LabelFontPickerActivity，写入 `tomo_label_font/path` |
| — | 提示 | pref_label_font_hint | 458 | 提示 | — | 「TTF 字体需放在 Fonts 文件夹内」 |

第 1 行右值来自 `layoutLabel()`（:73）：`DesktopKind.LIST` 显示 `oobe_layout_list`「列表」，否则 `oobe_layout_grid`「网格」。

### 2.4 开机密码页 LockSettingsActivity

| 顺序 | 中文标题 | 资源 | 行号 | 控件 | 动作 |
| --- | --- | --- | --- | --- | --- |
| 1 | 开机密码 | pref_lock | 86 | 开关 | 已有密码时 `PinPadActivity.verifyDisable`，否则 `PinPadActivity.set` |
| 2 | 更改密码 | pref_lock_change | 128-145 | 行 | `PinPadActivity.verifyChange` |

第 2 行与分隔线仅在 `store.hasPin()` 为真时可见（:42-55）；`onResume` 先调 `LockGate.ensure(this)` 再 `bindRows`（:175-176）。开关的初始状态即 `hasPin()`。

### 2.5 权限管理页 PermissionsActivity

页面标题 `R.string.pref_permissions`（`PermissionsActivity.java:158-160`）。行按 `TomoPermission.getEntries()` 全量生成（:162-165），行布局 `item_permission_row`，含 `perm_title`、`perm_reason`、`perm_status`、`perm_chevron` 四个控件，图标同样关闭 filterBitmap（:70），禁用触感与音效（:75-76）。状态文案取 `granted ? perm_granted : perm_denied`（:99）。点击行弹 `dialog_permission`（:104-127），提供「取消授权」`perm_dialog_revoke` 与「去授权」`perm_dialog_grant`。请求码 3101 返回后 `refreshStatuses`（:183-187）。

八项权限的身份、标题与说明资源定义在 `data/TomoPermission.java:25-32`：

| 序号 | 枚举 | 键 | 标题资源 | 说明资源 |
| --- | --- | --- | --- | --- |
| 1 | NETWORK | network | perm_network | perm_network_why |
| 2 | STORAGE | storage | perm_storage | perm_storage_why |
| 3 | LOCATION | location | perm_location | perm_location_why |
| 4 | BLUETOOTH | bluetooth | perm_bluetooth | perm_bluetooth_why |
| 5 | WRITE_SETTINGS | write_settings | perm_write_settings | perm_write_settings_why |
| 6 | USAGE | usage | perm_usage | perm_usage_why |
| 7 | NOTIFICATIONS | notifications | perm_notifications | perm_notifications_why |
| 8 | ACCESSIBILITY | accessibility | perm_accessibility | perm_accessibility_why |

`fromKey` 未命中返回 null（:44-58），因此权限键只是展示与查询用的稳定标识，与 SharedPreferences 无关。

### 2.6 关于页与协议页 AboutPageActivity

`Page` 枚举三个值（`AboutPageActivity.java:76-78`）：ABOUT 对应 `pref_about_short`、TERMS 对应 `pref_terms`、PRIVACY 对应 `pref_privacy`；`EXTRA_PAGE = "extra_about_page"`（:34），`fromKey` 未命中回落 ABOUT（:104）。正文按页取 `about_body`、`terms_placeholder`、`privacy_placeholder`（:159-178）。

| 页面 | 行 | 内容 | 动作 |
| --- | --- | --- | --- |
| ABOUT | 256-275 | 版本 | 右值 `UpdateClient.installedName`，点击触发 `EasterEgg.tap` |
| ABOUT | 284-301 | 用户协议 | 切换到 Page.TERMS |
| ABOUT | 310-327 | 隐私政策 | 切换到 Page.PRIVACY |

TERMS 与 PRIVACY 页隐藏 `about_version` 行（:217-233），只保留正文。

### 2.7 首启引导 OobeActivity

只有两步，进度文案 `oobe_progress` 显示 1/2、2/2（:518、:525）。初始值直接读现有存储（:575-583）：`DesktopLayoutStore.kind()` 与 `widgetEnabled()`。第 1 步为布局选择，标题 `oobe_layout_title`「您偏好使用哪种桌面布局?」，选项 `oobe_layout_grid`「网格」、`oobe_layout_list`「列表」；第 2 步为小组件开关，标题 `oobe_widget_title`「是否启用小组件？」。`finishSetup`（:403-417）写 `setKind`、`setWidgetEnabled`、`completeOobe()`，并广播 `DesktopLayoutStore.ACTION_CHANGED`。更新说明由 `ChangelogClient` 预取，文案 `oobe_notes_title`、`oobe_notes_body`。首页顶部还显示 `oobe_version` 与 `UpdateClient.installedName`（:590）。

### 2.8 长按壁纸菜单 dialog_wallpaper

入口为 `LauncherActivity.showWallpaperMenu()`（`LauncherActivity.java:1079-1194`），布局 `R.layout.dialog_wallpaper`，样式 `Theme_TomoLauncher_Dialog`。各行的可见性依赖当前状态。

| 行 id | 文案资源 | 行号 | 可见条件 | 动作 |
| --- | --- | --- | --- | --- |
| wallpaper_choose | 选择壁纸 | 1087-1092 | 恒显示 | 打开 ImagePickerActivity，请求码 1001（:582） |
| wallpaper_clear | 恢复白底 | 1093-1113 | `hasWallpaper()` | 确认后清壁纸，确认文案 `wallpaper_clear_confirm`（:1209） |
| wallpaper_unhide | 取消隐藏应用 | 1114-1133 | `HiddenAppsStore.hasHidden()` | 清空隐藏集合 |
| wallpaper_reset_icons | 重置图标 | 1134-1151 | `kind() != LIST` | 清空图标覆盖 |
| wallpaper_enable_widget | 启用小组件 | 1152-1170 | `!widgetEnabled()` | 写 `tomo_desktop/widget` |
| wallpaper_desktop_prefs | 桌面偏好 | 1171-1177 | 恒显示 | 打开 DesktopPrefsActivity |
| wallpaper_save_theme | 保存为主题包 | 1178-1184 | 恒显示 | `saveThemePack()`（:769-800） |

`saveThemePack` 在缺少存储权限时置 `pendingSaveTheme` 并申请 STORAGE（:770-774）；无壁纸且无图标覆盖时提示 `theme_save_empty`（:788-790）；成功后提示 `theme_save_ok`，文案含文件名。

### 2.9 设置项与存储键对照总表

| 中文标题 | 所属页面 | 存储文件 | 键 | 类型 | 默认值 | 取值域 |
| --- | --- | --- | --- | --- | --- | --- |
| 桌面布局 | 桌面美化 | tomo_desktop | kind | String | grid | grid、list |
| 桌面小组件 | 桌面美化 | tomo_desktop | widget | Boolean | true | — |
| 仅展示图标 | 桌面美化 | tomo_desktop | icon_only | Boolean | false | — |
| 使用图墨下拉栏 | 桌面美化 | tomo_desktop | tray | Boolean | true | — |
| 图墨文件管理 | 桌面偏好 | tomo_desktop | tomo_files | Boolean | true | — |
| 图墨状态栏覆盖 | 非设置项 | tomo_desktop | overlay | Boolean | false | — |
| 首启完成标记 | 非设置项 | tomo_desktop | oobe_done | Boolean | false | — |
| 组件提示已读 | 非设置项 | tomo_desktop | widget_tip | Boolean | false | — |
| 12小时制时间 | 桌面美化 | tomo_clock | twelve_hour | Boolean | false | — |
| 大时钟字体 | 桌面美化 | tomo_clock | clock_face | String | literata | 7 个字面 |
| 应用名称字体 | 桌面美化 | tomo_label_font | path | String | ""（系统字体） | TTF 绝对位置或空 |
| 开机密码摘要 | 开机密码 | tomo_lock | pin_hash | String | "" | 64 位小写十六进制 |
| 开机密码盐 | 开机密码 | tomo_lock | pin_salt | String | "" | 32 位小写十六进制 |
| 微信读书 Key | 桌面偏好 | tomo_weread | api_key | String | "" | wrk- 开头 |
| 壁纸洗白 | 非设置项 | tomo_wallpaper | wash | Boolean | false | — |
| 主题包应用时间 | 非设置项 | tomo_theme | applied_at | Long | 0 | — |
| 桌面组件类型 | 非设置项 | tomo_desk_widget | widget | String | clock | 13 个字面 |
| 书架布局 | 非设置项 | tomo_shelf | kind | String | grid | grid、list |
| 音量键反色集合 | 非设置项 | tomo_volume_keys | inverted | StringSet | 空（回落内置默认） | 包名集合 |
| 最近文件清理时间 | 非设置项 | tomo_recent_files | cleared_at | Long | 0 | — |
| 存储总容量 | 非设置项 | tomo_device_storage | total_bytes | Long | 0 | — |
| 隐藏应用集合 | 非设置项 | tomo_hidden_apps | hidden | StringSet | 空 | 组件扁平化字符串 |
| 应用排序 | 非设置项 | tomo_app_order | order | String | "" | 换行分隔的组件串 |
| 天气快照 | 非设置项 | tomo_weather | json、at、lat、lon | String/Long | 空/0 | JSON 原文 |

---

## 三、持久化机制

### 3.1 机制选型

三套机制并存：

1. `SharedPreferences`：全部开关、枚举字面、缓存 JSON、哈希凭据。
2. 应用私有目录下的文件：壁纸、组件图片、图标覆盖、微信读书封面、书架合集、书架索引、更新包。
3. 外部只读 SQLite：仅 `KoreaderStats` 读取 KOReader 自带的 `statistics.sqlite3`，程序自身不建库。

代码中不存在 `SQLiteOpenHelper` 子类、Room 注解或 `openOrCreateDatabase` 调用；全工程唯一的 `SQLiteDatabase` 使用点在 `data/KoreaderStats.java`，且为只读副本：

```java
// data/KoreaderStats.java:20-22
public static final String DB_RELATIVE = "settings/statistics.sqlite3";
private static final String TABLE = "page_stat_data";
```

`copyIfNeeded` 先把数据库及 sidecar 复制到 `cacheDir/koreader_stats`（缓存目录来自 `ui/DeskWidgetsController.java:2488`），再在副本上执行 `SELECT`，据此避免锁住 KOReader 的数据库。表名为 `page_stat_data`，与 KOReader 原生结构对应。

### 3.2 SharedPreferences 文件全表

所有 `getSharedPreferences` 调用一律带 `Context.MODE_PRIVATE`（即 0），并且除 `WereadStore` 使用传入的 applicationContext 外，其余均显式调用 `getApplicationContext()`。文件名统一为 `tomo_` 前缀加小写下划线领域名。

| 文件名 | 常量行 | 键 | 存取方法 |
| --- | --- | --- | --- |
| tomo_desktop | DesktopLayoutStore.java:13 | icon_only、kind、oobe_done、overlay、tomo_files、tray、widget、widget_tip | 各 get/set，`paging()` 组合 |
| tomo_clock | ClockFaceStore.java:18 | clock_face、twelve_hour | current、setFace、twelveHour |
| tomo_label_font | LabelFontStore.java:24 | path | currentPath、setPath、typeface |
| tomo_lock | LockStore.java:26-28 | pin_hash、pin_salt | hasPin、matches、setPin、clear |
| tomo_wallpaper | WallpaperStore.java:28 | wash | wash、setWash |
| tomo_theme | ThemePack.java:49 | applied_at | appliedAt、bump |
| tomo_app_order | AppOrderStore.java:23-24 | order | saved、save |
| tomo_hidden_apps | HiddenAppsStore.java:20-21 | hidden（StringSet） | keys、hide、unhide、clear |
| tomo_desk_widget | DeskWidgetStore.java:13-14 | widget | current、set、cycle |
| tomo_shelf | ShelfLayoutStore.java:12-13 | kind | kind、setKind、toggle |
| tomo_volume_keys | VolumeKeyStore.java:16-17 | inverted（StringSet） | inverted、isInverted、toggle |
| tomo_recent_files | RecentRecordStore.java:12-13 | cleared_at（Long） | clearedAt、clear |
| tomo_device_storage | StorageCapacityStore.java:12-13 | total_bytes（Long） | total、save |
| tomo_weather | WeatherStore.java:15-20 | at、json、lat、lon | save、snapshot、latitude、longitude |
| tomo_weread | WereadStore.java:33-44 | api_key、mark_json、mark_at、month_json、month_at、month_prev_seconds、month_prev_ok、progress_json、shelf_json、shelf_at | 见 4.4 |

命名规则可归纳为三层：文件级前缀 `tomo_` 表领域，键名全小写下划线，布尔量不加 `is_`/`enable_` 修饰；枚举一律存 `name().toLowerCase(Locale.ROOT)` 得到的短字面（`DesktopKind.java:79-83`、`DeskWidget.java:121-125`、`ShelfKind.java:79`），未命中时回落到默认值而不是报错。

### 3.3 文件数据全表

所有文件位于 `filesDir`（应用私有目录 `data/data/com.astraabove.tomo/files/`）或缓存目录。

| 文件或目录 | 位置常量 | 内容 | 写入方 |
| --- | --- | --- | --- |
| desktop_wallpaper.img | WallpaperStore.java:28、:64 | 壁纸原图字节，读取时 RGB_565 采样 | WallpaperStore.saveFrom(Uri/File) |
| desk_widget_image.img | WidgetImageStore.java:22、:31 | 图片组件源图 | WidgetImageStore.saveFrom |
| icon_overrides/ | IconOverrideStore.java:23、:46 | 每个组件一个 PNG，文件名为组件扁平化后替换 `/`、`\` 为 `_`，后缀 .png（:36-41、:70） | IconOverrideStore.save，PNG 质量 100（:92-120） |
| weread_covers/ | WereadStore.java:29、:79 | 微信读书封面图，文件名由 bookId 净化得到（:219-227） | WereadClient.ensureCover → download |
| shelf_collections.json | ShelfCollectionStore.java:29、:35 | 书架合集，JSON，版本 1 | ShelfCollectionStore 各写方法 |
| shelf_index.json | ShelfIndex.java:35、:313 | 书架索引 | ShelfIndex |
| koreader/ | KoreaderReader.java:27 | KOReader 数据目录扫描根 | 只读 |
| cacheDir/koreader_stats | DeskWidgetsController.java:2488 | KOReader 统计数据库副本 | KoreaderStats |
| cacheDir/theme_pack_apply | ThemePack.java:254 | 主题包解压临时目录 | ThemePack.apply |
| cacheDir/updates | UpdateClient.java:42、:100-104 | 下载的安装包，命名 tomo-launcher-<versionName>.apk | UpdateClient.download |

图像解码统一使用 `Bitmap.Config.RGB_565`、`inScaled = false`，并按 2 的幂次 `inSampleSize` 降采样（`data/WallpaperStore.java:44-54`、`data/WidgetImageStore.java:40-62`）；`ImagePickerActivity` 的预览链路另用 `inDensity = 0`、`inTargetDensity = 0` 避免系统按密度缩放。这些取舍共同指向 480×800 墨水屏的显存与灰度约束。

### 3.4 写入方式与并发

`ThemePack.java:132` 明确使用 `edit()...apply()` 异步提交；`ShelfCollectionStore` 用 `FilesKt.writeText` 直接覆盖写入（:138-160），没有临时文件与重命名步骤。长耗时网络与文件操作挂在 `TomoWorkers.io` 上：单线程守护线程池，线程名 `tomo-io`，优先级 4（`data/TomoWorkers.java:19-31`）。单线程串行意味着多个网络请求会互相排队，天气、更新、微信读书不会并发占用带宽。

---

## 四、数据模型

### 4.1 DeskWidget

`DeskWidget` 是 13 值枚举，构造参数为 `(key, titleRes, descriptionRes)`（`data/DeskWidget.java:29-41`）：

| 序号 | 枚举 | 键 | 标题资源 |
| --- | --- | --- | --- |
| 0 | READING | reading | widget_pick_reading |
| 1 | WEATHER | weather | widget_pick_weather |
| 2 | CLOCK | clock | widget_pick_clock |
| 3 | CLOCK_COMPACT | clock_compact | widget_pick_clock_compact |
| 4 | CALENDAR | calendar | widget_pick_calendar |
| 5 | COVERS | covers | widget_pick_covers |
| 6 | WEREAD_COVERS | weread_covers | widget_pick_weread_covers |
| 7 | WEREAD_MONTH | weread_month | widget_pick_weread_month |
| 8 | WEREAD_MARK | weread_mark | widget_pick_weread_mark |
| 9 | KOREADER_MONTH | koreader_month | widget_pick_koreader_month |
| 10 | USAGE | usage | widget_pick_usage |
| 11 | IMAGE | image | widget_pick_image |
| 12 | PLACEHOLDER | placeholder | widget_pick_placeholder |

`getCompact()` 仅对 CLOCK_COMPACT 返回真（:113-115）。`fromKey` 未命中回落 CLOCK，并且把 IMAGE 强制替换为 PLACEHOLDER（:53-71）——IMAGE 组件不通过字面持久化恢复，这与「图片组件由专门文件承载」的设计一致。`next()` 在可选项集合中循环（:131-138）。

持久化格式极为简单：`SharedPreferences("tomo_desk_widget")` 的 `widget` 键存单个键字符串，默认 `clock`（`data/DeskWidgetStore.java:22-30`）。不存在 JSON、不存在多组件列表、不存在位置坐标。桌面同一时刻只有一个小组件，位置由布局常量决定。

组件分组由 `DeskWidgetCategory` 给出（`data/DeskWidgetCategory.java:22-26`）：TIME（READING、CLOCK、CLOCK_COMPACT、CALENDAR）、LOCAL（COVERS、KOREADER_MONTH）、WEREAD（WEREAD_COVERS、WEREAD_MONTH、WEREAD_MARK）、MORE（USAGE、WEATHER）、PLACEHOLDER（PLACEHOLDER）。

### 4.2 应用列表、排序与分页

`AppRepository.load()`（`data/AppRepository.java:98-186`）分五步：

第一步，`queryIntentActivities(MAIN + LAUNCHER)` 取全部可启动组件（:104-107）。
第二步，排除本包，再用 `HiddenAppsStore.key(componentName)` 过滤隐藏项（:121-124）。
第三步，按三级比较器排序（:142-171）：

```java
compareByDescending { it.firstInstallTime }   // 最近安装的在前
  .thenBy { it.label.lowercase(Locale.ROOT) }  // 同安装时刻按名称
  .thenBy { it.packageName }                   // 再按包名
```

首次安装时间倒序意味着新装应用出现在最前面，而不是字母序，这是与多数桌面不同的取向。
第四步，把三个内建入口插到表头：`KoreaderApps.pinOnDesktop`、`FilesApp.pinOnDesktop`（受 `tomo_files` 开关控制）、`BookshelfApp.pinOnDesktop`（:173）。
第五步，`AppOrderStore.apply(...)` 覆盖前四步的顺序（:183）。也就是说，用户手动排序的优先级高于系统排序，未出现在 `order` 键里的应用按前述规则排在后面。

`AppOrderStore`（`data/AppOrderStore.java`）使用单一字符串键 `order`，以 `"\n"` 分隔组件扁平化字符串（`ComponentName.flattenToString()`，:89-92）。`saved()` 切分并过滤空串（:126-139）。`arrange`（:42-87）按保存顺序逐项从可变副本中取，先按完整组件串匹配，失败再按包名部分匹配（`packageOf(token) = token.substringBefore('/')`），未命中项忽略，剩余项追加尾部，因此卸载重装或类名变化不会破坏顺序，而只会把该项退到末尾。`moveToSlot`（:105-124）先移除目标项，`coerceIn(slot, 0, size)` 后插入，回写整个字符串再重排。

分页规则集中在 `AppPaging`（`data/AppPaging.java`），常量在 :15-22：

| 常量 | 值 | 含义 |
| --- | --- | --- |
| FIRST_PAGE_COMPACT | 9 | 紧凑组件的首页容量 |
| FIRST_PAGE_SIZE | 6 | 有组件时的首页容量 |
| LIST_FIRST_COMPACT | 8 | 列表布局紧凑组件首页容量 |
| LIST_FIRST_WITH_WIDGET | 5 | 列表布局有组件首页容量 |
| LIST_PAGE_SIZE | 10 | 列表布局每页容量 |
| MAX_PAGES | 15 | 页数上限 |
| PAGE_SIZE | 12 | 网格布局每页容量 |

六个 `Spec` 在 :79-88 定义：GRID_WIDGET = (6, 12)、GRID_WIDGET_COMPACT = (9, 12)、GRID = (12, 12)、LIST_WIDGET = (5, 10)、LIST_WIDGET_COMPACT = (8, 10)、LIST = (10, 10)；`spec(kind, widget, compact)` 在 :240-250 选择。`pageCount`（:216-222）在总数不超过首页容量时为 1，否则 `min(ceil((total - first) / pageSize) + 1, 15)`；`pageOf`（:224-233）首页取 `0..first`，其后按 `pageSize` 切片；`pageSize(0)` 返回首页容量，其余返回常态容量（:235-238）；`clampPage` 为 `coerceIn(0, pageCount - 1)`（:211-214）。15 页上限意味着最多展示约 180 个应用。

### 4.3 书架与合集

`ShelfBook`（`data/ShelfBook.java:11-15`）字段为 `file: File`、`title: String`、`percent: Float?`、`cover: File?`、`modified: Long`；`percent` 可空表示尚无阅读进度，`cover` 可空表示封面尚未生成。

`ShelfCollection`（`data/ShelfCollection.java:11-13`）字段为 `id`、`name`、`paths: List<String>`；`ShelfItem` 是 sealed 类，只有 `Book(ShelfBook)` 与 `Collection(ShelfCollection, List<ShelfBook>)` 两个子类（`data/ShelfItem.java:18-19`、:67-69），合集封面由内部书籍派生。

持久化只有合集一份文件，格式为带版本号的 JSON（`data/ShelfCollectionStore.java:29-30`、:138-160）：

```json
{ "version": 1, "collections": [ { "id": "...", "name": "...", "paths": ["..."] } ] }
```

`paths` 存书籍文件的绝对或相对位置字符串，读取时不做存在性校验（读取逻辑见 :44-62）。`nextUntitled(prefix, exceptId)`（:272-297）用于生成「新建合集 2」这类去重名称，`resolveName`（:321-327）在重名时追加序号。

书架布局只有两种：`ShelfKind` 的 GRID 与 LIST（`data/ShelfKind.java:22-23`），存于 `tomo_shelf/kind`，默认 grid（`data/ShelfLayoutStore.java:21-34`）。

### 4.4 微信读书

`WereadStore`（406 行，源文件 `WereadSkill.kt`）是最大的一处缓存层。键与语义（`data/WereadStore.java:29-44`）：

| 键 | 语义 | 过期策略 |
| --- | --- | --- |
| api_key | 授权 Key，写入前剥离 `Bearer ` 前缀并 trim（:59-62） | 不过期 |
| shelf_json / shelf_at | 书架快照与时间戳 | `MAX_AGE_MS = 1800000` 即 30 分钟（:43） |
| mark_json / mark_at | 最新划线 | 30 分钟 |
| month_json / month_at | 月度统计 | 30 分钟 |
| month_prev_seconds / month_prev_ok | 上月总时长与成功标志 | 不过期 |
| progress_json | bookId 到进度的映射 | 不过期 |

`cached(jsonKey, atKey, maxAge, now, parse)`（:82-104）先判空、再判 `now - at > maxAge`、最后解析；`stale*` 系列用 `Long.MAX_VALUE` 作为容忍窗口，等价于永不过期（:368-400）。

数据类：`WereadBook`（bookId、title、author、coverUrl、deepLink、readUpdateTime、progress）、`WereadMark`（bookId、title、author、markText、createTime、deepLink）、`WereadMonth`（totalSeconds、year、month、daySeconds: Int[]、lastMonthSeconds: Integer）、`WereadDuration`（小时与分钟）。`WereadMonth.maxDaySeconds()`（:122-126）供热力格归一化。

缓存序列化为紧凑 JSON：`compactMark`（`data/WereadClient.java:130-132`）逐字段写 `bookId/title/author/markText/createTime/deepLink`；`compactShelf`（:137-143）写 `{"books":[...]}`。月度统计直接保存接口原始响应字符串，不重新组装。

### 4.5 开机密码的凭据存储

`LockStore`（`data/LockStore.java`）的机制需要具体说明，因为它决定了离线破解成本。

```java
// data/LockStore.java:26-30
PREFS = "tomo_lock"; KEY_HASH = "pin_hash"; KEY_SALT = "pin_salt";
LENGTH = 4; SALT_BYTES = 16;
// :43-54
digest = sha256(salt || pin.getBytes(UTF_8)) 的 64 位小写十六进制
```

`normalize` 只保留数字并截断到 4 位（:72-86），`setPin` 在归一化后长度不等于 4 时抛 `IllegalArgumentException("pin")`，盐取自 `SecureRandom` 的 16 字节，最终把 `hex(salt)` 与摘要分别写入两个键（:149-159）。`matches` 重算摘要后与存储值比较（:137-147）；`hasPin` 要求两个键均非空（:133-135）。

要点有三：

1. 口令定长为 4 位数字，取值空间 10000，属于可穷举量级。
2. 只用了一轮 SHA-256，没有 PBKDF2、scrypt、Argon2 等迭代型 KDF，也没有迭代次数参数。
3. 加盐只阻断彩虹表与跨设备比对，对单设备穷举没有实质抬升；10000 次单轮 SHA-256 在普通设备上是毫秒级。

解锁态本身不落盘。`LockSession`（`data/LockSession.java:14-63`）是进程内单例，`unlocked` 为 volatile 布尔，`needsLock(store) = store.hasPin() && !unlocked`（:41-44），监听者用 `CopyOnWriteArrayList` 维护。进程重启或桌面被系统回收后重新上锁，属于「每次冷启动验证一次」的模型，而非系统级锁屏替代品。

### 4.6 主题包 .ttp 格式

`ThemePack`（源文件 `ThemePack.kt`，404 行）不是内置主题表，而是主题包的打包与还原逻辑。常量见 :38-51：

| 常量 | 值 |
| --- | --- |
| EXTENSION | ttp |
| FORMAT | tomo-theme-pack |
| VERSION | 1 |
| MANIFEST | manifest.json |
| WALLPAPER_NAME | wallpaper.img |
| ICONS_DIR | icons/ |
| ID_BOOKSHELF | tomo.bookshelf |
| ID_FILES | tomo.files |
| MAX_ENTRIES | 128 |
| MAX_ENTRY_BYTES | 8388608 |
| MAX_PACK_BYTES | 20971520 |

`manifest.json` 字段为 `format`、`version`、`name`、`wash`、`icons`（slug 到 `icons/<slug>.png` 的映射）、`wallpaper`（:332-342）。`themeId(app)`（:400-403）把书架映射为 `tomo.bookshelf`、文件管理映射为 `tomo.files`，其余用包名，因此内建入口也能被主题包替换图标。

应用流程 `apply`（:251-301）顺序为：校验扩展名与位置、体积（0 到 20 MiB）→ 解压到 `cacheDir/theme_pack_apply` → 校验 `format` 字段 → 清空现有图标覆盖与壁纸 → 写入壁纸并设置 wash → 按 manifest 逐项匹配当前应用写入图标覆盖 → `bump()` 刷新 `applied_at`。清空在前意味着应用主题包是破坏性替换，而不是叠加。`safeEntry`（:379-398）只放行 `manifest.json`、`wallpaper.img`、`icons/` 下一层的 `.png`，拒绝绝对位置与 `..`，因此不存在目录穿越。

导出命名为「图墨主题-」加 `yyyyMMdd-HHmmss` 时间戳加 `.ttp`（:135-137）。主题包的目录前缀由 `FileLibrary.THEME_DIR = "Theme"` 给出（`data/FileLibrary.java:38`），`apply` 还要求文件位于该目录内才接受。

---

## 五、主题体系

### 5.1 Theme.TomoLauncher

`res/values/styles.xml` 中只有一份真正的 Activity 主题 `Theme.TomoLauncher`，parent 为 `@android:style/Theme.DeviceDefault.Light.NoActionBar`，全部 item 如下：

| item | 值 | 作用 |
| --- | --- | --- |
| android:colorBackground | @color/white | 白底 |
| android:textColorPrimary | @color/black | 黑字 |
| android:textColor | @color/black | 黑字 |
| android:windowBackground | @color/white | 窗口白底，抑制壁纸透出 |
| android:windowContentOverlay | @null | 去掉系统内容遮罩 |
| android:windowAnimationStyle | @style/Animation.TomoLauncher.None | 关闭页面切换动画 |
| android:windowFullscreen | true | 全屏 |
| android:windowDisablePreview | true | 不显示启动预览窗 |
| android:windowShowWallpaper | false | 不显示系统壁纸 |
| android:selectableItemBackground | @android:color/transparent | 去掉点按高亮 |
| android:windowEnableSplitTouch | false | 关闭分屏触摸 |
| android:windowContentTransitions | false | 关闭内容转场 |
| android:colorControlHighlight | @android:color/transparent | 去掉控件高亮 |
| android:selectableItemBackgroundBorderless | @android:color/transparent | 去掉无边界点按高亮 |
| android:windowActivityTransitions | false | 关闭 Activity 转场 |

配色只有黑白两值，`res/values/colors.xml` 全文 10 项，其中应用自用四项：`black #000000`、`white #ffffff`、`wallpaper_wash #80ffffff`（壁纸洗白用的半透明白）、`weread_cover_empty #d9d9d9`（封面占位灰）。其余为 androidx 通知兼容色。

因此「主题字段」在资源层并不存在配色组、圆角组、线宽组。圆角与线宽是硬编码常量，落在图标绘制类里（`data/IconPack.java:22-27` 的 `CORNER_RADIUS_PX = 22`、`FRAME_PX = 94`、`INNER_LINE_PX = 3`、`SIZE_PX = 88`、`STROKE_PX = 3`；`data/IconCache.java:99-106` 用 94×94 位图与 22 像素圆角重绘）。界面尺寸则是 `res/values/dimens.xml` 中的 px 常量，例如 `calendar_cell 34px`、`clock_compact_size 72px`、`coverflow_center_width 170px`。

另有三套继承样式：`Theme.TomoLauncher.Dialog`（全透明、无 dim）、`Theme.TomoLauncher.WidgetLibrary`（`windowIsFloating=false`、`windowIsTranslucent=true`、`windowFullscreen=true`）、`Animation.TomoLauncher.None`（14 个动画项全部 `@null`）。

### 5.2 图标包

`IconPack`（`data/IconPack.java`）以位图资源的方式提供成套替换图标，位于 assets 的 `icons/` 目录，命名规则 `bm_<slug>_press.bmp` 与 `bm_<slug>_normal.bmp`（:99-105）。此处存在一处源码拼写问题：`wifitransfer` 的非按下状态返回 `bm_wifitransfer_nromal.bmp`（`normal` 拼成 `nromal`），资源侧必须按错拼命名才能生效。

`IconPackEntry`（`data/IconPackEntry.java:11-13`）字段为 `slug`、`packages: Set<String>`、`labels: Set<String>`。`match(packageName, label)`（:116-166）先按小写包名精确或前缀 `<pkg>.` 匹配，再按归一化标签匹配；`normalize` 保留字母数字与 CJK 区（19968 ≤ c < 40960）（:168-183）；标签包含匹配要求别名长度不小于 4（:88-97）。`extraPackages`（:35）为部分应用补充真实包名，例如 `einkbro → info.plateaukao.einkbro.fdroid`、`koreader → org.koreader.launcher.fdroid`、`kaiyuan_reader → io.legado.app.release`。

条目清单共 37 项（slug 与首选包名）：anki、apple_music、appcenter、audible、baicizhan、baidu_reader、bookshelf、bubei_words、changpei、dedao、duokan_reader、duolinguo、einkbro、fanshu、filemanagement、goodreads、jdreader、jinjiang_reader、kaiyuan_reader、kindle、kobo、libby、moon_reader、qidian_reader、qimao、qqreader、rednote、reeden、settings、wangyiyun_reader、weread、wifitransfer、xiaoyuzhou、ximalaya、yuexingtong、zhangyue、zhuishu。其中 `bookshelf` 与 `yuexingtong` 的首选包名为 null，只靠标签匹配。

用户自定义图标走另一套机制：`IconOverrideStore` 把替换图以 PNG 存在 `filesDir/icon_overrides/`，文件名由组件扁平化字符串替换斜杠得到（`data/IconOverrideStore.java:36-41`、:70），`IconCache` 维护 48 项的 LRU（`data/IconCache.java:34`）并在载入时套用 94 像素画框。

### 5.3 字体

两处可换字体，机制相同、来源不同。

时钟字体 `ClockFace`（`data/ClockFace.java:25-31`）共 7 值，均内置于 assets 的 `fonts/` 目录：

| 键 | 标题资源 | 资源位置 |
| --- | --- | --- |
| system | clock_face_system | 无，使用系统字体 |
| lexend | clock_face_lexend | fonts/lexend_medium_digits.ttf |
| bitter | clock_face_bitter | fonts/bitter_medium_digits.ttf |
| literata | clock_face_literata | fonts/literata_medium_digits.ttf |
| space_grotesk | clock_face_space_grotesk | fonts/space_grotesk_bold_digits.ttf |
| fredoka_one | clock_face_fredoka_one | fonts/fredoka_one.ttf |
| oxanium | clock_face_oxanium | fonts/oxanium_semibold.ttf |

默认值为 literata（`data/ClockFaceStore.java:38-40`），`typeface` 从 assets 加载并以 `HashMap` 缓存，失败回落 `Typeface.DEFAULT_BOLD`（:60-88）。

应用名称字体 `LabelFontStore` 读用户放置的 TTF：`currentPath()` 校验 `File.isFile()` 与 `FileLibrary.isLoadableFont(name)`，无效时移除键（:71-93）；`typeface()` 无自定义时使用 `Companion.defaultFace()`，SDK ≥ 28 用 `Typeface.create(DEFAULT, 700, false)`，否则 `DEFAULT_BOLD`（:154-182）。字体搜索根由 `FileLibrary.FONTS_DIR = "Fonts"` 给出。

### 5.4 「深色主题」的真实含义

`DarkThemeUnlockActivity` 的名称容易误读。读代码后可以确认它与应用配色无关：

```java
// DarkThemeUnlockActivity.java:16-17
COUNTDOWN_SECONDS = 5; TICK_MS = 1000;
// :65
jump() 内实际动作 = AccessibilityGate.INSTANCE.openSettings(this)
```

页面 `R.layout.activity_dark_theme_unlock` 上做 5 秒倒计时，文案 `dark_theme_unlock_soon`「即将跳转 (%1$ds)」，倒计时结束直接打开无障碍设置页并 `finish()`。配套文案还有 `dark_theme_unlock_title`「解除深色主题」、`dark_theme_unlock_confirm`「解除深色主题专用」、`dark_theme_unlock_hold`「将手指放在黑色区域内」、`dark_theme_unlock_tap`「在下个页面点击一次」。

据此可以判断：所谓解除深色主题，依赖的是先把无障碍服务打开，再由无障碍服务操作系统界面完成设置，而不是应用内部切换配色。应用自身没有任何 night 资源目录，也没有 `values-night`，`Theme.TomoLauncher` 只有浅色一套。相关入口位于 `SettingsActivity` 的 logo 三击（见 2.1），属于隐藏功能。

---

## 六、设备适配

### 6.1 屏幕判定

`SupportedScreen`（`data/SupportedScreen.java:13-16`）只有三个常量与两个方法：

```java
WIDTH = 480; HEIGHT = 800; SLACK = 2;
matches(w, h) = abs(min(w,h) - 480) <= 2 && abs(max(w,h) - 800) <= 2
```

判定只比较短边与长边，因此 480×800 与 800×480 都通过，横竖屏不需要额外分支。尺寸来源优先 `WindowManager.getDefaultDisplay().getRealMetrics()`，宽高非正时回退 `Resources.getDisplayMetrics()`（:94-109）。没有 DPI、密度、机型白名单、墨水屏特征探测。

判定为否时程序不降级而是退出：`LauncherActivity.java:1511-1515` 置 `screenBlocked = true`，调用 `DesktopDefaults.openOriginalDesktop(...)` 拉起原桌面后 `finish()`。也就是说 480×800 是硬性准入条件，非该分辨率的设备无法使用此桌面。

其他适配点：`overlay/OverlayHost.java:413` 用 `SupportedScreen.HEIGHT`（800）作为状态栏覆盖层高度；`AndroidManifest.xml` 中 `LauncherActivity` 声明 `configChanges` 覆盖 `density|screenSize|uiMode|screenLayout|orientation|keyboardHidden`，旋转与密度变化不重建 Activity。

### 6.2 原桌面回退

`DesktopDefaults`（`data/DesktopDefaults.java:15-17`）针对一款特定原桌面：

```java
PACKAGE  = "com.wetao.elauncher";
ACTIVITY = "com.wetao.elauncher.ui.main.MainActivity";
```

`launchIntent()`（:46-50）构造 `MAIN + HOME + DEFAULT` 并 `setClassName(PACKAGE, ACTIVITY)`。`openOriginalDesktop`（:52-64）三级回退：显式组件 → `getLaunchIntentForPackage(PACKAGE)` → `MAIN + HOME + setPackage(PACKAGE)`。`start(...)`（:22-34）先 `addFlags(0x10000000)`，再用 `resolveActivity` 判断可解析后才 `startActivity`。

`AndroidManifest.xml` 的 `queries` 中同时声明了 `com.wetao.emreader` 与 `com.wetao.elauncher`，说明这套回退是为文石类设备与既有阅读器生态准备的。

### 6.3 资源限定符

`res/` 下的限定符目录分两类。

语言类约 100 个：`values-af` 到 `values-zu`，含 `values-zh-rCN`、`values-zh-rHK`、`values-zh-rTW`，另有 `values-b+sr+Latn`（BCP47 形式）与 `values-en-rXC` 等地区变体。但 `values-zh-rCN/strings.xml` 仅 647 字节，内容只有 8 条 androidx 通知兼容文案（接听、视频通话、拒接、挂断等），说明简体中文的角色是补齐依赖库默认文案，应用自身中文文案直接写在默认 `values/strings.xml` 里，切换系统语言不会改变应用界面语言。

密度类：`values-ldpi`、`values-mdpi`、`values-hdpi`、`values-xhdpi`、`values-xxhdpi`、`values-xxxhdpi`、`values-anydpi`、`values-nodpi`，各自只有一个 67 字节的 `drawables.xml`，内容为空 `<resources/>`，用途是抑制依赖库在不同密度下的资源回退，没有实际的尺寸覆盖。

`res/xml/` 只有两个文件：`file_paths.xml` 与 `tomo_accessibility_service.xml`，无 Preference 屏幕定义，无备份规则，无网络配置。`res/values/arrays.xml` 只有一个 `weekdays` 数组，7 项从 `weekday_sun` 到 `weekday_sat`，周日在前。

### 6.4 尺寸单位

`res/values/dimens.xml` 共 17278 字节，绝大多数条目直接用 `px` 而非 `dp` 或 `sp`，例如 `arrange_list_target_height 52px`、`calendar_cell 34px`、`calendar_text 20px`、`coverflow_center_height 256px`。这个做法在 480×800 固定分辨率下成立，也正是 `SupportedScreen` 敢于做硬性分辨率判定、而不做等比缩放的技术前提。少数通用间距用 dp，例如 `cell_padding 6dp`、`TomoTrayTile` 的 6dp 外边距与 18sp 字号。

`DesktopDefaults` 与 `DesktopKind` 本身不随屏幕变化，屏幕差异只通过 `SupportedScreen.matches` 的准入判断体现；布局差异由 `DesktopKind`、`widgetEnabled`、`compactWidget` 三者组合成 `AppPaging.Spec`（`data/DesktopLayoutStore.java:61-63`），与屏幕尺寸无关。

---

## 七、网络面

### 7.1 TomoHttp 请求封装

`data/TomoHttp.java` 是唯一的通用 HTTP 封装，四类入口：`get`、`getBytes`、`post`、`request`，实现基于 `HttpURLConnection`（:59-67、:103-119）。

| 项 | 值 | 行号 |
| --- | --- | --- |
| 连接超时 | 15000 ms | :26 |
| 读取超时 | 15000 ms | :26 |
| User-Agent | TomoLauncher/0.4.2 | :27 |
| 跟随重定向 | true | — |
| GET 请求头 | Accept: */* | — |
| POST 请求头 | Content-Type: application/json; charset=utf-8 | — |
| POST 长度 | setFixedLengthStreamingMode | — |
| 状态码处理 | 非 200 到 299 抛 HttpException(code, body) | :74-76、:152-154 |

没有重试、没有缓存、没有 Cookie 管理、没有连接池复用、没有证书固定。`HttpException`（`data/HttpException.java`）只携带状态码与响应体，调用方按码分支。

版本字符串不一致值得记录：`TomoHttp` 报 `TomoLauncher/0.4.2`，而 `UpdateClient.java:47` 报 `TomoLauncher/1.0`，两者都写在 0.5.0 的包里，说明更新链路与常规网络链路是分别演进的两套代码，没有统一 UA 常量。

### 7.2 全部端点

| 用途 | URL | 定义位置 |
| --- | --- | --- |
| 更新清单 | https://astraabove.com/tomo/update.json | UpdateFeed.java:19 |
| 更新日志 | https://astraabove.com/usetomo/changelog.json | UpdateFeed.java:16 |
| 更新包基址 | https://astraabove.com/tomo/ | UpdateFeed.java:20、:66 |
| 天气 | https://api.open-meteo.com/v1/forecast?latitude=<lat>&longitude=<lon>&current=temperature_2m,weather_code,relative_humidity_2m,apparent_temperature&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=7&timezone=auto | WeatherClient.java:80 |
| IP 归属地 1 | https://ipwho.is/ | WeatherClient.java:24 |
| IP 归属地 2 | https://ipapi.co/json/ | WeatherClient.java:24 |
| IP 归属地 3 | https://ip.sb/geoip | WeatherClient.java:24 |
| 微信读书网关 | https://i.weread.qq.com/api/agent/gateway | WereadClient.java:38 |

另有若干文本内链接不出现在代码常量中，只在字符串资源里：隐私政策与用户协议正文包含 `support@astraabove.com` 与 `astraabove.com/usetomo`；更新提示 `update_manual_hint` 指向 `astraabove.com/usetomo`；微信读书引导 `pref_weread_hint` 指向 `weread.qq.com/r/weread-skills`。

### 7.3 更新流程

`data/UpdateFeed.java` 定义受信域与命名约束：

```java
APK_PREFIX = "tomo-launcher-"; APK_SUFFIX = ".apk";
HOST = "astraabove.com"; PATH = "/tomo/";
```

`acceptsApkUrl`（:25-52）要求 scheme 为 https、host 大小写不敏感等于 `astraabove.com`、path 以 `/tomo/` 开头、末段以 `tomo-launcher-` 开头且以 `.apk` 结尾且长度大于 18。`apkUrl`（:59-67）对相对文件名补全为 `https://astraabove.com/tomo/<文件名>`。

`UpdateClient` 负责取清单、比对版本、下载与校验：

| 项 | 值 | 行号 |
| --- | --- | --- |
| 清单超时 | 8000 ms | :46 |
| 下载超时 | 60000 ms | :43 |
| 下载 UA | TomoLauncher/1.0 | :47 |
| 文件过期状态 | 410 视为 Missing | :44 |
| 下载目录 | cacheDir/updates | :42、:100-104 |
| 下载缓冲 | 8192 字节 | — |
| 完整性校验 | SHA-256，大小写不敏感，失败删除文件 | :241-246 |

`check`（:106-146）把结果归为 `Current`、`Available`、`Failed` 三类，`isInstallable`（:83-85）要求 `versionCode > 0`、`versionName` 与 `sha256` 非空、apk URL 通过域名校验。`installedCode` 失败回落 1，`installedName` 失败回落 "1.0"（:311-313、:336-338）。`get`（:273-297）把 404 与 410 归为 Missing，把非 200 归为 Error，把不以 `{` 或 `[` 开头的响应体也归为 Error。

`UpdateRunner`（`data/UpdateRunner.java`）是界面侧状态机，状态文案用 `update_checking`、`update_latest`、`update_found`、`update_failed`、`update_downloading`、`update_download_failed`、`update_need_permission`、`update_need_network`、`update_install_need_permission`。安装（:129-146）在 SDK ≥ 26 且 `canRequestPackageInstalls()` 为假时，先跳 `android.settings.MANAGE_UNKNOWN_APP_SOURCES`，再通过 `FileProvider` 以 `application/vnd.android.package-archive` 打开安装意图。

`ChangelogClient`（`data/ChangelogClient.java`）从 `changelog.json` 取值：`bodyForVersion`（:29-35）只接受 `UpdateFetch.Body` 且响应体形如 JSON（:59-65）；`parse`（:93-138）解析数组，每项 `{version, items[]}`，版本号与条目均非空才收录；`normalizeVersion`（:88-91）去掉前缀 V/v。首启引导的「本次更新内容」即由此而来。

### 7.4 天气

`WeatherStore` 缓存 30 分钟（`data/WeatherStore.java:19`），四个键一次写入（:54-57），经纬度以字符串保存（:38-52）。`snapshot` 在 `at <= 0` 或超过最大时效时返回 null（:59-78），调用方据此决定是否联网。

`WeatherClient` 的定位策略是 IP 归属地而非 GPS：`lookupIp`（:83-103）按 `ipwho.is`、`ipapi.co`、`ip.sb` 的顺序尝试，全部失败抛 `IllegalStateException("ip geo failed")`。`parseIp`（:132-144）兼容 `latitude|lat` 与 `longitude|lon|lng` 两套字段名，排除 (0,0) 与越界值。经纬度写入 URL 前格式化 `%.4f`（:76-79），约 11 米精度，与 IP 定位的误差量级相称。

`AndroidManifest.xml` 同时申请 `ACCESS_COARSE_LOCATION` 与 `ACCESS_FINE_LOCATION`，但天气链路并未使用系统定位，权限申请与实现不匹配，属于遗留或预留。

### 7.5 微信读书

`WereadClient`（43809 字节）通过单一网关转发全部业务请求，鉴权在 HTTP 头：

```java
// data/WereadClient.java:599-603
post(apiKey, body) => TomoHttp.post(GATEWAY, body, {"Authorization": "Bearer " + apiKey})
```

请求体由 `requestBody`（:751-767）生成，固定两个字段加上业务参数：

```json
{ "api_name": "<接口名>", "skill_version": "1.0.5", ...extras }
```

`SKILL_VERSION = "1.0.5"`（:42）。五个接口名与参数：

| 接口名 | 参数 | 用途 | 行号 |
| --- | --- | --- | --- |
| /shelf/sync | 无 | 书架同步 | :706 |
| /book/getprogress | bookId | 单本进度 | :713 |
| /readdata/detail | mode=monthly，可选 baseTime | 月度阅读统计 | :665、:672 |
| /user/notebooks | count=10 | 笔记本列表 | :632 |
| /book/bookmarklist | bookId | 划线列表 | :639 |

`pullAll`（:613-626）串行执行书架、月度、划线三次拉取。`pullMonth`（:659-699）先取当月，再用 `previousMonthAt`（上月 15 日，:605-611）的秒级时间戳取上月，并通过 `monthMatchesRequested`（:357-367）校验响应确实对应请求月份，不匹配抛 `IllegalStateException("last month mismatch")`。`pullShelf`（:701-738）取最近阅读的 3 本（`RECENT = 3`，:41），逐本拉进度并合并，失败时保留接口返回的进度值；最后写 `shelf_json` 与 `progress_json`。

响应解析统一走 `payload`（:570-597）：若存在 `errcode` 或 `code` 且非 0，按错误类型抛出 `HttpException`——`-2012` 与 `401` 原样保留 401，错误消息含「授权」或（大小写不敏感）「key」时也归 401，其余归 400。这条规则让界面上能区分「Key 失效」与「其他失败」，`api_key` 失效后重新填写即可，不需要登出流程。

封面下载走 `download`（:189-241），先写临时文件，字节数为 0 时删除，成功后改名覆盖，失败回退 `FilesKt.copyTo`，最后删除临时文件。

鉴权凭据以明文存放在 `SharedPreferences("tomo_weread").api_key`，没有加密，也没有使用 `EncryptedSharedPreferences`。写入前只做 `Bearer ` 前缀剥离与 trim（`data/WereadStore.java:59-62`），`normalizeKey` 不校验格式。

---

## 八、权限与能力降级

### 8.1 权限模型

`TomoPermission` 的 8 个枚举值覆盖三类权限：运行时权限（NETWORK、STORAGE、LOCATION、BLUETOOTH、NOTIFICATIONS）、特殊访问（WRITE_SETTINGS、USAGE、ACCESSIBILITY）。分派逻辑在 `data/TomoPermissions.java`，两类请求码：

| 常量 | 值 | 行号 |
| --- | --- | --- |
| REQUEST_RUNTIME | 3101 | :24 |

`granted(context, permission)`（:219-242）按类型分派到各自探测函数：

| 权限 | 探测方式 |
| --- | --- |
| NETWORK | `NetworkAccess.hasPermission`，即 INTERNET 是否已授予 |
| STORAGE | `checkSelfPermission(readPermission()) == 0`，或 SDK ≥ 30 且 `Environment.isExternalStorageManager()` |
| LOCATION | `DeviceLocation.hasPermission` |
| BLUETOOTH | `BluetoothStatus.hasConnectPermission` |
| WRITE_SETTINGS | `Settings.System.canWrite` |
| USAGE | `AppUsage.hasAccess` |
| NOTIFICATIONS | `NotificationGate.isEnabled` |
| ACCESSIBILITY | `AccessibilityGate.isEnabled` |

`readPermission()`（:172-174）按系统版本切换：SDK ≥ 33 用 `android.permission.READ_MEDIA_IMAGES`，否则 `READ_EXTERNAL_STORAGE`。

`grant(activity, permission)`（:185-217）同样分派：NETWORK 调 `NetworkAccess.request(activity, 3101)`，失败回落应用详情页；STORAGE 在 SDK ≥ 30 且未持有 `MANAGE_EXTERNAL_STORAGE` 时跳 `android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`，失败再试 `MANAGE_ALL_FILES_ACCESS_PERMISSION`（:98-124）；BLUETOOTH 在 SDK ≥ 31 申请 `android.permission.BLUETOOTH_CONNECT`；其余四类跳各自的系统设置页。`revoke`（:244-274）按类型跳应用详情或对应特殊设置页。

### 8.2 降级点

| 功能 | 依赖权限 | 缺失时的表现 | 依据 |
| --- | --- | --- | --- |
| 壁纸选择、文件管理、字体加载、主题包保存 | STORAGE | 先弹权限申请，返回后继续原动作；`saveThemePack` 用 `pendingSaveTheme` 记住意图 | LauncherActivity.java:770-774、772；FileManagerActivity.java:1008、1425；ImagePickerActivity.java:313；LabelFontPickerActivity.java:354、447 |
| 天气组件 | LOCATION | 显示 `weather_need_location`「点按授权定位以获取天气」，点击发起授权 | LauncherActivity.java:1880、2126 |
| 天气数据失败 | 网络 | 显示 `weather_unavailable`「暂时无法获取天气」 | DeskWidgetsController.java:2163、2184 |
| 使用统计组件 | USAGE | 点击申请 `PACKAGE_USAGE_STATS` | LauncherActivity.java:1919 |
| 下拉栏蓝牙开关 | BLUETOOTH（SDK ≥ 31） | 显示 `tray_bluetooth_disconnected`；点击申请 | QuickTrayController.java:335、565、715；LauncherActivity.java:2105 |
| 下拉栏通知同步 | NOTIFICATIONS | `NotificationGate.isEnabled` 为假时点击申请 | QuickTrayController.java:293、363、509 |
| 图墨状态栏覆盖层 | ACCESSIBILITY | 覆盖层开关为开但服务未启用时显示引导行，点击申请 | QuickTrayController.java:397、503 |
| 亮度与系统开关 | WRITE_SETTINGS | 点击申请 `Settings.System.canWrite` | LauncherActivity.java:2147、OverlayHost.java:363 |
| 检查更新 | NETWORK | 显示 `update_need_network` | UpdateRunner.java:83、174 |
| 安装更新包 | REQUEST_INSTALL_PACKAGES | 显示 `update_install_need_permission` 并跳未知来源设置 | UpdateRunner.java:129-136 |
| 微信读书 | NETWORK + api_key | 无 Key 时右侧显示「未填写」；请求失败按 401/400 区分提示 | WereadStore.java:197-198、WereadClient.java:570-597 |

### 8.3 能力探测

`NetworkAccess`（`data/NetworkAccess.java`）是网络能力的唯一判定点：`hasPermission` 检查 INTERNET（:28-31），`isOnline` 用 `ConnectivityManager.getActiveNetwork` 加 `NET_CAPABILITY_INTERNET`（值 12）（:33-43），`request` 同时申请 INTERNET 与 ACCESS_NETWORK_STATE（:45-48），请求码 3201（:16）。

无障碍能力由 `AccessibilityGate.isEnabled` 与 `TomoAccessibilityService.isActive()` 双判（`ui/QuickTrayController.java:397` 同时使用），前者看设置中是否启用，后者看服务是否真的连上，两者可分离，因此覆盖层会额外检查 `isActive()`。

`res/xml/tomo_accessibility_service.xml` 声明的能力为：`accessibilityEventTypes=typeWindowStateChanged`、`feedbackType=feedbackGeneric`、`notificationTimeout=200`、`accessibilityFlags=flagRetrieveInteractiveWindows|flagRequestFilterKeyEvents`、`canRetrieveWindowContent=true`、`canRequestFilterKeyEvents=true`、`settingsActivity=com.astraabove.tomo.LauncherActivity`。可读窗口内容与可拦截按键事件这两项，与下拉栏读取通知、音量键注入的实现相对应（`overlay/VolumeKeyInjector.java`、`notify/NotificationGate.java`）。

---

## 九、未确认点

1. `ShelfIndex`（`data/ShelfIndex.java`，23721 字节）只确认了文件名为 `shelf_index.json` 与位置常量行号，索引的 JSON 结构、失效判定、与 `BookshelfLibrary` 的分工未逐行核对。
2. 书籍封面与书架的扫描规则未展开：`EpubCover`（23209 字节）、`CoverCrop`（18484 字节）、`FileLibrary`（44683 字节）的封面生成与缓存位置（`FileLibrary.COVER_DIR = "cover"`）只确认常量，未确认落盘目录与命名。
3. `KoreaderStats` 在副本上执行查询，未确认是否存在写回 KOReader 原库的分支；也未确认副本的刷新触发条件与失效策略。
4. 各 Store 的写回使用 `apply()` 还是 `commit()` 未逐个核对，仅 `ThemePack.java:132` 明确为 `apply()`。
5. `values-*` 的约 100 个语言目录中，各语言的翻译完成度未核对；`values-zh-rCN` 只覆盖 8 条依赖库文案这一点已确认，其他语言是否同样只覆盖部分条目未确认。
6. `DeskWidgetsController`（188629 字节）中各小组件的绘制、刷新与交互细节未展开，仅核对了天气降级文案的行号。
7. `WidgetLibraryController`（42272 字节）的长按组件选择器交互、`widget_tip` 提示的触发条件与 `tomo_desktop/widget_tip` 的读写位置未核对。
8. `dialog_clock_face` 的布局文件与行高、预览方式未核对，只确认了选择逻辑与写键位置。
9. `ThemePack` 导出时 zip 的压缩级别、条目顺序与图标写入的字节格式未核对。
10. `UpdateManifest.notes` 字段的展示位置未确认；`data/UpdateManifest.java` 只确认了解析字段名。
11. `AndroidManifest.xml` 申请了 `ACCESS_COARSE_LOCATION` 与 `ACCESS_FINE_LOCATION`，天气链路实际使用 IP 归属地；系统定位是否被 `DeviceLocation` 的其他功能使用，未完整核对。
12. `RecentRecordStore.cleared_at` 仅确认了键与默认值，`RECENT_LIMIT = 5`（`FileLibrary.java:37`）与最近文件列表的过滤逻辑未核对。
13. `data/AppUsage.java`（26594 字节）的使用统计聚合方式、`AppUsageRow` 的字段语义未展开。
14. `SystemSettings` 中 `findSubpage` 的候选打分在部分定制 ROM 上能否命中「电源管理」「系统设置」子页，属于运行时行为，静态分析无法确认。
