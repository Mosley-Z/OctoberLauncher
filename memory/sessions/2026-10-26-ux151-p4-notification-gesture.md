# UX151 交接

- 用户授权：复用已有无障碍服务验证自绘栏下滑展开通知；2026-10-26完成最终release交付。
- [任务](../../task/TASK-UX-151-p4-notification-gesture.md)、[验收](../../docs/acceptance/2026-10-26-ux151-p4-notification-gesture.md)为详细事实入口。
- P4服务实际连接→隐藏真实栏并恢复自绘指示；下滑调用GLOBAL_ACTION_NOTIFICATIONS；断开/未开/调用失败→真实栏回退。未加服务、权限、页面或配置迁移。S4未操作。
- 最终check/debug/release通过，各1018项0失败/错误1既有跳过；release2205411字节、1.0.31/51、SHA256 55d1081249c2ff7ca1a8573e69f45488b1a24299a5471843a6e803c9697f6c21，P4安装匹配。
- 最终release横竖各3次隐藏→NotificationShade→关闭恢复隐藏通过。原方向、配置、选书选记恢复，新增缓存保留；服务按本轮授权保留启用。
- Android CLI的持久UiAutomation会抑制服务；无障碍行为验证须先释放com.android.cli.interact.instrumentation并核对Bound services与UiAutomation。脚本key已改为纯ADB。早期受干扰实验不算最终结果。
- 未独立实测最终release刷新闪烁、反向/横向手势、真实手指、S4及压力；不得将源码/JVM结果写成这些实机通过。
- P4/构建释放，无后台任务、不升版提交发布。下一步接收真实手指反馈。
