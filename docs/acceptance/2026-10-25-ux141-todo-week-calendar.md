# 2026-10-25 UX141 月历改名与共享待办周历验收

主代理在master/f025b18及原差异上增量实现。关联[UX141](../../task/TASK-UX-141-todo-week-calendar.md)、[UX139共享契约](../../task/TASK-UX-139-widget-upgrade-integration.md)、[方案40](../design/40-widget-upgrade-plan.md)。未启动UX142–144。

## 实现与兼容

- 原`calendar`组件显示名改为「月历」，ID、分类、MINUTE刷新策略与已选状态不变，无配置迁移；新增`todo_week`（「待办周历」，时间分组、EVENT策略），注册与顺序清单仍由`WidgetCatalog`单一来源生成。
- 新增`feature/widgets/.../TodoWeekCalendar.kt`：起始星期归一（非法回周一）、按本地日历日求七天窗口、±1日/±7日、跨月与跨年标题（`2026年10–11月`、`2025年12月–2026年1月`）、`dateSec`到本地日期的换算，不按固定86400秒推进。
- 新增`TodoWeekItems.kt`：每日投影排序为未完成在前、同完成状态按时间升序、未设时间排在该组末尾、同时间保持原`order`与`id`稳定；`00:00`按有效时间处理；时间文本按`HH:mm`解析并拒绝越界值。
- 新增`TodoWeekWidget.kt`：三行表头固定，七列内容整体上下滚动；第一行年月与「＋ 添加待办」，第二行单字星期与两侧日移箭头（右箭头起始日−1、左+1），第三行日期与两侧周移箭头（右+7、左−7）；今日数字实心圆反白、对应星期加粗，窗口不含今天时无标记；有时间条目勾选框与时间独占一行、正文随后，无时间条目勾选框与首行同排，正文悬挂缩进；勾选直接改写共享完成状态并按新排序重排；点击命中按当前滚动位置裁剪，滚出视口的条目不再响应。
- 新增`TodoWeekEditor.kt`：新增默认今天且可改日期与时间，编辑复用同一表单，删除为二次确认；全部读写经`AppServices.wereadTodos`（`WereadTodoHost`），未复制存储，未改动原手工`order`语义。
- `core/common/.../Models.kt`增加`DesktopConfig.todoWeekStartDay`（ISO 1–7，缺省1）、`TODO_WEEK_START_DEFAULT`与`effectiveTodoWeekStartDay`；仅日移写入起始星期，周移不写；临时日期窗口与滚动位置不持久化，重进或重启回到含今天窗口。
- 编辑对话框在feature/widgets内独立实现：feature不横向依赖，未改动已验收的微读量测版式。跨入口一致性依赖同一`files/weread/kernel_prefs.json`、保存后清单重读与`HomeScreen.onResume→refreshCurrentWidget(true)`；仓库内没有待办存储广播。

## 工程与产物

项目JDK17、Android SDK、Gradle8.11.1离线执行。统一`check :app:assembleDebug :app:assembleRelease`退出0；两变体各968例、0失败0错误、1既有跳过（新增14例日期与排序规则、1例目录条目断言）。门禁日志在`_analysis/verify/ux141/gate-ux141.log`。

| 产物（1.0.31/51） | 字节数 | SHA256 |
| --- | --- | --- |
| debug | 6985086 | 8B82B041224E01FED73478FAEBBDA9EA969561E8E7F33A2C55D16729839754E4 |
| release | 2220679 | 62D9FC6ADCCE6E0CEA5AF7260099AA8B59A97D7ADFAE06438F04E5072E23DA6D |

## P4验收

P4为P4L202610130006，设备方向横屏布局，截图为帧缓冲旋转90°后的正常方向。测试数据为虚构待办与虚构桌面配置字段，正文未写入证据目录以外位置。

| 触发 | 实际与证据 | 边界 |
| --- | --- | --- |
| 组件清单 | 时间分组显示「月历」，「待办周历」已注册；picker截图02-picker | 自绘选择器不在accessibility树内，改用写入`widgetCycleIds`切换 |
| 首屏与今日标记 | 「2026年10月」「＋ 添加待办」、星期一至日、19–25，25实心圆反白且「日」加粗；07-rotCcw | 固定虚构数据，不改设备时间 |
| 新增共享待办 | 默认日期10月25日，设时间09:30后保存，第25列按00:00→09:30排序出现；18-saved | 输入法全屏编辑，正文为ASCII测试串 |
| 日移箭头 | 右箭头后起始日−1、星期整体前移为「日一二三四五六」，配置`todoWeekStartDay=7`；22-dayprev、config.after-day.xml | 点击坐标为正常方向像素 |
| 周移与跨月标题 | 右箭头+7得到26–1，标题「2026年10–11月」，空周显示「这七天没有待办」；左箭头回到18–24；21-weeknext2、27-weekprev | 不含今天时无圈与加粗 |
| 起始星期持久化与窗口复位 | 周移后再进桌面回到含今天窗口；重启后仍按保存星期回到25–31；23-restart、24-week-shifted、25-reenter | 临时周窗口不恢复 |
| 勾选与重排 | 第20列「未设时间的事」勾选后实心框、灰色删除线并移到该列完成组末尾；29-toggled、kernel_prefs.after-toggle.json | 存储`done=true`，原`order`字段未重写 |
| 滚动后勾选 | 当日14条虚构条目使内容超高，拖动后第20列可见09:00–12:00；点10:30条目勾选框，实际命中id25，相邻id24/id26保持未完成；39-scrolled-now、40-toggle-scrolled、kernel_prefs.t5.json | 仅验证一次滚动后命中，未做快速连续滚动压力用例 |
| 无日期与越界时间 | `date=0`条目不出现在周历；`min=1500`条目按无时间处理；07-body-size | 无日期条目保留在原微信读书待办界面 |
| 编辑与删除 | 点正文打开「编辑待办」，删除二次确认后条目消失；31-editor、32-confirm、33-deleted | 数据写入同一存储 |
| 竖屏 | 竖屏七列加宽、表头固定、内容按底部裁剪并可滚动；41-portrait | 竖屏仅验证渲染，未逐条点验 |
| 外部写入映射 | 外部修改存储中条目正文与完成状态，重启后周历同步反映；42-external | 模拟另一入口写入，未在微读页面内同屏操作 |

## 恢复与交接

测试前备份`_analysis/verify/ux141/kernel_prefs.orig.json`与`config.orig.xml`，验收后按原文件覆盖恢复：待办清单与`todo_next_id`回到原值，桌面配置回到原`widgetCycleIds`与钟面，桌面截图43-restored与原首页一致。未保留设备端临时探针文件。构建与P4操作已结束，无后台构建或设备占用。

未覆盖：S4未验证（由UX139组织横竖目标设备）；竖屏交互逐条点验；微读页面与桌面同屏的实时同步；快速连点切周压力用例。未升版、提交、推送或发布。
