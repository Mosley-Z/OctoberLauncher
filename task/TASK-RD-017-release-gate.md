# TASK-RD-017 完整集成、审查与交付验收

- 状态：IN_PROGRESS（Office/RD014整包门禁已过，双机最终矩阵中）
- 优先级/阶段：P0 / R5
- 执行者/集成者：Codex；S4/P4由各测试子智能体独占
- 分支/工作树/源码基线：master/0a1534c + Office/RD014工作区；保留无关修改
- 依赖：[011](TASK-RD-011-docx.md)、[012](TASK-RD-012-sheets.md)、[013](TASK-RD-013-slides.md)、[014](TASK-RD-014-search.md)、[015](TASK-RD-015-integration.md)、[016](TASK-RD-016-resources.md)、[FIX001](TASK-FIX-001-reader-review.md)、[FIX002](TASK-FIX-002-single-bookshelf.md)、[FIX003](TASK-FIX-003-special-icons.md)、[FIX004](TASK-FIX-004-shelf-controls-refresh.md)（最新书架行为交付）、[FIX006](TASK-FIX-006-shared-shelf-refresh.md)（覆盖独立自动模式）
- READY条件：依赖交付经集成者确认，接口与文件所有权完成交接，收到本卡执行指令
- 设计依据：[34总方案](../docs/design/34-files-reader-upgrade-plan.md)、[协作流程](../docs/design/35-development-workflow.md)、[契约草案](CONTRACTS.md)
- 允许修改：集成者共享注册/设置/许可/README/版本文件；本轮发布范围由实际授权决定；验收报告及证据。
- 共享入口：除本卡明确授予集成者的部分，其余只提接入差异；ReaderRegistry、ReaderScreen、模块Gradle和公共fixture由集成者串行接线
- 范围边界：不改无关微读、锁屏、图标和书架重构；不自动升版本、提交或发布

## 目标

将基础阅读与Office整合为可交付版本，证据覆盖最终源码和APK。

## 实施步骤与交付

1. 串行注册Office与搜索，核对固定资源摘要/版本/许可，更新ThirdPartyLicenses和实际支持矩阵。
2. 审查所有卡交付，完成check及debug/release构建，必要格式化与检查分开；保留最终APK摘要和源码状态。
3. 同一APK在S4/P4执行目标矩阵，补齐Office性能、外部URI、异常、锁屏恢复、字体变化和现有桌面回归。
4. 按授权决定是否升版本/签名/快照/发布；提交、合并、推送、装机、发布分别记录；回填卡/STATUS及经验。

交付包含实际改动、注册/调用接入点、接口变化、失败语义、必要测试和一份验收报告。代码中的签名以001冻结的实际契约为准，草案名称允许经记录后调整。

## 验收标准

- [ ] 所有必要测试/设备项目有证据且明确未覆盖；新模块release反射正常，用户测试状态已恢复。
- [ ] APK与源码一致；当前格式支持与文案一致；发布未授权时仅交付可审查产物，不擅自上线。

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

## 2026-10-22 审查整改关联（最新排期）

最终验收显式纳入三张 FIX：忙碌重排、最近阅读独立；旧配置迁移、唯一负一屏书架与旧路由兼容；设置/文件换图、恢复默认及主题持久化。以最终同一 APK 在 S4/P4 覆盖，Office 未集成不能据此关闭 017。原“不改图标和书架重构”仍限制本卡重新实施功能，但允许对 FIX 交付做必要集成回归，问题回原卡修。各旧验收仅作历史参考。

## FIX004 最新关联

书架行为以[FIX004](TASK-FIX-004-shelf-controls-refresh.md)及其验收为准：标题同行、默认手动快照、前台自动到期。资源审计不得恢复进入/切源自动扫描；最终设备门禁补P4、长间隔及断网矩阵，保留最近阅读正常写入。

## FIX006最新关联（覆盖旧FIX004计时要求）

使用[FIX006](TASK-FIX-006-shared-shelf-refresh.md)共享微信读书缓存/book刷新/dataVersion通知；不要恢复独立微信读书快照与计时器。RD016审计共享读写/并发/前后台资源；RD017补P4和真实网络失败，旧自动长间隔矩阵已取消。本地手动目录快照保留。

## 2026-10-23 P4基线补验（分派子任务，非整卡完成）

- 执行者/文件范围：P4测试子智能体，独占serial `P4L202610130006`；仅本节、[验收记录](../docs/acceptance/2026-10-23-reader-p4-baseline.md)、`_analysis/verify/p4-baseline/`、`_ui-out/p4-baseline-*`。源码/共享文档/Gradle/S4未操作。
- 基线：master工作树中稳定提交`0a1534c`的release副本，摘要`c2d9efcd09348c51663a2068a7b78ac1031130d18bc79681fd9951927a6d55ab`，1.0.28/48；并行源码开发未纳入该APK。
- 六行回读：①自身content URI外部入口保留；②阅读壳常显三按钮、设置菜单操作；③TXT/PDF/Markdown/EPUB/ZIP/DOCX各自合成样本；④仅刷新间隔单字段变更，消费者为阅读器Refresh；⑤原6→0→12→重启保留12→还原6，合成进度/书签保留；⑥用真实552×768 P4点击、状态、强停恢复、损坏PDF返回HOME与进程PSS作为证据，未测项单列。
- 验证：覆盖安装保留数据；TXT翻页/书签/强停恢复，PDF翻页/重开，Markdown/EPUB翻页，ZIP目录内TXT，DOCX正文/原版式及模式锚点强停恢复，全刷0/12可选、12重启持久化与6恢复，损坏PDF失败后HOME；详见验收矩阵。
- 资源：DOCX主+绑定WebView renderer PSS123,357 KiB；PDF主+isolated worker100,094 KiB，均为小合成样本单次快照，不代表峰值或压力通过。
- 恢复/释放：原刷新6已CHECKED回读，原物理552×768未变，未改网络/锁屏/字体；最终基线release、HOME，P4已释放给集成者。新建独立合成Download目录和阅读记录保留供新包复验。
- 未覆盖/下一步：新Office/统一搜索最终包、复杂样本、全格式状态矩阵、SAF/网络/锁屏、物理EPD与长压均待最终同一APK双机验收；017不记DONE。无后台脚本/Gradle，无提交/升版/发布。


## 2026-10-23 Office/RD014集成六行回读

- 入口：现有文件/最近阅读/外部URI统一Reader，补真实支持的Office VIEW/SEND精确MIME；图片保持原流程。
- 控件：阅读设置接工作表/行列、幻灯片目录/提纲、全文搜索与上下个；常显返回/前后页和刷新单选延续。
- 显示：仅当前表格窗口或当前幻灯片资源入视图；复杂对象提示，超限/无权限明确失败且可返回。
- 字段/消费者：Sheet与Slide Locator、ReaderTable/OfficeWebMode、内部SearchProvider；旧SQLite不重建，搜索词/结果不持久化。
- 迁移/保留：同源身份别名与既有进度书签延续；复制/移动保持独立，取消/退出沿唯一动作队列；界面成功后落盘。
- 验收：全仓门禁/debug/release、同最终APK双机计数/高亮/取消/入口与200/30压力；物理刷新/真实SAF等未覆盖独立保留。
- 自动结果与APK详见[集成交付](../docs/acceptance/2026-10-23-reader-office-search-integration.md)，根独占Gradle；本批未推送/升版/发布。
