# 微读墨记内化实施计划

本文把 `docs/design/24-weread-spec-reference.md`（微读墨记功能与交互规格，573 行）落到十雨桌面的可执行任务，覆盖用户提出的五条要求。所有代码位置以十雨仓库当前工作区为准，内核接口以 `libs/weread-kernel-1.3.2-k4.jar` 为基准，k5 增量在第四节列出。

## 一 五条要求与实现对应

| 要求 | 实现方式 | 任务 |
| --- | --- | --- |
| 微读墨记 App 侧移入设置内作为模块，点开后与打开原 App 无区别 | 设置页新增「微读墨记」入口，进入全屏页面；页面结构按规格第六章：主页面四段页签（阅读、设置、实验室、待办），阅读页内部四页签（本周、本月、本书、洞察）加五档下拉（含本记与待办形态） | W9 |
| 待办卡片无标题无切换，切到待办后回不到其他形态 | 待办形态补画抬头行（形态切换热区 88×34，无「更新于」），抬头短按在卡片池内轮换形态，长按弹隐藏菜单 | W7 |
| 所有卡片按钮不可点击 | 补齐宿主侧热区：抬头切换、更新于刷新、右下「换一条」与「打开」、左下「上一条」与「选书」、行末展开、待办复选框、本记正文纵向拖动；删除「打开墨台」按钮后其热区一并移除 | W7 |
| 负一屏舍弃详情页，内容在页内展示，与墨台原版一致 | 负一屏改为墨台的列表态：模块以分区形式上下排列，页内直接交互。模块集合为墨单、阅读资产、阅读指纹，加上十雨自有书架与最近图片，共五项；书架与最近图片改用与墨台一致的分区版式，不再是卡片外壳 | W8 |
| 阅读规格文档并继续内化 | 规格文档归档为 `docs/design/24-weread-spec-reference.md`，本文给出任务映射；实施中把与实现偏差写回本文第六节 | W7 至 W12 |

用户补充确认的三项：负一屏在墨台三模块之外加入书架与最近图片两个自研模块，取消卡片形式与全部详情页；卡片抬头「打开墨台」按钮删除；待办清单由十雨自建，结构与微读 `todo_list` 一致。

## 二 目标形态

### 2.1 桌面卡片

抬头行左端为形态切换热区（88×34），其后为「更新于 HH:MM」（128×34，待办形态留空），右端留白。热区短按轮换卡片池内下一形态，长按弹隐藏菜单（四行：十五秒、一分钟、五分钟、取消，八秒无操作自动关闭）。卡片主体区域按形态承接交互：本周与本月点击打开微信读书对应位置，本书点击「打开」跳阅读页、「选书」弹候选，本记「上一条」「换一条」切换条目、正文纵向拖动逐行滚动并吸附整行，行末「展开」切换折叠与展开，待办逐条勾选。

卡片池由偏好 `card_pool_mask` 决定，当前十雨配置为 `forms["weread_board"]` 与形态枚举，实施时补一个掩码字段，默认五形态全开。

### 2.2 负一屏

负一屏即墨台列表态。顶栏三单位为高，左侧「齿轮 设置」，中间衬线标题「墨台」，右侧「更新于 HH:MM」与「返回」。分区自上而下按 `desk_order` 排列，启用由 `desk_on_mask` 控制。五个模块：

| 模块 | 标识 | 渲染方式 | 默认启用 |
| --- | --- | --- | --- |
| 墨单 | bill | 内核 `DeskPage` 的 `SEC_BILL` | 是 |
| 阅读资产 | rank | 内核 `DeskPage` 的 `SEC_ASSET` | 是 |
| 阅读指纹 | fp | 内核 `DeskPage` 的 `SEC_FP` | 否 |
| 书架 | bookshelf | 十雨自绘分区，套用墨台版式常数 | 是 |
| 最近图片 | negative_files | 十雨自绘分区，套用墨台版式常数 | 是 |

自绘分区必须与墨台一致：抬头行高 3 号、分隔线、正文区留白、字号与颜色取 `DeskStyle` 同一套令牌；分区高度由内容决定，超出部分在分区内滚动，页面整体不跳转。

### 2.3 设置内 App 模块

设置页新增分组「微读墨记」，条目「打开微读墨记」，进入全屏页面。页面内部结构：

阅读页：顶部四段页签（本周、本月、本书、洞察）与周期选择器「‹ 2026-W40 ›」，页签下方整卡容器以全屏档绘制；右上角刷新按钮；页面支持下拉五档切换形态（含本记、待办）。设置页：三组表单（显示、卡片、成就与联动），项与规格 6.3 一致。实验室页：默认隐藏，设置页连续点击版本号展开。待办页：清单增删改查。本记页：全屏本记，含筛选、选书、导出。洞察页：年度概览、阅读画像、书架构成、时段分布四节自绘。

按规格 6.12 的建议，遥控台、锁屏软锁、升级安装不移植。

## 三 任务分解

### W7 卡片交互完整实现

负责人 tenrain-widget。写域 `feature/widgets/src/main/java/com/shuiyu/octoberrain/feature/widgets/` 与 `feature/home/src/main/java/com/shuiyu/octoberrain/feature/home/`（HomeView 的触摸转发）。

目标：把规格 4.8 的九类热区全部接到内核命中与动作，删除「打开墨台」按钮，补待办形态抬头。

做法：

1. `WereadBoardWidget.kt` 删除抬头右端的「打开墨台」按钮绘制与命中；`openBody()` 中开墨台的分支删除。
2. 抬头热区改为两个：形态切换（左 88×34）与更新于（其后 128×34）。短按切换形态并写 `forms["weread_board"]`，长按在 `HomeView` 中弹隐藏菜单，菜单选项为 15、60、300 秒隐藏与取消，隐藏期间组件不绘制，用 `postDelayed` 恢复。
3. 更新于热区点击触发 `KernelDataSourceImpl.invalidate()` 与 `preload()`，显示刷新标记，完成后 `requestDraw()`。
4. 待办形态补抬头；待办条目逐条命中，勾选写十雨待办数据层（W10），并 `requestDraw()`。
5. 本记形态补「上一条」「换一条」，正文纵向拖动逐行滚动、位移 12 像素锁定滚动、吸附整行、关闭惯性。
6. 本书形态「打开」跳微信读书阅读页（`weread://reading?bId=`），「选书」弹候选列表，第一行「自动（最近在读）」，其后书架候选，当前项行首画竖条。
7. 卡片池掩码：`DesktopConfig` 增 `wereadBoardPoolMask`，默认 31（五形态全开），短按只在池内轮换。

验收：真机上五形态可自由切换，待办能回到其他形态；每类按钮点击有可见变化；长按抬头弹出隐藏菜单且按时长恢复；本记滚动与展开可用；`ktlintCheck`、`detekt`、单测通过。

### W8 负一屏墨台化

负责人 tenrain-negative。写域 `feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/ui/` 与 `feature/home/`。

目标：负一屏改为墨台列表态，五个模块统一版式，取消卡片外壳与详情页。

做法：

1. `NegativeScreen` 改为墨台容器：顶栏由三个单位高、左侧齿轮、中间「墨台」、右侧更新时间与返回组成；分区从 `NegativeModuleRegistry` 取，按 `desk_order` 排序。
2. 墨单、阅读资产、阅读指纹继续用内核 `DeskPage` 分区（现状已具备），删除其外围的卡片边距与圆角，改为墨台分隔线。
3. 书架与最近图片改为自绘分区：`ShelfDeskSection` 与 `RecentDeskSection`，抬头行沿用墨台样式，内容为书目行（封面缩略、书名、作者、进度）与图片行（缩略图、文件名、时间），点击在页内展开详情浮层，不跳转页面。
4. 模块开关与顺序写入 `desk_order` 与 `desk_on_mask`，设置页改为墨台设置态的三段结构（模块开关与排序、阅读资产三项过滤、阅读指纹选书与字号）。
5. 删除 `BookScreen`、`NoteScreen`、`TodoScreen` 三个详情页路由与相关入口（页面文件保留给 W9 的 App 模块使用）。

验收：负一屏自上而下为五个分区，顺序可调、开关生效；点击任意分区内的元素都在页内响应；返回手势与左滑阈值按规格 5.9；无任何路由跳转到详情页。

### W9 设置内微读墨记模块

负责人 weread-app（新成员）。写域 `feature/weread-app/src/main/java/com/shuiyu/octoberrain/feature/weread/`、`feature/settings` 中的入口声明、`core/common` 的 Destination。

目标：按规格第六章重建 App 内页面，与原生微读墨记页面结构一致。

做法：

1. 新建模块 `feature/weread-app`，注册 `WEREAD_APP` 页面：主容器为四段页签（阅读、设置、实验室、待办），阅读页内四页签（本周、本月、本书、洞察）与周期选择器。
2. 阅读页整卡使用内核全屏档渲染：`CardState.setFullscreen(true)`、`padXRatio=0.05`、unit 取屏长边乘 0.0015；周期切换改 `PeriodCalendar` 起点并重新取数。
3. 设置页三组表单写 `LauncherConfig`：纸色、正文字号、署名日期、榜单书架过滤；卡片总开关、形态池、月历样式、动画、尺寸；成就与联动（成就开关与目标、保存提示、帮助、无障碍设置入口）。
4. 待办页：清单增删改查，数据来自 W10。
5. 本记页：一条划线全屏，含筛选（全部与只看想法）、选书浮层、导出长图。
6. 洞察页：四节自绘，渲染内核 k5 的 `InsightRenderer`。
7. 设置页新增「微读墨记」分组与「打开微读墨记」条目，跳 `Destination.WereadApp`。

验收：从设置进入后与原生微读墨记页面结构一致；四页签与五档下拉可切换；周期选择器周月移动与长按跳年可用；设置项写入后卡片与墨台立即变化。

### W10 待办数据层

负责人 lead。写域 `core/common`、`core/weread-sync`。

做法：`core/weread-sync` 增 `WereadTodoStore`，读写 `filesDir/weread/kernel_prefs.json` 的 `todo_list` 键（JSON 数组，元素含 `id`、`text`、`done`、`time`、`order`），与微读格式一致；`Contracts.kt` 增 `WereadTodoApi` 接口与 `AppServices.wereadTodos` 可空字段；卡片待办形态、负一屏、App 待办页共用这份数据。

验收：新增、修改、删除、勾选、排序后重启保持；内核待办形态读到同一份数据；单测覆盖增删改查与排序。

### W11 内核 k5

负责人 areal。写域微读仓库 `app/src/main/java/com/inkread/weekread/kernel/**`、`tools/kernel/**`、`docs/kernel-integration.md`。

k5 在 k4 基础上增加：

1. `InsightRenderer` 平移：年度概览、阅读画像、书架构成、时段分布四节，入口 `kernel/insight/InsightPage.render(Canvas, width, height, offset)` 与 `height(width)`。
2. 卡片全屏档入口：`CardState.setFullScreenMetrics(width, height, padXRatio)` 与 `CardRenderer` 按全屏档取字号档位（16 号字号按 4.4、4.6 的对应关系）。
3. 待办条目几何与勾选命中：`HitRegions` 增待办条目编号区间，`CardState` 增 `todoCount()` 与 `todoHit(index)`。
4. 书架分区与最近图片分区的版式常数导出：`DeskStyle.HEADER_UNITS`、`DeskStyle.ROW_HEIGHT_UNITS`、`DeskStyle.DIVIDER_UNITS` 供十雨自绘分区复用，避免两套视觉。
5. 保持 `Kernel-Api-Version: 5`，两次构建字节一致，selftest 新增洞察页与待办命中两组用例。

验收：jest 探针渲染洞察页四节与待办命中表；十雨接入后 `:app:assembleDebug` 通过；jar 数值写入 `docs/kernel-integration.md` 第十六节。

### W12 文档与门禁

负责人 lead。写域 `docs/`、`scripts/`、`_analysis/verify/`、`CHANGELOG.md`。

做法：规格文档归档后，新增 `scripts/verify-weread-internalized.ps1`，断言五个模块在负一屏注册、卡片无「打开墨台」文本、四页签与五档下拉存在、待办数据层可写可读；补 `docs/24` 的实施映射与偏差记录；版本升 1.0.5，重建候选包并真机验证。

## 四 排期

| 阶段 | 内容 | 人日 | 依赖 |
| --- | --- | --- | --- |
| W11 | 内核 k5 | 4 | 无 |
| W7 | 卡片交互 | 3 | W10 |
| W8 | 负一屏墨台化 | 4 | W11 常数导出 |
| W9 | 设置内 App 模块 | 8 | W11 洞察与全屏档 |
| W10 | 待办数据层 | 1 | 无 |
| W12 | 文档与门禁 | 1 | 全部 |

W10 与 W11 可并行开工；W7 卡片交互中待办勾选等 W10；W8 的版式常数等 W11 第 4 项；W9 等 W11 全量。

### 实施进度

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| W11 | 完成 | `libs/weread-kernel-1.3.2-k5.jar`，215433 字节，sha256 `a4db239c41947cae41beeeea62a617687f79a24a0b9e5198f1052805a6884fb6`，79 class，`Kernel-Api-Version: 5`；洞察页、全屏档、待办几何、`DeskStyle` 十常量齐备 |
| W10 | 完成 | `WereadTodoStore`、`WereadTodoHost`、`HostWereadTodos` 与单测十九用例通过，`kernel_prefs.json` 的 `todo_list` 与 `todo_next_id` 与内核键名一致 |
| W7 | 完成 | 卡片交互与待办命中：抬头热区拆为形态切换与更新于两块，待办形态带抬头可切回本周，九类热区经 `hitRegions`/`hitRegionAt` 与坐标派发，长按抬头弹隐藏菜单；「打开墨台」按钮绘制与命中已删除 |
| W8 | 完成 | 负一屏墨台化：`DeskContainerView`（列表态、设置态、左滑退出）、`DeskSectionStyle`、`ShelfDeskSection`、`RecentDeskSection`、`DeskKernelSection`、`DeskOverlayView`、`DeskSettingsView`、`DeskNegativeModule`；五分区顺序取 `desktop.negativePage.modules`；`NegativeScreen.open()` 置空，负一屏不再发起任何 Destination 跳转；顶栏高度与内核 `secHeadH` 同式 |
| W9 | 完成 | 设置内微读墨记模块 `feature/weread-app`：主容器四页签、阅读页四段页签与周期选择器、五档下拉、全屏整卡、设置页三组表单、实验室连点展开、待办页增删改查、本记页筛选与选书与导出、洞察页四节 |
| W12 | 完成 | 全量门禁 126 套件 1100 用例 0 失败 2 跳过；1.0.5 候选包与快照已出 |

| W13 | 完成 | 双设备适配与走查修复：P4（552×768）检查报告 `docs/acceptance/26-device-adaptation-report.md`；已修分区抬头与空态引导内边距、设置首页微读墨记入口、设置态模块行控件间距；内置页实验室页签改为常显，页头留白、紧凑周期标签、本记筛选菜单、选书浮层限行、洞察页滚动与周期范围解析由负责成员修复 |

W9 的页签口径：主容器四段为「阅读、设置、实验室、待办」，阅读页内四段为「本周、本月、本书、洞察」，五档下拉为「本周、本月、本书、本记、待办」；`docs/24` 6.1 首段那句描述的是阅读页内页签。

## 五 验收矩阵

| 项 | 判据 |
| --- | --- |
| 待办可切换 | 待办形态有抬头，短按后回到本周 |
| 卡片按钮可点 | 九类热区逐项点击有可见响应，日志记录动作编号 |
| 负一屏无详情页 | 全应用搜索不到从负一屏发起的页面跳转 |
| 墨台版式一致 | 五个分区抬头行高、分隔线、字号与内核 `DeskStyle` 一致，差异像素比不超过千分之一 |
| App 模块结构 | 主页面四页签、阅读页四页签、五档下拉、周期选择器齐备 |
| 待办持久 | 增删改查后重启保持 |
| 内核契约 | k5 jar 摘要、`Kernel-Api-Version: 5`、探针图齐备 |
| 门禁 | 全量 `testDebugUnitTest check :app:assembleDebug :app:assembleRelease` 退出码 0 |

## 六 偏差记录

实施过程中与规格不一致之处记入本节，逐条写明发现时间、规格原文、实际做法与理由。

暂无记录。
