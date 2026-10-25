# TASK-UX-110 本记宿主覆盖展开

- 状态：READY_FOR_REVIEW
- 优先级/阶段：用户确认后的阅读桌面升级
- 执行者/集成者：note_overlay（GPT-6.1 Sol low）/ Codex；Home接线由immersive_agent负责
- 分支/工作树/源码基线：master / D:\DSHStation\OctoberLauncher / f025b18
- 依赖/READY条件：106；共享契约由UX100集成者串行修改，无并行构建或同serial操作
- 设计依据：[37具体方案](../docs/design/37-reading-desktop-proposal.md)、[设计稿](../design/reading-desktop/README.md)；本轮用户确认覆盖展开、普通组件轮换、沉浸固定本书，8搁置
- 允许修改/共享入口/禁止范围：core/widget-api请求契约、widgets本记动作、home覆盖绘制/触摸；共享字段/Manifest/注册归UX100；保留现有差异，不改阅读器引擎、不提交/升版/发布

## 目标与用户闭环

展开增加可见行，底边按末排名称下缘，无穿透；固定收起，长文滚动，旋转/返回/锁屏/刷新保持合理状态。

## 实施步骤与交付物

核对调用点→配置/迁移→真实消费者→预览/主题对应→适当单测→最终release设备验证；证据仅技术字段/合成数据。

## 验收标准

展开增加可见行，底边按末排名称下缘，无穿透；固定收起，长文滚动，旋转/返回/锁屏/刷新保持合理状态。 必须验证保存→退出重进→冷启；未完成设备项不写DONE。

## 执行与交接

- 已改文件/接口决定：WidgetApi增加临时阅读请求、沉浸固定本书与切换起点判定；新WidgetNoteOverlay协议仅传内存内容；KernelCardFiller导出prepared缓存、WereadBoardWidget桥接；新NoteOverlayView负责白底、固定三按钮、长文滚动与全触摸消费。HomeView接线归immersive_agent。
- 收起语义静态核对：桌面展开点击只调用host.openNoteOverlay，不再调用内核toggleNoteExpanded；所以内核始终保持原折叠态，close不需要回调恢复expanded。正常切形态的KernelCardFiller仍setNoteExpanded(false)。
- UX107协同：封面MOVE只累计位移，UP直切一次，取消无切换；移除settle逐帧动画。沉浸临时固定BOOK，恢复时读取原form，不写坏pool或形态。
- 六行冻结：入口为本记展开热区；覆盖顶沿同组件、下沿由Home末排名称下缘钳安全底边；正文属于Home临时层；source缓存→widget-api→Home消费，身份变化回顶；无持久迁移；长/短/空、旋转/返回/暂停、后景恢复由最终release设备验收触发。
- 实际命令及结果/证据：已读项目/状态/记忆/开发流程与当前源码；git status保留既有UX设计/网站/工具链差异。
- 未覆盖/阻塞/解除条件：本执行者未运行Gradle/ADB，格式与门禁归集成者；Home接线和当前release设备项待补。短笔记沿用内核CardRenderer已有溢出条件决定展开按钮（javap只读核实），需合成短长实机核实。
- 后台进程/设备占用/恢复情况：首批暂无Gradle/ADB租约；构建和设备由Codex单一操作，开始前重查。
- 下一步/接收方：按依赖进入本卡，不重复请求已授权业务实施确认。
- 提交/合并/发布：未执行。

## 需求与证据回读

1. 最终入口：按37方案相应功能入口；8无产品入口。
2. 控件位置：首页/其他页行列各一行；模式相关控件条件显示；具体卡以目标为准。
3. 显示归属：普通组件轮换、沉浸固定本书；本记覆盖层属于Home宿主。
4. 字段/消费者：共享配置冻结见UX106/UX100执行记录；Home、触摸、分页、预览与主题同源。
5. 迁移：旧字段缺失与显式值区分，普通布局/应用顺序保留，间距不再消费；Key不入主题。
6. 验收触发：上述闭环逐一对应单测与设备技术证据；计划不写成实现。


## 集成验证更新

- 完整check及debug/release构建通过（最终设备修复源码，26秒，1564任务）；受影响模块均0失败0跳过。
- 设备结论尚在执行，不由构建通过推导设备通过；见[统一验收](../docs/acceptance/2026-10-23-reading-desktop-implementation.md)。
- 未提交、升版或发布；本卡相关设备闭环由UX100收口。

## 最终集成与交接（2026-10-23）

- 本卡代码已接入最终debug/release，完整门禁通过；设备已测项和未覆盖项以[统一验收](../docs/acceptance/2026-10-23-reading-desktop-implementation.md)为准。
- 最新首启为“扫码本机→手机粘贴提交→墨水屏保存”，官方获取只文字引导；历史双QR不再交付。
- 两机测试设置逐字段恢复、原缓存恢复，最终release摘要核对一致；无存活Gradle/设备租约，无提交/升版/发布。
- 保留READY_FOR_REVIEW：真实手机同网实扫/真实Key在线验证及局部视觉手势矩阵未测，不以代码通过替代完整设备验收。
