# TASK-RD-001 阅读器契约与模块骨架

- 状态：TODO（规划卡，未执行）
- 优先级/阶段：P0 / R1
- 执行者/集成者：未分配
- 分支/工作树/源码基线：执行时填写；保留已有修改
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

- 开始/最近更新：未开始
- 已改文件/接口决定：无
- 验证命令及结果/证据：无
- 未覆盖/阻塞/解除条件：尚未执行
- 构建进程/设备占用/恢复：未使用
- 下一步：按依赖分配执行者；先读实际源码及git差异
- 接收方/集成点：待分配
- 提交/合并/发布：未执行
