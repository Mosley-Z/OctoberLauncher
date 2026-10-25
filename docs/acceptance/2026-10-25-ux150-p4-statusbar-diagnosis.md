# UX150 P4 隐藏状态栏手势诊断

任务：[UX150](../../task/TASK-UX-150-p4-hidden-status-bar.md)。2026-10-25用户授权按研究建议执行最小实验。本轮是诊断，不是隐藏栏修复交付；UX149的79px真实栏回退继续有效。

## 环境与隔离

- P4 serial P4L202610130006，Android 11/SDK30；逻辑横屏768×552、竖屏552×768，210dpi。
- 源码基线master/f025b18，接手472项既有差异，未回退。正式应用源码、Manifest、配置与安装包均未修改。
- 独立包com.shuiyu.statusbarprobe：targetSdk30，普通Activity，无HOME声明、网络权限或用户存储访问。原生TextView白底；隐藏仅发生在窗口配置时，没有焦点/恢复回调重隐藏。
- probe Java源码、Manifest、构建脚本与原始技术结果在`_analysis/verify/ux150-statusbar/`。现代模式APK SHA256 a30761a6cc0f392c12157ad27ac96407d60195f4ecfd174bfe0490d2b06dc61d；增加旧式分支后APK SHA256 14004ef4c98b41c4328ba42d3b5b66707cc6651f004e24956afc12bc55111d10。
- 使用已配置JDK17、SDK35 javac/d8/aapt/zipalign/apksigner构建诊断包成功；未运行整包Gradle门禁，不冒充正式应用交付。

## 实验与实际结果

每组冷启动诊断Activity，核实起始焦点属于ProbeActivity，再从逻辑屏幕中央x、指定顶部y向下滑到80%屏高，350ms；连续两次，分别读取焦点和状态栏Insets源。系统边缘坐标通过项目android-ui脚本新增edge-swipe动作执行，每次读取实时尺寸与旋转，不沿用截图坐标。

| 条件 | 组数 | 实际结果 |
| --- | ---: | --- |
| 现代BEHAVIOR_SHOW_BARS_BY_SWIPE，fits=true，横竖各y=1/4/8/12/24px | 10 | 两次滑动均停留ProbeActivity；statusBars源visible=false |
| 现代BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE，同样方向/起点 | 10 | 同上 |
| 纯旧式FULLSCREEN+LAYOUT_STABLE+LAYOUT_FULLSCREEN+IMMERSIVE，横竖y=1 | 2 | 同上；系统UI标志读回0x8d0c |
| 纯旧式FULLSCREEN+LAYOUT_STABLE+LAYOUT_FULLSCREEN+IMMERSIVE_STICKY，横竖y=1 | 2 | 同上；系统UI标志读回0x950c |
| 现代两种behavior，fits=false，横竖y=1 | 4 | 同上 |
| 显示真实系统栏，横竖y=1，作为正对照 | 2 | 第一段即进入NotificationShade |

共30组有效实验：28组隐藏栏未展开面板，2组可见栏正对照成功。文件`mode{模式}-orientation{0横/1竖}-fits{True/False}.json`保存每组before/first/second状态；`probe-events.log`保存独立包的配置、焦点和触摸技术日志。现代behavior读回为1/2，触摸日志确认原始y起点；隐藏模式最终实际画面为全屏白底诊断内容，无可见系统栏（`_ui-out/ux150-hidden-probe/screen-annotated.png`）。

初次正对照后，通知面板未自动收起，污染了接下来的竖屏正对照与旧式首次结果。发现before焦点不属于ProbeActivity后，两项均作废并重跑，矩阵增加预先collapse与焦点检查；上表与JSON只采用有效重跑结果，不把遗留NotificationShade当成成功。

补充：隐藏栏时执行shell `cmd statusbar expand-notifications`成功进入NotificationShade，随后collapse返回ProbeActivity且statusBars源visible=false；见`shell-expand-hidden.txt`、`shell-collapse-hidden.txt`。这是shell权限下的系统命令结果，不证明普通应用、反射或无障碍接口可用。全局policy_control读回null。

## 结论与下一步

1. 该现象可在没有HOME身份、没有十雨布局及重复隐藏回调的最小Activity复现。十雨HOME身份与业务生命周期不是复现必要条件。
2. 在本轮远程输入条件下，移动起点、现代两种behavior、纯旧式沉浸标志和decorFits切换均未解决；不再将这些参数组合当作已找到的修复。
3. 系统可展开通知面板，失败集中在隐藏栏下的边缘手势唤出路径。尚未定位到P4具体WindowManager/SystemUI代码，不能据此宣称已证明ROM根因。
4. 生产方案保持UX149真实栏回退。下一候选是自绘栏下滑时通过无障碍GLOBAL_ACTION_NOTIFICATIONS展开面板。项目已有OverlayAccessibilityService，可考虑复用而非新增服务；本轮未添加调用、未启用权限、未改变交互，仍需授权实施和P4验证。

## 恢复与验证边界

- 原正式APK备份在私有临时目录（位置记录于证据目录private-location.txt），未覆盖安装，因此不需要回装或导入备份。
- 诊断包已卸载，原MainActivity回到前台、rotation=1、真实系统栏79px可见；正式APK SHA256仍为e037225772f4bf023901fe706242754fecd930ec509b0c601fcc9178837d3cd6，见restored.json。
- 未更改默认HOME、桌面配置、选书/选记或系统旋转设置；未读取/复制用户私有数据。诊断包安装/卸载会触发系统应用列表刷新，不宣称私有偏好逐字段审计通过。
- 最终恢复截图向仓库外目录输出两次均被CLI文件访问拒绝；恢复依据为窗口、旋转、栏状态和APK摘要，没有宣称最终桌面截图验收成功。
- P4已释放，无活动构建或诊断进程；S4未操作。真实手指、盖板触达、其他全屏应用、厂商实现和无障碍路径未验证。采样不能排除未捕获的极短瞬时系统栏动画；通知面板未展开的结论有焦点证据。
- 保留的工程改动仅scripts/android-ui.ps1系统边缘诊断动作及任务/验收/交接记录；原有脚本改动保留。未升版、提交、推送或发布。
- 文档检查260份、40条迁移、27张检查器覆盖任务，0错误、2条既有历史源码警告；PowerShell语法解析通过，脚本UTF-8 BOM保留。默认diff检查将CRLF报告为尾空白，按`core.whitespace=cr-at-eol`复核受影响已跟踪文件通过，没有批量改写既有换行。
