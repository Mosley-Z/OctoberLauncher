# 多机型适配设计

本文以 tomo-launcher 0.5.0 的现有适配实现为起点，给出新工程面向更多安卓机型的适配结构。上游事实依据为 `_analysis\jadx-out` 内源码，行号照录。

## 一、上游适配现状

上游对屏幕的要求是一条硬性准入判定。`data\SupportedScreen.java:13-16` 定义 `WIDTH = 480`、`HEIGHT = 800`、`SLACK = 2`，`matches` 在第 85 行以 `Math.abs(Math.min(width, height) - 480) <= 2 && Math.abs(Math.max(width, height) - 800) <= 2` 判定，取值为 `getRealMetrics` 的真实像素。`LauncherActivity.java:1511-1515` 在 `onCreate` 早期执行该判定，未通过时置 `screenBlocked`、调用 `DesktopDefaults.INSTANCE.openOriginalDesktop`、随后 `finish()`。`data\DesktopDefaults.java:15,17` 把回退目标写死为 `com.wetao.elauncher` 的 `com.wetao.elauncher.ui.main.MainActivity`。也就是说，在 480×800 之外的设备上，上游启动即把 HOME 交还给 OEM 桌面并退出。

其余适配痕迹沿同一画布铺开。`res\values\dimens.xml` 全部以 px 为单位并围绕 800 高度取值，例如 `desktop_grid_height` 356px、`desktop_icon_top` 28px、`coverflow_center_width` 170px、`coverflow_center_height` 256px、`dark_theme_unlock_bar_top` 612px、`dark_theme_unlock_text_width` 408px、`calendar_cell` 34px。圆角 22px 与描边 3px 未进 dimens，而是 `data\IconPack.java:22-27` 的常量。`overlay\OverlayHost.java:413` 以 `SupportedScreen.HEIGHT` 作为悬浮层高度。字号以固定像素写在布局与代码中，系统字体缩放设置对其无效。

## 二、目标形态与断点

480×800 保留为最小支持形态，向上扩展。断点采用窗口宽度尺寸档，与 Android 的 window size class 保持一致：

| 档位 | 可用宽度 | 典型设备 | 布局策略 |
| --- | --- | --- | --- |
| compact | 小于 600dp | 480×800、720×1280 墨水屏，小屏手机 | 单栏，宫格 4 列，组件区单块 |
| medium | 600dp 至 840dp | 824×1648、1072×1448 墨水屏，平板竖屏 | 宫格 5 列，组件区可排两块 |
| expanded | 大于 840dp | 1404×1872、1600×2560 平板横屏 | 双栏：左侧应用宫格与列表，右侧组件与托盘 |

形态判定同时参考短边与长边比例，横屏时把组件区从底部移入右侧栏，托盘改为顶部下拉或侧边抽屉。宫格列数与行数由可用宽高与单元格最小尺寸计算，而不是由 dimens 常量决定。

## 三、尺寸体系重构

以 dp 与 sp 替换 px，dimens 拆成 `values`、`values-sw320dp`、`values-sw480dp`、`values-sw600dp`、`values-sw840dp` 五套。源码只读语义化尺寸，例如 `dimen/icon_size`、`dimen/grid_gap`、`dimen/widget_height`，由 `DeviceProfile` 按档位与最小单元格尺寸给出建议值。

圆角与线宽移入主题体系，作为 `ThemeSpec` 的字段随主题包下发，默认值取 12dp 与 1dp，取代上游的 22px 与 3px 硬编码。

字号采用 sp 并尊重系统字体缩放，大字号档（系统缩放 1.3 以上）触发布局换行策略的降级：标签单行截断改为两行居中，宫格列数减一。上游以 px 写死字号，在系统放大字号后字形溢出，这一项在新工程中属必修。

## 四、设备能力分级

墨水屏与液晶屏在渲染策略上差异明显，必须按能力分支而非按分辨率分支。定义 `DisplayCapability` 三个取值：`einkFast`、`einkFull`、`lcd`。

探测顺序为：先读取厂商属性（`ro.eink.*`、`ro.display.*`、`persist.sys.*` 中与电子墨水相关的键），再检查 `/sys/class/graphics` 下是否存在电子墨水控制节点，最后参考 `Build.MANUFACTURER` 与 `Build.MODEL` 白名单。三者都未命中时按 `lcd` 处理，并在设置页提供手动覆盖项，避免误判把设备锁进错误分支。

`einkFast` 与 `einkFull` 关闭动画、阴影、渐变与模糊，位图统一使用抖动量化，滚动使用分块重绘；`lcd` 放开动画与抗锯齿。渲染层提供统一的刷新提示接口，页切换触发局部刷新标记，定时器按用户配置的间隔（默认 10 分钟）发起全屏重绘。上游以 `hardwareAccelerated=false` 全局关闭硬件加速，新工程改为按能力决定，液晶屏设备保持硬件加速。

## 五、系统与权限差异

HOME 角色申请在 API 29 以上使用 `RoleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)`，API 24 至 28 使用 `Intent.CATEGORY_HOME` 选择器。上游以 `LauncherActivity` 声明 HOME 过滤器，缺少角色申请引导，在部分机型上会出现双桌面并存。

通知监听、无障碍、使用情况访问、写入系统设置、悬浮窗五类授权，沿用上游的 8 类权限模型但按机型可用性屏蔽不可用项。存储权限在 API 30 以上以 `MANAGE_EXTERNAL_STORAGE` 为主、SAF 为辅，`requestLegacyExternalStorage` 只在 API 29 生效。前光控制改用自有签名权限，并对 `com.astraabove.tomo.frontlight` 这类上游专用包解除依赖，改为探测 `/sys/class/backlight` 与厂商 API。

刘海、挖孔与手势导航区域通过 `WindowInsets` 计算安全区，上游以固定全屏加绝对偏移排版，在异形屏上会出现内容压边。折叠屏与多窗口按尺寸档重新布局，`configChanges` 保留全量声明以避免重建，但需在 `onConfigurationChanged` 中重新计算宫格与组件区。

## 六、机型验证矩阵

| 设备类别 | 分辨率 | 密度档 | 关注点 |
| --- | --- | --- | --- |
| 基准墨水屏 | 480×800 | mdpi | 与上游像素级一致，回归基准 |
| 小屏墨水屏 | 720×1280 | hdpi | 宫格列数、字号缩放 |
| 便携墨水屏 | 824×1648 | xhdpi | 单栏到双栏的过渡 |
| 七寸墨水屏 | 1072×1448 | xhdpi | 组件区比例、托盘点位 |
| 十寸墨水屏 | 1404×1872 | xxhdpi | 双栏布局、横屏 |
| 普通手机 | 1080×2400 | xxhdpi | 液晶渲染分支、刘海避让 |
| 平板 | 1600×2560 | xhdpi | 横屏为主，双栏与折叠 |
| 模拟器矩阵 | 各 sw 档位 | 160 至 480dpi | 系统字号 1.3、显示大小 1.15 |

## 七、实施顺序

先以 `DeviceProfile` 与 `SizeClass` 替换 `SupportedScreen`，解除 480×800 硬门禁，并移除对 `com.wetao.elauncher` 的回退依赖。第二步完成 dimens 迁移与布局约束化，把内容从固定画布改为流式约束。第三步做宫格列数动态化与双栏布局。第四步引入能力分级与渲染开关。第五步补齐角色申请与权限引导。第六步接入截图比对与 UIAutomator 冒烟脚本，把上表矩阵纳入每次发布的回归范围。
