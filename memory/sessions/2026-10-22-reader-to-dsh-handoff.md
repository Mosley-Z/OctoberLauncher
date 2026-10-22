# 2026-10-22 Codex → DSH 接力

交接状态：READY。RD010首轮完成；构建及ADB脚本均结束，共享入口和设备交还DSH，Codex不再开新卡。

## 用户目标与本轮范围

用户先授权完成8项S4性能优化，再按34方案推进阅读器；最新指令是完成当前TASK（RD010 EPUB）后沉淀并交接DSH，不再开启新卡。04:30一次性续工安排已删除，后续不再自动续工。

## 源码现场

- 工作树 `D:/DSHStation/OctoberLauncher`，master，HEAD `1379e8b`。本轮全部改动未提交，新增feature/reader等仍为untracked；不能reset/clean或只拷贝git diff而漏掉新文件。
- VERSION仍为1.0.28 / 48。无提交、推送、版本升级或发布；APK是本地验证产物。
- 完成首轮的卡：PERF001、RD001、006 PDF、007 Web宿主、008 ZIP、009 Markdown，RD010 EPUB首轮完成。其余卡以状态字段为准，目录存在不等于实现。
- 共享入口包括core:common ReaderContracts/AppServices、settings/app Gradle、app MainActivity/OctoberRainApp、Manifest/R8、ReaderRegistry/Screen/Chrome、版本目录和许可证设置页，由集成者串行维护。

## 已完成与证据入口

- 八项性能优化：[验收](../../docs/acceptance/2026-10-22-s4-performance.md)。覆盖缩略图锁/内存预算、后台快照、桌面刷新/生命周期、封面并发、摘要缓存及画板位图复用。P95单次48→29ms有采样边界，不承诺所有场景稳定提升。该阶段APK与阅读器最终APK不同。
- 文本、来源与进度首轮：[验收](../../docs/acceptance/2026-10-22-reader-native-first.md)。固定64KiB窗口，UTF8/UTF16/GB18030，分块搜索与取消，书签添加/列表/跳转，SQLite定位。
- PDF：[验收](../../docs/acceptance/2026-10-22-reader-pdf.md)。隔离进程+PdfRenderer/SharedMemory，固定视口12MiB邻页缓存；S4百兆扫描PDF首尾、缩放/裁边/恢复及worker崩溃恢复通过。首屏2秒目标未证实，API26不能使用SharedMemory PDF路径。
- 离线宿主/Markdown：[验收](../../docs/acceptance/2026-10-22-reader-web-markdown.md)、[依赖](../../docs/acceptance/2026-10-22-reader-dependencies.md)。WebView83、可信消息、CSP与白名单资源、renderer实际崩溃恢复；源码库和完整许可已固定。
- ZIP：[验收](../../docs/acceptance/2026-10-22-reader-zip.md)。中央目录预检，虚拟目录，选中TXT按需解压；PDF按需落盘；PNG静态采样预览。S4返回中文目录与退出缓存清零通过。
- EPUB：[最终验收](../../docs/acceptance/2026-10-22-reader-epub.md)。按章、目录/NCX、有限CSS、跨章搜索/链接、图片章、书签和恢复已补验。

## 接口与资源约束

- DocumentRef支持路径、带授权URI、ArchiveEntry（charset默认UTF8）；Archive/Image/Epub Locator已序列化。引擎异步动作统一由ReaderSession IO串行处理，主线程确认显示后才保存位置。
- PDF Application进程名实际带服务类后缀，必须按`:reader_pdf`前缀跳过桌面初始化。错误精确匹配曾导致child误读配置，已修复；来源见PDF验收。
- 临时来源统一128MiB预约；压缩包URI可能复制本体，不能宣称全格式零落盘。TXT流EOF检查输出/CRC，部分预览退出不强制解压完；进程重启只清理自身source-*.cache。
- Web单活动View，暂停销毁；正文和CSS不直接可信。EPUB只加载当前章，逐章搜索，跨章链接必须属于当前会话精确清单。资源关闭与ZIP图片读取串行，避免退出时竞态。
- 原生功能Kotlin+View，feature不互相依赖；共享契约进core:common。关于页提供随包许可证原文，避免把候选Office探针库直接塞入产品。

## 未完项与DSH接手顺序

1. 先读PROJECT、STATUS、MEMORY、本交接及任务卡，再核实git与工具链；不要重复已有效验收，更不能复用历史设备连接假设。
2. RD002：ZIP内部标记鉴别（当前候选仍结合扩展名）、ACTION_SEND、完整SAF/授权撤销矩阵；ArchiveEntry及缓存回收已经实现，不要再从零写。
3. RD003/004/005：旋转/窗口重排、书签编辑删除、最近阅读入口、稀疏索引与高亮；当前已有基本工具栏、跨章定位/书签恢复和文本全局搜索，不要当作全空白。
4. RD015/016收基础阅读MVP与压力；Office仍只有兼容探针，按000与011–013依赖完成产品引擎。再由014/017收搜索和最终设备矩阵。
5. P4此前只能确认安装/启动；截图全黑、层级未见阅读控件，不能据此断言锁屏原因或宣称阅读通过。后续由017在可观察界面上完成双机交互。

ZIP64/多卷/加密ZIP、EPUB加密资源/固定版式、全部作者CSS/自定义字体、图片EXIF/缩放/动画压力、完整复杂Office兼容均未宣称支持。API26/27及其他WebView版本还没有真实设备矩阵。

## 可执行验证与交付边界

按docs/design/35-development-workflow.md设置toolchain.path对应JDK/SDK/GRADLE_USER_HOME，单独完成受影响模块ktlintFormat后，执行 `check :app:assembleDebug :app:assembleRelease --offline --console=plain`。共享Gradle输出一次只跑一个构建。Windows默认沙箱native DLL失败时使用已授权工具链执行环境，不能把工具链问题写成源码通过。

本轮最后全量check/debug/release已通过；reader30用例全部通过，core:common 81通过+1历史MonthGrid跳过。文档检查只有2个已登记历史WARN。最终摘要见_analysis/verify/epub-010/final-apk-manifest.json。S4/P4最终release、HOME；P4只计安装启动。S4无线恢复、合成probe已卸载，无运行构建/ADB脚本。

只有技术字段、计数、尺寸和合成样本进证据。不要读取/复制整份设备偏好、用户正文或凭据；保留用户状态，不恢复已删除的统计/月历/实验室。合成URI记录不含正文；最近阅读实现应处理非持久授权失效，不能清空用户DB。

最终经验：S4工具栏须避让底部44px手势区，已从743–800px上移到699–756px并以中心点击验证。驱动应删旧XML、验证新dump及按钮启用；旧阶段驱动尾段失败已在最终debug/R8焦点驱动补齐，不能宣称旧驱动退出0。
