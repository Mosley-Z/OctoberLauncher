# 阅读桌面：横屏、厂商壁纸可行性与本记分析

- 日期/执行者：2026-10-23 / Codex
- 任务：[UX001](../../task/TASK-UX-001-reading-desktop-proposal.md)
- 源码基线：master / 84a035e；已有gradle.properties与分析/网站未提交文件保留。本轮仅设计、文档及隔离探针，无业务源码变更。
- 产品包：双机已安装versionName=1.0.28、versionCode=48（dumpsys package实读）；本轮不重建、不替换产品APK。
- 工具：toolchain.path指定JDK17.0.20_12、SDK35 build-tools；adb；scripts/android-ui.ps1；本地javap与APK字符串/Manifest读取。未使用REA工具输出，不把字符串存在当功能运行通过。
- 原始技术证据：`_analysis/verify/desktop-ux/`（本轮局部忽略），标准UI节点与标注截图在`_ui-out/`；不采集用户Key、PIN、偏好全量或笔记正文。

## 1. 设备与恢复基线

| 项 | S4 | P4 |
| --- | --- | --- |
| serial | SA4B03CN2608081096X | P4L202610130006 |
| Android API | 30 | 30 |
| 原生尺寸 / density | 480×800 / 219 | 552×768 / 210 |
| 初始auto / user_rotation / display rotation | 1 / 0 / 0 | 1 / 0 / 0 |
| 窗口旋转策略 | USER_ROTATION_FREE、mFixedToUserRotation=false | USER_ROTATION_FREE、mFixedToUserRotation=false |
| 初始前台 | 十雨MainActivity | 十雨MainActivity |

设备由Codex串行操作。仅安装自建包`com.shuiyu.octoberrain.uxprobe`，不包含账户、网络或笔记读取逻辑。独立Activity请求方向并在onSizeChanged打印请求/rotation/宽高，同时调用WallpaperManager能力查询。源码/APK在本地证据目录；产品配置未修改。

## 2. 功能5：真实方向请求测试

方法：分别以`am start -W -n com.shuiyu.octoberrain.uxprobe/.ProbeActivity --ei orientation 0`和`--ei orientation 8`启动，每次先停止探针。0为LANDSCAPE、8为REVERSE_LANDSCAPE；这些不是rotation数值。以最终onSizeChanged、窗口rotation和合成画面相互对照。启动时过渡竖屏不作为最终结论。

| ID | 设备/请求 | 最终观测 | 判定 |
| --- | --- | --- | --- |
| R1 | S4 LANDSCAPE | rotation=1；800×428内容区 | 支持正横屏 |
| R2 | S4 REVERSE_LANDSCAPE | rotation=3；800×428内容区 | 支持反横屏 |
| R3 | P4 LANDSCAPE | rotation=1；768×473内容区 | 支持正横屏 |
| R4 | P4 REVERSE_LANDSCAPE | rotation=3；768×473内容区 | 支持反横屏 |

原始日志：`s4-probe.log`、`p4-probe.log`、`p4-probe-final.log`。P4反横屏标注图（本地证据：`_ui-out/20261023-031422/screen-annotated.png`）显示合成左右分区及request=8/rotation=3；Android CLI截图为原生面板552×768，画面内容已转向，不能仅用PNG宽高判横屏失败。没有用wm size覆写代替真实旋转。

尺寸差异来自系统状态/导航栏，未来布局必须按实际insets和onSizeChanged计算，不硬编码800×480的全屏内容区。

结论：两机方向请求层面可行。当前Home仍按上下布局和单套columns/rows计算，MainActivity虽声明orientation/screenSize的configChanges，仍需明确重建Home几何、壁纸裁切、弹层/触点/页码与当前书籍锚点。左组件右应用是待实现设计，不在本轮判为通过。S4已安装第三方Rotation应用，本次没有改其设置；目标release实现后须补与其共存和实体EPD验证。

## 3. 功能8：锁屏、待机与关机图片探索

### 3.1 标准接口能力查询

普通自建探针（target30，仅声明SET_WALLPAPER）实测：

| 项 | S4 | P4 |
| --- | --- | --- |
| isWallpaperSupported | true | true |
| isSetWallpaperAllowed | true | true |
| system wallpaperId | 5 | 2 |
| lock wallpaperId | -1 | -1 |

`-1`不代表“不支持锁屏”，只表示本次没有对应独立壁纸ID；允许状态也不证明setBitmap/屏幕关闭后的实际效果。产品target35且当前使用WallpaperView内部桌面壁纸，没有SET_WALLPAPER声明；新增系统锁屏能力需补Manifest并测最终release。

查询的几个常见Eink/EPD类在探针中ClassNotFound；这仅说明这些候选类不存在，不证明不存在其他厂商API。

### 3.2 S4

- 系统设置位于`/system_ext/priv-app/YXTSettings/YXTSettings.apk`；当前技术字段`standby_wallpaper_type=2`、`power_off_wallpaper_type=3`。
- UI真实走`android.settings.DISPLAY_SETTINGS`→“待机关机图片”→“选择壁纸来源”。入口存在，但来源列表为空；`cmd package query-activities --brief -a android.intent.action.SET_WALLPAPER`返回No activities found。
- 节点证据：显示页（本地证据：`_ui-out/20261023-031426/layout.json`）、壁纸来源页（本地证据：`_ui-out/20261023-031448/layout.json`）。
- 系统Settings及ELauncher字符串含`/data/misc/standby.jpg`；文件当前owner=system/group=system、权限0644。`/data/misc`目录权限为`drwxrwx--t system misc`，普通应用不具有其写权限。本轮没有root或尝试覆盖文件。
- 结论：厂商有能力，当前设备缺已验证的图片选择处理者。可先导出匹配尺寸图片并引导系统入口；若要在十雨内一键完成，需继续确认厂商组件/受支持接口，不能拿另一机图库APK直接宣称兼容。

### 3.3 P4

- 系统显示页有“壁纸”→“选择壁纸来源”→“图库”；标准SET_WALLPAPER解析到`com.wetao.gallery/.MainActivity`。
- 厂商WeGallery位于`/system/app/WeGallery/WeGallery.apk`；APK字符串包含`/data/misc/standby.jpg`、`/data/misc/poweroff.jpg`及对应设置动作；当前standby文件不存在，同名S4配置字段为空。不能因键名缺失推断P4不支持。
- 推送本轮合成的`DSH-UX-Probe.png`，以ACTION_VIEW/image/png打开导出的`com.wetao.gallery/.ImageShowActivity`，通过标准View的`id=com.wetao.gallery:id/cb_menu`打开菜单。
- 真实菜单同时出现“设为待机图片”“设置关机画面”“恢复系统图片”；证据：节点（本地证据：`_ui-out/20261023-032753/layout.json`）、合成图片上的标注截图（本地证据：`_ui-out/20261023-032820/screen-annotated.png`）。没有打开或截图其他用户图片。
- 本次测试用ADB/file URI证明系统组件与菜单入口可达。十雨实际转交需使用有读授权的content URI，并补厂商是否接受content URI/是否要求本地路径的最终产品验证；不能把shell file URI视为产品链路已接通。
- 结论：P4“导出→交系统图库→用户选择待机/关机”路径具有具体可行性；直接写私有路径不适用于普通十雨App。未执行最后应用动作，未实际休眠/关机，不宣称画面写入与断电效果通过。

### 3.4 建议入口与后续门槛

| 能力 | 产品建议 | 尚需验证 |
| --- | --- | --- |
| 桌面壁纸 | 继续使用十雨内部WallpaperView，纳入主题 | 主题资源迁移、方向裁切 |
| Android锁屏 | 单独可选能力，不叫“休眠壁纸” | target35权限/写入/恢复/实际锁屏 |
| P4待机/关机图 | 优先导出适配图并交已安装系统图库 | content URI实际接线、最后应用、恢复及熄屏/断电观测 |
| S4待机/关机图 | 导出适配图与系统引导，明确当前缺处理者 | 厂商接口或选择组件可用性 |

不借助“退出前把桌面画成壁纸”模拟待机/关机；系统可在进程退出后覆盖屏幕，应用也不能保证关机回调完成。

## 4. 本记与设计验证

- 主因静态证据：[方案第7节](../design/37-reading-desktop-proposal.md#7-本记展开失效证据与两种方案)。实际内核jar SHA256=`ef586cc8b1343a0c78f16fb972ff11e24ec8775e57638a5ab101663518ea7fa6`。
- `javap -p -c`输出在`card-state.txt`、`card-layout.txt`、`card-renderer.txt`。CardRenderer.drawNoteBody的isNoteExpanded分支与setNoteViewH仍受当前帧高度计算约束；HomeView固定clip与后绘应用是源码证据。未读取真实笔记内容做截图对比。
- 交付[具体方案](../design/37-reading-desktop-proposal.md)与[设计稿](../../design/reading-desktop/index.html)，9个竖屏状态×480×800/552×768、2张横屏图和总览。原型本记收展可操作，扩大正文视区；它不等于Android修复已实现。
- 本轮未运行Gradle；文档/设计与隔离探针不要求产品整包构建。最终文档检查结果见任务卡。

## 5. 恢复、限制与工具问题

- 两机探针均已卸载；P4合成PNG已删除；没有修改用户壁纸、厂商图片、Key/偏好或应用数据，也没有睡眠/关机。
- 探针退出后系统暂留rotation=3，已用`cmd window set-user-rotation lock 0`再`free 0`恢复：两机auto=1、user_rotation=0、display rotation=0、USER_ROTATION_FREE、前台十雨MainActivity；原生wm size/density未改变。S4壁纸type仍2/3。
- 不停止既有ADB/Java守护进程；无Gradle任务，设备资源已释放。
- 普通沙箱ADB无法创建用户目录；提权读取/操作后成功。Android CLI输出写`_analysis`时Java AccessDenied，改用脚本默认`_ui-out`目录后正常；不重复改业务以绕过工具问题。
- 最初一次含`$`的系统Activity通过远端shell被展开，落到不可导出的Settings并失败；改用公开DISPLAY_SETTINGS action，成功通过真实UI入口继续。不把该失败误记为厂商壁纸能力不可用。
- 物理EPD/P95、最终产品横屏、待机关机图写入/断电、content URI桥接及Android大字体为待实施/待验范围。
