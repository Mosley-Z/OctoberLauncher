# inkOS 与十雨桌面对照评估

评估对象为 GitHub 项目 gezimos/inkOS，阅读材料包括仓库 README、使用文档、模块列表与本地副本中的 44 个 Kotlin 源文件（副本由逐文件下载构成，部分被引用的界面文件缺失，文中标注未确认处均因此）。对照对象为十雨桌面当前代码，文件位置均相对仓库根目录。

## 一、inkOS 的定位与实现规模

inkOS 是一个面向墨水屏与实体键盘设备的桌面替代品，实测 Android 10 至 16，主要机型为 Mudita Kompakt、LightPhone 3、QIN F22、Cat S22 一类极简设备，包名 app.inkos，版本 0.6，minSdk 26、targetSdk 36，采用 GPL-3.0。代码以 Kotlin 与 Jetpack Compose 为主，仓库中 app 模块的 Kotlin 文件约 44 个（本地副本口径），体积偏大的几个文件是 MainActivity.kt 32KB、MainViewModel.kt 116KB、HomeUI.kt 224KB、AppsUI.kt 78KB、AppsFragment.kt 93KB、SettingsFragment.kt 163KB、EditModeHelper.kt 156KB，界面层为 Compose 与自定义 View 混用，导航为单 Activity 加手写页面栈。

其功能面覆盖桌面布局、底部组件、应用抽屉与检索、通知的三种呈现、最近任务、字体与主题、按键映射、墨水屏刷新与厂商面板模式、备份迁移。可复用到十雨桌面的部分集中在平台接口与几处交互设计，而非界面代码。

## 二、inkOS 关键功能的实现方式

### 墨水屏刷新与厂商面板模式

反色闪屏由 `helper\utils\EinkRefreshHelper.kt:19-41` 实现，在主线程向父容器添加一个铺满的 View，背景取主题反色（浅色主题用黑、深色主题用白），置于顶层后按延迟移除，以此推动面板自刷新。该实现没有 `invalidate()`、没有分区刷新、没有多次调用合并，也不做过期判断；调用点只有手势动作 `Action.EinkRefresh`（`ui\compose\GestureHelper.kt:101-112`），翻页、开应用、通知变化均不触发。

厂商面板模式由 `helper\EinkHelper.kt:21-27` 实现，通过反射 `android.os.ServiceManager.getService("meink")` 取得 IBinder，手写 Parcel 写入 `android.meink.IMeinkService`、包名与模式，再以事务码 5 提交，模式为关闭 0、高对比 1、清屏 3、阅读 4。该实现仅对 Mudita Kompakt 有效，未探测 Onyx、Boox、Hisense 的 EPD 接口，全库 `Class.forName` 只有这一处；设备识别另在 `helper\device\DeviceHelper.kt:7-41` 按 Build 字符串判断。

### 图标渲染与图标包

图标单色化在 `helper\IconUtility.kt:632-677`：降采样后按 `0.2126r + 0.7152g + 0.0722b` 统计亮度，取 `min + (max - min) * 0.70` 作硬阈值，高于阈值写透明、其余写主题色，alpha 小于 64 视为不透明。形状裁切按参数取椭圆、圆角矩形或原样，形状由 `Path` 描述。图标包解析在 `helper\IconPackUtility.kt:77-103`，读取包内 `appfilter.xml`，先用完整 `ComponentInfo{pkg/act}` 精确匹配，失败再退回包名前缀，资源目录与 assets 两处都查。缓存键包含图标模式、包名、尺寸、用户与 Activity，并附形状、对比度、背景色后缀；`LruCache` 上限取堆内存八分之一与 50 个 128 像素图标的较大值。

### 壁纸处理

`helper\WallpaperUtility.kt:250-282` 的 `recolorBitmap` 逐像素求亮度，再按 `fg + (bg - fg) * lum` 映射三通道，属双色线性插值，无量化步长、无二值化、无抖动（全库检索 `dither`、`Bayer`、`quantize` 均无结果）。预设图案由 Canvas 即时绘制点、线、网格、斜线、圆、交叉、波浪、十字、散点、短划，间距 20 至 24dp。跨会话无缓存，每次设置重新生成或解码。

### 通知

`services\NotificationService.kt` 实时接收 `onNotificationPosted` 与 `onNotificationRemoved`（:61、:76），连接时还原历史（:27），全量快照存于 `_sbnState`（:101-102）。数据模型分两层：包级 `NotificationInfo(count, title, text, category, timestamp)`（`NotificationManager.kt:14-20`），会话级 `ConversationNotification` 以 `StatusBarNotification.key` 关联（:22-30）。会话标识规则为分组通知取 `group_${pkg}_$group`、消息类取 `sms_$phone`、其余取发送者（`NotificationService.kt:275-293`）；摘要通知按 `flags and FLAG_GROUP_SUMMARY` 剔除，同包取非摘要的最新 `postTime` 计数，内容未变化不推送。点击复用系统 `contentIntent`，通过 `pendingIntent.send` 转投，API 34 附 `MODE_BACKGROUND_ACTIVITY_START_ALLOWED`；清除按组调用 `cancelNotification` 并单独撤销摘要。角标在 `NotificationBadge.kt:29-83` 内自绘，未使用 `setNumber` 或 `setBadgeIconType`。

界面上提供三种呈现：首页应用名旁的角标与消息摘要、`Letters` 全屏逐条阅读（纵向翻页，按键删除与打开，长按清空）、`SimpleTray` 分页托盘（每页通知数可配，按键为主，700ms 长按或长按重复实现全清）。三处各有一套独立的应用白名单。

### 小部件与快捷方式

`helper\AppWidgetHelper.kt` 是完整的 AppWidgetHost 宿主，`AppWidgetHost(context, 1024)`（:37），流程为 `allocateAppWidgetId`、`bindAppWidgetIdIfAllowed`、`createView`（:67-115），绑定失败改发 `ACTION_APPWIDGET_BIND`（:262-267），可上传任意已安装的 widget 提供者，但只保存一个槽位 `prefs.androidWidgetId`（:137-152）。固定快捷方式在 `PinnedShortcutUtility.kt:217-244` 先取图标再 `request.accept()`，撤销时以 `pinShortcuts` 重写剩余集合（:125-155）。

### 手势与按键

手势不按边缘划分区域，整块内容共用一个 `GestureDetector`，默认阈值 100dp，速度阈值写死 80，翻页阈值随密度换算（`ui\compose\GestureHelper.kt:339-357`）；轴向由首末点位移绝对值比较决定（:402）；长滑以 `longSwipeRatio × 阈值` 与 `1.8 × 速度阈值` 双条件判定（:464-466）；分页在 `onScroll` 中按 `(阈值 × shortSwipeRatio × 3f).coerceAtLeast(8)` 逐段推进，冷却 30ms。冲突规避有三处：双击在 350ms 内与图标点击互斥、`exclusionY` 与 `exclusionHeight` 排除区内屏蔽双击与水平滑、第二指按下即取消（:422-424、:444-446、:549-567）。动作以 `Action` 枚举统一绑定，包含打开抽屉、最近任务、通知、刷新、锁屏等。

按键映射为两套入口：`HomeKeyAction` 13 个动作对象，另把 KEYCODE_6、7、8、2 定义为虚拟的时钟、日期、引用、双击键（`helper\KeyMapperHelper.kt:7-27`），`mapHomeKey` 与 `mapAppButtonKey` 按偏好逐键分发（:30-52）。

### 编辑模式

编辑态为单例开关（`helper\EditModeHelper.kt:94-101`）。可调量集中在 `showAppSettings`（:1342-1438），包括图标尺寸 10 至 100、间距 0 至 50、首页应用数、页数、名称大小写、图标形状与来源、对比度，另有隐藏应用集合维护（:1826）。行列数没有设置项，网格容量由容器高度反推（`ui\AppsFragment.kt:314-326`）；拖拽落位按索引交换而非像素命中，上下移动时交换相邻项（:934-979），改动即时写入偏好，没有撤销。

### 检索

结果集为 `derivedStateOf`，触发条件为输入非空、非隐藏模式、未选字母（`ui\AppsFragment.kt:460-539`）。权重为应用 100（别名与原名取较大值，再叠加使用频次）、联系人 75 取前 10、设置项 `45 - index`、音乐与文件 40，末尾可选网页搜索项，结果按分数降序；空查询回落为字母分隔的全量列表。排序使用 `Collator` 的 PRIMARY 强度，已选中项前置（`helper\EditModeHelper.kt:2071-2074`）。

### 主题与配置

`data\ThemePresets.kt:7-51` 的 `ThemePreset` 约 30 个字段，采用可选覆盖语义：`null` 表示不修改，非空才写入。字段覆盖明暗两套文字色与背景色、字体、图标模式与形状、是否显示图标、文字岛屿、三处独立排布（时钟、日期、应用）、11 种钟面样式、底部组件类型（引用、日程、快捷方式、用量、页码点、停用）、首页应用数与页数、时钟字号、应用字号与间距、壁纸透明度与资源、名称大小写、第二时钟与时区偏移。14 条预设为手写常量，无生成算法。

配置为单个 SharedPreferences 文件（`Prefs.kt:18`），键为大写常量并保留部分遗留小写键；主题导出按 `themeExportKeys` 白名单（:305-331），带 `type` 与 `version` 包装；整机备份为 `Gson().toJson(prefs.all)`，导入按类型分派并剔除非本机包。迁移在 `Migration.kt:11-23` 按 `VERSION_CODE` 区间判断并回写版本，只做键级删除与改名。

## 三、逐域对照

| 域 | inkOS 已有 | 十雨桌面现状 | 评估 |
| --- | --- | --- | --- |
| 桌面布局 | 多页、应用数、页数、三处独立排布、应用 Y 偏移、上下组件边距、横向位置、页码点、记忆当前页开关 | 宫格与列表两种形态，列数、行数、页数上限、页码点开关、排序模式；间距与横向位置由 InkTokens 固定，不可调 | 十雨的可调项偏少，布局参数需要外露 |
| 底部组件 | 7 类可切换，含引用、日程、任意系统小部件、快捷方式、用量、页码点 | 13 个内置组件按 widgetId 单槽挂载，含时钟、日历、微信读书、KOReader、用量、图片、占位 | 数量上十雨更多，缺少"任意系统小部件"与页码点型组件 |
| 时钟 | 9 至 11 种样式，双时钟与时区偏移 | 时钟与紧凑时钟两种，十二小时制开关 | 样式数量差距明显，双时钟缺失 |
| 图标 | 文字字母、自适应、系统单色、图标包四种模式；三种形状；对比度档位 | 线稿框架加首字母回落，图标包选择已存在但绘制未读取 iconPackId | 图标包是"已记录偏好未生效"，补齐即可 |
| 字体 | 逐元素字体与字号，统一字体模式，支持导入 ttf 与 otf | 标签字体、钟面字体、字号缩放、字体导入 | 逐元素字体是其长处，十雨可按元素扩展 |
| 主题 | 明暗两套色值、15 套预设、透明度、文字岛屿、导入导出 | 三套内置主题加导入主题，InkColors 为常量，themeId 不参与绘制 | 主题开关当前只记录偏好，配色未随主题变化 |
| 手势 | 四方向滑、双击、时钟日期引用点击，各绑定 15 种以上动作，长短滑灵敏度可调 | 左右滑翻页、上下滑托盘、音量键翻页、长按进入整理 | 动作表与灵敏度可调是缺口 |
| 按键 | T9、方向键、QWERTY 支持，13 种 Home 键动作，虚拟功能键 | 音量键翻页 | 键盘机型适配显著弱于对方 |
| 抽屉与检索 | A-Z 字母条、三种排序、六类来源加权检索、自动启动唯一结果、长按菜单含锁定与信息 | 关于应用列表的排序、隐藏应用菜单、字母首字回落 | 检索与抽屉交互是最大功能差 |
| 通知 | 首页角标、消息摘要、媒体指示、Letters 全屏阅读、SimpleTray 分页托盘、三套白名单 | 通知中心平铺 20 条，托盘最多 8 条，无角标、无分组、无摘要 | 通知可见性弱，缺分组与角标 |
| 托盘 | WiFi、蓝牙、移动数据、手电、亮度、媒体、通知分页 | 电池、WiFi、蓝牙、前光双通道、媒体、通知、悬浮层提示 | 十雨的前光双通道更贴合墨水屏，缺移动数据与手电 |
| 最近任务 | 最近应用与使用统计，时间过滤，三种计量显示 | 使用统计配置开关与用量组件 | 缺独立页面 |
| 刷新 | 反色闪屏、退出应用后自动刷新、四种面板模式（仅 Kompakt） | 双次反色闪屏、定时整刷、按需局部刷新，无厂商适配 | 十雨机制更完整，缺厂商探测与"退出应用后刷新" |
| 硬件适配 | 按 Build 字符串识别，UI 缩放九档 | DeviceProfileDetector 按机型标记识别墨水屏与前光，字号 0.85 至 1.6 | 十雨的设备识别更细，缺 UI 缩放档位 |
| 数据与迁移 | 备份恢复、主题导入导出、版本区间迁移 | 单文件 JSON、版本字段未使用、解析失败整体回落默认 | 十雨缺少迁移与分节容错 |
| 编辑模式 | 单例开关、点击元素即时设置、索引交换、无撤销 | 长按 2 秒进入、拖拽重排、隐藏与卸载菜单、无撤销 | 十雨的进入门槛偏高，缺逐元素设置 |

## 四、建议吸收的功能

### 优先实施

一、刷新入口统一为单一策略服务。inkOS 用一个函数完成反色闪屏，调用方只有一处，边界清晰。十雨当前有两套整刷来源，`core\data\...\InkRefreshController.kt` 负责两次反色闪屏，`RefreshScheduler.kt` 负责定时整刷，`OctoberRainApp.kt:101-116` 中两者分别由外观变化与托盘配置触发，`HomeView.requestPartial` 又独立调用局部刷新，整刷间隔与实际调用点分散在多处。建议在 `core:device` 收拢为 `RefreshPolicy`：统一维护上次整刷时间、残影计数、场景类型（切页、关应用、定时、配置变化），对外只暴露 `onSceneChanged` 与 `requestFull`，并把"退出应用后刷新"作为策略项。改动覆盖三处调用点，代码量约 200 行。

二、通知模型升级为包级与会话级两层。inkOS 的会话标识规则、摘要过滤、按组撤销、内容未变不推送四条处理可原样借用。十雨 `core\data\...\NotificationCenter.kt` 的 `NotificationItem` 只有 key、包名、标题、正文、时间，托盘按条平铺，首页没有角标。建议增加会话归并（分组、消息类、发送者三类标识）、摘要剔除、按应用折叠显示，并在 `HomeView` 的图标绘制处补角标（自绘而非系统角标，符合墨水屏对比度要求）。代码量约 300 行，其中绘制部分约 60 行。

三、完成图标包消费。设置页已能列出内置线稿与导入的图标包，`feature\settings\...\IconPackPickerScreen.kt:11-13` 的注释自述"桌面绘制当前尚未读取 iconPackId"。建议在 `core:render` 的图标解析过程加入 `appfilter.xml` 解析，先按 `ComponentInfo` 精确匹配再按包名前缀回退，资源与 assets 双查，缓存键纳入图标包标识与形状。这条属于补齐既有设计，代码量约 150 行，收益显而易见。

四、桌面可调项扩充。inkOS 把间距、图标尺寸、名称大小写、三处独立排布、首页应用数、上下边距全部外露。十雨 `core\common\...\Models.kt:40-54` 的 `DesktopConfig` 只有形态、组件、仅图标、排序、隐藏、顺序、页数上限、列数、行数、页码点、长按阈值、滑动阈值与图片文件位置；`DesktopMetrics.compute`（`feature\home\...\DesktopMetrics.kt:175`）已按令牌计算行列，改成"配置优先、令牌回落"即可，无需重写布局。建议先开放列间距、行间距、图标尺寸、名称字号、横向位置、首页应用数六项，代码量约 150 行，设置页增加六项。

五、长按分级。inkOS 把"单元素设置"与"编辑模式"拆成两个阈值。十雨 `DesktopConfig.longPressEditMs` 默认 2000ms 只有单一入口，改为 400ms 打开单元素菜单、1200ms 进入整理模式，两个阈值入配置，改动集中在 `HomeView.handleUp` 与设置页，成本很小。

六、主题从标签升级为令牌覆盖。inkOS 的预设采用可选覆盖语义，明暗两套色值独立。十雨 `core\design\...\DesignRuntime.kt:46-52` 的 `update` 只重算设备令牌与字号，`InkColors` 为常量，`AppearanceRepositoryImpl.kt:135-146` 列出的素白、米白、墨黑三套主题与导入主题都不改变绘制颜色。建议把 `InkColors` 改为可注入对象，`ThemePayload` 解析后映射为覆盖，保留设备默认值兜底；绘制侧已统一走 `DesignRuntime`，改动面可控。代码量约 200 行。

### 可排期

一、通知全屏逐条阅读。把会话按页展示、纵向翻页、按键删除与打开，适合减少滚动带来的残影，可与现有托盘并存，作为托盘内的一个模式。

二、应用抽屉的字母条与多源检索。字母条与"设置项、应用、联系人"三类来源可以先做，拼音检索需要词库，成本较高，可放在后面。

三、最近任务独立页面与时间过滤，复用现有使用统计权限与数据源。

四、钟面样式扩充与双时钟、时区偏移。先补两到三种样式即可覆盖多数需求。

五、备份与恢复、主题导出。十雨已有导入，补导出与整机备份的导入校验。

六、UI 缩放档位。把现有 0.85 至 1.6 的字号缩放展开为若干固定档位，并在设置页以列表呈现。

七、系统小部件单槽宿主。inkOS 的 `AppWidgetHelper` 约 280 行，接入后可与现有 13 个内置组件并存；代价在于宿主生命周期、`startListening` 与绑定授权页的处理，与现有 `WidgetRegistry` 的注册方式需要并存设计。

### 暂不采纳

一、迁移到 Compose。十雨为纯 View 加 Canvas 绘制，已形成令牌与自绘体系，迁移的回归面远超收益。

二、MeInk 四种面板模式。该接口只对 Mudita Kompakt 有效，十雨的设备识别层已按机型区分，保留接口位即可，不必现在实现。

三、工作资料与私密空间切换。与启动器定位关联较弱，且需要设备管理相关权限。

四、单 Activity 手写页面栈。十雨已有 `LauncherRouter` 与页面栈，inkOS 的做法在结构上与之接近，没有替换价值。

五、一次补齐 15 套预设主题。预设依赖主题令牌化，先做令牌化与三到五套预设，后续按需增加。

## 五、现有设计的可优化点

一、整刷职责重复。`InkRefreshController` 与 `RefreshScheduler` 各自实现整刷，前者由 `OctoberRainApp` 调用，后者按托盘配置定时执行，两者不共享时间基准，可能出现十分钟内两次整刷。合并后由单一服务记录时间戳与场景。

二、配置迁移缺失。`Models.kt:152` 的 `LauncherConfig.version` 为 1，全库没有按版本判断的迁移代码；`JsonConfigStore.load` 在解析失败时整体回落默认配置，用户设置会一次性丢失。建议引入版本区间迁移（参照 `Migration.kt` 的写法），并把解析失败改为按分节兜底：已知分节单独解析，失败分节取默认值，其余保留。

三、设置项规格化。设置项当前分散在 `feature:settings` 的各 Screen 中以硬编码方式构造，新增一项需要同时改 Screen、配置模型、默认值与摘要文案。可抽出 `SettingsSpec`（分组、键、类型、范围、默认值、摘要模板），由渲染层统一生成控件，与 inkOS 的设置页组织方式接近，降低后续加项成本。

四、图标二值化参数外露。`IconResolver` 的阈值可以按对比度档位配置，缓存键纳入对比度与主题标识；inkOS 的 `min + (max - min) * 0.70` 属于固定值，十雨若做成档位即可超过对方。

五、通知权限引导。`NotificationCenter.canRead` 已有判断与跳转，可补首次进入托盘时的说明页，说明为什么需要通知读取权限；inkOS 有对应的首次提示与权限说明对话框。

六、页面切换与托盘展开的刷新时机。当前 `HomeView.requestPartial` 与 `TrayPanelView.refresh` 各自决定刷新范围，统一到策略服务后可区分"翻页只刷局部"与"开托盘整刷"。

七、抖动与壁纸尚未接入绘制。`core\render\Ditherer.kt:12` 的抖动算法实现完整，但没有调用点；`core\data\...\AppearanceRepositoryImpl.kt:62` 的壁纸读取同样没有调用方，桌面绘制不读取壁纸。这两处接入 `HomeView` 的绘制流程后，灰度层次与自定义背景可以同时生效。inkOS 的壁纸处理只做双色线性映射，也没有抖动，此处存在形成差异的空间。

八、图标缓存容量与解码次数。`feature\home\IconStore.kt:17-20` 的 LRU 上限为 48 条，图标按目标尺寸重复解码，没有预取与降灰处理。按屏上图标数量与跨页缓存估算，容量可提到 200 条以上，并在应用列表刷新时提前解码首屏图标。

九、组件单槽与测量协商。当前只有首页一个组件槽位，槽高按余量推算，`HomeView.kt:357-359` 依据尺寸回调重绘。若后续引入任意系统小部件宿主，需要先补测量协商机制，否则小部件在固定槽高位下会变形。

十、缺少自动化验证。仓库没有测试目录与 CI，回归依赖手工操作。可先为配置迁移、分页容量计算、通知会话归并三处纯逻辑补单元测试，再接入构建流水线，这一项在新增功能之前完成最为省力。

## 六、需要决定的事项

一、刷新策略是否按第四部分第一条合并两处整刷入口，并把"退出应用后刷新"纳入策略。涉及 `core:device` 与 `OctoberRainApp`、`HomeView`、`RefreshScheduler` 三处调用点。

二、通知能力做到哪一层。第一层为角标、分组、会话摘要；第二层增加全屏逐条阅读模式。第二层依赖第一层的数据模型。

三、主题令牌化的范围。仅内置三套主题走令牌覆盖，还是同时让导入主题 JSON 参与绘制。后者需要定义覆盖字段的校验规则与缺省回落。

四、是否引入 AppWidgetHost 单槽宿主。引入后可与内置组件并存，需处理宿主生命周期、绑定授权与尺寸测量。

五、桌面可调项先开放哪几项。建议先做间距、图标尺寸、横向位置、每页应用数，字号与钟面样式随后。

## 七、索引

inkOS 本地副本位于 `D:\DSHStation\_ref\inkOS-src\`，包含 `README.md`、`DOCUMENTATION.md` 与 44 个 Kotlin 文件，缺失文件已在文中以"未确认"标注。十雨桌面相关文件：`core\common\...\Models.kt`、`core\data\...\NotificationCenter.kt`、`core\data\...\JsonConfigStore.kt`、`core\design\...\DesignRuntime.kt`、`core\design\...\InkColors.kt`、`feature\home\...\DesktopMetrics.kt`、`feature\home\...\HomeView.kt`、`feature\tray\...\TrayPanelView.kt`、`feature\settings\...\IconPackPickerScreen.kt`、`service\...\NotificationRelayService.kt`。
