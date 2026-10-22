# 2026-10-23 Office与统一搜索集成交付

- 集成者：Codex；sheets/PPTX专属实现与S4/P4验证按任务分工。
- 基线：`master/0a1534c`，后续Office及RD014差异在当前工作区；保留网站、未验证Wrapper和工具链偏好等无关修改。
- 关联：[RD012](../../task/TASK-RD-012-sheets.md)、[RD013](../../task/TASK-RD-013-slides.md)、[RD014](../../task/TASK-RD-014-search.md)、[RD017](../../task/TASK-RD-017-release-gate.md)。
- 未推送、未升版、未发布；仍为1.0.28/48开发产物。

## 已实现行为

| 格式 | 实际能力与边界 |
| --- | --- |
| CSV | 原生流式引号/跨行/空字段、四种编码、20×10窗口、行列定位、单元格搜索；稀疏锚点重开仍需跳读前缀 |
| XLSX/XLS/ODS | 固定SheetJS0.20.3、原生窗口与合并格、切表/定位、已有公式缓存；无缓存公式明确占位，不重新计算公式。XLS仅BIFF8，CFB扩展DIFAT不支持 |
| PPTX | 固定PPTXjs1.21.1及完整本地依赖；每次只传当前slide关系闭包，原版式/提纲、跳页/搜索/书签/恢复。复杂图表、嵌入及音视频/EMF/WMF有提示 |
| 统一搜索 | TXT/Markdown/EPUB/DOCX/CSV/工作簿/PPTX全内容完整计数、上下个、64条结果窗口与按需重扫、取消、受控高亮；表格按匹配单元格计数；PDF保持无搜索入口 |

工作簿和PPTX在解析前约束压缩8MiB/展开32MiB；表格还限制10万格、100sheet、10万行/1024列，XML实体与实际展开/CRC预检，XLS按CFB/BIFF记录预算。超预算可返回或改用其他应用，不声称兼容所有Office文档。

定位器仅增加Sheet/Slide类型，旧SQLite结构、来源身份、书签及最近阅读不重建。搜索查询和结果只在会话内存；扫描不保存进度，显示命中后才记进度。取消沿现有唯一动作队列/generation及JS token，新结果不接收任意来源/Locator。

系统VIEW/SEND清单已精确登记DOCX/XLSX/XLS/ODS/PPTX及application/csv、application/xml；没有登记旧DOC/PPT或任意二进制通配。固定资源摘要、完整许可随包保留并进入关于页。

## 构建与自动检查

| 项目 | 结果与证据 |
| --- | --- |
| reader格式/模块check | 通过；`_analysis/verify/office-next/search-format-3.log`、`search-check-2.log` |
| 全仓check、debug/release | 最终PPTX跨run修复版退出0，23秒、1564任务；`final-gate-4.log` |
| reader JVM | debug/release各132例、0失败；含CSV/BIFF/XML预算、跨窗多编码/代理尾去重、计数/分页、200页/重复词、协议边界及CSV旧字节锚点迁移 |
| JS受限扫描 | Node计数/64条/实际取消/旧包无回传/语法通过；`search-host-test.cjs`及sheets专属日志；不当作WebView设备结论 |
| 文档检查 | 交付时补最终检查；已知两个删除源码引用只WARN |

最终APK：`_analysis/verify/office-next/office-search-final-release.apk`，2169968字节，SHA-256 `477ab628f67c8670428ae6d6e3b471f57236a2bfaafd2fe9c82df246944d50be`。前一搜索版`8809a550…`、Manifest版`d633e028…`、CSV兼容版`fcf3bc96…`分别保留；最终版包含旧Text字节锚点/编码向CSV逻辑记录迁移，并修正同段加粗拆run导致PPTX漏搜，设备报告逐项区分。源码校验清单在`office-next/source-manifest.json`，交付前以最终现场更新。

## 设备证据与待验

- Office首包双机证据：[S4](2026-10-23-reader-office-s4.md)、[P4](2026-10-23-reader-office-p4.md)。该包`bf8b834c…`不替代统一搜索版。
- 最终版双机搜索/系统入口及200翻页/30开关正在执行；结果由各自验收及RD017汇总，未完成前不记整卡DONE。
- 真实SAF持久授权重选、通用第三方URI身份关联、复杂真实Office集合、全桌面网络/锁屏矩阵及面板物理EPD耗时仍有边界，不能由JVM/截图或小合成样本推断完成。
- S4短测renderer code-1已由同PID系统`isolated not needed`日志佐证为关闭后回收；仍不据短测声明长压无泄漏。


### 升级与普通样式检索整改证据

P4在真实旧基线安装状态中准备两份独立CSV：UTF8 BOM与GB18030，逻辑142行前含quoted newline。旧版保存77%/76%与旧Text书签；覆盖升级到fcf3后，两份直接恢复142行1列，旧书签二级跳转一致，GB无需重选编码。最终477版再抽恢复，不用数据库注入。PPTX旧8809对加粗拆两run的完整词漏搜有设备反例，477版以同段拼接、段落换行修正，独立JVM用例与设备复验分开记录。


固定第三方资源原样保留其上游空白（如PPTXjs及divs2slides行尾空格），不为Git空白门禁改动冻结字节；10项JS/CSS实际SHA256与固定清单逐项一致。自有代码/宿主/文档通过diff --check，精确vendor及完整许可副本排除名单与摘要核对留在office-next/whitespace-exclusions.json；不排除自有host脚本。
