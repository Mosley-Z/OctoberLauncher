# 26 双设备适配检查报告

本文记录 V1.0.5 内化改动在两种屏幕尺寸上的检查、反思对照与适配结果。

## 一 设备与检查方法

| 项 | S4 | P4 |
| --- | --- | --- |
| 序列号 | SA4B03CN2608081096X | P4L202610130006 |
| 物理分辨率 | 480×800 | 552×768 |
| 逻辑尺寸 | 约 350×584 dp，219 dpi | 约 420×585 dp，210 dpi |
| 面板 | 电子墨水 | 电子墨水（EinkFull） |
| 系统版本 | 已安装微读墨记候选包与十雨数据 | 全新安装，无微读数据 |

检查方式为逐界面截图比对：先在 S4 上截取全部改动界面，再在 P4 上按同一路径逐界面截图，记录差异项后按影响面归纳为布局问题与功能问题两类。

## 二 发现问题

### 布局问题

一、负一屏分区抬头左右贴边。`DeskSectionStyle.buildHeader` 未设置内边距，抬头标题从屏幕最左侧起排，右侧操作按钮（刷新、壁纸）贴到右边缘，P4 上「壁纸」二字右半被屏幕裁切。S4 上因屏宽更窄，同样存在贴边，只是裁切不明显。

二、负一屏空态导入按钮贴左边。`WereadDeskSectionView.guideRow` 未设置内边距，「导入微读墨记数据」按钮左边缘与屏幕对齐。

三、内置微读墨记页周期行右侧「刷新」贴边。同属抬头行未留白，P4 上刷新图标部分出屏。

四、负一屏设置态「完成」与模块行右侧下移箭头贴边，P4 上箭头右半被裁。

### 功能问题

五、设置首页缺少微读墨记入口。`WereadAppSpecs.render` 挂在桌面偏好页底部，而用户路径为设置首页；`SettingsHomeScreen.cards()` 的十张卡片中没有微读墨记。

六、P4 上默认桌面未设定，按 HOME 弹出桌面选择器；`resolve-activity` 返回 `android/com.android.internal.app.ResolverActivity`，属设备初始状态，需用户手动选择「十雨桌面」为默认。

七、P4 上悬浮窗权限被系统关闭（「显示在其他应用的上层」提示系统已关闭该功能），与十雨功能无关，但会让权限页该项显示为不可授权。

## 三 反思对照

对照 `docs/design/24-weread-spec-reference.md` 第四与第五节的内核规格，差异集中在「留白」这一类。微读原版的分区绘制全部在 Canvas 内完成，几何由内核 `DeskStyle` 常量乘以排版单位得出，因此内边距天然随屏幕尺寸变化；十雨把顶栏、抬头、空态引导改为原生 View 后，沿用了 Canvas 侧的常量却没有补上对应的外边距，形成贴边。

内边距的统一定义已经存在于 `DeskSectionStyle.paddingXPx(widthPx)`，等于分区宽度乘 `PAD_X_RATIO_DESK` 且不小于一个间距单位。适配的做法是把所有原生 View 组成的抬头与引导区都改为调用这一函数，而不是各自写固定像素。

## 四 已完成的适配

| 改动 | 文件 | 说明 |
| --- | --- | --- |
| 分区抬头内边距 | `feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/ui/negative/DeskSectionStyle.kt` | `buildHeader` 增加左右 `paddingXPx(屏幕宽度)` |
| 空态引导内边距 | `feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/ui/negative/WereadDeskSectionView.kt` | `guideRow` 增加左右同值内边距 |
| 设置首页入口 | `feature/settings/src/main/java/com/shuiyu/octoberrain/feature/settings/SettingsHomeScreen.kt` | `cards()` 首位新增「微读墨记」卡片，跳转 `Destination.WereadApp` |
| 桌面偏好页入口 | `feature/settings/src/main/java/com/shuiyu/octoberrain/feature/settings/LauncherPrefsScreen.kt` | 新增「微读墨记」分组，作为次入口 |
| 设置态模块行间距 | `feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/ui/negative/DeskSettingsView.kt` | 上移与下移按钮水平内边距改为半个间距单位，模块名改为单行省略，避免右侧箭头被挤出 |

验证截图：`_analysis/verify/20261020-internalized/p4-25-settings-entry.png`（设置首页新卡片）、`p4-27-negative-padding.png`（负一屏抬头留白）、`p4-35-app-tabs.png`（内置页四页签常显）。

## 五 设备初始状态的处置

一、默认桌面：两台设备均执行 `adb shell cmd package set-home-activity com.shuiyu.octoberrain/.MainActivity`，`resolve-activity` 复核返回 `com.shuiyu.octoberrain/.MainActivity` 且 `isDefault=true`，按 HOME 不再弹桌面选择器。

二、悬浮窗权限：两台设备执行 `adb shell appops set com.shuiyu.octoberrain SYSTEM_ALERT_WINDOW allow`，`appops get` 复核返回 `SYSTEM_ALERT_WINDOW: allow`。P4 系统设置页仍显示「系统已关闭此功能」，属该机系统策略对第三方侧载应用的展示限制，`appops` 授权对十雨的托盘与浮层生效。

三、墨水屏休眠：两台设备均执行 `adb shell svc power stayon true`，走查期间保持亮屏，截图不再出现全黑帧。

## 六 待处理项

一、八项外观设置（纸色、字号、署明、月历样式、切换动画、尺寸档、榜单范围、成就目标）当前只写入 `filesDir/weread/kernel_prefs.json`，桌面卡片即时响应需要新增只读外观层；已新建 `core/common/src/main/java/com/shuiyu/octoberrain/core/common/WereadAppearance.kt`，组件侧接入由 V1.0.7 完成。

二、周期选择器可切换范围只覆盖本地已缓冲的周期，未同步的历史周期不出现在选择器内；V1.0.7 把同步范围扩到最近十二周、十二月、三年并支持按需拉取单期。

## 七 走查脚本

新增 `scripts/check-devices.ps1`，按屏幕宽高比例换算坐标，对任一设备依次截取桌面首页、第二页图标、负一屏列表态与内核分区、负一屏设置态、设置首页、桌面偏好、内置页阅读四段、内置页设置与实验室与待办、组件选择器共十五屏，输出目录内附 `index.md` 记录尺寸、密度与版本。语法校验零错误，双机走查用它复跑以消除手工坐标偏差。

## 八 结论

P4 与 S4 的差异集中在原生 View 的外边距与设置入口位置，内核 Canvas 分区在两种尺寸上排版一致。设备初始状态的三项（默认桌面、悬浮窗授权、休眠）已用 adb 处置完毕，两机后续检查都在同一初始条件下进行。
## 九 1.0.7 复检

在 1.0.7 上重跑两台设备。S4（480×800）验证负一屏五分区、内置页四页签，并连续进出后无 ANR；P4（552×768）验证相同路径，负一屏抬头与导入按钮留有内边距、内置页四页签与紧凑周期行无裁切、空态文案居中。

截图归档：S4 见 `_analysis/verify/20261020-anrfix/` 与 `_analysis/verify/20261020-app/`；P4 见 `_analysis/verify/p4-1.0.7/`（`p4-01-home.png`、`P4L202610130006-run1/03-negative.png`、`p4-10-settings.png`、`p4-11-app.png`）。

本轮同时修掉三处 ANR：负一屏分区测量失败后每帧重起线程、分区构造与首帧测量叠加、书架分区主线程读盘；组件侧内核渲染期回调重绘形成无限重绘与绘制线程内同步绑定内核。修复后主线程占用由 89% 以上降至 18% 以内，帧恢复正常。

周期选择器在 1.0.7 覆盖到最近十二周、十二月与三年共二十八期，目标期无缓存时触发按需同步并显示「同步中…」。
## 十 微读设置页布局对照

微读墨记 1.3.2-beta 装于 P4 实机对照，截图在 `_analysis/verify/p4-1.0.7/`：`orig-settings-init.png`、`orig-settings-init2.png`、`orig-settings-init3.png`、`orig-settings-custom.png`、`orig-custom-2.png`。

原版设置页只有两个页签，文案固定「初始化」「自定义」。源码侧对应 `app/src/main/res/layout/settings_layout.xml` 的 `page_init` 与 `page_custom`，页签标题资源为 `tab_init`、`tab_custom`；`page_lab` 属主页面顶部主导航，不在设置页内。

初始化页自上而下六段：本机角色单选与说明、微信读书 API Key 输入与粘贴与保存并返回与测试连接与获取说明、桌面卡片无障碍按钮、桌面卡片总开关与重新同步按钮、怎么用、版本与更新（版本号、更新通道、云端备用更新源、检查更新）。

自定义页自上而下六段：仅一页模式、启用墨台、刷新绑定三项、阅读成就提示（周与月各四态加自定义输入）、本记导出图（纸张底纹三档、字号三档、署名与日期）、书籍排名只统计书架。

十雨 1.0.7 的内置页设置页为单页纵向四段（显示、卡片、成就与联动、版本行），与上述两页签结构不符，差异集中在三处：缺页签分层、条目归属错位（纸色与字号属自定义页而非显示组）、缺本机角色与重新同步与无障碍三项入口。1.0.8 按上述顺序重排为两页签，截图在 `_analysis/verify/20261020-1.0.8/` 与 `_analysis/verify/20261020-period/`。