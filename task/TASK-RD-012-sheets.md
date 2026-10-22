# TASK-RD-012 Excel与CSV窗口化阅读

- 状态：READY_FOR_REVIEW（引擎/窗口/统一搜索源码已交集成，最终门禁与设备待验）
- 优先级/阶段：P1 / R4
- 执行者/集成者：sheets 子智能体 / Codex 根集成者
- 分支/工作树/源码基线：master / D:/DSHStation/OctoberLauncher / 0a1534c；保留已有修改
- 依赖：[004](TASK-RD-004-reading-state.md)、[007](TASK-RD-007-web-host.md)
- READY条件：依赖交付经集成者确认，接口与文件所有权完成交接，收到本卡执行指令
- 设计依据：[34总方案](../docs/design/34-files-reader-upgrade-plan.md)、[协作流程](../docs/design/35-development-workflow.md)、[契约草案](CONTRACTS.md)
- 允许修改：feature/reader/office/sheets及专属assets/样本；原生表格视图；依赖接入交集成者。
- 共享入口：除本卡明确授予集成者的部分，其余只提接入差异；ReaderRegistry、ReaderScreen、模块Gradle和公共fixture由集成者串行接线
- 范围边界：不改无关微读、锁屏、图标和书架重构；不自动升版本、提交或发布

## 目标

工作簿可切sheet、定位单元格和搜索；大表不会生成整页巨型DOM。

## 本轮六行需求回读（2026-10-23）

- 最终入口：现有文件页/书架/外部文档入口，经 ReaderRegistry 分发 CSV/XLSX/XLS/ODS，公共接线由集成者完成。
- 控件位置：阅读器菜单提供工作表切换和行列定位；正文区使用原生横向/纵向滚动表格窗口，共享搜索和书签入口沿用。
- 显示归属：本卡只显示表格内容与缓存值，不创建整表 DOM；公式缺失缓存显示“[公式无缓存]”，不重算公式、不展示完整图表。
- 状态与消费者：ReaderLocator.Sheet(sheet,row,column,charset) 经现有阅读记录持久化；CSV 解码器消费 charset，工作簿/表格视图消费位置，窗口最多20×10。
- 迁移：新增 sheet 类型，不改数据库表结构；原定位器和其他格式记录保留，TSV仍走原文本，不误识别为逗号CSV。
- 验收触发：跨工作表搜索、合并锚点在窗外、晚段CSV搜索、编码选择、书签恢复、强停重启、超预算在解析前拒绝；Node/JVM/设备分别记证据。

## 实施步骤与交付

1. 用冻结SheetJS解析XLSX/XLS/ODS样本，桥接按窗口返回数据，原生显示例如100行×20列窗口。
2. CSV原生流式解析引号、嵌入换行、编码和空字段，支持增量定位。
3. Locator保存sheet/row/column；跨工作表搜索，显示缓存值并明确缺失值/公式未计算语义。
4. 解析前检查工作簿预算；DOM窗口化不能解决SheetJS整簿内存成本，超限及时结束。

交付包含实际改动、注册/调用接入点、接口变化、失败语义、必要测试和一份验收报告。代码中的签名以001冻结的实际契约为准，草案名称允许经记录后调整。

## 验收标准

- [ ] 多sheet、中文、合并单元格、公式缓存值、引号跨行CSV及大表样本分别验证。
- [ ] 只有窗口数据进入显示；搜索定位与重启恢复可用；不宣传公式重算或完整图表能力。

- [ ] 自测命令/退出码和证据已记录；相关模块检查通过，最终整包与设备门禁由017统筹。
- [ ] 未验证项、原有失败、外部阻塞及下一步清晰；没有把代码完成写成设备验收完成。

## 执行与接力记录

- 开始/最近更新：2026-10-23；用户授权提交后继续并行开发与测试，根集成者派发本卡。
- 已改文件/接口决定：office/sheets 下 CsvRecords/CsvData/CsvSearch/CsvEngine/WorkbookBudget/BiffBudget/SheetXmlInput/WorkbookEngine/SheetTableView；专属 assets 下 SheetJS固定0.20.3、sheets-host.js、完整许可；专属 JVM 测试及合成样本脚本。根追加派发RD014表格部分，已接SearchProvider完整计数/64条分页/高亮，公共入口仍由根集成。
- 验证命令及结果/证据：Node脚本及额外样本Python脚本退出0，XLSX/XLS/ODS中文、缓存、合并裁窗、跨表搜索、完整计数/64条分页/取消回包/字节预算通过；阶段JVM旧16例通过，新增容器与统一搜索后最终门禁由根执行中，详见[本卡验收](../docs/acceptance/2026-10-23-reader-sheets.md)。
- 未覆盖/阻塞/解除条件：真实复杂Office文件、S4/P4产品通路、断网/renderer恢复/完整内存/强停恢复均待集成后验证；XLS限BIFF8且无扩展DIFAT，ODS大范围重复空格也计预算；CSV总行数到EOF前仅是已读下界。
- 构建进程/设备占用/恢复：本子智能体不运行Gradle、不占用ADB；共享构建与设备由根/独立设备子智能体租用。
- 下一步：根集成与模块检查；据实际失败修复，再以最终同一APK补设备验收。
- 接收方/集成点：根集成者接ReaderRegistry/ReaderScreen/隐藏WebView宿主/资源白名单/许可入口；两引擎实现TableEngine，SheetTableView(context,table)。
- 提交/合并/发布：本卡改动未提交；无升版、推送、发布。
