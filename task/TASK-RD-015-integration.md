# TASK-RD-015 基础阅读入口与最近阅读集成

- 状态：IN_PROGRESS（本轮增量完成，整包与全入口门禁待 017 统筹）
- 优先级/阶段：P0 / R3
- 执行者/集成者：Lead（本轮增量）；task-7 集成、task-8 能力表
- 分支/工作树/源码基线：master `1379e8b` 之后工作区修改；保留既有未提交改动
- 依赖：[004](TASK-RD-004-reading-state.md)、[005](TASK-RD-005-text.md)、[006](TASK-RD-006-pdf.md)、[008](TASK-RD-008-zip.md)、[009](TASK-RD-009-markdown.md)、[010](TASK-RD-010-epub.md)
- READY条件：依赖交付经集成者确认，接口与文件所有权完成交接，收到本卡执行指令
- 设计依据：[34总方案](../docs/design/34-files-reader-upgrade-plan.md)、[协作流程](../docs/design/35-development-workflow.md)、[契约草案](CONTRACTS.md)
- 允许修改：集成者拥有BrowseScreen、BookshelfScreen打开动作、FilesModule、ReaderRegistry、DocumentEntryActivity/Manifest及公共路由接线；只改必要部分。
- 共享入口：除本卡明确授予集成者的部分，其余只提接入差异；ReaderRegistry、ReaderScreen、模块Gradle和公共fixture由集成者串行接线
- 范围边界：不改无关微读、锁屏、图标和书架重构；不自动升版本、提交或发布

## 目标

形成文件页、书架、最近阅读和外部文件入口的基础阅读闭环。

## 实施步骤与交付

1. 替换非图片OPEN_PENDING，按类型进入统一阅读器；保留PhotoScreen/图片编辑及列表位置。
2. 接入书架打开和最近阅读记录，其他应用打开明确是备选；不可访问来源提供重选。
3. 整合外部ACTION_VIEW/ACTION_SEND单文件授权及返回路径；文件管理复制/移动与阅读记录正确区分。
4. 建立MVP格式能力表，验证图片/PDF/TXT/Markdown/EPUB/ZIP；Office未集成前显示准确支持范围。

交付包含实际改动、注册/调用接入点、接口变化、失败语义、必要测试和一份验收报告。代码中的签名以001冻结的实际契约为准，草案名称允许经记录后调整。

## 验收标准

- [ ] 每种入口都可打开→翻页→书签→退出→重进/进程重启，且同文档记录一致。
- [ ] 未授存储权限、外部临时URI、文件变化后有明确结果；微信读书和现有图片流程不回归。

- [ ] 自测命令/退出码和证据已记录；相关模块检查通过，最终整包与设备门禁由017统筹。
- [ ] 未验证项、原有失败、外部阻塞及下一步清晰；没有把代码完成写成设备验收完成。

## 执行与接力记录

### 2026-10-22 Codex 接手基础余项

- 用户批准按检查建议推进；负责人/集成者 Codex，master/cfeae53 + 保留差异。
- 最终入口：文件页/负一屏本地与最近阅读保持统一打开；外部来源不可访问时，阅读设置提供“重新选择文档”，不增加第二书架。
- 控件与显示：重选仅出现在 content URI 来源；系统选择器取消不导航，不清空旧记录；选中后进入所选文档。
- 状态与消费者：HostFilePicker 仅在系统返回 persistable + read 授权时持久保存，SourceResolver 据真实 persistedUriPermissions 标记可恢复性；不伪造永久授权。
- 迁移与保留：不变更数据库 schema；路径身份仍按规范路径区分，复制/移动不沿用另一文件书签；旧失效 URI 记录保留。
- 验收：系统选择/取消/持久授权/撤销后重选、文件页返回与复制移动区分；与 RD016 压力分开记录，未实测不勾选。
- 文件范围：ReaderScreen、ReaderErrors、HostFilePicker及相关验证；共享入口由本集成者串行修改。
- 文件页补测发现本应用FileProvider URI与LocalPath身份分岔；增加已知files_share根的双向别名，并在事务中保留较新进度、迁移全部书签。只合并可确认的同源，不按文件名或内容相似合并副本；任意第三方URI摘要关联仍待004后续收口。相关文件扩展为SourceResolver/OwnedDocumentPath、DocumentMetadata.aliases、ReadingRepository.mergeAliases及Session调用点，由本集成者串行修改。
- 2026-10-23增量：文件页本地打开与自有URI同源16/17进度/书签通过，副本/移动后新路径独立1/17；重选自有选择器通过，真实SAF因设备无DocumentsUI仍待。[DOCX与入口验收](../docs/acceptance/2026-10-23-reader-docx-and-entry.md)。完整门禁/双构建通过；未关闭所有格式/第三方URI/列表返回矩阵，保持IN_PROGRESS。

- 开始/最近更新：2026-10-22 16:15；Lead 执行本轮增量，task-7 集成、task-8 能力表
- 已改文件/接口决定：`core/common` 的 ReaderContracts.kt 与 Contracts.kt（`RecentDocument`、`RecentGateway`、`AppServices.recent`）；`feature/reader` 的 ReaderModule.kt（注册查询）；`feature/content` 的 BookshelfScreen.kt（最近阅读区、本地书统一入口）；`app` 的 MainActivity.kt（`MainIncoming` 入口诊断日志）
- 验证命令及结果/证据：`:core:common`、`:feature:reader`、`:feature:content` 单测与 detekt 通过（31s）；`check :app:assembleDebug :app:assembleRelease` 通过（3m18s，1564 tasks，EXIT=0）；设备书架最近阅读与三国演义.epub 打开通过；证据 `_ui-out/rd015/` 与 [RD015 验收](../docs/acceptance/2026-10-22-reader-entry-integration-dsh.md)
- 未覆盖/阻塞/解除条件：文件页非图片入口未重跑；复制移动后阅读记录区分未验；Office 未集成；能力表五条入口文案建议待决策
- 构建进程/设备占用/恢复：设备已回桌面，来源开关恢复为本地目录关、微信读书开；无后台构建
- 下一步：RD016 资源压力；RD017 整包与设备门禁收口；入口文案按能力表建议评估
- 接收方/集成点：下一轮集成者
- 提交/合并/发布：未提交，未升版本

## 2026-10-22 审查整改关联（最新排期）

最新产品目标由 [FIX002](TASK-FIX-002-single-bookshelf.md) 取代旧独立书架接入目标：唯一书架是负一屏，最近阅读和本地书打开迁往负一屏；旧 Destination.Bookshelf 兼容跳转，设置不保留第二套展示。原验收保留历史语境，不能作为新入口验收。本卡余项（文件页入口、复制/移动、真实授权重选、能力文案）仍需完成，不因搬迁自动关闭。验收按格式能力执行，图片/PDF 等未声明书签或搜索能力不能强行要求；不支持能力核对菜单准确。下一步先完成 FIX001 → FIX002 → FIX003，再回 RD016。

### 2026-10-23 Office入口增量

已随7d8a680集成真实Office精确VIEW/SEND MIME，README/许可与能力菜单同步；S4/P4真实Parcelable.EXTRA_STREAM+ClipData SEND及隐式VIEW证据逐包列于[集成交付](../docs/acceptance/2026-10-23-reader-office-search-integration.md)。先前“Office未集成”已被本轮取代；真实SAF持久授权/撤销重选和通用第三方URI身份仍待，不以自有FileProvider证据代替。
