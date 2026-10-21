# 微信读书融合实现说明

本文记录微信读书数据接口、阅读页面、桌面组件与负一屏入口的实现方式，供后续维护与扩展查阅。文中的路径、类型与行号以当前工作区源码为准。

## 一、融合范围

融合分为四层：

1. 协议层：`core/common` 的数据快照、能力接口与目的地。
2. 数据层：`feature/content` 的客户端、解析、缓存与内容源实现。
3. 页面层：`WereadScreen` 的分段页面与 `WereadCalendarScreen` 阅读月历。
4. 桌面层：`feature:widgets` 的微信读书组件集与 `feature:home` 的负一屏入口。

所有能力在未配置密钥或未开启来源时降级为 null 或空集合，界面展示对应空态，不向界面抛出网络异常。

## 二、融合边界

决策 D2 规定（`docs/design/15-V1.0开发计划.md:172`）：墨盘停用，能力只保留在桌面内；微读墨记保留独立工程，因其仍有基于其他桌面的用户群体；融合方式是桌面侧按组件协议重绘，微读墨记源码不迁入，两侧并行演进。

由此形成三条边界：

1. 本仓只实现协议、数据适配、页面与组件，不复制微读墨记工程源码，也不新增对微读墨记 APK 的运行依赖。
2. 接口字段以 skills 能力文档为准，请求体顶层平铺业务参数（`docs/design/15-V1.0开发计划.md:114`）；实现侧只做字段映射、降级与缓存。
3. 验收证据按同屏截图逐项对照、四级灰一致性检查、离线与超时降级记录三类收集（`docs/design/15-V1.0开发计划.md:35`）。

## 三、协议层

### 3.1 快照类型

`core/common/src/main/java/com/shuiyu/octoberrain/core/common/Contracts.kt` 定义只读快照：

| 类型 | 行号 | 字段 |
| --- | --- | --- |
| `WereadBucketSnapshot` | 91 | `startMillis`、`minutes` |
| `WereadStatsSnapshot` | 105 | 周期、总分钟、阅读天数、日均、分桶 |
| `WereadNoteSnapshot` | 122 | 书名、章节、划线、想法、时间 |
| `WereadNotesSnapshot` | 136 | 笔记列表与总数 |
| `WereadBillSnapshot` | 148 | 本月、上月分钟与逐日天数 |
| `WereadRankEntry` | 161 | 书名、作者、进度 |
| `WereadFingerprintSnapshot` | 175 | 累计分钟、阅读天数、日均、日序列 |

`WereadSection` 枚举（`Contracts.kt:53`）取值为 `OVERVIEW/STATS/NOTES/BILL/RANK`，用于页面分段与入口参数。

### 3.2 能力接口

`WereadContent`（`Contracts.kt:188`）集中声明八个读取方法：

- `wereadShelf(limit)`（`:190`）、`wereadMonth()`（`:193`）、`wereadHighlights(limit)`（`:196`）。
- `wereadPeriod(period)`（`:203`）、`wereadNotes(limit)`（`:210`）。
- `wereadBill()`（`:213`）、`wereadRank(limit)`（`:216`）、`wereadFingerprint()`（`:219`）。

`ContentSource` 继承该接口（`Contracts.kt:228`），保留阅读快照、天气、使用统计、书架、KOReader 热力、搜索与全量刷新。拆分的目的是把接口方法数控制在工程静态检查阈值以内，同时让组件侧只依赖协议类型。

### 3.3 目的地

微信读书相关目的地：

- `Destination.Weread`：总入口，见 `Contracts.kt:434`。
- `Destination.WereadTab(section)`：带分段参数，见 `Contracts.kt:443`。
- `Destination.WereadCalendar`：阅读月历，见 `Contracts.kt:448`。

显示标签集中登记在 `Contracts.kt:498-522`：总入口为“微信读书”，月历为“阅读月历”，分段入口回退为“微信读书”。

### 3.4 配置项

`core/common/.../Models.kt` 的 `IntegrationsConfig` 保存密钥与刷新设置：

- `wereadRefreshMinutes`（`Models.kt:316`）与 `wereadStatsRange`（`:318`）。
- 有效值经 `effectiveWereadRefreshMinutes`（`:323`）与 `effectiveWereadStatsRange`（`:329`）夹取。
- 可选刷新间隔为 30、60、180、360 分钟（`:339`）；统计范围为 `month/year/overall`（`:345`）。

## 四、数据层

### 4.1 客户端

`feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/data/WereadClient.kt`（720 行）负责 HTTP 请求与网关校验。

主要方法：

| 方法 | 行号 | 行为 |
| --- | --- | --- |
| `invalidate()` | 72 | 清空内存缓存 |
| `isConfigured()` | 81 | 判断密钥非空 |
| `shelf(limit)` | 84 | 书架列表 |
| `month()` | 107 | 本月与上月统计 |
| `weekly(base)` / `annually(base)` / `overall()` | 128 / 131 / 134 | 周期统计 |
| `notes(limit, cursor)` | 142 | 笔记分页 |
| `highlights(limit)` | 278 | 最近划线 |

网关校验与密钥读取集中在 `private fun config()`（`:334`）与 `resolvedKey()`（`:341`）。请求失败时记录日志并返回空结果，调用方无需捕获网络异常。

### 4.2 传输、解析与缓存

- `WereadTransport.kt`（69 行）：`RawResponse`（`:9`）与 `HttpWereadTransport`（`:38`），负责 JSON 请求与超时。
- `WereadParsing.kt`（183 行）：周期起点 `periodStartSeconds`（`:33`）、统计解析 `stats`（`:56`）、笔记本条目 `notebookEntries`（`:73`）、笔记解析 `notes`（`:99`）、深链 `deepLink`（`:111`）。
- `WereadBucketParsing.kt`（118 行）：分桶与周期数据的补充解析，供统计数据在缺失字段时回退。
- `WereadCache.kt`（107 行）：按模式与周期起点分键，默认保留最近六期（`maxPeriods = 6`，`:14`）；`get`（`:19`）、`put`（`:25`）、`invalidate(mode)`（`:39`）、`invalidate()`（`:50`）、`periods()`（`:56`）。
- `WereadStats.kt`（75 行）：数据模型 `WereadPeriod`（`:4`）、`WereadBucket`（`:19`）、`WereadStats`（`:34`）、`WereadNote`（`:54`）、`WereadNotePage`（`:71`）。

### 4.3 内容源实现

`feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/ContentSourceImpl.kt`（517 行）实现全部读取方法。

来源开关 `wereadShelfEnabled()` 位于 `:121`，同时检查微信读书来源开关与书架来源开关。

超时常量（`:50-71`）：

- 书架 38 秒、月报 35 秒、划线 65 秒。
- 统计 45 秒、笔记 65 秒。
- 阅读快照 42 秒、天气 12 秒、使用统计 5 秒。

新增方法均以 `guarded` 包裹并检查来源开关：

| 方法 | 行号 | 无数据行为 |
| --- | --- | --- |
| `wereadPeriod` | 326 | null |
| `wereadNotes` | 342 | 空快照 |
| `wereadBill` | 366 | null |
| `wereadRank` | 383 | 空列表 |
| `wereadFingerprint` | 398 | null |

账单由月报派生，排行按书架进度降序，指纹取累计统计与当月逐日数据。派生只读取既有客户端结果，不新增网络请求。

## 五、页面层

### 5.1 分段阅读页

`WereadScreen.kt`（445 行）是 `Screen` 实现，页面结构为：顶部分段标签、周期选择行、正文容器。

关键方法：

- `createView`（`:79`）构建页面框架。
- `refreshFlags`（`:138`）读取密钥与来源开关。
- `rebuildTabs`（`:150`）重建分段标签。
- `selectSection`（`:173`）与 `selectPeriod`（`:181`）切换状态。
- `loadSection`（`:191`）按分段加载数据；`loadStats`（`:233`）与 `loadOverview`（`:254`）为具体加载分支。
- `renderSection`（`:301`）渲染正文；`renderSetup`（`:341`）与 `renderDisabled`（`:356`）为未配置与未启用空态。

视图构件拆分为两个文件：

- `WereadSections.kt`（221 行）：分段常量、周期标签与共享状态。
- `WereadSectionViews.kt`（521 行）：统计、笔记、账单、排行与指纹的卡片视图。

### 5.2 阅读月历

`WereadCalendarScreen.kt`（310 行）展示按月热力与逐日数据：

- 页面入口 `createView`（`:55`）。
- 数据加载 `loadMonth`（`:82`）与请求封装 `requestMonth`（`:104`）。
- 渲染 `renderCalendar`（`:130`）、月份标题 `buildMonthTitle`（`:145`）、网格 `addMonthGrid`（`:172`）、选中摘要 `addSelectionSummary`（`:198`）。
- 星期行 `weekdayRow`（`:222`）与日格 `dayCell`（`:242`）。
- 空态与错误 `renderMessage`（`:283`）；导航与返回见 `:302`、`:307`。

### 5.3 路由注册

`feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/ContentModule.kt` 注册三类目标：

- `Destination.Weread -> WereadScreen()`（`:45`）。
- `is Destination.WereadTab -> WereadScreen(destination.section)`（`:46`）。
- `Destination.WereadCalendar -> WereadCalendarScreen()`（`:47`）。

### 5.4 书架与负一屏入口

书架页头部提供两个入口：

- “笔记”导航到 `WereadTab(NOTES)`，见 `BookshelfScreen.kt:161-163`。
- “统计”导航到 `WereadTab(STATS)`，见 `BookshelfScreen.kt:166-168`。

负一屏书架卡片在标题行提供同名入口，见 `BookshelfNegativeModule.kt:204-211`。两处入口都复用目的地参数，不复制页面状态。

## 六、桌面组件

### 6.1 组件清单

微信读书在桌面侧只保留一个组件 `weread_board`，条目 id 由 `WEREAD_BOARD_WIDGET_ID` 声明，在 `WidgetCatalog.kt` 登记为 `WidgetCategory.WEREAD`、`WidgetRefreshPolicy.EVENT` 与 `requiredCapabilities = setOf(Capability.WEREAD)`。

| 文件 | 内容 |
| --- | --- |
| `WereadBoardWidget.kt` | 单张卡片的五种形态、页签、长按菜单与命中区 |
| `WereadCardWidget.kt` | 卡片基类与三态判定 |
| `WereadWidgetData.kt` | 比例、分档、排行与指纹计算 |
| `WereadWidgetLogic.kt` | 三态文案与时长增减文案 |
| `kernel/KernelWereadBoard.kt` | 内核实例的绑定、渲染与动作转发 |
| `kernel/KernelHostImpl.kt` | 内核宿主：私有目录、屏幕密度、重绘回调与页面跳转 |
| `kernel/KernelDataSourceImpl.kt` | 内核数据源：内容服务调用、超时兜底与短时缓存 |
| `kernel/KernelDataMapper.kt` | 字段搬运与单位换算 |
| `kernel/KernelBridgeInstaller.kt` | 幂等写入内核工厂，组件实例创建时调用 |

形态枚举 `WereadBoardForm` 取 `WEEK`、`MONTH`、`BOOK`、`NOTE`、`TODO`，当前形态编号存于 `negativePage.forms["weread_board"]`。卡片以 368 像素为基准宽度绘制，字号与热区按 `scale = width / 368` 等比缩放并限制在 0.6 到 2.0。命中区 z 序为菜单三行、五个页签与正文，`hitRegions()` 按同一顺序导出供宿主判定。

组件基类 `InkWidgetView` 的契约在本版扩展：`WidgetView` 增加 `onLongPress(x, y)` 与 `hitRegions()`，无参 `onLongPress()` 保留并转调坐标版本，`hitRegionAt(x, y)` 取首个命中。

### 6.2 内核接入

内核 jar `libs/weread-kernel-1.3.2-k1.jar` 由微读墨记工程产出，包名 `com.inkread.weekread.kernel`，对外接口为 `KernelHost`、`CardSurface`、`DataSource` 与 `CardKernel`（`FORM_WEEK` 至 `FORM_TODO`）。`feature/content` 与 `feature/widgets` 的构建脚本以文件依赖引入，`app/proguard-rules.pro` 对内核包整体保留。

宿主侧约定：

- `KernelHostImpl.filesDir` 返回 `filesDir/weread`，与数据导入落位一致。
- `KernelHostImpl.scale` 返回屏幕密度，`requestRedraw` 转发组件的重绘请求。
- `openPage` 按标识映射到 `Destination.Negative`、`Destination.Bookshelf`、`Destination.WereadTab(WereadSection.BILL)`、`Destination.Book`、`Destination.Note` 与 `Destination.Todo`。

数据映射：`shelf()` 取 `wereadShelf(20)`；`period(mode)` 由 `wereadPeriod` 的每日平均时长与逐日桶换算，周形态补足七项、月形态补足三十一项；`notes(limit)` 取 `wereadNotes`；`todos()` 取 `wereadTodos`。内容服务缺少的字段按内核默认为空值。

动作对接：`menu:open` 映射内核动作 1（打开墨台），`card:open` 在本书、本记与待办形态映射内核动作 2，并按形态跳转到本书、本记或待办页；其余菜单项与页签名由组件自身处理。内核动作 3 至 10 与待办、热力区动作在组件现有命中区中没有对应项，未对接。

内核的账单、阅读资产与阅读指纹三种数据暂无契约来源，对应形态按空态绘制。`FingerprintGrid` 在 jar 中只有默认构造与顶点访问器，485 个顶点与 256 级墨量表没有公开填充入口，宿主无法构造有效网格。宿主未使用内核的 `KernelStore`，数据全部取自 `ContentSource`。

### 6.3 目录登记与配置迁移

`WidgetCatalog.kt` 的声明由十九条缩减为六条：保留 `clock`、`calendar`、`usage`、`image`、`placeholder` 与 `weread_board`。`clock_compact` 的紧凑形态并入 `clock`，由组件高度与构造参数选择；`covers`、`reading`、`weather`、`koreader_month` 与七个微信读书细分组件移除。`Entry` 增加 `forms` 字段，用于向选择页提供形态说明。

配置迁移升至 v8：`negativePage.modules` 按新版默认顺序重排已启用条目并保留用户顺序，`cardOrderInitialized` 标为真；桌面配置中已删除的组件编号由 `Models.kt` 的 `rewriteRemovedWidgetIds` 改写，微信读书类改为 `weread_board`，其余改为 `clock`。

## 七、刷新与缓存链

1. 组件为 `EVENT` 策略，数据变化时由内容源刷新或宿主事件触发重绘；`MINUTE` 策略只用于自带定时器的组件。
2. `ContentSourceImpl` 以 `guarded` 包裹请求，超时后返回空结果，避免组件线程被网络阻塞。
3. 缓存由 `WereadCache` 维护最近周期文件，`invalidate` 在密钥或设置变化后清空。
4. 密钥变更后由阅读页发起整刷（`WereadScreen.kt:406`）；组件在数据源变化时按事件重新加载。
5. 封面解码只发生在书架页与封面组件，统一经 `ThumbCacheRegistry` 读取（`CoversWidget.kt:174`）；统计、笔记、账单、排行与指纹组件不解码图片。

## 八、设置与许可

- 设置组 `GROUP_BOOKSHELF_WEREAD`（`BookshelfSpecs.kt:31`），包含启用开关、密钥填写与清除、缓存刷新（`:56`、`:67`、`:78`、`:86`）。
- 刷新间隔与统计范围在 `BookshelfSpecs.kt:100-135`，标签为 30 分钟、1 小时、3 小时、6 小时与月统计、年统计、全部（`:23`、`:26`）。
- 关于页展示数据来源与开源许可，见 `AboutScreen.kt:28`、`:59-63`。
- 许可条目集中定义在 `ThirdPartyLicenses.kt`（37 行）：来源提示为 `WereadMoji（MIT 许可）`（`:26`），详细说明在 `:29`，条目列表在 `:33`。

## 九、验证记录与限制

验证方式分三层：

1. 接口字段与请求体形状以 skills 能力文档为准，解析函数保持无 Android 依赖，可在 JVM 用例中直接喂入样例 JSON。
2. 缓存按模式与周期起点分键，并轮转保留六期，诊断时通过 `WereadCache.periods()` 读取现存周期（`WereadCache.kt:56`）。
3. 降级行为通过无密钥、无效密钥与断网三种状态核对：协议方法返回 null 或空集合，组件显示“未配置”“暂无阅读数据”或“加载中”，页面显示未配置说明。

与微读墨记的差异与并行约定：

- 本仓侧重数据融合、桌面组件与负一屏呈现；微读墨记保留独立的阅读界面与设置，两者不共享进程与数据文件。
- 字段解析以 skills 文档为准，微读墨记升级接口时本仓按同一文档同步，不直接读取其内部类型。
- 本仓不迁入微读墨记源码，组件视觉按桌面设计令牌重绘，四级灰与字号由 `InkColors` 与 `InkTokens` 决定。

真机记录：

- 480×800 上，未配置密钥时组件显示“微信读书未配置”，卡片占 4×2 区域；配置了无效密钥后显示“暂无阅读数据”，请求超时前显示“加载中”。
- 阅读月历在未配置状态下显示“微信读书未连接”，两种视图可通过点击切换。
- 负一屏书架卡片可见“笔记”“统计”入口，点击后创建阅读页，日志记录 `page=微信读书 create=41ms stack=2`。
- 设备未持有有效密钥，有数据态的统计、笔记、账单、排行与指纹内容未在真机验证；相关渲染逻辑由纯函数用例与静态检查覆盖。
- 真机走查期间出现过桌面焦点被其他应用抢占的情况，分段页面的截图未能稳定取得，验证记录以页面创建日志与入口截图为准。
- `WereadSection` 与 `WereadTab` 参数仅用于页面内分段，返回键统一回到书架或负一屏来源。

## 十、行号索引

| 主题 | 位置 |
| --- | --- |
| 快照类型 | `core/common/.../Contracts.kt:91-175` |
| 能力接口 | `core/common/.../Contracts.kt:188-228` |
| 目的地 | `core/common/.../Contracts.kt:434-448` |
| 配置项 | `core/common/.../Models.kt:316-345` |
| 客户端 | `feature/content/.../data/WereadClient.kt:72-278` |
| 解析 | `feature/content/.../data/WereadParsing.kt:33-111` |
| 缓存 | `feature/content/.../data/WereadCache.kt:19-56` |
| 内容源 | `feature/content/.../ContentSourceImpl.kt:121-403` |
| 分段页 | `feature/content/.../ui/WereadScreen.kt:47-356` |
| 月历页 | `feature/content/.../ui/WereadCalendarScreen.kt:40-307` |
| 路由 | `feature/content/.../ContentModule.kt:45-47` |
| 书架入口 | `feature/content/.../ui/BookshelfScreen.kt:161-168` |
| 负一屏入口 | `feature/content/.../ui/BookshelfNegativeModule.kt:204-211` |
| 组件基类 | `feature/widgets/.../WereadCardWidget.kt:26-66` |
| 状态文案 | `feature/widgets/.../WereadWidgetLogic.kt:25-59` |
| HeatMonth | `feature/widgets/.../HeatMonthWidget.kt:32-204` |
| 组件登记 | `feature/widgets/.../WidgetCatalog.kt:100-181` |
| 设置项 | `feature/settings/.../BookshelfSpecs.kt:31-135` |
| 许可 | `feature/settings/.../ThirdPartyLicenses.kt:26-33` |
