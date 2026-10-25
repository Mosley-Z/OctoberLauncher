# UX152 P4 通知面板返回布局稳定

用户2026-10-26反馈UX151关闭系统通知面板后，真实栏短暂占用79px、桌面压缩后恢复，授权修复过渡。[任务](../../task/TASK-UX-152-p4-statusbar-transition.md)承接[UX151](2026-10-26-ux151-p4-notification-gesture.md)。

## 改动

MainActivity仅在P4且无障碍入口实际连接的隐藏栏分支中，显式关闭decor fitting并保留FULLSCREEN窗口标志。桌面View布局不再跟随面板关闭期间的状态栏inset变化。

服务未连接/失败分支继续清除FULLSCREEN、开启decor fitting并显示真实栏，恢复正常避让。S4路径未改变。无新配置、权限、页面或依赖；未加延时隐藏、遮罩或轮询。正式源码增量仅MainActivity窗口分支三行。

## 过渡证据与边界

证据根：`_analysis/verify/ux152-statusbar-transition/`。设备P4L202610130006，Android11/SDK30；实际横屏768×552、竖屏552×768。

- 原包窗口采样before-window-trace.json记录内容框在`[0,0][768,552]`与`[0,79][768,552]`间变化，与用户反馈的79px避让相符。
- 修复后厂商WindowManager中间内容框仍可能报79px，但这不代表实际View缩小。必须同时核对Activity View Hierarchy的android:id/content边界。
- 最终release横屏final-release-landscape.json：展开前、展开、关闭期间10次采样与返回后，实际View内容框均为`0,0-768,552`。
- 最终release竖屏final-release-portrait.json：相同阶段的实际View内容框均为`0,0-552,768`。
- 每方向另2次完整展开/关闭通过，加上尺寸采样循环，最终release每方向共3次；关闭后MainActivity前台、statusBars visible=false、服务已绑定且没有UiAutomation。见final-release-*-cycles.json。
- 最终release服务未启用时，真实栏回退的View内容框为`0,79-552,768`，Bound/Enabled services均空；启用后恢复全屏，见final-release-fallback.json。这是有意保留的可用回退。
- 尝试用CLI布局采样模拟断开时，本次服务仍绑定，因此那组文件不计入断开验收；以上回退结论采用最终release实际未启用服务的记录。

系统screenrecord只输出绿帧，不能作视觉通过证据；短时screencap约0.8秒一帧，也不足以排除更短瞬时画面。因此正式结论是实际View布局在采样阶段保持完整尺寸，结合不随状态栏inset布局的窗口策略消除79px重排路径；不声称完成了每一显示帧或墨水屏光学残影的验收。横竖稳定态实际截图已保存私有目录并查看，物理手指收起瞬间观感仍由用户实机确认。

临时burst截图命令已从共享android-ui.ps1撤回；诊断快照留在证据目录android-ui-burst-diagnostic.ps1。此前既有脚本修改保留。

## 工程验证与安装

- master/f025b18及接手482项既有差异保留。`check :app:assembleDebug :app:assembleRelease --offline --console=plain --max-workers=2`通过，check-build.log。
- Debug、Release各1018项，0失败、0错误、1既有跳过；本次无新增JVM测试，用设备过渡与回退验证窗口变化。
- Release仍1.0.31/51，2205403字节，SHA256 `6185500aae8afa494dfed7afd72aa922c3cae5490dd08e866e66c17e9f67fefa`。P4安装摘要匹配，见build-summary.json、final-installed.json。

## 状态与恢复

原APK和数据备份保存在仓库外私有临时目录，位置在private-location.txt。仅临时调整方向；已恢复原LANDSCAPE、配置、选书和选记。最终debug审计configChanges=[]、intentionalChanges=[]、kernelChangedGroups={}、bookChoiceEqual=true、noteSelectionEqual=true；随后安装最终release，未整体回滚缓存。release不可run-as，不冒充再次读取私有偏好。

服务保持本轮开始时的启用状态。最终P4 MainActivity前台、rotation=1、真实栏隐藏、服务绑定、无UiAutomation。P4与构建资源释放；S4及新连接的Tango设备均未操作。无后台录屏/截图/构建任务，不升版、提交、推送或发布。
