# 2026-10-23 P4 Office release 验收

- 验收者/范围：P4测试子智能体，独占`P4L202610130006`；表格全格式代表样本与PPTX抽验。未操作S4、源码、Gradle或共享构建输出，未改RD017。
- APK：`_analysis/verify/office-next/office-release.apk`，2,160,730字节，SHA-256 `bf8b834c807411b114cf33474584299e44f75342ae265695eb2b1115c349412a`；集成者提供的Office首轮包，113例模块测试由集成者执行，本子任务只记录设备结果。不是后续搜索版最终门禁。
- 环境：P4、Android11、WebView83.0.4103.120、物理552×768，无override。覆盖安装`install -r` Success，保留用户数据。
- 样本：`_analysis/verify/sheets-012/`及`_analysis/verify/slides-013/`中的合成数据，部署到独立`/sdcard/Download/p4-office-20261023/`；外部VIEW自身files_share URI，读授权1。
- 关联：[RD012](../../task/TASK-RD-012-sheets.md)、[RD013](../../task/TASK-RD-013-slides.md)、[RD017](../../task/TASK-RD-017-release-gate.md)。

以下证据前缀均为`_analysis/verify/sheets-012/p4/ui/p4-baseline-office-`。界面全部用项目`android-ui.ps1`，显式Serial。原始采集在`_ui-out/`，已复制至本批独占证据目录。

| 用例 | 操作/预期 | 实际 | 证据后缀 | 结论 |
| --- | --- | --- | --- | --- |
| XLSX原生窗口 | 打开sheets-fixture.xlsx，中文/合并/公式 | 中文第一表1/4行1/4列；A2:B4合并锚点占据合并区域；公式缓存3，缺失缓存空白 | `xlsx/`、`xlsx-screen/`、`horizontal-screen/` | 通过 |
| 未加载工作表搜索 | 首表搜索“跨表目标” | 中文第二表2/3行1/1列 | `search-result/` | 通过 |
| 工作表单选 | 设置→工作表→中文第一表 | 双工作表列表，切回首表 | `sheet-layout/`、`coordinate-layout/` | 通过 |
| 非首行列定位 | 输入行3、列3，回读两框为3后跳转 | 中文第一表3/4行3/4列 | `coordinate-confirm/`、`jump3-layout/` | 通过 |
| 书签/强停恢复 | 3,3添加书签，强停重开后查看书签 | 列表保留3/4行3/4列条目；之后叠层布局可见恢复的XLSX3,3状态 | `mark33-list/`、`sheets-fixture.xls-open/`（首次采集有底层XLSX，后续同名复验覆盖为单层，持久化条目证据仍在列表） | 书签持久化通过；本轮未执行书签条目动作菜单的“跳转”按钮，不记书签回跳通过 |
| XLS/ODS | 分别打开同样合成表格 | 均中文第一表1/4行1/4列，原生中文合并格截图可见 | `sheets-fixture.xls-*`、`sheets-fixture.ods-*` | 通过 |
| CSV引号与换行 | quoted.csv | 3行；A2“中文,逗号”未拆列，B2第一行/第二行同格两行，C2 a"b；末行保留 | `quoted.csv-screen/` | 通过 |
| CSV晚段搜索 | large.csv初窗已读20行，搜索晚段目标 | 定位CSV901行3列、已读920行；样本0起始行900对应UI901 | `large-result/` | 通过 |
| 纵滚 | large.csv搜索后滚动原生ScrollView | 截图显示905–910行，底栏仍可用 | `large-scroll-screen/` | 通过 |
| 横滚 | XLS首表原生HorizontalScrollView向右 | 截图显示C/D列、公式缓存3及缺失缓存列 | `horizontal-screen/` | 通过 |
| CSV编码恢复 | gb18030.csv初次UTF-8失败→实际设置→编码GB18030 | 从“编码无法识别”恢复为2行；“列一/列二、中文/编码验证”正确 | `gb-settings-exact-layout/`、`gb-confirm-layout/`、`gb-confirm-screen/` | 通过 |
| 超预算恢复 | oversized-dimension.xlsx | 显示文档超出当前阅读预算，返回及HOME可用；错误后设置仍可用 | `oversized-dimension.xlsx-*`、`final-home-layout/`、`final-id-layout/` | 通过 |
| PPTX混合样本 | mixed.pptx原版式→文字提纲 | 原版式1/3中文/表格可见；提纲1/3列出标题/正文/表格及不支持对象提示 | `mixed.pptx-*`、`mixed-outline-layout/` | 文本/表格/模式通过；1×1合成PNG区域出现base64尾段alt，未据此宣称图片显示质量通过 |
| PPTX200页 | deck-200.pptx跳到指定页200 | 200/200，中文幻灯片200、跨页检索命中_200 | `deck-page200-layout/` | 通过；未做200次连续翻页压力 |
| 最终刷新/恢复 | 全刷只读核对 | 每6页CHECKED；HOME；无尺寸override | `final-id-layout/`、`_analysis/verify/sheets-012/p4/final-{front,size}.txt` | 通过 |

## 工具误判纠正

GB18030错误页曾报告“点击设置无菜单”，最终排除为工具子串匹配，不是产品缺陷：`text=阅读设置`匹配了错误状态中“请在阅读设置中”的文字；`class=android.widget.Button`也不匹配InkWidgets实际TextView。经当前annotate→resolve确认阅读设置为#3、275,698，`tap -Label #3`打开编码菜单并恢复成功。最终稳定选择器为`id=reader_settings`。仅限定Reader/AndroidRuntime日志无异常，见`_analysis/verify/sheets-012/p4/gb-technical-log.txt`。

行列输入同理应使用CLI文档支持的`desc=`，不能照layout键名写`content-desc=`。已回读输入实际3/3后才计定位通过。首次OutDir直接指向较长`_analysis/.../p4/`路径时CLI未写JSON/PNG，改回既有`_ui-out/`采集再复制；不据此判设备失败。

## 恢复与边界

- 最终P4保持本Office包、HOME、refresh6、原生552×768，已释放给集成者，可装后续搜索版；没有仍运行的设备脚本。未改系统网络、字体、锁屏，不读取PIN或用户正文/数据库/全配置。
- 新增独立合成文件目录、文档阅读记录与XLSX书签保留供最终包复验；GB18030编码仅对该合成CSV选择。未覆盖或删除用户文件。
- 未提交/推送/升版/发布；无Gradle构建。payload-cancel.xlsx已部署但未执行取消压力，不算通过。
- 未覆盖：后续统一搜索多结果UI、搜索取消/忙碌重排、所有格式书签回跳/改名/删除、CSV编码强停后保存、PPTX真实图片质量及复杂图表/嵌入、长时压力/峰值PSS、真实SAF/网络/锁屏及桌面全部FIX闭环。由集成者在最终同一APK双机收口。
