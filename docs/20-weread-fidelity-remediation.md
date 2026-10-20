# 微读原版功能对照与整治计划

本文对照微读墨记 1.3.2-beta 原版与十雨桌面当前实现，列出自研部分与原版的差距，给出分阶段整治计划与验收标准。编写日期 2026-10-20，对应提交状态为十雨 1.0.1 candidate 与微读 1.3.2-beta candidate。

## 一 问题定性

十雨桌面在接入微读能力时，除抽取内核与迁移数据外，另写了一套并行的实现：一组组件自绘代码、一组数据抓取与解析代码、四个负一屏快照模块。这些实现能跑通，但与微读原版在视觉、字段与统计规则上存在实质差异。

### 1.1 渲染层双实现

`feature/widgets/src/main/java/com/shuiyu/octoberrain/feature/widgets/WereadBoardWidget.kt` 共 738 行，其中约 330 行是自行绘制的五形态：

| 方法 | 作用 | 与原版对应物 |
| --- | --- | --- |
| `computeLayout` | 计算页签、正文、菜单命中区 | 无，原版由 `CardInteraction` 与内核 `CardGeometry` 提供 |
| `drawWeek` | 自绘本周时长与柱状 | 内核 `render/CardRenderer.java` FORM_WEEK |
| `drawMonth` | 自绘月历热力 | 内核 FORM_MONTH（含票据头、合计、备注） |
| `drawBook` | 自绘本书进度 | 内核 FORM_BOOK |
| `drawNote` | 自绘摘录单条 | 内核 FORM_NOTE（含字号、槽位、滚动、展开） |
| `drawTodo` | 自绘待办 | 内核 FORM_TODO |
| `drawBars` `drawHeatGrid` `drawProgress` `drawFooter` `drawMenu` | 自绘辅助图元 | 内核 `render/CardLayout` 与 `CardRenderer` 内部 |

内核路径可用时走内核，内核不可用时回落到这套自绘。两套绘制并存的结果是：同一块卡片在不同数据状态下呈现不同视觉，自绘版缺少原版的排版常量、字重、留白与热力分档。

原版同类代码规模：`feature/WeekCardView.java` 1000 行、`feature/CardLayout.java` 298 行、`feature/CardInteraction.java` 210 行、`kernel/render/CardRenderer.java` 2217 行。

### 1.2 状态注入残缺

内核 `kernel/CardState.java` 提供 32 个写入方法，十雨适配层 `feature/widgets/.../kernel/KernelWereadBoard.kt` 只调用了 13 个：

已注入：`setHost` `setApiKey` `setRefreshing` `setUnit` `setNoteExpanded` `setFullscreen` `setMode` `setPhMain` `setPhSub` `setStats` `setBook` `setNote` `setTodo`

未注入：`setNoteHint` `setAchievementText` `setNoteCardSize` `setNoteFullSize` `setMonthHeatmap` `setNoteSlot` `setCoverBitmap` `setErrorText` `setEmptyNote` `setMonthScrollMax` `setNoteScrollY` `setNoteLineH` `setNoteViewH` `setNoteContentH` `setNoteScrollMax` `setDeskEnabled` `setDeskOpenLabel`

后果逐项列出：

- 书封不显示。原版卡片中的书封由 `setCoverBitmap(bitmap, id)` 提供，十雨从未调用。
- 摘录无提示语与字号控制。`setNoteHint` 承载「共 N 条」一类文案，`setNoteCardSize` 与 `setNoteFullSize` 控制本记卡片字号与展开态尺寸。
- 月形态热力开关无效。`setMonthHeatmap` 未被设置，内核沿用默认值。
- 本记与月形态无法滚动、无法展开。六项滚动相关字段全部缺省，`setNoteExpanded` 被固定写为 `false`。
- 取数失败与空数据无提示。`setErrorText` 与 `setEmptyNote` 未使用，卡片在失败时呈现为空白，这也是本次「整体卡片空白」故障的成因之一。
- 成就文案缺失。`setAchievementText` 未使用。
- 「打开桌面」按钮文案缺失。`setDeskEnabled` 与 `setDeskOpenLabel` 由 `KernelHostImpl` 的 `deskEnabled()` 与 `deskOpenLabel()` 提供，但未同步进 `CardState`。

### 1.3 数据层自建

`feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/data/` 下是与微读原版并行的数据实现：

| 文件 | 行数 | 与微读原版对应物 |
| --- | --- | --- |
| `WereadClient.kt` | 720 | `core/StatsStore.java` 374 行、`core/NoteStore.java` 1974 行、`core/BillStore.java` 206 行、`core/AssetStore.java` 234 行 |
| `WereadParsing.kt` | 183 | 原版各 Store 内的 JSON 解析 |
| `WereadStats.kt` | 75 | `core/PeriodStats` 与 `StatsStore` 的统计计算 |
| `WereadBucketParsing.kt` | 118 | 原版按周期分桶的统计 |
| `WereadCache.kt` | 107 | 原版 `kernel/KernelStore` 的磁盘缓存 |
| `WereadTransport.kt` | 69 | 原版网络请求封装 |
| `ContentSourceImpl.kt` | 517 | 微读数据服务的十雨实现 |

`ContentSourceImpl` 的微读方法全部走 `wereadClient` 网络请求：`wereadShelf` `wereadMonth` `wereadHighlights` `wereadPeriod` `wereadNotes` `wereadBill` `wereadRank` `wereadFingerprint` `wereadAssets` `wereadTodos`。导入到 `filesDir/weread/` 的原盘数据只服务内核渲染，两条数据来源并存，统计结果可能不一致。

### 1.4 统计规则偏差实证

**排行**。十雨实现：

```kotlin
wereadShelf(SHELF_CACHE_LIMIT)
    .sortedByDescending { it.progress }
    .take(count)
    .map { WereadRankEntry(title = it.title, author = it.author, progress = it.progress) }
```

按阅读进度降序，条目字段只有书名、作者、进度。微读原版 `feature/WeekCardView.java:987-988`：

```java
public java.util.List<com.inkread.weekread.kernel.PeriodSnapshot.Longest> rankItems() {
    return com.inkread.weekread.core.RankFilter.apply(getContext(), stats == null ? null : stats.longest);
}
```

按阅读时长排序，数据来自 `PeriodSnapshot.Longest`，并带 `rank_shelf_only` 书架过滤开关。真机截图对照：微读原版足迹页「读书排行」显示「卡拉马佐夫兄弟 28 小时 48 分」「演员自我修养 20 小时 11 分」，按小时降序；十雨排行卡片按进度百分比降序。

**本书**。十雨用书架第一本，阅读秒数与完成时刻写零值；原版 `BookProgress` 含阅读时长、完成时刻与书封。

**账单**。十雨 `WereadBillSnapshot` 只有本月时长、上月时长与环比；原版 `BillStore` 按周、月、年生成票据（`bill_weekly_<start>` `bill_monthly_<start>` `bill_year_backfilled`），含票据头、合计行、整单备注三块（由 `menu_blocks_mask` 位 1、2、4 控制）。

**指纹**。十雨只列累计时长、有效天数、自然日均与七日热力四行数字；原版是 485 个顶点、256 级墨量的书封构成图。

**资产**。十雨列出文字阅读、有声书、视频学习三项分钟数；原版统计页另含阅读天数、读完本数、笔记条数、成就等。

### 1.5 配置悬空

墨单外观 13 项已写入 `LauncherConfig.integrations.wereadMenu`（`Models.kt` 的 `WereadMenuConfig`），字段覆盖标题、单位、条数、最短时长、离架过滤、摘录来源、摘录模式、作者、时长、进度、页脚、票据块掩码、视图形态。内核对这 13 项没有任何读取通道，设置页改动只写进配置，渲染结果不变。

## 二 成因

1. 交接文档第七节 B2 与 B3 的要求是「模块带内核渲染」。实施时为让界面先显示，给内核不可用状态写了自绘回落，回落代码后来成为真机可见形态。
2. 数据层沿用十雨 1.0.0 已有的微信读书客户端，没有改到「读导入数据 + 内核计算」。
3. 验收只覆盖编译、安装、启动、崩溃与「新包对旧包」像素比对，缺少「与微读原版同屏比对」这一层。1.3.1 与 1.3.2 的逐像素比对只能证明内核替换前后一致，不能证明十雨呈现与原版一致。

## 三 整治原则

单一来源：微读原版内核与迁移后的原盘数据。十雨承担宿主职责：文件系统、配置存储、画布提供、手势转发、界面容器。近似实现删除，不再保留第二套绘制与第二套统计。

## 四 分阶段计划

### P0 删除组件自绘回落

范围：`WereadBoardWidget.kt` 删除 `computeLayout` `drawWeek` `drawMonth` `drawBook` `drawNote` `drawTodo` `drawBars` `drawHeatGrid` `drawProgress` `drawFooter` `drawMenu` 与 `BoardData` 近似装配，约 330 行；`WereadWidgetData.kt` 与 `WereadWidgetLogic.kt` 中与内核重复的装配一并删除。

内核不可用时显示统一占位「内核未就绪，请重新安装」。

验收：全仓搜索无 `drawWeek` 一类自绘方法；`:feature:widgets:assemble` 通过；五形态仅走内核路径。

工作量 0.5 人日。

### P1 补齐状态注入

建立 `CardState` 32 个字段与来源的对照表，逐项落实：

| 字段组 | 来源 |
| --- | --- |
| 书封 `setCoverBitmap` | 微读资产数据或十雨图片缓存，按 bookId 取缩略图 |
| 摘录 `setNoteHint` `setNoteCardSize` `setNoteFullSize` `setNoteSlot` `setNoteExpanded` | 笔记条数、墨单外观配置、展开交互状态 |
| 月形态 `setMonthHeatmap` `setMonthScrollMax` | 墨单外观配置与月历滚动位置 |
| 本记滚动 `setNoteScrollY` `setNoteLineH` `setNoteViewH` `setNoteContentH` `setNoteScrollMax` | 手势滚动量与本记排版度量 |
| 状态文案 `setErrorText` `setEmptyNote` `setAchievementText` | 取数结果、空态判定、成就数据 |
| 桌面入口 `setDeskEnabled` `setDeskOpenLabel` | 宿主 `KernelHost` 实现 |

交互转发：组件收到滚动与展开手势后调用 `CardSurface.onAction` 与 `hitTest`，内核已有的滚动与展开逻辑不必在十雨侧重写。

墨单 13 项通过 `CardState` 新增的配置入口注入；若需在 `CardState` 增方法，内核契约版本由 k1 升到 k2，jar 与接口同步更新。

验收：32 个字段全部有赋值语句，无字段依赖默认值；有效密钥下五形态各截一张，与微读原版同机同位截图比对，卡片区域差异像素为 0（状态栏、刷新图标、更新时间戳除外）。

工作量 1.5 人日。

### P2 数据层统一到原盘数据

`ContentSourceImpl` 的微读方法改为读 `filesDir/weread/` 原盘数据，经共享解析或内核 `KernelStore` 取值；`WereadClient` 网络请求路径保留为迁移期开关，默认关闭。

数据结构补齐：`WereadRankEntry` 增阅读秒数与书架过滤结果；`WereadBillSnapshot` 改读 `BillStore` 的周、月、年票据结构与票据块；指纹几何由内核 `FingerprintGrid` 提供；`WereadBookProgress` 补阅读时长、完成时刻与书封。

验收：断网状态下书架、负一屏、整体卡片、微读页全部可用；同一份原盘数据下，十雨显示的时长、条数、排行顺序与微读原版逐项相同。

工作量 2 人日。

### P3 负一屏四模块改内核渲染

账单、排行、指纹、资产四个模块改为内核对应形态渲染：账单用 FORM_MONTH（票据形态，13 项外观生效），排行按阅读时长与书架过滤，指纹用 485 顶点图，资产按原版分项。若内核形态与负一屏卡片尺寸不合，改为「摘要卡 + 打开完整页」，完整页用内核整卡渲染。

验收：与微读原版对应界面并排截图，文字、数值、图形一致。

工作量 1.5 人日。

### P4 防回归机制

- 像素比对脚本纳入门禁：同设备、同数据、同手势，五形态与负一屏四模块各截一张，与基准图比对，差异像素超阈值即失败。
- `docs/20` 的字段对照表与统计规则表作为后续修改的基准记录。
- 门禁新增 `fidelity` 段。

工作量 0.5 人日。

## 五 影响与代价

- P2 删改数据客户端会牵动 1.0.0 已有的双源书架（本地扫描与微信读书），本地扫描分支需保留。
- 内核 `DataSource.shelf()` 返回 `ShelfSnapshot`，与十雨 `WereadShelfItem` 字段不同，需要适配而不改内核契约。
- 墨单 13 项注入若改内核 `CardState`，契约版本升到 k2，微读侧 jar 与文档同步更新。
- 总工作量约 6 人日，可按 P0 到 P4 顺序分批交付，每批独立验收。

## 六 三种取舍

甲 完全对照原版。P0 至 P4 全做，负一屏也用内核渲染，视觉与数值与微读原版一致，工作量 6 人日。

乙 数据与组件对照原版，负一屏保留十雨卡片风格。做 P0、P1、P2 与 P4，负一屏只统一数据来源，视觉差异保留，工作量 4.5 人日。

丙 只修组件层。做 P0、P1，负一屏与数据层维持现状，工作量 2 人日。

推荐乙：组件与数据回到原版，负一屏风格属于十雨自有界面，保留其卡片语言不违反原版功能完整性；甲可作为后续版本目标。
