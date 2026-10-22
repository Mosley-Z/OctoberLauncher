# TASK-RD-008 ZIP虚拟目录与条目来源

- 状态：DONE（本卡首轮；最终设备矩阵见017）
- 优先级/阶段：P1 / R3
- 执行者/集成者：Codex（本聊天，串行集成）
- 分支/工作树/源码基线：master / 1379e8b + 本轮已验证差异；保留已有修改
- 依赖：[002](TASK-RD-002-sources.md)、[003](TASK-RD-003-reader-shell.md)
- READY条件：依赖交付经集成者确认，接口与文件所有权完成交接，收到本卡执行指令
- 设计依据：[34总方案](../docs/design/34-files-reader-upgrade-plan.md)、[协作流程](../docs/design/35-development-workflow.md)、[契约草案](CONTRACTS.md)
- 允许修改：feature/reader/archive、ArchiveEntry来源适配及测试；不修改通用文件管理写操作。
- 共享入口：除本卡明确授予集成者的部分，其余只提接入差异；ReaderRegistry、ReaderScreen、模块Gradle和公共fixture由集成者串行接线
- 范围边界：不改无关微读、锁屏、图标和书架重构；不自动升版本、提交或发布

## 目标

ZIP像目录一样浏览，选中条目使用同一阅读引擎，无需整包解压。

## 实施步骤与交付

1. 建立目录索引、排序和条目元数据，处理中文文件名与可配置编码。
2. 条目作为DocumentRef交路由；流式格式按流读取，PDF/OOXML按需预算落盘，显示实际限制。
3. 按真实输出字节、条目数和路径约束控制展开，不自动递归打开嵌套归档。
4. 保持返回归档当前位置；首版不支持的加密/算法明确提示，密码ZIP进入018。

交付包含实际改动、注册/调用接入点、接口变化、失败语义、必要测试和一份验收报告。代码中的签名以001冻结的实际契约为准，草案名称允许经记录后调整。

## 验收标准

- [x] 归档内TXT/图片/PDF可打开并返回原目录；不为浏览展开全部内容。
- [x] 路径逃逸、超限条目、坏归档、中文名称和临时缓存释放有测试；不把所有条目宣称零落盘。

- [x] 自测命令/退出码和证据已记录；相关模块检查通过，最终整包与设备门禁由017统筹。
- [x] 未验证项、原有失败、外部阻塞及下一步清晰；没有把代码完成写成设备验收完成。

## 执行与接力记录

- 最近更新：2026-10-22；执行/集成者Codex。
- 已改：archive、ArchiveEntry来源、统一SourceCache、TextEngine通道、ReaderRegistry/Screen和固定预算ImageEngine；共享契约ArchiveEntry.charset与Archive/Image Locator。
- 验证命令及结果/证据：全量check/debug/release退出0，1564 tasks。S4中文目录→TXT/PNG/PDF→返回同目录及HOME缓存清零通过。
- 证据与限制：[ZIP验收](../docs/acceptance/2026-10-22-reader-zip.md)；GB18030真实包、跨设备、压力矩阵仍需017。ZIP64/多卷/密码ZIP首版不支持，不自动递归。
- 当前设备：S4 debug，HOME，合成probe保留后续EPUB；无后台构建。P4未计本卡交互通过。
- 下一步：RD010复用ZIP索引及Web宿主；最终015–017收口。
- 提交/合并/发布：未执行。
