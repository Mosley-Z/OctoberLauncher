# 阅读器资源与DOCX接力（2026-10-23）

用户批准本聊天按检查建议接手剩余开发。此授权覆盖后续必要实现，不需逐卡重新问开工；不包含升版本、Git提交/推送或发布。当前master/cfeae53 + 全部既有差异，未清理他人修改。

## 本批交付

- RD016刷新0/6/12、有效翻页计数和全局全刷完成版本协调；内存压力串行交给引擎，PDF释放可重建缓存。S4合成PDF200次翻页/30次开关、PSS及边界见[资源第一批](../../docs/acceptance/2026-10-22-reader-resource-first.md)，整卡仍IN_PROGRESS。
- RD011 DOCX双模式READY_FOR_REVIEW：正文段落/表格/图片，固定离线docx-preview/JSZip，原版式按宽和滚动，跳页/搜索/进度；许可/白名单/预算已接。复杂包、完整失败与Office压力/P4未齐，不宣称全保真。
- RD015新增URI重选；持久授权仅使用系统真实标志。S4无DocumentsUI，实际自有选择器成功不等同真实SAF。已知files_share URI/本地路径共用身份，事务保留较新记录与全部书签；副本和移动新路径独立。归档条目继承父别名。其他Provider摘要关联仍待RD004。
- [DOCX/入口验收](../../docs/acceptance/2026-10-23-reader-docx-and-entry.md)包含最终APK摘要、来源版本、测试、设备和限制。完整check及debug/release通过，reader85例0失败；最终日志 `_analysis/verify/reader-takeover-final-gate.log`。

## 现场与状态恢复

- 工具链取toolchain.path；标准View观测/点击经scripts/android-ui.ps1的layout/find/click，Canvas经annotate/resolve/tap -Label。S4在线，P4未连接；下轮重新核实，不沿用设备占用。
- 最终S4为本批release、HOME；最后文件目录恢复 `/storage/emulated/0`，网格等其他偏好未改，全刷6。合成DOCX和移动副本保留在Download，后续可用于表格/Office对照；不含用户正文。没有导出配置全量、密钥或用户数据库。
- 构建已退出，Codex释放构建/ADB；观察到的Python进程属于其他项目资源转换，不操作它们。Java为Gradle/Kotlin守护进程，不应按活跃构建误杀。
- CLI一次取消文本标号resolve失败；后续安装会结束旧选择Activity，不把那次标号失败写成取消按钮通过。S4文件目录恢复已由截图确认。

## 下一步

1. RD012：表格工作簿/CSV窗口阅读、sheet/row/column定位、公式缓存语义、预检预算；复用000固定候选资源，按真实样本验证，CSV按流解析引号/跨行。
2. RD013：PPTX固定资源链、按页挂载/卸载、提纲、缺失图形提示和页定位；随后RD014统一搜索交互与跨未加载内容、高亮/计数/取消。
3. 同步补RD000兼容矩阵、RD004第三方来源身份、RD015列表/真实SAF等遗留；RD016补文本/EPUB/Markdown/Office同压力矩阵与物理刷新观测；RD017最终同APK双机/断网/异常/锁屏等门禁。
4. 阅读器刷新仍经RefreshScheduler；不要恢复FIX006已取消的独立书架快照/自动计时器。018–020后置不自动扩展。

新Docx定位器及DocumentMetadata.aliases已增量冻结到task/CONTRACTS.md。共享Registry、ReaderScreen、Web宿主、契约和许可由集成者串行改；保持其他功能现场。下一卡先读PROJECT/STATUS/本卡/35流程/实际差异。
