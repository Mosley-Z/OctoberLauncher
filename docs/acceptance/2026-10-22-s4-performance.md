# 2026-10-22 S4 八项性能优化

- 范围/验收者：TASK-PERF-001；Codex，用户授权全面接管后的第一批。
- 源码基线：master / 1379e8b，开工干净；本轮不升版本、不提交、不推送。
- 版本：1.0.28；最终 APK SHA-256：debug `c5503a208d0b0c28141290ebd3798a7d808367d96b5dc66af9b5657b01084947`；release `ba56038fadb8c1bda8d7cfb82de480fb62f02137c1e4eceb95c58820daa64f14`。
- 工具链：toolchain.path 的 JDK17 / Gradle8.11.1 / Android SDK；API30。
- 设备：S4 SA4B03CN2608081096X；P4 P4L202610130006。设备操作由本聊天串行执行。

## 实现与验证边界

| 项目 | 实现 | 验证 |
| --- | --- | --- |
| 缩略图锁 | 绘制查缓存只取内存锁，不读 lastModified；抖动、PNG、索引、磁盘清理走独立锁；磁盘按16次写入整理 | 代码调用链；文件/负一屏图片设备冒烟；非毫秒级锁等待实测 |
| 内核快照 | 后台装配统计、本记、进度、待办与外观；内核隐式 bind 也使用快照数据源；跨天只重载一次 | S4 本记原进度与内容保留，五形态切换；非所有空态/跨天设备覆盖 |
| 桌面配置 | 过滤锁屏/托盘等无关配置；不可见时延后装配；取消重复 onConfigChanged；轮播暂停 | HomeConfigChangesTest 2例；S4 进入负一屏/返回 |
| 回桌面刷新 | 只重读本地组件，去掉每次 onResume 的全量 invalidate/sync；数据变更版本驱动可见桌面更新 | 代码链路；保留显式刷新与启动到期同步 |
| 总预算 | 各尺寸共用 ByteBudgetCache，平衡档合计8MiB；降档即时缩容；onTrimMemory/onLowMemory逐出内存，不删除磁盘或回收视图位图 | ByteBudgetCacheTest 3例；S4 RUNNING_LOW 后仍可返回桌面 |
| 封面队列 | 共享下载器合并订阅；本地解码独立、并发上限2；视图解绑取消解码及下载回调 | S4 卡片/负一屏；不声称所有网络失败路径已真机覆盖 |
| 洞察汇总 | 逐书合并想法，按 nt/ni 文件版本缓存字数；后台计算、取消后不回填；兼容旧数组索引 | WereadIdeaSummaryTest 2例，含变更/移除/旧索引/归并 |
| 分区缓冲 | 仅借用后台 spare，主线程发布前一帧后归还；同尺寸复用，尺寸变化释放；保留完整长指纹 | S4 负一屏往返滚动；未以最大50本长指纹做压力验收 |

## S4 一次同场景辅助采样

桌面本记，同一内容与进度，六次固定坐标上滑；来自 Android gfxinfo，未测物理面板刷新：

| 指标 | 更新前 | 更新后 |
| --- | --- | --- |
| 中位帧耗时 | 34ms | 19ms |
| P95帧耗时 | 48ms | 29ms |
| P99帧耗时 | 57ms | 34ms |
| 慢UI线程计数 | 365 | 220 |
| 总采集帧数 | 379 | 707 |

证据：`_analysis/verify/perf-001/s4-before-gfx.txt`、`s4-after-gfx.txt`。一次采样、总帧数不同、墨水屏统计仍几乎全部判jank，不能据此声称稳定提升百分比或达到60fps。

PSS采样前85090KiB、后67386KiB；前2个Activity/178个View，后1个Activity/89个View，场景驻留不同，**不作为优化节省内存的对照结论**。见同目录 before/after-memory.txt。

## 门禁、恢复与后续

- 首轮全量 `check :app:assembleDebug :app:assembleRelease --offline --console=plain`：通过，1464 tasks；后补跨天/重建细节的最终门禁再次通过，49秒，1464 tasks（152 executed）。见 `_analysis/perf-gate.log`。
- 7个新增纯JVM用例验证预算、配置决策与字数汇总，不替代 Android 位图/并发设备验证。
- S4 覆盖安装保留数据，五形态检查后恢复原本记形态，进度未改变；负一屏测试后返回桌面；RUNNING_LOW 无崩溃日志。截图仅在系统临时目录，不纳入Git，避免保存用户笔记正文。
- 不修改PIN/盐/锁屏配置，不创建用户数据备份；没有执行全量配置恢复。
- 完成最终检查后再推进阅读器，独立记录其验证边界。
