# 2026-10-23 沉浸桌面精修 Codex交接

- 用户目标与最新范围变化：删除“十雨”，背景按用户设置，保留自定义图标，其余按参考图建议整改。
- 任务卡/设计/当前状态：[UX116](../../task/TASK-UX-116-immersive-refinement.md) READY_FOR_REVIEW；[验收](../../docs/acceptance/2026-10-23-immersive-refinement.md)为本轮证据权威。
- 源码基线、真实工作树、原有修改与本轮修改：master/f025b18，D:\DSHStation\OctoberLauncher；保留既有差异。本轮沉浸宿主、绘制、轮播几何、统计缓存和帮助；版本1.0.31/51、配置v14不变。
- 已完成及实际证据：最终完整check/debug/release退出0，每变体914例0失败1既有Ignore；S4最终release五封面/切书，P4反向横屏/抽屉通过。最终release SHA256 f8db1e34d7e20528b33dd6b99aa4e389516bb008fd2fb640f1e6d2a6895fdf36，两机实际base.apk相同。
- 未完成/失败/待核实/阻塞解除条件：S4成功恢复后断开ADB，恢复后真实数据截图未取得；不要把合成截图表述为真实阅读统计验证。真实手机LAN/Key与第8项仍沿前批待验/搁置。
- 接口决定及下游消费者：ImmersiveReadingStats可空缓存快照由后台prepare读取、绘制只消费；无额外网络。首尾循环仅沉浸；绘制/命中共用expandedCoverRings几何。短书名仅展示，原始数据不变。
- 共享文件、构建进程、设备占用/恢复：root唯一构建/ADB；两机本轮测试字段合并恢复、原weread目录恢复断言通过；最终release启动桌面，构建/设备操作已结束；私有备份和证据指针不进Git。
- 下一步：用户查看最终S4截图；若需真实数据截图，S4重连后scripts/android-ui.ps1 -Action annotate -Serial SA4B03CN2608081096X，只补截图，不重复测试和改配置。
- 可复用经验及来源：外层封面偏移必须大于内层，否则绘制顺序会遮住外层；见WereadBoardCarouselTest新增可见与命中回归。
- 提交/推送/发布情况：均未执行。

## 需求与证据回读

最终入口不迁移；沉浸时钟/书封/标题/进度/统计/常用栏分层，背景与应用图标仍归用户配置；字段和消费者见上方，未做数据迁移，验收触发与未覆盖见UX116及验收表。
