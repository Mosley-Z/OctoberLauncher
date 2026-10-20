# 微读墨记整体组件、墨台入负一屏与 APP 内置方案

## 一、范围与材料

评估对象为 `D:\WorkBuddyStation\WereadMoji`（微读墨记，包名 com.inkread.weekread）与 `D:\DSHStation\OctoberLauncher`（十雨桌面，包名 com.shuiyu.octoberrain）。本报告只做方案与估算，不写代码，不修改两侧工程文件。

阅读材料：微读墨记 `README_项目导览.md`、`docs/00_项目宪法.md`、`docs/02_架构.md`、`docs/03_墨水屏UI规范.md`、`docs/04_桌面让位判据.md`、`docs/FEATURES/desk.md`、`docs/FEATURES/stats-data.md`，以及主工程 `weread-widget/app/src` 全部 Java 源文件；十雨桌面 `docs/03-target-architecture.md`、`docs/04-identity-removal.md`、`core/common`、`core/widget-api`、`core/render`、`core/design`、`feature/home`、`feature/widgets`、`feature/content`、`feature/settings`、`feature/files`。

侧重点差异：微读墨记的文档目标是「在第三方桌面上以无障碍悬浮窗显示自有卡片」，十雨桌面的文档目标是「以自绘 View 承载页面、组件与负一屏」。两侧的卡片、页面、组件在结构上并非同一类对象，方案以十雨桌面现有契约为主干，微读墨记的内容与版式作为输入。

## 二、两侧工程形态

### 2.1 微读墨记

应用名「微读墨记」，包名 com.inkread.weekread，当前 V0.6.0，纯 Java、零第三方依赖、不用 Gradle，构建入口为 `weread-widget/tools/build.sh`（`README_项目导览.md:7-8`、`:29-45`）。设备为阅星曈 S4，Android 11（API 30），仅 armeabi-v7a 32 位，low_ram 堆 128–256MB，屏幕 480×800、219dpi（`设备_签名_凭据.md:9-18`）。构建脚本的 SDK 版本为 min 23 / target 30（`weread-widget/tools/build.sh:221-222`），与十雨桌面 minSdk 26 / targetSdk 35（`gradle/libs.versions.toml:4-6`）不同。

主工程 `weread-widget/app/src` 的 Java 文件按包统计约 101 个（命令：`Get-ChildItem -Path D:\WorkBuddyStation\WereadMoji\weread-widget\app\src -Recurse -File -Include *.java,*.kt | ForEach-Object { ... }` 按目录聚合行数），分布为 a11y 10、core 31、feature 21、net 3、remote 12、shell 9、ui 13、update 2。行数集中在少数文件：

| 包 | 关键文件与行数 |
| --- | --- |
| feature | CardRenderer 2018、BillSection 1636、DeskPageView 1506、InsightRenderer 1199、AssetSection 948、FingerprintPoster 817、BillWallpaper 725、WeekCardView 736、DeskSettings 526、NotePickView 419、NoteExport 372、AssetPoster 339、DeskRenderer 337、BookPickView 330、InsightPageView 279、CardLayout 269、OverlayWindow 252、DeskModules 254、PricePad 227、CardInteraction 197、DeskModule 31 |
| core | NoteStore 1834、BillScheduler 778、CardPrefs 612、BillEstimator 498、BookStore 398、MenuPrefs 394、PagePrefs 385、PeriodStats 348、CardSpec 344、Bill 338、StatsStore 331、BillMoney 300、AssetRefresher 288、BgImageUtil 233、PeriodRange 231、AssetStore 205、LockPrefs 189、CoverStore 185、BillStore 181、PowerSettingsManager 180、TodoStore 150、BookStats 132、BindRefresher 124、FeatureGate 99、TodoItem 96、ProgressLog 94、CardDebug 82、NoteStats 78、RankFilter 65、MenuPrice 62、AchievementPrefs 55 |
| a11y | A11yEventRouter 803、OverlayController 830、CardContentController 662、CardA11yService 515、TomoPageGate 481、ElaHomeProbe 338、LockOverlay 336、ElauncherPageGate 318、SettingsPageProbe 276、CardVisibilityState 189 |
| shell | MainActivity 约 1690、LabPageController 1487、SettingsPageController 933、ConsoleActivity 816、SettingsActivity 439、TodoPageController 333、DeskActivity 262、HelpActivity 65、TodoActivity 39 |
| net | WereadApi 560、NoteSync 379、UpdateChecker 351 |
| remote | ShakeDetector 775、WifiTcpLink 386、HidLink 335、RemoteInjector 313、RemoteLinkManager 297、HidKeepAliveService 245、RemoteKeyService 159、HidKeymap 119、HidConst 108、RemoteProtocol 42、RemoteLink 37、RemoteRole 25 |
| ui | ShakePageView 709、ConnectPageView 417、KeyPadView 388、InkTheme 308、KeyboardPageView 304、FlipKeyView 245、NavDropView 223、FoldHintView 172、PeriodPickerView 170、CardMenuView 169、StatusChipView 161、SegTabView 148 |
| update | ApkInstaller 219、ApkProvider 67 |

职责划分：core 为存储与统计模型（StatsStore/NoteStore/BookStore/BillStore/AssetStore/CardPrefs/PeriodStats 等），net 为网关与同步（WereadApi/NoteSync），feature 为绘制与版面（CardRenderer/WeekCardView/Desk*/Insight*），shell 为页面装配（MainActivity 及三个控制器），a11y 为悬浮窗与让位判据，remote 为手机遥控台，update 为自更新。

### 2.2 十雨桌面

13 个 Gradle 模块（`settings.gradle.kts:20-32`），Kotlin 为主，Kotlin 2.1 与 AGP 8.7（`docs/03-target-architecture.md:7-16`）。组件协议为自绘视图协议而非系统 AppWidgetProvider：`WidgetView` 定义 `onSizeChanged/onConfigChanged/onRefresh/draw/onClick/onLongPress/onDestroy`（`core/widget-api/src/main/java/com/shuiyu/octoberrain/core/widget/WidgetApi.kt:17-48`），`WidgetProvider` 只有 `id/label/description/create`（同文件 `:116-123`），注册表为手写 LinkedHashMap（同文件 `:126-143`）。桌面同一时刻只启用一个组件，组件置于首页顶部通栏槽（`feature/home/src/main/java/com/shuiyu/octoberrain/feature/home/HomeView.kt:391-416`、`:419-426`）。

负一屏为模块化宿主：`NegativeModule` 契约含 `id/label/createView(context, host)/onResume/onPause`（`core/common/src/main/java/com/shuiyu/octoberrain/core/common/Contracts.kt:1089-1107`），注册表 `NegativeModuleRegistry`（同文件 `:1119-1132`），宿主 `NegativeScreen` 按配置中的模块顺序纵向排列（`feature/home/src/main/java/com/shuiyu/octoberrain/feature/home/NegativeScreen.kt:29-37`、`:74-113`），配置字段为 `NegativePageConfig.modules`（`core/common/src/main/java/com/shuiyu/octoberrain/core/common/Models.kt:85-98`，默认 `["bookshelf","negative_files"]`）。

微信读书能力现状：数据源 `ContentSource` 已含 `wereadShelf/wereadMonth/wereadHighlights/wereadPeriod/wereadNotes/wereadBill/wereadRank/wereadFingerprint`（实现见 `feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/ContentSourceImpl.kt:326-421`）；页面已有五段式 `WereadScreen`（分段枚举 `WereadSection` 见 `Contracts.kt:52-68`，路由见 `feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/ContentModule.kt:41-54`）与 `WereadCalendarScreen`；组件已有 8 个微信读书条目（`feature/widgets/src/main/java/com/shuiyu/octoberrain/feature/widgets/WidgetCatalog.kt:98-183`，注册表 `:229-249`，分组 `:252-268`）。

## 三、微读墨记可迁移对象的真实形态

### 3.1 原本的桌面卡片

桌面卡片是一枚无障碍悬浮窗内的单个自绘 View，不是系统 AppWidget，也不是多张卡片并列：

- 窗口：`feature/OverlayWindow.java:25-28` 使用 `TYPE_ACCESSIBILITY_OVERLAY`；卡片主体 368×346，位于 (56,70)，由 `core/CardSpec.java:39-49`、`:78-84` 定义；卡片主体带 `FLAG_NOT_TOUCHABLE`，触摸由八类独立子窗承接（`feature/OverlayWindow.java:42-54`、`:64-75`、`:86-97`、`:109-120`、`:141-152`、`:173-184`、`:194-205`、`:214-225`、`:240-251`）。
- 内容：`feature/WeekCardView.java:18-46` 说明同一套绘制在三处复用（桌面悬浮卡、主页整屏、设置页预览），按 `mode` 分周柱状图与月历网格两个主形态，另有本书、本记、待办三个形态由 `CardRenderer` 分派（`feature/CardRenderer.java:238-346` 的 `draw` 先画抬头再按形态分派；方法地图为 `drawHeader:348`、`drawWeekBody:568`、`drawMonthBody:694`、`drawMonthFullBody:805`、`drawMonthRank:962`、`drawBookBody:1323`、`drawNoteBody:1622`、`drawTodoBody:1532`）。
- 结构：抬头（形态名、切换图标、刷新图标）＋分隔线＋正文（图形或文字）＋底部统计行，成就行开启时整体重排（`feature/CardRenderer.java` 常量 `ACHV_*` 见其 `:67-79`）。四个形态切换由抬头点击与切换图标承接（`feature/OverlayWindow.java:86-120`）。
- 尺寸体系：字号「号」= 屏幕长边 × 0.0015，480×800 屏上 1 号 = 1.2px（`feature/WeekCardView.java:44-56`，真源在 `ui/InkTheme.java:39`、`:51-54`），正文几何以「号 × unit」与绝对像素混排（`feature/CardRenderer.java:44-129`）。

因此「原本的桌面卡片组合」在微读墨记内的实体是「一枚 368×346 的卡片，可在五种形态间切换，外挂八类触摸子窗」。它并不是多块并排的卡片集合。这一点决定了「整体组件」不是把八块组件堆在一起，而是把微读墨记的形态切换能力收进一个组件，或按固定版式并列展示其中若干块。

### 3.2 墨台

「墨台」是微读墨记的全屏页面，代码中不以「墨台」命名：全仓检索 `墨台|Motai|motai|Dashboard` 命中三千余处，全部位于 `_probe`、`_archive`、`docs`、`tasks`，主工程 `weread-widget/app/src/main/java` 内零命中。实现对应 `feature/Desk*` 与 `shell/DeskActivity.java`。

- 形态：原为第三个全屏覆盖窗，TASK-088 起改为自有 Activity（`docs/FEATURES/desk.md:3-12`、`:22-44`；`shell/DeskActivity.java:99-108` 设全屏并 `setContentView(page)`）。
- 容器：`feature/DeskPageView.java` 纯 Canvas 自绘，三态 `MODE_LIST/MODE_SETTINGS/MODE_WALLPAPER`（`:79-82`），量高、钳位、单指纵向拖动、左滑返回，无惯性（`:47-71`）；页眉由 `DeskRenderer` 绘制（337 行）。
- 模块：注册表 `feature/DeskModules.java:55-58` 为三个真模块，`bill`（阅读账单/墨单，id 见 `:109`）、`rank`（阅读资产，`:126`）、`fp`（阅读指纹，`:140`）；每个模块由 `build` 内的 `refresh` 只读本地缓存（`:105-148`）。文档模块表只列 bill 与 rank（`docs/FEATURES/desk.md:63-72`），代码比文档多出 fp，模块数以代码为准。
- 数据来源：账单读 `BillStore`（由 `core/BillScheduler.java:16-40` 在 App 启动时幂等补齐，一次补齐默认 1 次 `/readdata/detail`，`BACKFILL_MAX=4`），价格三态与合计见 `feature/BillSection.java:24-62`；资产读 `AssetStore`（`/shelf/sync` 全量书架加逐本 `/book/getprogress`，`core/AssetStore.java` 205 行，续补在 `AssetRefresher.java:1-40`）；指纹读 `res/raw/fp_paths.txt`（9,394 字节、530 行、22 条线、485 顶点）与 `NoteStore.marks`（`feature/FingerprintPoster.java:28-54`）。
- 开关与顺序：`core/PagePrefs.java` 的 `desk_order` 与 `desk_on_mask`（`docs/FEATURES/desk.md:74-77`）。

### 3.3 APP 本体页面

`shell/MainActivity.java:50-85` 记录四大标签（阅读/设置/实验室/待办，常量见 `:88-92`）与阅读页下拉五形态（本周/本月/本书/本记/洞察）加周期选择器；另有 `shell/DeskActivity.java`（墨台）、`shell/SettingsActivity.java`（439 行）、`shell/ConsoleActivity.java`（816 行，手机遥控台）、`shell/TodoActivity.java`、`shell/HelpActivity.java`。主页内容由 `feature/WeekCardView` 与 `CardRenderer` 绘制，洞察页由 `feature/InsightPageView.java` 与 `InsightRenderer.java` 承担，四个 `InsightRenderer.Section` 实现为 `AssetSection`、`BillSection`、`FingerprintPoster` 与内部 `RankSection`（`feature/InsightRenderer.java:1181`）。

### 3.4 接口与数据模型

- 网关：`net/WereadApi.java:41` 为 `https://i.weread.qq.com/api/agent/gateway`，请求头 Bearer（`:493`）；端点 `/shelf/sync`（`:132`、`:150`）、`/book/getprogress`（`:177`）、`/book/chapterinfo`（`:186`）、`/readdata/detail`（`:209`）、`/book/bestbookmarks`（`:239`）、`/user/notebooks`（`:579`）。
- 同步：`net/NoteSync.java:20-37` 三步（书索引按 hasMore/lastSort 翻完、想法预热 `/review/list/mine`、划线预热 `/book/bookmarklist`），6 小时 TTL，渲染不触发请求。
- 模型：`core/PeriodStats.java`（总时长/天数/日均/逐日/最长/偏好等）、`core/BookStats.java`、`core/NoteStats.java`、`core/Bill.java`、`core/TodoItem.java`、`core/ProgressLog.java`。
- 与十雨桌面现有模型的对应：`WereadStatsSnapshot`（`Contracts.kt:105-111`）、`WereadNoteSnapshot`（`:122-128`）、`WereadBillSnapshot`（`:148-152`）、`WereadRankEntry`（`:161-164`）、`WereadMonth`（`Models.kt:537-541`）、`WereadShelfItem`（`:527-534`）已能承载周/月/年/累计、账单、笔记、排行、书架；缺口为价格与逐本资产明细（见第五节）。

## 四、方案一 整体组件 WereadBoardWidget

### 4.1 组件槽的真实几何

十雨桌面没有「4×4 单元格组件」概念。桌面只有一个首页顶部通栏槽：槽宽为内容区左右边界之差，槽高由 `DesktopMetrics.compute` 按「先保证首页应用行数，剩余高度全部给槽」反算，下限为 1.6 行单元高度（`feature/home/src/main/java/com/shuiyu/octoberrain/feature/home/DesktopMetrics.kt:193-197`、`:371-388`）。单元高度 = 图标边长 + 标签高度 + 行间距（同文件 `:362-364`）。480×800、密度约 1.37、三列时按令牌估算图标约 95px、标签 30px、间距 11px，单元高度约 136px，槽高下限约 218px，实际上限取决于首屏应用行数，需真机量取（待确认）。

方案建议：不引入格网坐标，组件以「通栏槽内的四段式版式」实现，段高按槽高比例分配；若确需固定长宽比，可在 `DesktopConfig` 增加可选项，属于第二阶段以后的扩展。

### 4.2 版式与内容

新增 `feature/widgets/.../WereadBoardWidget.kt`，继承 `WereadCardWidget`（`feature/widgets/.../WereadCardWidget.kt:14-72`），复用三态与内容区计算：

- 段一 本周统计：标题「本周阅读」、大字号总时长、说明行（阅读天数与日均）、七柱图。绘制逻辑取自 `WereadStatsWidget.kt:49-91`，数据取 `AppServices.content.wereadPeriod(WereadStatsPeriod.WEEKLY)`（`WereadStatsWidget.kt:29`）。
- 段二 本月账单：标题「本月阅读」、总时长、环比、阅读天数。逻辑取自 `WereadBillWidget.kt:46-66`，数据 `wereadBill()`。
- 段三 最近摘录：书名、划线正文按行折行、可选想法。逻辑取自 `WereadNoteWidget.kt:46-71`，数据 `wereadNotes(1)`。
- 段四 底栏：进度最高的书一行（`wereadRank(1)`），右侧「进入」提示；整卡点击进入微信读书页。
- 段间以 1px 细线分隔（`InkColors.INK_4`），段标题统一用 `textCaption`，主数值用 `textTitle`。

槽高不足时按优先级裁段：先保段一与段三，再保段二，最后保段四；`tight` 判定沿用 `InkWidgetView` 的高度阈值（`feature/widgets/.../InkWidgetView.kt:58-59`）。

### 4.3 注册与选择页

- 在 `WidgetCatalog.kt:98-183` 同级新增条目 `weread_board`，`label="微读墨记"`、`description="本周、本月与最新摘录"`、分类 `WEREAD`、`refreshPolicy=EVENT`、`requiredCapabilities=setOf(Capability.WEREAD)`。
- `providers` 追加（`WidgetCatalog.kt:229-249`），`byCategory` 的 WEREAD 组改为只列 `weread_board` 一项（`:252-268`）。选择页按 `byCategory` 渲染（`WidgetPickerScreen.kt:39-50`），因此选择页只显示「微读墨记」一项，无需改页面代码。
- 注册由 `WidgetsModule.install` 遍历 `WidgetCatalog.providers` 自动完成（`feature/widgets/.../WidgetsModule.kt:15`）。

### 4.4 空态与未配置态

三态由 `WereadWidgetLogic.stateOf` 判定（`WereadWidgetLogic.kt:46-64`）：密钥空或开关关闭为 `UNCONFIGURED`，提示「微信读书未配置」；已配置无数据为 `EMPTY`，提示「暂无阅读数据」。整卡统一走 `drawUnavailableIfNeeded`（`WereadCardWidget.kt:44-53`），各段无数据时该段画「—」，不改变整卡高度。

### 4.5 与现有 8 个微信读书组件的处置

现状为 8 项（`WidgetCatalog.kt:236-244`），任务描述中的「七个」与代码不一致，按 8 项处置：

| 组件 | 处置 | 理由 |
| --- | --- | --- |
| weread_covers 微信读书封面 | 保留注册、从选择页隐藏 | 封面墙与整卡内容不重复，隐藏后旧配置仍可解析 |
| weread_month 微信读书月报 | 保留注册、从选择页隐藏 | 热力月历版式与整卡段二不同，留给偏好用户 |
| weread_mark 微信读书摘录 | 保留注册、从选择页隐藏 | 单条摘录为极简形态 |
| weread_note 微信读书笔记 | 删除 | 与整卡段三重复（`WereadNoteWidget.kt:46-71`） |
| weread_stats 微信读书统计 | 删除 | 与整卡段一重复（`WereadStatsWidget.kt:49-91`） |
| weread_bill 微信读书账单 | 删除 | 与整卡段二重复（`WereadBillWidget.kt:46-66`） |
| weread_rank 微信读书排行 | 删除 | 与整卡段四重复（`WereadRankWidget.kt:45-85`） |
| weread_fingerprint 阅读指纹 | 删除 | 与负一屏指纹模块重复（见第五节） |

删除注册项会让旧配置的 `widgetId` 无法创建组件，`HomeView.ensureWidget` 在创建失败时清空标识（`HomeView.kt:404-415`），界面显示「未安装组件」（`:523`）。因此删除必须同时处理配置迁移：在 `core/data` 的配置迁移逻辑中把被删 id 改写为 `weread_board`（`ConfigMigration` 现有实现待确认可扩展点，需在实施阶段核实）。

### 4.6 改造点与规模

新增 `WereadBoardWidget.kt` 约 240–300 行；`WidgetCatalog.kt` 约 +80 行、−60 行；`ConfigMigration` 约 30 行；单测 `WereadBoardLogicTest.kt` 约 120 行（段高分配、裁段顺序、空态选择）；删除 5 个组件文件共 491 行（67+87+100+117+120）。合计新增约 470–530 行、删除约 550 行。

## 五、方案二 墨台三模块入负一屏

### 5.1 模块清单与卡片粒度

墨台三个模块各对应负一屏一张卡片，理由：负一屏各模块独立构建视图、独立加载（`Contracts.kt:1089-1107`），三模块数据源与刷新节奏不同，合并会互相阻塞。

| 模块 id | 名称 | 内容 | 数据来源 | 刷新 | 开关 |
| --- | --- | --- | --- | --- | --- |
| weread_bill | 阅读账单 | 本月/本周时长、逐书时长、价格与合计、备注 | `wereadPeriod` 周与月统计 + 封面/书名；价格需新增 | 首次创建加载，30 分钟缓存，`onResume` 过期重载 | `modules` 追加 |
| weread_assets | 阅读资产 | 逐本时长、进度、收入与原价前三至五行 | 扩展后的书架快照与逐本进度 | 同上 | 同上 |
| weread_fingerprint | 阅读指纹 | 四周二十八格灰度（第一版）或几何线稿（第二版） | `wereadFingerprint` 或 `wereadPeriod(OVERALL/MONTHLY)` | 同上 | 同上 |

指纹第一版直接用现有七列四行灰度（`WereadWidgetData.kt:12-16`、`:58-61`），几何线稿版本需要重写 `FingerprintPoster` 的 817 行绘制并复刻 `fp_paths.txt` 的 485 顶点几何，列为后续阶段。

### 5.2 负一屏实现方式

每个模块按 `FilesNegativeModule` 的范式实现（`feature/files/src/main/java/com/shuiyu/octoberrain/feature/files/ui/negative/FilesNegativeModule.kt:107-158`、`:165-211`、`:334-354`）：卡面用 `InkWidgets.background` 圆角描边，标题行右侧放「全部」入口，正文按行渲染，`onResume` 从缓存取值并 `host.requestRedraw()`，点击整卡 `host.open(...)` 跳到对应页面。

落地位置：在 `feature/content` 内新增 `ui/negative/WereadBillNegativeModule.kt`、`WereadAssetsNegativeModule.kt`、`WereadFingerprintNegativeModule.kt`，在 `ContentModule.install` 注册（`feature/content/.../ContentModule.kt:28-35` 现有书架模块注册处并列追加）。纯排版与取数逻辑抽到 `WereadNegativeLogic.kt` 以便 JVM 单测。

### 5.3 开关、顺序与默认值

- 开关：`NegativeSpecs.kt` 现有两条 toggle（`:29-47`），按同一写法追加三条，分组副标题需同步（`:19-27`）。开关写入经 `applyNegativeModule` 追加到 `modules` 末尾（`:82-91`）。
- 顺序：现有设置页只提供开关，无排序控件，模块顺序即 `modules` 列表顺序（`NegativeScreen.kt:29-37`、`:74-113`）。第一版按「书架、最近图片、阅读账单、阅读资产、阅读指纹」固定；可调顺序需新增上移下移控件，列为可选扩展。
- 默认值：`NegativePageConfig.modules` 默认只含书架与图片（`Models.kt:97`），新模块默认不开启，由用户在设置中打开，避免升级后负一屏突然变长。

### 5.4 数据缺口

- 价格：微读墨记的价格链为手填价 → 笔记价格索引 → 账单落盘市场价 → 统计落盘原价（`feature/AssetSection.java:55-62`，另有 `core/BillMoney.java` 300 行、`core/MenuPrice.java` 62 行）。十雨桌面没有任何价格存储与录入界面，第一版账单卡片不显示价格与合计，仅显示时长与本书条目；价格列为独立后续项（含录入界面设计）。
- 逐本资产：微读墨记 `AssetStore` 依赖 `/shelf/sync` 全量书架（回包 462KB、1175 本，见 `core/BookStore.java:17`）加逐本 `/book/getprogress`（`core/AssetRefresher.java` 288 行）。十雨桌面现有 `WereadClient.shelf()` 只取前 40 本并补 8 本进度（`feature/content/.../data/WereadClient.kt:40-45`、`:252-279`），超时 38 秒（`ContentSourceImpl.kt:44`）。资产卡片第一版用现有 40 本快照的前若干条，全量书架与批量进度列为独立数据项（需新增分页与并发预算）。
- 指纹几何：`fp_paths.txt` 为微读墨记自有资源，来源与授权待确认；第一版不引入该资源。

### 5.5 改造点与规模

| 改动 | 文件 | 规模 |
| --- | --- | --- |
| 三个模块视图 | `feature/content/.../ui/negative/WereadBillNegativeModule.kt`、`WereadAssetsNegativeModule.kt`、`WereadFingerprintNegativeModule.kt` | 260–340、220–300、140–200 行 |
| 纯逻辑与测试 | `WereadNegativeLogic.kt` 与 3 个测试文件 | 200–260 行 |
| 注册与规格 | `ContentModule.kt` +3 行、`NegativeSpecs.kt` +70 行 | 约 75 行 |
| 数据扩展（第一版不含量） | `ContentSourceImpl.kt` 复用现有方法 | 0 |

第一版合计约 900–1,200 行；若含全量书架、批量进度与价格，另加约 600–900 行。

## 六、方案三 APP 本体内置

### 6.1 与现有页面的关系

十雨桌面已有微信读书入口，内置化不是新建一套，而是补齐形态：

- 已有：五段式页面（总览、统计、笔记、账单、排行，`Contracts.kt:52-68`，视图分派 `WereadScreen.kt:302-322`，取数 `:214-218`）、阅读月历页（`WereadCalendarScreen.kt:33-40`）、书架页与书架负一屏模块（`BookshelfNegativeModule.kt:47`）。
- 缺口：本书页、本记页（微读墨记的核心页面）、墨台（由第五节承接）、设置项扩展。

### 6.2 页面清单与落点

| 微读墨记页面 | 十雨桌面落点 | 改动 | 估算 |
| --- | --- | --- | --- |
| 阅读页 本周/本月 | `WereadSection.OVERVIEW`、`STATS` | 补热力月历视图与周期切换 | 180–260 行 |
| 本书 | 新增 `WereadSection.BOOK` | 封面、书名、作者、进度条、时长 | 140–200 行 |
| 本记 | 新增 `WereadSection.NOTE` | 划线、想法、书名、翻页与展开 | 160–220 行 |
| 洞察 | 由统计段与指纹承接 | 复用 `WereadCalendarScreen` 或统计段 | 80–120 行 |
| 墨台 | 负一屏三模块 | 见第五节 | 900–1,200 行 |
| 设置 | `BookshelfSpecs.kt` 增「微读墨记」分组 | 卡片周期、月历样式、摘录字号、成就行、指纹开关 | 180–260 行 |
| 书架 | 已有 `BookshelfScreen` | 补全量同步入口（依赖 5.4 数据项） | 60–100 行 |
| 待办 | 不内置 | 与阅读无关，需确认是否要 | 0 |
| 实验室 | 不内置 | `LabPageController` 1487 行为调试台 | 0 |
| 遥控台 | 不内置 | `ConsoleActivity` 816 行加 `remote` 包约 2,700 行，服务于手机端 | 0 |
| 更新 | 现有 `UpdateService` | 无需改动 | 0 |

`WereadSection` 增加两项后，`WereadScreen` 的分段渲染需同步扩展（`WereadScreen.kt:302-322`）与 `WereadSectionLogic.SECTIONS`（`WereadScreen.kt:154`）。负一屏书架模块的两个跳转点使用 `WereadTab(NOTES/STATS)`（`BookshelfNegativeModule.kt:206-211`），新增分段不影响既有跳转。

### 6.3 Destination 与路由

不需要新增 Destination 类型：书页与本记页走既有 `Destination.WereadTab(section)`（`Contracts.kt:445`），由 `ContentModule` 的提供者按 `is Destination.WereadTab` 分派（`ContentModule.kt:46`）。墨台三个模块的「全部」入口同样使用 `WereadTab`。若后续需要独立入口图标，可在 `SpecialEntry` 增加一项（`core/data/.../AppCatalogImpl.kt:171` 现有 `SpecialEntry.WEREAD -> Destination.Weread` 映射），不新增 Destination。

### 6.4 原工程去留

建议微读墨记工程冻结为只读参考，不并入 Gradle，不保留独立 APK 发布。理由：十雨桌面有明确的去上游身份要求，规定「以功能清单、交互规格与数据结构为输入重写全部代码，逐类实现等价行为，不搬运字节码、不复制布局文件、不复制字符串资源」（`docs/04-identity-removal.md:39`），并列出发布前检索 `astraabove/tomo/图墨/wetao` 应为零（`:45`）；微读墨记未声明开源许可（包根无 LICENSE 文件，全部 md 中检索许可、著作权、版权均无第三方许可声明，属待确认）。两侧若长期并行，密钥、缓存目录与页面分叉的维护成本会持续叠加。

### 6.5 迁移量

APP 本体（不含墨台）：约 1,000–1,500 行 Kotlin 新增，涉及 10–14 个文件（`WereadScreen.kt` 扩展、新增 `WereadBookPage.kt`、`WereadNotePage.kt`、`WereadSectionLogic` 扩展、`BookshelfSpecs.kt` 与自定义行、两个单测）。
含第五节与第四节的完整工程：新增约 2,400–3,300 行 Kotlin、文件 20–26 个；不迁入 101 个 Java 文件；删除 5 个重复组件与 491 行。

## 七、分期与验收

### 阶段一 整体组件（3–4 人日）

改动：`WereadBoardWidget.kt` 新增；`WidgetCatalog.kt` 增条目与分组收缩；`ConfigMigration` 兼容旧 id；`WereadWidgetLogic` 增加段高分配纯函数；测试 3 项。
验收：480×800 真机上组件槽高度与四段比例正确；八种旧配置（含被删 id）升级后不白屏；未配置密钥与关闭开关时显示对应文案；点击进入微信读书页；浅色与深色壁纸下细线与灰阶可辨。

### 阶段二 负一屏三模块（5–8 人日，依赖阶段一的数据层结论）

改动：三个模块文件、`WereadNegativeLogic.kt`、`ContentModule.kt`、`NegativeSpecs.kt`、`SettingsSpec.kt`（分组常量）、测试 3 项。
验收：设置中开启后负一屏出现对应卡片且顺序稳定；关闭后卡片消失、再次开启追加末尾；离线时卡片显示缓存并用「—」占位；进入负一屏不阻塞主线程；`onPause` 取消加载。

### 阶段三 APP 页面（6–9 人日，可并行于阶段二后半）

改动：`WereadSection` 增两项、`WereadScreen` 分派与分节视图、`WereadBookPage.kt`、`WereadNotePage.kt`、`BookshelfSpecs.kt` 与设置页自定义行、`WereadSectionLogic` 扩展、测试 2 项。
验收：四段页签切换不重复请求；本书页封面与进度正确；本记页翻页与展开可用；设置项修改后组件与页面同时生效；返回栈正确。

### 阶段四 收尾（2–3 人日）

改动：删除 5 个重复组件与配置迁移兜底、文档（本报告与 `docs/13` 系列编号）、集成回归。
验收：`./gradlew :app:assembleRelease` 通过；旧版本配置导入后组件不丢失；负一屏与组件同时启用时内存与刷新次数在预算内（整屏刷新 10 分钟一次，`RefreshScheduler.kt:21`）。

总工作量：约 16–24 人日；若含全量书架、批量进度与价格链，追加 6–10 人日。

## 八、未定与待确认

1. 「4×4」的确切语义。十雨桌面只有一个通栏组件槽（`DesktopMetrics.kt:371-388`），没有行列格网；若指固定长宽比或固定像素高，需要补充说明后在 `DesktopMetrics` 增加约束。
2. 「现有七个组件」与代码的 8 项不一致（`WidgetCatalog.kt:236-244`），处置表按 8 项给出。
3. 墨台模块数：文档列 bill 与 rank（`docs/FEATURES/desk.md:63-72`），代码含 fp 共 3 个（`DeskModules.java:55-58`），本方案按 3 个。
4. 待办是否内置：`TodoStore`/`TodoPageController`（约 480 行）与阅读无耦合，需决定取舍。
5. 价格数据是否保留：保留则需设计手填单价入口与合计口径（`BillSection.java:24-62`），并新增存储；否则账单与资产卡片不显示价格。
6. 阅读指纹几何版是否本期实现：需重写 817 行绘制并复刻 485 顶点资源，且该资源来源待确认。
7. 来源与许可：微读墨记未声明开源许可，全部新增代码按十雨桌面既定原则重写；若 Doow 持有该工程著作权，可在报告中补充授权说明以简化流程（待确认）。
8. 微读墨记的遥控台与实验室是否保留：遥控台服务于手机端角色，十雨桌面无对应需求；实验室为调试台，产品化无收益。

## 九、索引

- 微读墨记卡片：`D:\WorkBuddyStation\WereadMoji\weread-widget\app\src\main\java\com\inkread\weekread\feature\WeekCardView.java`、`...\feature\CardRenderer.java`、`...\feature\OverlayWindow.java`、`...\core\CardSpec.java`
- 微读墨记墨台：`...\feature\DeskPageView.java`、`...\feature\DeskModules.java`、`...\feature\DeskRenderer.java`、`...\shell\DeskActivity.java`、`docs\FEATURES\desk.md`
- 微读墨记模块内容：`...\feature\BillSection.java`、`...\feature\AssetSection.java`、`...\feature\FingerprintPoster.java`、`...\core\BillScheduler.java`
- 微读墨记页面：`...\shell\MainActivity.java`、`...\shell\SettingsPageController.java`、`...\shell\LabPageController.java`、`...\feature\InsightRenderer.java`
- 十雨桌面组件：`feature\widgets\src\main\java\com\shuiyu\octoberrain\feature\widgets\WidgetCatalog.kt`、`...\WereadCardWidget.kt`、`...\WereadStatsWidget.kt`、`...\WereadBillWidget.kt`、`...\WereadNoteWidget.kt`、`...\WereadRankWidget.kt`、`...\WereadFingerprintWidget.kt`、`...\WidgetPickerScreen.kt`
- 十雨桌面负一屏：`core\common\src\main\java\com\shuiyu\octoberrain\core\common\Contracts.kt`、`...\Models.kt`、`feature\home\src\main\java\com\shuiyu\octoberrain\feature\home\NegativeScreen.kt`、`feature\settings\src\main\java\com\shuiyu\octoberrain\feature\settings\NegativeSpecs.kt`、`feature\files\...\ui\negative\FilesNegativeModule.kt`
- 十雨桌面页面与数据：`feature\content\src\main\java\com\shuiyu\octoberrain\feature\content\ContentModule.kt`、`...\ContentSourceImpl.kt`、`...\ui\WereadScreen.kt`、`...\ui\WereadCalendarScreen.kt`、`...\data\WereadClient.kt`
- 十雨桌面设置与几何：`feature\settings\src\main\java\com\shuiyu\octoberrain\feature\settings\BookshelfSpecs.kt`、`BookshelfSettingsScreen.kt`、`feature\home\...\DesktopMetrics.kt`、`core\design\src\main\java\com\shuiyu\octoberrain\core\design\InkTokens.kt`
- 约束文档：`D:\DSHStation\OctoberLauncher\docs\03-target-architecture.md`、`docs\04-identity-removal.md`、`docs\13-inkOS对照评估.md`
