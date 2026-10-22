# TASK-RD-014 跨引擎搜索与能力收口

- 状态：DONE（统一搜索已集成并完成本卡验收）
- 优先级/阶段：P1 / R4
- 执行者/集成者：Codex；sheets子智能体负责CSV/Workbook专属适配
- 分支/工作树/源码基线：master / D:/DSHStation/OctoberLauncher / 0a1534c+Office批次；保留已有修改
- 依赖：[005](TASK-RD-005-text.md)、[009](TASK-RD-009-markdown.md)、[010](TASK-RD-010-epub.md)、[011](TASK-RD-011-docx.md)、[012](TASK-RD-012-sheets.md)、[013](TASK-RD-013-slides.md)
- READY条件：依赖交付经集成者确认，接口与文件所有权完成交接，收到本卡执行指令
- 设计依据：[34总方案](../docs/design/34-files-reader-upgrade-plan.md)、[协作流程](../docs/design/35-development-workflow.md)、[契约草案](CONTRACTS.md)
- 允许修改：feature/reader/search、搜索工具栏适配与集成测试；引擎内修复由原卡负责人交接。
- 共享入口：除本卡明确授予集成者的部分，其余只提接入差异；ReaderRegistry、ReaderScreen、模块Gradle和公共fixture由集成者串行接线
- 范围边界：不改无关微读、锁屏、图标和书架重构；不自动升版本、提交或发布

## 目标

统一搜索交互，并确保“全文”覆盖未加载的块、章节、工作表和幻灯片。

## 实施步骤与交付

1. 组合各SearchProvider，统一结果计数、上下个、取消、加载中及错误状态。
2. 跨未加载内容增量查找，以Locator跳转，受控DOM标记或原生高亮，不依赖Custom Highlight API。
3. 当前DOM的WebView.findAllAsync只作为局部备选；PDF能力矩阵仍关闭搜索/选字。
4. 核查各格式能力与菜单、搜索范围文案、空结果和过期会话处理。

交付包含实际改动、注册/调用接入点、接口变化、失败语义、必要测试和一份验收报告。代码中的签名以001冻结的实际契约为准，草案名称允许经记录后调整。

## 验收标准

- [x] 文本跨块、EPUB跨章、表格跨sheet、PPTX跨页、DOCX正文的命中可复现。
- [x] 取消/换文档后旧结果不出现；搜索不强制全文件大DOM；PDF没有假搜索入口。

- [x] 自测命令/退出码和证据已记录；相关模块检查通过，最终整包与设备门禁由017统筹。
- [x] 未验证项、原有失败、外部阻塞及下一步清晰；没有把代码完成写成设备验收完成。

## 执行与接力记录

- 开始/最近更新：2026-10-23，用户授权提交后续接并行
- 已改文件/接口决定：search/SearchProvider、TextScan、ReaderSearchUi，各引擎scan/showHit及公共Web搜索协议；见CONTRACTS和集成交付
- 验证命令及结果/证据：全仓check/debug/release退出0，reader132例0失败；_analysis/verify/office-next/final-gate-4.log
- 未覆盖/阻塞/解除条件：最终477包S4核心全格式检索/取消换书通过，P4工作簿及迁移smoke通过；历史中间包单列。真实复杂Office、完整状态/尺寸矩阵留RD017
- 构建进程/设备占用/恢复：根唯一Gradle已退出；两机压力与补丁小回归已结束、恢复并释放，详见设备报告
- 下一步：汇总[最终S4](../docs/acceptance/2026-10-23-reader-search-s4.md)与[最终P4](../docs/acceptance/2026-10-23-reader-search-p4.md)，资源压力交017统一收口
- 接收方/集成点：Codex根集成者
- 提交/合并/发布：基线0a1534c及本卡7d8a680已提交；未推送/升版/发布

## 2026-10-22 审查整改关联（最新排期）

[FIX001](TASK-FIX-001-reader-review.md) 先修宿主忙碌重排；本卡后续将“搜索执行期间尺寸改变/退出”加入集成回归，沿用其 generation 与调度契约，不另做第二套调度。Office 引擎自身依赖不变。


## 2026-10-23 授权续接与六行回读

- 最终入口：阅读设置统一“全文搜索”，PDF保持无搜索入口；旧next内部接口过渡保留，UI不再单独分叉。
- 控件：搜索词输入、可取消加载、总结果计数、上一个/下一个与换词；表格文案明确按单元格计数。
- 显示：当前结果才高亮/跳转；查找没有结果不改变当前位置，扫描不装入整书DOM。
- 状态：SearchProvider.scan返回最多64条SearchHit与完整total；offset决定结果窗口，query/结果仅在当前UI内存，不写正文或查询到日志。
- 迁移：既有Locator/书签/SQLite不变，showHit定位成功并显示后才保存；取消/换词/退出沿唯一动作队列与generation。
- 验收：跨未加载块/章节/sheet/幻灯片及DOCX末段；上下个和计数对照、取消/尺寸/换书、最终双机构建。Office旧包设备验收单独标版本，不冒充搜索版。
- 所有权：根修改search公共契约/UI与Text/EPUB/Markdown/DOCX及公共Web协议；sheets仅自己包和专属脚本/测试；PPTX由原执行者设备结束后接适配。根独占Gradle。

## 2026-10-23 根集成者最终验收关闭

全部本卡标准已有真实集成及设备证据；477跨未加载内容计数/高亮/取消换书、CSV自然升级和PDF无假搜索入口见reader-search-s4/p4及reader-office-search-integration。P4最终4e98忙碌横屏/退出无旧回填见reader-final-p4。全格式资源与复杂样本由RD016/017统筹，不扩大本卡。

最终源码b4ee37f、版本1.0.29/49，gate-4 check/debug/release退出0，reader各136例零失败。详细证据与边界见[收口记录](../docs/acceptance/2026-10-23-reader-final-closure.md)及[卡片审计](../docs/acceptance/2026-10-23-reader-card-audit.md)。本卡DONE仅表示任务集成交付，不表示正式签名或发布；后置RD018–020继续搁置。
