# 阅读器跨任务接口（原生v1与后续草案）

状态：原生契约v1及Web/归档/最近阅读增量已落地；2026-10-22继续接入DOCX与资源收口，基于master/cfeae53和保留的工作区差异。下表保留总体设计，实际实现与未实现项以本节和源码为准。

- `core/common/ReaderContracts.kt`：DocumentRef、DocumentMetadata、ReaderLocator（Text/Pdf/Markdown/Epub/Archive/Image及新增Docx）、ReaderCapabilities、ReaderFormat、ReaderFailure、ReaderGateway、RecentGateway；来源/位置使用带类型标记的JSON。Docx保存preview/textOffset/fraction/page，旧类型名称不变，SQLite表结构不变，不需重建表；需要新列时才做schema迁移。
- `Destination.DocumentReader(ref)`、`AppServices.reader`接入路由；ReaderModule注册ScreenProvider，Gateway仅在主线程调用。feature不互相依赖。
- `feature/reader/source/DocumentHandle.kt`：metadata/openStream/openSeekable/close；调用者关闭流/PFD，handle关闭时删除自己拥有的临时文件。来源、解码、SQLite操作在IO线程；UI呈现在主线程。
- `DocumentMetadata.aliases`仅携带可确认的旧身份。本应用FileProvider的files_share URI与规范路径双向映射；ReaderIdentity.mergeAliases在事务中保留较新进度并迁移书签，归档条目传递父别名+编码/路径/CRC。其他Provider仍独立，未实现通用内容摘要关联。
- `feature/reader/ReaderEngine.kt`：page/turn/findNext/trimMemory/close，ReaderFrame支持文本、位图、归档与WebRequest。ReaderRegistry集中创建引擎；WebView主线程与IO引擎之间通过WebRequest/WebEvent传递定位。OpenPlan/EngineFactory/独立SearchProvider仍是草案名称，不视为已有API。
- `session/ReadingRepository.kt`：load/save、书签增删改查、最近阅读；ReaderScreen按generation丢弃过期结果，只记录已显示帧，关闭时等待取消并释放来源/引擎/数据库。内存压力经同一动作队列转给引擎，PDF仅逐出可重建缓存。
- 明确失败区分见ReaderFailure；取消原样传播，页面显示可恢复提示。未知二进制必须由用户显式选择编码；ArchiveEntry、原生PDF及Web产品通路已接通。content URI可通过阅读设置重选，持久授权以系统真实返回标志与权限列表为准。
- 最新验证分别见各格式验收与[资源第一批](../docs/acceptance/2026-10-22-reader-resource-first.md)；历史原生首轮门禁不替代当前全格式验证。表格/PPTX与统一搜索契约由后续卡增量冻结。

## 冻结步骤

001先核查实际路由与AppServices，结合000兼容结果给出最小Kotlin签名；在本文件填入契约版本、提交或源码差异、类型路径、调用线程、错误和关闭语义。下游以该版本实际源码接入；不得各自创建同名但不同含义的接口。

## 草案边界

| 对象 | 必要字段/操作 | 生产者→消费者 |
| --- | --- | --- |
| DocumentRef | LocalPath / ContentUri / ArchiveEntry；可持久化来源；不携带流/Bitmap/大内容 | 文件/书架/外部入口→002来源 |
| DocumentMetadata | 名称、MIME、长度可未知、修改时间可未知、稳定身份及版本线索 | 002→路由、记录与UI |
| DocumentHandle | metadata、openStream、需要时openSeekable、close；流由调用方关闭 | 002/008→各解析器 |
| ReaderGateway | open(ref)；统一类型判定、打开结果及不可支持/失效提示 | 各feature经common→reader |
| OpenPlan | 引擎ID、实际格式、能力、必要临时缓存方式、限制原因 | 路由→宿主 |
| ReaderCapabilities | search/select/reflow/pageJump/bookmark等实际能力；不能从扩展名直接推断 | 引擎→工具栏 |
| EngineFactory | supports(plan)、create(session)；独立注册适配器 | 引擎卡→集成者Registry |
| ReaderEngine | prepare、呈现View/内容、turn、locate、settingsChanged、dispose | 引擎→003宿主 |
| ReaderLocator | 格式标记+schema版本+格式位置；页码/文本锚点/spine/sheet/slide分别建模 | 引擎↔004记录及搜索 |
| ReadingRepository | 读取/保存Locator、书签CRUD、最近阅读查询、来源可访问性 | 004→宿主、书架与最近阅读 |
| SearchProvider | 增量查找、取消、结果Locator/片段/范围及错误；能覆盖未加载内容 | 各引擎→014搜索UI |
| SessionToken | sessionId+generation；关闭/切换/设置变化使过期结果失效 | 宿主→异步解析/服务/Web消息 |
| WebHost | 固定本地资源URL、允许消息、Locator/翻页事件、内容就绪及错误 | 007→EPUB/Office/Markdown |

## 生命周期、错误与线程

宿主创建会话→来源准备→后台解析/渲染→主线程呈现→Locator更新→记录保存→取消任务与关闭资源。PDF service串行渲染；WebView只在主线程调用；文件与数据库IO不在主线程。

openStream每次返回独立可关闭流；handle关闭后不再取得新资源。缓存由来源层标记归属/配额；引擎不得任意删除其他会话缓存。临时URI不能虚构持久授权；ArchiveEntry保持父归档身份和规范化条目路径。

错误至少区分：权限失效、源不存在、格式不支持、损坏、超预算、渲染器失败、用户取消。取消不提示成功/失败；错误结果保留可返回与其他应用打开路径。Locator失效回退必须能说明或恢复合理位置，不能静默覆盖原书签。

PDF在API30基础版的search/select能力为false；文本文档的全局搜索不依赖当前DOM；所有内容进度与KOReader/微信读书统计分离。

## 001执行时回填

- 冻结版本/日期：原生v1 / 2026-10-22；Office/Web/SearchProvider统一抽象待后续扩展
- 实际类型路径/签名：见开头落地清单及对应Kotlin源码
- 字段与schema决定：带类型标记JSON，SQLite v1；新增类型保留旧字段与标记
- 注册、失败、释放样例：ReaderModule / ReaderScreen / ReaderSession
- 下游接收确认：Codex串行接入002–005；其他任务保持待实施

冻结后如果变更：记录原因、兼容策略和受影响卡；集成者安排一次同步接线，不能让下游静默适配两套语义。

原生增量：ReaderEngine/ReaderSession增加suspend findNext(query)返回下一匹配帧或null；TextEngine声明search=true，IO扫描每64KiB检查取消，查询长度最多256；统一SearchProvider和结果高亮仍属后续014。


## 006原生PDF增量（2026-10-22）

- ReaderRegistry.create是实际分发点，当前TEXT/PDF；不要求尚未出现的OpenPlan/通用EngineFactory。
- ReaderEngine.page/turn变为suspend；ReaderLocator.Pdf新增默认zoom/centerX/centerY/crop，旧JSON仍能解码，SQLite表不变。ReaderCapabilities增加zoom，ReaderFailure增加PASSWORD。
- PDF每会话绑定一个隔离服务；每个请求独立IPdfResult回调即结果令牌，ReaderScreen generation验证发布，退出取消旧回调等待。Binder只传只读FD、固定视口SharedMemory和小型参数；原生序列操作不在主线程。
- Android隔离进程名包含追加的服务限定名，Application仅对此PDF前缀跳过桌面初始化。服务API27+启用，API26显式不支持。
- ReaderScreen暂停丢弃帧但保留Locator，清除返回栈大位图引用；临时来源配额128MiB。细节及验证边界见PDF验收。

## 2026-10-22 Web/Markdown 实际接入补充

ReaderRegistry现支持TEXT/PDF/MARKDOWN真实factory。ReaderEngine挂起page/turn，ReaderFrame.web只携带WebRequest，主线程OfflineWebHost完成可信ready后才发布并保存位置。ReflowEngine接受WebPosition字符锚点；Markdown Locator保持textOffset/fraction。单活动WebView在onPause销毁；URI授权范围不能由文件名推断。详细预算、实际测试和剩余项见reader-web-markdown验收。旧草案中的未实现说明以这里的实际接入为准。

## 2026-10-22 ZIP/EPUB 实际契约

- DocumentRef.ArchiveEntry新增charset默认UTF8，旧序列化保持可读；禁止自动递归ArchiveEntry来源。DocumentHandle新增openChannel()/archiveFile()；普通本地文件零复制，URI ZIP可按总预算复制压缩包。
- SourceCache共享128MiB预约，包含LazyEntryChannel尚未展开的保留额度；TXT按读取位置增量解压，PDF选中后落盘。流EOF核查输出大小/CRC，部分预览close不强制读完。
- ReaderLocator.Archive(directory,page,charset)、Image、Epub(spine,textOffset,fraction)已接SQLite。Image仅采样静态首帧；不替代原图库。
- EPUB一章一WebDocument，共用一个WebView；内部跨章链接必须命中当前WebDocument.internalLinks精确清单。CSS只接受EpubStyles生成的有限属性与作用域选择器；不信任原始CSS、导入、字体或网络URL。
- Registry实际支持TEXT/PDF/MARKDOWN/ZIP/EPUB/IMAGE；其他容器候选不代表Office已产品接入。系统ACTION_VIEW已补充EPUB/ZIP MIME；ACTION_SEND仍未实现。
