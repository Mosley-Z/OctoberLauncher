# TASK-RD-017 完整集成、审查与交付验收

- 状态：DONE（1.0.31测试签名双机验收与恢复完成，无正式发布）
- 优先级/阶段：P0 / R5
- 执行者/集成者：Codex；S4/P4由各测试子智能体独占
- 分支/工作树/源码基线：master/2da644b + 后续验收文档；保留无关修改
- 依赖：[011](TASK-RD-011-docx.md)、[012](TASK-RD-012-sheets.md)、[013](TASK-RD-013-slides.md)、[014](TASK-RD-014-search.md)、[015](TASK-RD-015-integration.md)、[016](TASK-RD-016-resources.md)、[FIX001](TASK-FIX-001-reader-review.md)、[FIX002](TASK-FIX-002-single-bookshelf.md)、[FIX003](TASK-FIX-003-special-icons.md)、[FIX004](TASK-FIX-004-shelf-controls-refresh.md)（最新书架行为交付）、[FIX006](TASK-FIX-006-shared-shelf-refresh.md)（覆盖独立自动模式）
- READY条件：依赖交付经集成者确认，接口与文件所有权完成交接，收到本卡执行指令
- 设计依据：[34总方案](../docs/design/34-files-reader-upgrade-plan.md)、[协作流程](../docs/design/35-development-workflow.md)、[契约草案](CONTRACTS.md)
- 允许修改：集成者共享注册/设置/许可/README/版本文件；本轮发布范围由实际授权决定；验收报告及证据。
- 共享入口：除本卡明确授予集成者的部分，其余只提接入差异；ReaderRegistry、ReaderScreen、模块Gradle和公共fixture由集成者串行接线
- 范围边界：不改无关微读、锁屏、图标和书架重构；不自动升版本、提交或发布

## 目标

将基础阅读与Office整合为可交付版本，证据覆盖最终源码和APK。

## 2026-10-23 用户授权最终收口（当前批次）

- 范围：执行剩余1–4（第三方URI同源、真实授权、性能/刷新、完整回归），第5仅任务卡最终验收关闭；小版本迭代，沿现有测试签名，不正式签名/发布；RD018–020搁置。
- 负责人/边界：根唯一集成/Gradle，uri_identity仅SourceResolver/新身份规则及测试；p4_remaining独占P4L202610130006，s4_performance独占SA4B03CN2608081096X。不覆盖UX001/网站/Wrapper/工具链等既有差异。
- 入口：文件/最近阅读/外部VIEW/SEND和授权失败后的重新选择文档，继续统一宿主，无新业务入口。
- 控件：阅读设置原位置；仅技术计时开关供实测，不新增产品菜单。物理EPD需实际接口/观测证据。
- 显示：能证实同一底层文件才归并路径/URI；副本及移动新路径独立，不按同名或相同内容猜测。
- 字段/消费者：metadata.id/aliases供ReadingRepository进度与书签；动作→正文绘制提交计时不含URI/正文，不等同面板响应。
- 迁移：旧SQLite/Locator不重建，合并保留较新位置与全部书签；覆盖安装保留现有数据。设备按字段恢复。
- 验收：同源/副本/移动、persistable授予/撤销/重选，TXT/PDF/Office P95、帧绘制与物理EPD分列、双机状态/复杂样本；证据不足不伪记关闭。
- 基线：master/84a035e，1.0.28/48；开工git状态另有.gitignore/docs设计索引/STATUS/看板/gradle.properties修改，均保留。
- 性能整改：802b包TXT/PDF每次冷启成功计时均sample=2；Main入栈与Activity.onResume重复调用同一ReaderScreen.onResume，首个open被第二次取消。给已可见会话的onResume加幂等保护，保留后台返回重新打开与generation取消语义；新包对比冷启sample=1、全链路P95、重开/后台恢复。
- 全链路整改：215b去重后TXT/PDF完整系统入口P95仍2.289/2.630秒。Main在前台文档前先恢复Home并调用applyData/组件读取/目录刷新；已知立即进入文档时只跳过不可见Home的首次onResume，仍保留Home栈项，退出阅读照常首次恢复Home。授权不合格、入口失败或未完成引导仍按原流程，入口失败补恢复Home。验证冷启动P95及返回HOME/负一屏/后台/重复VIEW，禁止为指标绕开锁屏或PDF隔离。

## 实施步骤与交付

### 2026-10-23 复杂PPTX实际故障整改

官方master-slides样本预算内读取失败：页索引全树扫描同名sldId，误收presentation扩展p14:sldId（无r:id）。最终入口/控件/显示归属保持既有阅读壳；只将SlideIndex消费者限制正式直属sldIdLst子项，不改Locator/schema/书签。根串行版本1.0.30/50沿测试签名；slides专属回归、整包门禁及两机同样本/必要PPTX切换验收。TXT/CSV/DOCX已完成证据保留4e98真实APK归属，不为未受影响引擎重跑全批。

1. 串行注册Office与搜索，核对固定资源摘要/版本/许可，更新ThirdPartyLicenses和实际支持矩阵。
2. 审查所有卡交付，完成check及debug/release构建，必要格式化与检查分开；保留最终APK摘要和源码状态。
3. 同一APK在S4/P4执行目标矩阵，补齐Office性能、外部URI、异常、锁屏恢复、字体变化和现有桌面回归。
4. 按授权决定是否升版本/签名/快照/发布；提交、合并、推送、装机、发布分别记录；回填卡/STATUS及经验。

交付包含实际改动、注册/调用接入点、接口变化、失败语义、必要测试和一份验收报告。代码中的签名以001冻结的实际契约为准，草案名称允许经记录后调整。

## 验收标准

- [x] 所有必要测试/设备项目有证据且明确未覆盖；新模块release反射正常，用户测试状态已恢复。
- [x] APK与源码一致；当前格式支持与文案一致；发布未授权时仅交付可审查产物，不擅自上线。

- [x] 自测命令/退出码和证据已记录；相关模块检查通过，最终整包与设备门禁由017统筹。
- [x] 未验证项、原有失败、外部阻塞及下一步清晰；没有把代码完成写成设备验收完成。

## 执行与接力记录

- 开始/最近更新：2026-10-23，Office/RD014最终同包集成验证
- 已改文件/接口决定：7d8a680集成Office/统一搜索/CSV迁移与实际MIME；2da644b仅Entry任务affinity修复，详见集成交付
- 验证命令及结果/证据：最终check/debug/release退出0，reader debug/release各132例0失败；office-next/entry-affinity-confirm-gate.log，原477门禁final-gate-4.log单列
- 未覆盖/阻塞/解除条件：477双机核心及各200有效翻页、fbd4双机各30真实隐式开关与必要smoke已过；真实SAF/物理EPD/P95及完整桌面/复杂Office矩阵仍待
- 构建进程/设备占用/恢复：根唯一构建已退出；两机全刷6/原生尺寸/HOME已恢复并释放，P4新增probe卸载，保持fbd4安装
- 下一步：本轮交付见集成及S4/P4验收；后续按未覆盖矩阵与外部条件推进，不重复已有效验证，不自动发布
- 接收方/集成点：Codex根集成者
- 提交/合并/发布：0a1534c、7d8a680与2da644b及验收4bccccc已提交，并按用户2026-10-23明确指令推送GitHub origin/master；未升版/发布

## 2026-10-22 审查整改关联（最新排期）

最终验收显式纳入三张 FIX：忙碌重排、最近阅读独立；旧配置迁移、唯一负一屏书架与旧路由兼容；设置/文件换图、恢复默认及主题持久化。以最终同一 APK 在 S4/P4 覆盖，Office 未集成不能据此关闭 017。原“不改图标和书架重构”仍限制本卡重新实施功能，但允许对 FIX 交付做必要集成回归，问题回原卡修。各旧验收仅作历史参考。

## FIX004 最新关联

书架行为以[FIX004](TASK-FIX-004-shelf-controls-refresh.md)及其验收为准：标题同行、默认手动快照、前台自动到期。资源审计不得恢复进入/切源自动扫描；最终设备门禁补P4、长间隔及断网矩阵，保留最近阅读正常写入。

## FIX006最新关联（覆盖旧FIX004计时要求）

使用[FIX006](TASK-FIX-006-shared-shelf-refresh.md)共享微信读书缓存/book刷新/dataVersion通知；不要恢复独立微信读书快照与计时器。RD016审计共享读写/并发/前后台资源；RD017补P4和真实网络失败，旧自动长间隔矩阵已取消。本地手动目录快照保留。

## 2026-10-23 P4基线补验（分派子任务，非整卡完成）

- 执行者/文件范围：P4测试子智能体，独占serial `P4L202610130006`；仅本节、[验收记录](../docs/acceptance/2026-10-23-reader-p4-baseline.md)、`_analysis/verify/p4-baseline/`、`_ui-out/p4-baseline-*`。源码/共享文档/Gradle/S4未操作。
- 基线：master工作树中稳定提交`0a1534c`的release副本，摘要`c2d9efcd09348c51663a2068a7b78ac1031130d18bc79681fd9951927a6d55ab`，1.0.28/48；并行源码开发未纳入该APK。
- 六行回读：①自身content URI外部入口保留；②阅读壳常显三按钮、设置菜单操作；③TXT/PDF/Markdown/EPUB/ZIP/DOCX各自合成样本；④仅刷新间隔单字段变更，消费者为阅读器Refresh；⑤原6→0→12→重启保留12→还原6，合成进度/书签保留；⑥用真实552×768 P4点击、状态、强停恢复、损坏PDF返回HOME与进程PSS作为证据，未测项单列。
- 验证：覆盖安装保留数据；TXT翻页/书签/强停恢复，PDF翻页/重开，Markdown/EPUB翻页，ZIP目录内TXT，DOCX正文/原版式及模式锚点强停恢复，全刷0/12可选、12重启持久化与6恢复，损坏PDF失败后HOME；详见验收矩阵。
- 资源：DOCX主+绑定WebView renderer PSS123,357 KiB；PDF主+isolated worker100,094 KiB，均为小合成样本单次快照，不代表峰值或压力通过。
- 恢复/释放：原刷新6已CHECKED回读，原物理552×768未变，未改网络/锁屏/字体；最终基线release、HOME，P4已释放给集成者。新建独立合成Download目录和阅读记录保留供新包复验。
- 未覆盖/下一步：新Office/统一搜索最终包、复杂样本、全格式状态矩阵、SAF/网络/锁屏、物理EPD与长压均待最终同一APK双机验收；017不记DONE。无后台脚本/Gradle，无提交/升版/发布。


## 2026-10-23 Office/RD014集成六行回读

- 入口：现有文件/最近阅读/外部URI统一Reader，补真实支持的Office VIEW/SEND精确MIME；图片保持原流程。
- 控件：阅读设置接工作表/行列、幻灯片目录/提纲、全文搜索与上下个；常显返回/前后页和刷新单选延续。
- 显示：仅当前表格窗口或当前幻灯片资源入视图；复杂对象提示，超限/无权限明确失败且可返回。
- 字段/消费者：Sheet与Slide Locator、ReaderTable/OfficeWebMode、内部SearchProvider；旧SQLite不重建，搜索词/结果不持久化。
- 迁移/保留：同源身份别名与既有进度书签延续；复制/移动保持独立，取消/退出沿唯一动作队列；界面成功后落盘。
- 验收：全仓门禁/debug/release、同最终APK双机计数/高亮/取消/入口与200/30压力；物理刷新/真实SAF等未覆盖独立保留。
- 自动结果与APK详见[集成交付](../docs/acceptance/2026-10-23-reader-office-search-integration.md)，根独占Gradle；本批未推送/升版/发布。

## S4重复外部入口整改（2026-10-23）

- 最终入口：继续DocumentEntryActivity接真实VIEW/SEND并转发Main；修复同URI携NEW_TASK重复打开只回HOME。
- 控件位置：不增加控件，保留系统“打开/分享”及既有阅读工具栏。
- 显示归属：入口只作转交，正文由Main的Reader宿主显示；关闭后HOME，再打开恢复原位置。
- 字段与消费者：仅入口Activity任务affinity；避免临时Entry成为长期Main任务baseIntent，不改Locator/数据库/搜索状态。
- 迁移：覆盖安装保留数据；旧任务先安全结束后重开验证，新Entry任务在finish后结束，Main保留唯一宿主。
- 验收触发：同URI/真实MIME/读授权的隐式NEW_TASK重复打开、HOME闭合和位置恢复，跨格式/真实SEND；最终重新门禁。477包200翻页为未改引擎的历史包压力，修复包路由/30开关单独摘要，不冒充同包。
- 反例：search-014/s4/repeated-entry-task.txt中baseIntent为Entry但Activities仅Main；重复start无forward/MainIncoming日志。根串行修改Manifest并构建，设备测试者继续独占。

## 2026-10-23 最终集成验收关闭

用户授权剩余1–4及第5任务卡关闭，最终1.0.31/51，源码1ca51be；gate7 check/debug/release成功1m22s/1564tasks，reader各137/data各128零失败，Android Debug v2签名验证通过。154源码摘要零差异，APK2172584B/SHA29784637149498aa00814b4dcf2af60bc4f09b3a1da5986eb58eeb0427b40005，两机实际base.apk相同。无正式签名/Release，RD018–020明确DEFERRED。

第三方身份/真实OS持久授权/撤销重选、版本变化保留书签、复杂官方Office实际输入、TXT/CSV/DOCX代表200翻页/30开关、全链路P95/实际缓存/PSS、两机原片光学、前后台/屏灭/权限网络/图标主题及故障恢复完成证据收口。PPTX同名扩展节点误索引、坏图覆盖旧图两处真实故障已修并有设备前后对照。各APK实测归属明确，历史包不冒充最终包。

已知性能限值：S4 PDF完整冷首屏P952.173s未达2s；PPTX6.335s未达5s，分段定位及已实施冷启修复保留。有限Office非完整像素保真；厂商DocumentsUI不存在故未测其界面，P4桌面BOOK双向未单测、PIN锁未操作，S4本次锁屏未覆盖、实体/模拟宽视口未改变Main布局，明确未计尺寸通过，保留历史S4及P4实体证据。不把卡DONE写成所有目标或所有组合达标。

两机恢复全刷6、原生480×800/552×768、auto1/user0、原网络与HOME；ReaderPerf回空。P4新probe已卸、临时图/主题已清，原图/主题保留；S4原probe保留，仅清已知本轮合成文件。设备均释放，无活跃UI/ADB/Gradle。

权威：[收口报告](../docs/acceptance/2026-10-23-reader-final-closure.md)、[S4](../docs/acceptance/2026-10-23-reader-final-s4.md)、[P4](../docs/acceptance/2026-10-23-reader-final-p4.md)、[光学](../docs/acceptance/2026-10-23-reader-optical.md)。全部RD000–017与FIX001–006按有效需求及证据关闭，局部未覆盖与性能限制继续明列；提交推送由根完成，不表示正式上线。
