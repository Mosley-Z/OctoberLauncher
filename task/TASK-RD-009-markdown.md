# TASK-RD-009 Markdown轻量解析与阅读

- 状态：DONE（本卡首轮；跨设备最终门禁见017）
- 优先级/阶段：P1 / R3
- 执行者/集成者：Codex（本聊天，串行集成）
- 分支/工作树/源码基线：master / 1379e8b + 已验证PERF与原生TEXT/PDF差异，保留全部修改
- 依赖：[005](TASK-RD-005-text.md)、[007](TASK-RD-007-web-host.md)
- READY条件：依赖交付经集成者确认，接口与文件所有权完成交接，收到本卡执行指令
- 设计依据：[34总方案](../docs/design/34-files-reader-upgrade-plan.md)、[协作流程](../docs/design/35-development-workflow.md)、[契约草案](CONTRACTS.md)
- 允许修改：feature/reader/markdown、必要扩展和对应assets/样本；依赖变更交集成者。
- 共享入口：除本卡明确授予集成者的部分，其余只提接入差异；ReaderRegistry、ReaderScreen、模块Gradle和公共fixture由集成者串行接线
- 范围边界：不改无关微读、锁屏、图标和书架重构；不自动升版本、提交或发布

## 目标

Markdown在WebView83呈现常见排版，不受当前marked库的Chromium92门槛影响。

## 实施步骤与交付

1. 使用000确认的commonmark-java解析，按块生成原生内容或受控HTML；记录本次选择及原因。
2. 支持标题、列表、引用、代码块、链接和本地图片，表格提供可读窗口/横向操作。
3. 处理原始HTML和外部资源，不执行文档脚本；接文本索引、锚点和字号设置。
4. 提供独立注册及SearchProvider，使用007公共宿主而不复制WebView。

交付包含实际改动、注册/调用接入点、接口变化、失败语义、必要测试和一份验收报告。代码中的签名以001冻结的实际契约为准，草案名称允许经记录后调整。

## 验收标准

- [x] 中文、长代码块、相对图片、表格和含原始HTML的样本可读；飞行模式及WebView83实测。
- [x] 搜索跳转和重启恢复可用；不引入最新marked及全局polyfill作为隐含依赖。

- [x] 自测命令/退出码和证据已记录；相关模块检查通过，最终整包与设备门禁由017统筹。
- [x] 未验证项、原有失败、外部阻塞及下一步清晰；没有把代码完成写成设备验收完成。

## 执行与接力记录

- 最近更新：2026-10-22；执行/集成者Codex，本聊天串行构建与ADB。
- 已实现：feature/reader/web、markdown、assets，ReaderRegistry/Screen和授权入口；原生PDF仍独立进程。
- 验证命令及结果/证据：全量check/debug/release及末轮reader:check/debug/release退出0；S4离线、重排、搜索/书签/恢复、私有URI授权、native renderer崩溃、R8宽表格通过。
- 证据与边界：[Web与Markdown验收](../docs/acceptance/2026-10-22-reader-web-markdown.md)。P4完整交互、压力测试、跨本地文档链接等未覆盖项逐条保留，不作全矩阵通过结论。
- 设备：S4最新release，HOME；无线状态恢复。P4仍为前一PDF release，只计安装启动。
- 下一步：RD008，后续010复用该宿主；017统一收敛设备门禁。
- 提交/合并/发布：未执行。
