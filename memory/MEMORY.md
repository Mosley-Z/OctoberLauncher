# 稳定项目记忆

维护日期：2026-10-23。只收可追溯结论；动态进度见 [STATUS.md](STATUS.md)。本文件手工维护，不声称存在自动注入。

| 结论/经验 | 来源与边界 |
| --- | --- |
| Office用SheetJS0.20.3与PPTXjs1.21.1固定完整资源链，工作簿原生20×10窗口，PPTX只传当前页关系闭包；8MiB/32MiB及结构预算在JS前检查 | 2026-10-23-reader-office-search-integration、专属许可/manifest；小样本兼容不等于完整Office |
| 统一搜索扫描不变更位置，完整计数仅缓存64命中，跨窗口重扫；表格按匹配单元格计数。受控mark/原生高亮，不依赖WebView83缺失的Custom Highlight API | SearchProvider/ReaderSearchUi；显示命中后remember/flush，取消同时使JS token失效 |
| CSV从旧纯文本升级时必须迁移Text字节锚点和旧编码到逻辑记录，不能按物理行数（引号可含换行）；先8KiB增量解码计字符，再按CSV记录边界定位 | CsvData.legacyRow/CsvLegacyTest；只保留当前窗口，旧书签共用迁移 |
| Android CLI text=为子串匹配，状态含“阅读设置”会误命中；阅读按钮用id=reader_settings或当前annotate标签，并回读实际结果 | 2026-10-23 P4合成CSV错误恢复验收；CLI成功不等于产品交互通过 |
| DOCX正文/原版式采用固定docx-preview0.4.0与JSZip3.10.1；原版式按宽适配/页内滚动，禁止将长页整屏缩到不可读。Docx定位器保存模式与锚点；复杂Word排版/P4仍待 | 2026-10-23-reader-docx-and-entry验收，S4/WebView83及release首轮 |
| 自有FileProvider files_share URI与规范路径共用身份，旧别名在事务中保留较新进度与全部书签；归档需继承父别名和编码/路径/CRC。不可将任意第三方URI或同名副本直接合并 | SourceResolver/OwnedDocumentPath/ReaderIdentity，2026-10-23入口迁移设备证据；通用摘要关联未做 |
| 负一屏微信读书与桌面本书共用weread缓存及book刷新入口，不独立计时/复制快照；本地书源仍手动。先排序后限量，以同一目标书对照所有入口的成员和排名 | FIX005/006、2026-10-22-shared-shelf-refresh验收；取代FIX004独立自动模式，P4待补 |
| 十雨是 Android 墨水屏 Launcher，Kotlin + View，包名 com.shuiyu.octoberrain；feature 不横向依赖 | README、settings.gradle.kts、现有模块；新增阅读器沿用此边界 |
| 本目录已经是 Git 仓库，旧规范的“无 Git”过时；不要清理其他人未提交改动 | 2026-10-22 git status；分支和版本每轮重查 |
| 本机无 Gradle Wrapper，构建入口 scripts/build.ps1；环境取 toolchain.path，不依赖全局 JAVA_HOME | 实读脚本；任意任务环境初始化见开发流程 |
| 模块入口由反射注册，新增模块同步 R8 规则；debug 成功不能证明 release 可运行 | README、FilesModule、阅读器方案 |
| 文件页非图片已接ReaderGateway，当前原生TEXT/PDF；其他格式依任务卡实现 | 2026-10-22 ReaderRegistry/BrowseScreen，reader-native-first与reader-pdf验收 |
| WebView 83 适配按格式决定；PDF 原生、Markdown JVM、Office 固定资源验证 | docs/design/34，第5节；库的最低版本是资源特性，不是格式特性 |
| API30 PdfRenderer 基础路线不提供 PDF 全文搜索/选字，扫描件 OCR 是另一项能力 | 阅读器方案第4、5节；不要宣传完整PDF编辑/搜索 |
| 整个桌面已有 INTERNET 权限，阅读器离线要由资源边界实现 | app Manifest、阅读器方案第6节；不能复制“整应用无联网权限”表述 |
| 微信读书当前目标是十雨独立获取/使用数据，不要求原 App 导入或回写 | docs/design/32、33；历史迁移实现不等于当前产品需求 |
| 当前已打包 jar 的 API 才是契约，不能用上游最新源码猜枚举/位标志 | docs/design/33 §4；必要时 javap 核实并测 release |
| 配置写入必须追到实际消费者，取消/清空/同步/重启要保留用户语义 | docs/design/33 §1–5；页面回显不等于功能生效 |
| 绘制不能反复全库 IO；先保证后台队列，再消除逐书重复扫描；缓存要有明确失效 | docs/design/33 §6；已有P4大书库经验，不代表本轮已测 |
| 墨水屏 UI 需要实际触点、滚动、字体与尺寸验证，截图不证明交互命中 | docs/design/33 §7、10；P4/S4分开记录 |
| 重绘请求不等于 EPD 波形刷新；应核实设备控制器实际行为 | 阅读器方案第9节，待设备验证 |
| 多Agent共享源码可并行，公共契约/Gradle构建/同一设备必须明确唯一操作者 | docs/design/33 §8、新流程 §4 |
| ZIP 容器按内部标记判定格式，扩展名只作回退；先经条目预算与归档路径规范化 | 2026-10-22 ZipContainerProbeTest 9 例、DocumentKindsTest 5 例；Office 仅识别容器，未渲染 |
| 外部入口只接受带读授权的 content URI；VIEW 取 data 与 ClipData，SEND 取 EXTRA_STREAM 与 ClipData；多 URI 与 SEND_MULTIPLE 拒绝；持久授权仅在 persistable 时写入 | 2026-10-22-reader-sources-002；S4 七例，与真实 SAF 重选边界见记录 |
| 导出 Provider 的 openFile 在该设备未见系统按 URI 授权拦截；撤销用例需探针显式拒绝读取，以产品 reject= 日志为可观察证据；uiautomator dump 在 S4 滞后于渲染 | 2026-10-22-reader-sources-002 与探针日志；设备与合成 Provider 范围内成立 |
| 界面观测用 android CLI：标准 View 走 layout/find/click，桌面自绘元素走 screen capture --annotate 加 resolve 与 tap -Label；脚本 scripts/android-ui.ps1，输出 _ui-out/ | docs/design/33 §10；2026-10-22 在 S4 验证 layout、annotate、resolve、tap、find、click、key 七个动作 |
| 最近阅读通过 core 契约 `RecentDocument` 与 `RecentGateway` 暴露，阅读器模块注册、书架只读契约；书架本地书打开走 ReaderGateway，不再交外部应用 Intent | 2026-10-22-reader-entry-integration-dsh；S4 验证书架最近阅读点击与三国演义.epub 打开，文件页入口未重跑 |
| CLI 的 annotate 需要可写的 TMP/TEMP；系统 TEMP 被拒时报 `Can't create cache file!`，脚本已把 TMP/TEMP 与 ANDROID_USER_HOME 默认指到工作区 `_ui-out/` | 2026-10-22 S4 实测；无标注截图不受影响，bundles lock 拒绝访问同源 |
| 跨卡整改先固定最终入口/位置/显示归属，再逐调用点核对；规则单测不替代忙碌设备触发。原始字段存在性与默认值必须区分 | [36 需求对接约定](../docs/design/36-demand-handoff-contract.md)、FIX 接管；源码与 S4 范围，P4 待验 |


原始深入经验保存在 [DSH 工作手册](../docs/design/33-dsh-development-playbook.md)，不在本文件复制长案例。遇到结论被新证据推翻，更新本表并链接新旧证据，禁止将推测直接升级为事实。

隔离PDF经验：Android会在`:reader_pdf`后追加服务名；精确名称匹配导致隔离Application误读配置，已改前缀跳过桌面初始化。S4实际isolated UID990xx与服务异常恢复通过，见2026-10-22-reader-pdf验收；不据此推断其他进程命名。

阅读器资源经验：ZIP先预检中央目录再建索引；文本按需解压前缀，并以共享预约计入尚未展开的缓存额度，EOF核查真实输出/CRC。S4合成ZIP首窗64KiB、退出缓存清零通过，见2026-10-22-reader-zip验收；不能据此宣称URI压缩包零复制或ZIP64支持。

WebView83经验：单活动宿主+会话/来源/请求校验+可信脚本，文档正文与CSS均经允许列表。字号重排保存字符锚点；EPUB只加载当前章，逐章搜索，不将全部spine挂入DOM。验收见2026-10-22-reader-web-markdown及EPUB记录；最终双机/压力范围仍由017收口。
