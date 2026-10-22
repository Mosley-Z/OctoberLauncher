# RD012 表格窗口源码与分层验证

日期：2026-10-23。执行：sheets 子智能体；集成：Codex。源码基线 master/0a1534c 加本卡专属差异。此报告当前记录源码交付及Node脚本结果，不代表Android产品或设备验收通过。

## 实现与接口

- `CsvEngine.create(source, locator, charset)`：原生流式RFC4180解析，包含引号、双引号转义、CRLF/引号内换行、空字段、UTF-8/GB18030；每256行保存解码字符锚点，重开流跳过字符前缀后恢复解析，取消检查在跳过、逐行及长字段中执行。源不seek时仍需读前缀，不宣称常数时间随机跳转。单字段65536字符、单记录262144字符、最多100000行/1024列；仅窗口保留20×10、单格显示最多512字符。
- `WorkbookEngine.create(source, locator, font)`：复用既有离线Web宿主，唯一来源路径`res/source.bin`。主机先执行容器/内容预算，再由固定SheetJS CE0.20.3整体解析；跨桥只返回20×10和与窗口相交合并区，单条JSON控制在250000 UTF-8字节内，必要时继续截短窗口文本。
- 两者实现`TableEngine`，提供`sheetNames/locate/acceptTable`，专属行列定位替代共享PDF跳页；`SheetTableView(context,table)`用原生黑白网格与双轴滚动显示窗口，合并原格锚点在窗外仍保留值。
- Locator沿用本批冻结`ReaderLocator.Sheet(sheet,row,column,charset)`。根负责Registry/Screen/宿主/保存进度/书签与最近阅读接线，本卡没有修改这些公共入口。

## 预算与格式边界

工作簿压缩/二进制源最多8MiB，ZIP声明及真实展开受32MiB限制；100个工作表、100000个存储单元格、100000行/1024列、500000个XML节点。XLSX逐XML元素/坐标预检；ODS重复行列按展开单元格计数。CFB先验证扇区链并提取Workbook流，BIFF8记录扫描统计格数和维度后才能交SheetJS，不把DOM窗口当整簿内存预算。

本批XLS支持BIFF8，旧BIFF版本不支持；扩展DIFAT的CFB超本批预览边界。ODS大范围重复空行空列也计入预算，可能拒绝内容稀疏但声明范围极大的文件。公式只显示已有缓存值；缺失缓存标记`[公式无缓存]`，无公式重算/宏执行/完整图表能力。CSV在到达EOF前只报告已读行数下界，不虚报已扫描全文。新搜索从全表/全簿开头查，重复同一查询从上次命中之后继续，到末尾返回未命中，不自动环回。

## 已执行证据

命令`node _analysis/verify/sheets-012/verify-host.cjs`及`python _analysis/verify/sheets-012/generate-extra-fixtures.py`，均退出0。脚本验证固定库版本0.20.3，生成合成工作簿/CSV/超限范围样本，并在Node VM模拟桥调用；输出保存在`_analysis/verify/sheets-012/node-host-results.txt`：

| 格式 | 检查 | 结果 |
| --- | --- | --- |
| XLSX | 中文、两sheet、缓存公式值、缺失缓存标记、合并锚点裁窗、跨表搜索和未命中 | 通过 |
| XLS/ODS | 中文、两sheet、缓存值、合并锚点裁窗、跨表搜索和未命中 | 通过 |
| CSV样本 | quoted.csv包含BOM/逗号/转义/跨行；large.csv含1000行和晚段目标 | 已生成，Android待验 |
| 桥预算 | 20×10控制字符单格512字符导致JSON转义膨胀，自动缩短窗口文本至250000 UTF-8字节内 | 通过 |
| 统一搜索 | 3格式全簿计数/offset/showHit；5980个命中仅返回offset64起64条，扫描被新定位请求取消不回旧结果 | 通过 |

样本/脚本在`_analysis/verify/sheets-012/`。这是可信脚本逻辑验证，不覆盖WebView83解析性能/Android绘制/触点，也不替代主机预算JVM测试。

## 待执行

根串行运行reader `ktlintFormat/testDebugUnitTest/detekt`及整包门禁。测试类：CsvRecordsTest（7例）、WorkbookBudgetTest（6例）、CsvEngineTest（6例）、WorkbookContainerTest（3例）。JVM阶段已观察前3类旧16例通过，新增容器/统一搜索后最终复验仍待根回报；不以旧结果覆盖最终源码。设备至少补最终release的S4/P4打开、sheet切换、行列定位、跨表搜索、GB18030、合并横滚、书签/强停恢复、退出释放和超限反馈。最终APK、摘要和设备证据由集成者追加，不预写通过。

## RD014表格增量（根分派）

两引擎实现公共`SearchProvider`：CSV独立流完整扫描，不改当前窗口/可见EOF状态；每匹配单元格一条，完整计数但只保留offset起64条。`CsvData`抽离既有IO，`CsvSearch`单独计数；查询不进入持久化。Workbook发`searchAll`请求，JS自然顺序遍历所有工作表，返回`searchResults`中的sheet/row/column/snippet，计数完成前不回虚假总数。

命中选择走`showHit`，CSV直接定位、Workbook发`hit`并待表格确认后显示；ReaderTable临时highlight为绝对行列，原生灰底/粗黑框突出单元格。扫描本身不显示帧或改变阅读位置。统一搜索UI、结果协议验证和进度保存归根集成者，本卡未越界修改。

为适配Android SAX，预算预检不依赖Apache专有feature。SheetXmlInput扫描整个输入流，跨read边界、UTF-8/UTF-16LE/BE均拒绝DOCTYPE/ENTITY；实体resolver额外拒绝外部实体。全部ZIP非目录项在SheetJS前读到EOF校验真实输出和CRC，非XML只用8KiB缓冲，不把声明尺寸当真实输出。

## 旧CSV文本进度和书签升级

基线将CSV作为TEXT打开，旧位置是`ReaderLocator.Text(byteOffset,charset)`。本批`CsvEngine.page`兼容此旧类型：按根Registry提供的旧编码，用固定8KiB增量解码前缀累计已完整解码字符数，再按原始CSV逻辑记录扫描定位行，列固定0；不把引号内换行误计为新记录。BOM内部锚点回首记录，恰在记录边界归下一记录，EOF/越EOF/空表夹到有效末记录或0。旧书签与旧进度共用同一迁移路径；显示成功后经现有流程保存Sheet位置，不修改源文件。

`CsvLegacyTest`新增4例，覆盖UTF-8/GB18030、BOM有无、引号跨行、旧字节锚点/书签、8KiB处四字节字符拆分、边界/EOF/空表和取消；新增后待根重新执行最终JVM门禁。迁移不分配整个文件前缀，也不使用物理行号；读取前缀和记录扫描均检查取消。

## 许可

`reader/web/sheets/xlsx.full.min.js`为SheetJS CE0.20.3，951904字节，SHA256 `cc015130aa8521e7f088f88898eba949ccdcbfb38df0bd129b44b7273c3a6f41`，与既有vendor-manifest完全一致。`reader/web/sheets/LICENSES.txt`保留源文件原始版权header、固定来源与摘要和完整Apache-2.0标准正文。原始来源是固定Gander提交中资源，非该项目其他代码迁入；官方精确tag许可链接本轮访问不可达，标准Apache正文取现有reader Apache许可资产。根已登记全局许可入口路径，本卡不触碰全局许可。
