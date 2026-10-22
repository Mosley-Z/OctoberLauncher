# 2026-10-23 统一全文搜索 S4 验收（最终补丁完成）

- 验证者：slides子智能体，S4 `SA4B03CN2608081096X` 独占；未操作P4、未运行Gradle。
- 最终源码：`2da644b`（基于`7d8a680`，仅DocumentEntryActivity的Manifest taskAffinity置空）；根确认146文件摘要仅Manifest变化，reader源码与JS/CSS均同477。
- 最终APK：`_analysis/verify/office-next/entry-affinity-release.apk`，2170000B，现场复核SHA-256 `fbd4bebc2e593fc413ea77b03f1f2f5b2125b748080a35d1fbcab2d8fe2878e9`；根补丁完整门禁退出0（1564tasks），reader132例0失败。核心搜索和200翻页使用历史477包，入口修复、跨格式小回归和完整30开关使用最终fbd4包，边界如下。
- 设备：Android11 / WebView83.0.4103.120，480×800/219dpi；覆盖安装保留用户数据。仅部署合成检索/格式/压力样本，期望见`_analysis/verify/search-014/expected.json`。
- 证据根：`_analysis/verify/search-014/s4/`；界面操作全部经`scripts/android-ui.ps1`，原始CLI输出保存在`_ui-out/search-s4-*`。

## APK边界

旧Office APK bf8b的S4验证在独立reader-office-s4记录，不能算统一搜索通过。第一搜索APK8809仅完成PPTX精确200条、64→65与65→64窗口、唯一词_200定位末页、同页3个MATCH原版式/提纲上下个；曾按顺序走到96后按根最新指令停止，不计最终包200翻页压力。d633/fcf3没有安装到S4。

普通跨run缺陷：旧8809原版式能连续显示“检索目标”，搜索完整词却不展示结果；对应同a:p内两个a:r（第二run加粗）。修复将段内a:t拼接、段落换行；最终477同样本`inline-run.pptx`精确1条，完整词在两个样式run上正确高亮。旧对照`old-inline-no-result.json`与最终`final-inline-result-1.json`、`final-inline-last-highlight.png`。

## 最终477核心矩阵

| 项目 | 实际 | 证据 | 结论 |
| --- | --- | --- | --- |
| PPTX跨run | 检索目标精确1/1，正常/加粗两run均受控描边 | final-inline-* | 通过 |
| TXT编码窗口 | emoji完整查询精确3条，第二条跨64KiB字节边界；上下个对应；最后完整字级高亮 | final-txt-result-*、final-txt-last-highlight.png | 通过 |
| 字号/书签/重启 | 新合成TXT末段99%加书签，默认20→增加22保持99%，书签可跳；减回20，强停重启仍99% | final-txt-font-*、final-txt-restart.json | 通过；字体仅该合成文档恢复 |
| Markdown | 含跨inline完整词及末段精确3条，上下个及末页高亮 | final-md-* | 通过 |
| EPUB | 精确3条，next/previous；末条实际第3/3章17/17，正确高亮 | final-epub-* | 通过 |
| DOCX双模式 | 正文和原版式分别精确3条，next/previous；末段及原版式3/3高亮 | final-docx-body-*、final-docx-preview-* | 通过 |
| 取消/旧结果 | 32MiB统计忙碌时取消，0%和书签保留；第二次忙碌切MD，随后MD查询3条正常，没有旧47万结果覆盖 | final-cancel-*、final-switch-* | 通过 |
| 最终PPTX抽样 | 精确200条、64→65→64；同页3处MATCH原版式ordinal与上下个正确，跨run完整词1条 | final-window-*、final-repeat-original-*、final-inline-* | 通过 |
| PDF/ZIP/外部入口 | PDF菜单顶部和滚动底部无全文搜索；ZIP内TXT实际读出；三种Office MIME VIEW(content scheme)/SEND均解析到DocumentEntryActivity；实际隐式VIEW和真实Parcelable URI+ClipData SEND成功 | final-pdf-*、final-zip-txt-visible.png、resolvers-with-content.json、external-intents.txt、final-send-docx-real.json | 通过 |
| 压力 | 477包200有效翻页逐次断言全过；fbd4包完整30次真实隐式开关、闭合HOME、恢复199全过 | pressure-flip-steps.json、pressure-cycle-steps.json、pressure-closed-guard-* | 完成；分包边界见最终段 |

完整门禁由根执行，本记录的设备验证不替代许可/来源检查及真实复杂Office兼容矩阵。最终包不升版，未推送或发布。最终已核对全刷6、原生480×800/219dpi、HOME和无绑定renderer；租用释放见最终段。


外部入口核对边界：首次VIEW resolver查询未提供data URI而返回No activities found，只证明scheme缺失不匹配；补content URI后的三种真实MIME均唯一指向DocumentEntryActivity。实际`am start ... -p com.shuiyu.octoberrain`不指定组件，ActivityTaskManager明确经DocumentEntryActivity转主入口并打开成功。实际SEND由已安装readerprobe的sendFixture构造`EXTRA_STREAM` Parcelable Uri及ClipData，并授读权限；系统START日志显示clip={text/uri-list ...}，产品显示docxzip正文1/1。探针合成DOCX内部“应识别不支持”是旧样本文字，不是本次产品提示。未把ADB `--eu`字符串冒充真实SEND。

## 477翻页压力与重复入口中断

同包200次有效翻页完成（1→200为199次，第200次回199），脚本exit0；每一步同时断言状态页码及本页中文标题，避免边界空翻计数。采样主/绑定renderer合计KiB依次为：基线130234、20次149856、40次171734、60次143590、80次150218、100次156163、120次152866、140次156062、160次152402、180次146842、200次145873。峰值为采样点峰，不代表瞬时全程峰；后段回落，不能据此宣称长期无泄漏。

以下是477历史中断过程，已由最终fbd4完整30次结果取代。测试脚本首次误用PowerShell只读HOME同名变量而退出，已仅改脚本变量；未计有效循环。随后重复隐式VIEW（同一deck200 content URI、读取flag1、限定包）和明确指定DocumentEntryActivity的最小对照均被系统复用既有task，结果留在HOME，没有DocumentEntry.forward/MainIncoming日志。Task的baseIntent组件仍为DocumentEntryActivity，但Activities仅MainActivity；相关证据repeated-implicit-entry.txt、repeated-explicit-entry-*.txt、repeated-entry-task.txt。显式Main可打开，但不拿该结果替代重复外部入口通过；根已接手共享入口处理，后续开关计数另记。

一次隐式VIEW加MULTIPLE_TASK对照（shell flag0x08000001，系统0x18000001）成功新建Entry，DocumentEntry.forward和MainIncoming.valid/opened=true后实际显示199/200，证据repeated-multitask-start/log.txt与repeated-multitask-loaded.json。由此确认重复外部入口的task复用路径问题，不能用显式Main成功替代修复。477翻页renderer PID22517在关闭后02:19:38出现code-1，同时ActivityManager明确isolated not needed；随后临时显式宿主开关的4个renderer也配对该回收理由，无本段ReaderWeb.rendererGone/FATAL/ANR，证据pressure-477-renderer-technical.txt。

## 最终fbd4补丁验收与交还

最终源码2da644b只把外部入口taskAffinity置空，使完成转发后不以旧Entry的baseIntent复用Main所在任务。覆盖安装保留数据，未主动清除旧task。相同URI、普通READ flag1、限定包、不指定组件的隐式VIEW连续两次均实际恢复199/200，中间BACK实际HOME；不再需要MULTIPLE_TASK。证据affinity-first/second-start.txt及loaded.json。旧477的入口失败已修复，本节取代前述“暂缓/待修复”状态。

在同一fbd4包上完成30次有效闭合重开：每次BACK后守卫十雨桌面窗口且无reader_status/reader_title，再经真实隐式VIEW恢复199/200及本页标题；30条计数与30份闭合守卫全部通过，脚本exit0。每5次关闭均无绑定renderer，打开按services取得当次实际renderer PID。PSS单位KiB如下；这是离散采样峰和趋势，不等于全程瞬时峰或长期无泄漏证明。

| 次数 | 打开主 | 打开renderer | 打开合计 | 关闭主（无绑定renderer） |
| --- | --- | --- | --- | --- |
| 5 | 90967 | 44793 | 135760 | 128136 |
| 10 | 91165 | 46649 | 137814 | 129566 |
| 15 | 92284 | 44849 | 137133 | 151210 |
| 20 | 88875 | 47156 | 136031 | 118673 |
| 25 | 87778 | 44906 | 132684 | 131972 |
| 30 | 89110 | 44898 | 134008 | 104552 |

见pressure-fbd4-pss-summary.json及原始pss/bindings。02:27后技术窗口中的31个renderer code-1事件均逐PID配对ActivityManager“isolated not needed”（初次对照关闭加30次），未见ReaderWeb.rendererGone、产品FATAL/ANR，记录为关闭后系统回收；见pressure-fbd4-only-technical.txt及renderer-pairs.json。

补丁小回归：TXT隐式打开保留99%/UTF-8；EPUB隐式打开第3/3章17/17并显示章末合成正文；既有probe发送真实Parcelable Uri和ClipData的DOCX SEND，系统日志clip=text/uri-list，经DocumentEntry.forward与MainIncoming.opened=true后实际正文1/1。证据affinity-txt/epub/send-loaded.json及affinity-smoke-entry-log.txt。不重复未变引擎完整矩阵，不将旧包200翻页写成fbd4重新跑过。

最后核对翻页全刷仍CHECKED每6页（affinity-final-refresh.json），wm显示Physical480×800、density219，无override（final-wm.txt）；关闭合成页面并执行HOME，最终homeWindow=true、readerStatus/Title=false（final-home-guard.json），无绑定renderer（final-bindings.txt）。完成smoke和关闭后的主PSS174340KiB单独记录在final-closed-main-pss.txt，不混入前述30循环末点。仅增加合成文档阅读记录与书签，未清用户数据/全量覆盖偏好；该合成TXT字号已恢复20。最终fbd4保持安装，S4 serial SA4B03CN2608081096X已释放给根；未操作P4，未推送/升版/发布。

结论：本卡范围内统一搜索核心、477的200有效翻页、fbd4的入口修复与30有效开关通过。真实复杂Office兼容、实际EPD波形与RD017其余发布门禁仍由总集成记录界定，不以本次合成样本替代。
