# 验收记录索引

现存架构见[架构导航](../architecture/README.md)，生效工作方法见[开发导航](../development/README.md)，历史分类见[归档导航](../archive/README.md)。本目录不承担逐轮更新日志。

返回[文档导览](../README.md)。新记录从[验收模板](../templates/acceptance.md)建立，必须关联源码、任务和证据；原始日志/截图仍放原证据目录。

`_analysis/`原始设备数据与分析证据仅保留维护者本地，不随公开源码分发；历史记录中的相关路径用于本地核对。

| 文件 | 主题 | 使用边界 |
| --- | --- | --- |
| [2026-10-25-ux148-safe-area-note-expansion.md](2026-10-25-ux148-safe-area-note-expansion.md) | P4安全区、本记整卡展开与局部换条 | P4方向/真实界面和合成Widget探针，S4离线；物理盖板仍需实触 |
| [2026-10-25-ux147-cleanup-landscape-assessment.md](2026-10-25-ux147-cleanup-landscape-assessment.md) | 已选设置清理与横屏/本记方案 | 最终P4与APK权限验证，S4离线；第二部分只读评估待确认 |
| [2026-10-25-ux146-settings-consolidation.md](2026-10-25-ux146-settings-consolidation.md) | 设置合并、说明/重复项/权限待选审计 | 双机页面与 P4 真实解锁；区分 debug 交互和最终 release 入口复核 |
| [2026-10-25-ux145-desktop-settings-file-actions.md](2026-10-25-ux145-desktop-settings-file-actions.md) | 最近十书循环、设置主页与卸载/文件操作 | 双机真实系统卸载、文件详情和删除；区分debug功能与release烟测 |
| [2026-10-25-ux143-144-integration.md](2026-10-25-ux143-144-integration.md) | 小组件最终集成与双机验证 | 首页自动布局、图片手势、双向封面与账号统计；区分实体触屏、Android探针和JVM边界 |
| [2026-10-25-ux142-direction-grid-and-widget-reflow.md](2026-10-25-ux142-direction-grid-and-widget-reflow.md) | UX142方向十二宫格与关闭组件补位 | 其他页方向容量、无组件整页版面与页数收敛；S4、备份导入端到端与美化逐屏点验另行组织 |
| [2026-10-25-ux141-todo-week-calendar.md](2026-10-25-ux141-todo-week-calendar.md) | UX141月历改名与共享待办周历 | 七日窗口、箭头语义、投影排序与共享存储；S4、竖屏逐条点验与同屏实时同步另行组织 |
| [2026-10-24-ux138-files-entry-visibility.md](2026-10-24-ux138-files-entry-visibility.md) | UX138文件入口恢复与显隐 | 一次迁移及既有开关统一语义；本包APK/ZIP证据单独记录，不改UX137历史 |
| [2026-10-24-ux137-apk-open-choice.md](2026-10-24-ux137-apk-open-choice.md) | UX137 APK打开选择 | 工程通过，P4覆盖安装；文件入口待用户，选择及系统安装等实机分支未测 |
| [2026-10-24-ux136-files-grid-thumbnails.md](2026-10-24-ux136-files-grid-thumbnails.md) | UX136文件宫格与EPUB封面 | 缩略图优先最终包，S4横竖屏样本；P4文件入口及Picker未实机覆盖 |
| [2026-10-24-ux135-landscape-spacing-time.md](2026-10-24-ux135-landscape-spacing-time.md) | UX135横屏应用位置与时间分行 | 应用累计左移、展开右界跟随及HH/冒号/mm分行；只认本轮包与实际验证 |
| [2026-10-24-ux134-landscape-sidebar.md](2026-10-24-ux134-landscape-sidebar.md) | UX134普通横屏侧栏与本记纸面 | 当前实现、模式边界及独立时间来源；工程及设备证据以本轮记录为准 |
| [2026-10-24-ux133-approved-landscape.md](2026-10-24-ux133-approved-landscape.md) | UX133获批横屏周月空间及本记展开 | 第3/4项确认后实现与本轮统一门禁；设备结果按当前包记录，不改写首轮历史 |
| [2026-10-24-ux133-desktop-followup.md](2026-10-24-ux133-desktop-followup.md) | UX133桌面收尾整改 | 授权项1/2/4/5当前实现待统一验证；周月空间第3项仅方案 |
| [2026-10-23-immersive-refinement.md](2026-10-23-immersive-refinement.md) | UX116沉浸桌面精修 | 用户背景/图标、五层书封、真实缓存统计与最终双机专项 |
| [2026-10-23-reading-desktop-development-summary.md](2026-10-23-reading-desktop-development-summary.md) | 阅读桌面本轮开发总结 | 实现清单、双机证据、未覆盖与完整帮助页 |
| [2026-10-23-reading-desktop-visual-alignment.md](2026-10-23-reading-desktop-visual-alignment.md) | UX112–115入口与视觉整改 | 重复入口、连续展开、沉浸构图与双机边界 |
| [2026-10-23-launcher-guide.md](2026-10-23-launcher-guide.md) | UX111完整离线说明 | 原怎么用入口、目录/滚动/返回与打包核对 |
| [2026-10-23-reader-optical.md](2026-10-23-reader-optical.md) | S4/P4原片逐帧光学阶段测量 | 各20翻页/20手动全刷；实际PTS、摄影配准与阈值敏感性，不含触点等待 |
| [2026-10-23-reader-final-closure.md](2026-10-23-reader-final-closure.md) | 1.0.29–1.0.31开发与最终验收收口 | 双机恢复/卡关闭；性能未达和局部未测明列 |
| [2026-10-23-reader-final-s4.md](2026-10-23-reader-final-s4.md) | S4最终候选性能、复杂样本与状态 | 802b/215b基线和4e98最终包分列；光学视频另行分析 |
| [2026-10-23-reader-final-p4.md](2026-10-23-reader-final-p4.md) | P4真实授权、同源、状态与压力 | 系统授权机制与合成picker边界、逐APK结果分列 |
| [2026-10-23-reader-uri-identity.md](2026-10-23-reader-uri-identity.md) | 第三方PFD与本地路径身份核对 | 不按同名/内容猜测，不绕过原URI授权读取 |
| [2026-10-23-reader-upstream-fixtures.md](2026-10-23-reader-upstream-fixtures.md) | 官方复杂Office输入来源与许可 | 夹具结构不代表实机渲染通过 |
| [2026-10-23-reader-card-audit.md](2026-10-23-reader-card-audit.md) | RD000–017与FIX001–006关闭审计 | 审计时点缺口；不代替最终设备验证 |
| [2026-10-23-reader-search-s4.md](2026-10-23-reader-search-s4.md) | 最终统一搜索版S4回归与PPTX压力 | 最终477包；执行计数、恢复及未覆盖见报告 |
| [2026-10-23-reader-search-p4.md](2026-10-23-reader-search-p4.md) | P4搜索、CSV升级与工作簿压力 | 逐项区分中间包与最终477包 |
| [2026-10-23-reader-office-search-integration.md](2026-10-23-reader-office-search-integration.md) | Office、统一搜索、旧CSV迁移与系统入口集成 | 最终门禁132例及双机报告汇总；物理刷新/真实SAF等边界单列 |
| [2026-10-23-reader-office-s4.md](2026-10-23-reader-office-s4.md) | S4 PPTX原版式/提纲/200页与短压 | Office首包，非最终统一搜索包 |
| [2026-10-23-reader-office-p4.md](2026-10-23-reader-office-p4.md) | P4工作簿/CSV/PPTX首轮 | Office首包，非最终统一搜索包 |
| [2026-10-23-reader-p4-baseline.md](2026-10-23-reader-p4-baseline.md) | P4已提交基线补验 | TXT/PDF/Markdown/EPUB/ZIP/DOCX代表用例 |
| [2026-10-23-reader-sheets.md](2026-10-23-reader-sheets.md) | 表格专属实现与预算/搜索证据 | 子任务交付；最终设备由集成报告汇总 |
| [2026-10-23-reader-slides-subtask.md](2026-10-23-reader-slides-subtask.md) | PPTX专属实现与固定资源链 | 子任务交付；最终设备由集成报告汇总 |
| [2026-10-23-reader-docx-and-entry.md](2026-10-23-reader-docx-and-entry.md) | DOCX双模式、自有URI/路径进度及书签迁移 | S4/debug/release首轮；复杂包、真实SAF、P4及全格式门禁待补 |
| [2026-10-22-reader-resource-first.md](2026-10-22-reader-resource-first.md) | RD016刷新设置、低内存与基础恢复第一批 | S4合成PDF压力；Office/P4/最终APK矩阵待补 |
| [2026-10-22-shared-shelf-refresh.md](2026-10-22-shared-shelf-refresh.md) | FIX005/006 最近在读、封面清理、与本书共用缓存/刷新 | 取代独立自动模式；S4及门禁，P4待补 |
| [2026-10-22-shelf-refresh-controls.md](2026-10-22-shelf-refresh-controls.md) | FIX004 书架标题操作与刷新模式 | 本轮代码门禁及S4，P4和长间隔矩阵待补 |
| [2026-10-22-project-management.md](2026-10-22-project-management.md) | 本轮管理整理、文档迁移与任务拆分 | 只验文档管理产物，不代表阅读器已实施 |
| [09-M0骨架记录.md](<09-M0骨架记录.md>) | M0 骨架执行记录 | 历史版本记录，不代表当前源码已验收 |
| [12-验收记录.md](<12-验收记录.md>) | 验收记录 | 历史版本记录，不代表当前源码已验收 |
| [14-本批实施记录.md](<14-本批实施记录.md>) | 14 本批实施记录：inkOS 对照后的六项优先改造、三项排期功能与七项设计优化 | 历史版本记录，不代表当前源码已验收 |
| [18-handoff-v1.0.1-and-weread-v1.3.2.md](<18-handoff-v1.0.1-and-weread-v1.3.2.md>) | 交接文档：十雨桌面 V1.0.1 与微读墨记 V1.3.2-beta 同步开发 | 历史版本记录，不代表当前源码已验收 |
| [202610210141来自CODEX的CODE REVIEW.md](<202610210141来自CODEX的CODE REVIEW.md>) | 202610210141来自CODEX的CODE REVIEW | 历史版本记录，不代表当前源码已验收 |
| [26-device-adaptation-report.md](<26-device-adaptation-report.md>) | 26 双设备适配检查报告 | 历史版本记录，不代表当前源码已验收 |
| [27-weread-page-parity.md](<27-weread-page-parity.md>) | 微读墨记与十雨内置页逐页对照 | 历史版本记录，不代表当前源码已验收 |
| [28-weread-batch1-remediation.md](<28-weread-batch1-remediation.md>) | 微读墨记迁移保真：第一批修复记录 | 历史版本记录，不代表当前源码已验收 |
| [29-weread-batch2-remediation.md](<29-weread-batch2-remediation.md>) | 微读墨记迁移保真：第二批修复记录 | 历史版本记录，不代表当前源码已验收 |
| [30-weread-batch3-remediation.md](<30-weread-batch3-remediation.md>) | 微读墨记功能集成：第三批修复记录 | 历史版本记录，不代表当前源码已验收 |
| [31-weread-batch4-remediation.md](<31-weread-batch4-remediation.md>) | 微读墨记功能集成：第四批修复记录 | 历史版本记录，不代表当前源码已验收 |
| [2026-10-22-reader-native-first.md](2026-10-22-reader-native-first.md) | 阅读器原生文本首轮 | 首轮设备证据，不覆盖后续增量 |
| [2026-10-22-reader-epub.md](2026-10-22-reader-epub.md) | 阅读器 EPUB 渲染 | 设备证据以记录时的驱动为准 |
| [2026-10-22-reader-pdf.md](2026-10-22-reader-pdf.md) | 阅读器 PDF 隔离渲染 | 不构成整包发布验收 |
| [2026-10-22-reader-zip.md](2026-10-22-reader-zip.md) | 阅读器 ZIP 归档 | 归档来源，不覆盖 Office 渲染 |
| [2026-10-22-reader-sources-002.md](2026-10-22-reader-sources-002.md) | RD-002 增量：ZIP 内部标记、ACTION_SEND、授权与缺失矩阵 | 设备用例以 S4 与合成探针为准，SAF 系统撤销后重选未覆盖 |
| [2026-10-22-reader-shell-state-text-dsh.md](2026-10-22-reader-shell-state-text-dsh.md) | RD-003/004/005 增量：旋转重排、书签编辑与文本索引 | 门禁通过；设备矩阵因 S4 离线未执行 |
| [2026-10-22-android-cli-tooling.md](2026-10-22-android-cli-tooling.md) | Android CLI 界面观测工具接入 | 只在 S4 验证；桌面自绘元素必须走标注截图与 resolve |
| [2026-10-22-reader-mvp-capability-dsh.md](2026-10-22-reader-mvp-capability-dsh.md) | RD015 MVP 格式能力表 | 源码只读核对；Office 三种格式仅到容器识别 |
| [2026-10-22-reader-entry-integration-dsh.md](2026-10-22-reader-entry-integration-dsh.md) | RD015 增量：最近阅读契约、书架入口与本地书统一打开 | 设备只验书架两个入口与外部 VIEW 回归；整包门禁由 RD017 统筹 |

- [2026-10-22 FIX 接管审查与验收](2026-10-22-fix-takeover-codex.md)：三卡纠正、最终整包/S4 证据，P4 与完整矩阵待补。
