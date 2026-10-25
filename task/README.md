当前[REL001 V1.0.0本地准备](TASK-REL-001-v1-stable-release.md)已完成正式签名、版本及五件套；用户自行处理旧历史，提交推送及远端Release暂缓。[验收](../docs/acceptance/2026-10-26-rel001-v1-release-preparation.md)。

此前[UX152 P4通知返回跳动](TASK-UX-152-p4-statusbar-transition.md)已交付固定全屏View布局；[横竖过渡验收](../docs/acceptance/2026-10-26-ux152-p4-statusbar-transition.md)。

此前[UX151 P4通知下滑](TASK-UX-151-p4-notification-gesture.md)已通过现有无障碍服务交付：真实栏隐藏时自绘栏下滑展开通知，未连接时真实栏回退；[release横竖验收](../docs/acceptance/2026-10-26-ux151-p4-notification-gesture.md)。

此前[UX150 P4隐藏栏诊断](TASK-UX-150-p4-hidden-status-bar.md)已完成最小实验，未找到可替代79px回退的窗口参数；[结果与边界](../docs/acceptance/2026-10-25-ux150-p4-statusbar-diagnosis.md)。

此前[UX149 沉浸与多页组件](TASK-UX-149-immersive-device-pages-plan.md)已完成：B版及七项实施、最后左列20px调整、双机最终release安装和原配置恢复；[最终验收](../docs/acceptance/2026-10-25-ux149-implementation.md)。

本轮[UX148 P4安全区与本记整改](TASK-UX-148-safe-area-note-expansion.md)已交付，承接用户确认的UX147方案并追加元信息整理及WereadMoji本地换条策略。[验收与边界](../docs/acceptance/2026-10-25-ux148-safe-area-note-expansion.md)：P4最终release已安装；S4离线，盖板手指效果待用户实触。

本轮[UX147 已选清理与横屏评估](TASK-UX-147-settings-cleanup-landscape-assessment.md)已交付：删除已选说明/主题入口/最近应用与权限；P4最终包验证，S4离线。第二部分仅给[待确认方案](../docs/acceptance/2026-10-25-ux147-cleanup-landscape-assessment.md)，未改横屏/本记行为。

本轮已交付：[UX146 设置入口合并与文案审计](TASK-UX-146-settings-consolidation.md)。指定删改完成，其他说明/重复项/权限仅列待选；[验收与清单](../docs/acceptance/2026-10-25-ux146-settings-consolidation.md)。

本轮已交付：[UX145 最近十书循环、设置首页与应用/文件操作](TASK-UX-145-desktop-settings-file-actions.md)。主代理直接实施、审查及双机验证；最终release已安装，原测试状态恢复。[验收及边界](../docs/acceptance/2026-10-25-ux145-desktop-settings-file-actions.md)。

当前批次：[UX139小组件升级集成](TASK-UX-139-widget-upgrade-integration.md)已完成本轮授权集成交付。UX143/144由DSH Tauri实施，主代理审查整改、统一门禁和P4/S4验证；首页自动竖2行3列、横4行2列，显式选择保留。最终包/恢复/证据及验证边界见[集成验收](../docs/acceptance/2026-10-25-ux143-144-integration.md)。旧卡除S4测试外不扩做，以下早期记录保留当时边界。

UX140已按2026-10-25反馈完成数字圈、中文逐字时间、日期左齐与默认数字宽度微调；[新包验收](../docs/acceptance/2026-10-25-ux140-clock-adjustments.md)替代对应旧细节，S4本轮未操作。

UX140最新追加：E三行时/日期/分、F距线增加，A–F独立固定样式provider进入长按统一组件清单；设置钟面区与旧独立页删除。[最新验收](../docs/acceptance/2026-10-25-ux140-direct-clock-picker.md)为准，旧clock配置兼容，141–144未实施。

| 卡 | 范围 | 依赖/共享文件交接 |
| --- | --- | --- |
| [UX139](TASK-UX-139-widget-upgrade-integration.md) | 公共契约/迁移/注册、批次集成与统一验收 | 主代理串行；最终依赖140–144交付 |
| [UX140](TASK-UX-140-clock-styles.md) | A–F六款时钟 | 无前置；现有时钟入口，公共配置归139 |
| [UX141](TASK-UX-141-todo-week-calendar.md) | 月历改名、同源待办周历 | 无前置；待办/注册契约归139 |
| [UX142](TASK-UX-142-grid-and-widget-reflow.md) | 横3×4/竖4×3、关闭组件补位 | 无前置；先负责HomeView几何/分页 |
| [UX143](TASK-UX-143-widget-gesture-cleanup.md) | usage删除、组件切换手势删除、图片四种滑动模式 | 142交接HomeView后执行 |
| [UX144](TASK-UX-144-reading-carousel-stats.md) | 本书/沉浸双向轮播、账号今日总量与连续天数 | 143交接手势后执行 |

当前追加：[UX138 文件入口显隐](TASK-UX-138-files-entry-visibility.md)：新装默认显示、旧配置一次恢复；既有隐藏管理提供文件开关，与安装应用统一为开显示关隐藏。

当前追加：[UX137 APK打开选择](TASK-UX-137-apk-open-choice.md)：文件页APK先选择安装或查看结构，安装交系统、结构交原阅读器；其他压缩包沿用默认查看。

当前追加：[UX136 文件宫格与缩略图](TASK-UX-136-files-grid-thumbnails.md)：缩略图优先宫格与动态容量、图片无“图”角标、EPUB复用共享封面及缓存；具体范围和状态以任务卡为准。

当前追加：[UX135 普通横屏微调](TASK-UX-135-landscape-spacing-time.md)：累计左移30px，组件原位，展开右界跟随首页；小时/横置冒号/分钟分行。实现、统一工程和P4有限验证已交付，未覆盖见卡。

当前追加：[UX134 普通横屏侧栏整改](TASK-UX-134-landscape-sidebar.md)：应用区左移15px、两列阅读层边界、本记扁圆角大字按钮、切换/标题/刷新/更新时间竖排；仅普通横屏。

当前追加：[UX133 桌面收尾整改](TASK-UX-133-desktop-followup-corrections.md)。第1/2/5项已完成限定验收；用户2026-10-24确认第3项周/月空间与第4项横屏右向阅读层方案，当前实现与统一工程/双机限定验证已交付，未覆盖见卡与验收。新增功能/界面必须先经用户确认。

当前入口：[UX120 桌面整改集成](TASK-UX-120-correction-integration.md)。UX121–132实现与本轮统一验证已交付；周/月严格对[原C稿](../design/ux-corrections-proposal/index.html)，本记/待办对[修订稿](../design/ux-corrections-proposal/revision-2.html)。用户随后取消标题长按与临时隐藏，已删除并完成新包工程和双机受影响回归。单卡状态为权威；[验收](../docs/acceptance/2026-10-23-ux120-corrections.md)区分前包完整矩阵与最终包补测、列明未覆盖项。双机均恢复释放，无升版/提交/推送/发布。

| 卡 | 用户项 | 依赖/文件协调 |
| --- | --- | --- |
| [UX121 文档与权限治理](TASK-UX-121-documentation-governance.md) | 1 | 先核实硬权限能力，文档由专职角色维护 |
| [UX122 沉浸负一屏](TASK-UX-122-immersive-negative-gesture.md) | 4 | HomeView手势；交接后由UX127接长按菜单 |
| [UX123 横屏其他页全宽](TASK-UX-123-landscape-full-width-pages.md) | 5 | DesktopMetrics先行 |
| [UX124 横屏首页2×4](TASK-UX-124-landscape-home-eight-slots.md) | 6 | UX123交接后串行修改同文件 |
| [UX125 壁纸拉伸与图片转向](TASK-UX-125-wallpaper-stretch-rotation.md) | 7 | 配置/主题由集成者串行；UX128接透明策略 |
| [UX126 本月默认热力图](TASK-UX-126-month-heatmap-default.md) | 8 | 保留显式旧选择，双端默认统一 |
| [UX127 沉浸桌面菜单](TASK-UX-127-immersive-desktop-menu.md) | 2 | UX122交接手势后 |
| [UX128 沉浸纯透明](TASK-UX-128-immersive-transparent-wallpaper.md) | 3 | UX125交接壁纸后 |
| [UX129 十雨设置重组](TASK-UX-129-project-settings-regroup.md) | 9 | UX132后；系统设置不变 |
| [UX130 横屏组件](TASK-UX-130-landscape-weread-widgets.md) | 10 | UX131后；竖屏/本书不变 |
| [UX131 横屏状态栏侧边距](TASK-UX-131-landscape-status-side-spacing.md) | 新增 | UX124后，DesktopMetrics串行 |
| [UX132 删除KOReader集成](TASK-UX-132-remove-koreader-integration.md) | 新增 | 主代理串行公共契约/旧配置迁移 |

前轮精修：[UX116沉浸桌面细节](TASK-UX-116-immersive-refinement.md)，[验收及边界](../docs/acceptance/2026-10-23-immersive-refinement.md)。前序整改：[UX115入口与视觉集成](TASK-UX-115-visual-integration.md)，分卡[UX112](TASK-UX-112-entry-cleanup.md) / [UX113](TASK-UX-113-note-continuity.md) / [UX114](TASK-UX-114-immersive-design-alignment.md)。

# 阅读器任务看板与执行入口

当前追加：[UX111 总结与完整离线使用说明](TASK-UX-111-summary-and-guide.md)，原“怎么用”入口保持不变。

## 阅读桌面实施批次（2026-10-23 已授权）

用户已确认本记覆盖展开、普通组件轮换与沉浸固定本书；授权1/2/3/4/5/6/7/9实施，8搁置。本批Codex集成，三个GPT-6.1 Sol / low子智能体按文件分工；Gradle串行、每台设备单一操作者，保留现有修改，不升版或发布。按依赖推进，不因阶段交付重复请求确认。

实现已交付，卡保留READY_FOR_REVIEW；最新为单一本机录入二维码。见[最终验收与边界](../docs/acceptance/2026-10-23-reading-desktop-implementation.md)，8仍DEFERRED。

| 卡 | 范围 | 前置 |
| --- | --- | --- |
| [UX100](TASK-UX-100-integration.md) | 阅读桌面集成与双机交付 | 101、102、103、104、105、106、107、109、110 |
| [UX101](TASK-UX-101-onboarding.md) | 首次引导与微信读书连接 | 102 |
| [UX102](TASK-UX-102-immersive.md) | 沉浸本书桌面与应用抽屉 | 106 |
| [UX103](TASK-UX-103-icons-theme.md) | 透明图标与壁纸主题 | 106 |
| [UX104](TASK-UX-104-list-layout.md) | 列表对齐数量与左右边距 | 106 |
| [UX105](TASK-UX-105-landscape.md) | 正反横屏与左右分区 | 102、104、106 |
| [UX106](TASK-UX-106-grid-layout.md) | 首页与其他页独立行列 | 无 |
| [UX107](TASK-UX-107-widget-gestures.md) | 普通桌面组件直切手势 | 102、110 |
| [UX108](TASK-UX-108-sleep-wallpaper.md) | 休眠关机壁纸后置研究 | 无 |
| [UX109](TASK-UX-109-paper-ui.md) | 纸页书桌视觉落地 | 101、102、103、104、105、106、107、110 |
| [UX110](TASK-UX-110-note-overlay.md) | 本记宿主覆盖展开 | 106 |


2026-10-23最终收口：1.0.31/51、源码1ca51be；RD000–017/FIX001–006 DONE，RD018–020 DEFERRED。双机最终包与字段恢复/释放完成，完整门禁reader137/data128每变体零失败。PDF/PPTX冷首屏仍超目标及局部未测如实保留；详见[最终收口](../docs/acceptance/2026-10-23-reader-final-closure.md)。无正式签名/Release，设备与构建均无占用。

2026-10-23续接：0a1534c、7d8a680及2da644b已提交；RD012/013/014已集成Office与统一搜索，最终门禁reader各132例0失败。477包P4工作簿/S4 PPTX各200有效翻页完成；2da644b仅修外部入口任务复用，fbd4包双机各30真实隐式开关与必要smoke完成，设备已恢复并释放。详见[集成交付](../docs/acceptance/2026-10-23-reader-office-search-integration.md)，RD016/017保留真实SAF/物理EPD及完整状态矩阵未覆盖，设备租用以STATUS为准。

最新阅读器批次（2026-10-23）：用户批准接手剩余开发。RD016第一批、RD015入口/已知同源身份增量已验证；RD011 DOCX双模式READY_FOR_REVIEW。见[DOCX与入口验收](../docs/acceptance/2026-10-23-reader-docx-and-entry.md)、[接力](../memory/sessions/2026-10-23-reader-resources-docx-codex.md)。后续RD012→RD013→RD014及资源/最终门禁；P4、真实SAF和复杂格式矩阵仍待，未关闭全方案。

最新交付：FIX005/006均READY_FOR_REVIEW；[验收](../docs/acceptance/2026-10-22-shared-shelf-refresh.md)与[交接](../memory/sessions/2026-10-22-shared-shelf-refresh-codex.md)。本书共享缓存/刷新取代FIX004独立自动模式，下方“当前”旧记录按历史理解；后续依安排回RD016。


当前最新：[FIX006 书架与本书共用缓存/刷新](TASK-FIX-006-shared-shelf-refresh.md)，取消FIX004独立自动模式，连同FIX005一起交付。


当前用户反馈优先：[FIX005 最近在读排序与书封底部清理](TASK-FIX-005-shelf-recency.md)，Codex负责。


最新执行：[FIX004 书架标题操作与刷新方式](TASK-FIX-004-shelf-controls-refresh.md)，Codex已交付并标记READY_FOR_REVIEW，[验收](../docs/acceptance/2026-10-22-shelf-refresh-controls.md)/[最新交接](../memory/sessions/2026-10-22-shelf-refresh-codex.md)；后续按依赖回RD016。本卡取代 FIX002 中标题控件仍位于设置里的旧交付目标，保留其修复成果。


创建于2026-10-22。21张卡创建时都是TODO；2026-10-22已开始原生阅读器实现；实时状态以每张卡的状态字段为准。本看板不复制动态状态。文件存在不等于已向Agent派工。

先读 [PROJECT](../PROJECT.md)、[STATUS](../memory/STATUS.md)、自己的卡、[开发流程](../docs/design/35-development-workflow.md)。设计来源是 [34总方案](../docs/design/34-files-reader-upgrade-plan.md)。通用任务模板在 [task模板](../docs/templates/task.md)。

性能前置任务：[PERF-001](TASK-PERF-001-s4-performance.md)已完成，验证边界见卡片及验收报告。

## 当前接管结果（2026-10-22）

三 FIX 已由 Codex 接管纠正并交付 S4/debug/release 证据；单卡为 READY_FOR_REVIEW，P4 未连接，不写全部 DONE。[最新接力](../memory/sessions/2026-10-22-fix-takeover-codex.md)、[验收](../docs/acceptance/2026-10-22-fix-takeover-codex.md)。用户已明确本轮以 S4 收尾、P4 延后；后续可按依赖回 RD016，最终双机门禁补齐 P4；不要按下方历史 TODO 描述重复实施。用户已授权的接管继续有效。

## 当前插队批次（2026-10-22 用户最新指令）

DSH 下一步改为 **FIX001 → FIX002 → FIX003 → RD016 → 按原依赖继续**。当前已开始的操作先安全收尾，核实是否有活跃执行者，不中断半途写入或另开重复构建。本节保留原排期；实施结果以顶部接管结果和单卡最新记录为准。

| 顺序 | 实施卡 | 前置与交付 |
| --- | --- | --- |
| 1 | [FIX001 阅读器两处修复](TASK-FIX-001-reader-review.md) | 基于现有 RD003/004/015 增量，修重排调度与最近阅读独立性 |
| 2 | [FIX002 唯一负一屏书架](TASK-FIX-002-single-bookshelf.md) | 依赖 FIX001；迁移最近阅读，拆分展示来源与数据开关 |
| 3 | [FIX003 内置入口图标](TASK-FIX-003-special-icons.md) | 依赖 FIX002 的共享入口交接；支持设置/文件换图与主题 |

这三个 FIX 是本批串行优先级，不要求无关 Office 引擎新增技术依赖。RD003/004/014 的交叉范围、RD015/016/017 的后续责任已在各卡追加，不形成 RD015 ↔ FIX 的依赖环。最新交接见 [整改交接](../memory/sessions/2026-10-22-review-remediation-to-dsh.md)。

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

本轮交接：[Codex→DSH](../memory/sessions/2026-10-22-reader-to-dsh-handoff.md)。先核实未提交差异与设备，无需重开已结束构建。

## 产品网站（独立范围）

- [WEB-001 产品站本地预览](TASK-WEB-001-product-site.md)：负责人Codex，状态以卡为准；与Android构建/设备任务无依赖。

## 阅读桌面设计与可行性（独立范围）

- [UX-001 阅读桌面方案、设备探索与设计稿](TASK-UX-001-reading-desktop-proposal.md)：Codex负责；1/2/3/4/6/7具体方案、5/8可行性、9设计稿及本记展开分析，业务实施待用户确认；不自动接续RD或FIX业务。

UX140最新E时分逐字竖排、日期横排保持，高度自适应间隔与字号已完成；[竖排验收](../docs/acceptance/2026-10-25-ux140-vertical-clock.md)。S4延期、141–144未实施。

UX141代码审查整改及E日期字号调整已完成，受影响范围与未测项见[最新审查验收](../docs/acceptance/2026-10-25-ux141-review.md)。

UX142审查、微读刷新与E两字小时/日期字号本轮完成；[最新验收](../docs/acceptance/2026-10-25-ux142-review-refresh.md)。此前E字号误改共用路径已纠正，143/144未实施。
