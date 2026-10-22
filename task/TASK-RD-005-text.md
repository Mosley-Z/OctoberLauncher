# TASK-RD-005 大文本、编码与原生分页

- 状态：IN_PROGRESS（原生文本首个闭环）
- 优先级/阶段：P0 / R2
- 执行者/集成者：Codex（本聊天）
- 分支/工作树/源码基线：master / 1379e8b + PERF-001、RD-001/002/003差异
- 依赖：[002](TASK-RD-002-sources.md)、[003](TASK-RD-003-reader-shell.md)、[004](TASK-RD-004-reading-state.md)
- READY条件：依赖交付经集成者确认，接口与文件所有权完成交接，收到本卡执行指令
- 设计依据：[34总方案](../docs/design/34-files-reader-upgrade-plan.md)、[协作流程](../docs/design/35-development-workflow.md)、[契约草案](CONTRACTS.md)
- 允许修改：feature/reader/text、文本样本与测试；公共宿主只提交注册适配器。
- 共享入口：除本卡明确授予集成者的部分，其余只提接入差异；ReaderRegistry、ReaderScreen、模块Gradle和公共fixture由集成者串行接线
- 范围边界：不改无关微读、锁屏、图标和书架重构；不自动升版本、提交或发布

## 目标

TXT、日志和代码在小内存设备上快速打开，支持翻页、字号、搜索和恢复。

## 实施步骤与交付

1. 分块读取并建立稀疏索引，默认块约256KiB，显式编码选择UTF-8/UTF-16/GB18030及错误提示。
2. StaticLayout排版并缓存有限页，处理中文标点、长行和跨块字符，字号/行距变化后从锚点重排。
3. 实现文本SearchProvider，跨块匹配及取消；结果返回Locator，不生成整文件Spannable。
4. 接入004保存/恢复，通知003内容就绪及页进度；二进制未知文件仅经明确操作按文本显示。

交付包含实际改动、注册/调用接入点、接口变化、失败语义、必要测试和一份验收报告。代码中的签名以001冻结的实际契约为准，草案名称允许经记录后调整。

## 验收标准

- [ ] 中文编码、混合长行、跨块匹配/多字节边界、50MiB样本可复现；首屏无需读完全文。
- [ ] 连续翻页和重排后定位合理；退出释放块缓存及索引任务；代码高亮不作为本卡完成前提。

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

TextEngine固定64KiB窗口+StaticLayout，UTF-8/UTF-16/GB18030显式切换、双向翻页与字节锚点落地。3个窗口测试覆盖跨字节/编码/50MiB seek，7个reader用例通过；全局搜索、稀疏索引与更多重排样本仍待实现。

验证：`check :app:assembleDebug :app:assembleRelease --offline --console=plain`退出0（1556 tasks）；日志`_analysis/reader-probe/native-gate.log`。未提交、未发布。下一步按本卡未完成项接力，不能把原生探针当产品PDF验收。

### 分块搜索增量

已实现搜索下一个：固定窗口、跨块尾部保留、UTF-8/UTF-16/GB18030字节位置、每块检查取消；增加2个有意义的搜索测试。UI提供输入及取消进度框。历史页锚点上限64。稀疏索引、搜索高亮和完整设备矩阵仍未完成。

### 2026-10-22 向DSH交接增量

条目文本改用DocumentHandle.openChannel，ZIP选中TXT首窗仅解压64KiB，回翻复用前缀，S4退出缓存清零。普通文本搜索已完成；稀疏索引、高亮、设备矩阵未完成，交DSH继续。

### 2026-10-22 稀疏索引与搜索高亮增量（DSH text-index）

改动文件（均在 `feature/reader/.../feature/reader/text/` 与其 test 目录）：

- `TextWindow.kt`：新增常量 `TEXT_WINDOW_BYTES = 64 * 1024`、`TEXT_CHUNK_BYTES = 256 * 1024`；新增 `SparseTextIndex(channel, chunkSize)`，提供 `size`、`chunkCount`、`chunkIndexOf`、`locate`、`chunkOffsets`、`alignToCharBoundary`、`seek`；新增便捷函数 `seekTextWindow(channel, byteOffset, charset, chunkSize)`。块表由块数算术生成，建表与查询只调用 `channel.size()`，50MiB 为 200 项。
- `TextSearch.kt`：新增 `TextMatch(byteOffset, windowOffset, charStart, charEnd)`、`TextRange(start, end)`、`findTextMatch(channel, from, charset, query, checkpoint)`、`findTextRanges(text, query)`；`findText` 改为委托 `findTextMatch` 并返回字节偏移，原有调用契约保持不变。
- `TextHighlight.kt`（新增）：`TEXT_HIGHLIGHT_COLOR` 与 `highlightRanges(text, ranges): CharSequence`，用 `SpannableString` 与 `BackgroundColorSpan` 只包裹当前页文本。
- `TextEngine.kt`：持有 `SparseTextIndex`；恢复锚点与搜索命中先经 `alignToCharBoundary` 对齐；`render` 按当前页文本重算 `findTextRanges` 并交给 `layout(CharSequence)`，翻页与重排后按新页重算；`close` 清理高亮查询。
- `SparseTextIndexTest.kt`（新增6用例）、`TextSearchTest.kt`（新增2用例）：覆盖块表算术与不读文件、50MiB 尾块直达、文件尾短块、UTF-8/UTF-16/GB18030 边界、命中窗口区间、页内重复与相邻命中。

语义取舍：块索引只承担定位与字符边界对齐，页面读取继续使用固定 64KiB 窗口与字节锚点，`turn`、历史页上限 64、跨块搜索与取消检查保持原样。`TextMatch.windowOffset` 为当前读取窗口起点，`charStart` 与 `charEnd` 为该窗口文本内区间，满足 `readTextWindow(channel, windowOffset, charset).text.substring(charStart, charEnd) == query`。GB18030 的尾字节可能是合法单字节字符，索引无法在本地判定全部边界，仅能跳过解码报错的错位并回退原偏移，作为已知局限记录。

验证命令与结果：

- Kotlin 2.0.21 编译器直接编译 `TextWindow.kt`、`TextSearch.kt` 与纯逻辑断言程序，退出 0；断言程序输出 `PASS: 43 checks`，退出 0（覆盖块表、尾块、三类编码边界、命中窗口区间、重复与相邻区间、取消）。
- 用最小 Android 表面桩与真实 `ReaderEngine.kt`、`DocumentHandle.kt` 联合编译 `TextEngine.kt`、`TextHighlight.kt`，退出 0，确认签名与类型一致。
- 未执行 Gradle：任务边界禁止运行 Gradle，且当前 PATH 无 java 入口；`feature:reader` 的 JUnit 测试待集成环境执行，建议命令 `./gradlew :feature:reader:testDebugUnitTest --tests "com.shuiyu.octoberrain.feature.reader.text.*"`。
- 设备矩阵、真实墨水屏高亮对比度、真机字号重排未验证，本轮结论仅覆盖纯逻辑与编译层面。
