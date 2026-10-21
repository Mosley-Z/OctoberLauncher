# 十雨桌面文件阅读器升级方案

日期：2026-10-21。状态：设计方案，尚未实施或进行设备验证。

## 1. 目标与依据

目标设备为 RK3326_GO、Android 11 / API 30、2GB RAM，当前 WebView 为 `com.android.webview 83.0.4103.120`。以现有 480×800 基线设计，继续支持工程的其他尺寸档位。

将“文件”从目录和图片浏览器升级为离线、多格式、适合墨水屏的只读阅读入口：统一打开、分页、目录、搜索、书签、阅读进度、最近阅读和外部文件接收。编辑仍限于现有图片编辑功能。

引用聊天的用户消息可读取，但助手正文由内容引用占位符替代，无法核实原回答的逐项建议。本方案依据这些设备约束、当前工程源码及下列公开源码独立设计，不声称复现不可读的回答。

已核对的外部依据：

- [Gander README](https://github.com/mokshablr/gander)：多格式分发、离线 WebView、原生 PDF 退路和 ZIP 浏览思路。
- [WebViewFloor.kt](https://github.com/mokshablr/gander/blob/main/app/src/main/java/com/arjun/gander/WebViewFloor.kt)：当前捆绑库的 PDF / DOCX / Markdown 下限分别为 Chromium 125 / 80 / 92，Custom Highlight 搜索路径要求 105。这些是该版本资源的限制，不能推导为格式本身的限制。
- [FileKind.kt](https://github.com/mokshablr/gander/blob/main/app/src/main/java/com/arjun/gander/FileKind.kt) 与 [ViewerActivity.kt](https://github.com/mokshablr/gander/blob/main/app/src/main/java/com/arjun/gander/ViewerActivity.kt)：文件分发和宿主接入参考。
- [Android PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer)：原生位图渲染、可寻址文件描述符及隔离执行建议。
- [WebViewAssetLoader](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content)：以本地 HTTPS 同源资源承载阅读页。
- [commonmark-java](https://github.com/commonmark/commonmark-java)：Markdown 解析候选。

## 2. 当前工程的真实起点

| 已有代码 | 当前行为 | 升级处理 |
| --- | --- | --- |
| `feature/files/.../ui/BrowseScreen.kt` 的 `onOpenItem` | 图片进入查看器，非图片显示 `OPEN_PENDING` | 接统一打开契约，作为首个可验证改动 |
| `feature/files/.../data/FileItem.kt` | 基于绝对路径的条目，区分图片 | 保留列表模型；打开时转为通用来源模型 |
| `FileListing`、`FilePrefs`、`FilePager` | 浏览、排序、分页和偏好 | 复用浏览基础设施；正文分页需要独立算法 |
| `PhotoScreen`、`ViewerDecoder`、`core:render` | 图片查看、采样、灰度、缩略图 | 图片继续使用；文档预览复用缓存与色调处理 |
| `feature/content/.../data/FileLibrary.kt` | 另有文件模型和复制至缓存后生成外部打开 Intent 的流程 | 内部阅读避免整文件复制；外部打开保留为用户选项 |
| `feature:content` 的书架 | 书籍发现、封面、KOReader 相关能力 | 保留内容聚合职责，只将打开动作汇入契约 |
| `core:common`、`ScreenRegistry`、`FilesModule` | 页面路由与反射模块注册 | 延用现有注册模式，增加阅读目的地 |
| `RefreshScheduler` | 定时全刷和内容变更调度 | 增加阅读翻页触发策略，经原出口统一执行 |

工程已有网络权限和存储管理权限，不能继承 Gander 的“整个应用无网络权限”承诺。阅读器应实现自己的离线资源边界，保留桌面现有联网功能。

## 3. 推荐架构

新增一个 `feature:reader`，负责文档阅读。现有 `feature:files` 保持目录、相册和图片编辑职责，不把所有解析器塞入 BrowseScreen。各 feature 通过 `core:common` 契约协调，不互相依赖。

```text
文件页 / 书架 / 最近阅读 / 外部 content URI
                    ↓
      core:common 中的 DocumentRef / ReaderGateway
                    ↓
          feature:reader / ReaderRegistry
                    ↓
   SourceResolver → TypeDetector → OpenPlan
                    ↓
 PDF原生 | 文本分页 | EPUB章节 | Office预览 | 表格 | ZIP
                    ↓
        定位 / 搜索 / 进度 / 书签 / 刷新
```

建议的包与责任：

| 包 | 主要类型 | 责任 |
| --- | --- | --- |
| `source` | `SourceResolver`、`DocumentHandle`、`SeekableSource` | 本地文件、授权 URI、归档条目；只在需要随机访问时落盘 |
| `routing` | `TypeDetector`、`ReaderRegistry`、`OpenPlan`、`ReaderCapabilities` | 结合 MIME、扩展名、文件头选择引擎；ZIP 容器进一步检查内部标记 |
| `session` | `ReaderSession`、`ReaderLocator`、`ReadingRepository` | 文件身份、进度、书签、最近阅读、退出恢复 |
| `pdf` | `PdfScreen`、`PdfRenderService`、`PdfPageCache` | 页面位图、跳页、裁边、缩放；独立隔离服务执行渲染 |
| `text` | `TextIndex`、`TextPaginator`、`TextSearch` | 文本按块索引，原生 StaticLayout 排版 |
| `epub` | `EpubPackage`、`ChapterLoader`、`EpubLocator` | OPF/spine/nav、章节资源、章节定位和搜索 |
| `web` | `LocalDocumentHost`、`AssetRouter`、`WebCapabilities` | 单个活动 WebView、离线资源、能力探测和会话生命周期 |
| `office` | `DocxReader`、`SheetReader`、`SlideReader` | 结构化内容与原版式预览适配 |
| `archive` | `ArchiveIndex`、`ArchiveEntrySource` | ZIP 虚拟目录，选中条目交回统一路由 |
| `ui` | `ReaderScreen`、`ReaderChrome`、`ReaderActions` | 页码、目录、搜索、书签、显示设置、其他应用打开 |

`DocumentRef` 是可持久化来源描述，不持有 Stream、Bitmap 或 WebView。`ReaderSession` 由 feature:reader 持有，不放进全局 AppServices 保存大型对象。

来源初期支持 `LocalPath`、`ContentUri`、`ArchiveEntry`。外部入口仅接收系统授予读取权限的 URI；不能接受外部任意绝对路径。临时 URI 授权不保证重启后仍有效：能够持久授权才保留，否则经用户操作导入私有文件或将最近条目标记为需要重新选择。

能力按引擎声明 `canSearch`、`canSelectText`、`canReflow`、`canJumpToPage`，工具栏据此展示。不能只凭扩展名让所有格式显示相同功能。

## 4. 格式路线与支持层级

| 格式 | 推荐实现 | 交付内容与边界 |
| --- | --- | --- |
| JPG / PNG / WebP 等 | 现有 PhotoScreen | 保持图片查看编辑；超大图后续按真实需求增加分块解码 |
| PDF | Android PdfRenderer + 原生页面 View | 分页、跳页、缩放、裁边、页书签、进度；Android 11 基础版无全文搜索和文本选择 |
| TXT / 日志 / 代码 / JSON / XML | 分块读取 + StaticLayout | 字号行距、分页、编码选择、全文搜索、位置恢复；首版代码不做全文件语法高亮 |
| Markdown | commonmark-java → 原生文本块或受控 HTML | 标题、列表、代码块、链接、图片；复杂表格可横向查看，不依赖当前 marked |
| EPUB，无 DRM | ZipFile + XML 解析 + 每章受控 HTML | 目录、字号行距、章节分页、书签、全书搜索；固定版式 EPUB 单独标识支持范围 |
| DOCX | docx-preview + JSZip 的固定兼容资源 | 原版式预览；另提供正文阅读模式，面向小屏重排；复杂域、浮动对象和字体不保证等同 Word |
| XLSX / XLS / ODS | 固定版本 SheetJS CE + 原生表格窗口 | 工作表切换、单元格范围、内容搜索、行列定位；显示已有缓存值，不承诺重新计算公式或完整图表 |
| CSV | 原生流式解析 | 正确处理引号、换行和编码，不只用字符串 split；大表按窗口显示 |
| PPTX | 固定版本 PPTXjs，按页挂载 | 单页查看、跳页、文字搜索；不能绘制的图形提供占位说明，可另选文字提纲 |
| ZIP | JDK ZipFile 虚拟目录 | 浏览、打开单个条目；不为预览解压整个归档 |
| ODT / RTF / 旧 DOC | 后续引擎或参考 Gander 解析逻辑 | 单独的兼容阶段，不把“提取出文字”算作原版式支持 |
| 音视频 / STL | 后续选配 | 音视频优先外部播放器；需要内置时再引入 Media3；STL 低优先级 |
| 旧 PPT / DRM / 不支持的加密文件 | 格式说明 + 其他应用打开 | 不伪装成已支持；PDF 密码等能力按 Android 11 实际接口定义 |

完整目标是分期交付各格式；首个可用版本重点覆盖 PDF、文本、Markdown、EPUB、图片和 ZIP。Office 进入第二批，避免其兼容性研究阻塞基础阅读链路。

## 5. WebView 83 的处理

不以替换系统 WebView 为前置条件，不捆绑第二套浏览器内核。`dumpsys` 中的 minimum target/version code 是设备提供者验证条件，不能证明任意新 APK 都可被选为系统 WebView。

1. PDF 直接原生渲染，不引入旧 PDF.js 来追求搜索。API 30 的 PdfRenderer 没有新系统的文本提取能力；如果以后要求 PDF 搜索/选字，另行评估包含文本 API 的 PDF 引擎，测量包体、ABI、内存及许可后再决策。扫描件还需 OCR，属于独立范围。
2. Markdown 在 JVM 解析，避免通过全局 polyfill 勉强维持最新版 marked。
3. DOCX 的 Chromium 80 下限仅说明当前资源具备候选条件。JSZip、包装脚本、CSS、图片、字体和搜索仍须在真实 WebView 83 测试后才能标为支持。
4. EPUB 每章装载。先用 Chromium 83 已有能力排版，不依赖 CSS Custom Highlight；分页和滚动位置受字体与图片加载影响，须等待布局稳定后记录位置。
5. Office HTML 渲染库固定版本、来源、摘要及许可。构建输出目标为 Chrome 83；转译解决不了缺少的 DOM API，必须运行样本测试。升级资源时重跑兼容矩阵。
6. 全文搜索由内容索引完成：文本按块、EPUB 按章、表格按工作表。结果跳转后用受控 DOM 标记或原生高亮；WebView.findAllAsync 只作为当前已加载内容的备选，不能等价为全文搜索。
7. 运行时同时记录 WebView 包版本和必要能力探测结果，未知能力走保守路线。设置页展示实际能力，不只显示“支持 Android 11”。

## 6. 离线 WebView 与资源入口

沿用 WebViewAssetLoader 思路，以本地 HTTPS 域名提供固定资源和当前文档资源。阅读 WebView 禁止任意网络请求、文件协议和任意外部导航；AssetLoader 未命中也返回阻断结果，不能回落公网。CSP 限制脚本和资源来源。

Office 渲染脚本属于应用可信资源；文档里的脚本、宏、外部链接资源和事件处理属性不能执行。EPUB 和 HTML 内容进入页面前按允许列表处理，图片按需要解码。不要直接透传不可信 HTML。

宿主通道限于 ready、locator、搜索结果和翻页等固定消息，校验来源、会话及长度；不提供任意文件读取或执行能力。离线阅读场景禁用文件选择、下载和新窗口。由用户点击链接后，才交给外部浏览器。

同时只保留一个文档 WebView，退出销毁；缩略图不创建额外隐藏 WebView。处理 onRenderProcessGone 后结束当前会话并提供重新打开入口，桌面返回仍可用。

## 7. PDF 隔离与来源处理

PdfRenderer 放入非导出、isolatedProcess 的绑定服务。宿主以只读 ParcelFileDescriptor 传文件；服务不自行解析路径或访问桌面私有文件。服务通过共享内存或文件描述符返回页像素，不经 Binder 传整张 Bitmap/ByteArray。

不能 seek 的 URI 流或 ZIP PDF 条目，按需落入有配额的临时文件，再交描述符。这样比一律复制文件更节省空间，也比宣称所有归档条目都能零落盘更符合 PDF 随机访问要求。

服务串行操作文档，关闭 Page 后再开下一页；请求带 sessionId 和 generation，翻页后丢弃过期结果。退出关闭 Page、Renderer、FD 和绑定。渲染服务异常时显示当前文档失败，不能导致 Launcher 主进程退出。

## 8. 阅读位置与跨入口一致性

进度独立于“最近修改文件”和 KOReader 统计。新增阅读记录库，初期使用平台 SQLite，不为少量表增加 Room 编译链。

位置采用格式专属 Locator：PDF 是页码和页内视口；文本是编码、稳定字节锚点及附近文本；EPUB 是 spine 项、元素/文本锚点和章内比例；表格是 sheet/row/column；PPTX 是 slide。字号改变后用锚点重排，不保存易失效的屏幕页码作为唯一位置。

身份先以规范来源及版本信息定位，必要时异步计算内容摘要合并跨入口记录。路径、URI 字符串和同名文件不能天然视为同一本书；大文件也不能在首屏前先完整计算哈希。

翻页后合并写入，暂停/退出立即保存；进程重启可恢复。文件修改时校验原定位，失效则回到最近可解析锚点。记录删除与源文件删除是两种操作。

## 9. 墨水屏与资源预算

以下是首轮工程预算，必须实测修订，不是性能承诺。

- 默认无翻页动画，左右区域点击或滑动一屏翻页；音量键沿用 VolumeKeySink。缩放时只在手势结束后生成清晰页面，限制连续高开销渲染。
- 默认白底黑字；PDF/图片可切灰度或量化，正文保持文字边缘，避免对所有内容强制抖动。
- 新增阅读刷新计数策略，例如每 6 次翻页申请一次全刷，可设为 0 / 6 / 12；与现有定时刷新和手动刷新共用 RefreshScheduler，避免重复申请。
- UI 重绘不等同于面板波形刷新。必须验证当前 RefreshRequester 对目标设备实际做了什么；如无厂商接口，只描述可实现的重绘行为，不承诺指定 EPD 波形或残影完全消除。
- 页位图 LRU 初始 12MiB，仅缓存当前页与相邻页，并受字节预算约束；缩略图继续使用现有 8MiB 内存 / 32MiB 磁盘缓存，避免按引擎重复创建。
- 480×800 ARGB_8888 一张约 1.46MiB；824×1648 约 5.18MiB。大屏不保证三页同时常驻；放大只渲染可见范围，禁止整份 PDF 转图。
- 文本按 256KiB 左右块读取，处理跨块编码和搜索边界；稀疏索引增量落盘。EPUB 同时仅保留当前章与有限邻章数据。
- 首轮 Office 预览阈值为压缩文件 8MiB、解压相关内容 32MiB，另限制节点、图片像素和表格单元格。阈值外仍可尝试有预算的正文/表格读取或外部打开；不能把 ZIP 压缩大小当作内存占用。
- 表格首屏仅挂载例如 100 行×20 列窗口；DOM 窗口化不能减少 SheetJS 整工作簿解析成本，超预算工作簿应在解析前作容器检查并结束尝试。
- 文档临时磁盘配额初始 128MiB，按真实输出字节限制 ZIP 条目展开；归档路径不得逃出临时目录，预览不递归自动打开嵌套归档。
- PDF 渲染队列一个、解析会话一个，缩略图低优先级；离开阅读页取消任务并释放大对象，不启用 largeHeap 来替代预算。

## 10. 依赖选择

| 用途 | 选择 | 决策理由 |
| --- | --- | --- |
| PDF | 系统 PdfRenderer | 不引入额外 PDF 原生库，适配 API 30 |
| ZIP / EPUB 容器 | JDK ZipFile、平台 XMLPullParser | 基础读取无新增大依赖；密码 ZIP 后续按需求评估 zip4j |
| 文本排版 | StaticLayout / TextPaint | 原生分页，复用现有 View 体系 |
| Markdown | commonmark-java 核心及必要扩展 | 避开 JS 新 API；选兼容当前工具链的固定版本 |
| WebView 本地宿主 | androidx.webkit 的兼容固定版本 | AssetLoader；AndroidX 库升级不会升级系统 Chromium |
| DOCX | docx-preview + JSZip | 只随阅读器资源装载，真机验收后冻结 |
| 表格 | SheetJS CE | 解析常见工作簿；原生窗口展示，不生成整表 DOM |
| PPTX | PPTXjs 及必要资源 | 后置接入，核对其传递脚本、CSS、字体和图表依赖 |
| 进度库 | 平台 SQLite + 现有协程 | 不增加新的数据库框架 |
| 图片 | 现有 core:render | 避免双缓存和双解码栈 |

不直接照搬 Gander 的构建 SDK、Activity 结构和全部依赖；保留十雨现有 JDK/AGP/minSdk 基线。具体依赖版本在兼容性尖峰阶段确定，未经 WebView 83 样本测试不写成“已兼容”。新增 JS 资源按目录打包并记录压缩后包体增量，R8 不会自动删减不用的脚本。

若复制 Gander 源码，保留 MIT 版权声明；其捆绑库按各自许可登记到 ThirdPartyLicenses。当前 README 的“未复制上游源码”表述也需随实际采用情况修正。包体目标单独验收，不把 Gander 的整包大小作为十雨整包承诺。

## 11. 实施任务与验收

按单人有效工作日粗估 23–36 日，包含首轮测试修复；复杂 Office 保真可能继续增加。每一阶段均可单独交付，不同时改造微信读书链路。

| 阶段 | 工作与接入文件 | 完成条件 | 估算 |
| --- | --- | --- | --- |
| R0 兼容尖峰 | 建立 WebView83 测试页、PDF 渲染样例、Office/EPUB 样本集，记录设备 ABI、memoryClass、可用空间和 WebView 信息 | 固定候选库版本，明确格式能力表与包体基线 | 2–3日 |
| R1 统一打开 | common 契约；settings.gradle.kts、app 依赖与模块注册；BrowseScreen、FilesModule；来源解析与读取授权；外部 DocumentEntryActivity | 文件页非图片不再提示待实现；本地路径和外部 URI 打开同一会话；返回列表保持位置 | 2–3日 |
| R2 PDF与文本 | 原生 PDF 服务、文本索引/分页/编码、ReaderChrome、音量键、进度与书签 | 飞行模式读 PDF/TXT；退出和进程重启恢复；坏文档失败后桌面可用 | 4–6日 |
| R3 EPUB/Markdown/ZIP | 章节资源路由、目录、Markdown 解析、归档虚拟目录、跨章搜索 | 中文 EPUB 图文可读；字号切换位置合理；ZIP 内 TXT/PDF 可读；资源与临时文件有预算 | 4–6日 |
| R4 Office | DOCX 原版式与正文模式、表格窗口、PPTX 单页及提纲、文本索引 | 三类真实样本各完成打开/定位/恢复；超限和缺失元素有明确提示；不执行宏 | 6–10日 |
| R5 集成与稳定 | 书架与最近阅读共用打开契约、设置项、缓存清理、许可、低内存和多尺寸复核 | release 构建与工程门禁通过；目标机持续阅读和 Launcher 往返无异常 | 3–5日 |
| R6 扩展格式 | ODT/RTF/旧DOC、密码ZIP、可选音视频/STL、增强PDF能力 | 每项另有样本矩阵、预算与验收，不计入上述工期 | 另估 |

R1 需要同步：`app/proguard-rules.pro` 保留新增反射入口；Manifest 注册文档 Activity、隔离 PDF Service；外部 Intent 精确声明实际支持的 MIME；内部目的地不携带大内容或依赖内存中的临时索引。

R4 之前，Office 菜单可明确提供“其他应用打开”，不能将此算作内置阅读完成。全部支持旧格式与 PDF 文本搜索的时间需在 R0 后另行排期。

## 12. 测试与设备指标

核心纯 JVM 测试：类型识别、错误 MIME、容器识别、Locator 编解码、编码跨块边界、搜索跨块/跨章、文本重排、ZIP 预算与路径、进度迁移。PDF/位图/WebView/授权和输入交互用目标机验证，不能用返回默认 Android 方法的 JVM 测试替代。

样本覆盖：中文 UTF-8/GB18030、50MiB 文本；纯文字和扫描 PDF（包括 100MiB 样本）、超大页面与损坏文档；图文章节 EPUB；带表格图片 DOCX；多工作表及大表格；含不支持图形 PPTX；中文 ZIP 名称、超限条目、不可 seek URI 和失效授权。

首轮目标：普通文本/PDF 首屏 P95≤2秒、缓存翻页 P95≤200ms，均从输入到提交显示内容计时，另测墨水屏物理刷新耗时；Office 小样本首屏目标≤5秒。目标不达标时按解析、布局、位图、面板分别定位。

通过 `dumpsys meminfo` 分别记录桌面基线、宿主、WebView renderer、隔离 PDF 服务的 PSS，以及总增量；首轮持续阅读增量目标≤120MiB，Office 瞬时目标≤180MiB，依据 R0 实机 memoryClass 调整。不能只看主进程 Java heap。

连续翻页200次、开关文档30次后，检查退出后的对象/进程回收趋势和磁盘配额；记录异常与残影截图。首页返回、屏幕关闭恢复、方向/尺寸变化、字体变化、权限撤销均应验证。

代码实施后执行工程约定的 debug/release 构建、testDebugUnitTest、ktlintCheck、detekt 和 check；本次只新增设计文档，未执行构建或宣称真机通过。

## 13. 源码阅读顺序

先读十雨的 `BrowseScreen → FilesModule → core:common 路由契约 → PhotoScreen/ViewerDecoder → RefreshScheduler → FileLibrary/BookshelfScreen`，确定可复用边界。

再按 Gander 的 `FileKind → WebViewFloor → ViewerActivity 的来源/资源/生命周期处理 → PDF服务及 SimplePdf → 各格式 assets → ArchiveBrowser/ArchiveProvider → 进度与缩略图实现 → docs/VENDORED.md` 阅读。目的是提取职责和接口，再接入十雨的模块体系；具体路径以实施时固定的上游 commit 为准。

推荐第一个里程碑：文件页直接打开 PDF/TXT，支持无动画翻页、字号（文本）、跳页（PDF）、书签和阅读恢复；同时保持图片与现有桌面功能可用。

## 14. 执行任务入口（2026-10-22）

已拆分为 [21张TASK及依赖看板](../../task/README.md)，契约草案见 [CONTRACTS](../../task/CONTRACTS.md)。卡为规划状态，尚未实施；当前协作与交付按 [35开发流程](35-development-workflow.md) 执行。018–020为后置扩展，不属于首轮完成前提。
