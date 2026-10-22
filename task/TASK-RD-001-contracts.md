# TASK-RD-001 阅读器契约与模块骨架

- 状态：DONE
- 优先级/阶段：P0 / R1
- 执行者/集成者：Codex（本聊天，串行集成）
- 分支/工作树/源码基线：master / 1379e8b + PERF-001 完成差异
- 依赖：[000](TASK-RD-000-compatibility.md)
- READY条件：依赖交付经集成者确认，接口与文件所有权完成交接，收到本卡执行指令
- 设计依据：[34总方案](../docs/design/34-files-reader-upgrade-plan.md)、[协作流程](../docs/design/35-development-workflow.md)、[契约草案](CONTRACTS.md)
- 允许修改：集成者拥有：core/common阅读契约、settings.gradle.kts、app依赖/注册/R8；feature/reader模块骨架、ReaderRegistry接口；更新CONTRACTS.md。
- 共享入口：除本卡明确授予集成者的部分，其余只提接入差异；ReaderRegistry、ReaderScreen、模块Gradle和公共fixture由集成者串行接线
- 范围边界：不改无关微读、锁屏、图标和书架重构；不自动升版本、提交或发布

## 目标

建立稳定模块和接口，供各引擎独立开发；文件页可路由到明确的空态阅读会话。

## 实施步骤与交付

1. 核查当前git差异，核准CONTRACTS草案并落实际Kotlin签名、错误语义、关闭顺序及版本；下游使用实际源码。
2. 新增feature:reader、ReaderModule和DocumentReader目的地，保持feature不横向依赖，注册反射及release保留规则。
3. 定义DocumentRef、DocumentMetadata、ReaderLocator、ReaderCapabilities、OpenPlan、EngineFactory和统一打开入口。
4. 空实现明确显示未支持并可返回文件列表；建立新模块最小构建配置，依赖仅纳入000确认部分。

交付包含实际改动、注册/调用接入点、接口变化、失败语义、必要测试和一份验收报告。代码中的签名以001冻结的实际契约为准，草案名称允许经记录后调整。

## 验收标准

- [ ] 新模块debug/release可编译，注册后可进入和返回，不影响图片入口；其他feature无需引用reader实现。
- [ ] 文档与实际签名一致；来源描述不保存流/位图/视图，大型对象不放AppServices；下游收到接口文件和版本。

- [ ] 自测命令/退出码和证据已记录；相关模块检查通过，最终整包与设备门禁由017统筹。
- [ ] 未验证项、原有失败、外部阻塞及下一步清晰；没有把代码完成写成设备验收完成。

## 执行与接力记录

- 开始/最近更新：2026-10-22 03:18
- 已改文件/接口决定：见本卡下方原生首轮进展及task/CONTRACTS.md
- 验证命令及结果/证据：见本卡增量与docs/acceptance/2026-10-22-reader-native-first.md
- 未覆盖/阻塞/解除条件：000原生PDF/SharedMemory/WebView83探针已通过；Office资源待验，按34方案原生通路先行，不引入外部依赖
- 构建进程/设备占用/恢复：最终构建已退出；S4 debug/P4 release，探针正常收尾后卸载
- 下一步：按依赖分配执行者；先读实际源码及git差异
- 接收方/集成点：Codex（本聊天）
- 提交/合并/发布：未执行


### 2026-10-22 原生首轮进展（Codex）

原生公共契约v1、feature:reader、Gateway/路由/Manifest/R8接线落地，见CONTRACTS.md。全量check及debug/release通过；后续Registry/OpenPlan随多引擎扩展，故本卡暂保留IN_PROGRESS。

验证：`check :app:assembleDebug :app:assembleRelease --offline --console=plain`退出0（1556 tasks）；日志`_analysis/reader-probe/native-gate.log`。未提交、未发布。下一步按本卡未完成项接力，不能把原生探针当产品PDF验收。


### 006集成后冻结

ReaderRegistry最小实际工厂已接入TEXT/PDF，模块/路由/入口/R8/debug/release/原生设备闭环成立；本卡完成。OpenPlan/通用EngineFactory草案不作为虚构接口；后续Web引擎由集成者增量扩展Registry。接口见CONTRACTS.md。
