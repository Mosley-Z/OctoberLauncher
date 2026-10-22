# 2026-10-23 阅读器Office S4验证（Office版完成）

- 验证者：slides子智能体；S4独占租用由Codex根分派，未操作P4。
- APK：`_analysis/verify/office-next/office-release.apk`，2160730B，SHA-256 `bf8b834c807411b114cf33474584299e44f75342ae265695eb2b1115c349412a`；根报告reader113例0失败及模块check/debug/release成功。本记录仅覆盖该Office版，不能替代随后RD014统一搜索版验收。
- 设备：SA4B03CN2608081096X，Android11/WebView83.0.4103.120，物理480×800/219dpi。
- 保留数据覆盖安装`adb install -r`成功。旧APK存私有临时目录；release不可run-as、私有目录拒绝读取、无su，因此未新取files/shared_prefs备份。不清数据，不回滚全部偏好。
- 仅部署本轮合成文件`/sdcard/Download/october-slides-013/mixed.pptx`与`deck-200.pptx`。图片更换为100×80黑白棋盘，便于辨识。

| 项目 | 操作与实际结果 | 证据 | 结论 |
| --- | --- | --- | --- |
| 中文/PNG/表格 | mixed首屏原版式1/3，中文标题、棋盘图片与中文表格单元格可见 | `_analysis/verify/slides-013/device/mixed-original.png` | 通过（合成样本） |
| 复杂对象提示 | 原版式底部明确提示图形/图表/EMF/WMF及嵌入/音视频可能未显示；提纲标记本页包含不支持对象 | `mixed-original.png`、`mixed-outline.json` | 通过 |
| 提纲切换 | 阅读设置→文字提纲；状态提纲1/3，显示本页文字与表格文本 | `mixed-outline.json` | 通过 |
| 第200页 | 阅读设置→跳页输入200；状态200/200，标题及本页特征文字匹配 | `jump-200.json` | 通过 |
| 跨未加载搜索 | 先跳首屏，搜索`_200`，定位200/200且显示受控黑框mark | `cross-page-search-highlight.png`、`cross-page-search.json` | 通过（旧next搜索） |
| 书签 | 在200页添加，翻到199页后列表仍有200页；进入二级菜单→跳转后回200/200 | `bookmark-list.json`、`bookmark-jump-200.json` | 通过 |
| 强停恢复 | 停进程前已显示199页（书签菜单尚未执行跳转），重启同URI显示199/200 | `restart-199.json` | 通过（对应已显示位置） |
| 连续翻页/开关 | 200→190→200共20次控件翻页逐步页码断言通过；10次关闭再打开每次恢复200/200 | `flip-pages.json`、`cycle-pages.json`、`cycle-*-200.json` | 抽样通过 |
| 内存 | 主进程及绑定renderer分别采样，服务绑定确认归属 | `pss-*.txt`、`bindings-*.txt` | 抽样，不视为长压通过 |

所有界面观测与点击使用`scripts/android-ui.ps1`。最初向_analysis写layout被Java拒绝，改用绝对_ui-out目录后正常；筛选合成证据复制至device。第一次外部URI错误导致“源文件不存在”，改用FileProvider相对根`files_share/Download/...`后打开成功，不计格式失败。

测试未变更显示尺寸、密度、翻页全刷；基线全刷每6页，初始checked证据`refresh-baseline.json`。结束时再次核对全刷仍每6页（refresh-final.json）、尺寸480×800/219dpi无override，执行key HOME成功，S4租用已释放。未改用户已有配置。未验证真实复杂PowerPoint样本/字体、实际EPD波形、全200次翻页或30次开关压力；不把合成样本和抽样当完整RD017完成。


内存技术抽样（KiB）：20次翻页前/第5/10/15/20次/结束主+绑定renderer合计126017/134821/134245/133461/135361/133553；10次开关前/第5/10次/结束合计126973/141544/145030/130315。主进程14973，renderer绑定按每次services快照取实际PID（不可把同机其他WebView计入）；详见flip-pss-summary.json、cycle-pss-summary.json及原始pss/bindings。短序列未观察到主进程FATAL/ANR或用户可见打开失败；末点回落，但这些样本不能证明长期无泄漏。crash-filter.txt捕获8条WebView83 renderer进程“crash detected (code -1)”日志，集中在关闭/重开期间；每次重新绑定的新renderer均打开成功，未见ReaderWeb rendererGone记录。根补抓的renderer-exit-context.txt确认8个PID均紧邻ActivityManager“isolated not needed”回收记录（如16112在00:58:10.578，16169在00:58:21.540），与关闭最后WebView对应，现记录为已观测关闭后系统回收；未见本批OOM/FATAL佐证。仍不据此宣称长压无泄漏。

最终样本SHA-256：mixed.pptx 31672B，48cd681a6c982c5662d2e5b8f5f0babb309ea88d6420b7a2e0f4963be06c26c7；deck-200.pptx 291646B，a5dabd59a7fee315c43d80caf067a42e61b38ae6806328ed2598d995437bd5a6。生成器及合成样本留在_analysis/verify/slides-013供后续统一搜索版复验。测试仅新增这两个合成文件的阅读记录/书签，无清用户记录或批量恢复动作。
