# 阅读器任务看板与执行入口

创建于2026-10-22。21张卡的初始状态都是TODO，均未实施；实时状态以每张卡的状态字段为准。本看板不复制动态状态。文件存在不等于已向Agent派工。

先读 [PROJECT](../PROJECT.md)、[STATUS](../memory/STATUS.md)、自己的卡、[开发流程](../docs/design/35-development-workflow.md)。设计来源是 [34总方案](../docs/design/34-files-reader-upgrade-plan.md)。通用任务模板在 [task模板](../docs/templates/task.md)。

## 任务索引

| 任务卡 | 目标 | 依赖编号（均为RD系列） | 优先级/阶段 |
| --- | --- | --- | --- |
| [TASK-RD-000](./TASK-RD-000-compatibility.md) | 设备与兼容性尖峰 | 无 | P0 / R0 |
| [TASK-RD-001](./TASK-RD-001-contracts.md) | 阅读器契约与模块骨架 | 000 | P0 / R1 |
| [TASK-RD-002](./TASK-RD-002-sources.md) | 文件来源、类型识别与外部授权 | 001 | P0 / R1 |
| [TASK-RD-003](./TASK-RD-003-reader-shell.md) | 阅读宿主、工具栏与按键 | 001 | P0 / R1 |
| [TASK-RD-004](./TASK-RD-004-reading-state.md) | 进度、书签与最近阅读存储 | 001, 002 | P0 / R2 |
| [TASK-RD-005](./TASK-RD-005-text.md) | 大文本、编码与原生分页 | 002, 003, 004 | P0 / R2 |
| [TASK-RD-006](./TASK-RD-006-pdf.md) | 原生PDF隔离渲染与分页 | 002, 003, 004 | P0 / R2 |
| [TASK-RD-007](./TASK-RD-007-web-host.md) | WebView83离线宿主与资源边界 | 002, 003 | P0 / R3 |
| [TASK-RD-008](./TASK-RD-008-zip.md) | ZIP虚拟目录与条目来源 | 002, 003 | P1 / R3 |
| [TASK-RD-009](./TASK-RD-009-markdown.md) | Markdown轻量解析与阅读 | 005, 007 | P1 / R3 |
| [TASK-RD-010](./TASK-RD-010-epub.md) | EPUB章节、目录、分页与定位 | 004, 007, 008 | P1 / R3 |
| [TASK-RD-011](./TASK-RD-011-docx.md) | DOCX预览与正文模式 | 004, 007 | P1 / R4 |
| [TASK-RD-012](./TASK-RD-012-sheets.md) | Excel与CSV窗口化阅读 | 004, 007 | P1 / R4 |
| [TASK-RD-013](./TASK-RD-013-slides.md) | PPTX单页预览与提纲 | 004, 007 | P1 / R4 |
| [TASK-RD-014](./TASK-RD-014-search.md) | 跨引擎搜索与能力收口 | 005, 009, 010, 011, 012, 013 | P1 / R4 |
| [TASK-RD-015](./TASK-RD-015-integration.md) | 基础阅读入口与最近阅读集成 | 004, 005, 006, 008, 009, 010 | P0 / R3 |
| [TASK-RD-016](./TASK-RD-016-resources.md) | 资源预算、刷新与低内存收口 | 015 | P1 / R5 |
| [TASK-RD-017](./TASK-RD-017-release-gate.md) | 完整集成、审查与交付验收 | 011, 012, 013, 014, 015, 016 | P0 / R5 |
| [TASK-RD-018](./TASK-RD-018-legacy.md) | 扩展旧文档与密码ZIP | 017 | P2 / R6（后置） |
| [TASK-RD-019](./TASK-RD-019-pdf-text.md) | 增强PDF搜索与文本能力评估 | 017 | P2 / R6（后置） |
| [TASK-RD-020](./TASK-RD-020-media-model.md) | 音视频与STL可选扩展 | 017 | P2 / R6（后置） |

## 推荐批次与并行边界

1. 000先做兼容尖峰，001由集成者冻结契约和骨架。契约未冻结时其他卡只阅读，不抢写公共文件。
2. 002来源、003宿主可以分工；同一构建/共享文件仍由集成者串行。004在来源交付后接状态库。
3. 005文本、006PDF、007离线Web宿主、008ZIP按依赖分批；同一时间的Agent数量由当前授权和环境决定，不固定强制四人。
4. 009Markdown、010EPUB、011DOCX、012表格、013PPTX按各自依赖并行；公共宿主、资源清单、依赖和Registry禁止争抢。
5. 015先交付基础阅读MVP，不等Office。016先测MVP；Office就绪后补测。014统一全格式搜索收口，不阻塞基础阅读阶段验收。
6. 017负责Office接入、最终资源矩阵和整包门禁。018–020为后置可选项，须有具体范围及执行指令；不计入首轮完成条件。

## 集成者负责的共享文件

settings.gradle.kts；app Gradle/Manifest/OctoberRainApp/proguard；core:common契约、AppServices和Destination；ReaderRegistry/ReaderScreen/feature:reader Gradle及公共fixture；全局刷新和设置入口；VERSION/CHANGELOG/发布。某卡需要修改时在卡上明确交接时间和所有权，交还后再允许下一人接手。

目录分工：002 source/routing检测、003 ui/session生命周期、004 session存储、005 text、006 pdf、007 web、008 archive、009 markdown、010 epub、011 office/docx、012 office/sheets、013 office/slides、014 search。同目录仍要按具体文件声明，不能凭包名取得整模块所有权。

## 派工与收卡

派工时直接提供：卡路径、执行者角色、真实工作树、允许/禁止文件、已冻结契约版本、构建/设备负责人、预期交付。执行者更新自己的卡；集成者汇总STATUS。每张卡的交付报告放docs/acceptance，原始证据放_analysis/verify/<批次>/。

模板指令：

> 执行 task/TASK-RD-xxx-主题.md。先读PROJECT与STATUS，再核实依赖交付和已有差异。你只拥有卡内指定文件；共享入口提交具体接线建议给集成者。完成代码和必要自测，更新任务卡、验收记录与接力；不自行安装同一设备、升版本或发布。

任务编号只表达身份，不表达实际执行顺序。新范围用新卡或有记录地调整原卡，取消时注明影响下游；不能因聊天压缩遗失未完成事项。
