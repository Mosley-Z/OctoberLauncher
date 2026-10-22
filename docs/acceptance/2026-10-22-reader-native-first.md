# 原生文本阅读器首轮验收（2026-10-22）

范围：用户授权八项性能优化后开始设计34的阅读器计划。源码基线1379e8b + 本轮未提交差异，版本仍1.0.28。本报告不是全部21张RD卡完成结论。

## 已实现

- 独立feature:reader模块，公共来源/位置/能力契约，经AppServices和Destination路由；文件浏览与外部content URI接入，不新增外部解析库。
- 原生文本阅读、64KiB窗口、字节锚点、上一页/下一页/音量键、字号和UTF-8/UTF-16/GB18030切换。页历史最多64项，超出后反向窗口定位。
- 来源只读；可seek文件不复制，管道按256MiB总缓存上限落盘，正常退出删除所属临时文件。
- 独立SQLite保存已显示位置、编码、字号；书签添加/列出/跳转。主线程只发布帧，IO和数据库在后台；退出取消并关闭会话。
- 文本分块搜索下一个匹配，保留查询长度尾部以跨窗口匹配，查询最多256字符，每窗口检查取消，不保存全文或全部命中结果。

## 验证与边界

- 全项目check及debug/release构建通过，日志`_analysis/reader-probe/native-gate.log`。reader单元测试覆盖魔数识别、有界复制、跨编码字符边界、50MiB seek、搜索跨窗口与取消；公共JSON契约另有2个用例。
- S4使用隔离的合成样本Provider；连续8次下一页，位置3536；强制结束进程再打开，同一位置3536。没有写入Launcher凭据或偏好。
- S4打开约50MiB合成文本显示0% UTF-8，首屏无需读完全文件。该项验证打开能力和代码读取边界，不宣称屏幕首显耗时。
- S4未知长度pipe.txt正常显示，阅读时拥有171000字节缓存；返回后reader-sources目录为0。证据`_analysis/reader-probe/s4-text-fixtures.txt`。
- S4 GB18030样本通过显式编码选择显示中文，截图在系统临时目录。UTF-16目前只有纯JVM字符边界测试，尚无设备排版验收。
- AndroidRuntime错误日志为空（上述样本范围）。P4的PdfRenderer/SharedMemory/WebView能力来自独立探针，不代表产品阅读器PDF通过。

## 未完成

- PDF隔离服务、Web宿主、Markdown/EPUB/ZIP/Office引擎尚未实现，页面明确显示引擎未启用，可由用户选择其他应用打开。
- 稀疏索引、搜索高亮/结果列表、书签编辑删除、最近阅读入口、旋转重排、异常退出缓存回收和完整压力矩阵仍待后续卡。
- TASK-RD-000的Office真实库探针待完成；001–005本轮部分实现保持IN_PROGRESS。没有把设计中的Registry/OpenPlan/WebHost写成已实现接口。
- 无版本升级、提交、推送或发布；后续继续在当前差异上工作，不能reset/clean。


## 最后验证增量

S4搜索输入合成数字串后定位4001；在该处添加书签，继续翻4页，通过书签列表回跳，保存位置再次为4001，合成书签计数1。`s4-search-bookmark-runtime.txt`无AndroidRuntime错误。两台独立探针均已卸载，S4返回桌面；合成来源的少量阅读记录保留于独立reader.db，不触碰用户桌面配置。

reader模块9个JVM用例、common全模块79个用例无失败（含本轮2个契约测试）。最后处理Lint的绘制分配、Intent data属性和performClick；仅程序构造View的预览构造函数及保守使用当前空闲空间两项有具名理由的局部SuppressLint，未扩大错误基线。最终reader Lint报告No issues found。

最终门禁：1556 tasks，21秒，退出0；收尾后S4覆盖安装debug、P4覆盖安装release并启动宿主成功。上述完整文本流程在收尾Lint调整前执行，最终包完成安装/启动冒烟。

- debug：5320465字节，SHA256 `0ddc4c8ce6d48dcff0c48684998b2b1a5e6d31d71513fabb8ff55efa6e868189`。
- release：1241901字节，SHA256 `f67a19cbe0dc573496a4e04fcadbd9f8e81b4bc8a3cf18bc74c29468fe7961bc`。
