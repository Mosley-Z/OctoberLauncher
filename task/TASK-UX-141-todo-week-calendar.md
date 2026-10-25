# TASK-UX-141 月历改名与共享待办周历

- 状态：DONE
- 优先级/阶段：P1 / 实现、工程门禁与P4验收完成；S4与竖屏逐条点验按UX139组织
- 执行者/集成者：代码执行者待分配 / Codex主代理
- 分支/工作树/源码基线：落卡master/f025b18及既有差异；执行前重核
- 依赖：无；共享契约/路由/注册由[UX139](TASK-UX-139-widget-upgrade-integration.md)冻结接入
- READY条件：用户已确认三行表头、固定表头+七列内容整体滚动；收到执行分派
- 设计依据：[方案40](../docs/design/40-widget-upgrade-plan.md)、[候选稿](../design/widgets-upgrade-proposal/widgets-design.png)
- 允许修改：feature/widgets月历显示名/新周历视图与纯日期排序规则；现有待办录入/通知适配按需明确文件归属；core:common/WereadTodoHost与core:weread-sync改动先交集成者
- 共享入口/禁止范围：不得复制第二份待办存储，不新增微信官方App云同步承诺，不修改原待办手工order语义；注册和配置主代理串行

## 目标与用户闭环

组件库原日历更名月历（calendar ID不变），新增待办周历。左年月、右新增；第二行星期箭头平移一天，第三行日期箭头切七天；七列仅投影有日期待办，共享原微信读书待办的增改删与勾选。细节以方案40为准。

## 实施步骤与交付物

1. 建立本地日期窗口规则：起始日、七天映射、±1/±7天、跨月年标题、今日标记；按日历日运算，不直接加固定86400秒。
2. 第二行右箭头起始日−1：一19…日25→日18…六24；左+1。第三行右+7/左−7，星期排列不变。补入新日期，不只是循环搬旧七列。
3. 复用WereadTodoHost与现有录入，新增默认今天可改日期/时间；共用保存后的失效/重读机制，核对原微信读书内核路径是否同样通知，避免单向更新。
4. 每天投影排序done→时间→原order：未完成先、有时间早先、无时间放该完成组末尾，同时间稳定。不将周历排序写回原手工排序。
5. 有时间checkbox+时间独立一行；无时间checkbox与首行同排，正文均悬挂缩进。勾选直接改共享完成状态，改后重排。
6. 三行表头固定，七列内容整体上下滚动，横竖均七列，文字多行；处理滚动与勾选/新增/箭头点击取消，保持点击目标对应原ID。

## 需求与证据回读

1. 入口：组件库月历/待办周历，新增复用已有编辑能力；原微信读书待办仍在原形态。
2. 控件：第一行左年月右+添加，第二行单字星期及两侧日移箭头，第三行单数字日期及两侧周移箭头。
3. 归属：仅可见7天且hasDate的待办；无日期留原清单；今日圈/星期加粗只在今日可见时出现。
4. 字段/消费者：id/content/dateSec/minuteOfDay/done/order同源，周历只增加视图起始星期，字段由UX139冻结；不影响原清单排序。
5. 迁移：calendar身份不变；无新待办迁移/复制。建议默认策略为保存起始星期、重进/重启回含今天窗口，临时日期与滚动不持久化；实现前与方案40对齐。
6. 验收：日/周箭头、跨月年、今日、双端CRUD/勾选、无日期过滤、排序与长内容滚动；退出重进/冷启核同源状态。

## 验收标准

- 同一条待办两处添加/改日期时间/删/勾选及反勾，另一处及时一致；移出可见日期后消失但不被删除。
- 未完成的晚时刻排在已完成的早时刻之前；无时间及同时间按既定规则；周历排序不打乱原手工清单。
- 用虚构数据验证跨月/跨年/闰日与时区日界、今天圈/星期粗体；时间值00:00不能误判无时间。
- 长中文、多条超高、空白日/全周无事项、滚动后勾选、快速切周均不出现串日期/误勾。
- 规则单测与实际组件交互分开，横竖在目标设备验证由UX139组织；用户待办正文不写证据/Git。

## 执行与交接

本轮Codex审查整改：修复重复可见通知导致日移窗口误复位、箭头小热区、UI同步写配置、时间越列/完成框缺勾；视图状态回主线程，策略改MINUTE以支持跨日。统一门禁通过，各968例0失败0错误1既有跳过；P4日移/周移、三尺寸12箭头及可见性探针通过，恢复测试字段并安装最终release。详见[审查验收](../docs/acceptance/2026-10-25-ux141-review.md)，以本轮受影响范围取代下述旧细节，S4和未测项保留。

- 已改文件/接口决定：新增 `feature/widgets/.../TodoWeekCalendar.kt`（起始星期归一、七天窗口、±1/±7、跨月年标题、本地日历日换算）、`TodoWeekItems.kt`（每日投影排序、HH:mm 解析）、`TodoWeekWidget.kt`（三行固定表头、七列整体滚动、勾选/编辑/新增/日移/周移命中）、`TodoWeekEditor.kt`（新增默认今天、可改日期与时间、删除）；`WidgetCatalog.kt` 将 calendar 标签改为「月历」并登记 `todo_week`（TIME、EVENT）；`core/common/.../Models.kt` 增加 `DesktopConfig.todoWeekStartDay`、`TODO_WEEK_START_DEFAULT`、`effectiveTodoWeekStartDay`；测试新增 `TodoWeekRulesTest.kt`，更新 `WidgetCatalogTest.kt`。编辑界面在 feature/widgets 内独立实现，数据全部经 `AppServices.wereadTodos`（feature 不横向依赖，避免改动已验收的微读对话框）；跨入口一致性依赖同一 `files/weread/kernel_prefs.json`、保存后清单重读与 `HomeScreen.onResume→refreshCurrentWidget(true)`。
- 验证命令及结果/证据：`:feature:widgets:testDebugUnitTest :feature:widgets:ktlintCheck :feature:widgets:detekt --offline` BUILD SUCCESSFUL；`gradle check :app:assembleDebug :app:assembleRelease --offline` BUILD SUCCESSFUL（1566 tasks，日志 gate-ux141.log），两变体各968例0失败0错误1既有跳过，debug 6985086字节、release 2220679字节（SHA256见验收记录）；`scripts/build.ps1 -Variant debug -Offline` 通过；设备证据在 `_analysis/verify/ux141/`：月历改名（02-picker）、首轮渲染（07-rotCcw）、添加默认今天并保存 09:30（18-saved）、周移跨月标题「2026年10–11月」与空周提示（21-weeknext2）、日移起始日−1 且星期整体前移（22-dayprev）、起始星期写入配置 7（config.after-day.xml）、重启与重进回到含今天窗口（23/25）、勾选后完成项置底并加删除线（29-toggled、kernel_prefs.after-toggle.json）、滚动后勾选命中正确条目（39/40、kernel_prefs.t5.json）、竖屏七列与表头（41-portrait）、外部改存储后重进反映（42-external）。设备原始待办与配置已由 `kernel_prefs.orig.json`、`config.orig.xml` 恢复（43-restored）。
- 未覆盖/阻塞/解除条件：竖屏下逐条点击与滚动仅验证渲染，未逐项点验；S4 未验证（UX139 组织横竖目标设备）；微信读书 App 页面与桌面同屏可见的实时同步未做，仓库无待办存储广播，当前依赖回到桌面触发重读；快速连点切周未做压力用例。
- 后台进程/设备占用/恢复情况：无后台构建；P4L202610130006 已恢复原始数据与配置，无占用。
- 下一步：等待UX139整批汇总；S4与竖屏逐条点验由UX139组织时补验。验收记录见[2026-10-25 UX141](../docs/acceptance/2026-10-25-ux141-todo-week-calendar.md)。
- 提交/合并/发布：未执行。


本轮S4回归及首页自动档追加已纳入[UX139最终集成验收](../docs/acceptance/2026-10-25-ux143-144-integration.md)。仅补已列S4范围，不把旧未测项全部改写为通过。
