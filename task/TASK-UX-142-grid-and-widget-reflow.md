# TASK-UX-142 方向十二宫格与关闭组件补位

- 状态：DONE
- 优先级/阶段：P1 / 实现与设备验证完成
- 执行者/集成者：Codex主代理（执行与集成）/ Codex主代理
- 分支/工作树/源码基线：master/f025b18及既有差异
- 依赖：无；共享方向配置与迁移由[UX139](TASK-UX-139-widget-upgrade-integration.md)接入
- READY条件：已确认支持横3行4列/竖4行3列、保留既有选择；HomeView归属与执行分派明确
- 设计依据：[方案40](../docs/design/40-widget-upgrade-plan.md)
- 允许修改：feature/home DesktopMetrics、HomeView几何/页码/关闭后补位、HomeDesktopLayoutProvider；feature/settings桌面布局与DesktopPagination/预览/美化消费者及必要测试
- 共享入口/禁止范围：配置结构/迁移主代理串行；HomeView交接后才由UX143修改；不改首页有组件时的既定分栏/独立布局，不强改现有宫格选择

## 目标与用户闭环

设置其他页横屏3×4/竖屏4×3，转向后独立生效且顺序一致；组件库选关闭后释放空间并重新分页，横屏释放组件分栏让应用使用整页。退出组件库但未改变选择不触发布局变更。

## 实施步骤与交付物

1. 列出行列字段与所有消费者，区分其他页、首页、列表模式、横竖方向，交主代理冻结新增配置及兼容映射。
2. 支持两个12格规格；旧配置保持旧选择，未设置方向值按既有行为兼容，不将“支持”改成强制迁移十二格。
3. 关闭组件时以有效当前组件判断有无槽位，不被残余轮换ID撑住；横屏不再保留空组件分栏。
4. 保持应用相对顺序并重算页数/容量/命中；后页应用前移、空尾页收敛、当前页不越界，重新启用恢复正常组件布局。
5. 桌面、预览、美化、备份导入与布局快照一致；向UX143交接HomeView修改点和最终hasWidget语义。

## 需求与证据回读

1. 入口：现有桌面布局设置与组件库关闭项，无新页面。
2. 控件：其他页横竖行列可分别保存；组件选择关闭才释放槽位。
3. 归属：横竖12格针对其他页；无组件首页使用释放后的全页容量；有组件首页沿原独立布局；列表模式不改成宫格。
4. 字段/消费者：配置名实施前由UX139冻结；DesktopMetrics、绘制/点击、分页快照、美化预览消费同值。
5. 迁移：保留已选行列、应用顺序及无关设置；关闭/重开与冷启一致；与usage清退共用关闭补位路径。
6. 验收：横/竖设置→旋转→冷启；首页/其他页区分；关闭/仅返回/重开；多页、末页与预览吻合。

## 验收标准

- 横其他页3行4列，竖其他页4行3列；各12个容量，图标/名称不裁切，真实触点对应条目。
- 旧规格与显式首页布局保持；关闭组件后横屏左区被应用使用，竖屏顶区被应用使用，无占位空白。
- 第N页关闭后页数减少时当前位置有效，应用顺序/显隐/文件入口不变，不制造丢失或重复条目。
- 新旧配置正常加载和备份导入、关闭状态冷启均验证；旋转设备与尺寸模拟证据分开记。

## 执行与交接

本轮由Codex主代理审查DSH交付：发现并修复分页fallback未消费方向/关闭组件未整页、默认容量测量被方向覆盖、关闭组件时首页请求值被反写。真实桌面与快照优先复用DesktopMetrics的路径保持。统一门禁各976例0失败0错误1既有跳过；最终原生横竖关闭均12容量，设置修复未重复逐项步进；见[审查验收](../docs/acceptance/2026-10-25-ux142-review-refresh.md)。S4未操作，保留原备份/美化未测边界。

- 已改文件：`core/common/.../Models.kt`（新增四个方向字段、四个 `effective*` 扩展与 `effectiveWidgetId`、`ThemeLayout` 四个可空字段）、`feature/home/.../DesktopMetrics.kt`（方向容量、无组件首页共用整页、横屏无组件提前返回整页、`hasWidget` 改取有效组件）、`feature/settings/.../DesktopPrefsScreen.kt`（其他页横屏/竖屏两行、`fitDirection`、重置含方向字段）、`core/data/.../ThemeRepositoryImpl.kt`（主题映射四个方向字段、`resolveDirectionCapacity` 拆分）、新增 `core/common/src/test/.../DesktopDirectionCapacityTest.kt`。
- 接口决定：方向字段为 0 时沿用原 `columns/rows` 与自动计算；组件槽位以 `effectiveWidgetId` 判断，`widgetCycleIds` 首项优先于 `widgetId`；朝向切换由 `desktop.orientation` 驱动，方向行只在当前屏幕方向用预览快照收敛。`hasWidget` 语义交给 UX143：空串即不占槽位，横屏不再构造分栏。
- 验证命令及结果：`gradle :core:common:testDebugUnitTest :core:data:compileDebugKotlin :feature:home:compileDebugKotlin :feature:settings:compileDebugKotlin --offline` 通过；四个模块 testDebugUnitTest/ktlintCheck/detekt 通过（期间修正 `applyThemeLayout` 圈复杂度 18→拆分两条）；整仓 `gradle check :app:assembleDebug :app:assembleRelease --offline` 通过，1946 例 0 失败 0 错误 2 跳过（每变体 973 例）。
- 产物与证据：debug 7213098 字节 SHA256 ca45de6b2ad76256be907e1f17c0a79eac10798c370e57705d4333cc041b75f7；release 2223935 字节 SHA256 1f751f0663bfb760a0eb02877dcd71529273079dfda1c64c1ffddffdba5eb90d；门禁日志 `_analysis/verify/ux142/gate-ux142.log`；设备截图与配置快照 `_analysis/verify/ux142/`；记录见[UX142验收](../docs/acceptance/2026-10-25-ux142-direction-grid-and-widget-reflow.md)。
- 未覆盖/阻塞：S4 未验证；应用方向由配置锁定，未做物理旋转对照；备份导入端到端与美化逐屏点验未执行；18 格以上满容量与连续切页压力未测。
- 后台进程/设备占用/恢复情况：P4 已恢复原配置并重启（原时钟组件与既有行列），无后台构建与设备占用。
- 下一步：等待 UX139 整批汇总；UX143 接 HomeView 时按 `effectiveWidgetId` 与整页容量语义继续。
- 提交/合并/发布：未执行，版本保持 1.0.31/51。

2026-10-25新授权：首页有组件且行列为自动时，竖屏2行3列、横屏4行2列，随实际横竖布局切换；非零显式行列保留，无组件整页补位保持。主代理所有文件DesktopMetrics、DesktopPagination及其测试；DSH不得覆盖，最终与143/144统一门禁和双机验收。


本轮S4回归及首页自动档追加已纳入[UX139最终集成验收](../docs/acceptance/2026-10-25-ux143-144-integration.md)。仅补已列S4范围，不把旧未测项全部改写为通过。
