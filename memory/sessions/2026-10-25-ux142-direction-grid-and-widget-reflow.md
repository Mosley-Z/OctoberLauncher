# 2026-10-25 UX142 交接

任务：[TASK-UX-142](../../task/TASK-UX-142-grid-and-widget-reflow.md)，已完成。验收：[2026-10-25-ux142-direction-grid-and-widget-reflow.md](../../docs/acceptance/2026-10-25-ux142-direction-grid-and-widget-reflow.md)。

## 实现位置

- `core/common/src/main/java/com/shuiyu/octoberrain/core/common/Models.kt`：`DesktopConfig` 新增 `portraitColumns/portraitRows/landscapeColumns/landscapeRows`（0 沿用原行列），扩展 `effectivePortraitColumns/Rows`、`effectiveLandscapeColumns/Rows`、`effectiveWidgetId`；`ThemeLayout` 增加四个可空字段。
- `feature/home/src/main/java/com/shuiyu/octoberrain/feature/home/DesktopMetrics.kt`：竖屏按方向容量替换其他页行列；无组件时首页复用整页版面；横屏在无组件时提前返回整页，不再构造 64:36 分栏；`hasWidget` 统一取 `effectiveWidgetId`。
- `feature/settings/src/main/java/com/shuiyu/octoberrain/feature/settings/DesktopPrefsScreen.kt`：宫格容量四行（首页、其他页默认、横屏、竖屏），`fitDirection` 只对当前方向用预览快照收敛，重置包含方向字段。
- `core/data/src/main/java/com/shuiyu/octoberrain/core/data/ThemeRepositoryImpl.kt`：主题映射四个方向字段，`resolveDirectionCapacity` 拆分降低圈复杂度。
- 测试：`core/common/src/test/java/com/shuiyu/octoberrain/core/common/DesktopDirectionCapacityTest.kt` 5 例。

## 交给 UX143 的语义

`DesktopConfig.effectiveWidgetId` 为空串即视为不占组件槽位；横屏无组件时首页与应用其他页同规格、整页宽度；有组件时首页仍按 `firstPageColumns/firstPageRows` 独立布局。UX143 删除 usage、`widgetCycleIds` 轮换与 `widgetSwitchGesture` 时，`effectiveWidgetId` 应改为只看 `widgetId`，`HomeView` 的 `hasWidget` 与关闭补位路径可保留。

## 验证边界

整仓 check 与两种构建通过，1946 例 0 失败 0 错误 2 跳过。P4 覆盖竖横两向 12 容量、关闭组件整页排布、页数收敛与当前页夹取、旧配置兼容；S4、备份导入端到端、美化逐屏点验、18 格以上满容量与连续切页压力未执行。设备配置已用 `_analysis/verify/ux141/config.orig.xml` 恢复并重启。版本保持 1.0.31/51，未提交、未推送、未发布。
