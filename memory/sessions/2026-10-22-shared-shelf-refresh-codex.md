# FIX005/006 选书与共享刷新交接

- 执行者/集成者：Codex；基线master cfeae53 + 原有未提交差异；不清理、不提交/推送/升版/发布。
- [FIX005](../../task/TASK-FIX-005-shelf-recency.md)、[FIX006](../../task/TASK-FIX-006-shared-shelf-refresh.md)均READY_FOR_REVIEW；用户指定本轮S4，P4延后。当前不是实施中，不重复开发。
- 最终行为：书架标题同行操作保留；微信读书与桌面本书共用filesDir/weread书架/资产和book刷新入口；既有dataVersion通知只触发本地重读。无负一屏独立计时器，无微信读书独立快照读写；本地仍按目录手动快照。右上百分比保留，底部进度条/沙漏及预留区域删除。
- 旧字段shelfRefreshMode/Minutes在v13配置清退，旧auto也不再生效；其他集成设置、本书book_pick和绑定掩码保留。旧shelf-display/weread.json不删除但不再消费/更新。
- 代码范围：content的WereadClient完整解析后排序限量、WereadShelfCandidate、ShelfBookCard/ShelfProgressBadge、ShelfDisplayRepository/ShelfLoader/SharedBookRefresh/SettingsPanel/SnapshotStore；common Models、data ConfigMigration及回归。复用原HostWereadSync，不新增feature横向依赖。
- 验证：完整check及debug/release成功；相关每变体common82/data122/weread-sync54/content65/settings58均0失败。S4两入口更新同一shared savedAt、旧快照时间不变、目标最近第一；最终release设置只剩目录、本地缓存与最近阅读正常、恢复weread/2行。证据和摘要见[验收](../../docs/acceptance/2026-10-22-shared-shelf-refresh.md)。
- 最终S4 release SHA256 c22daf66ec739a165d675ab5358c3df1d6166c036aa03f2193a88fd0546a4207；480×800/font1.0、Books、weread/2行。未留下样本或修改本书选择。构建/ADB操作已释放，下轮重新核实设备与占用。
- 曾被额度导致的自动审批复核失败打断；用户继续后完成剩余本地回归。设备休眠时节点抓取超时，确认Asleep后用scripts/android-ui.ps1 -Action key -Key 224唤醒；未更改锁屏或绕过审批。
- 未覆盖：P4、真实断网；旧独立自动计时矩阵已被用户取消，不继续补。RD016按共享源做资源审计，RD017补P4和网络失败。当前无新RD实施授权增量，不自动扩展任务。
- 经验：先确认权威数据源及所有消费者再新增缓存/计时器；排序后限量，用同一目标书对照成员与排名。FIX004由我引入分叉快照，测试未覆盖小组件一致性，不能只归因为旧客户端。详见验收反思与设计36。
