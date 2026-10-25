# UX151 P4 自绘状态栏下滑通知面板

用户2026-10-25授权“进行下一步”，本轮跨午夜于2026-10-26完成。[任务](../../task/TASK-UX-151-p4-notification-gesture.md)承接[UX150诊断](2026-10-25-ux150-p4-statusbar-diagnosis.md)。本次交付是无障碍动作绕过隐藏栏边缘手势路径，不是厂商ROM修复。

## 最终行为

- P4现有无障碍服务实际连接后：隐藏真实系统状态栏，恢复应用自绘时间、电量和充电标识，保留刷新按钮；自绘栏下滑超过门限时通过GLOBAL_ACTION_NOTIFICATIONS展开系统通知面板。
- 面板关闭后恢复隐藏栏，没有常驻79px真实系统栏。保留原12px物理安全区；自绘栏恢复原令牌高度，不把系统栏高度计入内容区。
- 未启用、服务断开或动作返回失败时：恢复真实系统栏和去重指示，避免留下无通知入口的全屏窗口。
- 仅P4机型路径生效。S4原窗口行为保持，本轮未安装或操作S4。
- 使用既有OverlayAccessibilityService，未新增服务、权限、设置页面或持久偏好。用户沿用系统无障碍设置开关。

实现入口：core:common/NotificationPanelAccess持有实际连接状态与系统动作；现有服务负责connect/unbind/destroy；MainActivity消费状态同步窗口与状态栏；StatusBarView处理定向下滑并取消原点击。短按继续传递原刷新回调。最终源码没有获焦重隐藏、延时轮询或P4旧FLAG_FULLSCREEN混用。

## 工程验证

源码基线master/f025b18，接手476项既有差异，保留。版本仍为1.0.31/51。

- `check :app:assembleDebug :app:assembleRelease --offline --console=plain --max-workers=2`通过，见`_analysis/verify/ux151-notification-gesture/check-build-2.log`。
- Debug、Release各1018项测试，0失败、0错误、1既有跳过；新增4项覆盖未连接、断开、系统拒绝后回退与异常回退。
- 首轮门禁发现StatusBarView返回数量超出detekt规则，已重组消费状态，未扩充基线；最终全门禁通过。
- 最终release为2205411字节，SHA256 `55d1081249c2ff7ca1a8573e69f45488b1a24299a5471843a6e803c9697f6c21`；P4设备摘要相同。证据`build-summary.json`、`final-installed.json`。
- 文档链接检查263份、0错误、2条既有历史警告；受影响文件CRLF兼容空白检查、PowerShell语法解析通过，脚本UTF-8 BOM保留。

## 设备验证

P4 serial P4L202610130006，Android11/SDK30；横屏768×552、竖屏552×768。

| 场景 | 实际结果 | 证据 |
| --- | --- | --- |
| 最终debug横屏，起点y=30px | 连续3次：隐藏栏→下滑进入NotificationShade→BACK回MainActivity且statusBars visible=false | final-debug-landscape.json |
| 最终release竖屏，起点y=20px | 连续3次同上，服务始终绑定、UiAutomation不存在 | final-release-portrait.json |
| 最终release横屏，起点y=30px | 连续3次同上 | final-release-landscape.json |
| 系统设置关闭服务 | 真实栏恢复，原生下滑仍进入NotificationShade | disabled-fallback.txt、disabled-native-open.txt |
| 系统设置重新启用服务 | 服务绑定后切回隐藏栏，自绘指示恢复 | final-installed.json及最终release循环 |
| 强停并覆盖安装后的冷启动 | 本机系统撤销服务启用状态；应用显示真实栏，系统设置重新启用后恢复新行为 | 本轮设备操作与任务阶段记录；不声称强停后服务一定自动重启 |
| 实际画面 | release横竖自绘时间、电量可见，真实栏不常驻，原内容布局与壁纸保留 | 私有release-portrait、final-release-landscape截图，已人工查看 |

原始技术文件均在`_analysis/verify/ux151-notification-gesture/`。每个循环操作前核对MainActivity焦点、系统栏隐藏、服务已绑定且没有UiAutomation；失败前置条件不计入通过次数。手势由项目android-ui.ps1执行；边缘动作直接读取当前逻辑尺寸/旋转，以ADB注入。真实盖板和手指可达性仍需用户实机确认。

## 排除的测试干扰

Android CLI的持久`com.android.cli.interact.instrumentation`会使用UiAutomation抑制其他无障碍服务。实际观测为Enabled services包含十雨、Bound services空、Ui Automation存在，触发应用真实栏回退。早期CLI选择器下滑/按返回后的“残留系统栏”结果因此不能作为产品故障结论。

释放该工具进程后，Bound services恢复，纯ADB输入闭环成功。曾尝试的获焦hide、post hide与追加FULLSCREEN均已删除；不保留用来补偿测试干扰的代码。项目脚本key改为纯ADB keyevent，避免不必要的UiAutomation。标准View布局/点击仍可用于系统设置，但验证无障碍行为前须释放该工具进程并检查服务状态。截图annotate本轮未抑制服务。

## 恢复与边界

- 原APK与私有数据备份保存在仓库外临时目录，位置记录于private-location.txt。未清除应用数据。
- 只临时改变desktop.orientation，已按字段恢复原LANDSCAPE。验收中选记游标变化，保存当前值后恢复原选书与选记；最终debug审计`configChanges=[]`、`intentionalChanges=[]`、bookChoiceEqual=true、noteSelectionEqual=true。新阅读缓存与同步状态保留；未整包回滚数据。
- 最终release安装后以APK摘要、窗口与实际画面验证；不可run-as，不冒充再次读取release私有偏好。
- 最终当前MainActivity内容框与可见框均为[0,0][768,552]、content inset为0、请求状态栏可见性为false，见final-content-frame.txt；系统stable inset仍为79px不代表实际占用，旧任务快照也不能替代当前窗口。
- 本次授权启用的十雨无障碍服务保留开启，供已交付手势使用。最终MainActivity前台、rotation=1、statusBars visible=false、服务绑定、无UiAutomation。
- 点击刷新路径保留且源码核对；本轮未对最终release单独采样闪烁过程，也未独立实测横向/向上误触、强杀自动重连、物理盖板、S4或压力测试。无障碍动作拒绝/异常回退为JVM测试，设备验证覆盖实际服务关闭回退。
- P4与构建资源已释放，无后台任务；未升版、提交、推送、发布或新建休眠调度。
