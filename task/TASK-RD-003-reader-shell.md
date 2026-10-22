# TASK-RD-003 阅读宿主、工具栏与按键

- 状态：DONE（2026-10-23 根集成者按实际证据关闭）
- 优先级/阶段：P0 / R1
- 执行者/集成者：Codex（本聊天）
- 分支/工作树/源码基线：master / 1379e8b + PERF-001、RD-001差异
- 依赖：[001](TASK-RD-001-contracts.md)
- READY条件：依赖交付经集成者确认，接口与文件所有权完成交接，收到本卡执行指令
- 设计依据：[34总方案](../docs/design/34-files-reader-upgrade-plan.md)、[协作流程](../docs/design/35-development-workflow.md)、[契约草案](CONTRACTS.md)
- 允许修改：feature/reader/ui、session/ReaderSession生命周期、公共交互测试；核心路由改动交集成者。
- 共享入口：除本卡明确授予集成者的部分，其余只提接入差异；ReaderRegistry、ReaderScreen、模块Gradle和公共fixture由集成者串行接线
- 范围边界：不改无关微读、锁屏、图标和书架重构；不自动升版本、提交或发布

## 目标

让各引擎共用一个墨水屏阅读宿主，并保持返回目录位置和按键一致。

## 实施步骤与交付

1. 实现标题、页码/进度、目录、搜索、书签、显示设置、其他应用打开的宿主，按钮根据Capabilities展示。
2. 通过EngineFactory装载/销毁引擎，页面退出取消任务，旧generation结果不能覆盖新会话。
3. 实现VolumeKeySink、点击区域与无动画翻页；复用DeviceProfile和设计令牌，字体/尺寸变化触发引擎重排。
4. 与004约定定位/书签入口；与006/007约定后台准备和主线程呈现，禁止在UI线程解析文档。

交付包含实际改动、注册/调用接入点、接口变化、失败语义、必要测试和一份验收报告。代码中的签名以001冻结的实际契约为准，草案名称允许经记录后调整。

## 验收标准

- [x] 假引擎可演示打开→翻页→返回→恢复目录；能力不支持的操作不会出现假按钮。
- [x] 480×800和552×768的菜单、底栏、触点可用；销毁后旧回调不更新页面；未测设备明确记录。

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

原生ReaderScreen/Chrome、无动画翻页、音量键、全刷节奏、generation失效和取消释放落地。全量门禁通过；PDF/Web能力工具栏与旋转重排等仍需补齐。

验证：`check :app:assembleDebug :app:assembleRelease --offline --console=plain`退出0（1556 tasks）；日志`_analysis/reader-probe/native-gate.log`。未提交、未发布。下一步按本卡未完成项接力，不能把原生探针当产品PDF验收。

### 2026-10-22 向DSH交接增量

能力工具栏已随PDF/Web/ZIP/EPUB接入，单WebView/隔离PDF退出释放有S4证据。旋转/窗口尺寸变化和完整双机按键矩阵仍待验，保持IN_PROGRESS，交DSH继续。

### 2026-10-22 DSH 旋转重排与书签接线增量

- `ReaderChrome.kt`：`ReaderPageView` 新增 `sizeListener`，布局尺寸变化时回调；底栏三按钮单行截断，避免窄屏换行挤压正文。
- `ReaderScreen.kt`：记录最近一次正文可用宽高 `openedWidth/openedHeight`；`onPageSize` 在尺寸变化、可见且存在会话时按 `frame.locator` 重开，动作执行中置 `relayoutPending` 并在 finally 补一次；`open` 成功时记录当前正文尺寸。`MainActivity` 自行处理配置变化，旋转不重建 Activity，故不依赖 `onResume`。
- 书签对话框改用 `bookmarkRows()`，条目支持跳转、重命名与删除；重命名预填标题、上限 80，删除二次确认，操作不删除源文件。
- 验证：`:feature:reader:testDebugUnitTest` 70 用例 0 失败；`:feature:reader:detekt`、`:feature:reader:ktlintCheck` 退出 0；`check :app:assembleDebug :app:assembleRelease` 通过（1564 tasks，debug 5,853,173B SHA-256 `6d0bfa9f…`，release SHA-256 `5213b458…`）。
- 设备：S4 于 13:13 恢复连接后执行矩阵，六项全部通过（480×800 打开、音量键前后翻页、正文左右点按、运行中 `wm size 552x768` 重排与复位，552×768 下状态 3% 保持且正文行宽变化）。系统旋转设置写入后读回 `user_rotation=0`、display rotation 0，设备为固定竖屏且第三方 Rotation 应用接管方向，旋转路径以尺寸变化覆盖同一 `ReaderPageView.sizeListener`→`ReaderScreen.onPageSize` 入口。音量键验证期间临时将 `tray.volumeKeyFlip` 置 true，测毕恢复 false。驱动脚本 `_analysis/verify/reader-003/check_reader003_device.py`。
- 最近阅读入口留待 RD-015 集成。

## 2026-10-22 审查整改关联（最新排期）

重排调度缺陷统一由 [FIX001](TASK-FIX-001-reader-review.md) 修复，ReaderScreen 在该批由 DSH 串行持有；本卡不重复实现。关闭本卡前关联其忙碌期间尺寸变化和退出取消的证据，既有空闲尺寸模拟不能覆盖该用例。

## 2026-10-23 最终验收关闭

历史480×800/552×768真实布局、菜单/底栏/音量键、退出取消与基础恢复证据保留。FIX001调度整改S4忙碌尺寸与连续变化、P4最终4e98真实横屏搜索忙碌→退出→无旧回填→重开0%补齐；新Reader重复onResume已修，返回HOME/前后台恢复有记录。

证据见[最终S4](../docs/acceptance/2026-10-23-reader-final-s4.md)、[最终P4](../docs/acceptance/2026-10-23-reader-final-p4.md)、[收口记录](../docs/acceptance/2026-10-23-reader-final-closure.md)及原卡专项。当前修复源码0d242dd/1.0.30，完整门禁137项/变体零失败；PPTX独立页索引补丁不改变本卡引擎，历史及4e98实测保留APK归属。完整资源与最终设备恢复归RD016/017，无正式发布。
