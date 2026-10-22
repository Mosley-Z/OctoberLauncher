# TASK-RD-006 原生PDF隔离渲染与分页

- 状态：DONE（S4原生闭环；P4完整交互留017补测）
- 优先级/阶段：P0 / R2
- 执行者/集成者：Codex（本聊天，串行集成）
- 分支/工作树/源码基线：master / 1379e8b + PERF与RD000–005本轮差异；保留全部修改
- 依赖：[002](TASK-RD-002-sources.md)、[003](TASK-RD-003-reader-shell.md)、[004](TASK-RD-004-reading-state.md)
- READY条件：依赖交付经集成者确认，接口与文件所有权完成交接，收到本卡执行指令
- 设计依据：[34总方案](../docs/design/34-files-reader-upgrade-plan.md)、[协作流程](../docs/design/35-development-workflow.md)、[契约草案](CONTRACTS.md)
- 允许修改：feature/reader/pdf、AIDL及像素共享协议、PDF测试；Manifest/刷新共享入口交集成者。
- 共享入口：除本卡明确授予集成者的部分，其余只提接入差异；ReaderRegistry、ReaderScreen、模块Gradle和公共fixture由集成者串行接线
- 范围边界：不改无关微读、锁屏、图标和书架重构；不自动升版本、提交或发布

## 目标

在WebView83直接阅读PDF，渲染异常不拖垮Launcher。

## 实施步骤与交付

1. 实现非导出isolatedProcess绑定服务，只读FD输入，PdfRenderer串行操作，Page按顺序关闭。
2. 通过共享内存或FD传页像素，禁止Binder传整页大数组；按session/generation丢弃过期结果。
3. 原生页面视图支持跳页、缩放、裁边及页书签；当前/邻页LRU初始12MiB，缩放限制可见区域。
4. 管道URI通过002提供seekable缓存；服务死亡、坏文件、密码不支持均可退出并返回桌面。

交付包含实际改动、注册/调用接入点、接口变化、失败语义、必要测试和一份验收报告。代码中的签名以001冻结的实际契约为准，草案名称允许经记录后调整。

## 验收标准

- [x] 飞行模式下正常与100MiB PDF可读；连续翻页、退出重开、进程恢复、服务异常后Launcher可用。
- [x] API30不暴露搜索/选字/批注能力；FD/位图/服务释放有证据，实际PSS包含隔离进程。

- [x] 自测命令/退出码和证据已记录；相关模块检查通过，最终整包与设备门禁由017统筹。
- [x] 未验证项、原有失败、外部阻塞及下一步清晰；没有把代码完成写成设备验收完成。

## 执行与接力记录

- 开始/最近更新：2026-10-22 04:30
- 已改文件/接口决定：pdf服务/异步AIDL/SharedMemory、PdfEngine/Viewport、ReaderRegistry/Screen、公共Pdf视口Locator、Application隔离引导及API27服务开关
- 验证命令及结果/证据：全量check/debug/release退出0，1560tasks；docs/acceptance/2026-10-22-reader-pdf.md和_analysis/verify/pdf-006
- 未覆盖/阻塞/解除条件：P4完整交互未验，首屏性能目标仍待017分段优化；详见验收边界
- 构建进程/设备占用/恢复：无Gradle；S4调试包，飞行模式/Wi-Fi已恢复；退出后隔离进程消失
- 下一步：007离线WebHost；017补P4与首屏P95矩阵，不重复已有效验证
- 接收方/集成点：Codex（本聊天）
- 提交/合并/发布：未执行


收口：原生PDF已接入文件与外部URI闭环；页缓存12MiB、来源临时缓存128MiB；ReaderEngine.page/turn改为suspend以容纳异步Binder，TextEngine保持相同窗口逻辑。没有PDF全文搜索/选字能力，P4未验和性能目标未达证明均明确留017。
