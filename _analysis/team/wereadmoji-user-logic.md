# 微读墨记用户侧逻辑与硬限制清点

清点对象：`D:\WorkBuddyStation\WereadMoji`（包名 `com.inkread.weekread`，应用名「微读墨记」，V0.6.0）。目的：为把该项目内置进十雨桌面提供边界依据，说明哪些用户侧行为与自设限制必须延续，哪些随宿主替换而失去依据。

本报告只读清点，未修改任何工程文件。

## 一、清点范围与取证方法

读过的材料：

- 文档：`docs\00_项目宪法.md`、`docs\01_设备_S4.md`、`docs\02_架构.md`、`docs\04_桌面让位判据.md`、`docs\09_手机端遥控台UI规范.md`、`docs\10_开发专家交接手册（代际版）.md`、`docs\12_交接_2026-10-10_TASK-086断点与待拍板.md`、`docs\15_团队工作纪律_T条与K条.md`、`docs\FEATURES\remote.md`、`docs\FEATURES\update-channel.md`、`docs\FEATURES\desk.md`、`docs\DECISIONS\ADR-012`、`使用说明.md`、`README_项目导览.md`、`设备_签名_凭据.md`。
- 代码：`a11y\CardA11yService.java`、`a11y\TomoPageGate.java`、`a11y\OverlayController.java`（头部与常量）、`feature\OverlayWindow.java`、`core\CardPrefs.java`（第 679-710 行）、`core\FeatureGate.java`、`core\BillScheduler.java`（第 1-40 行、第 255 行附近）、`shell\MainActivity.java`（导航与角色分流段）、`shell\ConsoleActivity.java`（第 37-62 行）、`shell\SettingsActivity.java`、`shell\SettingsPageController.java`、`feature\WeekCardView.java`、`feature\CardRenderer.java`（方法表）、`net\WereadApi.java`、`net\NoteSync.java`、`core\StatsStore.java`、`core\NoteStore.java`、`remote\HidKeepAliveService.java`（入口段）。
- 构建与发布：`weread-widget\tools\build.sh`、`weread-widget\.github\workflows\release.yml`、`res\xml\a11y_card_service.xml`、`res\xml\a11y_remote_service.xml`、`AndroidManifest.xml`、`weread-widget\LICENSE`。

## 二、两处「用户侧」的含义

**功能面**：`使用说明.md` 以使用者视角记录了两个入口（`使用说明.md:10-15`）、桌面上四个形态与可点区（`使用说明.md:54-93`）、App 内四页签结构与刷新入口（`使用说明.md:96-118`）、本记页的每日一签与导出（`使用说明.md:120-137`）。这些是产品行为约定，属于内置时需要逐项确认保留或替代的内容。

**限制面**：`docs\00_项目宪法.md`、`docs\01_设备_S4.md`、`docs\04_桌面让位判据.md` 与代码中的注释登记了一组项目自设的约束，来源分三类——设备与平台的客观能力、项目自设的工程纪律、为规避已知故障而定的实现禁忌。内置进十雨桌面后，三类约束的去留判断不同。

## 三、仍然成立的限制

### 3.1 绘制与取数分离

`docs\02_架构.md:117-119` 记载两条红线：绘制阶段只允许调用内存函数（`onDraw` 读文件曾造成 700ms/帧的事故），渲染函数永不发起网络请求。该约束与宿主组件协议同构：`core\widget-api\...\WidgetApi.kt:82-89` 的 `BaseWidgetView.draw` 只做尺寸校正后调用 `onDraw`，数据在 `InkWidgetView.kt:92-108` 的协程中加载完成后才 `requestDraw`。内置实现需要沿用这一分工。

### 3.2 无定时闹钟

`core\BillScheduler.java:20` 的项目铁律是「不做定时闹钟（AlarmManager / 前台服务一律不引）」，到点落定靠启动时机幂等补齐（同文件第 255 行附近的说明）。宿主 `core\render\...\RefreshScheduler.kt:21` 的十分钟间隔只负责整屏刷新时机，与数据刷新无关。内置后数据刷新需要重新设计，可参照的现成形态是：启动与进入页面时校验，加手动刷新入口（`使用说明.md:67-75` 的「更新于…」短按刷新即卡片唯一取数入口）。

### 3.3 缓存与换钥的数据处置

`core\StatsStore.java:12-23`、`:28-33` 与 `docs\FEATURES\stats-data.md:46-50` 记载：按周期分键、`CACHE_MAX=6` 的顺序池、年度与累计独立段、换 API Key 时 `wipePersonalData` 与 `clearCache` 两段一起清。宿主对应实现是 `WereadClient.kt:56-61` 的三十分钟内存缓存与 `invalidate()`，以及 `ContentSourceImpl.kt:170-177` 的六类缓存清理、`:186-199` 的先出缓存。内置后缓存键与清空范围要与宿主既有实现统一，清空动作需要覆盖笔记与账单类数据，否则换钥后旧数据仍会显示。

### 3.4 文案与能力边界

`docs\00_项目宪法.md:21-36` 要求「文案不许超出已验证能力」。现有面向使用者的表述有明确边界：`使用说明.md:50` 写「不读取任何屏幕内容」，而 `res\xml\a11y_card_service.xml:38` 实为 `canRetrieveWindowContent="true"`（注释 `:22` 记为 2026-09-26 由 false 翻为 true），`AndroidManifest.xml:131` 亦注明窗口内容用于判断桌面与页码。两处文字与配置不一致，内置时需要重新统一表述。

### 3.5 安装角色与形态开关

`core\CardPrefs.java:679-710` 定义 `install_role`（阅读器 = 0、手机 = 1、缺省阅读器），`core\FeatureGate.java:9-36` 说明「一套源码两发布线」，`:39-43` 以版本名含 `-beta` 判定完整形态，`:76-91` 三个开关分别管实验室、遥控与角色选择，`:56-62` 的 dev 覆盖仅在 `FLAG_DEBUGGABLE` 下可用。内置进宿主后安装角色不再由本应用决定，需改由宿主的设置项或分发形态表达。

### 3.6 设备与平台边界

`docs\01_设备_S4.md:8-20` 与 `设备_签名_凭据.md:9-18`：rk3326、Android 11（API 30）、仅 armeabi-v7a 32 位、low_ram 堆 128–256MB、480×800、无 root。宿主的支持矩阵以 480×800 为首要形态（`docs\05-device-adaptation.md:49-58`），minSdk 26 / targetSdk 35（`gradle\libs.versions.toml:4-6`）。S4 是否纳入宿主正式支持矩阵，与 32 位 ABI 打包方式一起，属于需要确认的事项。

### 3.7 发布纪律

`weread-widget\tools\build.sh` 是无参数的发布构建入口（`:1-10`），`--dev` 插入 `android:debuggable` 并打开密钥注入入口，输出名带 `-dev-debug`，仍用发布签名，且不生成 `update.json`。`weread-widget\.github\workflows\release.yml:22-32` 只在推送 `v*` 标签或手动触发时运行，`:89-148` 以 `dist/update.json` 的 sha256 与体积逐字节校验安装包，`:174-185` 对含 `-beta` 的标签标预发布。该文件定义的流程只做发布，不做编译与测试。内置后构建并入宿主 Gradle，发布校验逻辑可以保留为打包脚本的检查项，标签式发布改由宿主仓库承担。

`tools\build.sh:249` 只调用 `com.android.tools.r8.D8` 编译 dex，全仓检索 proguard 与 R8 压缩配置无命中，即该项目不启用代码混淆与资源压缩。因此「系统类不被混淆」这一要求在无混淆前提下自然成立；迁移到宿主后，宿主 release 构建启用了 `isMinifyEnabled` 与 `isShrinkResources`（`app\build.gradle.kts:18-27`），届时需要通过 keep 规则保护反射与无障碍相关类。

版本号与升级规则见 `docs\FEATURES\update-channel.md:39-44`（只认 versionCode、不可降级、两条发布线共享单调递增的 versionCode 池）与 `release.yml:174-179`（标签含 `-beta` 时标预发布）。内置后该规则并入宿主版本管理。

## 四、随宿主替换而失去依据的限制

### 4.1 悬浮窗与无障碍呈现

`a11y\OverlayController.java`、`feature\OverlayWindow.java:25-28` 说明卡片走 `TYPE_ACCESSIBILITY_OVERLAY`，不需要 `SYSTEM_ALERT_WINDOW`；`:42-54` 说明卡片主体 `FLAG_NOT_TOUCHABLE`，靠多个独立触摸子窗接收点按。宿主组件由 `HomeView.kt:391-426` 直接持有实例并绘制到同一画布，`WidgetApi.kt:29` 的 `draw(canvas,width,height)` 与 `:32` 的 `onClick(x,y)` 已覆盖这两件事。该项限制与实现整体放弃。

### 4.2 桌面让位判据

`docs\04_桌面让位判据.md:9-12` 把需求二值化为「卡片在第 1 页则显示，否则隐藏」，`:14-17` 规定必须用像素真值判定；`:21-33`、`:35-45` 记录 Tomo 的 `WINDOW_CONTENT_CHANGED` 计数规则，`:192-210` 记录 Elauncher 的 `scrollX == -480` 精确相等规则。`a11y\TomoPageGate.java:9-15` 声明判据整段平移、一行未改，`:37-44` 说明动机是卡片窗口压住两行图标。宿主自身即桌面，组件位置由 `DesktopMetrics.kt:193-194`、`:371-388` 的组件块计算得出，不存在覆盖图标与翻页隐藏的问题。该判据及其两套桌面适配整体放弃。

### 4.3 页面探测与窗口内容权限

`a11y\CardA11yService.java:45-49` 记载窗口内容权限的两个使用处为 `SettingsPageProbe` 与 `ElaHomeProbe`，且各查一个控件 id、命中才让位、不存储不上传。两处探测只服务让位判据，与 4.2 同去。宿主现有 `res\xml\accessibility_service.xml:3-9` 为 `flagDefault`、`canRetrieveWindowContent="false"`、仅 `typeWindowStateChanged`，维持该配置即满足内置所需。

### 4.4 调试用密钥注入入口

`shell\MainActivity.java:553-561` 与 `docs\00_项目宪法.md:21-36` 记载的 `am start --es api_key` 入口由 dev 构建把门。宿主设置页已提供密钥输入与清除（`feature\settings\...\BookshelfSpecs.kt:67-73`、`:78-81`），内置后该入口没有必要保留，相应 `FLAG_DEBUGGABLE` 分支一并删除。

### 4.5 构建与工具链约束

`docs\00_项目宪法.md:9-15` 的纯 Java、无 AndroidX、无 Gradle、手工 `javac` + `d8` + `aapt2` + `zipalign` + `apksigner`，来自 `docs\02_架构.md:64-67` 记录的理由（32 位设备与 128MB 堆，引入 Gradle 会更换工具链）。内置进宿主后代码以 Kotlin 表达、由宿主 Gradle 构建，该约束随之终止；`build.sh:116-120` 的唯一验证组合（build-tools 33.0.2 + android-33）不再适用。

### 4.6 应用内更新

`net\UpdateChecker.java`（351 行）、`update\ApkInstaller.java`（219 行）、`update\ApkProvider.java`（67 行）与 `docs\FEATURES\update-channel.md:24-44` 组成独立更新通道（双通道、只认 versionCode、不可降级）。宿主已有 `core\data\...\UpdateServiceImpl` 与自己的通道，内置后只保留宿主一套，`ApkProvider` 一类对外暴露安装包的组件可以删除。

## 五、遥控台与内置版的关系边界

遥控台是仅手机端出现的形态，`shell\ConsoleActivity.java:57` 明写只服务 `install_role=phone`，阅读器端不实例化该类与布局。三页能力（按键、晃动、连接）与墨水屏端接收侧分离：

**手机端**：`remote\RemoteKeyService.java` 捕获音量键（只处理 `ACTION_DOWN`、消费返回 true，`docs\FEATURES\remote.md:45-64`），`remote\WifiTcpLink.java` 作为服务端监听 45678（同文 `:66-87`），`remote\ShakeDetector.java`（775 行）与 `ui\ShakePageView.java`（709 行）实现晃动翻页（`:89-131`，灵敏度三档、冷却三档、三道门控，`:121-125` 记录偶发弹划线页的未定论缺陷）。

**墨水屏端**：`remote\RemoteInjector.java`（313 行）用 `dispatchGesture` 注入单点滑动（`remote.md:258-270`：下一页 x=425、上一页 x=55、y=400、50ms、串行队列）。该动作要求无障碍服务具备手势能力；锁屏与息屏状态下音量键通路实测全部失败（`remote.md:133-155`），蓝牙 HID 是另一通路（`docs\DECISIONS\ADR-012`）。

**与内置版的关系**：遥控台的数据与控制对象是「另一台设备上的微读墨记阅读页」，与阅读统计、书架、划线数据无关。`docs\FEATURES\desk.md:31-36` 把墨台定位为独立全屏页面，并明确其红线是「不是负一屏」；`docs\FEATURES\remote.md:236-244` 的角色表规定手机端隐藏卡片相关界面、不启用卡片悬浮服务。三项结合可知遥控台与卡片、墨台、负一屏没有数据或渲染上的耦合，并入宿主时可按独立模块处理。内置后两端关系有两种可能：其一，手机端保留独立应用，墨水屏端在宿主内新增接收服务；其二，手机端并入宿主同一 APK，按 `install_role` 隐藏阅读相关界面。范围一（并入宿主）需要改动宿主无障碍声明以支持按键过滤与手势，现有 `accessibility_service.xml:3-9` 两项皆无；实测宿主 `service\...\OverlayAccessibilityService.kt:113` 重写了 `onKeyEvent`，但配置文件缺 `flagRequestFilterKeyEvents`，按键是否真被派发需要真机验证（待确认，验证方式：S4 上按键后查该服务日志与系统无障碍事件）。

## 六、构建与发布纪律的可延续部分

`docs\10_开发专家交接手册（代际版）.md:71-79` 的七步流程中，第③步「出实现方案、交用户、等拍板（不拍板不动代码）」与第⑥步「审查只在正式任务收尾启用」属于协作约定，内置工作沿用同一约定。`:81-85` 要求方案给出改动文件、关键逻辑、取舍、验收判据与待定点，改动策略要给选项与推荐；`:89` 规定一次一张卡，同动设置页三文件的卡禁并行；`:90` 规定提交时显式列文件。

`docs\15_团队工作纪律_T条与K条.md:26-33` 的 T 条与 `:39-44` 的 K 条，以及 `docs\00_项目宪法.md:21-36` 的「状态类事实当场复查」「Key 注入入口只在 dev 构建可用」，属于流程纪律，与宿主现有的 `docs\08-engineering-conventions` 系列并行存在，合并时以宿主文档为准，条目内容可以逐条转写。

发布前回归：`docs\12_交接_2026-10-10_TASK-086断点与待拍板.md:39` 记载 `regress full` 36 项发版前必跑，`验证记录/230` 记录无线通道 36/36 跑通。宿主 `docs\12-验收记录` 已有自己的清单，两套清单需要合并，不能互相替代。

## 七、融合时需要重新设计的事项

**尺寸与排版**：卡片以「号 × `unitPx`」排版，`ui\InkTheme.java:39` 的 `UNIT_RATIO=0.0015f`、`:51-54` 按屏幕长边换算，`core\CardSpec.java:39-49` 把 480×800 上的卡片框定在 `[56,70][424,416]`。宿主使用 dp/sp 体系（`core\design\...\InkTokens.kt:47-131`），组件尺寸由 `DesktopMetrics.kt` 计算。迁移时排版参数需要按宿主 token 重算，卡片固定 368×346 的假设不再成立。

**页面结构**：`shell\MainActivity.java:50-70` 为四大标签壳（阅读、设置、实验室、待办），`:663` 的可见表第三项由 `FeatureGate.labVisible` 控制。宿主已有微信读书页面与分段（`WereadScreen` 与 `WereadSection` 的概览、统计、笔记、账单、排行），内置后页面清单与标签结构需要重新编排，「实验室」「待办」两类页面的去留需要单独确认。

**权限**：`AndroidManifest.xml:20` 的 `WRITE_SECURE_SETTINGS` 对应 `core\PowerSettingsManager.java`（180 行）的电源与刷新管理；宿主清单（`app\src\main\AndroidManifest.xml:5-32`）没有该权限，也不申请 `REQUEST_INSTALL_PACKAGES` 的安装用途以外的系统写权限。内置后是否保留电源管理能力，需要决定是否扩大宿主权限申报。

**前台服务**：微读墨记唯一前台服务是遥控侧的 `remote\HidKeepAliveService.java:100`、`:209-221`，若遥控台并入，宿主需要新增对应的前台服务声明与通知渠道。

## 八、需要先决定的问题

1. 遥控台是否并入宿主，或保留为独立手机端应用。前者需要扩大宿主无障碍声明与前台服务，后者需要维持第二条发布线。
2. 内置后是否需要墨水屏端接收遥控。该决定直接影响 `canRequestFilterKeyEvents`、`canPerformGestures` 与 `flagRequestFilterKeyEvents` 是否进入宿主无障碍配置。
3. 电源与刷新管理（`WRITE_SECURE_SETTINGS`）是否保留。
4. 更新通道归属：内置后只有宿主通道，微读墨记的通道与 `ApkProvider` 是否立即删除。
5. S4（API 30、仅 32 位、128MB 堆）是否纳入宿主正式支持矩阵，以及 32 位打包方式。
6. 四页签与本记页的交互约定，是否整体照搬（`使用说明.md:96-137`），还是并入宿主现有微信读书页面。

## 九、索引

| 主题 | 文件 | 关键行 |
|---|---|---|
| 许可 | `weread-widget\LICENSE` | 1-21（MIT，Copyright (c) 2026 Mosley-Z） |
| 技术栈限制 | `docs\00_项目宪法.md` | 9-15、21-36、38-52 |
| 设备能力 | `docs\01_设备_S4.md` | 8-20、27-43 |
| 让位判据 | `docs\04_桌面让位判据.md` | 9-17、21-45、192-210 |
| 渲染红线 | `docs\02_架构.md` | 107-119、64-67 |
| 遥控台 | `docs\FEATURES\remote.md` | 45-87、89-155、236-270 |
| 安装角色 | `docs\DECISIONS\ADR-012` | 77-82、99-107 |
| 形态开关 | `core\FeatureGate.java` / `core\CardPrefs.java` | 9-43、76-91 / 679-710 |
| 窗口与触摸 | `feature\OverlayWindow.java` | 25-28、42-54、64-251 |
| 无障碍配置 | `res\xml\a11y_card_service.xml` / `a11y_remote_service.xml` | 22、37-38 / 8、15-17 |
| 宿主组件协议 | `core\widget-api\...\WidgetApi.kt` | 17-48、66-109 |
| 宿主无障碍 | `app\src\main\res\xml\accessibility_service.xml` / `service\...\OverlayAccessibilityService.kt` | 3-9 / 24-30、113 |
| 构建 | `weread-widget\tools\build.sh` | 1-10、71-76、116-120、221-222 |
| 发布 | `weread-widget\.github\workflows\release.yml` | 22-32、89-148、174-185 |
| 流程纪律 | `docs\10_开发专家交接手册（代际版）.md` | 71-79、81-90 |
| 团队纪律 | `docs\15_团队工作纪律_T条与K条.md` | 26-33、39-44 |
| 回归 | `docs\12_交接_2026-10-10_TASK-086断点与待拍板.md` | 39 |

一处与派单口径不符、以配置文件为准的事实：无障碍服务 `canRetrieveWindowContent` 现为 `true`（`res\xml\a11y_card_service.xml:38`），并非派单中所述的 `false`；`docs\01_设备_S4.md:53` 仍保留旧表述，属文档滞后。

许可与复用：`weread-widget\LICENSE` 为 MIT，允许复制与修改，但需保留版权声明。宿主 `docs\04-identity-removal.md:39` 的既定原则是按规格重写、不搬运字节码与资源，两者并不冲突：行为约定可以照搬，代码与布局文件按宿主规范重写，MIT 声明在移植少量工具函数时保留即可。
