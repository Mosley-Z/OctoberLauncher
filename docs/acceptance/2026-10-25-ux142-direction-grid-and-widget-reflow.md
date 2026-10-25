# UX142 方向十二宫格与关闭组件补位验收

返回[验收索引](README.md)。任务卡：[TASK-UX-142](../../task/TASK-UX-142-grid-and-widget-reflow.md)；方案：[设计40](../design/40-widget-upgrade-plan.md)。

## 实现与兼容

其他页新增横竖两套容量，字段为 `desktop.landscapeColumns/landscapeRows/portraitColumns/portraitRows`，取值 0 表示沿用原 `columns/rows` 及其自动计算，旧配置与旧主题缺失字段时行为不变。`DesktopMetrics` 在竖屏按方向字段替换其他页行列，横屏同样按方向字段重算，并在有效组件为空时直接返回整页版面，不再保留 64:36 组件分栏。

组件是否占槽位统一以 `DesktopConfig.effectiveWidgetId` 判断：优先取 `widgetCycleIds` 首个非空项，否则取 `widgetId`，空串表示不占槽位。关闭组件后首页与其他页共用同一页容量，页数按实际条目重算，`HomeView.clampPage` 把当前页夹到新范围。

设置页「桌面与布局」的宫格容量分区现为四行：首页、其他页默认、其他页横屏、其他页竖屏。方向行只在当前屏幕方向下用版面预览快照收敛，另一方向保留输入值；恢复本页默认同时把四个方向字段归零。主题映射 `toThemeLayout/applyThemeLayout` 同步四个字段，旧主题缺省沿用当前值。

## 工程与产物

统一门禁 `gradle check :app:assembleDebug :app:assembleRelease --offline` 通过，日志 `_analysis/verify/ux142/gate-ux142.log`。测试聚合 314 个结果文件、1946 例、0 失败、0 错误、2 跳过（每变体 1 例既有跳过），即每变体 973 例；新增 `DesktopDirectionCapacityTest` 5 例。

产物：debug 7213098 字节 SHA256 ca45de6b2ad76256be907e1f17c0a79eac10798c370e57705d4333cc041b75f7；release 2223935 字节 SHA256 1f751f0663bfb760a0eb02877dcd71529273079dfda1c64c1ffddffdba5eb90d。版本保持 1.0.31/51。

## P4 验收

设备 P4L202610130006，屏幕 552×768（竖）与 768×552（横），debug 包覆盖安装。证据目录 `_analysis/verify/ux142/`。

| 项 | 操作与结果 | 证据 |
| --- | --- | --- |
| 方向行写入 | 竖屏行设 3 列 4 行、横屏行设 4 列 3 行，配置读回 portraitColumns=3 portraitRows=4 landscapeColumns=4 landscapeRows=3 | `08-portrait-3x4.png`、`09-landscape-4x3.png`、`config.cur2.xml` |
| 竖屏 12 容量 | 无组件时竖屏其他页 3 列 4 行，日志 cap0=cap1=12、pages=1，九个条目单页排布不裁切 | 日志 `03:42:20` 行、`38-portrait-home2.png` |
| 横屏 12 容量 | 有组件时横屏首页 2 列 2 行、其他页 4 列 3 行，日志 cap0=4 cap1=12、pages=2 | 日志 `03:35:22` 行、`17-landscape-p1b-n.png`、`15-landscape-home-n.png` |
| 有组件首页保持独立 | 竖屏首页 3 列 2 行（cap0=6），其他页 3 列 4 行（cap1=12），页面 2 页 | 日志 `03:44:40` 行、`42-portrait-widget2.png` |
| 关闭组件后横屏铺满 | 组件库选「不使用组件」后横屏首页 4 列 3 行（cap0=12）、pages=1，应用占满整页、无空分栏 | 日志 `03:36:45` 行、`20-closed-n.png` |
| 页数收敛与当前页夹取 | 停在第二页时关闭组件，页数 2→1，返回桌面直接落在第一页，无空页与越界 | 日志 `03:41:43` 行、`34-clamped-n.png` |
| 关闭状态冷启 | 关闭组件后强制停止并重启，仍为无组件整页版面 | `config.cur3.xml` 与重启后截图 |
| 方向切换后各自生效 | 横屏改为 4 列 3 行后切竖屏，竖屏立即显示 3 列 4 行；再切回横屏恢复 4 列 3 行 | `36-portrait-nw.png`、`20-closed-n.png` |
| 旧配置兼容 | 恢复原始配置（四个方向字段缺失即 0）后重启，桌面回到原 3 列 2 行首页与原组件 | `01-baseline.png`、`44-restored.png` |
| 布局预览与美化同源 | 预览与美化入口统一走 `DesktopPagination` 与 `HomeDesktopLayoutProvider` 的同一快照；代码路径复核，未逐屏点验 | 源码 `PreviewScreen.loadState`、`DesktopPagination.snapshot` |

日志行的临时 `Ux142Dbg` 打印来自验证构建，`HomeView.kt` 已在最终构建前移除，最终包不含该打印。

## 恢复与交接

验证完成后已用 `_analysis/verify/ux141/config.orig.xml` 覆盖回设备配置并重启，桌面恢复为原时钟组件与既有行列。未执行升级版本、提交、推送或发布。

未覆盖：S4 设备；真机旋转物理角度（本应用按 `desktop.orientation` 锁定方向，方向切换以设置为准）；备份导入的端到端流程（仅核对主题与配置映射代码）；美化预览逐屏点验；多页满容量（18 格以上条目）与快速连续切页压力。
