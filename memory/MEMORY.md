# 稳定项目记忆

维护日期：2026-10-22。只收可追溯结论；动态进度见 [STATUS.md](STATUS.md)。本文件手工维护，不声称存在自动注入。

| 结论/经验 | 来源与边界 |
| --- | --- |
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

原始深入经验保存在 [DSH 工作手册](../docs/design/33-dsh-development-playbook.md)，不在本文件复制长案例。遇到结论被新证据推翻，更新本表并链接新旧证据，禁止将推测直接升级为事实。

隔离PDF经验：Android会在`:reader_pdf`后追加服务名；精确名称匹配导致隔离Application误读配置，已改前缀跳过桌面初始化。S4实际isolated UID990xx与服务异常恢复通过，见2026-10-22-reader-pdf验收；不据此推断其他进程命名。

阅读器资源经验：ZIP先预检中央目录再建索引；文本按需解压前缀，并以共享预约计入尚未展开的缓存额度，EOF核查真实输出/CRC。S4合成ZIP首窗64KiB、退出缓存清零通过，见2026-10-22-reader-zip验收；不能据此宣称URI压缩包零复制或ZIP64支持。

WebView83经验：单活动宿主+会话/来源/请求校验+可信脚本，文档正文与CSS均经允许列表。字号重排保存字符锚点；EPUB只加载当前章，逐章搜索，不将全部spine挂入DOM。验收见2026-10-22-reader-web-markdown及EPUB记录；最终双机/压力范围仍由017收口。
