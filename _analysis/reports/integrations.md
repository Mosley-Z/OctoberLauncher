# 图墨桌面 0.5.0 外部集成与系统服务分析

## 分析对象与方法

分析对象为 `tomo-launcher-0.5.0.apk`，包名 `com.astraabove.tomo`，versionCode 13，versionName 0.5.0，minSdk 24，targetSdk 34（`AndroidManifest.xml:1-12`）。源码取自 JADX 反编译结果，Java 文件根目录为 `D:\DSHStation\OctoberLauncher\_analysis\jadx-out\sources\com\astraabove\tomo\`，资源根目录为 `D:\DSHStation\OctoberLauncher\_analysis\jadx-out\resources\res\`。文中行号均指该反编译源码或清单文件中的行号；清单中权限、组件、Intent action 与包名按原文照录。

反编译结果中少数方法未被完整还原，涉及处已在正文标注，并在末节「未确认点」集中列出。本次分析为只读，未修改任何被分析文件。

### 清单中的系统集成组件

| 组件 | 类型与关键属性 | 位置 |
| --- | --- | --- |
| `TomoNotificationService` | Service，`android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`，exported=true，action `android.service.notification.NotificationListenerService` | `AndroidManifest.xml:220-228` |
| `TomoAccessibilityService` | Service，`android.permission.BIND_ACCESSIBILITY_SERVICE`，exported=true，meta-data `android.accessibilityservice=@xml/tomo_accessibility_service` | `AndroidManifest.xml:229-240` |
| `SettingsActivity` | Activity，exported=true，intent-filter `android.settings.SETTINGS`，可被系统设置入口调用 | `AndroidManifest.xml:95-107` |
| `FileProvider` | `androidx.core.content.FileProvider`，authorities `com.astraabove.tomo.files`，exported=false，grantUriPermissions=true，paths `@xml/file_paths` | `AndroidManifest.xml:211-219` |

`PackageChangeReceiver` 未在清单中声明，只在 `LauncherActivity` 前台运行时动态注册（`LauncherActivity.java:2505-2511`、`LauncherActivity.java:2552-2557`）。

---

## 一、通知托盘

### 1.1 通知监听的接入

`TomoNotificationService` 继承 `NotificationListenerService`（`notify/TomoNotificationService.java:25`），由系统绑定，权限声明见 `AndroidManifest.xml:220-228`。服务重写了 `onListenerConnected`、`onListenerDisconnected`、`onDestroy`，其中 `onListenerConnected` 把静态 `instance` 指向自身（`notify/TomoNotificationService.java:132`），断开与销毁时清空。服务未重写 `onNotificationPosted` 与 `onNotificationRemoved`，托盘在打开时通过 `getActiveNotifications()` 拉取当前挂起通知，不维护实时事件流。

是否已授权由 `NotificationGate.isEnabled` 判断：读取 `Settings.Secure` 的 `"enabled_notification_listeners"` 字符串，按 `:` 分割，与 `new ComponentName(context, TomoNotificationService.class).flattenToString()` 做忽略大小写比对（`notify/NotificationGate.java:29-41`）。未授权时 `openSettings` 跳转 `android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` 并附加 `FLAG_ACTIVITY_NEW_TASK`（`notify/NotificationGate.java:50`）。

### 1.2 过滤与排序规则

单条通知的转换在 `toNotice` 中完成（`notify/TomoNotificationService.java:42-69`）：

- 取 `notification.extras` 的 `EXTRA_TITLE` 与 `EXTRA_TEXT` 并做 `trim`，两者都为空则丢弃该通知；未读取 `EXTRA_BIG_TEXT`、`EXTRA_SUB_TEXT`、`EXTRA_TEXT_LINES` 等其他字段。
- 标题为空而正文非空时，把正文提升为标题、正文留空。
- 键值优先用 `StatusBarNotification.getKey()`，为空时用 `packageName + ":" + id`。
- 保存 `title`、`text` 与 `notification.contentIntent` 三项，封装为 `TrayNotice`（`notify/TrayNotice.java:12-25`）。

列表构建在 `current()` 中（`notify/TomoNotificationService.java:78-116`）：取 `getActiveNotifications()` 全量，按 `getPostTime()` 降序排序（`:91-97`），逐条转换并过滤空项，最后 `take(4)`（`:106`），上限常量 `MAX_NOTICES = 4`（`:29`）。过滤规则只有“标题与正文至少一个非空”，没有包名白名单、没有按会话或分组去重、没有重要性过滤；排序只有投递时间降序。`getActiveNotifications()` 的语义是当前仍挂起的通知，因此已被用户清除或应用撤回的通知不会出现在托盘。

### 1.3 托盘界面呈现与点击行为

托盘的布局文件为 `res/layout/panel_quick_tray.xml`，根节点 `quick_tray`（`:3`），自上而下包含：

| 区域 | 内容 | 位置 |
| --- | --- | --- |
| 顶部 | `tray_date` 日期，`tray_battery` 电量百分比 | `res/layout/panel_quick_tray.xml:24-40` |
| 网络卡片 | `tray_wifi` 标题与 `tray_wifi_detail`，显示 SSID 或断开状态 | `res/layout/panel_quick_tray.xml:52-75` |
| 蓝牙卡片 | `tray_bluetooth` 标题与 `tray_bluetooth_detail`，显示已连接设备名 | `res/layout/panel_quick_tray.xml:87-110` |
| 前光 | 冷光与暖光两组，各含减、`StepBar`、加（10 级） | `res/layout/panel_quick_tray.xml:142-238` |
| 通知 | `tray_notice_header`、`tray_notice_clear`、`tray_notice_list`、`tray_notice_empty`、`tray_notice_hint` | `res/layout/panel_quick_tray.xml:256-309` |
| 悬浮层提示 | `tray_overlay_hint`、`tray_overlay_hint_line` | `res/layout/panel_quick_tray.xml:322-334` |
| 底部 | `tray_close` 关闭把手 | `res/layout/panel_quick_tray.xml:340` |

通知行由 `item_notice.xml` 提供，`notice_title` 与 `notice_text` 两行文本（`res/layout/item_notice.xml:16-26`）。`QuickTrayController.bindNotice` 把标题、正文写入控件，正文为空时置 `GONE`，点击时调用 `notice.contentIntent.send()`，发送异常被吞掉（`ui/QuickTrayController.java:232-271`）。因此点击通知的行为完全取决于原通知的 `PendingIntent`，图墨不自行解析通知内容，也不启动对应应用。

清空按钮的行为由授权状态分流：未授权时点击 `tray_notice_clear` 改为发起通知读取权限申请；已授权时调用 `TomoNotificationService.cancelAll()`，即系统通知栏的全部通知被撤销（`ui/QuickTrayController.java:292-299`、`notify/TomoNotificationService.java:71-76`）。这一操作影响系统通知栏，不只是托盘显示。

托盘数据刷新由 `refresh()` 统一完成，先刷新日期，再刷新电量、WiFi、蓝牙、前光、悬浮层提示与通知（`ui/QuickTrayController.java:724-742`）。托盘在打开时注册网络与蓝牙状态广播接收器（`ui/QuickTrayController.java:708-722`、`:427-440`），关闭时反注册（`:657-663`）。

### 1.4 权限缺失时的降级

`refreshNotices` 在 `NotificationGate.isEnabled` 为假时显示 `tray_notice_hint`，清空列表并隐藏标题与空态（`ui/QuickTrayController.java:359-394`）。点击该提示会调用 `TomoPermissions.grant(NOTIFICATIONS)`，跳转通知监听授权页（`ui/QuickTrayController.java:756-919`、`TomoPermissions.java:138-154`）。由于服务未连接时 `current()` 返回空列表，托盘在未授权状态下只显示提示，不显示任何通知条目（`notify/TomoNotificationService.java:78-116`）。

---

## 二、无障碍悬浮层

### 2.1 服务配置与监听事件

无障碍服务声明在 `AndroidManifest.xml:229-240`，配置文件为 `res/xml/tomo_accessibility_service.xml`：

- `accessibilityEventTypes="typeWindowStateChanged"`（`:5`）
- `accessibilityFeedbackType="feedbackGeneric"`（`:6`）
- `notificationTimeout="200"`（`:7`）
- `accessibilityFlags="flagRetrieveInteractiveWindows|flagRequestFilterKeyEvents"`（`:8`）
- `canRetrieveWindowContent="true"`（`:9`）
- `canRequestFilterKeyEvents="true"`（`:10`）
- `settingsActivity=com.astraabove.tomo.LauncherActivity`（`:4`）

运行时在 `onServiceConnected` 中再次把 `getServiceInfo().flags` 置上 `32`，即 `FLAG_REQUEST_FILTER_KEY_EVENTS`，随后 `setServiceInfo`（`overlay/TomoAccessibilityService.java:297-325`）。服务实际只处理 `eventType == 32`（`TYPE_WINDOW_STATE_CHANGED`），忽略自身包名的事件，记录外部包名后以 200ms 去抖调用 `applyOverlayVisibility`（`overlay/TomoAccessibilityService.java:213-222`）。

`resolveForegroundPackage` 通过 `getWindows()` 取 `type == TYPE_APPLICATION` 且 `isActive` 的窗口根节点包名，失败时回退到缓存的外部包名（`overlay/TomoAccessibilityService.java:179-210`）。服务未调用节点文本读取接口，`canRetrieveWindowContent` 的用途是取得窗口与包名信息。

### 2.2 OverlayHost 的窗口层级与内容

`OverlayHost` 在无障碍服务连接时创建（`overlay/TomoAccessibilityService.java:297-325`），内部持有 `WindowManager`、`ContextThemeWrapper` 与主线程 `Handler`（`overlay/OverlayHost.java:61-69`）。

窗口参数统一由 `overlayParams(height, flags)` 构造：类型 `2032`，即 `TYPE_ACCESSIBILITY_OVERLAY`；格式 `-3`，即 `PixelFormat.TRANSLUCENT`；`gravity = TOP`（`48`）；`x = y = 0`；标题 `"TomoOverlay"`；Android 9 及以上设置 `layoutInDisplayCutoutMode = 1`（`overlay/OverlayHost.java:396-406`）。

三类窗口的参数如下：

| 窗口 | 尺寸 | flags | 位置 |
| --- | --- | --- | --- |
| 状态栏 | 宽 `MATCH_PARENT`，高 `R.dimen.status_bar_height` | `808`，即 `FLAG_NOT_FOCUSABLE` + `FLAG_NOT_TOUCH_MODAL` + `FLAG_LAYOUT_IN_SCREEN` + `FLAG_LAYOUT_NO_LIMITS` | `overlay/OverlayHost.java:392-410` |
| 下拉托盘 | 全屏 `-1 × SupportedScreen.HEIGHT` | 继承 `overlayParams` 的 `-3` 格式与默认 flags | `overlay/OverlayHost.java:412-414` |
| PIN 面板 | 全屏 `-1 × -1` | `768`，即 `FLAG_NOT_FOCUSABLE` + `FLAG_LAYOUT_IN_SCREEN` + `FLAG_LAYOUT_NO_LIMITS` | `overlay/OverlayHost.java:388-390` |

状态栏窗口位于屏幕顶部、占据系统状态栏的同一区域，作为无障碍悬浮窗绘制在普通应用窗口之上；图墨不修改系统状态栏本身的可见性，桌面内则改用应用内的 `include_status_bar` 布局（`res/layout/include_status_bar.xml:1-89`）并把系统 UI 隐藏（各 Activity 统一调用 `setSystemUiVisibility(5894)`，例如 `BookshelfActivity.java:731-733`、`WereadSkillActivity.java:167-169`）。悬浮窗是否在具体 ROM 上压过系统状态栏，属于运行期层级与厂商策略问题，本次未做实机验证，列入未确认点。

状态栏悬浮层的内容来自 `res/layout/overlay_status_bar.xml`：刷新按钮 `overlay_refresh`（`:15-24`）、时间 `overlay_time`（`:25-35`）、占满剩余宽度的托盘触控区 `overlay_tray_hit`（`:36-42`）、WiFi 图标 `overlay_wifi`（`:43-51`）、电量文本 `overlay_battery`（`:52-61`）、充电图标 `overlay_charge`（`:62-71`）。`inflateStatus` 绑定这些控件并构造 `StatusBarController`，位图绘制关闭过滤（`overlay/OverlayHost.java:200-269`）。点击时间或刷新按钮触发 `flashScreen()`，点击托盘触控区或根布局下滑手势触发 `showTray()`（`overlay/OverlayHost.java:200-269`、`:289-386`）。

`flashScreen` 的全屏视图先以 `ViewCompat.MEASURED_STATE_MASK` 背景加入，500ms 后改为白色，再 500ms 后移除（`overlay/OverlayHost.java:446-474`、`:109-119`），重复调用时提前返回。这是墨水屏的局部刷新手段。

托盘悬浮窗由 `PanelQuickTrayBinding` 与 `QuickTrayController` 构成，创建时注入三个权限请求回调，分别对应蓝牙、位置与前光所需的 `WRITE_SETTINGS`（`overlay/OverlayHost.java:289-386`、`ui/QuickTrayController.java:130-185`）。PIN 面板悬浮窗由 `include_pin_pad`、`PinPadController` 与 `LockStore` 组成，匹配成功后解锁并收起；忘记密码走 `LockGate.forget`（`overlay/OverlayHost.java:136-198`）。

### 2.3 OverlayPolicy 的启用条件

`OverlayPolicy.shouldShow` 的判断顺序为：桌面可见时为假；前台包名等于自身包名时为假；其他情况返回“前台包名不是 KOReader”（`overlay/OverlayPolicy.java:24-32`）。`tomoChrome` 在前台为空或等于自身包名时为真（`:34-37`），用于决定状态栏文字与图标的配色。

`TomoAccessibilityService.applyOverlayVisibility` 组合了多个条件（`overlay/TomoAccessibilityService.java:135-171`）：

1. 宿主为空时返回。
2. `LockGate.needsLock` 为真时显示 PIN 面板并结束。
3. 桌面界面可见（`launcherUiVisible`）时隐藏托盘与状态栏并结束。
4. 否则用 `OverlayPolicy.tomoChrome` 计算是否使用图墨配色。
5. 如果 `DesktopLayoutStore.overlayEnabled()` 为假，隐藏托盘与状态栏并结束。
6. 如果托盘已打开，保持现状。
7. 如果 `OverlayPolicy.shouldShow` 为假，只隐藏状态栏；否则按配色显示状态栏。

`overlayEnabled` 默认关闭（`data/DesktopLayoutStore.java:57-59`），因此悬浮状态栏是一项需要用户显式开启的能力。`trayEnabled` 默认开启（`:94-96`），控制托盘本身是否可用。

### 2.4 VolumeKeyInjector 的实现

`onKeyEvent` 是无障碍按键过滤的入口（`overlay/TomoAccessibilityService.java:260-281`）：锁屏面板存在时只吞掉返回键 `KEYCODE_BACK`（4）；若正在注入音量键、按键不是音量加或音量减、或前台包名不满足 `VolumeKeys.shouldInvert`，则放行；否则置 `injectingVolume` 防递归，调用 `VolumeKeyInjector.invert` 并返回结果。

`VolumeKeys.opposite` 只映射 `24` 与 `25`，即 `KEYCODE_VOLUME_UP` 与 `KEYCODE_VOLUME_DOWN`（`data/VolumeKeys.java:47-52`）。`shouldInvert` 要求前台包名非空、不等于自身，且存在于用户选定的包名集合中（`data/VolumeKeys.java:54-61`）。包名集合由 `VolumeKeyStore` 保存，缺省值为 `VolumeKeys.DEFAULT_READERS`（`data/VolumeKeyStore.java:16-28`），默认清单为 `KoreaderApps.PACKAGES`、`com.wetao.emreader` 以及图标包中匹配阅读器标识的包（`data/VolumeKeys.java:15-28`）。

`VolumeKeyInjector` 的实现分为两步：

- `inject` 通过 `context.getSystemService("input")` 取得输入服务，遍历 `getMethods()` 寻找名为 `injectInputEvent` 且参数个数为 2 的方法，`setAccessible(true)` 后 `invoke(systemService, event, 0)`，返回布尔结果（`overlay/VolumeKeyInjector.java:45-91`，方法名为 `:73`，调用为 `:84`）。确实使用了 `injectInputEvent`，且通过反射隐藏 API 完成。
- `copy$app_release` 复制按键事件：来源为空时置 `SOURCE_KEYBOARD`（257），事件时间为 0 时用 `SystemClock.uptimeMillis()`，设备号置 0（`overlay/VolumeKeyInjector.java:93-102`）。

`invert` 先注入对调后的按键；注入失败且动作为按下时，降级为 `adjustSuggestedStreamVolume` 调整音量（`overlay/VolumeKeyInjector.java:104-117`、`:27-43`）。图墨未申请任何输入注入相关权限，能力来自无障碍服务的按键过滤标志与反射调用。

### 2.5 TomoForeground 的实质

`TomoForeground` 由进程内的深度计数器实现，不涉及前台服务、通知或通知渠道：`enter()` 加一，`leave()` 减一并夹到零，`reset$app_release()` 清零（`overlay/TomoForeground.java:12-30`）。每次变化都转调 `TomoAccessibilityService.setLauncherVisible`，让无障碍服务在桌面界面或其子页面处于前台时隐藏悬浮窗（`overlay/TomoAccessibilityService.java:283-294`）。调用点位于 `TomoChrome.onStart` 与 `onStop`（`ui/TomoChrome.java:97-101`），因此该机制覆盖桌面内多个页面，不代表 Android 前台服务语义。

---

## 三、前光能力

### 3.1 通道与系统设置键

前光由两个 `Settings.System` 键控制：`screen_brightness` 与 `screen_brightness1`（`data/FrontLight.java:36-48`）。前者对应冷光，后者对应暖光，快捷托盘中的两组加减与 `StepBar` 分别绑定到两个键（`ui/QuickTrayController.java:130-185`、`:225-230`）。等级从 `-1` 到 `9` 共十档，`-1` 表示关闭；`valueForLevel` 把档位映射为 `((level + 1) * 255) / 10`，`levelForValue` 反向取最接近的档位（`data/FrontLight.java:113-118`、`:134-157`、`:229-234`）。

### 3.2 写入方式与降级

`write` 对两个厂商键采取“先内容提供者、后系统设置”的两级写入（`data/FrontLight.java:236-277`）：

- 两个键属于 `VENDOR_KEYS` 集合（`data/FrontLight.java:49`），先通过 `ContentResolver.update` 写入 `content://com.astraabove.tomo.frontlight/<key>`，值放在 `ContentValues` 的 `"value"` 字段（`data/FrontLight.java:236-246`、`:260-264`）。
- 提供者写入失败时退回 `Settings.System.putInt` 并 `notifyChange`（`data/FrontLight.java:248-277`）。

为避免跳变，亮度写入采用渐变：`writeRamped` 取消同键旧任务，先写首帧，之后每 `20ms` 写一帧，总时长约 `320ms`，步长为 `ceil(abs/16)` 且至少为 1（`data/FrontLight.java:42-45`、`:159-177`、`:279-345`）。任一通道大于 0 时同时把 `brightness_button_status` 置 1，两个通道都归零时置 0（`ui/QuickTrayController.java:152-155`、`:181-183`）。

### 3.3 自定义签名权限与辅助应用

清单声明了 `com.astraabove.tomo.permission.WRITE_FRONT_LIGHT`，保护级别为 `signature`（`AndroidManifest.xml:39-41`）。图墨自身的 Java 代码未引用该权限字符串，报告中无法给出图墨侧的权限校验点。与之配套的是包名 `com.astraabove.tomo.frontlight` 的外部辅助应用（`data/FrontLight.java:38-39`），其内容提供者接收上述两个键的写入。按 Android 权限模型推断，该签名权限的用途是让同签名的辅助应用获得写前光通道的授权，或由辅助应用在提供者侧校验调用方；由于辅助应用本身未纳入本次分析范围，这一判断列入未确认点。

图墨通过 `getPackageInfo("com.astraabove.tomo.frontlight", 0)` 判断辅助应用是否安装（`data/FrontLight.java:120-132`），并通过 `android.settings.action.MANAGE_WRITE_SETTINGS` 加 `package:com.astraabove.tomo.frontlight` 引导用户给辅助应用授予修改系统设置权限（`data/FrontLight.java:197-213`）。

### 3.4 权限缺失时的行为

缺少 `WRITE_SETTINGS` 时，`QuickTrayController.applyChannel` 不放弃用户操作，而是暂存待应用等级并调用 `requestWriteSettings` 回调（`ui/QuickTrayController.java:130-185`）；写渐变失败且操作为暖光时再次请求 `WRITE_SETTINGS`（`:181-183`）。托盘刷新时若读不到两通道的等级，则按 `levelForValue` 解释当前值（`ui/QuickTrayController.java:344-357`）。未发现前光失败后跳转辅助应用界面的代码，只有跳转授权页的分支。

---

## 四、微信读书集成

### 4.1 数据来源

微信读书数据全部来自官方 Agent HTTP 接口，不使用无障碍抓取，也不读取 `com.tencent.weread.eink` 的本地数据库或文件。网关为 `https://i.weread.qq.com/api/agent/gateway`（`data/WereadClient.java:38`），请求方法 `post` 走 `TomoHttp.post(GATEWAY, body, header)`，请求头为 `Authorization: Bearer <API key>`（`data/WereadClient.java:599-603`）。请求体由 `requestBody` 生成，包含 `api_name`、`skill_version`（固定 `1.0.5`）与业务参数（`data/WereadClient.java:751-767`、`:42`）。响应中 `errcode` 或 `code` 非 0 时抛出异常，`-2012` 与 `401` 映射为 401，其余映射为 400（`data/WereadClient.java:570-597`）。

使用的接口与用途如下：

| 接口 | 用途 | 位置 |
| --- | --- | --- |
| `/shelf/sync` | 书架，含电子书与有声书专辑 | `data/WereadClient.java:706`、`:506-568` |
| `/book/getprogress` | 单本阅读进度 | `data/WereadClient.java:713`、`:110-113` |
| `/readdata/detail`，`mode: "monthly"` | 本月阅读统计，第二次请求取上月 | `data/WereadClient.java:665`、`:672`、`:659-699` |
| `/user/notebooks`，`count: 10` | 有笔记的书目 | `data/WereadClient.java:632`、`:628-657` |
| `/book/bookmarklist` | 单本划线，取最新一条 | `data/WereadClient.java:639`、`:369-417` |

### 4.2 可获得的字段

书架解析 `parseShelf` 同时处理 `books` 与 `albums`（`data/WereadClient.java:506-568`）：

- 电子书：`bookId`、`title`、`author`、`cover`、`deepLink`、`readUpdateTime`、`progress`。
- 有声书专辑：`albumId` 或 `bookId`、`name` 或 `title`、`authorName` 或 `author`、`cover`、`deepLink`、`lectureReadUpdateTime`、`progress`。

阅读进度优先取 `readingProgress.progress`，其次顶层 `progress`，限制在 0 到 100（`data/WereadClient.java:110-113`）。月度统计解析每日阅读秒数，兼容毫秒与秒两种单位，汇总本月与上月总时长（`data/WereadClient.java:455-466`、`:47-69`），并提供日均、热力等级、环比文案等展示函数（`data/WereadClient.java:148-355`）。划线取 `bookId`、`title`、`author`、`markText`、`createTime`、`deepLink`，要求 `markText` 非空且 `createTime` 最大（`data/WereadClient.java:369-417`）。

封面由 `download` 下载，先写 `.tmp` 再改名；`ensureCover` 把封面缓存到 `filesDir/weread_covers/<bookId>.img`（`data/WereadClient.java:189-268`；`data/WereadStore.java:219-225`）。

### 4.3 本地缓存

缓存位于 `SharedPreferences("tomo_weread")`（`data/WereadStore.java:44`、`:78`），键包括 `api_key`、`mark_json` 与 `mark_at`、`month_json` 与 `month_at`、`month_prev_seconds` 与 `month_prev_ok`、`progress_json`、`shelf_json` 与 `shelf_at`（`data/WereadStore.java:33-42`）。缓存有效期 30 分钟（`:43`）。`apiKey` 读取时会去掉 `Bearer ` 前缀（`:59-62`、`:197-200`）。

### 4.4 技能页与引导流程

`WereadSkillActivity` 是设置项内的独立页面，布局为 `activity_weread_skill.xml`，只包含一段说明、一个 API key 输入框（`weread_key`，`inputType=textVisiblePassword`）与一个应用按钮（`weread_apply`），没有 OAuth、扫码或 WebView（`res/layout/activity_weread_skill.xml`；`WereadSkillActivity.java:217-330`）。

流程为：输入 key 后点击应用，先持久化输入（`WereadSkillActivity.java:193-210`）；key 为空提示 `pref_weread_apply_empty`（`:68-77`）；否则置应用中状态、禁用按钮并显示 `pref_weread_applying`（`:78-100`）；后台线程执行 `WereadClient.pullAll`（`:103-108`、`:124`）；主线程按代次校验后显示 `pref_weread_apply_ok` 或 `pref_weread_apply_fail`（`:139-165`）；`onPause` 再次持久化（`:334-343`）。页面隐藏系统栏（`:167-169`）。入口在桌面设置 `DesktopPrefsActivity:183` 与 `LauncherActivity:1850`。

打开某本书时，`WereadApps.open` 依次尝试 `weread://reading?bId=<bookId>`、原始 `deepLink`、从 `deepLink` 中提取 `bId`、`bookId`、`bid` 参数生成的自定义 scheme（`data/WereadApps.java:150-169`、`:80-104`），并通过 `startView` 以 `ACTION_VIEW`、可选 `CATEGORY_BROWSABLE` 启动；指定包名时组件为 `com.tencent.weread.LaunchExternalSchema`（`data/WereadApps.java:24`、`:46-78`、`:209-223`），全部失败后回退 `getLaunchIntentForPackage`（`:127-148`）。目标包为 `com.tencent.weread.eink` 与 `com.tencent.weread`，按顺序探测安装（`data/WereadApps.java:23`、`:110-125`）。

---

## 五、KOReader 集成

### 5.1 安装定位

`KoreaderApps` 定义包名 `org.koreader.launcher` 与 `org.koreader.launcher.fdroid`（`data/KoreaderApps.java:30-31`），主 Activity 为 `org.koreader.launcher.MainActivity`（`:28`）。`installedPackage` 先尝试 `officialPackage`，再按包名顺序探测（`:132-151`）；`officialPackage` 会跳过系统分区中的 stock lite 版本，判断依据是 `FLAG_UPDATED_SYSTEM_APP`、系统标志以及 `sourceDir` 是否以 `/system/`、`/product/`、`/vendor/`、`/odm/` 开头（`:157-167`、`:330-347`）。

打开书籍时，图墨用 `FileProvider` 为文件生成 `content://com.astraabove.tomo.files/...` URI，`Intent.ACTION_VIEW` 加上书籍 MIME 类型，并显式指定组件为 KOReader 主 Activity，附加读权限标志（`data/KoreaderApps.java:349-396`）。MIME 映射覆盖 epub、mobi、azw、azw3、pdf、txt、fb2、djvu、cbz、cbr（`:177-192`），无法识别时回退 `application/octet-stream`。如果 `FileProvider` 取 URI 失败，则退回 `Uri.fromFile`。

### 5.2 读取的目录与文件

KOReader 数据位于外部存储的 `koreader` 目录，候选位置包括 `Environment.getExternalStorageDirectory()/koreader`、`/sdcard/koreader`、`/storage/emulated/0/koreader`（`data/KoreaderReader.java:141-157`）。读取内容包括：

| 文件 | 解析内容 | 位置 |
| --- | --- | --- |
| `settings.reader.lua` | `lastfile`，最近阅读的书 | `data/KoreaderReader.java:31`、`:231-256` |
| `history.lua` | `file` 字段序列，用于补足最近书目 | `data/KoreaderReader.java:28`、`:65-80` |
| `<书名>.sdr/metadata.<ext>.lua` 或 `metadata.lua` | `title`、`doc_props.title`、`percent_finished` | `data/KoreaderReader.java:259-284`；`data/KoreaderLua.java:101-153` |
| `settings/statistics.sqlite3` | 阅读时长，表 `page_stat_data` | `data/KoreaderStats.java:20-22` |

Lua 文件由正则表达式解析，不引入 Lua 解释器（`data/KoreaderLua.java:60-153`）；读取有 64KiB 上限并按 8192 字节分块（`data/KoreaderReader.java:29-30`、`:350-384`）。统计数据库先复制到应用缓存目录 `koreader_statistics.sqlite3`，同时复制 `-wal` 与 `-shm` 文件，按文件长度与修改时间判断是否重新复制，再通过 `SELECT start_time, duration FROM page_stat_data WHERE start_time >= ? AND start_time < ?` 按日期累加每日秒数（`data/KoreaderStats.java:27-47`、`:57-82`、`:122-134`、`:148-172`）。本月与上月区间由月初时间戳计算（`:158-172`）。`queryMonth` 本身未被 JADX 还原（`:187-193`），月度快照的组装细节列入未确认点。

书目封面优先在 sidecar 目录查找 `cover.jpg`、`cover.jpeg`、`cover.png`、`cover.webp`、`cover.bmp`、`coverpage.*`，其次在书所在目录与附加目录查找同名图片；都找不到时调用 `EpubCover.extractTo` 从 EPUB 中提取（`data/KoreaderReader.java:34-35`、`:174-229`、`:159-172`）。

### 5.3 书架与进度

书架扫描 `Books` 目录及其 `book` 子目录（`data/FileLibrary.java:31-32`、`:200-205`），递归收集书籍文件（`data/FileLibrary.java:642-670`），逐本读取 sidecar 得到标题与百分比（`data/BookshelfLibrary.java:35-55`）。排序规则为：最近阅读的书优先、修改时间降序、标题小写升序（`data/BookshelfLibrary.java:148-253`）。索引缓存写在 `filesDir/shelf_index.json`，字段包含书籍与 sidecar 的修改时间和大小，命中时不重新解析（`data/ShelfIndex.java:35`、`:60-67`、`:237-242`、`:390-411`）。

---

## 六、文件管理与书架

### 6.1 FileManager 的浏览模型

`FileManagerActivity` 把文件管理分成“首页”和“浏览”两种状态（`FileManagerActivity.java:74-98`）。首页显示四张入口卡片与最近文件（`:1595-1676`）：

- `filesCardBooks`：打开 `Books` 目录，只显示书籍类型（`FileManagerActivity.java:2182-2200`）。
- `filesCardImages`：打开 `Pictures` 目录，只显示图片（`:2208-2226`）。
- `filesCardFonts`：打开 `Fonts` 目录，只显示字体（`:2234-2252`）。
- `filesCardAll`：打开外部存储根目录，不限制类型（`:2260-2278`）。
- All 卡片同时显示存储占用标签（`:172-183`）。
- 最近文件取 `Books`、`Pictures`、`Fonts` 三个目录中修改时间最新的 5 项，并排除用户“清空最近记录”之前的时间点（`:1640-1648`；`data/FileLibrary.java:766-826`；`data/RecentRecordStore.java:28-34`）。

进入目录后使用 `folderStack` 维护层级，标题、类型过滤与页码随之更新（`FileManagerActivity.java:1541-1594`、`:1832-1886`）。列表每页 9 项（`data/FileLibrary.java:33`、`:745-759`）；图片目录改用网格画廊与异步缩略图（`FileManagerActivity.java:185-227`）。

### 6.2 支持的操作

- 重命名：`promptRename` 校验 `FileLibrary.isValidFileName`，目标已存在时失败；成功时同步重命名同名 `.sdr` 目录（`FileManagerActivity.java:1293-1347`；`data/FileLibrary.java:828-844`）。
- 复制与移动：`copyInto` 与 `moveInto` 支持单文件与批量；复制到自身内部会被拒绝；目标重名时以 `name-2.ext` 形式去重；移动优先 `renameTo`，失败时复制后删除（`data/FileLibrary.java:207-312`、`:691-743`、`:857-887`）。
- 新建文件夹：`createFolder` 校验名称合法性（`data/FileLibrary.java:355-372`）。
- 删除：`deleteAll` 调用 `deleteRecursively`（`data/FileLibrary.java:374-386`），确认对话框按数量显示提示（`FileManagerActivity.java:682-731`）。
- 批量选择：`setBatch(true)` 后条目切换选中，底部出现批量操作条（`FileManagerActivity.java:1967-1983`）。
- 转移模式：复制或移动时进入“选择目标目录”状态，标题变为 `files_copy` 或 `files_move`，在目标目录确认后执行（`FileManagerActivity.java:1888-1955`、`:733-783`）。

文件条目点击行为依类型分流：目录入栈；图片进入预览；书籍交给 KOReader 打开，失败时再尝试通用 `ACTION_VIEW`；主题包走主题应用流程；其他文件按 MIME 用 `ACTION_VIEW` 打开（`FileManagerActivity.java:1065-1102`、`:1104-1148`、`:1178-1197`）。

### 6.3 存储权限与 FileProvider

存储访问分两层：Android 11 及以上检查 `Environment.isExternalStorageManager()`，否则要求 `READ_EXTERNAL_STORAGE`，Android 13 及以上改为 `READ_MEDIA_IMAGES`（`data/TomoPermissions.java:87-89`、`:172-174`；`FileManagerActivity.java:856-858`）。未授权时首页卡片计数归零并提示 `files_need_permission`（`FileManagerActivity.java:252-320`），授权条跳转 `android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`，失败再跳 `android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION`（`FileManagerActivity.java:1150-1176`）。

FileProvider 的 authorities 为 `com.astraabove.tomo.files`（`AndroidManifest.xml:211-219`），目录配置包含缓存目录 `updates/`、外部存储根 `.` 与根文件系统 `/`（`res/xml/file_paths.xml:3-11`）。用途有三处：把书籍 URI 授权给 KOReader（`data/KoreaderApps.java:349-396`）、把待安装 APK 交给系统安装器（`data/UpdateRunner.java:129-152`）、把任意文件交给外部应用打开（`FileManagerActivity.java:1178-1197`）。

### 6.4 书架导入与 EPUB 封面提取

书架没有单独的“导入”动作，扫描 `Books` 目录即可把放入的电子书纳入书架；`Books` 与 `Books/cover` 由 `ensureShortcutDirs` 自动创建（`data/FileLibrary.java:388-394`）。封面查找顺序为显式封面文件、同名图片、EPUB 内提取（`data/KoreaderReader.java:174-229`）。

`EpubCover` 的提取步骤（`data/EpubCover.java:37-42`、`:212-235`、`:423-432`）：

1. 以 `ZipFile` 打开 EPUB，读取 `META-INF/container.xml` 的 `rootfile` 的 `full-path`，得到 OPF 文件位置。
2. 读取 OPF 文本，按优先级确定封面项：`<meta name="cover" content="id">` 或 `<meta property="cover" content="id">` 指向的 `item`；`properties` 含 `cover-image` 的项；`id` 命中 `cover`、`cover-image`、`coverimage`、`cover_image` 的项；`href` 含 `cover` 的项。
3. 用 OPF 所在目录解析并规范化压缩包内位置，只接受图片扩展名，单张上限 4MiB，XML 上限 512KiB。
4. 输出到封面目录，文件名使用 `safeStem` 处理后的书名词干。

`extractTo` 本身未被 JADX 还原（`data/EpubCover.java:386-392`），以上步骤由同文件中已还原的辅助方法推断，列入未确认点。

---

## 七、应用列表与使用统计

### 7.1 应用枚举

`AppRepository.load` 使用 `PackageManager.queryIntentActivities`，查询条件为 `Intent.ACTION_MAIN` 加 `CATEGORY_LAUNCHER`，Android 13 及以上使用 `ResolveInfoFlags.of(0)`（`data/AppRepository.java:88-92`、`:104-107`）。过滤规则包括跳过自身包名（`:121`）与跳过用户隐藏的应用（`:124`，隐藏集合由 `HiddenAppsStore` 维护）。每项保留标签、包名、组件名、首次安装时间与启动 Intent（`:125-134`）。

排序依次为：首次安装时间降序、标签小写升序、包名（`data/AppRepository.java:142-171`）；随后插入三个特殊入口，分别是 KOReader、文件管理（仅当 `DesktopLayoutStore.tomoFilesEnabled` 为真）与书架（`:172-173`）；最后应用用户自定义顺序 `AppOrderStore.apply` 并缓存结果（`:183-185`）。`invalidate` 清空缓存（`:94-96`）。因此桌面应用列表的来源是 `PackageManager`，未使用 `LauncherApps`；使用 `QUERY_ALL_PACKAGES` 权限（`AndroidManifest.xml:13-38`）规避 Android 11 的包可见性限制。

### 7.2 图标加载与缓存

`IconCache` 使用 `accessOrder = true` 的 `LinkedHashMap` 维护最近使用的 48 个图标（`data/IconCache.java:34`、`:43`、`:49`）。加载顺序为：用户自定义图标覆盖、图标包匹配、系统 `getActivityIcon`（`:145-191`）。系统图标先缩放到图标尺寸，绘制为 `RGB_565` 位图，再套 94×94、圆角 22 的白色框，绘制时关闭抗锯齿、抖动与位图过滤（`:98-136`、`:176-191`）。缓存满时按最久未使用淘汰（`:213-219`），并提供预热保留与清理接口（`:221-277`）。

### 7.3 使用统计与最近记录

应用使用时长来自 `UsageStatsManager`，需要 `PACKAGE_USAGE_STATS` 权限（`AndroidManifest.xml:13-38`）。`AppUsage.hasAccess` 通过 `AppOpsManager` 检查 `"android:get_usage_stats"`，Android 10 及以上用 `unsafeCheckOpNoThrow`，否则用 `checkOpNoThrow`（`data/AppUsage.java:346-354`）。未授权时设置页跳转 `android.settings.USAGE_ACCESS_SETTINGS`（`:356-372`）。

`today` 先计算当天零点，再调用 `queryUsageStats(0, startOfDay, now)`，累加各包的 `getTotalTimeInForeground`（`data/AppUsage.java:420-464`），并额外用 `queryEvents` 统计书架页面自身的前台时长（`:280-302`、`:457-462`）。统计结果排除自身包名、旧包名 `com.tomo.launcher`、自身包名前缀与所有系统应用（`:304-315`），按总时长降序、标签升序取前 3 条（`:374-408`、`:42`）。格式化输出为“不到一分钟”“若干分钟”“若干小时”与“若干小时若干分钟”四种（`:317-344`）。

`RecentRecordStore` 只保存“最近文件”列表的清除时间，键为 `"cleared_at"`，存储于 `SharedPreferences("tomo_recent_files")`（`data/RecentRecordStore.java:12-13`、`:28-34`），不记录应用使用轨迹。桌面应用顺序由用户自定义顺序与安装时间决定，不按最近使用排序（`data/AppRepository.java:142-183`）。

### 7.4 包变更与卸载

`PackageChangeReceiver` 监听 `PACKAGE_ADDED`、`PACKAGE_REMOVED`、`PACKAGE_CHANGED`、`PACKAGE_REPLACED`，并附加 `addDataScheme("package")`；仅在 `Intent.getData()` 非空时回调（`receiver/PackageChangeReceiver.java:33-54`）。该接收器由 `LauncherActivity` 动态注册与反注册（`LauncherActivity.java:2156`、`:2505-2511`、`:2552-2557`），清单中无声明，因此只在启动器前台期间生效。

卸载应用使用 `Intent.ACTION_DELETE` 加 `package:` URI（`LauncherActivity.java:1307-1308`），对应清单中的 `REQUEST_DELETE_PACKAGES`；安装更新通过系统安装器完成（`data/UpdateRunner.java:129-152`），对应 `REQUEST_INSTALL_PACKAGES`。

---

## 八、设备能力

### 8.1 WiFi

WiFi 状态读取使用 `WifiManager`：`isWifiEnabled` 判断开关，`getConnectionInfo` 取 `SSID`、`RSSI` 与 `networkId`；`networkId != -1` 且 `RSSI > -127` 视为已连接；SSID 去掉引号与空白，拒绝空串、`<unknown ssid>` 与 `0x` 形式（`data/WifiStatus.java:92-134`）。读取 SSID 需要位置或附近设备权限：Android 13 及以上要求 `NEARBY_WIFI_DEVICES` 或 `ACCESS_FINE_LOCATION`，更低版本要求 `ACCESS_FINE_LOCATION` 或 `ACCESS_COARSE_LOCATION`（`data/WifiStatus.java:69-79`），权限数组由 `requiredPermissions` 返回（`:136-138`）。

信号强度按 3 档转换为状态栏图标（`data/StatusBarController.java:236-260`），托盘显示 SSID 或断开状态（`ui/QuickTrayController.java:402-425`）。开关使用 `WifiManager.setWifiEnabled`，异常或返回失败时打开系统 WiFi 设置（`data/WifiStatus.java:140-175`；`ui/QuickTrayController.java:607-640`）；Android 10 及以上优先打开 `android.settings.panel.action.WIFI` 面板（`ui/QuickTrayController.java:316-320`）。

### 8.2 蓝牙

蓝牙状态读取使用 `BluetoothManager.getAdapter`（`data/BluetoothStatus.java:24-30`）。已连接设备名通过 `getBondedDevices` 遍历已配对设备，并对每个设备反射调用 `isConnected` 判断，名称为空时回退 MAC 地址（`:40-105`、`:107-123`）。Android 12 及以上需要 `BLUETOOTH_CONNECT`，更低版本视为已授权（`:125-128`）；无权限时只返回“已启用”而不返回设备名（`:130-149`）。开关调用 `enable()` 或 `disable()`，失败时跳转 `android.settings.BLUETOOTH_SETTINGS`（`:151-182`；`ui/QuickTrayController.java:564-605`）。蓝牙状态只在托盘卡片显示（`ui/QuickTrayController.java:332-342`），状态栏不显示。

### 8.3 天气与定位

天气接口为 Open-Meteo，请求 `current=temperature_2m,weather_code,relative_humidity_2m,apparent_temperature` 与 `daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=7&timezone=auto`（`data/WeatherClient.java:75-81`）。无坐标时依次使用 `https://ipwho.is/`、`https://ipapi.co/json/`、`https://ip.sb/geoip` 做 IP 定位（`data/WeatherClient.java:24`、`:83-103`），解析结果拒绝 `(0, 0)` 与越界值（`:132-144`）。返回结构包含当前温度、体感温度、天气代码、湿度与 7 天预报（`data/WeatherClient.java:105-130`；`data/WeatherDay.java:8-19`；`data/WeatherSnapshot.java:10-26`）。

定位由 `DeviceLocation` 完成：优先使用 15 分钟内的最近位置，否则向各 provider 请求单次更新，超时 8 秒，超时后以最近位置兜底，无权限时返回空（`data/DeviceLocation.java:28-30`、`:150-192`、`:195-333`）。权限为粗略定位与精确定位（`:335-337`）。

天气快照缓存于 `SharedPreferences("tomo_weather")`，键为 `json`、`at`、`lat`、`lon`，有效期 30 分钟（`data/WeatherStore.java:15-20`、`:59-78`）。天气显示在桌面天气小组件，包含图标、温度、体感、湿度与 7 天预报（`ui/DeskWidgetsController.java:1603-1634`、`:2088-2157`）；刷新时先用缓存回填，再根据定位权限决定使用定位结果还是缓存坐标（`:2576-2629`）。

### 8.4 存储容量

可用空间用 `StatFs.getAvailableBytes`（`data/DeviceStorage.java:28-42`）。总容量优先取静态缓存，其次取 `StorageCapacityStore`，最后在 Android 8 及以上通过 `StorageStatsManager.getTotalBytes(StorageManager.UUID_DEFAULT)` 获取并持久化，更低版本退回 `StatFs.getTotalBytes`（`data/DeviceStorage.java:51-99`）。展示格式为“已用GB / 总GB”，保留一位小数，按 10 亿字节为 1GB 计算（`data/DeviceStorage.java:105-129`；`data/StorageUsage.java:13-17`）。持久化键为 `total_bytes`，位于 `SharedPreferences("tomo_device_storage")`（`data/StorageCapacityStore.java:12-13`、`:21-26`）。容量标签出现在文件管理器 All 卡片（`FileManagerActivity.java:172-183`）。

### 8.5 日历与时钟

月历由 `MonthCalendar` 生成 6 行 7 列共 42 格，以当月 1 日的星期偏移填充，非本月日期使用空 `CalendarCell`，当天标记 `today`（`data/MonthCalendar.java:14-17`、`:57-73`；`data/CalendarCell.java:9-16`）。桌面日历小组件据此绑定标题与 42 个格子（`ui/DeskWidgetsController.java:1258-1265`）。

时间格式化由 `ClockTime` 完成：24 小时制输出 `HH:mm`，12 小时制按本地化模板拼接上午与下午标记（`data/ClockTime.java:284-310`），中文为“上午/下午 + 时间”，英文为“时间 + AM/PM”（`:111-129`）。状态栏与托盘日期都复用该格式化（`ui/StatusBarController.java:184-234`；`ui/QuickTrayController.java:724-742`）。时钟字体由 `ClockFace` 枚举提供，包含系统字体与六个随包字体文件，缺省为 `LITERATA`（`data/ClockFace.java:25-58`）。时间变化通过广播 `TIME_TICK`、`TIME_SET`、`TIMEZONE_CHANGED`、`DATE_CHANGED` 与自定义 `com.astraabove.tomo.CLOCK_CHANGED` 驱动刷新（`ui/StatusBarController.java:296-302`、`data/ClockTime.java:19`）。

---

## 九、权限流程与首次启动

### 9.1 清单权限

清单共声明 20 项权限（`AndroidManifest.xml:13-38`）：

`QUERY_ALL_PACKAGES`、`READ_EXTERNAL_STORAGE`（maxSdk 32）、`READ_MEDIA_IMAGES`、`WRITE_EXTERNAL_STORAGE`（maxSdk 32）、`MANAGE_EXTERNAL_STORAGE`、`INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE`、`CHANGE_WIFI_STATE`、`ACCESS_COARSE_LOCATION`、`ACCESS_FINE_LOCATION`、`NEARBY_WIFI_DEVICES`、`REQUEST_DELETE_PACKAGES`、`REQUEST_INSTALL_PACKAGES`、`BLUETOOTH`、`BLUETOOTH_ADMIN`、`BLUETOOTH_CONNECT`（neverForLocation）、`WRITE_SETTINGS`、`PACKAGE_USAGE_STATS`、`com.astraabove.tomo.permission.WRITE_FRONT_LIGHT`。

其中 `com.astraabove.tomo.permission.WRITE_FRONT_LIGHT` 属于应用自定义的签名级权限（`AndroidManifest.xml:39-41`）。

### 9.2 首次启动不申请权限

首次启动由 `LauncherActivity.maybeStartOobe` 判断是否进入引导页，条件为引导未完成且未在本次启动中打开过（`LauncherActivity.java:433-447`），随后启动 `OobeActivity`。引导页只有介绍、布局选择、小组件选择与完成四步（`OobeActivity.java:139-250`、`:416`），没有权限申请，也不跳转系统授权页。权限改由设置页集中管理或由具体功能触发。

### 9.3 权限页与八项权限

权限管理页由 `DesktopPrefsActivity:202` 启动 `PermissionsActivity`。页面列出 `TomoPermission` 的八项：网络、存储、位置、蓝牙、修改系统设置、使用情况访问、通知读取、无障碍（`data/TomoPermission.java:25-32`）。每行显示标题、理由与状态文字 `perm_granted` 或 `perm_denied`（`PermissionsActivity.java:162-165`、`:97-100`）；点击行弹出授权或撤销对话框（`:105-146`）。

授权与撤销的映射集中在 `TomoPermissions`（`data/TomoPermissions.java:185-274`）：

| 权限 | 授权方式 | 撤销方式 |
| --- | --- | --- |
| 网络 | 运行时申请 `INTERNET` 与 `ACCESS_NETWORK_STATE` | 应用详情页 |
| 存储 | Android 11 及以上跳全文件访问页，否则运行时申请读权限 | 全文件访问页或应用详情页 |
| 位置 | 运行时申请粗略与精确定位 | 应用详情页 |
| 蓝牙 | Android 12 及以上运行时申请 `BLUETOOTH_CONNECT` | 应用详情页 |
| 修改系统设置 | 跳 `android.settings.action.MANAGE_WRITE_SETTINGS` 并带包名 | 同一页面 |
| 使用情况访问 | 跳 `android.settings.USAGE_ACCESS_SETTINGS` | 同一页面 |
| 通知读取 | 跳通知监听授权页 | 同一页面 |
| 无障碍 | 跳无障碍设置页 | 同一页面 |

运行时权限请求码统一为 `3101`（`data/TomoPermissions.java:24`），结果回到 `LauncherActivity.onRequestPermissionsResult` 处理（`LauncherActivity.java:2608`）。状态判断分别使用 `NetworkAccess.hasPermission`、`hasStorage`、`DeviceLocation.hasPermission`、`BluetoothStatus.hasConnectPermission`、`Settings.System.canWrite`、`AppUsage.hasAccess`、`NotificationGate.isEnabled`、`AccessibilityGate.isEnabled`（`data/TomoPermissions.java:219-242`）。

### 9.4 启用的功能依赖

按功能触发点归纳，各项权限与能力的对应关系如下：

| 权限 | 影响的功能 | 缺失时的表现 |
| --- | --- | --- |
| 存储 | 文件管理、书架扫描、封面缓存、壁纸与图标选择 | 首页卡片计数归零，提示申请权限（`FileManagerActivity.java:252-320`） |
| 无障碍 | 其他应用上方的图墨状态栏、托盘、音量键翻页、PIN 悬浮面板 | 悬浮层不出现，设置页提示开启（`ui/QuickTrayController.java:396-400`） |
| 修改系统设置 | 前光调节 | 暂存待应用等级并请求授权（`ui/QuickTrayController.java:130-185`） |
| 通知读取 | 托盘通知列表 | 显示授权提示，不显示通知（`ui/QuickTrayController.java:359-394`） |
| 使用情况访问 | 使用时长小组件 | 显示申请入口，统计为空（`ui/DeskWidgetsController.java:2520-2574`） |
| 位置 | 天气定位与 WiFi 名称 | 天气退回缓存坐标或 IP 定位，WiFi 名称隐藏（`ui/DeskWidgetsController.java:2576-2629`；`data/WifiStatus.java:92-134`） |
| 蓝牙 | 托盘蓝牙设备名与开关 | 只显示开关状态（`data/BluetoothStatus.java:130-149`） |
| 网络 | 天气、更新检查、微信读书同步 | 相关请求失败并显示对应提示（`data/WeatherClient.java:83-103`） |

图墨没有一次性申请全部权限的流程，也没有把权限申请阻塞在启动阶段；存储与无障碍影响主要功能，其余权限各自对应独立能力，缺失时分别降级。

---

## 未确认点

1. `KoreaderStats.queryMonth` 未被 JADX 还原（`data/KoreaderStats.java:187-193`），月度总时长、上月对比与日数组的组装细节由同文件已还原的辅助方法推断。
2. `EpubCover.extractTo` 未被 JADX 还原（`data/EpubCover.java:386-392`），封面输出文件命名、写入与失败处理由 `safeStem` 与上限常量推断。
3. `FileManagerActivity.startTransfer` 反编译结果为伪汇编（`FileManagerActivity.java:1888-1955`），转移模式的状态迁移与界面切换顺序由伪汇编与相邻方法推断。
4. 自定义签名权限 `com.astraabove.tomo.permission.WRITE_FRONT_LIGHT` 在图墨代码中无引用，校验发生在 `com.astraabove.tomo.frontlight` 辅助应用内；该 APK 未提供，无法确认提供者是否强制要求该权限，也无法确认签名匹配关系。
5. `VolumeKeyInjector` 通过反射调用 `injectInputEvent`（`overlay/VolumeKeyInjector.java:45-91`），不同 Android 版本对隐藏 API 的限制是否导致调用失败，未做实机验证；失败时仅在按下动作为真时降级为音量调节。
6. 通知服务未实现 `onNotificationPosted` 与 `onNotificationRemoved`，托盘只在打开时拉取 `getActiveNotifications()`；实时刷新行为与监听断开期间的通知补齐情况未验证。
7. `PackageChangeReceiver` 只在 `LauncherActivity` 前台动态注册（`LauncherActivity.java:2505-2511`），进程退出后包安装、卸载事件不会触发刷新。
8. 悬浮状态栏与系统状态栏的实际层级关系取决于窗口类型 `2032` 与厂商策略，未在实机验证是否压过系统状态栏。
9. 首次启动不申请权限，哪些权限被产品定义为“必需”缺少显式清单，正文的必需性判断依据功能触发点归纳，不代表官方声明。
10. 微信读书接口依赖用户自备 API key，`skill_version` 固定为 `1.0.5`（`data/WereadClient.java:42`），服务端字段变更与接口下线情况未验证。
11. 蓝牙 `isConnected` 通过反射调用（`data/BluetoothStatus.java:107-123`），Android 12 及以上设备上的实际返回值未验证。
12. 更新检查与下载逻辑不在本次范围，仅确认安装阶段使用 `FileProvider` 与未知来源设置页（`data/UpdateRunner.java:129-152`）。
13. `ShelfIndex` 的 `toBook` 方法未被 JADX 还原，书架快速回显时封面与修改时间的合并规则由字段与相邻方法推断（`data/ShelfIndex.java:249-286`）。




