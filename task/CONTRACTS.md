# 阅读器跨任务接口草案

状态：待 TASK-RD-001 核实并冻结，尚无对应实现。此处让各卡知道对接对象，不宣称下列类型已经存在。

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

- 冻结版本/日期：未冻结
- 实际类型路径/签名：待实现
- 字段与schema决定：待实现
- 注册、失败、释放样例：待实现
- 下游接收确认：未分配

冻结后如果变更：记录原因、兼容策略和受影响卡；集成者安排一次同步接线，不能让下游静默适配两套语义。
