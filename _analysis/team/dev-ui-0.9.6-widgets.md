# 0.9.6 微信读书组件集与数据接口扩展

## 一、范围

对应 `docs/15-V1.0开发计划.md` 任务 6.2，写作用域为 `feature/widgets/src/main/java`、
`feature/widgets/src/test`、`core/common/.../Contracts.kt`、`feature/content/src/main/java` 与
`_analysis/team`。未修改 `core/common/.../Models.kt`（task-29 占用）、`feature/files` 与 `app`。

## 二、数据接口

### 2.1 core/common 契约

`Contracts.kt` 新增 `interface WereadContent`，由 `ContentSource` 继承，避免 `ContentSource`
方法数超过 detekt 接口阈值。八个方法如下，均在无配置、未授权或无数据时返回 null 或空快照，不抛异常：

| 方法 | 返回类型 | 说明 |
| --- | --- | --- |
| `wereadShelf()` | `List<WereadShelfItem>` | 书架条目 |
| `wereadMonth()` | `WereadMonth?` | 月度逐日时长 |
| `wereadHighlights(limit)` | `List<WereadHighlight>` | 最新摘录 |
| `wereadPeriod(period)` | `WereadStatsSnapshot?` | 周期统计，分桶按周期生成 |
| `wereadNotes(limit)` | `WereadNotesSnapshot` | 最近笔记 |
| `wereadBill()` | `WereadBillSnapshot?` | 月度账单，与上月对比 |
| `wereadRank(limit)` | `List<WereadRankEntry>` | 书架进度排行 |
| `wereadFingerprint()` | `WereadFingerprintSnapshot?` | 阅读指纹原始数据 |

快照类型与 `Models.kt` 的既有类型同文件放置，组件只依赖 `core:common`，不引用 `feature:content`
的内部类型：

- `enum class WereadStatsPeriod { WEEKLY, MONTHLY, ANNUALLY, OVERALL }`
- `data class WereadBucketSnapshot(startMillis: Long = 0L, minutes: Int = 0)`
- `data class WereadStatsSnapshot(period, totalMinutes, readDays, dayAverageMinutes, buckets)`
- `data class WereadNoteSnapshot(bookTitle, chapterTitle, text, thought, createdAt)`
- `data class WereadNotesSnapshot(notes, total)`
- `data class WereadBillSnapshot(monthMinutes, lastMonthMinutes, days)`
- `data class WereadFingerprintSnapshot(totalMinutes, readDays, averageMinutes, longestStreak, days)`

### 2.2 feature/content 实现

`ContentSourceImpl` 实现五个新方法，全部经既有 `guarded` 超时包装，并受微信读书来源开关约束：

- `wereadPeriod`：周、年、累计调用 `WereadClient` 对应方法并映射为快照；月周期由 `WereadMonth`
  的逐日时长生成，分桶起始时间戳为 0。超时 45 秒。
- `wereadNotes`：先取笔记概览，再按书取明细，映射为快照。超时 65 秒。
- `wereadBill`：复用月统计，输出本月、上月分钟数与逐日数据。超时 35 秒。
- `wereadRank`：由书架条目按进度降序截取。超时 38 秒。
- `wereadFingerprint`：由累计统计与月内逐日数据组合。超时 45 秒。

组件不直接调用网络，读取路径统一为 `AppServices.content`。

## 三、组件

### 3.1 文件与职责

| 文件 | 行数 | 职责 |
| --- | --- | --- |
| `WereadCardWidget.kt` | 73 | 卡片基类：三态判定、空态提示、内容区、点击跳转 |
| `WereadWidgetData.kt` | 98 | 纯数据逻辑：状态判定、时长与差值格式、连续天数、指纹格、排行与摘要 |
| `WereadWidgetLogic.kt` | 125 | 组件侧数据装配：读取内容源、计算派生指标、空态文案 |
| `WereadNoteWidget.kt` | 87 | 笔记卡：书名、章节、划线或想法摘要 |
| `WereadStatsWidget.kt` | 100 | 统计卡：本周总时长、天数、七柱图 |
| `WereadBillWidget.kt` | 67 | 账单卡：本月时长、上月对比、日分布 |
| `WereadRankWidget.kt` | 117 | 排行卡：前三本书名与进度条 |
| `WereadFingerprintWidget.kt` | 120 | 阅读指纹：四周二十八格灰度图案与摘要 |
| `HeatMonthWidget.kt` | 约 295 | 月热力：整月月历与按周两种视图 |

### 3.2 三态与文案

基类按 `wereadWidgetState(keyPresent, sourceEnabled, shelfEnabled, hasData)` 判定：

- 未配置：密钥为空或微信读书来源关闭，文案「微信读书未配置」。
- 无数据：已配置但周期统计为空，文案「暂无阅读数据」。
- 有数据：绘制各卡片正文；点击跳转 `Destination.Weread`，空态不跳转。

空态提示居中绘制，字号取 `tokens.textBody` 与高度比例的较小值，颜色取 `InkColors.INK_2`。

### 3.3 几何与配色

所有位置、尺寸、字号由 `onSizeChanged` 的宽高与 `InkTokens` 推导，未使用绝对像素常量：
内边距 `padX = tokens.padH`、上下 `pad = tokens.pageTop`，标题与正文取 `textHeading`、
`textBody`、`textCaption` 与高度的比例上限；柱图、进度条、指纹格按可用区域等分。
配色只用 `InkColors` 的 `PAPER`、`INK`、`INK_2`、`INK_3`、`INK_4`。
在 480×800 设备上组件占 4 列 × 2 行，实测卡片约 430×250 像素。

### 3.4 HeatMonth 两种视图

- 整月视图：六行七列月历，按当月一日在周一阵列中的偏移绘制，格内按四档灰度表示当日时长。
- 按周视图：一行七格并标注「一」至「日」，展示本周七天。
- 默认整月；点击组件在两种视图间循环切换，切换后调用 `requestDraw` 重绘。
- 数据源不可用时仍绘制当前视图的空网格，并在左上角给出「微信读书未连接」或「KOReader 无记录」，
  两种视图保持可区分。

### 3.5 目录登记

`WidgetCatalog` 新增五个条目，`category` 均为 `WidgetCategory.WEREAD`，
`refreshPolicy` 均为 `WidgetRefreshPolicy.EVENT`，`requiredCapabilities` 均为
`setOf(Capability.WEREAD)`：

| id | 标签 |
| --- | --- |
| `weread_note` | 微信读书笔记 |
| `weread_stats` | 微信读书统计 |
| `weread_bill` | 微信读书账单 |
| `weread_rank` | 微信读书排行 |
| `weread_fingerprint` | 阅读指纹 |

`byCategory` 的微信读书分组同步加入五个条目与既有 `weread_covers`、`weread_month`、`weread_mark`。

## 四、测试

`feature/widgets` 首次建立 `src/test`，全部为纯 JVM 逻辑测试，不启动 Android 画面：

- `WereadWidgetLogicTest`：21 例，覆盖状态判定、空态文案、时长格式、同环比、有效天数、
  连续天数、峰值、柱高比例、指纹格数、进度夹取与百分比、排行排序与截断、摘要折叠、指纹说明。
- `HeatMonthWidgetTest`：5 例，覆盖两种视图的循环切换。

模块测试结果：2 个套件、26 例、0 失败。

## 五、验证

命令：

```
$env:JAVA_HOME='D:\DSHStation\.toolchain\jdk\jdk17.0.20_12'
$env:ANDROID_HOME='D:\DSHStation\.toolchain\android-sdk'
$env:GRADLE_USER_HOME='D:\DSHStation\.toolchain\gradle-home'
gradle.bat --offline :feature:widgets:testDebugUnitTest :feature:content:compileDebugKotlin
    :app:assembleDebug check
```

结果：BUILD SUCCESSFUL，1005 个任务；`feature:widgets` detekt 0 条、ktlint 0 条；
新增与修改文件超过 120 列的行数为 0；APK `app/build/outputs/apk/debug/app-debug.apk`
4,267,185 字节。过程中修正了两处协作问题：`ContentSource` 接口方法数超阈值改为继承
`WereadContent`；`ContentSourceImpl.kt:430` 分桶映射的换行格式经 ktlintFormat 修正。

## 六、真机

设备 S4 `SA4B03CN2608081096X`，480×800，density 219。安装调试包后依次将
`shared_prefs/octoberrain_config.xml` 的 `desktop.widgetId` 改为目标组件，重启应用并截图。
配置备份见 `_analysis/team/shots/config-backup-0.9.6.xml`，结束后已恢复。

截图（`_analysis/team/shots/`）：

| 文件 | 内容 |
| --- | --- |
| `widget-note.png` | 笔记卡，未配置态 |
| `widget-stats.png` | 统计卡，未配置态 |
| `widget-bill.png` | 账单卡，未配置态 |
| `widget-rank.png` | 排行卡，未配置态 |
| `widget-fingerprint.png` | 阅读指纹，未配置态 |
| `widget-month-calendar.png` | 月热力整月视图，空网格与提示 |
| `widget-month-week.png` | 月热力按周视图，七格与星期标注 |
| `widget-nodata.png` | 统计卡无数据态，文案「暂无阅读数据」 |

设备无微信读书密钥，故有数据态未在真机出现；密钥与来源开关的真实数据表现需在配置密钥后复验。
设备 HOME 角色保持 `com.wetao.elauncher`，临时截图已删除。

## 七、遗留问题

1. 无数据态与未配置态只绘制统一提示，五个卡片在该两态下视觉一致，后续可加入组件名或图标区分。
2. `refreshPolicy` 已登记，但宿主刷新调度尚未按组件声明分发事件刷新，0.9.7 接线时需消费该字段。
3. 组件选择页未按 `requiredCapabilities` 过滤，未授权时仍可选择微信读书组件。
4. 月热力不可用态的网格为空骨架，若产品需要在无数据时展示估算分布，需另行定义。
5. 组件尺寸按首页默认 4×2 区域绘制，4×3、4×4 尺寸下的留白比例尚未走查。

## 八、修订记录

2026-10-20 初稿。数据接口与五个组件、月热力双视图、26 例测试、门禁与真机结果。
