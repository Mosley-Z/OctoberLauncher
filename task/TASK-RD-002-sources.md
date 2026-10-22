# TASK-RD-002 文件来源、类型识别与外部授权

- 状态：DONE（2026-10-23 根集成者按实际证据关闭）
- 优先级/阶段：P0 / R1
- 执行者/集成者：Codex（本聊天）
- 分支/工作树/源码基线：master / 1379e8b + PERF-001、RD-001差异
- 依赖：[001](TASK-RD-001-contracts.md)
- READY条件：依赖交付经集成者确认，接口与文件所有权完成交接，收到本卡执行指令
- 设计依据：[34总方案](../docs/design/34-files-reader-upgrade-plan.md)、[协作流程](../docs/design/35-development-workflow.md)、[契约草案](CONTRACTS.md)
- 允许修改：feature/reader/source、routing/TypeDetector及对应测试；外部Activity和Manifest由集成者接入。
- 共享入口：除本卡明确授予集成者的部分，其余只提接入差异；ReaderRegistry、ReaderScreen、模块Gradle和公共fixture由集成者串行接线
- 范围边界：不改无关微读、锁屏、图标和书架重构；不自动升版本、提交或发布

## 目标

将本地路径和外部授权URI转换为可关闭文档来源，并可靠识别真实格式。

## 实施步骤与交付

1. 实现DocumentHandle元数据、openStream和按需seekable；本地文件直接读取，管道URI仅在引擎需要时缓存。
2. 结合文件头、MIME、扩展名识别格式；ZIP继续检查OOXML/EPUB内部标记，不能全部当普通ZIP。
3. 定义会话临时授权、持久授权、失效后重选/导入行为；外部入口仅接受带授权content URI。
4. 给集成者提供ACTION_VIEW/ACTION_SEND单文件入口适配器和精确MIME清单；实现有配额临时缓存及退出关闭。

交付包含实际改动、注册/调用接入点、接口变化、失败语义、必要测试和一份验收报告。代码中的签名以001冻结的实际契约为准，草案名称允许经记录后调整。

## 验收标准

- [x] 错误MIME、无扩展名、不可seek URI、权限撤销、缺失文件均有可观察结果；不接受外部任意绝对路径。
- [x] 本地打开不默认整文件复制；文件描述符、Stream和缓存生命周期有行为验证；对接008归档来源接口。

- [x] 自测命令/退出码和证据已记录；相关模块检查通过，最终整包与设备门禁由017统筹。
- [x] 未验证项、原有失败、外部阻塞及下一步清晰；没有把代码完成写成设备验收完成。

## 执行与接力记录

- 开始/最近更新：2026-10-22 03:22
- 已改文件/接口决定：见本卡下方原生首轮进展及task/CONTRACTS.md
- 验证命令及结果/证据：见本卡增量与docs/acceptance/2026-10-22-reader-native-first.md
- 未覆盖/阻塞/解除条件：依赖001原生接口编译通过并冻结v1；Office支持不在本卡宣称
- 构建进程/设备占用/恢复：最终构建已退出；S4 debug/P4 release，探针正常收尾后卸载
- 下一步：按依赖分配执行者；先读实际源码及git差异
- 接收方/集成点：Codex（本聊天）
- 提交/合并/发布：未执行


### 2026-10-22 原生首轮进展（Codex）

LocalPath/ContentUri、魔数/扩展名判定、外部content授权入口、只读独立流、不可seek来源有界缓存及其他应用打开已落地。未知mtime保持null；ArchiveEntry、异常退出缓存回收待后续卡。

验证：`check :app:assembleDebug :app:assembleRelease --offline --console=plain`退出0（1556 tasks）；日志`_analysis/reader-probe/native-gate.log`。未提交、未发布。下一步按本卡未完成项接力，不能把原生探针当产品PDF验收。

### 2026-10-22 向DSH交接增量

ArchiveEntry已接入；统一128MiB缓存预约与启动时清理自身遗留缓存已完成，见ZIP验收。仍未完成ZIP内部标记鉴别、ACTION_SEND、完整SAF重选/撤销授权矩阵；外部当前只有授权ACTION_VIEW。保持IN_PROGRESS，交DSH继续。

### 2026-10-22 DSH 增量：ZIP 内部标记、ACTION_SEND 与授权矩阵

- 完成项：ZIP 容器内部标记鉴别（`ZipContainerProbe`，EPUB/DOCX/XLSX/PPTX/普通 ZIP，先经 `ZipPreflight` 预算校验，条目名经 `ArchivePaths.normalize`），`DocumentKinds.detect` 增加容器参数且容器标记优先于扩展名；外部入口重写为 `ExternalEntryPolicy` 加 `DocumentEntryActivity` 单文件适配（VIEW 取 data 与 ClipData，SEND 取 EXTRA_STREAM 与 ClipData，多 URI、未知 action、非 content scheme、无读授权分别拒绝），持久授权仅在 `persistable` 时调用 `takePersistableUriPermission`；`SourceResolver` 把 SecurityException/IllegalArgumentException 映射为 PERMISSION，FileNotFoundException 与空返回映射为 MISSING；Manifest 为 DocumentEntryActivity 增补 `ACTION_SEND` filter，mime 与 VIEW 一致且不带 scheme；入口与拒绝路径补 `DocumentEntry` 日志，供拒绝类设备用例观察。
- 验证：`:feature:reader:testDebugUnitTest` 51 用例 0 失败（新增 ZipContainerProbeTest 9、ExternalEntryPolicyTest 9、DocumentKindsTest 5）；`:feature:reader:detekt` 通过；`:app:assembleDebug` 退出 0，APK 5,846,784 字节，SHA-256 `1afe5c64ce62665592e3709d6c6678030711e7eb3cab530fb960fb9df4ccb4b1`；aapt2 确认 VIEW 与 SEND 两个 filter；S4 设备七例全过（SEND 文本、VIEW EPUB、DOCX 容器拒绝、SEND_MULTIPLE 拒绝、无读授权拒绝、缺失文件、撤销后读取）。证据与边界见[验收记录](../docs/acceptance/2026-10-22-reader-sources-002.md)。
- 未覆盖：真实 SAF 授权在系统存储中撤销后的重选流程；Office 内容渲染（当前只识别容器标记）；SEND_MULTIPLE 冷启动的 `W Bundle` 警告（流程正确，属可优化项）。
- 下一步：等待用户审查本轮增量；通过后按依赖继续下一张卡，整包与设备门禁由 017 统筹。

### 2026-10-22 审查修复：类型安全、主文档与附带授权、日志脱敏

- P1：`DocumentEntryActivity.streamUri()` 先按动作判定，仅在 `ACTION_SEND` 与 `ACTION_SEND_MULTIPLE` 读取 `EXTRA_STREAM`，并以 `Parcelable` 类型安全取出后再判断是否为 `Uri`；其他 Parcelable 按缺失处理，不抛出未捕获的 `ClassCastException`。
- P2：`ExternalEntryPolicy` 区分主文档与附带授权。VIEW 以 data、SEND 以 `EXTRA_STREAM` 为主文档；主文档存在时 ClipData 全部视为附带授权，不参与文件数量判定。主文档缺失时才回退到 ClipData 首项，多于一项仍按不支持处理。补充 `viewKeepsDocumentWithClipGrants`、`viewRejectsMultipleClipUrisWithoutData`、`sendKeepsStreamWithClipFolderGrant` 三组回归用例。
- P3：转发日志只记录 scheme、MIME 前 64 字符与授权状态，路径与查询参数不写 logcat。
- 验证：`:feature:reader:testDebugUnitTest` 53 用例 0 失败（`ExternalEntryPolicyTest` 11）；`:feature:reader:detekt` 与 `:feature:reader:ktlintCheck` 通过；`check :app:assembleDebug :app:assembleRelease` 退出 0（52s），debug 5,846,784 字节 SHA-256 `77bd08bfae2d604edc0a15476c4f7074ef4665ee87f542f031d1dad4d327b03d`，release 1,434,377 字节 SHA-256 `308fc6eb0c6648c30a2145f8f005d03b326b2c2beffce4562d34455ffd8b6a35`，apksigner 验证通过。
- 设备复核：非 Uri 类型的 `EXTRA_STREAM` 得到 `reject=MISSING` 且产品进程存活；sample.md 加父目录 ClipData 打开为 `reader_title=sample.md`、`reader_status=42/47`，未按多文件拒绝。
- 仍属边界：真实 SAF 系统撤销后的重选流程、Office 内容渲染、release 沿用 debug 签名配置；不把设备复核当整包发布验收。

## 2026-10-23 根集成者最终验收关闭

P4已完成Android真实持久授权→强停由最近阅读重开→真实撤销失败→产品重选恢复。合成DocumentsProvider/picker与不存在的厂商DocumentsUI界面明确区分。历史类型/ZIP识别、VIEW/SEND、不可seek/缺失/权限错误及来源生命周期证据保留；本轮私有DOCX关闭后三项实际缓存计数零、声明超预算拒绝。

证据：[P4最终增量](../docs/acceptance/2026-10-23-reader-final-p4.md)、[前轮集成交付](../docs/acceptance/2026-10-23-reader-office-search-integration.md)、[本轮收口](../docs/acceptance/2026-10-23-reader-final-closure.md)。身份链802b、版本变更215b、状态/缓存4e98分别注明，不冒充同包重复。最终源码b4ee37f、版本1.0.29/49，gate-4 check/debug/release退出0，reader各136项零失败。DONE不等于正式发布；完整资源/状态余项继续归RD016/017。
