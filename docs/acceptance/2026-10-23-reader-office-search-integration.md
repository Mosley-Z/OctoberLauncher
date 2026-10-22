# 2026-10-23 Office与统一搜索集成交付

- 集成者：Codex；sheets/PPTX专属实现与S4/P4验证按任务分工。
- 源码：`master/2da644b`（基于已提交`0a1534c`与`7d8a680`）；2da644b仅修外部入口任务affinity，阅读引擎/资源未变，后续仅验收文档。保留网站、未验证Wrapper和工具链偏好等无关修改。
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
| 文档检查 | `python scripts/check-project-docs.py`退出0，128篇/40迁移/27卡，2条历史删除源码WARN、0错误；最终收尾核对通过 |

引擎压力包477：`_analysis/verify/office-next/office-search-final-release.apk`，2169968字节，SHA-256 `477ab628f67c8670428ae6d6e3b471f57236a2bfaafd2fe9c82df246944d50be`。前一搜索版`8809a550…`、Manifest版`d633e028…`、CSV兼容版`fcf3bc96…`分别保留；最终版包含旧Text字节锚点/编码向CSV逻辑记录迁移，并修正同段加粗拆run导致PPTX漏搜，设备报告逐项区分。源码校验清单在`office-next/source-manifest.json`，146份源码/资源已逐一复核与7d8a680及最终APK一致；之后只有验收文档修改。

## 设备证据与待验

- Office首包双机证据：[S4](2026-10-23-reader-office-s4.md)、[P4](2026-10-23-reader-office-p4.md)。该包`bf8b834c…`不替代统一搜索版。
- 最终版核心见[S4](2026-10-23-reader-search-s4.md)、[P4](2026-10-23-reader-search-p4.md)。S4覆盖TXT/Markdown/EPUB/DOCX双模式与PPTX的全文计数、上下个、高亮、跨run及64/65窗口，忙碌取消/换书无旧结果，字号/书签/强停恢复，PDF无搜索、ZIP内TXT及真实系统入口；P4最终smoke覆盖CSV旧迁移保持、工作簿2条及高亮/隐式VIEW。P4其他格式及真实SEND按报告保留实际中间包，不冒充最终包重复通过。
- P4最终477包工作簿6000×10完成200有效翻页，行1→4001；30次重开均恢复4001行，关闭HOME，无观察到的OOM/ANR/崩溃。11次主+绑定renderer阶段PSS116,792–123,766KiB，开关10/20/30打开150,806/151,379/152,367KiB，关闭115,022/109,854/113,879KiB且无绑定renderer；是采样范围而非全时峰值或无泄漏保证。全刷6/原生552×768/HOME已恢复，新增probe卸载，serial释放。
- S4最终477包PPTX已完成200有效翻页，逐次核对页号与标题（第1→200，再回199，避免边界空翻计数）。200次主/renderer PSS88,609/57,264KiB，合计145,873KiB；40次171,734KiB后回落，为阶段采样而非全时峰值。补丁fbd4的30次真实隐式开关已通过，分包回收与恢复结果见下节。
- 真实SAF持久授权重选、通用第三方URI身份关联、复杂真实Office集合、全桌面网络/锁屏矩阵及面板物理EPD耗时仍有边界，不能由JVM/截图或小合成样本推断完成。
- S4短测renderer code-1已由同PID系统`isolated not needed`日志佐证为关闭后回收；仍不据短测声明长压无泄漏。


### 升级与普通样式检索整改证据

P4在真实旧基线安装状态中准备两份独立CSV：UTF8 BOM与GB18030，逻辑142行前含quoted newline。旧版保存77%/76%与旧Text书签；覆盖升级到fcf3后，两份直接恢复142行1列，旧书签二级跳转一致，GB无需重选编码。最终477版再抽恢复，不用数据库注入。PPTX旧8809对加粗拆两run的完整词漏搜有设备反例，477版以同段拼接、段落换行修正，独立JVM用例与设备复验分开记录。


固定第三方资源原样保留其上游空白（如PPTXjs及divs2slides行尾空格），不为Git空白门禁改动冻结字节；10项JS/CSS实际SHA256与固定清单逐项一致。自有代码/宿主/文档通过diff --check，精确vendor及完整许可副本排除名单与摘要核对留在office-next/whitespace-exclusions.json；不排除自有host脚本。

## 重复外部入口修复与补丁验证

S4在30开关前置发现：首次NEW_TASK隐式VIEW经Entry转Main成功，但task的baseIntent仍为Entry、活动仅Main；同URI再次打开只前置HOME，Entry/Main均无回调。显式Entry同样复现，增加MULTIPLE_TASK则触发正常转交。不是渲染失败，也没有把这些尝试计为有效开关。

2da644b仅给DocumentEntryActivity设置空taskAffinity，使临时入口任务与长期Main宿主分开，转交后入口finish。无需迁移正文、Locator或SQLite。两机保留旧任务自然覆盖安装后，同URI/真实MIME/flags1隐式打开连续两次均成功，随后各完成30次完整HOME闭合/重开位置恢复。不会以显式Main旁路掩盖原问题。

补丁最终APK：`_analysis/verify/office-next/entry-affinity-release.apk`，2,170,000字节，SHA256 `fbd4bebc2e593fc413ea77b03f1f2f5b2125b748080a35d1fbcab2d8fe2878e9`。新源码清单`entry-affinity-source-manifest.json`逐一复核146份，只有Manifest与477不同，reader JS/CSS全逐字节相同，APK CRC通过。首轮完整门禁`entry-affinity-gate.log`SUCCESS1m9s/1564任务但外层pwsh等待，构建已结束后只终止该外壳；直接调用同脚本再确认`entry-affinity-confirm-gate.log`退出0、5s/1564任务，reader debug/release仍各132例0失败。未扩大静态基线。

477双机200翻页与P4原30开关均保留原包归属；fbd4的双机30次真实隐式路由开关及必要smoke另记，不宣称在fbd4重新执行引擎200次。

| 补丁fbd4阶段 | 打开主/renderer PSS KiB | 关闭主PSS KiB | 路由/回收结果 |
| --- | --- | --- | --- |
| S4第10次 | 91,165 / 46,649 | 129,566 | HOME闭合后隐式重开恢复199 |
| S4第20次 | 88,875 / 47,156 | 118,673 | 同上 |
| S4第30次 | 89,110 / 44,898 | 104,552 | 30/30通过，5/10/15/20/25/30关闭均无绑定renderer |
| P4第10次 | 82,179 / 66,734 | 92,758 | HOME闭合后隐式重开恢复4001行 |
| P4第20次 | 81,578 / 66,745 | 90,449 | 同上 |
| P4第30次 | 78,452 / 66,790 | 87,741 | 30/30通过，关闭采样无绑定renderer |

两组PSS是实际绑定进程阶段采样，关闭主进程仍承担Launcher，不要求降到零；不据此声称长期无泄漏。最终字段恢复与serial释放见设备报告。


S4补丁抽验TXT99%、EPUB第3章17/17及真实Parcelable Uri+ClipData DOCX SEND1/1通过；全刷6/原生480×800/219dpi/HOME与无绑定renderer已核对并释放。31个关闭期renderer code-1均逐PID配对系统isolated not needed，未见该段产品FATAL/ANR。跨格式smoke关闭后主PSS174,340KiB另记，不混入30循环末点104,552KiB。

P4补丁抽验UTF8 BOM/GB18030旧CSV仍逻辑142行，GB中文无需重选；真实Parcelable Uri+ClipData DOCX SEND正文1/37通过。最终安装base.apk摘要与fbd4一致，全刷6/原生552×768/HOME恢复，新增probe卸载且pm path为空、临时send文件删除，serial已释放。两机原始证据及细分版本/恢复以各自报告为准；本轮交付完成，RD016/017未覆盖矩阵保持IN_PROGRESS。
