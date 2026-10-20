# 桌面小组件清点与删减整合清单

复核范围：`feature/widgets`、`core/widget-api`、`feature/home` 的组件宿主与版面度量、`feature/settings` 中与组件相关的设置项。只读分析，未修改工程源码。

声明表共 18 条（`feature/widgets/src/main/java/com/shuiyu/octoberrain/feature/widgets/WidgetCatalog.kt:229`），分四类（同文件 `:252`）：

| 分类 | 数量 | 组件 id |
| --- | --- | --- |
| 时间 | 3 | clock、clock_compact、calendar |
| 本地内容 | 5 | reading、covers、koreader_month、usage、image |
| 微信读书 | 8 | weread_covers、weread_month、weread_mark、weread_note、weread_stats、weread_bill、weread_rank、weread_fingerprint |
| 其他 | 2 | weather、placeholder |

---

## 一、宿主与尺寸约束

清点前先说明三条影响处置判断的机制。

其一，桌面在同一时刻只启用一个组件。`DesktopConfig.widgetId` 默认值为 `"clock"`（`core/common/src/main/java/com/shuiyu/octoberrain/core/common/Models.kt:133`），`HomeView.ensureWidget` 按该标识创建唯一实例（`feature/home/src/main/java/com/shuiyu/octoberrain/feature/home/HomeView.kt:391-415`），组件选择页在标题下标注「同一时刻只显示一个组件」（`feature/widgets/src/main/java/com/shuiyu/octoberrain/feature/widgets/WidgetPickerScreen.kt:56`）。因此「删减组件」减少的是可供选择的种类，不改变桌面并行展示数量。

其二，组件槽为首页通栏、高度反算而非固定格数。`DesktopMetrics.compute`（`feature/home/.../DesktopMetrics.kt:210`）在首页启用组件时，先求首页应用行数的最大可行值，再把剩余高度交给组件槽（同文件 `:441` `maxFirstRows`、`:464` `resolveWidgetBlock`）；宫格形态组件槽最小高度取单元高度的 1.6 倍（`:194`），列表形态取四行高度（`:197`），首页应用行数下限为 2（`:191`）。组件内部以 `heightPx < tokens.dp(150f)` 判定紧凑形态（`feature/widgets/.../InkWidgetView.kt:60-61`），紧凑时多数组件会隐藏次要信息行。

其三，刷新与能力由声明驱动。整分刷新只对 `WidgetRefreshPolicy.MINUTE` 生效（`core/widget-api/src/main/java/com/shuiyu/octoberrain/core/widget/WidgetApi.kt:162`、`feature/home/.../HomeView.kt:253`），当前仅 calendar 使用该策略（`WidgetCatalogTest.minuteRefreshOnlyAppliesToCalendar`）。`requiredCapabilities` 只做声明，组件选择页未按能力过滤（`WidgetPickerScreen.fill` 直接遍历全部分类），缺少授权时由各组件自行绘制空态。

数据接口与超时集中在 `feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/ContentSourceImpl.kt:50-71`：书架 38 s、月报 35 s、摘录 65 s、统计 45 s、笔记 65 s、阅读快照 42 s、天气 12 s、使用统计 5 s、本地扫描 8 s、KOReader 7 s。

---

## 二、逐项清点

行数为 `(Get-Content).Count` 结果；「实现」列给出文件与类声明行号，文件均位于 `feature/widgets/src/main/java/com/shuiyu/octoberrain/feature/widgets/`。

| 编号 | 组件 id | 名称 | 实现（文件:行，行数） | 绘制内容 | 数据来源与超时 | 刷新 | 空态与未配置态 | 点击行为 | 测试覆盖 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| W01 | reading | 阅读进度 | ReadingWidget.kt:18，106 行 | 书名与百分比大字、作者、进度条、今日与本月分钟，共 4 段文字加 1 条 | `ContentSource.readingSnapshot()`，KOReader 优先、微信读书回退，42 s | EVENT | 「还没有阅读记录」 | 来源含 weread 进微信读书页，否则进 KOReader 页（`:95-105`） | 无组件级用例；逻辑无独立纯函数 |
| W02 | weather | 天气 | WeatherWidget.kt:15，84 行 | 温度大字、状况、城市、最高最低、4 个时段逐时温度 | `ContentSource.weather()`，网络，12 s，能力 WEATHER | EVENT | 「天气数据不可用」 | 无点击 | 无 |
| W03 | clock | 时钟 | ClockWidget.kt:20，279 行；表盘在 ClockFaces.kt:17-25 | 5 种表盘（大字、上下、翻页、方框、指针）之一，配日期与星期；透明底板 | 本机时间（`Calendar.getInstance()`） | MANUAL，内部 Handler 每分钟重绘（`:31-60`） | 无空态 | 无点击 | 无绘制用例 |
| W04 | clock_compact | 紧凑时钟 | ClockWidget.kt:20（`compact = true`） | 单行钟面与日期，按宽度分配字号 | 同上 | 同上 | 无 | 无 | 无 |
| W05 | calendar | 日历 | CalendarWidget.kt:16，110 行 | 年月标题、当日日期、星期表头、6×7 网格、当天反白圆角块 | 本机 `LocalDate.now()` | MINUTE | 无 | 无 | 无 |
| W06 | covers | 书架封面 | CoversWidget.kt:24（`Source.LOCAL`），196 行 | 三张封面叠放加书名，缺图绘制书本占位与描边 | `ContentSource.bookshelf(3)`，本地扫描，8 s，能力 STORAGE | EVENT | 「书架为空」 | 进 `Destination.Bookshelf`（`:184-190`） | 无 |
| W07 | weread_covers | 微信读书封面 | CoversWidget.kt:24（`Source.WEREAD`） | 同上 | `ContentSource.wereadShelf(3)`，网络，38 s，能力 WEREAD | EVENT | 「微信读书未连接」 | 进 `Destination.Weread` | 无 |
| W08 | weread_month | 微信读书月报 | HeatMonthWidget.kt:21（`Source.WEREAD`），286 行 | 本月时长、上月时长、7×6 热力格四档灰度；点按在周视图与整月视图间切换 | `ContentSource.wereadMonth()`，35 s，能力 WEREAD | EVENT | 「微信读书未连接」并绘制空网格（`:153-168`） | 切换周/月视图（`:128-135`） | 无；视图切换无用例 |
| W09 | weread_mark | 微信读书摘录 | WereadMarkWidget.kt:16，72 行 | 左侧竖线、摘录折行（按可用行数）、底部书名 | `ContentSource.wereadHighlights(1)`，65 s | EVENT | 「暂无摘录」 | 无（未继承卡片基类，不跳转） | 无 |
| W10 | weread_note | 微信读书笔记 | WereadNoteWidget.kt:17，87 行 | 书名、正文最多 4 行、分隔线、想法一行 | `ContentSource.wereadNotes(1)`，65 s | EVENT | 三态：「微信读书未配置」「暂无阅读数据」 | 进 `Destination.Weread` | `WereadWidgetLogicTest` 覆盖三态与截断函数 |
| W11 | weread_stats | 微信读书统计 | WereadStatsWidget.kt:18，100 行 | 本周时长、阅读天数与日均、7 根柱状图 | `ContentSource.wereadPeriod(WEEKLY)`，45 s | EVENT | 三态同上 | 进 `Destination.Weread` | 逻辑层部分覆盖（分档、格式化） |
| W12 | weread_bill | 微信读书账单 | WereadBillWidget.kt:16，67 行 | 本月时长、环比文案、已阅读天数（紧凑时省略天数） | `ContentSource.wereadBill()`，复用月报接口，35 s | EVENT | 三态 | 进 `Destination.Weread` | 逻辑层覆盖（环比文案） |
| W13 | weread_rank | 微信读书排行 | WereadRankWidget.kt:17，117 行 | 标题、3 行排行（紧凑 2 行）：序号、书名、百分比、进度条 | `ContentSource.wereadRank(3)`，由书架派生，38 s | EVENT | 三态 | 进 `Destination.Weread` | 逻辑层覆盖（排序、进度夹取） |
| W14 | weread_fingerprint | 阅读指纹 | WereadFingerprintWidget.kt:17，120 行 | 标题、7×4 共 28 格四档灰度、连续天数与日均文案 | `ContentSource.wereadFingerprint()`，同时取年统计与月报，45 s | EVENT | 三态 | 进 `Destination.Weread` | 逻辑层覆盖（指纹格、标签） |
| W15 | koreader_month | KOReader 月热力 | HeatMonthWidget.kt:21（`Source.KOREADER`） | 与 W08 相同的热力网格，标题取本月合计 | `ContentSource.koreaderHeat()`，读统计库副本，7 s，能力 KOREADER | EVENT | 「KOReader 无记录」 | 切换周/月视图 | 无 |
| W16 | usage | 使用统计 | UsageWidget.kt:17，92 行 | 标题、3 行应用名与分钟数、归一比例条 | `ContentSource.usageToday()`，5 s，能力 USAGE_ACCESS | EVENT | 「暂无使用记录」 | 无 | 无 |
| W17 | image | 图片 | ImageWidget.kt:29，195 行 | 单张图片等比铺满并居中裁剪；无图时虚线框与提示 | `DesktopConfig.widgetImagePath`，解码经 `ThumbCacheRegistry`，能力 STORAGE | MANUAL | 「轻触选择图片」「图片无法读取」 | 打开图片选择器并复制到 `filesDir/widget-image`、写配置、失效缓存（`:96-125`） | 无 |
| W18 | placeholder | 占位 | PlaceholderWidget.kt:14，43 行 | 虚线框、「占位组件」「轻点打开组件库」 | 无 | MANUAL | 即空态本身 | 打开 `Destination.WidgetPicker` | 无 |

组件测试合计 34 例：`feature/widgets/src/test/.../WidgetCatalogTest.kt`（5 例，标识唯一、分类覆盖、分类一致、整分刷新归属、能力声明）、`WereadWidgetLogicTest.kt`（22 例，三态、格式化、环比、连续天数、分档、指纹、排行、摘要）、`core/widget-api/src/test/.../WidgetProviderDefaultsTest.kt`（7 例，默认分类、默认刷新策略、能力默认值与整分判定）。全部为纯逻辑用例，18 个组件的绘制与点击均无用例。

---

## 三、重叠与差异

**微信读书八项内部重复严重。** `weread_month`（W08）与 `weread_bill`（W12）都展示本月时长，前者附热力、后者附环比；`weread_stats`（W11）的 7 根柱子、`weread_fingerprint`（W14）的 28 格、`weread_month` 的月历格，表达的是同一组逐日分钟数的三种画法；`weread_mark`（W09）与 `weread_note`（W10）都展示最新划线，后者多一行想法。`weread_rank`（W13）与 `weread_covers`（W07）都取书架条目，前者按进度排序、后者按封面。单个组件槽的限制下，这 8 项只能择一，选择成本高而信息增量有限。

**阅读类跨来源重复。** `covers`（W06）与 `weread_covers`（W07）仅数据来源不同，实现同一份 `CoversWidget`；`koreader_month`（W15）与 `weread_month`（W08）仅来源不同，实现同一份 `HeatMonthWidget`；`reading`（W01）同时汇总两种来源的书名、进度与今日时长，与封面类和热力类在信息上部分重叠。

**负一屏已覆盖的能力。** 负一屏默认模块为 `["bookshelf","negative_files"]`（`Models.kt:97`），书架卡片展示微信读书或本地书架与进度操作，最近图片卡片展示三列缩略图并可进入全部图片。`weread_covers`（W07）与 `covers`（W06）的封面展示、`image`（W17）的单图展示在这两处已有更完整的呈现。

**时间类差异明确。** `clock` 与 `clock_compact` 共用 `ClockWidget`，差别只在布局；钟面样式另有独立选择页 `ClockStyleScreen`（入口在 `feature/settings/.../LauncherPrefsScreen.kt:72`），表盘数量为 5 种（`ClockFaces.kt:17-25`），与组件标识无关。`calendar` 数据自成一体，与其他组件无重复。

**小尺寸下的信息密度。** 组件槽高度由首页应用行数反算（`DesktopMetrics.kt:464-505`），在 480×800 上通常只够 2 至 3 行应用，组件槽高度约在 150 dp 上下波动，`tight` 判定频繁成立。此条件下：W12 的三行文字压缩为两行，W13 的三行排行降为两行，W14 的 28 格每格不足 10 px，H08 的 6 行月历会被压扁。信息最吃亏的是 W14，最稳的是 W03、W05、W16。

**使用频率的间接依据。** 默认组件为 `clock`（`Models.kt:133`），初次安装即启用时钟；设置页仅对 `image` 组件提供联动项（「组件图片」「清除组件图片」只在 `widgetId == "image"` 时出现，`feature/settings/.../DesktopPrefsScreen.kt:268-279`），其余组件在设置页仅显示名称（同文件 `:482-488`）。微信读书 8 项默认均可见，但依赖密钥与开关，未配置时统一显示「微信读书未配置」。

---

## 四、处置清单

列名按任务要求固定。代价分三档：小＝只增删声明表与少量文件，中＝涉及合并绘制或配置迁移，大＝需要新组件与数据接口调整。

| 编号 | 组件 id | 名称 | 处置 | 合并去向 | 理由 | 代价 |
| --- | --- | --- | --- | --- | --- | --- |
| W01 | reading | 阅读进度 | 保留 | 可作「阅读概览」主体（与 W06、W15 合并） | 唯一同时给出书名、进度与今日时长的组件，信息密度最高；负一屏只有书架列表，不替代 | 小（保留原样） |
| W02 | weather | 天气 | 保留 | — | 唯一网络天气组件，逐时温度与其他信息构成独立需求 | 小 |
| W03 | clock | 时钟 | 保留 | 吸收 W04，作为钟面与布局的统一入口 | 默认组件，5 种表盘已由 `ClockFaces.kt` 承载，两种布局可以并为一个声明的形态选项 | 小（保留）/中（吸收 W04） |
| W04 | clock_compact | 紧凑时钟 | 合并 | clock（形态选项「单行」） | 与 W03 同一实现，唯一差别是 `compact` 参数；保留两条 id 只会增加选择噪音 | 中：合并声明、选择页合并条目、旧配置 `clock_compact` 需迁移到 `clock`（id 发布后不可改，见 `WidgetCatalog.kt:12` 注释） |
| W05 | calendar | 日历 | 保留 | 可并入「时间」组件（上时钟下月历） | 无网络、无授权依赖，墨水屏上可读性稳定，与时钟场景互补 | 小（独立保留）/中（并入时间组件） |
| W06 | covers | 书架封面 | 合并 | weread_covers（来源选项）或「阅读概览」 | 与 W07 共用 `CoversWidget`，差别仅来源；负一屏书架卡片已提供更完整的书架入口 | 中：合并为带来源选项的单一声明，旧 id 需迁移 |
| W07 | weread_covers | 微信读书封面 | 合并 | 微读墨记整体组件（task-41）或 W06 的来源选项 | 封面信息在整体组件内的小封面区即可承载 | 中 |
| W08 | weread_month | 微信读书月报 | 合并 | 微读墨记整体组件（热力区）或与 W15 合一的「阅读热力」 | 月时长与热力是整体组件的核心区块；单独占满组件槽信息偏少 | 中 |
| W09 | weread_mark | 微信读书摘录 | 合并 | weread_note 或整体组件的摘录区 | 与 W10 同为最新划线，W10 多一行想法且共用三态基类；W09 未继承基类，交互不一致 | 小：删除声明与文件，逻辑函数保留于 `WereadWidgetData` |
| W10 | weread_note | 微信读书笔记 | 保留 | 可并入整体组件摘录区（保留独立入口） | 三态完整、点击进微信读书，摘录类中信息最全 | 小 |
| W11 | weread_stats | 微信读书统计 | 保留 | 整体组件的统计区 | 周柱状图与天数、日均是独立信息，且属纯展示 | 小 |
| W12 | weread_bill | 微信读书账单 | 删除 | 本月时长并入 W08 或 W11 | 本月时长与环比可由月报区一行文案表达，单独成组件只多一行文字 | 小：删除声明、类与测试断言 |
| W13 | weread_rank | 微信读书排行 | 保留 | 整体组件的排行区 | 按进度排序的榜单是其他组件没有的信息 | 小 |
| W14 | weread_fingerprint | 阅读指纹 | 删除或并入 | 整体组件的热力子区（作为小号指纹） | 28 格在 480 宽屏上每格不足 10 px，与 W08/W11 表达同一组逐日数据；信息辨识度最低 | 小（删除）/中（并入整体组件时重排尺寸） |
| W15 | koreader_month | KOReader 月热力 | 合并 | W08 的来源选项，或「阅读概览」 | 与 W08 同一实现；KOReader 用户在设置中关闭微信读书来源，可按来源切换 | 中：合并声明并迁移旧 id |
| W16 | usage | 使用统计 | 保留 | — | 唯一系统使用统计组件，非微信读书用户同样需要；数据本地可取 | 小 |
| W17 | image | 图片 | 保留并升级 | 相册轮播（复用 `MediaIndex`） | 唯一自定义图片组件，设置页有联动项；单图能力偏弱，相册索引已具备 | 小（保留）/中（升级为最近图片轮播） |
| W18 | placeholder | 占位 | 保留 | — | 组件库入口与空槽占位，交互简单，维护成本接近零 | 小 |
| W19 | （新增）weread_overview | 微读墨记整体 | 新增 | — | 把 W07、W08、W09/W10、W11、W13、W14 收进一个卡片：顶部封面与书名、中部本月时长与热力、底部最新摘录与排行入口；点击进入微读墨记页面 | 大：新文件、分区绘制、与 task-41 的页面方案共用数据接口 |
| W20 | （新增）reading_overview | 阅读概览 | 新增（可选） | — | 合并 W01、W06、W15：书名与进度、封面、本月热力，覆盖非微信读书用户 | 中：复用现有数据接口，重排绘制 |
| W21 | （新增）time_desk | 时间 | 新增（可选） | — | 上时钟下月历，替代 W03 与 W05 的并列选择 | 中：重排绘制与形态选项 |
| W22 | （新增）album_flow | 图片轮播 | 改造 W17 | — | 按日轮播最近图片，复用 `feature/files/.../data/MediaIndex.kt` 的索引与缩略图缓存 | 中：新数据来源与点击进入相册 |

与 task-41 的相容说明：整体组件（W19）把微信读书类 7 项（W07、W08、W09、W10、W11、W13、W14）收进一个卡片，`WidgetCatalog` 中保留一条 `weread_overview` 声明；原 7 条声明按处置分别删除或标记隐藏，旧配置 `widgetId` 指向这些 id 时在 `ConfigMigration` 中映射到 `weread_overview`。这一映射与 W04、W06、W15 的迁移属同一批工作，可一并完成。

---

## 五、推荐组合

三套方案共用同一条版面约束：同一时刻只有一个组件槽，槽高由 `DesktopMetrics` 反算，首页保留 2 至 3 行应用。480×800 下建议首屏排布为「组件槽 1 个 + 3 列 × 2 行应用」，第二页起为 3 列 × 4 行应用。

### 方案 A：精简（6 项）

保留 6 项：W01 reading、W03 clock（吸收 W04 的单行形态）、W05 calendar、W16 usage、W19 weread_overview、W22 图片轮播（W17 升级）。删除或合并其余条目：W02 天气、W06、W07、W08、W09、W10、W11、W12、W13、W14、W15、W17、W18；其中 W07、W08、W10、W11、W13 并入 W19，W06 与 W15 并入 W01 的「阅读概览」后续版本，W09、W12、W14 删除。

- 组件数量：6 项可选。
- 桌面排布：首页组件槽默认 `reading` 或 `clock`，应用区 3 列 × 2 行共 6 个图标；第二页 3 列 × 4 行共 12 个图标。使用统计、微读墨记整体、图片轮播、日历按需切换。
- 代价：W19 为大项，其余为声明删除与配置迁移，合计中等。

### 方案 B：均衡（10 项）

在 A 的基础上保留 W02 weather、W06 covers（来源合并 W07 后）、W10 weread_note、W11 weread_stats、W13 weread_rank、W18 placeholder；删除 W04、W09、W12、W14、W15，W17 升级为 W22。

- 组件数量：10 项可选。
- 桌面排布：首页组件槽默认 `clock`，3 列 × 2 行应用；负一屏继续承载书架与最近图片；第二页 3 列 × 4 行应用。
- 代价：合并类改动集中在 W06/W15，其余为声明层调整，合计中等。

### 方案 C：保留全部（18 项 + 可选整体组件）

保留现有 18 条声明与全部实现，仅做两项修正：`weread_mark` 与 `weread_note` 的点击行为统一（W09 目前不跳转），`widgetId` 为已删除或未知标识时回退到 `clock` 并提示（当前 `HomeView.kt:513-527` 绘制「未安装组件」占位）。

- 组件数量：18 项（如另加 W19 则为 19 项）。
- 桌面排布：首页组件槽默认 `clock`，3 列 × 2 行应用；负一屏书架与最近图片保持默认；第二页起 3 列 × 4 行应用。
- 代价：小，仅两处行为修正；维护面维持现状。

---

## 六、附：可直接引用的文件位置

- 声明表与分类：`feature/widgets/src/main/java/com/shuiyu/octoberrain/feature/widgets/WidgetCatalog.kt:36-227`（18 条声明）、`:229`（providers）、`:252`（byCategory）、`:271`（find）。
- 组件协议：`core/widget-api/src/main/java/com/shuiyu/octoberrain/core/widget/WidgetApi.kt:18`（WidgetView）、`:75`（BaseWidgetView）、`:131`（分类）、`:145`（刷新策略）、`:171`（WidgetProvider）、`:184`（WidgetRegistry）。
- 基类与紧凑阈值：`feature/widgets/.../InkWidgetView.kt:29`、`:60-61`、`:169`（空态）、`:189-191`（长按打开组件库）。
- 宿主与槽位：`feature/home/.../HomeView.kt:253`（整分刷新）、`:391-415`（创建唯一组件）、`:492`（组件矩形）、`:513-527`（未知标识占位）；`feature/home/.../DesktopMetrics.kt:210`、`:441`、`:464`、`:191-197`。
- 选择页与写入：`feature/widgets/.../WidgetPickerScreen.kt:37`（当前标识）、`:152-160`（写入 widgetId）。
- 设置页引用：`feature/settings/.../DesktopPrefsScreen.kt:267-279`、`:482-488`；钟面入口 `feature/settings/.../LauncherPrefsScreen.kt:72`。
- 数据接口与超时：`feature/content/.../ContentSourceImpl.kt:50-71`、`:243`（阅读快照）、`:304`（月报）、`:326`（周期统计）、`:342`（笔记）、`:366`（账单）、`:383`（排行）、`:398`（指纹）、`:448`（KOReader 热力）。
