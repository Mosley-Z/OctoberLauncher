# 上游实现结构剖析

本文说明 tomo-launcher 0.5.0 的组织方式，作为新工程结构设计的对照。全部内容取自 `_analysis\jadx-out` 静态阅读。

## 一、分层与装配

工程只有一个 Module，包结构为 `data`（105 个类）、`ui`（42 个）、`overlay`（6 个）、`notify`（3 个）、`receiver`（1 个）与根包 15 个 Activity，外加 69 个由 ViewBinding 生成的 Binding 类。没有依赖注入框架，没有导航框架，也没有跨模块边界。

`LauncherActivity` 是唯一的装配点，`onCreate` 以二十步命令式顺序创建九个控制器并立即接线，构造 `DeskWidgetsController` 时一次性传入十二个 View、四个存储与八个回调，该构造签名有 25 个参数（`ui\DeskWidgetsController.java:523`）。控制器之间通过传入的 lambda 回调通信，没有事件总线，也没有共享状态容器。

| 控制器 | 职责 | 代码规模 |
| --- | --- | --- |
| `LauncherActivity` | 装配、分页、菜单、锁屏、更新入口、权限分派 | 2688 行 |
| `DeskWidgetsController` | 十三个组件的切换、刷新、预览、组件库 | 2971 行 |
| `FileManagerActivity` | 文件浏览与全部文件操作 | 2455 行 |
| `BookshelfActivity` | 书架网格与列表 | 1549 行 |
| `QuickTrayController` | 托盘全部控件与业务 | 848 行 |
| `HomeGridController` | 宫格绑定、长按、按压态 | 518 行 |
| `WidgetLibraryController` | 组件库弹窗与十种预览 | 720 行 |

## 二、状态与刷新

没有单向数据流，也没有可观察状态。刷新由三类信号驱动：

第一类是生命周期，`onResume` 里比对布局键 `kind|widgetEnabled|当前组件|iconOnly|tomoFilesEnabled|labelFonts.token()`，任一变化触发整页重建（`LauncherActivity.java:392-431`）；`onStart` 里对组件区、阅读面板、托盘分别调 `refresh()`（`LauncherActivity.java:2717-2760`）。

第二类是广播，时间用 `ACTION_TIME_TICK`、`TIME_SET`、`TIMEZONE_CHANGED`、`DATE_CHANGED` 与自定义 `CLOCK_CHANGED`，电量用 `ACTION_BATTERY_CHANGED`，WiFi 用三个状态广播并对 RSSI 做 1500ms 节流，包变更用动态注册的 `PackageChangeReceiver`（`ui\StatusBarController.java:33`、`:105-124`、`:296-302`）。

第三类是用户手势与定时，托盘打开时注册网络与蓝牙广播并在关闭时反注册（`ui\QuickTrayController.java:708-742`）。

每次刷新都先与上一轮字符串比较，相同则跳过重绘（`ui\StatusBarController.java:184-234`），这是墨水屏上避免无谓刷新的主要手段。

## 三、数据层

持久化只有 SharedPreferences 与文件两种，共 15 个以 `tomo_` 为前缀的偏好文件，无 Room，唯一 SQLite 使用点是只读查询 KOReader 的统计库副本（`data\KoreaderStats.java:20-47`）。结构化数据以 JSON 字符串存入偏好或写入 `filesDir`：书架合集 `shelf_collections.json`、索引 `shelf_index.json`、微信读书七类缓存、天气快照。图像资产以文件形式写入：`desktop_wallpaper.img`、`desk_widget_image.img`、`icon_overrides/*.png`、`weread_covers/*.img`。

写操作没有事务概念。`ShelfCollectionStore` 整体覆盖写入文本文件，无临时文件与原子改名；主题包应用时解压到 `cacheDir/theme_pack_apply` 后再搬运。写偏好统一用 `apply()` 异步提交。

长耗时任务统一挂 `data\TomoWorkers.java:19-31` 的单线程守护池（线程名 `tomo-io`，优先级 4），天气、更新、微信读书请求因此串行排队，主线程只做结果校验与绑定，回调前用代次（generation）判断结果是否过期。

## 四、渲染层

墨水屏适配集中在 `ui` 包的九个自绘控件与三个绘制工具：

`EinkRefresh.flash` 以黑白各 500ms 的全屏覆盖完成整屏刷新，优先调用无障碍服务的 `flashScreen`，失败时退回本 Activity 的 `decorView`（`ui\EinkRefresh.java:19-89`）。

`EinkRoundRect`、`EinkCapsule` 以逐像素算法生成圆角矩形与胶囊位图，再交给 `EinkRoundRectDrawable` 缓存正反两态（`ui\EinkRoundRectDrawable.java:38-65`）。`OutlineTextView` 以白描边加黑正文保证文字在壁纸上可读，并提供 `setInkOffsetY` 做基线校正。`EinkPressTextView`、`EinkPressImageView`、`EinkRoundRectLayout` 实现即时反色按压。

全包检索不到 `ValueAnimator`、`ObjectAnimator`、`startAnimation`、`AnimationUtils`、`ViewPropertyAnimator`、`TransitionManager` 的任何引用，动画在工程层面被整体移出。惯性滚动由 `NoFlingScrollView` 关闭，`fling` 为空实现。

位图统一按 `RGB_565` 解码，`inScaled=false`，采样倍率取 2 的幂，绘制时关闭 `filterBitmap` 与 `dither`，以最近邻缩放保持边缘锐利。

## 五、系统能力封装

| 能力 | 封装类 | 关键点 |
| --- | --- | --- |
| 通知 | `notify\TomoNotificationService` | 只重写连接回调，托盘拉取 `getActiveNotifications` |
| 无障碍 | `overlay\TomoAccessibilityService` | 只监听窗口变化，200ms 去抖，反射注入按键 |
| 悬浮窗 | `overlay\OverlayHost` | 三个 `TYPE_ACCESSIBILITY_OVERLAY` 窗口 |
| 前光 | `data\FrontLight` | 双通道十档，内容提供者优先，失败退系统设置，写入带渐变 |
| 权限 | `data\TomoPermissions` | 八类权限的授权与撤销映射，统一请求码 3101 |
| 网络 | `data\TomoHttp` | 单例封装 GET 与 POST，UA 固定 |
| 更新 | `data\UpdateClient`、`data\UpdateRunner` | 版本比对、下载、SHA-256 校验、系统安装器 |

## 六、值得沿用的设计

**以枚举承载产品语义。** `DesktopKind`、`DeskWidget`、`DeskWidgetCategory`、`ClockFace`、`FileKind`、`ShelfKind`、`TomoPermission` 都以枚举定义取值集合，偏好里存小写字面，解析失败一律回落默认值，配置损坏不会导致白屏。

**分层裁剪的位图管线。** 图标先缩到目标尺寸再套统一外形框，解码与绘制参数全局一致，缓存上限与裁剪时机都有明确策略。

**两阶段长按。** 第一阶段取系统长按时长，第二阶段固定 2 秒，第一阶段到点只给一次按下态反馈，抬手才弹菜单，避免了“按住弹菜单、松手又触发点击”的歧义。

**单线程 IO 加代次校验。** 所有网络与文件任务串行，回调带代次，旧结果自然丢弃，无需取消逻辑。

**整页闪烁刷新。** 以两次全屏着色完成整屏刷新，不依赖厂商刷新接口，在任意机型上行为一致。

## 七、结构性问题清单

| 问题 | 表现 | 影响 |
| --- | --- | --- |
| 单一 Activity 承担装配与业务 | `LauncherActivity` 2688 行，装配、分页、菜单、锁屏、更新、权限混在同一类 | 任何改动都要触碰主类，回归面大 |
| 组件不可扩展 | 十三个组件以十二个预置容器加 `when` 分支实现，新增组件需改布局、控制器与预览三处 | 扩展成本线性上升 |
| 尺寸写死像素 | dimens 全部为 px 且围绕 800 高度，圆角与线宽为常量 | 换分辨率即错位，与屏幕准入互为因果 |
| 无状态容器 | 控制器各自持有 View 与存储，状态分散 | 无法做预览、测试与跨进程恢复 |
| 数据无版本与迁移 | 偏好键与 JSON 字段逐项读写，无 schema 版本 | 结构调整会读到旧数据 |
| 写文件无原子性 | 选集 JSON 整体覆盖写 | 写入中断会留下损坏文件 |
| 图标缓存偏小 | 上限 48，页面切回需重解码 | 大应用量下滚动有明显重绘 |
| 通知只在打开时拉取 | 不实现投递回调 | 托盘数据不实时 |
| 音乐与媒体控制缺失 | 无媒体会话接入 | 播放器场景需要另开应用 |
| 语言单一 | 自有文案仅中文 | 无海外可用性 |
