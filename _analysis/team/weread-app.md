# 微读墨记 App 页面实施记录（task-25 / W9）

负责人：weread-app。写域：`feature/weread-app/` 全部；`feature/settings` 仅入口声明；`settings.gradle.kts` 与 `app/build.gradle.kts` 各一行；`core/common` 的 Destination 与标签、`OctoberRainApp.kt` 模块名单由 lead 修改。

## 一、产出文件

新建模块 `feature/weread-app/`：

- `build.gradle.kts`：android-library + kotlin-android，命名空间 `com.shuiyu.octoberrain.feature.wereadapp`，依赖 core/common、core/design、core/render 与 `libs/weread-kernel-1.3.2-k5.jar`。
- `src/main/AndroidManifest.xml`。
- `WereadAppModule.kt`：实现 LauncherModule，install 内 `ScreenRegistry.register(WereadAppScreenProvider)`，仅响应 `Destination.WereadApp`。
- `WereadAppScreen.kt`：Screen 包装、`WereadAppTab` 枚举、`WereadAppView` 主容器；四段页签「阅读 / 设置 / 实验室 / 待办」常显，分段缓存，返回键回阅读页，外部跳转回调。
- `WereadAppPrefs.kt`：`KernelStore` 包装，读写 `filesDir/weread/kernel_prefs.json`；实验室内容开关与版本号连点计数（`VERSION_TAP_WINDOW_MS = 8000L`，容纳墨水屏刷新耗时）；`WereadAppSettings` 数据类与 `update()`；键名对齐 docs/24 6.10（card_enabled、card_pool_mask、note_card_size、switch_mode、rank_shelf_only、month_style、update_channel、upd_cloud_fallback、install_role、achv_*），纸色与字号档用自定义 `app_paper_color`/`app_body_size`。
- `WereadAppData.kt`：`KernelDataLoader` 与 `KernelStore` 封装；周期快照从偏好 `d_<mode>:<base>`/`t_<mode>:<base>` 经 `PeriodSnapshot.parseInto` 重建，缺失时回退 loader 当期；历史可切换范围由 `stats.json` 顶层 `d_<mode>:` 键解析（`KernelStore.listText` 列的是目录文件而非偏好键，不能用于此）；笔记、待办（优先 `AppServices.wereadTodos`，缺失回退磁盘）、封面。
- `WereadAppHost.kt`：同时实现 `KernelHost` 与 `DeskHost`；刻度 `max(屏宽,屏高)×0.0015`；页面跳转映射到既有 Destination；供 InsightPage 使用。
- `WereadCardForm.kt`：五形态枚举与 `PeriodCalendar` 常量映射。
- `WereadCardView.kt`：整卡全屏绘制视图。`CardState` + `CardRenderer`，`setFullScreenMetrics(w,h,0.05)` 与 `setUnit(长边×0.0015)`；按形态填充周月快照、本书进度与封面、本记池（筛选/选书）、待办；触摸点按按 `HitRegions` 分派（墨台、打开、选书、上一条、展开、导出、筛选、待办条目），筛选命中改为回调由页面弹「全部 / 只看想法」菜单；拖动滚动本记与本月，本记按行高吸附；监听器上报导出、选书、待办勾选与本记进度。
- `WereadInsightView.kt`：`InsightPage` 整页绘制。首帧 `bind()` 后用渲染器 `contentHeight` 取总高；因内核 `InsightPage.render` 把滚动参数固定为 0、只绘制首个视口，这里反射读取页面的 `renderer` 字段，`onDraw` 调 `renderer.draw(canvas,w,h,unit,offset)`；触摸落在排行分区时 `page.scrollRankBy` 滚排行内部，其余区域整页滚动（无惯性、12 像素阈值）；反射失败退回流页面 `render`。
- `ui/SegmentBar.kt`：等分页签栏，选中加粗加下划线。
- `ui/WereadReadingPage.kt`：阅读页。顶部形态下拉、周期选择器（周/月，点击移一期、长按移一年、越界禁用箭头）、刷新；内层四段页签（本周/本月/本书/洞察）；卡片区与洞察区切换；底部本记进度；选书浮层（关键字过滤，首屏限 80 行防主线程阻塞）；导出 PNG 到 `filesDir/weread/export/`。
- `ui/WereadSettingsPage.kt`：三组表单（显示、卡片、成就与联动），改动即写偏好并 `AppServices.refresh.requestPartial()` 请求桌面重绘；「卡片总开关」与「五种形态启用池」同时桥接到 `LauncherConfig`（`desktop.widgetId` 与 `desktop.wereadBoardPoolMask`），桌面整体卡片立即按新开关与形态池重绘；版本号连点 5 次展开实验室内容。
- `ui/WereadTodoPage.kt`：待办清单，新增/编辑/删除/切换完成/上移下移均走 `AppServices.wereadTodos`，未装配时显示提示。
- `ui/WereadLabPage.kt`：页签常显；未展开时显示展开说明，展开后显示数据目录文件状态、偏好快照、重开卡片、收起实验内容。
- `src/test/.../WereadCardFormTest.kt`：形态与内核常量映射、反向查找、未知形态容错，共 3 例。

修改（最小范围）：

- `feature/settings/.../WereadAppSpecs.kt`（新建）：以 `SettingsUi.section` 渲染「微读墨记」分组与「打开微读墨记」入口，动作 `router.navigate(Destination.WereadApp)`；`DesktopPrefsScreen.kt` 末尾追加 `WereadAppSpecs.render(page)`。lead 后续重构出 `LauncherPrefsScreen`，并在设置首页加了「微读墨记」卡片、在桌面偏好页加了「阅读与待办」行，两处入口均可用，本文件保留不冲突。
- `settings.gradle.kts:34`、`app/build.gradle.kts:66` 各一行模块引用。

## 二、验证结果

构建入口：`bash _tools/gradle-task.sh <tasks>`（读取 `toolchain.path`，设置 JAVA_HOME/ANDROID_HOME/GRADLE_USER_HOME 后调用 gradle.bat --offline）。

| 命令 | 结果 |
| --- | --- |
| `:feature:weread-app:compileDebugKotlin` | 通过 |
| `:feature:weread-app:ktlintCheck` | 通过（先跑 ktlintFormat 自动整理） |
| `:feature:weread-app:detekt` | 通过（类级 @Suppress 按模块既有做法抑制 TooManyFunctions 等） |
| `:feature:weread-app:testDebugUnitTest` | 通过（1 个测试类、3 例） |
| `:app:assembleDebug` | 通过 |
| `:feature:settings:ktlintCheck :feature:settings:detekt :feature:settings:testDebugUnitTest` | 通过 |
| `:core:common:testDebugUnitTest` | 通过 |

真机走查（设备 SA4B03CN2608081096X，480×800，安装 debug APK 覆盖，数据目录保留）。截图目录 `_analysis/verify/20261020-app/`：

- `step-92`：设置 → 微读墨记首屏，四段页签常显、页头内边距与「刷新」完整、周期标签紧凑「2026-W43」、阅读页内层四页签、本周整卡。
- `step-93` / `step-94` / `step-95`：周期选择器点击上一周（2026-W42，注入缓存数据 1小时0分）、长按左箭头跳上一年（2025-W43）、长按右箭头回到当前周。
- `step-96`：本记筛选菜单「全部 / 只看想法」；`step-97`/`step-99`：选书浮层首屏 80 行、无 ANR，选书后过滤到该书空态。
- `step-100`/`step-100-export-note.png`：导出长图 480×498 落地 `files/weread/export/`，拉取校验内容完整。
- `step-109`：设置页版本号连点五次展开实验室内容，显示数据目录与文件清单。
- `step-113`–`step-115`：洞察页自上而下滚动，年度视图、累计视图、读书排行、阅读画像、时长分布、笔记热力图全部可见，底部无空白。
- `step-116`：本书形态（封面占位、书名、时长、进度、选书/打开）。
- `step-117`–`step-119`：测试数据清理后重启，桌面卡片与阅读页数据正常、待办回到空态。
- `step-122`–`step-129`：设置页关闭「卡片总开关」后回桌面，整体卡片消失、图标网格上移（`step-125`）；重新开启后卡片恢复（`step-129`），验证设置项对桌面卡片即时生效。

验证用注入数据已清理：`kernel_prefs.json` 恢复并删除 `todo_list`/`todo_next_id`/版本连点键/实验室键，`stats.json` 恢复原文件，`files/weread/export` 删除。

## 三、偏差与说明

1. 纸色、正文字号档缺少既有键名，使用 `app_paper_color`、`app_body_size`；月历键已对齐 `month_style`（0 打卡 / 1 热力图）。成就目标键暂用 `achv_week_min`/`achv_month_min`，需与微读墨记侧确认。「卡片总开关」与「形态启用池」已桥接 `LauncherConfig` 的 `desktop.widgetId`/`desktop.wereadBoardPoolMask`，其余键（纸色、字号、署名、月历样式、切换动画、尺寸档、榜单只取书架、成就）当前只写内核偏好，桌面卡片若要立即响应需补配置字段或读取内核偏好，属结构性问题。
2. 设置入口未注册进 SettingsCatalog：避免修改 SettingsSpec.kt 分组常量与 SettingsSpecTest 的约束，改用直接渲染；lead 新版设置页已另有入口。
3. 主容器四段按 lead 确认口径：阅读、设置、实验室、待办（实验室页签常显，内容仍由设置页连点版本号展开）；阅读页内四段为本周、本月、本书、洞察，五档下拉为本周、本月、本书、本记、待办。
4. 内层页签只覆盖周/月/书/洞察，下拉切到本记或待办时内层高亮停在「本书」，以形态按钮文字标示当前档位；洞察态形态按钮显示「洞察」并隐藏周期选择器。
5. 周/月形态打开微信读书暂用 `weread://reading`；本书用 `weread://reading?bId=`。
6. 周期选择器可切换的历史范围来自本地已有缓存（`stats.json` 的 `d_<mode>:` 键与 `kernel_prefs` 的 `d_`/`t_` 键）；未同步过的历史周期不会出现在可切换范围。
7. 洞察页渲染依赖反射读取 `InsightPage.renderer`（k5 未公开该字段）；若内核升级调整字段名，需同步修改并保留 `page.render` 退回路径。
8. 选书浮层首屏限制 80 行（1177 本全量实例化会触发 ANR），输入关键字可缩小范围。

## 四、待办

- lead 合并 1.0.6 时带上本模块；合并后跑全量门禁与一次真机回归。
- 与 lead 确认自定义偏好键是否需要与微读墨记原版键名合并。
- 运行环境为离线 Gradle；未改版本号与签名。

## 五、周期覆盖未同步历史（m01864 二次任务）

### 5.1 改动文件

- `core/weread-sync/src/main/java/com/shuiyu/octoberrain/core/wereadsync/WereadSyncService.kt`：
  - `defaultPeriods()` 由 4 期扩为 28 期：最近 12 周、最近 12 月、最近 3 年、全时段，按由近到远排列；新增公开常量 `PERIOD_WEEK_COUNT = 12`、`PERIOD_MONTH_COUNT = 12`、`PERIOD_YEAR_COUNT = 3`。
  - `fetchStats()` 增加 `MAX_FETCH_MILLIS = 45_000` 截止保护：超过后记录 `同步周期过多，已跳过剩余 N 期` 并停止取数，保证一轮同步不超过 60 秒。
  - `billablePeriods()` 保持只筛 weekly 与 monthly，账单不随窗口扩大而增加。
- `core/weread-sync/src/test/java/com/shuiyu/octoberrain/core/wereadsync/WereadStoreTest.kt`：
  - `完整同步写入五类数据` 的断言改为 `statCount = 28`、`billCount = 24`（周月各 12 期）。
  - `默认同步范围四项` 改写为 `默认同步范围覆盖近三年`：断言 28 期、mode 分组、首项与各类末项起点（周按 7 天递减、月按月递减、年按年递减）。
- `feature/weread-app/src/main/java/com/shuiyu/octoberrain/feature/wereadapp/WereadAppData.kt`：
  - 新增窗口常量 `SYNC_WINDOW_WEEKS = 12`、`SYNC_WINDOW_MONTHS = 12`、`SYNC_WINDOW_YEARS = 3`、`TAG`。
  - 新增 `windowEarliestPeriodStart(mode)`（周回退 11 期、月回退 11 期、年回退 2 期）。
  - `earliestPeriodStart` 改为取「窗口起点、缓存最早、当前期」三者最小值，未同步的历史期也能被选中。
  - 新增 `hasPeriod(mode, baseTimeSec)` 与 `requestPeriod(mode, baseTimeSec, onFinished)`：后者调用 `AppServices.wereadSync.syncNow` 触发整轮同步（宿主只提供整轮入口），在工作线程完成并回调，不阻塞主线程。
- `feature/weread-app/src/main/java/com/shuiyu/octoberrain/feature/wereadapp/ui/WereadReadingPage.kt`：
  - 新增等待态字段 `pendingPeriodMode`、`pendingPeriodBase`、`pendingPeriodSince` 与轮询任务。
  - `shiftPeriod()` 移动后目标期无缓存时调用 `requestPeriod`；周期标签显示「同步中…」，两侧箭头禁用。
  - 每 `POLL_INTERVAL_MS = 1500` 毫秒轮询一次，缓存出现或同步结束即 `finishPendingPeriod()`：重载数据、重绘卡片、恢复标签；超过 `SYNC_TIMEOUT_MS = 120_000` 或同步结束仍无数据时收尾并提示「该周期暂无阅读数据」。
  - `refresh()` 与 `release()` 取消等待中的轮询。

### 5.2 验证

| 命令 | 结果 |
| --- | --- |
| `:core:weread-sync:testDebugUnitTest` | 通过（19 例） |
| `:feature:weread-app:ktlintCheck` | 通过（先 ktlintFormat） |
| `:feature:weread-app:detekt` | 通过 |
| `:feature:weread-app:testDebugUnitTest` | 通过 |
| `:app:assembleDebug` | 通过 |

真机走查（S4）因其他模块 ANR 阻塞：主线程栈落在 `feature/content/.../negative/WereadDeskSectionView.kt:118 onMeasure`，长按桌面空白、切换桌面布局、进入设置均触发「十雨桌面没有响应」。已向 lead 报告并请求转交负一屏负责人，同时申请临时关闭负一屏模块以完成验证。

### 5.3 偏差与待办

1. 同步宿主无单期入口，按需拉取实际执行整轮 28 期同步；若希望只取目标期，需要在 `WereadSyncHost` 增加 `syncPeriod(mode, baseTimeSec, onFinished)` 并由 lead 改 `core/common` 与 `HostWereadSync`。
2. 窗口取值在 `weread-sync` 与 `feature:weread-app` 各有一份常量，改动默认范围时需同步修改两处（模块间无依赖）。
3. 长按跳一年受窗口限制：周档最多回退 11 周、月档最多回退 11 个月，长按会落在窗口边界而非整一年前；年度形态不在 App 阅读页选择器范围内。
4. 真机验证待 ANR 修复后补做：左箭头连续回退 ≥8 周、≥6 月，缺失期「同步中」后出数据，截图存 `_analysis/verify/20261020-period/`。

## 六、周期覆盖真机验收与设置页两页签改造

### 6.1 周期覆盖真机结果（S4，lead 的 1.0.7 包 + 本轮修复）

| 验收项 | 结果 | 截图 |
| --- | --- | --- |
| 左箭头连续回退 ≥8 周 | 2026-W43 → 2026-W35，卡片显示 10小时30分、阅读 7/7 天 | `_analysis/verify/20261020-period/step-27-week-back8.png` |
| 左箭头连续回退 ≥6 月 | 2026-10 → 2026-04，卡片显示 31小时50分、打卡网格、阅读 28/30 天 | `step-28-month-back6.png` |
| 长按跳年 | 2026-04 → 2025-11（窗口下界，被 `coerceIn` 夹住） | `step-29-month-long-jump-year.png` |
| 缺失期「同步中」后出数据 | 运行中删除 W37 键后进入该周，显示「同步中…」，同步完成后显示 4小时19分、阅读 7/7 天 | `step-38-syncing-w37.png`、`step-39-after-sync-w37.png` |
| 120 秒超时提示 | 代码已实现「同步超时，该周期暂无数据」；S4 上未截到该场景 | — |

期间修复：`WereadCardView` 持有独立数据入口，同步落盘后卡片内存缓存不刷新导致「同步完成仍显示暂无记录」；在 `finishPendingPeriod` 中先 `card.reload()` 再绑定。另将 `WereadSyncService.sync` 统一切到 `Dispatchers.IO`，避免首页 onResume 触发的同步在 28 期写入时阻塞主线程。

### 6.2 设置页两页签改造（lead m02735 实测规格）

改动文件：

- `feature/weread-app/src/main/java/com/shuiyu/octoberrain/feature/wereadapp/ui/WereadSettingsPage.kt`：重写为「初始化 / 自定义」两页签；初始化页六段（本机角色单选、API Key 输入与粘贴/保存/测试、无障碍跳转、桌面卡片总开关与重新同步、怎么用、版本与更新）；自定义页六段（仅一页模式、启用墨台、刷新绑定、阅读成就提示与周/月目标、本记导出图、书籍排名）；自建复选框与单选框，整宽按钮与等宽双按钮；切换页签重置滚动。
- `feature/weread-app/src/main/java/com/shuiyu/octoberrain/feature/wereadapp/WereadAppPrefs.kt`：偏好键改为内核同名键（`install_role`、`single_page_mode`、`cb_desk_enabled`、`bind_week/month/book`、`cb_achv`、`achv_*`、`export_tpl`、`paper`、`nsize`、`note_sign`、`note_date`、`rank_shelf_only`），新增 `versionCode()`；`WereadAppSettings` 字段同步。
- `feature/weread-app/src/main/java/com/shuiyu/octoberrain/feature/wereadapp/WereadAppScreen.kt`：设置页装配 `Listener.onSaveAndBack()`，保存密钥后切回阅读页。
- `feature/weread-app/src/main/java/com/shuiyu/octoberrain/feature/wereadapp/ui/WereadLabPage.kt`：偏好快照改读新字段。

真机截图（S4）：

| 截图 | 内容 |
| --- | --- |
| `step-200-settings-init.png` | 初始化页顶部：两页签、本机角色、API Key、无障碍 |
| `step-201-settings-init2.png` | 初始化页中部：卡片总开关、重新同步、怎么用、版本 1.0.7（27）、更新通道 |
| `step-202-settings-init3.png` | 初始化页底部：云端备用更新源、检查更新 |
| `step-203-settings-custom.png` | 自定义页顶部：仅一页模式、启用墨台、刷新绑定、阅读成就 |
| `step-204-settings-custom2.png` | 自定义页底部：本记导出图、书籍排名 |
| `step-207-check-update.png` | 检查更新跳转到十雨更新页 |

交互核对：勾选「仅一页模式」写入 `single_page_mode=true`；切换本机角色写入 `install_role=1/0`；「检查更新」跳转 `Destination.Update`。

门禁：

| 命令 | 结果 |
| --- | --- |
| `:feature:weread-app:ktlintCheck` | 通过 |
| `:feature:weread-app:detekt` | 通过 |
| `:feature:weread-app:testDebugUnitTest` | 通过（3 例） |
| `:app:assembleDebug` | 通过 |

### 6.3 偏差

1. API Key 段按 lead 约定未自建保存到内核，而是写 `LauncherConfig.integrations.wereadApiKey`（与书架设置同一配置源）；「保存并返回」后切回阅读页。
2. 更新通道与云端备用更新源写 `kernel_prefs` 键并在页面说明「检查与安装由十雨桌面的更新入口统一完成」，检查更新直接跳十雨更新页。
3. 原版「桌面卡片无障碍」按钮跳系统无障碍设置；本机未授予时由系统页面提示。
4. 原版自定义页的「卡片形态池」「月历样式」「切换动画」「卡片尺寸档」不在 lead 实测截图中，未放入两页签；这些能力当前仍可通过既有设置项或内核偏好改动。


## 七、阅读页对照微读墨记（m02958 / m03039）

### 7.1 做法

1. 把微读阅读页用到的 Java 控件与依赖原样复制到 `feature/weread-app/src/main/java/com/inkread/weekread/`，保留原包名：`ui/`（PeriodPickerView、SegTabView、NavDropView、InkTheme）、`feature/`（InsightPageView、WeekCardView、CardLayout、CardRenderer、CardInteraction、NoteExport、InsightRenderer）、`core/`（PeriodRange、PeriodStats、RankFilter、BookStats、NoteStats、CardPrefs、TodoItem、NoteStore、BookStore、StatsStore、PagePrefs、FeatureGate、CardDebug、CardSpec）。
2. 新阅读页按微读 `activity_main.xml` 的 `mp_reader` 重排：主导航 SegTabView（本周▽/设置/实验室/待办）→周期行 PeriodPickerView（左箭头+日期区间（本周）+右箭头）→卡片 WeekCardView→洞察 InsightPageView→底部整宽刷新；删除四段子页签与文本周期行，形态切换交给主导航首项与 NavDropView 下拉。
3. 数据入口统一到内核：新增 `WereadMicroBridge`，把 WeekCardView 内部五处 SharedPreferences 读取（API Key、墨台开关、书架排名过滤、本记进度、本记落盘时刻）转发到十雨 `AppServices.config` 与 `WereadAppData`；周期卡片用 `PeriodStats.parse` 从 `kernel_prefs` 的 `d_<mode>:<base>` 重建后注入，本书/本记/待办用内核对象域子类（BookStats/NoteStats/TodoItem）转换。
4. 形态下拉用 NavDropView（triggerless 浮层）挂在阅读页，主导航首项点击展开；选择「足迹」切洞察页，选本周/本月/本书/本记切对应卡片形态，并同步主导航首项文案。
5. 门禁排除：`feature/weread-app/build.gradle.kts` 为 detekt 加 `exclude("**/com/inkread/weekread/**")`，为 ktlint 用 `ktlint { filter { exclude("**/com/inkread/weekread/**") } }`；新增 Kotlin 胶水代码照常受检。

### 7.2 改动文件

| 文件 | 说明 |
| --- | --- |
| `feature/weread-app/src/main/java/com/inkread/weekread/**`（24 个 Java 文件） | 复制的微读控件与依赖，仅两处最小改动：CardLayout 导航文案用字符串、WeekCardView 的 `desk_open_btn` 用字面量 |
| `feature/weread-app/src/main/java/com/inkread/weekread/feature/WeekCardView.java` | 五处内部读盘改走 `WereadMicroBridge` |
| `feature/weread-app/src/main/java/com/shuiyu/octoberrain/feature/wereadapp/WereadMicroBridge.kt` | 新增，跨层桥接 |
| `feature/weread-app/src/main/java/com/shuiyu/octoberrain/feature/wereadapp/ui/WereadReadingPage.kt` | 重写为微读阅读页骨架 |
| `feature/weread-app/src/main/java/com/shuiyu/octoberrain/feature/wereadapp/WereadAppScreen.kt` | 主导航由 SegmentBar 换成 SegTabView，首项「本周▽」联动形态文案与下拉 |
| `feature/weread-app/build.gradle.kts` | detekt 与 ktlint 目录排除 |

### 7.3 真机截图（S4，`_analysis/verify/20261020-parity/`）

| 截图 | 内容 |
| --- | --- |
| `step-301-reading-new.png` | 本周阅读页：主导航首项「本周▽」、周期行日期区间、无子页签、底部整宽刷新 |
| `step-302-form-menu.png` | 主导航首项展开形态下拉：本周/本月/本书/本记/足迹 |
| `step-303-month-form.png` | 本月形态：双箭头年度跳转、月历、排名标题 |
| `step-304b-settings-tab.png` | 设置页：主导航可见，页内两页签 |
| `step-306-todo-tab.png` | 待办页 |
| `step-309-reading-final.png` | 底部按钮与卡片位置调整后的本周页 |
| `step-310-side-by-side.png` | 与微读同页（`_analysis/verify/20261020-compare/s4-cur.png`）并排对比 |
| `step-311-insight-tab.png` | 足迹页：年度视图、累计视图、读书排行 |
| `step-312-note-form.png` | 本书形态 |
| `step-313-week-again.png` | 切回本周 |

### 7.4 门禁

| 命令 | 结果 |
| --- | --- |
| `:feature:weread-app:compileDebugKotlin :feature:weread-app:compileDebugJavaWithJavac` | 通过 |
| `:feature:weread-app:ktlintCheck` | 通过 |
| `:feature:weread-app:detekt` | 通过 |
| `:feature:weread-app:testDebugUnitTest` | 通过 |
| `:feature:weread-app:assembleDebug :app:assembleDebug` | 通过 |

### 7.5 偏差与遗留

1. SegTabView 放在 `WereadAppView`（全局主导航）而非阅读页内部：四个分段各自独立容器，需要跨容器切换；视觉位置与微读一致。
2. NavDropView 以 triggerless 模式挂在阅读页，扳机由全局主导航首项承担。
3. `WereadMicroBridge.noteProgress()` 与 `noteFetchedAt()` 暂返回 null/0（本记池进度与书目落盘时刻需要额外数据源），已在源码注释标注；不影响卡片数字、柱高与结论行。
4. 复制的 Store 类仅作编译依赖，未参与运行时读写；后续如需彻底移除可在第二阶段替换为内核对象直接构造。
5. 底部刷新按钮行按微读 XML 复刻：行内 padding 12dp/8dp、按钮高 40dp、整行宽；实测与微读并排位置一致。

## 八、本记进度与落盘时刻（noteProgress / noteFetchedAt）

### 8.1 做法

- `WereadMicroBridge.kt`：`noteProgress(context, slot)` 从 `WereadAppPrefs.store(context)` 读 `nt_total_slot<slot>`，为 0 返回 null，否则返回 `intArrayOf(taken.coerceIn(1,total), total)`；`noteFetchedAt(context, bookId)` 取 `files/weread/nt_<bookId>.json` 的 `lastModified()`，非文件返回 0；新增 `recordNoteProgress(context, slot, taken, total)` 供页面写进度。
- `WereadReadingPage.bindNoteCard()`：按 `slot = if (ideasOnly) 1 else 0` 在绑定前写入 `nt_taken_slot<slot>` / `nt_total_slot<slot>`（池为空写 0/0）。
- `WereadAppData.notes()`：内核 `KernelDataLoader.notesOfBook` 只读 `nt_index.json` 书目条目顶层的 title/author/cover，十雨索引把书信息放在 `entry.book` 子对象，导致本记显示「（未命名）」；改为装载后按 bookId 回查索引补全 title/author/cover，作者按「作者 著 译者 译」拼接。该修复同时解决本记书名/作者行空白。

### 8.2 与 lead 建议数据源的偏差

| 建议 | 实际 | 原因 |
| --- | --- | --- |
| 本记进度取 `note_books` 的 `readPct` | 页面写入 `kernel_prefs` 的 `nt_taken_slot<i>`/`nt_total_slot<i>`，桥接读取 | `nt_index.json` 根键只有 synckey/totalBookCount/noBookReviewCount/hasMore/totalNoteCount/books，条目键为 bookId/book/reviewCount/markedStatus/readingProgress/noteCount/bookmarkCount/sort；全仓与设备数据无 `note_books`、`readPct`；且「第 X / 共 N 条」的 X 是当前条目序号，只存在于页面运行态，`readPct` 语义是阅读百分比 |
| 落盘时刻取 `nt_index.fetchedAt` | 取 `nt_<bookId>.json` 的 mtime | `nt_index.json` 与 `nt_*.json` 均无 `fetchedAt` 字段（已 grep 确认） |

### 8.3 验证

| 截图（`_analysis/verify/20261020-parity/`） | 内容 |
| --- | --- |
| `step-341-note.png` | 补书名前：本记「（未命名）」、进度已显示 |
| `step-342-note-fixed.png` | 补书名后：书名、作者行（含第 15 章 · 2026-10-17）、第 1 / 共 671 条、更新于 21:14 |
| `step-343-note-side-by-side.png` | 左微读 `kernel-1.3.2/new-note.png` 右 S4：摘录、书名作者行、进度、按钮、底部刷新位置一致 |
| `step-344-note-next.png` | 点「换一条」后第 2 / 共 671 条 |

- 门禁：`:feature:weread-app:ktlintFormat ktlintCheck detekt testDebugUnitTest :app:assembleDebug` 通过。

## 九、待办页对照

- 实现：`feature/weread-app/src/main/java/com/shuiyu/octoberrain/feature/wereadapp/ui/WereadTodoPage.kt` 按 `activity_todo.xml` / `item_todo.xml` / `TodoPageController` 重写。
- 截图与差异表：`_analysis/verify/20261020-parity/todo-diff.md`；截图 `step-400`～`step-407`。
- 偏差：编辑对话框暂未实现选日期/选时间/清除（原日期时间保留）；子页签高度沿用 `tokens.topBarHeight`（S4 密度折算与微读 44dp 一致）；未额外触发桌面卡同步。

## 八、待办编辑对话框按微读实机对齐（2026-10-20 晚）

### 8.1 改动文件

| 文件 | 改动 |
| --- | --- |
| `feature/weread-app/src/main/java/com/shuiyu/octoberrain/feature/wereadapp/ui/WereadTodoPage.kt` | 编辑/新增对话框重写：日期行、时间行、选日期/选时间/清除、删除提示行；状态文字与列表次行时间 |
| `_analysis/verify/20261020-parity/export-diff.md` | 对话框与导出的对照记录 |

### 8.2 行为

- 对话框六段：标题、输入行、日期行、时间行、底部按钮行；编辑态底部「删除 + 取消 + 保存」，新增态「取消 + 保存」。
- 选日期弹 `DatePickerDialog`，选时间弹 `TimePickerDialog`，清除把对应值置空回「未设日期」「未设时间」。
- 保存写入 `AppServices.wereadTodos.add/update(id, content, dateSec, minuteOfDay)`；列表行在设置日期或时间后显示次行「M月d日 HH:mm」。
- 移除了「删除后该条不再出现在任何分组。」提示行。

### 8.3 截图（S4 480×800，`_analysis/verify/20261020-parity/`）

| 截图 | 内容 |
| --- | --- |
| `step-41x-todo-add.png` / `step-41x-todo-add-side-by-side.png` | 新增对话框及与 `micro-todo-add.png` 并排 |
| `step-41x-todo-edit.png` / `step-41x-todo-edit-side-by-side.png` | 编辑对话框及与 `micro-todo-edit.png` 并排 |
| `step-41x-date-picker.png` / `step-41x-time-picker.png` / `step-41x-todo-clear-date.png` | 日期、时间选择与清除 |
| `step-41x-todo-saved.png` / `step-41x-todo-empty.png` | 保存后列表次行时间、删除后空态 |
| `step-41x-todo-persisted.png` | 强制停止并重启后，日期时间行仍为「10月20日 23:48」 |

### 8.4 门禁

`:feature:weread-app:ktlintCheck`、`:feature:weread-app:detekt`、`:feature:weread-app:testDebugUnitTest`、`:app:assembleDebug` 全部通过。

## 九、本记导出对照（2026-10-20 晚）

### 9.1 实现

- 复用复制的微读 `feature/NoteExport.java`（原 372 行），四个样式 getter 改经 `WereadMicroBridge`：`exportPaper`←`paper`/`export_tpl`、`exportSizeTier`←`nsize`、`exportShowSign`←`note_sign`、`exportShowDate`←`note_date`，与设置页「自定义 → 本记导出图」同键联动。
- 导出：`WereadReadingPage.exportNote()` 后台线程 `NoteExport.renderAndSave(context, NoteStats)`，主线程 `NoteExport.presentResult(activity, result)`；保存到 `Pictures/微读墨记/benji_<ms>.png` 并呼起分享面板。

### 9.2 实测（S4 480×800，`_analysis/verify/20261020-parity/`）

| 变体 | 截图 | 像素 |
| --- | --- | --- |
| 手账 + 中字 + 署名/日期开 | `step-42x-export-0-hand-zhong.png` | 480×702 |
| 素白 + 中字 | `step-42x-export-1-su-bai.png` | 480×680 |
| 卡纸 + 中字 | `step-42x-export-2-ka-zhi.png` | 480×702 |
| 手账 + 小字 | `step-42x-export-3-size-small.png` | 480×496 |
| 手账 + 大字 | `step-42x-export-4-size-large.png` | 480×994 |

### 9.3 偏差

1. 导出宽度恒为 `WeekCardView.EXPORT_W=480`（微读同源常量），P4 上同为 480；高度按内容计算。
2. 条码与「墨单 · 年份-周」属墨单生成壁纸，不在本记导出；本记导出水印固定为「微读墨记 · 本记」。
3. P4 552×768 实测六变体（见 10.3），尺寸与 S4 完全一致。

## 十、待办对话框、时间步进弹窗与本记导出两机对照（2026-10-21 凌晨）

### 10.1 待办编辑/新增对话框（`feature/weread-app/src/main/java/com/shuiyu/octoberrain/feature/wereadapp/ui/WereadTodoPage.kt`）

- 新增日期行与时间行：左侧状态文字（未设/格式化日期、未设/格式化时间）+「选日期」「清除」/「选时间」「清除」；删掉原提示行。
- 选日期走系统 `DatePickerDialog`；选时间改为自绘步进弹窗。
- 保存调用 `AppServices.wereadTodos.add/update(content, dateSec, minuteOfDay)`；列表行在 `whenLabel` 非空时第二行显示灰色时间。
- 截图：`_analysis/verify/20261020-parity/step-41x-todo-add.png`、`step-41x-todo-edit.png`、`step-41x-todo-saved.png`、`step-41x-todo-empty.png`，并排图 `step-41x-todo-add-side-by-side.png`、`step-41x-todo-edit-side-by-side.png`；与微读基准差 ≤10px。

### 10.2 时间步进弹窗

- 结构对照微读 `dialog_time_pick.xml`：标题「选择时间」、时/分两行 [标签][－][数字][＋]、输入框下划线、底部提示、取消/确定。
- 行为：±1 步进、时按 24 循环、分按 60 循环；长按 450ms 后每 120ms 连发；数字可直接键入，确认时夹取 0–23 与 0–59。
- 实现期修复：小时步进误用 1440 取模导致「00 减一」显示 14，改为 24 后正常；步进按钮文字被 21dp 内边距压没，固定尺寸后清零 padding 并居中。
- 截图：`step-43x-time-stepper.png`、并排图 `step-43x-time-side-by-side.png`；实测长按 900ms 分钟减 5 次，与 450+4×120ms 节奏吻合。

### 10.3 本记导出两机实测

- S4 480×800 与 P4 552×768 各测六变体，像素尺寸逐一相同：手账中字 480×702、素白中字 480×680、卡纸中字 480×702、小字 480×496、大字 480×994、无署名日期 480×702。
- 结论：导出宽度恒为 `WeekCardView.EXPORT_W=480`，与设备分辨率无关；高度随纸张边框与字号档变化。
- 条码与「墨单 · 年份-周」属墨单生成壁纸；本记导出页脚固定为居中灰字「微读墨记 · 本记」。
- 明细见 `_analysis/verify/20261020-parity/export-diff.md`；截图 `step-42x-export-*` 与 `p4-export-*`。

## 十一、四项像素微调（2026-10-21）

- 周期行：`WereadReadingPage.kt` 挂载高度改为 `dpRound(44f)`（`TypedValue.applyDimension(...).roundToInt()`），S4 219dpi → 60px、P4 210dpi → 58px；两条基线量测写入 `docs/27-weread-page-parity.md` 第六节，截图 `step-44x-period-row-s4.png`、`step-44x-period-row-p4.png`。
- 待办对话框：`WereadTodoPage.kt` 改为实例像素属性 + 固定尺寸描边按钮；以 `micro-todo-edit.png` 为基准量测，新增/编辑两态面板 238..613、日期行 393..451、时间行 470..528、动作行 546..604，与微读基准（238..612/392..450/469..527/545..603）误差 ≤1px；截图 `step-44x-todo-add.png`、`step-44x-todo-edit.png`。
- 选记搜索行放大镜：微读 `NotePickView.java` 无 `setCompoundDrawables*`、全仓无图标绘制代码，P4 实机 `micro-notepick.png`/`micro-notepick-focused.png` 均无放大镜；lead 确认按甲处理，跳过该项并从 docs/27 残差删除「未画放大镜」。
- 遮罩：`NotePickView.java:55` 与 `BookPickView.java:49` 的 `SCRIM=0xFFD8D8D8` 为纯色 `drawRect`，无渐变；十雨 `WereadPickSheet.drawScrim` 同为纯色；「遮罩纯色无渐隐」条目从 docs/27 第九节删除。
- 选记半屏截图：S4 `step-44x-note-sheet-s4.png`、P4 `step-44x-note-sheet-p4.png`（弹层代码本轮未改，取 1.0.11 实机图）。
- 门禁：`:feature:weread-app:ktlintCheck/detekt/testDebugUnitTest` 与 `:app:assembleDebug` 通过；S4 注入的测试待办已删除（`kernel_prefs.json` 中 `todo_list`/`todo_next_id` 计数 0）。

## 十二、洞察页刷新按钮高度封顶（2026-10-21）

- `WereadReadingPage.kt` 新增 `onMeasure`：父容器以 `AT_MOST`/`UNSPECIFIED` 测量本页时限高到 `resources.displayMetrics.heightPixels`，保证底部刷新行不被内容高度挤出屏外；`EXACTLY` 时行为不变。
- 复验：S4 480×800 刷新按钮 bounds `[16,736][464,790]`；P4 552×768 bounds `[15,706][537,758]`，首屏与整页滚动到底均可见。截图 `_analysis/verify/20261020-parity/step-45x-insight-bottom-s4.png`、`step-45x-insight-bottom-p4.png`、`step-45x-insight-scrolled-p4.png`；阅读页首屏 `step-45x-reading-s4-first.png`、`step-45x-reading-p4-first.png`。
- 未采用「卡片区固定高度」方案：会破坏与微读一致的全屏卡片版面（`step-310-side-by-side.png` 已验收），改在测量层限高，视觉零变化。
- 无法在 1.0.13 debug/release 上复现缺失刷新按钮的状态，待 lead 确认证据包 versionCode。

## 十三、设置页自定义页补四段（m05609）

### 13.1 改动文件

| 文件 | 说明 |
| --- | --- |
| `feature/weread-app/src/main/java/com/shuiyu/octoberrain/feature/wereadapp/WereadAppPrefs.kt` | 设置快照新增 `switchMode`，`settings()` 读 `switch_mode`、`update()` 写回；`KEY_SWITCH_MODE` 注释改为卡片切换方式 |
| `feature/weread-app/src/main/java/com/shuiyu/octoberrain/feature/wereadapp/ui/WereadSettingsPage.kt` | 自定义页在阅读成就提示之后、本记导出图之前补四段：本月呈现、本记字号（卡片）、卡片切换方式、桌面显示的卡片 |
| `feature/weread-app/src/main/java/com/shuiyu/octoberrain/feature/wereadapp/ui/WereadReadingPage.kt` | 形态下拉按 `card_pool_mask` 过滤形态项，末项「足迹」保留；切换方式仅写偏好 |

### 13.2 行为

- 本月呈现：写入 `month_style`（0 打卡、1 热力图），卡片读取侧 `WereadCardView` 已有 `setMonthHeatmap` 注入。
- 本记字号：滑块范围 12..24，数值显示「N 号 · x.xpx」；写入 `note_card_size`，阅读页 `card.setNoteCardSize` 与内核同键读取。
- 卡片切换方式：写入 `switch_mode`（0 循环、1 列表），本轮不做交互行为。
- 形态池：五个勾选写 `card_pool_mask` 低五位，全清时提示「至少要保留一张卡片」并保持原值；阅读页主导航下拉按掩码过滤形态项。

### 13.3 真机验证

| 设备 | 截图 | 内容 |
| --- | --- | --- |
| S4 480×800 | `step-46x-custom-top.png` | 自定义页顶部：仅一页模式、启用墨台、刷新绑定、阅读成就提示 |
| S4 480×800 | `step-46x-custom-bottom.png` | 四段：本月呈现、本记字号（17 号 · 20.4px）、卡片切换方式、桌面显示的五张卡片 |
| S4 480×800 | `step-46x-custom-persist.png` | 重启后保留：热力图、20 号 · 24.0px、列表模式 |
| S4 480×800 | `step-46x-custom-pool.png` | 取消「本月」后掩码 29，勾选态保持 |
| S4 480×800 | `step-46x-nav-filter.png` | 形态池过滤后下拉只剩 本周/本书/本记/足迹 |
| P4 552×768 | `step-46x-custom-p4-top.png`、`step-46x-custom-p4-month.png`、`step-46x-custom-p4-mid.png`、`step-46x-custom-p4-bottom.png` | 自定义页顶部、四段与底部导出/排名完整滚动 |
| 两机并排 | `step-46x-custom-side-s4.png`、`step-46x-custom-side-p4.png` | 左微读 `orig-custom-2.png`，右十雨对应页 |

### 13.4 偏差

1. 微读 `orig-custom-2.png` 只覆盖自定义页底部（导出图与排名），四段本体无同屏微读截图；按源码 `inc_card_custom.xml` 的控件顺序、文案与取值范围实现。
2. 本记字号的 px 值按「号 × 长边 × 0.0015」实时计算，S4 显示 17 号 · 20.4px，P4 显示 17 号 · 19.6px（屏幕长边不同），与微读按宿主 unit 计算一致。
3. 切换模式仅写键，未改变主导航下拉的展开方式；形态池过滤已生效。
