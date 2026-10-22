# 2026-10-23 P4 阅读器最终收口

状态：本分派完成，最终1.0.31/51图标修复复验通过，设备已恢复并释放。各阶段APK证据分开，不把历史包长压冒充最终补丁包执行。

- 执行者：p4_remaining；独占 P4 `P4L202610130006`，不操作 S4 或 Gradle。合成来源/界面证据存 `_analysis/verify/final-reader/p4/` 与 `_ui-out/final-p4-*`。
- 初始只读基线：在线，Android 11，物理 552×768、210dpi，无尺寸 override；accelerometer_rotation=1、user_rotation=0、wifi_on=1、airplane_mode_on=0；HOME 6 节点。`com.shuiyu.readerfinalprobe` 与旧 readerprobe 原本均不存在。
- OPEN_DOCUMENT / GET_CONTENT 的 text/plain resolver 仅自有 PickEntryActivity。`pm list packages -u documentsui` 与 disabled 列表为空，系统 DocumentsUI 不存在，不能写成禁用可恢复。工具链 aosp 目录为空且未找到本地 DocumentsUI APK；未联网下载或替换系统组件。
- 按集成者分派编写独立合成 DocumentsProvider 与 ACTION_OPEN_DOCUMENT picker。MANAGE_DOCUMENTS 保护实际读取，URI 授权依 Android 系统执行；通过真实 persistable grant/revoke API 验证，不使用 Provider 自定义 deny 模拟撤销。合成 picker 不等同厂商 DocumentsUI 界面。
- 探针手工 javac/aapt2/d8 构建成功；只包含合成私有文本与三个外部白名单样本路径，无真实用户正文、PIN、全量偏好或全量 grants。

## 执行范围

1. VIEW 带 READ/PERSISTABLE → 产品 takePersistable → 探针仅自身 SAF URI outgoing persisted 布尔值；产品强停后由最近阅读重开，避免重新授予掩盖持久性。
2. Provider 真实 revokeUriPermission → 产品最近阅读出现授权失败 → 既有“重新选择文档” → 系统 chooser 选择合成 picker → 返回 READ/PERSISTABLE → 恢复并确认新持久授权。
3. 同一外部文件经第三方 PFD URI 与本地入口读到相同进度/书签；相同内容副本与移动新路径独立。
4. 锁屏/前后台/阅读尺寸变化/权限异常及必要桌面离线刷新；切网络前先通知根，结束逐字段恢复。
5. 补其余格式代表压力缺口，避免重复历史有效 Workbook 200/30。旧报告不可冒充新 APK 同包结果。

最终恢复与未覆盖边界见末节。

## 802b 候选包真实授权与身份结果

- APK `_analysis/verify/final-reader/reader-1.0.29-release.apk`，1.0.29/49，2,171,852B，SHA256 `802be27d04db22775fd7b5530a668b59dc3caf64bd5172e634343c59d0a37443`；覆盖安装保留数据。ReaderPerf 原属性为空，本批临时 DEBUG，收尾恢复空。
- `Grant persistent SAF` 实际 VIEW READ/PERSISTABLE 进入产品，合成文本由0%翻到1%，保存1%书签。探针只筛自身 `saf` URI 的 outgoingPersisted 得到 true。产品 force-stop 后由 HOME 右滑负一屏、原微信读书切本地、真实最近阅读 saf 重开，恢复1%，中间没有新的 VIEW/URI grant。
- Provider 调用真实 `revokeUriPermission` 后 outgoingPersisted=false；从最近阅读再开明确“无法读取文档，请重新授权”。阅读设置→重新选择文档→Android 系统 chooser→Synthetic SAF Picker→仅此一次→Select synthetic SAF document，返回 READ/PERSISTABLE 后恢复1%，原1%书签仍可见，outgoingPersisted=true。证据 `_ui-out/final-p4-saf-*`；系统授权机制通过，厂商 DocumentsUI 界面未覆盖且此机不存在。
- 第三方 Provider `identity` regular PFD 指向本轮独立外部文件。0%→3%保存书签；随后自有 files_share 本地 canonical 入口打开同路径恢复3%，书签列表有3%。同字节副本 copy.txt 从第三方打开0%，查看书签未打开列表；真实 mv identity→moved 后第三方新路径打开0%；移回原 identity 路径仍3%。证据 `_ui-out/final-p4-identity-*`、`copy-*`、`moved-*`，合成清单见 `fixture-manifest.json`。此处同源对照入口是自有 FileProvider 的本地 canonical 来源，不冒称文件页点击。
- 根新增 android-ui swipe 包装已实机验证：当前稳定截图 label14→16 两端 resolve 后右滑，进入负一屏。冷启加载中截图标签变化，首次引用不存在label解析报错未执行手势，重新 annotate 后通过；没有将工具失败当产品失败。
- 授权/身份过程中临时本地书源，初始原值微信读书、2行；网络/尺寸/方向未改。后续矩阵及结束恢复另记。

## 215b 生命周期包增量

- APK `reader-1.0.29-resume-release.apk`，2,171,892B，SHA256 `215b686c426bfbcb433d4694abd459cef084b9bf84afb8185573a564491e284d`。仅重复 onResume 幂等修复；覆盖安装保留数据，前述完整授权/身份链保留802b包归属。
- 探针已存在持久授权仍 true；升级后 HOME→负一屏本地→最近 saf，无新 VIEW，仍1%可读。HOME关闭阅读后显式无URI Main回6节点HOME，没有旧阅读回填。`final-p4-resume-*`。
- RD004独立 `versioned.txt`：79,200B，搜索 row0600 到49%，保存49%书签，字号增加一次；退出后同路径改79,222B并mtime变化，再开0%，可见字号保留增大；查看书签仍49%，二级“跳转”实际恢复49%。最后字号减小一次还原本轮增量。`final-p4-version-*`截图/布局；只修改本轮合成文件。
- 固定上游复杂回归样本来源/提交/摘要见 `_analysis/verify/final-reader/upstream/manifest.json`。XLSX、XLS共享公式首表19×2，首部10/20/30/40/50缓存值可见，实际定位19行2列均180；XLSX工作表列表有Sheet1/2/3。`final-p4-upstream-xlsx-*`、`xls-*`。
- ODS合并样本首表24×10，A1的6列12行合并大区域原生显示且无内部格线；对唯一HorizontalScrollView/ScrollView实际横纵滚动，合并边界随窗口出现，未崩溃/异常。初次截图为prepare瞬态，不算显示结果，后续 `ods-ready/hscroll-screen/vscroll-screen` 才是已就绪证据。不据三个上游样本宣称完整Office兼容。
- 为物理观测准备两页A/B反相8×8棋盘 `physical-pattern.pdf`，8,290B，SHA256 `e75528b0b8e3755febaf39d14392e71624043612a347598e74addb583625012f`；当前未录像，不把PDF或屏幕截图当物理EPD结果。

## 4e98 冻结包状态与异常回归

- APK `reader-1.0.29-home-defer-release.apk`，2,171,928B，SHA256 `4e98a2438409bade2078783a2eab04b90b8b48bebe639c691b56e35ee6a3bac5`，1.0.29/49；源码冻结提交 b4ee37f。只补外部冷启动隐藏HOME延迟首次resume；已过旧包测试保留实际版本归属。
- 忙碌实际横屏：32MiB合成 busy.txt 查询“统计取消”，竖屏明确“正在分块统计全文结果…”，实际user_rotation1后横屏仍为该忙碌弹层。随后BACK退出到HOME，等待后无旧回填，重新打开0%。不把该取消/退出当完整结果计数完成。恢复方向时先auto0/user0，等待实际display rotation0再auto1；立即连写auto1可能保留旧rotation，最终已核实原552×768/display0/auto1/user0。`final-p4-busy-*`、`rotation0-confirm`。
- 真实前后台：versioned49%→独立probe前台→BACK返回仍49%。屏灭短按power得到Wakefulness=Asleep，再power唤醒阅读仍49%，系统keyguard showing=false。未启用/猜测PIN，未测试凭据锁。CLI不支持sleep名称，首次被CLI拒绝未执行，改用支持的power。`final-p4-background-*`、`screen-*`。
- 真实断网：svc/cmd wifi禁用命令返回0但wifi仍1，未记成功。系统WLAN可见开关关闭后wifi_on0、Active default network none，微信读书手动刷新明确“部分刷新：book刷新未完成…monthly刷新未完成，保留已有数据”；切本地出现独立最近阅读，versioned仍49%可读。恢复原微信读书/2行，系统开关恢复wifi1/airplane0，默认网络技术ID101重新建立（不记录SSID/IP/凭证）。`final-p4-offline-*`、`wifi-*`。本例只覆盖可观察失败及恢复，不宣称长时间无重复取数。
- 私有Provider DOCX缓存：5,023B上游合并表格样本经私有PFD无法映射外部可读路径，实际显示正文1/1；关闭ReaderPerf sourceClosed的files/diskBytes/reservedBytes均0。另一URI仅metadata声明129MiB、实际仍短DOCX，产品明确超预算拒绝，随后三项仍0。`final-p4-private-docx-*`、`oversize-*`及 `4e98-technical-log.txt`。这是声明预算拒绝，不冒称真实写满磁盘压力。

## 用户外部录像序列（4e98）

- 用户明确开始P4录制后才启动自动UI脚本；此前棋盘页1静止、全刷关闭已重新打开设置确认为CHECKED，原值每6页也已确认。20次A/B普通翻页，按下一页/上一页交替，ReaderPerf得到20个唯一PDF turn（sample12–31），结束1/2页；随后顶部时间单击20次手动全刷，仍1/2页。
- `physical-action-events.jsonl` 恰好partial20+manualFull20条，均有序号及调用开始/返回UTC；`physical-readerperf.txt` 是软件绘制提交端点，不是物理EPD时长。录像配准/真实PTS/ROI稳定观测已由集成者完成，见[光学验收](2026-10-23-reader-optical.md)；不含触点等待，不声称厂商波形API能力。
- 40动作完成立即停止UI并保持棋盘静止，根得到用户明确停止P4录制后才恢复每6页并继续其他矩阵。无用户未录时提前执行，无手动波形API能力推断。

## TXT/CSV压力完成（4e98）

- 冻结4e98包；两个独立6,000行合成样本。TXT 432,000B、CSV 182,679B/3列，均存本轮Download目录，不覆盖用户文件。
- 每次点击必须得到新的唯一ReaderPerf `event=turn`，不把边界/忽略点击算成功；每20次取实际位置与主进程PSS。各格式随后30次同URI自然隐式VIEW，逐次验证原位置、BACK关闭到无reader_status的HOME。
- 自动脚本、逐条结果和原始内存见 `pressure-template.ps1`、`pressure-txt/summary.json`、`pressure-csv/summary.json`。
- TXT已完成200/200唯一有效翻页，0%→20%，每20次精确递增2%；同URI自然隐式开关30/30均恢复20%，BACK后6节点HOME无reader_status。全200样本不剔除，软件action→draw P50=175.172ms、nearest-rank P95=199.305ms、max225.175ms，刷新每6页。中途82例估计P95约202.8ms，最终完整样本自然回落，未重测挑样。
- TXT 11个翻页主PSS采样102,819–106,430KiB，末105,170；第10/20/30开关主PSS打开109,262/101,086/107,473，关闭HOME148,214/145,514/127,894KiB。HOME恢复组件后可能高于阅读页，不凭单次快照推断泄漏；这是此前Office/桌面共享进程长会话暖基线，不是新冷基线或总峰值。`pressure-txt/summary.json`保留分项。
- CSV脚本预检两次修正：实际ReaderFormat为SHEET，状态是增量已读行而非总6000行；第一次实际前进21行后脚本过滤错误停，上一页回行1再开始正式计数。正式20次均唯一SHEET turn且到401行/已读420行，随后中文断言被PS5默认ANSI解码无BOM CLI JSON阻止；改显式UTF8后保留这20条，从第21次继续，没有重计或伪造连续动作。此为测试脚本问题，未改产品源码。
- CSV最终200/200，逻辑行1→4001、已读4020，200序号连续/唯一sample；30/30自然隐式开关均恢复4001行/已读4020并关闭6节点HOME。全200软件action→draw P50=39.120ms、nearest-rank P95=54.726ms、max69.651ms，刷新6。原fixture共6000行，未把增量已读4020写成已扫描全部。
- CSV翻页10个主PSS采样101,213–105,216KiB，末104,433；20页PSS因脚本断言中断未采，不伪补。第10/20/30开关打开109,305/118,437/105,692，关闭HOME176,817/108,301/169,012KiB；波动保留，未据三点推断长期无泄漏。全部样本属于长会话暖进程，软件draw不是面板响应；未发现本序列OOM/ANR/主进程退出。

## 最终1.0.30补丁与FIX专项

- 最终APK `_analysis/verify/final-reader/reader-1.0.30-pptx-release.apk`，2,172,076B，SHA256 `abe64167308c8d9f354a5ce554d84625eda7bb67b61c364af5121873b6b1feca`，1.0.30/50，源码0d242dd；门禁/137例每变体由根统筹。覆盖安装保留数据，不重跑未变的TXT/CSV历史包长压。
- 官方双母版 `pptx-master-slides.pptx` 54,445B，在本机实际原版式1/1显示暗色母版、正文、日期/版权，无先前预算内损坏错误；正文较小/复杂对象警示仍存在，不宣称逐像素兼容。切提纲1/1正文完整，关闭HOME，同URI隐式重开恢复提纲；CSV补丁smoke仍4001行/已读4020且可关闭。`final-p4-pptxpatch-*`、`patch-csv-*`。
- FIX003：原两special均系统默认，原6个普通应用已有图标覆盖。本轮先在产品内部保存独立ALL临时主题（6图标/8排序，不导出或读取全配置），文件/设置真实长按菜单均无应用信息/卸载；取消文件选图保留默认。文件设置合成黑方块A、设置合成圆环B后不串图；强停和1.0.30覆盖后保持，桌面/美化第2页/预览均相同。
- 逐个恢复默认：先文件恢复文件夹而设置仍圆环，再设置恢复齿轮。原6普通图标未改；图片目录原Pictures未改；仅两本轮合成PNG删除。临时主题精确清理后主题库仅原有1个/6图标主题，未删除用户原主题。截图仅合成形状与技术计数，不在文档展开原映射。
- 自动审批曾拒绝一次“通用删除选择器再打开确认”的命令，理由为当前状态不明可能删除用户主题，命令未执行。随后先只读确认本轮主题名及行，再当前标注label精确打开；顶层确认框通过项目`find -FindText 删除`精确读到按钮bounds与可点击，才以`click -FindText`确认；回读新主题消失、原主题保留。没有绕过审批；早先CLI同文跨窗口/标题子串误匹配不计产品失败。
- FIX002：真实本地最近阅读恢复/撤销错误/离线可读与微信读书不混排已在上述链覆盖；本地选择跨覆盖安装保持。最终微信读书2行恢复、无本地最近阅读混入。P4只覆盖现有布局与本轮字段变化，历史全配置迁移组合/空目录专项沿S4和JVM既有证据，不冒称重新跑过。
- FIX005/006：本机真实负一屏book刷新断网失败保留、恢复网络、来源独立已过；桌面原WEEK未变，未单独执行P4桌面BOOK双向刷新。根明确采用P4真实失败恢复+S4既有双向同源证据，不为不可唯一定位的粗粒度Canvas标注添加热区或塞裸坐标。

## 最终恢复、释放与边界

- 1.0.30阶段设备端base.apk摘要精确abe641，`installed-sha256.txt`、`final-version.txt`；最新1.0.31见下节。翻页全刷每6页重新打开确认CHECKED；原物理552×768/210dpi无override、display0、auto1/user0；wifi1/airplane0/活动默认网络已恢复，技术ID101；原widget本周、微信读书2行、原图标与原主题保持。`final-state.json`、`final-p4-final-*`。
- `log.tag.ReaderPerf` 原空已恢复空。本轮新`com.shuiyu.readerfinalprobe`卸载Success，pm path为空；没有清应用数据/用户配置/用户文档，独立合成Download样本及其阅读记录保留供回归。两合成Pictures PNG与内部临时主题已清理。
- 最终HOME6节点、无reader_status，无本分派活跃ADB/脚本，P4资源释放。仅本报告/自有证据目录修改，不操作S4或Gradle，不自行提交/推送/发布。
- 系统DocumentsUI本机不存在，已验真实Android授予/持久/撤销机制+合成picker，不能记厂商DocumentsUI界面通过；屏灭唤醒未启用或测试凭据PIN锁。上游复杂样本及代表长压不等于所有未知用户文档全兼容；物理观测边界以光学报告为准。

## 最小坏图/缺失副本补验与修复闭环

- 根审卡发现FIX003缺少图像缺失/损坏的设备证据，重新分派P4。本轮未触及原6普通应用覆盖和设置special，不写release私有目录；仅默认文件special通过既有UI选择本轮有效PNG方块，再选本轮27B无效PNG。
- 产品明确Toast“图标设置失败，请更换图片”，当前预览因缓存仍是方块；强停重开后文件回默认文件夹。结合源码setOverride直接覆写目标副本、解码失败删除但保留映射，可确认坏图选择实际毁掉既有有效副本，同时证明缺失副本映射会默认回退。证据 `final-p4-badicon-valid-before/failure-prompt/fallback-actual`。不把此记为坏图保留旧图通过。
- 随后通过文件special“恢复默认图标”清除本轮失效映射，仅删除本轮两PNG；图片目录原Pictures保持，原6普通图与设置special未改，HOME6节点。根负责最小修复及新包，本分派等待复验“坏图拒绝且旧有效图仍在”。
- 根交付1.0.31/51最小副本保留修复：`reader-1.0.31-icon-release.apk`，2,172,584B，SHA256 `29784637149498aa00814b4dcf2af60bc4f09b3a1da5986eb58eeb0427b40005`，源码1ca51be。门禁与reader137/data128每变体由根记录，不在此代替设备结果。
- 相同文件special有效方块A→同27B坏PNG，仍明确“图标设置失败，请更换图片”；强停重开到special页后实际仍黑方块A，消除内存缓存后确认旧副本保留，设置齿轮及原6普通图不变。`final-p4-iconfix-valid-before/failure-prompt/valid-kept`，与1.0.30强停后回默认反例形成对照。未写release私有目录、未选择用户图。
- 再通过UI仅清文件special覆盖，恢复文件夹默认，删除仅两本轮PNG；回原首页HOME6节点。最终设备端base.apk摘要精确297846，版本1.0.31/51，见`installed-final-131-sha256.txt`、`final-131-version.txt`；`final-131-state.json`确认ReaderPerf空、auto1/user0、wifi1/airplane0、552×768、probeAbsent=true。全刷6/微信读书2行/原WEEK及目录本次未改。
- 本分派最终设备操作结束，P4再次释放，无残留UI/ADB脚本；后续只有本报告与自有证据汇总。
