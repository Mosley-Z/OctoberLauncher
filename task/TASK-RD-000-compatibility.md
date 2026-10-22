# TASK-RD-000 设备与兼容性尖峰

- 状态：DONE（兼容尖峰已交付；最终资源/光学数据归RD016/017）
- 优先级/阶段：P0 / R0
- 执行者/集成者：Codex（本聊天）
- 分支/工作树/源码基线：master / 1379e8b + PERF-001已验证差异；保留性能修改
- 依赖：无；收到执行指令后可开展
- READY条件：依赖交付经集成者确认，接口与文件所有权完成交接，收到本卡执行指令
- 设计依据：[34总方案](../docs/design/34-files-reader-upgrade-plan.md)、[协作流程](../docs/design/35-development-workflow.md)、[契约草案](CONTRACTS.md)
- 允许修改：_analysis/reader-probe/；docs/acceptance/ 本卡报告；不修改产品源码和全局依赖。
- 共享入口：除本卡明确授予集成者的部分，其余只提接入差异；ReaderRegistry、ReaderScreen、模块Gradle和公共fixture由集成者串行接线
- 范围边界：不改无关微读、锁屏、图标和书架重构；不自动升版本、提交或发布

## 目标

冻结目标机与候选资源的兼容边界，使后续任务可以按实测能力实现。

## 实施步骤与交付

1. 读取34方案第1、5、9、10、12节；核实serial、ABI、Android、WebView、memoryClass和可用空间，只记录技术数据。
2. 固定Gander commit及docx-preview、JSZip、SheetJS、PPTXjs、commonmark候选版本、摘要、许可；样本含中文、图表及大文件。
3. 用独立探针验证PDF渲染、DOCX/表格/PPTX、EPUB所需JS/CSS/API；每项记录打开、分页、图片及搜索限制。
4. 记录基线APK大小和分进程PSS；输出能力矩阵、依赖清单及对预算的修订建议，供001冻结。

交付包含实际改动、注册/调用接入点、接口变化、失败语义、必要测试和一份验收报告。代码中的签名以001冻结的实际契约为准，草案名称允许经记录后调整。

## 验收标准

- [x] WebView83真实设备每项有通过/失败/未验证状态和样本证据；没有设备时报告阻塞，不能以桌面Chrome替代。
- [x] 候选资源有确切版本/摘要/来源，失败原因能定位到资源或API；不把转译成功等同运行兼容。

- [x] 自测命令/退出码和证据已记录；相关模块检查通过，最终整包与设备门禁由017统筹。
- [x] 未验证项、原有失败、外部阻塞及下一步清晰；没有把代码完成写成设备验收完成。

## 执行与接力记录

- 开始/最近更新：2026-10-22 03:16
- 已改文件/接口决定：见本卡下方原生首轮进展及task/CONTRACTS.md
- 验证命令及结果/证据：见本卡增量与docs/acceptance/2026-10-22-reader-native-first.md
- 未覆盖/阻塞/解除条件：已开始；未完成项见本卡增量与验收边界
- 构建进程/设备占用/恢复：最终构建已退出；S4 debug/P4 release，探针正常收尾后卸载
- 下一步：独立APK验证原生PDF和WebView83能力，原生通路通过后冻结001基础契约；Office资源矩阵保持待验（34方案明确Office不阻塞基础阅读）
- 接收方/集成点：Codex（本聊天）
- 提交/合并/发布：未执行


### 2026-10-22 原生首轮进展（Codex）

S4/P4均API30、armeabi-v7a、128MiB memoryClass、lowRam=true、WebView83。原生PdfRenderer/SharedMemory及Web基础特性探针通过；Office候选包真实加载、表格/幻灯片压力测试尚未完成，保持IN_PROGRESS。证据：_analysis/reader-probe/*native-webview.txt。

验证：`check :app:assembleDebug :app:assembleRelease --offline --console=plain`退出0（1556 tasks）；日志`_analysis/reader-probe/native-gate.log`。未提交、未发布。下一步按本卡未完成项接力，不能把原生探针当产品PDF验收。

### Office基础增量

S4/P4的DOCX中文80段、SheetJS中文1001行及显式21行窗口、PPTX3页基础DOM通过。候选资源版本/摘要/来源/完整许可已固定，仅独立探针使用。限制与初次宿主错误修正见reader-native-probe验收增量；图片、图表、大文件、renderer总PSS等仍待验，不完成本卡。

### 2026-10-23 产品通路补验

Office及统一搜索已随7d8a680进入产品，非仅探针；最终477整包check/debug/release通过，reader各132例0失败。固定资源链、两机实际格式/系统入口、P4工作簿200/30及S4 PPTX最终压力由[集成交付](../docs/acceptance/2026-10-23-reader-office-search-integration.md)逐项汇总。真实复杂Office、首屏/翻页P95与面板物理刷新尚未有完整样本证据，不将本轮代表用例升级为整卡DONE。

## 2026-10-23 根集成者最终验收关闭

双机API30/WebView83原生与Office真实加载、固定资源版本/摘要/许可及产品通路均有证据。依据reader-card-audit和reader-office-search-integration关闭尖峰卡；不把尖峰完成扩大为全Office兼容。复杂官方输入与性能继续由RD017/RD016汇总。

最终源码b4ee37f、版本1.0.29/49，gate-4 check/debug/release退出0，reader各136例零失败。详细证据与边界见[收口记录](../docs/acceptance/2026-10-23-reader-final-closure.md)及[卡片审计](../docs/acceptance/2026-10-23-reader-card-audit.md)。本卡DONE仅表示任务集成交付，不表示正式签名或发布；后置RD018–020继续搁置。
