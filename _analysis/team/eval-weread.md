# 微信读书能力融合评估：WereadMoji 与 OctoberLauncher

## 一、评估范围与材料

评估对象为 D:\WorkBuddyStation\WereadMoji（应用名微读墨记，包名 com.inkread.weekread，V0.6.0，纯 Java、零第三方依赖，构建入口 weread-widget/tools/build.sh，见 README_项目导览.md:7-8）与 D:\DSHStation\OctoberLauncher（包名 com.shuiyu.octoberrain，13 个 Gradle 模块，Kotlin 为主）。评估方式为只读源码、文档与构建脚本的交叉核对，未执行构建，未修改两侧任何文件。凡未能从源码或文档读到的内容，均在文中标注「待确认」。

## 二、工程形态对比

WereadMoji 为单模块 Java 工程，主工程 108 个 .java 文件位于 weread-widget/app/src/main/java。技术栈限定为纯 Java、无 Kotlin、无 AndroidX、无第三方依赖（docs/00_项目宪法.md:9-15），不用 Gradle，改用手写 javac + d8 + aapt2 + zipalign + apksigner 的构建脚本（tools/build.sh:3），理由为设备仅支持 32 位 armeabi-v7a、堆上限 128–256MB（docs/00_项目宪法.md:17-19）。呈现方式唯一：无障碍悬浮窗（docs/00_项目宪法.md:46-52），因为两款设备桌面均不支持标准 AppWidget（docs/04_桌面让位判据.md:9-12）。目标屏幕为 480×800 px @219dpi。

OctoberLauncher 为 Kotlin 多模块工程，settings.gradle.kts:20-32 注册 13 个模块（app、core 六件、feature 五件、service），compileSdk 35 / minSdk 26 / targetSdk 35（gradle/libs.versions.toml:4-6），release 开启 R8 与资源压缩（app/build.gradle.kts:18-27）。桌面与页面均自绘 View，组件协议为自研 WidgetView 与 WidgetProvider（core/widget-api/src/main/java/com/shuiyu/octoberrain/core/widget/WidgetApi.kt:17-123），组件由宿主绘制到同一个 Canvas。设备适配以 480×800 为最小支持形态（docs/05-device-adaptation.md:13），尺寸以 dp 与 sp 表达（docs/05-device-adaptation.md:25-29）。

两侧在呈现载体、坐标体系、配置存储与构建方式上均不同：WereadMoji 是覆盖在他人桌面之上的悬浮窗，OctoberLauncher 是桌面本体。

## 三、能力对照

### 3.1 书架同步

WereadMoji 调用 /shelf/sync 取全量书架，回包 462KB、实测 1175 本（weread-widget/app/src/main/java/com/inkread/weekread/core/BookStore.java:17,22-27），压缩为两份落盘文件：shelf.json 只保留最近读过的 200 本及 bookId/title/author/readUpdateTime（BookStore.java:22-27,35），shelf_ids.json 保存全部 bookId，约 15KB（BookStore.java:184）。有效期由 SHELF_TTL_MS 控制，六小时内视为新鲜（BookStore.java:110-111）；文件位于 getFilesDir()（BookStore.java:255,371）。资产续补复用同一份书架快照（core/AssetRefresher.java:168-187），并逐本补 /book/getprogress（feature/AssetSection.java:44-47）。

OctoberLauncher 同样调用 /shelf/sync（feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/data/WereadClient.kt:75），随后对前 8 本补 /book/getprogress，并发 2、总超时 20 秒（WereadClient.kt:40-42,252-279），书架条目携带 0–1 进度与封面位置（core/common/src/main/java/com/shuiyu/octoberrain/core/common/Models.kt:527-534）。结果仅存内存，30 分钟失效（WereadClient.kt:36,56-61），上层再截取 30 条（feature/content/ContentSourceImpl.kt:58,61）。

差异三处：持久化（一侧落盘六小时，另一侧仅内存三十分钟）；容量（一侧全量 id 加最近 200 本，另一侧 30 条）；进度覆盖（一侧逐本续补，另一侧前 8 本）。

### 3.2 阅读进度

WereadMoji 单本进度取自 /book/getprogress（net/WereadApi.java:177,387），本书形态绘制封面与进度（feature/CardRenderer.java:118-124）；另有 core/ProgressLog.java 承担进度记录，字段未读，待确认。

OctoberLauncher 在书架同步后附加进度，解析兼容 readingProgress.progress、book.readingProgress.progress 等四种结构并按百分比折算（WereadClient.kt:298-307）；feature/widgets/src/main/java/com/shuiyu/octoberrain/feature/widgets/ReadingWidget.kt:68-85 绘制进度条，:90-97 按来源跳转；读数快照由书架首本拼装，今日分钟恒为 0（ContentSourceImpl.kt:319-334，:331）。

差异：一侧有逐本进度与进度记录，另一侧进度限于书架前 8 本，且当日分钟数无数据来源。

### 3.3 划线笔记

WereadMoji 由 core/NoteStore.java 维护四类文件（NoteStore.java:20-49）：nt_index.json 为按最近读过排序的书索引，nt_<bookId>.json 为划线永久缓存，ni_<bookId>.json 为想法与点评，带六小时有效期，nt_batch*.json 与 SharedPreferences 保存抽取状态；索引按文件指纹判失效，按需抽样 BATCH=40、IDEA_EVERY=8、BOOK_CACHE=8、RECENT_HEAD=6（NoteStore.java:62-71）。同步分三步：/user/notebooks 拉全量索引、/review/list/mine 预热想法、/book/bookmarklist 全量替换划线（net/NoteSync.java:20-37）；在途去重与五分钟退避见 NoteSync.java:84-105。展示形态为本记，含两段式滚动状态回写（CardRenderer.java:17-31），「换一条」由 a11y/CardContentController.java:73-90 处理。

OctoberLauncher 仅取划线：/user/notebooks count=10，取前 3 本 /book/bookmarklist，解析 markText 后按时间降序（WereadClient.kt:109-154，:115,:129,:134）；模型为三个字段（Models.kt:544-548）；组件只展示第一条（WereadMarkWidget.kt:20-27）。

差异：想法与点评、按书索引、本地持久化、换一条交互均为 WereadMoji 独有；OctoberLauncher 侧仅一条且不落盘。

### 3.4 书评与想读

两侧均无。WereadMoji 全库检索「书评」「想读」「推荐」未命中实现，唯一相关调用为 NoteSync.java:25 的 /review/list/mine，属想法预热，是否含公开书评待确认。OctoberLauncher 全库检索未命中微信读书书评或想读实现。

### 3.5 阅读统计

WereadMoji 使用 /readdata/detail 四种 mode：weekly、monthly、annually、overall（docs/FEATURES/stats-data.md:12-17）。缓存按 mode 与周期起点分键（core/StatsStore.java:12-23），轮转池上限六期（StatsStore.java:33），年度与累计独立分段（docs/FEATURES/stats-data.md:48-49）。周期模型含总时长、阅读天数、日均、环比、31 日分布、最长单次、偏好分类与作者、阅读时段、奖章（docs/FEATURES/stats-data.md:25-42）；缺字段的老缓存按空集合处理（同上:54-55）。

OctoberLauncher 仅月：本月与上月各一次 /readdata/detail，mode=monthly，携带 month、beginTime、endTime（WereadClient.kt:89-106,311-322）；模型三字段 monthMinutes、lastMonthMinutes、days（Models.kt:537-541）；页面展示月总分钟、日分布与上月对比（feature/content/.../ui/WereadScreen.kt:134 起）。

差异：周、年、累计四种周期，环比、偏好、奖章、最长单次均缺。

### 3.6 热力图与月历

WereadMoji 周形态为七柱条形（CardRenderer.java:17-31 的 drawWeekBody），月形态为月历网格，全屏档单元格字号 52（CardRenderer.java:83-90），另有月排名段（CardRenderer.java:96-102）。

OctoberLauncher 的 HeatMonthWidget 仅当月，7 列 6 行，按分钟数分四档灰（HeatMonthWidget.kt:106,117-139,155-158），首日按周一为第 0 列计算偏移；无周视图、无月历数字、无图例、点击无动作。

差异：周视图、月历数字与月排名为 WereadMoji 独有；月热力方块为两侧共有。

### 3.7 推荐书单

两侧均无（检索结果同 3.4）。

### 3.8 划线便签组件

WereadMoji 本记形态为两段式，可换一条、可切字号（CardContentController.java:73-90 的 applyNoteFont），信息含书名、摘录、想法。

OctoberLauncher 的 WereadMarkWidget 固定展示最新一条与书名，摘录按行折行并以省略号结尾（WereadMarkWidget.kt:12-14），取数只一条（:20-27），不显示时间，无切换。

### 3.9 封面墙

WereadMoji 无封面墙，仅在本书形态绘制单本封面（CardRenderer.java:118-124，BOOK_COVER_W=90 对应 108px）；feature/AssetSection.java:38-64 是「阅读资产」书目行，不是封面网格。

OctoberLauncher 的 CoversWidget 三张叠放（CoversWidget.kt:66-71，COVER_COUNT=3 见 :170），封面缓存于 cacheDir/weread_covers（WereadClient.kt:410-440；ContentPaths.kt:57-60），加载时按目标尺寸采样解码（CoversWidget.kt:140-153），销毁时回收位图（:155-161），点击跳转微信读书（:163-166）。

差异：封面墙为 OctoberLauncher 独有。

### 3.10 登录态维持

WereadMoji 密钥存于 SharedPreferences 文件 cfg 的 api_key（core/StatsStore.java:28,36），只由调试入口注入，即 adb am start 携带 --es api_key（shell/MainActivity.java:553,561），入口受 FLAG_DEBUGGABLE 门控（docs/00_项目宪法.md:21-36）；更换密钥时 wipePersonalData 清理缓存与笔记（StatsStore.java:86-93）。

OctoberLauncher 配置项 integrations.wereadApiKey 默认取内置常量（Models.kt:10,275-293），读取时为空则回退内置值（WereadClient.kt:165-166），关闭开关时返回 null（:169-172）；配置存于 SharedPreferences 的 launcher_config（core/data/JsonConfigStore.kt:36,120）；401 与 errcode -2012 判为密钥无效（WereadClient.kt:454-496）；invalidate 仅清内存（WereadClient.kt:56-61；ContentSourceImpl.kt:170-177）。

差异：一侧无内置密钥、依赖外部注入，另一侧内置公共密钥并可覆盖；一侧换密钥清空个人数据，另一侧只清内存。

## 四、数据与凭据

密钥来源与配置键：WereadMoji 为 SharedPreferences 的 cfg/api_key（StatsStore.java:28,36），无配置文件层级；OctoberLauncher 为 launcher_config 中的 LauncherConfig.integrations.wereadApiKey（Models.kt:275-293），默认值在 Models.kt:10。

缓存目录：WereadMoji 全部落在 getFilesDir() 下的多份 JSON（BookStore.java:255,371；core/NoteStore.java:1886），不依赖外部存储；OctoberLauncher 封面落在 cacheDir/weread_covers（ContentPaths.kt:57-60；WereadClient.kt:410-440），月度统计与划线仅在内存，feature 目录内除封面下载与设置页写入外未见其他文件写入（全目录检索仅命中两处 FileOutputStream，均与微信读书无关）。

数据模型：WereadMoji 的周期模型为 13 个字段（docs/FEATURES/stats-data.md:25-42），笔记索引为 bookId、title、author、cover、noteCount、reviewCount、sort（NoteStore.java:22-24）；OctoberLauncher 的书架项六字段、月份三字段、划线三字段（Models.kt:527-548）。

统一方式候选三条：

其一，共用一套配置与缓存。保留 OctoberLauncher 的 launcher_config 作唯一配置源，缓存统一存放于 cacheDir 下的 weread 子目录并按域分目录（shelf、detail、notes、covers），改动集中在 ContentPaths.kt 与新增缓存类。

其二，按域切分缓存键。统计沿用 mode 与周期起点分键与六期轮转（docs/FEATURES/stats-data.md:46-50），笔记沿用 nt_ 与 ni_ 双文件与六小时有效期（NoteStore.java:20-49），书架沿用全量 id 与精简快照双文件（BookStore.java:22-27,184）。该方式把两侧行为差异显式化，便于逐域验收。

其三，迁移旧数据。不可行：WereadMoji 数据位于 com.inkread.weekread 私有目录，设备无 root（设备_签名_凭据.md:108-111），跨应用读取受系统隔离；只能在 OctoberLauncher 首次运行时重新拉取并重建缓存。

刷新策略差异：WereadMoji 在启动时幂等补账，不引入 AlarmManager（core/BillScheduler.java:19-23），卡片事件驱动且最小间隔 5 秒、在途闸门 30 秒（a11y/CardContentController.java:40-56），笔记六小时有效期与五分钟退避（NoteSync.java:84-105），书架六小时有效期（BookStore.java:110-111）；OctoberLauncher 数据三十分钟内存有效期（WereadClient.kt:36），refreshAll 先清缓存再请求局部刷新（ContentSourceImpl.kt:312-316），整屏十分钟整刷（core/render/RefreshScheduler.kt:21,34）。融合方向为把单一三十分钟有效期换成分层有效期（书架六小时、统计按期、笔记六小时、封面七天，封面七天见 WereadClient.kt:37）。

## 五、融合方式候选

### 候选一 源码并入 feature:content

改造点：包名替换；剥离 a11y 与 shell 依赖；InkTheme 与 CardSpec 的绝对像素换为 tokens 与组件尺寸；SharedPreferences 换 ConfigStore；卡片绘制改为 WidgetView.draw。

代码量：主工程 108 个文件，数据相关约 20 个（WereadApi 28KB、NoteStore 96KB、StatsStore 17KB、BookStore 18KB、CardPrefs 40KB、PeriodStats 18KB、BillScheduler 56KB、NoteSync 23KB、AssetRefresher 与 Bill 系列），绘制相关约 8 个（CardRenderer 125KB、WeekCardView 36KB、DeskPageView 77KB、BillSection 89KB、AssetSection 54KB、InsightRenderer 64KB、FingerprintPoster 43KB、InkTheme 18KB），合计约 600KB Java。

构建与依赖代价：Gradle 需为该模块加 Java 源集；无新增第三方依赖（仅 org.json，系统内置）；minSdk 23 与 26 不冲突；R8 对无反射代码影响有限，是否使用反射待确认。导入后 480×800 绝对坐标仍需整体换算，工作量不会因并入源码而减少。

能力上限：可保留全部形态与交互，但悬浮窗的触摸窗划分与让位判据在桌面本体中失去意义（docs/04_桌面让位判据.md:9-12）。

### 候选二 双 APK 互调

改造点：WereadMoji 暴露 ContentProvider 或前台 Binder 服务；OctoberLauncher 新增跨进程数据源实现 ContentSource 子集；约定共享密钥与缓存目录。

代码量：契约与两端适配 400–700 行，另需保活与数据一致性处理。

代价：两套构建、两套缓存、两套刷新；用户需安装两个应用；组件仍受悬浮窗与桌面图标让位约束。

能力上限：视觉可保留，交互仍为悬浮窗，无法与桌面页面融合。

### 候选三 只迁移独有能力为组件（推荐）

改造点：在 feature:content 扩展数据层（三个接口、多 mode 统计、磁盘缓存、模型字段），在 feature:widgets 新增按 WidgetApi 实现的组件与页面，不引入任何 Java 源码。

代码量：见第八节，约 2000–3100 行 Kotlin，新增 6–9 个文件，修改 6–8 个文件。

代价：无新模块、无新依赖，构建与身份要求不变；视觉按规格重绘，像素一致性需逐屏验收。

能力上限：受组件尺寸与家页槽位约束，全屏月历与指纹适合做页面，周柱状图、本记、账单、月排名适合做组件。

推荐候选三，理由四条：与 docs/04-identity-removal.md:39 的重写原则一致；沿用既有 WidgetView 生命周期（WidgetApi.kt:17-48）与 InkWidgetView（InkWidgetView.kt:29-172），数据加载与绘制已分离；避开了 Java 与 Kotlin 混编与包名迁移；维护面只落在 feature 模块内的文件。候选一作为授权明确且要求像素级一致时的备选，候选二仅在需要保留独立发行时考虑。

## 六、渲染与组件衔接

WereadMoji 绘制代码依赖三类外部条件：

绝对像素几何。屏幕 480×800 与卡片窗口 56/424/70/416（core/CardSpec.java:39-49），宽 368、高 346（:78-84），触摸窗按常量划分（:63-76）。

「号 × 单位」排版。UNIT_RATIO=0.0015f，unitPx 取屏幕长边比例（ui/InkTheme.java:38-39），480×800 上 1 号约为 1.2px；字号以「号」为单位（CardRenderer.java:44-53）。

灰阶常量。墨水屏使用 MONO 一套（InkTheme.java:126-140），卡片内另有 INK、GRAY、LIGHT（CardRenderer.java:39-41）。

OctoberLauncher 组件侧的对应机制：

基类 BaseWidgetView 缓存宽高，按 DesignRuntime.tokens 取间距与字号，final draw 在尺寸变化时转发 onSizeChanged，其余情况转发 onDraw（WidgetApi.kt:66-109）；InkWidgetView 承担协程加载与 requestDraw（InkWidgetView.kt:29-120）；绘制辅助为 InkDraw（InkWidgetView.kt:183-276）。

尺寸来自 onSizeChanged(width,height)（WidgetApi.kt:20），无常量窗口；点击由宿主传入坐标 onClick(x,y)（WidgetApi.kt:32），无独立触摸窗。

迁入改动量估算：

几何改为相对布局，以宽高与 tokens.gap 计算，每个形态约 10–20 处；每形态 300–600 行重写（CardRenderer 12.5 万字符约三到四千行，含六种形态与公共小件）。

排版以 tokens 的 dp 与 sp 替换 unitPx，约 20 个字号常量逐一映射，行高与折行改用 InkDraw.wrap 与 lineHeight。

色彩把 INK、GRAY、LIGHT 映射到 InkColors 四级灰，与正文、次要、弱化三类语义一一对应。

组件协议由 onClick 接收坐标，原触摸窗常量可删；刷新由 onRefresh 与 RefreshScheduler 驱动（RefreshScheduler.kt:21,87-95），无需自行计时。

位图处理：core/render 提供 BitmapPipe.kt、Ditherer.kt、InkCanvas.kt，feature 目录内未检索到 BitmapPipe 与 Ditherer 的引用，现有微信读书组件对封面位图作原位解码（CoversWidget.kt:140-153）；新增组件是否接入抖动管线需单独决定。

尺寸上限：月历七列六行约需 300×260dp 见方，指纹需整屏；家页槽位能否容纳整屏组件待确认。

## 七、重复建设与取舍

保留 OctoberLauncher 一套的理由（逐文件）：

data/WereadClient.kt：唯一网关封装，含 401 与 errcode 判定、封面缓存、深链与超时降级（:454-496,:410-440,:448），扩展成本低于重写。

data/ContentPaths.kt：存储位置与权限读取集中（:22-60），继续作唯一入口。

ContentSourceImpl.kt：缓存、超时包裹与来源开关齐备（:102-131,:170-199,:342-357），扩展方法即可。

core/common/Models.kt：三个模型字段清晰（:527-548），需扩周期、笔记条数、封面来源。

core/common/Contracts.kt 的 ContentSource（:51-81）：页面与组件共用的取数口，扩展优于另立接口。

feature/widgets 的四个组件（WereadMarkWidget、CoversWidget、HeatMonthWidget、ReadingWidget）：作为风格与交互基线，扩展而非替换。

core/data/JsonConfigStore.kt 与 Models.kt 的 IntegrationsConfig（:275-293）：唯一配置源。

core/render/RefreshScheduler.kt：整刷时机已具备，融合后无需自建计时。

可在融合中降级或不迁入的部分（位于 WereadMoji 侧）：

a11y 包五个文件（OverlayController 45KB、CardA11yService 30KB、CardContentController 36KB、A11yEventRouter 48KB、ElauncherPageGate）：桌面本体绘制后，悬浮窗、让位判据、无障碍事件路由均无对应位置（docs/04_桌面让位判据.md:9-12；docs/00_项目宪法.md:46-52）。

shell 包（MainActivity 93KB、LabPageController 87KB、SettingsPageController 53KB）：由 OctoberLauncher 的 Screen 体系替代。

ui/InkTheme.java：由 core:design 的 InkColors、InkTokens、DesignRuntime 替代。

core/CardSpec.java：绝对像素规格由组件尺寸与 dp 换算替代。

core/CardPrefs.java 的形态与开关项：映射为 IntegrationsConfig 字段与组件参数，不迁入实现。

遥控台 remote 包与 update 包：职责未核读，暂不纳入首期，待确认。

## 八、分期与工作量

阶段一 数据层（2–3 人日，700–1000 行）

feature/content/.../data/WereadClient.kt：新增 /review/list/mine、/readdata/detail 的 weekly 与 annually 与 overall、/book/bestbookmarks，抽公共分页与错误处理，约 250–350 行。

新增 feature/content/.../data/WereadCache.kt：磁盘缓存，按 mode 与周期起点分键，轮转六期，笔记六小时有效期，约 200–300 行。

core/common/Models.kt：新增 PeriodStat、NoteItem、BillItem，约 80–120 行。

core/common/Contracts.kt 与 ContentSourceImpl.kt：新增 wereadPeriodStat、wereadNotes、wereadBills 等方法，约 120–180 行。

阶段二 组件（3–4 人日，900–1400 行）

HeatMonthWidget.kt：增加周视图与月历数字，约 150–250 行。

新增 NoteCardWidget.kt：划线、想法、换一条、字号切换，约 200–280 行。

新增 StatsWidget.kt：周月年累计切换与柱状图，约 250–350 行。

新增 BillWidget.kt 与 RankWidget.kt，约 200–300 行。

新增 FingerprintWidget.kt 或对应页面，约 150–250 行；几何数据来源待确认。

WidgetCatalog.kt：注册与文案，约 40–60 行。

阶段三 页面与设置（2–3 人日，400–700 行）

WereadScreen.kt：统计分段、账单与月排名入口，约 200–300 行。

新增 WereadStatsScreen.kt：全屏月历与年度视图，约 150–250 行。

设置项：刷新间隔、周期、组件参数，约 50–100 行。

阶段四 验证（2 人日）

480×800 像素对照、墨色与暗色主题、刷新与并发、身份检索（docs/04-identity-removal.md:45）、启动耗时与内存。

合计约 2000–3100 行 Kotlin，约 9–12 人日；单人实施约 2–3 周。

## 九、主要障碍

来源与许可。WereadMoji 包根无 LICENSE、COPYING、NOTICE 文件，全部文档未检索到开源许可声明或第三方代码来源（docs/00_项目宪法.md 只述技术栈与不采用的工程）；docs/04-identity-removal.md:39 要求按功能清单与数据结构重写、不搬运字节码与字符串。并入源码与该原则冲突，且授权状态待确认。另需注意 WereadMoji 文档与脚本中出现明文接口密钥（设备_签名_凭据.md:108-111），融合时不得引入。

身份隔离。OctoberLauncher 对上游身份有零残留要求（docs/04-identity-removal.md:25,45）。迁入 WereadMoji 的类名、字符串或布局会带入第二套来源标识；按规格重写可规避，验收时需加检索。

minSdk 与依赖。WereadMoji 为 minSdk 23、targetSdk 30、编译平台 android-33（tools/build.sh:221-222），OctoberLauncher 为 minSdk 26、targetSdk 35、compileSdk 35（gradle/libs.versions.toml:4-6）。向下兼容不构成问题，差异集中在构建工具（手工 javac 与 Gradle 13 模块）与 R8 处理。

坐标与适配。WereadMoji 全量使用 480×800 绝对像素与 1.2px 单位（CardSpec.java:39-49；InkTheme.java:38-39），OctoberLauncher 使用 dp 与 sp 与三档屏宽（docs/05-device-adaptation.md:13,25-29）。迁移需整体换算并逐屏比对。

并行维护与共享密钥。两应用同时存在时同一密钥调用频率叠加（WereadMoji 五分钟节流、OctoberLauncher 三十分钟有效期），限流与配额行为待确认；两套缓存使同一账号数据各存一份。

接口稳定性。两侧均调用 /api/agent/gateway（net/WereadApi.java:41；WereadClient.kt:34），属公开接口，字段与 errcode 可能变化；WereadMoji 对缺字段的容错处理（docs/FEATURES/stats-data.md:54-55）值得吸收。

## 十、需要先决定的问题

1. WereadMoji 的授权状态与源码参考边界。
2. 融合后是否继续发布 WereadMoji 独立版本。
3. 组件尺寸上限与家页槽位能力，决定月历与指纹做成组件还是页面。
4. 密钥策略：沿用内置公共密钥或要求用户填写自有密钥；内置密钥的来源与可用期待确认。
5. 首期迁移的独有能力范围与顺序。

## 十一、索引

WereadMoji：docs/00_项目宪法.md、docs/02_架构.md、docs/04_桌面让位判据.md、docs/FEATURES/stats-data.md、core/BookStore.java、core/StatsStore.java、core/NoteStore.java、core/BillScheduler.java、core/CardSpec.java、net/WereadApi.java、net/NoteSync.java、feature/CardRenderer.java、feature/WeekCardView.java、feature/FingerprintPoster.java、feature/AssetSection.java、ui/InkTheme.java、a11y/CardContentController.java、tools/build.sh。

OctoberLauncher：core/common/Models.kt、core/common/Contracts.kt、core/widget-api/WidgetApi.kt、core/render/RefreshScheduler.kt、core/data/JsonConfigStore.kt、feature/content/data/WereadClient.kt、feature/content/ContentSourceImpl.kt、feature/content/data/ContentPaths.kt、feature/content/ui/WereadScreen.kt、feature/content/ui/BookshelfNegativeModule.kt、feature/widgets 下四个微信读书组件、docs/03-target-architecture.md、docs/04-identity-removal.md、docs/05-device-adaptation.md、docs/13-inkOS对照评估.md。
