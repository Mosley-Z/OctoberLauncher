# TASK-RD-004 进度、书签与最近阅读存储

- 状态：IN_PROGRESS（原生文本首个闭环）
- 优先级/阶段：P0 / R2
- 执行者/集成者：Codex（本聊天）
- 分支/工作树/源码基线：master / 1379e8b + PERF-001、RD-001/002/003差异
- 依赖：[001](TASK-RD-001-contracts.md)、[002](TASK-RD-002-sources.md)
- READY条件：依赖交付经集成者确认，接口与文件所有权完成交接，收到本卡执行指令
- 设计依据：[34总方案](../docs/design/34-files-reader-upgrade-plan.md)、[协作流程](../docs/design/35-development-workflow.md)、[契约草案](CONTRACTS.md)
- 允许修改：feature/reader/session/ReadingRepository、Locator编解码、SQLite存储及测试；不修改KOReader统计。
- 共享入口：除本卡明确授予集成者的部分，其余只提接入差异；ReaderRegistry、ReaderScreen、模块Gradle和公共fixture由集成者串行接线
- 范围边界：不改无关微读、锁屏、图标和书架重构；不自动升版本、提交或发布

## 目标

同一文档不同入口共享阅读记录，重启和字号变化后按锚点恢复。

## 实施步骤与交付

1. 设计SQLite schema及迁移，存文档身份、来源权限状态、格式Locator、书签和最近阅读。
2. 以规范来源及版本信息定位，内容摘要异步计算合并跨入口记录；同名不同内容不误合并。
3. 定位写入合并，暂停/退出落盘；文件变化校验锚点，不以屏幕页码作为文本唯一位置。
4. 暴露给003/015的查询与操作；记录删除与源文件删除分离，错误保存不显示假成功。

交付包含实际改动、注册/调用接入点、接口变化、失败语义、必要测试和一份验收报告。代码中的签名以001冻结的实际契约为准，草案名称允许经记录后调整。

## 验收标准

- [ ] 重进、进程重启、字体变化、文件修改、同名不同文件和跨入口恢复有测试/设备证据。
- [ ] 未知授权与临时URI不会伪装永久可访问；记录操作不删除源文件，旧schema升级保留用户书签。

- [ ] 自测命令/退出码和证据已记录；相关模块检查通过，最终整包与设备门禁由017统筹。
- [ ] 未验证项、原有失败、外部阻塞及下一步清晰；没有把代码完成写成设备验收完成。

## 执行与接力记录

- 开始/最近更新：2026-10-22 03:28
- 已改文件/接口决定：见本卡下方原生首轮进展及task/CONTRACTS.md
- 验证命令及结果/证据：见本卡增量与docs/acceptance/2026-10-22-reader-native-first.md
- 未覆盖/阻塞/解除条件：已开始；未完成项见本卡增量与验收边界
- 构建进程/设备占用/恢复：最终构建已退出；S4 debug/P4 release，探针正常收尾后卸载
- 下一步：按依赖分配执行者；先读实际源码及git差异
- 接收方/集成点：Codex（本聊天）
- 提交/合并/发布：未执行


### 2026-10-22 原生首轮进展（Codex）

独立SQLite v1保存字节位置/编码/字号，源版本变化重置，书签添加/列出/跳转已落地。S4翻页后强停重开位置3536保持一致；书签编辑删除、最近阅读入口仍待实现。

验证：`check :app:assembleDebug :app:assembleRelease --offline --console=plain`退出0（1556 tasks）；日志`_analysis/reader-probe/native-gate.log`。未提交、未发布。下一步按本卡未完成项接力，不能把原生探针当产品PDF验收。

### 2026-10-22 向DSH交接增量

PDF视口、Markdown字符、ZIP目录/页码/编码和EPUB章节/字符/比例定位已接入SQLite；Markdown/EPUB书签跳转与强停恢复已验。书签编辑/删除、最近阅读入口仍未实现，交DSH继续。

### 2026-10-22 DSH 增量：书签编辑删除与最近阅读存储层

存储层接口补齐，UI 接线与门禁未做，状态保持 IN_PROGRESS。

改动文件（严格限于本卡授予范围，未改 ReaderScreen、ReaderChrome、其他 feature、Gradle、Manifest）：

- `feature/reader/.../session/ReadingRepository.kt`：schema 仍为 v1（documents 已含 ref/locator/updated/persistent，bookmarks 已有自增 id，无需加列），`onUpgrade` 保留空实现并注明后续加列必须用 `ALTER TABLE ADD COLUMN`、禁止重建 bookmarks 表。新增 `BookmarkRow(id, label, locator)`、`RecentReading(ref, locator, updated, persistent)`；新增 `bookmarkRows(id: String): List<BookmarkRow>`（created/id 降序，上限 100）、保留旧签名 `bookmarks(id): List<Pair<String, ReaderLocator>>`（ReaderScreen 现调用点不受影响）、`updateBookmark(id: Long, label: String): Boolean`、`deleteBookmark(id: Long): Boolean`（false 同时表示标题被拒或主键未命中）、`recent(limit: Int): List<RecentReading>`（updated/id 降序、损坏 ref 跳过且不删行）、伴生 `recent(context, limit)`（独立连接以 try/finally 关闭，不影响进行中的会话数据）。标题规范化抽取为 `ReadingStateRules.normalizeLabel`（去首尾空白、空拒绝、按码点截断 80）、`labelOrFallback`、`clampLimit`、`orderRecent`、`refKey`、`displayName`。
- `feature/reader/.../ReaderSession.kt`：新增 `bookmarkRows()`、`renameBookmark(id, label)`、`deleteBookmark(id)`，均转发 store 并注明由宿主在 IO 线程调用；旧 `bookmarks()`/`bookmark()` 保留。
- `feature/reader/src/test/.../session/ReadingStateRulesTest.kt`：新增 9 个 JVM 用例覆盖标题去空白与空拒绝、80 码点截断（含代理对不拆）、排序与 limit 边界、LocalPath/ContentUri/ArchiveEntry 名称解析与 refKey 区分。

验证：边界禁止执行 Gradle 与 adb，本轮用 .toolchain 的 JDK 17 与缓存中 kotlinc 2.0.21 直接编译并运行 JUnit。ReadingRepository.kt 单独编译退出 0；ReaderSession.kt 单独编译退出 0；`org.junit.runner.JUnitCore ...ReadingStateRulesTest` 输出 `OK (9 tests)`、退出 0。证据目录 `D:/DSHStation/.tmp-verify-rd004/{out,test-out,session-out}`（临时产物，未入库）。复跑标准命令仍为 `./gradlew :feature:reader:testDebugUnitTest :feature:reader:detekt :feature:reader:ktlintCheck`，本轮未执行。

未覆盖：ReaderScreen 的书签编辑/删除与最近阅读入口接线（按分派留给 Lead）；SQLite update/delete rowcount、recent 游标顺序、损坏行跳过不清库均为设备待验；reader 模块无 detekt 基线文件，静态检查结论由整包门禁确认。不能把本节写成设备验收完成。

### 2026-10-22 DSH UI 接线增量（Lead）

- `ReaderScreen.showBookmarks` 改用 `bookmarkRows()` 渲染带主键列表；新增 `bookmarkActions`、`renameBookmark`、`deleteBookmark`。重命名预填原标题、输入上限 80；删除二次确认；两者只更新阅读记录，不删除源文件，失败仅提示。
- 最近阅读入口按任务边界留给 RD-015 集成，本卡只交付存储与查询 API。
- 门禁：reader 70 用例 0 失败（含 ReadingStateRulesTest 9）、detekt 与 ktlint 通过、整包 `check :app:assembleDebug :app:assembleRelease` 通过；日志 `_analysis/verify/reader-003/`。设备侧书签添加、列出、重命名与删除共 16 个步骤全部通过（`_analysis/verify/reader-003/check_bookmarks_device.py`），数据库更新删除与列表刷新以该链路为证据。

## 2026-10-22 审查整改关联（最新排期）

最近阅读显示独立性由 [FIX001](TASK-FIX-001-reader-review.md) 修复，入口搬迁由 [FIX002](TASK-FIX-002-single-bookshelf.md) 完成；数据库与 RecentGateway 继续复用，不新建第二份阅读记录。后续验证同一本地路径从文件页/负一屏打开共享记录；ContentUri 与路径不承诺跨来源身份自动合并。

### 2026-10-23 Office定位兼容增量

7d8a680仅增加Sheet/Slide Locator，不重建数据库；旧CSV Text字节锚点与编码按逻辑记录迁移，旧书签走同一路径。P4真实旧基线覆盖升级，UTF8 BOM/GB18030两份旧进度及书签均恢复142行1列，quoted换行无偏移；证据见[最终P4报告](../docs/acceptance/2026-10-23-reader-search-p4.md)。搜索扫描不写进度，命中显示后才保存；完整第三方URI身份关联仍待，不按本轮兼容用例关闭整卡。
