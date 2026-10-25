# TASK-UX-101 首次引导与微信读书连接

- 状态：READY_FOR_REVIEW
- 优先级/阶段：用户确认后的阅读桌面升级
- 执行者/集成者：onboarding_orientation（app首启/方向） / Codex（统一构建和设备验收）
- 分支/工作树/源码基线：master / D:\DSHStation\OctoberLauncher / f025b18
- 依赖/READY条件：102；共享契约由UX100集成者串行修改，无并行构建或同serial操作
- 设计依据：[37具体方案](../docs/design/37-reading-desktop-proposal.md)、[设计稿](../design/reading-desktop/README.md)；本轮用户确认覆盖展开、普通组件轮换、沉浸固定本书，8搁置
- 允许修改/共享入口/禁止范围：app/OnboardingScreen、现有WereadKeyEntry契约/实现；共享字段/Manifest/注册归UX100；保留现有差异，不改阅读器引擎、不提交/升版/发布

## 目标与用户闭环

启用/跳过/无效Key/断网/中断恢复；验证后选普通/沉浸，旧completed用户不重跑。

## 实施步骤与交付物

核对调用点→配置/迁移→真实消费者→预览/主题对应→适当单测→最终release设备验证；证据仅技术字段/合成数据。

## 验收标准

启用/跳过/无效Key/断网/中断恢复；验证后选普通/沉浸，旧completed用户不重跑。 必须验证保存→退出重进→冷启；未完成设备项不写DONE。

## 执行与交接

- 已改文件/接口决定：OnboardingScreen、OnboardingConnection、MainActivity方向消费、OctoberRainApp最小装配；新增weread-app/qr/OnboardingKeyEntryAdapter复用既有server/encoder，KeyEntryPage仅加官方获取链接。step=0欢迎/1形态/2启用选择/3连接/4模式/5完成；wereadDecision与step落盘，旧completed分流保留。验证仅请求既有/readdata/detail月统计，不同步书库；无效Key与暂时无法验证分开，Key不写日志、二维码或URL。
- 实际命令及结果/证据：已读项目/状态/记忆/开发流程与当前源码；git status保留既有UX设计/网站/工具链差异。
- 未覆盖/阻塞/解除条件：未运行Gradle/格式化/ADB（按集成者独占约定）；真实启用/跳过/无效Key/断网/扫码/重启断点、90/270方向与release设备验证待统一门禁，未记设备通过。
- 后台进程/设备占用/恢复情况：首批暂无Gradle/ADB租约；构建和设备由Codex单一操作，开始前重查。
- 下一步/接收方：Codex统一app与weread-app格式化、构建和设备验收；方向设置UI由UX100集成，Activity仅消费配置不写系统旋转。
- 提交/合并/发布：未执行。

## 需求与证据回读

1. 最终入口：按37方案相应功能入口；8无产品入口。
2. 控件位置：首页/其他页行列各一行；模式相关控件条件显示；具体卡以目标为准。
3. 显示归属：普通组件轮换、沉浸固定本书；本记覆盖层属于Home宿主。
4. 字段/消费者：共享配置冻结见UX106/UX100执行记录；Home、触摸、分页、预览与主题同源。
5. 迁移：旧字段缺失与显式值区分，普通布局/应用顺序保留，间距不再消费；Key不入主题。
6. 验收触发：上述闭环逐一对应单测与设备技术证据；计划不写成实现。


## 本批需求回读（2026-10-23）

- 最终入口：首次形态之后询问微信读书，暂不启用直接普通完成；官方获取与本机录入分别标明。
- 控件位置：连接页官方浏览器/官方二维码、本机录入二维码、设备密码框；验证后普通/沉浸同级选择。
- 显示归属：首启宿主维护流程；扫码适配器归weread-app，app只装配调用，无feature横向依赖。
- 字段/消费者：onboarding.step/wereadDecision由首启读写；integrations.wereadApiKey/wereadEnabled沿用；desktop.mode由Home消费，desktop.orientation由MainActivity订阅为PORTRAIT/LANDSCAPE/REVERSE_LANDSCAPE请求。
- 迁移：既有completed不重跑；暂不启用不删除缓存或旧Key；网络失败保留待验证Key；返回/重启恢复非敏感进度。
- 验收触发：统一门禁后以首次选择、错误Key、断网重试、退出重进、冷启和正反横屏逐项补证，当前仅静态核对。

## 静态复核补正（2026-10-23）

- 暂停/返回使请求编号失效，后台验证与旧扫码回调不会导航；验证中断后保留Key供显式重试。正在扫码时暂停关闭端口，回前台自动生成新会话；主动关闭不自动重开。首次完成清栈前补调用onPause/onDestroy。
- 发现Application启动同步只在初始化触发，首启新Key需额外接入。现改为验证成功后调用wereadSync.refresh(book)，通过HostWereadSync→WereadCardRefreshService→WereadBookSync写同份weread缓存；完成后HostWereadSync.notifyDataChanged→ContentSourceImpl.invalidate/dataVersion→HomeScreen.refreshCurrentWidget。验证请求仍只取月统计，首轮数据加载在验证成功后进行。
- 旧completed分流源码仍由MainActivity.startInitialScreen直接进入Home；跳过分支写NORMAL且不清缓存。未引入app测试依赖：app当前没有src/test和testImplementation，生命周期行为须由统一设备验收覆盖；本次无Gradle/ADB。

## S4 debug首启专项（2026-10-23）

- 证据：`_analysis/verify/desktop-ux/s4-onboarding-report.md`，沿root debug摘要06fb163f；真实启用连接页、两种二维码显示/Back关闭、连接页强停断点、暂不启用直接完成、completed冷启不重跑已观测。最终普通竖屏clock并释放S4。
- 边界：二维码Dialog透底已发现并由root源码修正，修复后待复验；真实Key成功/无书成功、模式选择、首轮加载、无效/网络失败与验证中暂停、有效期扫码自动重建未测。本报告不作为整卡DONE依据。

## 最新需求纠正（2026-10-23，优先于前述双二维码方案）

- 用户明确只保留“手机扫描本机二维码→手机粘贴已复制API Key→传给墨水屏自动保存”。官方获取仅文字指引，不提供官方网页二维码或浏览器按钮。
- 本次已移除OnboardingScreen官方网页/二维码入口及OnboardingConnection.showOfficial；手机KeyEntryPage去官方链接，改文字引导与textarea粘贴提交。保留设备手输、自动保存后轻量验证，并明确“已保存”与“验证连接”状态。
- 当前仅静态修改；旧双二维码截图不作为最新UI验收，root统一构建后补本机HTTP合成提交保存验证。无Gradle/ADB，无真实Key。

## 最终集成与交接（2026-10-23）

- 本卡代码已接入最终debug/release，完整门禁通过；设备已测项和未覆盖项以[统一验收](../docs/acceptance/2026-10-23-reading-desktop-implementation.md)为准。
- 最新首启为“扫码本机→手机粘贴提交→墨水屏保存”，官方获取只文字引导；历史双QR不再交付。
- 两机测试设置逐字段恢复、原缓存恢复，最终release摘要核对一致；无存活Gradle/设备租约，无提交/升版/发布。
- 保留READY_FOR_REVIEW：真实手机同网实扫/真实Key在线验证及局部视觉手势矩阵未测，不以代码通过替代完整设备验收。
