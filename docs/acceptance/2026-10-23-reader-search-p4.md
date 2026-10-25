# 2026-10-23 P4 全文搜索与资源验收

本批P4核心搜索、CSV自然升级回归已完成。477a包完成引擎200次有效翻页及显式入口30次开关；最新fbd4包另完成同URI隐式入口30次回归。设备最终已恢复并释放，各APK证据分开记录。

- 验收者：P4测试子智能体，独占`P4L202610130006`；Android11、WebView83、物理552×768无override。无Gradle/源码/S4操作，不改017/STATUS。
- 初始搜索包：`_analysis/verify/office-next/search-release.apk`，2,168,944B，SHA-256 `8809a550ce3451244de0c975b74744af02ea52f8588695e4d356b8685e48a3aa`（下称8809）。
- 补Manifest包：`_analysis/verify/office-next/final-release.apk`，2,169,236B，SHA-256 `d633e0283a55d0373c540dc04f949de40970d35b03a9621f33a6cba12303f33f`（下称d633）。与8809业务源码相同，仅补Office VIEW/SEND MIME过滤；均覆盖安装保留数据。
- 样本：预期（本地证据：`_analysis/verify/search-014/expected.json`）、独立校验（本地证据：`_analysis/verify/search-014/validation.json`）。全部合成数据；部署独立Download/p4-search-20261023和既有p4-office-20261023目录。
- 证据：`_analysis/verify/search-014/p4/ui/p4-search-<下表后缀>/`；原始UI观测在`_ui-out/p4-search-*`，用项目脚本及明确Serial，已复制专属证据目录。CSV自然升级与冻结包压力结论见后文。

| 用例/包 | 操作与精确预期 | 实际/证据后缀 | 结论 |
| --- | --- | --- | --- |
| TXT Unicode/8809 | 查询检索😀目标，3命中，第二命中的emoji UTF8字节65535–65539跨64KiB；上下个 | 第1/3→2/3→3/3→previous2/3，第二snippet正确，关闭后41%与灰底高亮；`txt-result/second/third/previous/highlight` | 通过 |
| Markdown/8809 | 检索目标3命中，包含跨inline检**索**目标和文末 | 1/3→2/3→3/3，previous回跨inline并灰底高亮；`md-result/second/third/inline-highlight` | 通过 |
| EPUB/8809 | 3章各章末1命中，初始第三章未加载 | 1/3→2/3→3/3；第三章17/17与章末灰底高亮；`epub-result/second/third/third-locator/third-highlight` | 通过 |
| DOCX正文/8809 | 首/中/末3命中，末段超首屏 | 1/3→3/3与末段高亮；`docx-result/third/tail-highlight` | 通过 |
| DOCX原版式/8809 | 同一文档3命中，不按当前页计数 | 1/3→3/3，原版式3/3页末段高亮；`docxfixed-result/third/tail-highlight` | 通过 |
| Workbook/8809 | 首表发起目标搜索，第二工作表两匹配格 | 1/2跨表目标→2/2重复目标→previous1/2；原生灰底粗框；`workbook-result/second/highlight` | 通过 |
| 最终Manifest/d633 | 七MIME各VIEW/SEND系统resolver | text/csv、application/csv、DOCX、XLSX、XLS、ODS、PPTX共14查询均DocumentEntryActivity；`final-resolvers.json` | 通过 |
| 隐式VIEW/d633 | -p包，无component，Markdown content URI | 正常进入Markdown1/37，无旧TXT结果串入；`implicit-md` | 通过 |
| 最终抽样/d633 | MD、DOCX、Workbook、CSV全文精确统计 | MD3、DOCX3、Workbook2、CSV晚段目标1；`finalmd-result/finaldocx-result/finalworkbook-result/csv-result` | 通过 |
| Workbook高亮/d633 | 关闭第1/2结果框 | 对应第二工作表匹配单元格灰底粗框；`finalworkbook-highlight` | 通过 |
| GB18030持久化/d633 | 强停再开此前已选GB编码的合成CSV | 中文两行正确，无需再选编码；`gb-restart-persist` | 通过 |
| 书签二级跳转/d633 | XLSX查看书签→3,3条目→跳转，等就绪 | 第一表3/4行3/4列；`mark-actions/mark2-ready` | 通过 |
| 32MiB取消/d633 | 从0%启动472,597命中统计，忙碌时取消 | 独立一次采到“正在分块统计全文结果…”；下一次立即点明确取消成功，回0%且无结果框；`cancel2-progress/cancel3-after`。先前back取消后打开MD正常 | 通过；没有把472,597总统计作为完整完成结果 |
| 真实SEND/d633 | 本轮新装既有合成probe，以Parcelable EXTRA_STREAM+ClipData+读授权，显式产品入口发送DOCX/XLSX/PPTX | DOCX正文1/37且搜索3；XLSX首表1/4；PPTX中文/表格显示，`send-docx/send-xlsx/send-pptx`。系统隐式过滤另由resolver验证 | 通过；PPTX即时状态仍prepare，不据该瞬态节点宣称全部版式质量通过 |

## 旧CSV迁移升级前置

集成者审查发现旧版CSV按Text存储字节锚点，新CsvEngine需要迁移为逻辑记录；按其明确分派自然覆盖安装已提交基线，未注入DB/全偏好。

- `_analysis/verify/search-014/p4/rd014-legacy-utf8.csv`含UTF8 BOM、181逻辑记录、第92记录quoted换行；`rd014-legacy-gb18030.csv`同内容GB18030。目标旧进度目标在UI142行（0based141），查询所在列2。UTF8字节4601、GB字节3463；精确预期/摘要见`legacy-expected.json`。旧Text锚点迁移应至少落在该逻辑记录，不要求由Text恢复其原本未编码的列语义。
- 覆盖安装`baseline-release.apk`（摘要c2d9efcd09348c51663a2068a7b78ac1031130d18bc79681fd9951927a6d55ab）保留数据，仅打开上述两个新CSV，不打开新Office记录。UTF8原生Text搜索后77%并添加书签；GB选择GB18030后搜索76%并添加书签，强停重开仍76%·GB18030。证据`legacy-old-*`。
- 此阶段停HOME等待迁移修复包，最终安装/恢复状态以本记录后续追加为准。200翻页/30开关尚未启动，避免在即将替换的APK上长压。

## CSV迁移修复包 fcf3 自然升级结果

- `_analysis/verify/office-next/upgrade-final-release.apk`，2,169,668B，SHA-256 `fcf3bc961ca71a9c0f43e203b2bb0aeaed6f0801ac8bf2ffeed39d32b562bbc6`，从上述baseline直接`install -r`升级成功，未注入/导出数据库。
- UTF8 BOM样本旧77%直接恢复CSV142行1列/已读161行；旧77%书签条目→二级跳转→等就绪仍142行1列。GB18030旧76%直接恢复142行1列且中文正确，无需重新选择编码；旧76%书签二级跳转同样142行1列。quoted换行位于目标之前，未造成物理行/逻辑行混淆。证据`legacy-upgrade-utf8*`、`legacy-upgrade-gb*`。
- 同包抽验TXT代理对查询3、Markdown3、Workbook2及灰底粗框，证据`upgrade-txt-result/upgrade-md-result/upgrade-workbook-result/upgrade-workbook-highlight`。
- 同包Office实际MIME隐式VIEW（仅-p，不指定component）打开XLSX成功；既有probe真实Parcelable/ClipData SEND DOCX成功，正文1/37。证据`upgrade-implicit-xlsx/upgrade-send-docx`。
- 仅运行`run-as com.shuiyu.octoberrain true`得到package not debuggable，未访问其私有文件；release缓存计数不可读，不把JVM/源码预算常量当设备缓存统计。证据`release-cache-access.txt`。
- 此时集成者发现PPTX跨run完整词索引需修，明确要求等新包再200/30；本包核心结果保留，设备回HOME，压力仍未执行。最终安装及收尾状态以后续段落为准。

## 477a包引擎200次翻页与显式入口30次开关（历史包）

- `_analysis/verify/office-next/office-search-final-release.apk`，2,169,968B，SHA-256 `477ab628f67c8670428ae6d6e3b471f57236a2bfaafd2fe9c82df246944d50be`。集成者冻结源码随后提交`7d8a680`，与该APK源文件摘要一致；后续仅验收文档差异，未推送/升版/发布。
- 本包smoke：两份legacy CSV仍142行1列，GB18030中文保持；Office实际XLSX隐式VIEW正常；Workbook搜索2条、灰底粗框通过，证据`frozen-utf8/frozen-gb/frozen-workbook-result/frozen-workbook-highlight`。
- Workbook6000×10合成样本本包完成200次有效窗口翻页：起始行1→4001，10批各20次、每批逻辑行精确增加400。独占serial期间没有其他翻页动作，校验结果见`turn-results.jsonl`、`pressure-summary.json`和`pressure-turn-200-verified/`、`pressure-200-screen/`。
- 同包通过显式MainActivity VIEW入口完成30次打开/关闭：每次等就绪显示4001/6000行，再返回到无reader_status的6节点HOME；序号1–30连续、无重复、全部closed=true，见`cycle-results.jsonl`与各`pressure-cycle-*-ready/closed`。观察过程无OOM/ANR/崩溃，不把准备阶段的点击忽略计为翻页或开关成功。最新隐式Entry路由验证另见fbd4段。
- 阶段PSS包含主PID和服务记录中绑定renderer PID；初始/20页主PSS取系统PSS汇总的该PID行，40页起明确PID输出。翻页11个阶段采样合计116,792–123,766KiB；这是阶段采样范围，不是全时峰值。完整分项见内存摘要（本地证据：`_analysis/verify/search-014/p4/memory-summary.json`），原始`*-host.txt/*-renderer-*.txt/*-services.txt`保留关联。

| 阶段 | 主PSS KiB | 绑定renderer PSS KiB | 合计KiB |
| --- | --- | --- | --- |
| 翻页0 | 74,829 | 43,014 | 117,843 |
| 翻页200 | 78,605 | 45,161 | 123,766 |
| 开关10打开 | 84,230 | 66,576 | 150,806 |
| 开关10关闭 | 115,022 | 无绑定renderer | 115,022 |
| 开关20打开 | 84,873 | 66,506 | 151,379 |
| 开关20关闭 | 109,854 | 无绑定renderer | 109,854 |
| 开关30打开 | 85,791 | 66,576 | 152,367 |
| 开关30关闭 | 113,879 | 无绑定renderer | 113,879 |

## 477a阶段设备恢复与交接（历史状态）

- P4实际已安装base.apk的设备端SHA-256精确等于477a最终包，版本1.0.28/48；`installed-apk-sha256.txt`、`final-version.txt`。没有停在自然升级测试的旧baseline。
- 每6页CHECKED已回读（`final-refresh-checked/layout.json`）；552×768 physical无override，HOME/MainActivity，见`final-size.txt/final-front.txt/final-home-layout/`。未改变系统网络、锁屏、字体；未读取PIN、用户正文、全偏好、用户数据库或备份。
- 本轮新装probe已卸载Success，pm path为空；`probe-uninstall.txt/probe-final-path.txt`。仅本轮`/data/local/tmp/p4-rd014-send.bin`临时文件已删除。独立Download合成文件、对应阅读进度与书签保留供后续回归，未覆盖/删除用户文件。
- P4已释放给集成者，无本子任务仍运行的ADB/测试脚本；只剩验收文档交接。本批源码7d8a680，后续仅本批验收文档差异，未自行提交/推送/升版/发布。

## 验证边界与工具处理

- 模块JVM测试、完整Gradle门禁与源码由集成者负责，本子任务执行设备验证。未测试真实SAF撤销、系统锁屏/网络、物理EPD波形、真实用户文档及全部复杂Office对象。
- 原版式切换后立即点设置可能仍忙碌；等就绪后重新打开全文搜索完成，未据准备阶段CLI无匹配判产品失败。
- 大CSV统计第二次采到progress后再点取消发生已完成竞态，该次不计取消；第三次立即明确取消命中才计通过。
- Probe原本未安装（`probe-before.json`）；本轮私有files/docxzip仅写合成样本，最终已卸载及删除临时文件。未读取用户应用私有数据。

## 最新Entry任务归属补丁fbd4（已完成，取代前文最终安装状态）

- S4发现真实NEW_TASK入口重复打开同URI可能只bring HOME而不forward。集成者仅将DocumentEntryActivity Manifest的taskAffinity设为空，源码提交`2da644b`；reader JS/CSS与477a逐字节相同，差异清单`_analysis/verify/office-next/entry-affinity-source-manifest.json`。
- 补丁APK`_analysis/verify/office-next/entry-affinity-release.apk`，2,170,000B，SHA-256 `fbd4bebc2e593fc413ea77b03f1f2f5b2125b748080a35d1fbcab2d8fe2878e9`，覆盖安装保留数据。没有主动清旧task、force-stop或重建数据；自然升级后首两次相同XLSX URI隐式VIEW flags1均恢复4001/6000行，关阅读后HOME正常。
- 本轮独占P4，补丁包完成同URI隐式VIEW30次和必要状态/SEND smoke；477a的200有效引擎翻页保留为历史包证据，不重记为补丁包执行。新证据独立存`_analysis/verify/search-014/p4/entry-fix/`，未覆盖477a开关30次记录。
- 30次均为`am start -p com.shuiyu.octoberrain -a android.intent.action.VIEW -d <同一payload-cancel.xlsx URI> -t application/vnd.openxmlformats-officedocument.spreadsheetml.sheet -f 1`，shell按NEW_TASK启动；不指定component、不清旧task、不force-stop。每次等4001/6000行就绪，BACK关闭阅读再HOME，布局无reader_status。序号1–30连续、唯一，脚本断言全部4001且closedHome=true；证据`implicit-cycles.jsonl`、`summary.json`、`cycle-*-am.txt`与`ui/p4-search-entryfix-cycle-*`。
- 第8次准备状态超过原一次重试观察窗口，先停止脚本、只读补布局确认4001，再关闭记入第8次，未重新发打开Intent；日志标记lateReadyObserved=true。之后使用最多4次有界就绪轮询。该事件是准备延迟，没有出现只bring HOME不forward。
- UTF8-BOM/GB18030两份自然迁移CSV仍142行1列；GB截图中文正确。真实Parcelable EXTRA_STREAM+ClipData/readgrant SEND DOCX到正文1/37成功，证据`ui/p4-search-entryfix-legacy-*`、`ui/p4-search-entryfix-real-send-docx/`。无需DB注入或重新选择编码。

| fbd4路由阶段 | 主PSS KiB | 绑定renderer PSS KiB | 合计KiB |
| --- | --- | --- | --- |
| 第1次打开 | 97,521 | 68,024 | 165,545 |
| 第5次打开/关闭 | 83,407 / 95,818 | 66,784 / 无绑定 | 150,191 / 95,818 |
| 第10次打开/关闭 | 82,179 / 92,758 | 66,734 / 无绑定 | 148,913 / 92,758 |
| 第20次打开/关闭 | 81,578 / 90,449 | 66,745 / 无绑定 | 148,323 / 90,449 |
| 第30次打开/关闭 | 78,452 / 87,741 | 66,790 / 无绑定 | 145,242 / 87,741 |

- 阶段PSS取主PID及服务绑定renderer，原始关联与独立摘要（本地证据：`_analysis/verify/search-014/p4/entry-fix/memory-summary.json`）保留；本轮路由观察无OOM/ANR/崩溃，采样不是全时峰值。
- 最终设备端base.apk SHA256精确匹配fbd4补丁；每6页CHECKED、552×768无override、HOME。对应`entry-fix/installed-apk-sha256.txt/final-size.txt/final-front.txt`及`ui/p4-search-entryfix-refresh-checked/`、`ui/p4-search-entryfix-final-home-layout/`。
- 本轮重新安装的probe原本不存在，已卸载Success且pm path为空；仅本轮`/data/local/tmp/p4-entryfix-send.bin`临时文件已删除。未改网络/锁屏/字体，未读取用户正文/全偏好/DB；合成Download文件与自身阅读记录保留。P4已恢复并再次释放，无残留脚本；后续只有验收文档差异，未推送/升版/发布。
