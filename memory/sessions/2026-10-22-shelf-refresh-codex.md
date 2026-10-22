# FIX004 书架操作与刷新交接

- 负责人/范围：Codex直接开发，用户确认的标题同行、设置迁移、默认手动/前台自动行为；本轮S4，P4延后。
- 基线/现场：master cfeae53 + 大量原有未提交差异；不清理、不提交/推送/升版/发布。
- 当前卡：[FIX004](../../task/TASK-FIX-004-shelf-controls-refresh.md) READY_FOR_REVIEW；[验收与摘要](../../docs/acceptance/2026-10-22-shelf-refresh-controls.md)。
- 改动：ShelfHeader、ShelfSettingsPanel、ShelfLoader、ShelfDisplayRepository、ShelfSnapshotStore；模型v12/迁移、content序列化插件、ContentSource严格刷新、WereadClient强制/新鲜返回；移除Settings旧页/卡，Home旧路由映射至新设置及精确R8成员保留。
- 最终完整门禁退出0，S4实际测试手动待刷、调整行数/冷启不扫描、显式刷新、目录错误保留、到期快照只在列表前台更新、微信读书真实请求成功、最终release读已存本地及远程快照。没有将JVM Android org.json空实现当成真实网络覆盖。
- 恢复：S4最终release；Books目录，weread，2行，manual，原360分钟间隔，480×800/font1.0；隔离样本逐一删除。无活跃构建或ADB操作，只有常驻守护进程可保留。下轮重新核实，不根据历史假定设备或工具链状态。
- 未覆盖：P4、长间隔墙钟连续计时、运行中断网、无可见旧路由的设备触发；RD017按验收补矩阵。UI就绪前的瞬时占位不当作最终失败。
- 下一步：按用户后续安排继续RD016资源审计；书架资源预算基于本轮快照/前台调度，不能恢复旧的进入即扫描；RD017补矩阵。当前不另行扩展RD任务。
- 对接经验：需求回读要分清控件放在哪里、哪些事件可获取数据、哪些仅读已存视图；封面加载同样是联网入口。新序列化类型显式serializer，最终混淆包单独验证。最新用户行为取代旧FIX002位置约定，历史验收不能覆盖新需求。
