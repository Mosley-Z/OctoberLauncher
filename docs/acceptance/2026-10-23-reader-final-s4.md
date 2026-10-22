# 2026-10-23 阅读器最终收口 S4 验收

执行者：s4_performance 子智能体，独占 `SA4B03CN2608081096X`；根负责构建，未操作 P4。本轮设备验收及字段恢复已完成，serial 已释放；未覆盖与目标未达项按下文保留。

首个候选 APK：`reader-1.0.29-release.apk`，2,171,852B，SHA256 `802be27d04db22775fd7b5530a668b59dc3caf64bd5172e634343c59d0a37443`；现场核对摘要，覆盖安装 Success，versionName 1.0.29 / versionCode 49。源码为 84a035e + 本批改动；根 gate-2 完整 check/debug/release 退出 0、reader 双变体各 136 例零失败。此包冷启成功 sample 全为 2，结合 Main.pushScreen / Activity.onResume 路径确认重复排队 open；根追加最小生命周期幂等修复，后续修复结果见下文。下列基线不冒充修复后结果。

第二候选修复包：`reader-1.0.29-resume-release.apk`，2,171,892B，SHA256 `215b686c426bfbcb433d4694abd459cef084b9bf84afb8185573a564491e284d`，仍 1.0.29 / 49；根 gate-3 完整门禁通过。本机自然覆盖 Success，TXT/PDF 冷启各 20 次成功 sample 均为 1，重复 open 已消除；仍须按全链路数据判断预算，不由幂等修复推定性能达标。该批记录独立收在 `baseline-215b/`；根随后优化外部文档冷启时不可见 Home 的首次 resume，后续包结果见下表。

本阶段性能/压力源码 `b4ee37f`；本阶段测试包 `reader-1.0.29-home-defer-release.apk`，2,171,928B，SHA256 `4e98a2438409bade2078783a2eab04b90b8b48bebe639c691b56e35ee6a3bac5`，仍 1.0.29 / 49，现场摘要一致、自然覆盖 Success。根 gate-4 完整 check/debug/release 退出 0、reader 双变体各 136 例零失败。后续发现上游 PPTX 多母版失败，由专属代理诊断，最终修复包专项验收另列，不把本阶段称为未含修复的最终包。

## 基线与状态保留

- Android 11，S4，原生 480×800 / 219dpi，无 wm 覆写；自动旋转 1、用户方向 0、实际显示方向 0，Wi-Fi 与移动数据开关均为 1。初始界面为 HOME，未打开阅读器。
- 初始已安装 1.0.28 / 48 与既有 `readerprobe`；后者不是本轮新增，不卸载既有工具。全刷历史值 6，最终已通过产品控件回读每6页 CHECKED 并恢复。未读取或输出用户正文、PIN 或偏好全量。
- 证据：`_analysis/verify/final-reader/s4/`；UI 原始证据 `_ui-out/final-s4-*`。保留现有 UX001、网站、工具链和其他源码差异。

## 性能测量约定

使用根集成的 opt-in `ReaderPerf`，起点为应用接受 open / 有效 turn，终点为当前内容发布（Web 格式需 ready）后下一次 ReaderPageView.dispatchDraw 完成。此指标是应用内容绘制提交耗时，**不包含进程启动前段、物理触点到应用分发、屏幕光学完成**。不使用 adb am start、CLI layout 的墙钟时间代替。

每一条件至少 20 个成功样本，记录进程内 sample 序号、格式、event 与 elapsedUs；nearest-rank P95 为排序后第 ceil(0.95N) 项。冷首屏每次 force-stop 后打开，暖首屏为关闭后重开；缓存翻页为已访问相邻页来回，边界翻页不计有效样本。条件分别记录刷新 0/6/12、字号与屏幕方向，失败及缺事件单列。TXT/PDF 首屏目标 2s，缓存翻页 200ms，Office 小样本 5s；物理响应另列。

本机 logcat 支持 `threadtime,monotonic,usec`。追加完整系统入口指标：唯一合成 URI 的第一条 ActivityTaskManager START（DocumentEntryActivity）到同次 ReaderPerf open draw，均使用系统单调时间戳。这包含任务/进程启动至内容提交，不采用中间 Main START 或 Displayed/TotalTime 作为正文首屏终点。dmesg 读取拒绝不影响 main/system 日志。原始端点只保留本轮合成 URI，不收其他应用正文。

## 物理 EPD 只读调查

RefreshScheduler 当前统一转发到 InkRefreshController；后者为全屏黑色 View 覆盖（内容单次 70ms）及 invalidate，并未调用已证实的厂商波形 API。刷新计数、覆盖层动画完成与物理面板完成不能混同。

本机清单未显示独立 EPD/Eink framework jar 或 Binder 服务；存在 `sys.eink.*` 属性，但属性存在不能证明普通应用有刷新控制能力。`/dev/graphics/fb0` 为 root:graphics 0660，其 name 读取亦被拒绝。存在 `ebc-misc` platform 驱动，`/dev/ebc-misc` 为 root:root 0600，`/dev/ebc-wetao` 为 system:system 0666。后者节点权限不提供 ioctl 的参数、波形或完成回调契约。本轮没有向设备节点写入、调用未知 ioctl、修改系统或刷机。

证据 `device-api-inventory.txt`、`baseline-technical.txt`、`ebc-nodes.txt`。本轮 API 调查未得到经验证的面板完成接口；后续用户提供的 60fps 原始录像另列动作与分析边界。原片分析已完成，光学结论见下文链接；厂商波形 API 仍未证实。dumpsys 报告的 78Hz 是显示逻辑模式，不能据此宣称墨水物理刷新速度。

## 复杂 Office 样本计划

现有本地样本均为合成测试，不能改称真实复杂文档。代表矩阵如下，最终以有来源与许可的公开样本和合成边界样本分列：

| 格式 | 代表内容 | 预期与检查 |
| --- | --- | --- |
| DOCX | 多页分页、嵌套/合并表格、浮动图片、页眉脚 | 正文与原版式的可读内容、图片表格、分页与受限对象提示；位置/取消/重开 |
| XLSX/XLS/ODS | 多工作表、合并区域、公式缓存/无缓存、中文和日期 | 原生窗口实际显示、跳行列、缓存数值和缺缓存文案、搜索与重开 |
| PPTX | 图片、表格、不同样式 run、布局/母版、媒体或嵌入对象 | 当前页关系闭包、提纲、完整词搜索高亮、受限对象提示及翻页恢复 |

优先仓库或合法本地 SDK，再取固定版本官方上游公开 fixtures 并记 URL/许可/SHA256。未经许可的用户正文不进入日志或 Git。

## 最终执行结果

| 格式/条件 | N | P95 | min–max | 实际 |
| --- | --- | --- | --- | --- |
| 802b 基线 TXT 158134B，内部 open→draw | 20 | 543.256ms | 411.894–543.701ms | 分段指标，不是完整首屏 |
| 802b 基线 PDF 3页/2956B，内部 open→draw | 20 | 1037.229ms | 779.486–1068.828ms | 分段指标，不是完整首屏 |
| 802b 基线 TXT，系统 Entry START→draw | 20 | 2393.180ms | 1762.116–2404.746ms | 超 2s，根据此修复重复 open |
| 215b TXT，系统 Entry START→draw | 20 | 2288.832ms | 1640.919–2571.262ms | 完整冷首屏仍超 2s，不记达标 |
| 215b TXT，同批内部 open→draw | 20 | 546.476ms | 见逐次样本 / max575.818ms | 内部阶段与启动前段分开 |
| 215b PDF，系统 Entry START→draw | 20 | 2630.445ms | 2054.474–2690.013ms | 完整冷首屏仍超 2s |
| 215b PDF，同批内部 open→draw | 20 | 1051.718ms | 752.633–1055.581ms | 内部阶段 |
| 215b DOCX 正文，系统 Entry START→draw | 8 | 不计算 20 样本 P95 | 4660.719–5750.270ms | 根要求换包，人工停止批次 |
| 4e98 TXT，系统 Entry START→draw | 20 | 1747.823ms | 1453.389–1752.434ms | 完整冷首屏 2s 目标通过 |
| 4e98 TXT，同批内部 open→draw | 20 | 502.648ms | 307.480–506.030ms | 分段指标 |
| 4e98 PDF，系统 Entry START→draw | 20 | 2173.188ms | 1781.035–2183.007ms | 超 2s 约173ms，如实保留未达标 |
| 4e98 PDF，同批内部 open→draw | 20 | 940.025ms | 696.523–993.204ms | 分段指标 |
| 4e98 DOCX正文 1598B，系统 Entry START→draw | 20 | 4480.703ms | 3794.431–4516.176ms | 小样本 5s 目标通过 |
| 4e98 DOCX正文，同批内部 open→draw | 20 | 3299.545ms | 2753.521–3394.009ms | Web 冷初始化计入 |
| 4e98 XLSX 17362B，系统 Entry START→draw | 20 | 4800.479ms | 4357.334–5041.356ms | P95 5s 目标通过，最大单次超5s |
| 4e98 XLSX，同批内部 open→draw | 20 | 3671.761ms | 3169.705–3754.071ms | 原生窗口，Web解析计入 |
| 4e98 PPTX图片/表格 31672B，系统 Entry START→draw | 20 | 6335.057ms | 5600.361–6423.425ms | 完整首屏未达 5s |
| 4e98 PPTX，同批内部 open→draw | 20 | 5042.022ms | 4400.939–5157.570ms | 内部阶段也略超 5s |

旧批记录移至证据根 `baseline-802b/`，含 samples/summary 及逐次 timing/closed-metrics。TXT-system 第 17 次工具快照瞬时只有 1 个节点但稍后已有成功 draw，脚本从“立即判错”改为继续等待正文快照，前 16 个有效样本保留并补齐 17–20，退出 0；这是工具观察竞争，不记产品崩溃。退出后的 SourceCache 实际占用会独立汇总，不把无法 run-as 当作零占用。

215b PDF 第 20 个已有样本完整分段：Entry 454231.192176→Main START 454231.710477（518.301ms）→Displayed 454232.730711（再 1020.234ms）→draw 454233.644880（再 914.169ms），总 2452.704ms；内部 open→draw 931.882ms。证据 `baseline-215b/pdf-resume-last-full-endpoints.txt`。不把 Main Displayed 当正文完成。DOCX 第 9 次未计入，父任务要求停止换包后的人工中断退出 1 不记产品失败；随后经 UI 返回 HOME，确认无 reader_status。

## 60fps 光学录像动作与分析

用户同意固定镜头录像。S4 已部署共享 `physical-pattern.pdf`（8290B，SHA256 `e75528b0b8e3755febaf39d14392e71624043612a347598e74addb583625012f`），两页 8×8 黑白反相 A/B 图案。原全刷每6页 CHECKED 保存为 `final-s4-optical-refresh-before`，切关闭后重新打开单选确认 CHECKED（`final-s4-optical-zero-check`），关闭菜单，当前第1/2页（`final-s4-optical-ready`）。

根收到用户“正在录制”后明确授权，`optical-sequence.ps1` 实际执行 20 次标准 id 前后页（唯一 ReaderPerf turn 和页码双守卫，1→2→1反复），再 20 次按当前布局的顶部时间 TextView 单击（源码实际 MANUAL 全刷），动作后稳定至少 1.2s；共40动作，脚本退出0，`optical-actions.json`、`optical-monotonic.txt` 与 `optical-command-summary.json` 已保存。每次翻页均有效，未把边界空翻计数。

shell request 标记仅帮助动作对应，不能把标记前 CLI 开销算作物理触点响应。执行完成立即停手，静态棋盘第1/2页、全刷仍0、无活跃 UI 脚本。根确认用户 S4 停录后，已通过原产品单选选择每6页并继续自动矩阵；最终交还前再次回读 CHECKED。原片光学分析、PTS/配准/阈值误差与不含触点边界见[双机光学报告](2026-10-23-reader-optical.md)，不以命令提交时间替换物理观测；也不把20次点击命令当厂商波形 API 完成证据。

录像序列的应用内部 PDF turn P95=29.063ms、max64.118ms（N20）。包含首次访问目标页2，因此不把全部20条标成纯缓存翻页；后续只补一条已缓存样本与其余19条组成严格缓存20，不重复整批。

## 复杂样本与大文本阶段结果

- 上游 DOCX table-spans：原版式 1/1 实际显示合并单元格线框、多行单元格内容，重启保持原版式；header-footer 正文为 Content，原版式有3页，Even/Odd 的页眉页脚实际可见。首面上游故意设置超 top margin 的大页眉，DOM 含 First Header/That bigger than top margin/Header end，但截图可见 Header end、Content、First Footer，上部页眉溢出可见区域；不把 DOM 存在等同全部像素可读。原版式按宽缩放，固定字体/复杂域及浮动对象差异仍适用页内提示，不宣称像素等价 Word。
- 上游 PPTX table-style：实际 6×6 表格线框；shape-image：实际可见文字形状与 521×265 图片；原版式、提纲及重启成功。底部明确提示复杂图形/图表/EMF/WMF/嵌入及音视频对象可能未显示。
- 上游 PPTX master-slides：54445B、2 masters / 7 layouts、结构预算内，却显示“文档读取失败，可返回后重新打开”。旧包失败截图 `pptx-master-failure-4e98.png` 与 `master-failure-technical.txt` 保留真实反例；当时未记录提纲成功。根定位扩展元数据中同名 page 节点误入索引并修复 namespace 选择，最终包专项结果见下文；本机未改业务源码。
- 50MiB：52428800B 合成 UTF-8 中文/emoji/约3k长行，首屏0%可读，单次内部 open→draw 2138.346ms（不是20样本P95）。全文唯一 FINAL_LARGE_TEXT_TARGET 精确1条，实际末段99%高亮，阅读中真正 force-stop 后仍恢复99%。主 PSS 首屏48881KiB、末段40696KiB，离散快照不代表峰值；退出 SourceCache files/diskBytes/reservedBytes 均0。见 `large-*`。
- 五格式冷启各20次（总100）退出后均有实际 `sourceClosed files=0 diskBytes=0 reservedBytes=0`，见 `final-cold-close-cache-summary.json`；PDF当前页 bitmap cache 实际 884208B/1entry。不把 release run-as 拒绝当零占用；这也不代表其他应用缓存目录总大小为零。

## DOCX 共享 Web 宿主代表压力

4e98 / b4ee37f 的 DOCX 正文37页合成样本，1↔2页共200次有效翻页，退出0；200个独立成功 sample，无 boundary 计数。前54次逐页 layout，第55次脚本切换时已有成功 turn 且实读2/37，经日志/页码同步补录；之后每步标准 id click + 唯一新增 `event=turn format=DOCX` ACK，每20次实际页码与主/绑定renderer PSS双守卫。优化的是观察脚本，没有重跑或重开该压力会话；人工停脚本的退出1不记产品失败。

200次应用内部 turn P95=55.902ms，max89.998ms；包括共享刷新6条件下的实际有效翻页，不等于物理面板完成。11个PSS点（基线及每20次）主+绑定renderer合计121927–128350KiB，末点主88062 / renderer40288 / 合计128350KiB。是离散采样范围，不能据单个样本宣称长期无泄漏。

证据 `docx-pressure-flips.json`、`docx-pressure-flips-summary.json`、`docx-pressure-flips-pss-summary.json`、逐点PSS/bindings、`docx-pressure-flips-technical.txt`。

同包 DOCX 30次闭合重开完成退出0，每次实际 HOME（无 reader_status）并恢复正文1/37；每次均等到新增 `sourceClosed files=0 diskBytes=0 reservedBytes=0`。每5次关闭确认无绑定renderer，打开按实际绑定PID采主+renderer：打开合计121610–122437KiB，关闭主102438–119719KiB；窗口内未见 ReaderWeb.rendererGone 或 FATAL EXCEPTION。证据 `docx-pressure-cycles.json`、`docx-pressure-cycles-summary.json`、`docx-pressure-cycles-pss-summary.json` 与逐次闭合指标。关闭PSS波动不等于长期泄漏结论。

## 预热翻页

1.0.30 / 0d242dd 覆盖后，TXT两相邻页预热再往返20次，独立 non-boundary turn ACK 全过：P95=129.200ms，min91.086 / max184.735ms，应用提交200ms目标通过。TextEngine每次仍读取64KiB并重建StaticLayout，因此正确口径为“预热相邻页复访（含重读/排版）”，不声称存在已排版页缓存。见 `txt-cache-turn-*`。

PDF 严格相邻页缓存20条由4e98录像中后19条与1.0.30补充1条组成（相同PDF引擎、同一图案文件、刷新0），不是单包新跑20次。补充前实际预热2→1，再翻至2，新增turn=12677us，实际LRU=1768416B/2entries。合计P95=28.828ms，min11.694 / max29.063ms，200ms目标通过。见 `pdf-cached-20-samples.json`、`pdf-cached-20-summary.json`、`pdf-cache-supplement-turn-1-timing.txt`。

## 生命周期与最终包专项

1.0.30 的 DOCX 正文2/37进入系统设置后台后，返回实际恢复2/37。第一次Back落在已有设置子页，第二次Back才返回阅读器；首次脚本等待超时为导航预期问题，未将其记作阅读位置丢失。见 `life-foreground-restored.json`。

S4尝试用户方向1后，Main的behind方向仍为480×800；临时wm size800×480后，实际应用布局仍480×800，未观察到横屏/宽布局。因此不记S4实体旋转或宽视口通过，也未扩大到修改Manifest。finally已恢复wm size reset、原生480×800、自动旋转1/用户方向0。见 `life-rotation-portrait-observed.json` 和对应技术字段。S4锁屏/PIN恢复未测，未更改PIN。

对服务绑定确认的单个Web renderer PID进行一次受控 `am crash`：出现 `ReaderWeb.rendererGone crashed=true`，阅读器显示“文档显示已中断或超时，请返回后重新打开”，主进程存活；返回并重新打开后实际恢复DOCX2/37。注入窗口的renderer FATAL属于本次故障动作，与前述压力窗口无崩溃结论分开。见 `life-renderer-failure.json`、`life-renderer-recovered.json`、`life-renderer-technical.txt`、`life-renderer-before-bindings.txt`。

最终自然覆盖1.0.31 / 51，源码 `1ca51be`，APK 2172584B，SHA256 `29784637149498aa00814b4dcf2af60bc4f09b3a1da5986eb58eeb0427b40005`；设备实际base.apk摘要亦相同（`final-installed-apk-sha256.txt`）。根提供gate7完整check/debug/release通过，reader137、data128各变体零失败；这是根的构建证据，不冒充本代理运行。1.0.30的PPTX修复已包含于此包，新增图标数据保留修复由P4专项验收。

本机1.0.31实际重验三份上游PPTX：master-slides原版式显示深绿母版背景及标题，提纲1/1有完整文字，强停重开保持提纲；shape-image文字形状与图片、table-style的6×6表格均实际可见，原版式/提纲/重开成功。证据 `pptx-master-slides.pptx-opened/outline/restarted.json/.png` 及另两样本同名文件。原版式长行仍有小字/裁切边界，提纲补充完整文字；不宣称与PowerPoint像素等价。该处新成功文件已取代同名旧失败快照，旧反例单独保留为上述failure PNG。

1.0.31最小共享宿主smoke：Markdown打开1/37，实际有效下一页到2/37（首页previous为边界，不计有效翻页）；EPUB为第三章17/17，实际前页16再后页17成功。见 `basic-md-*`、`basic-epub-*`。两格式未各自扩展200/30矩阵，DOCX为本轮共享Web宿主代表。

最终HOME实际显示原主题、组件与图标；从应用图标区按当前标注滑动实际进入负一屏，随后Back回HOME。书架内首次手势被组件消费，未将其误报为负一屏。证据 `_ui-out/final-s4-final-home-ann/`、`_ui-out/final-s4-final-negative-layout/`、`_ui-out/final-s4-final-negative-ann/`；不在报告复述用户书名。

## 最终恢复与交接

产品单选回读“每6页”CHECKED；HOME无reader_status、无绑定Web renderer；原生480×800/219dpi无override，自动旋转1/用户方向0，Wi-Fi1/移动数据1，ReaderPerf属性恢复为空。证据 `final-refresh-checked.json`、`final-home-fields.json`、`final-device-fields.txt`、`final-bindings.txt`、`final-lease-release.json`。原已安装readerprobe保留，本轮未新增probe。

仅清理本轮独立 `/sdcard/Download/october-final-reader/` 中14个明确合成文件及空目录，见 `final-synthetic-cleanup.json`；既有search/slides夹具和用户文件、偏好保留。测试生成的阅读记录未通过清库删除，不声称阅读历史完全还原。无活跃UI脚本或Gradle，S4 serial已通知根释放，之后没有继续设备操作。

最终边界：完整冷首屏PDF P95 2.173s、PPTX P95 6.335s仍超过目标，Word超大页眉溢出、S4实体横屏及锁屏局部未覆盖均明确保留。冷启20/格式、50MiB与DOCX200/30归属4e98，暖翻页/生命周期归属1.0.30，最终PPTX及最小宿主/桌面smoke归属1.0.31；未改动引擎的证据按源码继承，不谎称最终包重跑全矩阵。本轮为小版本开发验收，不涉及正式签名发布。
