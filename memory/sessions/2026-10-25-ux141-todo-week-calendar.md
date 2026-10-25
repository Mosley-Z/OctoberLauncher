# 2026-10-25 UX141 待办周历交接

关联[UX141](../../task/TASK-UX-141-todo-week-calendar.md)、[验收记录](../../docs/acceptance/2026-10-25-ux141-todo-week-calendar.md)、[方案40](../../docs/design/40-widget-upgrade-plan.md)。

## 本轮结果

月历改名与待办周历已实现并完成工程门禁与P4验收。`WidgetCatalog`中`calendar`显示名改「月历」，新增`todo_week`（时间分组、EVENT策略）；新增`TodoWeekCalendar`/`TodoWeekItems`/`TodoWeekWidget`/`TodoWeekEditor`，`DesktopConfig`增加`todoWeekStartDay`（ISO 1–7，缺省1）与`effectiveTodoWeekStartDay`。两变体各968例0失败0错误1既有跳过，debug 6985086字节、release 2220679字节，版本仍1.0.31/51。

## 关键决定

- 编辑对话框在feature/widgets内独立实现，数据全部经`AppServices.wereadTodos`；feature不横向依赖，未改动已验收的微读对话框版式。
- 日移右箭头起始日−1、左+1，并把新起始星期写回配置；周移±7只移动临时窗口，不写配置；重进或重启回含今天窗口。
- 投影排序不写回原手工`order`；`00:00`按有效时间处理。
- 跨入口一致性依赖同一`files/weread/kernel_prefs.json`、保存后重读与`HomeScreen.onResume→refreshCurrentWidget(true)`；仓库内没有待办存储广播。

## 证据与设备

证据在`_analysis/verify/ux141/`，门禁日志`gate-ux141.log`，测试聚合脚本`count_tests.py`。P4L202610130006测试后按`kernel_prefs.orig.json`与`config.orig.xml`恢复，桌面回原钟面；无后台构建或设备占用。S4未操作。

## 未覆盖

竖屏仅验证渲染，未逐条点验；微读页面与桌面同屏实时同步未做；快速连点切周未做压力用例。以上由UX139组织目标设备验收时补验。未升版、提交、推送或发布。
