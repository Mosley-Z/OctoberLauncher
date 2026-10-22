# TASK-RD-010 EPUB章节、目录、分页与定位

- 状态：DONE（首轮；最终矩阵见017）
- 优先级/阶段：P1 / R3
- 执行者/集成者：Codex（本聊天，串行集成）
- 分支/工作树/源码基线：master / 1379e8b + 本轮已验证差异；保留全部修改
- 依赖：[004](TASK-RD-004-reading-state.md)、[007](TASK-RD-007-web-host.md)、[008](TASK-RD-008-zip.md)
- READY条件：依赖交付经集成者确认，接口与文件所有权完成交接，收到本卡执行指令
- 设计依据：[34总方案](../docs/design/34-files-reader-upgrade-plan.md)、[协作流程](../docs/design/35-development-workflow.md)、[契约草案](CONTRACTS.md)
- 允许修改：feature/reader/epub、EPUB样本及测试；共享离线宿主不复制。
- 共享入口：除本卡明确授予集成者的部分，其余只提接入差异；ReaderRegistry、ReaderScreen、模块Gradle和公共fixture由集成者串行接线
- 范围边界：不改无关微读、锁屏、图标和书架重构；不自动升版本、提交或发布

## 目标

无DRM重排版EPUB支持目录、字号、章节分页、书签和全书搜索。

## 实施步骤与交付

1. 解析container/OPF/spine/nav及必要NCX，按包路径解析CSS/图片和相对链接。
2. 仅载当前章与有限邻章，清理不可信脚本和事件属性，等待字体图片布局稳定后定位。
3. Locator保存spine与文本/元素锚点及章内比例，字体变化重排；图片章节也能前后翻页。
4. 逐章提取搜索文本并输出全书SearchProvider，命中未加载章节也能跳转；固定版式/DRM明确能力边界。

交付包含实际改动、注册/调用接入点、接口变化、失败语义、必要测试和一份验收报告。代码中的签名以001冻结的实际契约为准，草案名称允许经记录后调整。

## 验收标准

- [x] 中文图文、跨章跳转、目录、字体改变、书签重启恢复可复现；跨章搜索不只查当前DOM。
- [x] 大书不一次挂载全书；损坏包和不支持DRM有明确结果，资源越界被阻断。

- [x] 自测命令/退出码和证据已记录；相关模块检查通过，最终整包与设备门禁由017统筹。
- [x] 未验证项、原有失败、外部阻塞及下一步清晰；没有把代码完成写成设备验收完成。

## 执行与接力记录

- 开始/最近更新：2026-10-22；执行者Codex，接收方DSH。
- 已改文件/接口决定：epub按章引擎、有限CSS、资源、Epub Locator、目录/链接；Manifest EPUB/ZIP MIME、ReaderChrome系统手势避让。
- 验证命令及结果/证据：全量check/debug/release退出0（1564 tasks），reader30测试通过；最终debug及R8焦点驱动退出0，见[EPUB验收](../docs/acceptance/2026-10-22-reader-epub.md)。旧驱动问题与补验未隐去。
- 未覆盖/阻塞/解除条件：复杂真实包、编码/排版/API/P4矩阵仍未验，最终由017收口。
- 构建进程/设备占用/恢复：构建结束，S4/P4最终release、HOME；无线恢复，合成probe卸载，设备释放。
- 下一步：依用户最新指令完成当前卡后交DSH，不开启新卡。
- 接收方/集成点：memory/sessions/2026-10-22-reader-to-dsh-handoff.md；共享入口由DSH串行接手。
- 提交/合并/发布：未执行。
