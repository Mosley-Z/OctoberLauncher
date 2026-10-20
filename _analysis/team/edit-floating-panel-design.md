# 整理模式桌面布局悬浮面板设计

本设计只读代码与配置，未修改工程源码、`VERSION` 与 `CHANGELOG.md`。报告给出四种候选项、对比表、推荐方案、手势冲突点与待定项，供 Doow 拍板后进入实现。

## 一、需求与范围

进入整理模式后，在小组件的位置出现一块悬浮面板，集中提供「设置 → 桌面偏好 → 桌面与布局」中的列数、行数、间距、尺寸四项调整；面板在翻页后仍然存在，整理模式期间持续有效。本设计覆盖面板的位置与尺寸计算、控件形态、数值范围、即时预览、撤销与恢复、翻页保活、退出状态、误触防护、墨水屏刷新代价、实现位置与测试拆分，不包含代码。

## 二、当前实现事实

### 整理模式的进入与退出

`feature/home/src/main/java/com/shuiyu/octoberrain/feature/home/HomeView.kt:1111` 的 `enterEditMode(index)` 只做四件事：置 `editing = true`、记录 `dragIndex` 与 `pressedIndex`、复位 `dragMoved`、调用 `invalidate()` 与 `requestPartial()`。`HomeView.kt:1121` 的 `exitEditMode()` 对称复位这些字段。

进入渠道有两处。`HomeView.kt:111` 的 `enterEditRunnable` 在按下图标后按 `editHoldMs()` 计时，到点置 `editHoldTriggered = true` 并进入整理模式；`HomeView.kt:1189` 的单条菜单与 `HomeView.kt:1223` 的空白菜单也提供「整理模式」入口。返回键由 `HomeView.kt:278` 的 `handleBack()` 优先退出整理模式并返回 `true`。

编辑模式当前只绘制一条顶部提示条。`HomeView.kt:809` 的 `drawEditBanner()` 在 `0` 到 `tokens.textCaption + tokens.gap * 2` 之间铺黑底白字，文案为「整理模式：拖动排序，点击图标打开菜单，点击空白退出」。`HomeView.kt:432` 的 `onDraw()` 在普通分页与页码点之后绘制这条提示，除此之外没有底栏、按钮、工具带或任何参数入口。

拖拽与翻页手势的分派集中在三处。`HomeView.kt:842` 的 `onTouchEvent()` 按动作分派；`HomeView.kt:906` 的 `handleMove()` 在 `editing` 为真时只处理拖动：命中入口且位移超过 `touchSlop` 时置 `dragMoved = true` 并调用 `moveEntry()`，随后在 `HomeView.kt:919` 提前返回，编辑模式下横向滑动不会翻页。`HomeView.kt:952` 的 `handleUp()` 在编辑状态下，未拖动且长按未触发时命中入口则打开单条菜单，未命中则 `exitEditMode()`；拖动结束时调用 `persistOrder()`。音量键分派 `HomeView.kt:264` 的 `turnPageByVolume()` 在编辑模式下返回 `true`，不翻页。

网格几何全部来自 `DesktopMetrics`。`feature/home/.../DesktopMetrics.kt:210` 的 `compute(width, height, config)` 生成度量对象，`HomeView.kt:316` 的 `recomputeMetrics()` 在尺寸变化、附加到窗口与每次 `applyData()` 后重算，`HomeView.kt:184` 位于 `applyData()` 内。命中与绘制使用同一套槽位计算：`DesktopMetrics.kt:116` 的 `cellRect()` 与 `DesktopMetrics.kt:139` 的 `slotAt()`。

### 宿主层级

`app/src/main/java/com/shuiyu/octoberrain/MainActivity.kt:143` 的 `buildLayout()` 搭建五层结构：`wallpaperLayer`、`contentFrame`、`trayHolder`、`lockHolder`、`statusHolder`（`MainActivity.kt:204` 至 `MainActivity.kt:208` 依次加入根 `FrameLayout`）。`contentFrame` 的 `topMargin` 为状态栏高度（`MainActivity.kt:167` 至 `MainActivity.kt:175`），屏幕内容高度约为 800 − 46 = 754 像素。

`MainActivity.kt:286` 的 `pushScreen()` 把 `screen.createView(context)` 加入 `contentFrame`。`feature/home/.../HomeScreen.kt:38` 的 `createView()` 返回的正是 `HomeView` 本身，因此桌面视图铺满内容区，没有额外的容器层。`HomeScreen.kt:50` 至 `HomeScreen.kt:61` 订阅配置流，配置变化后调用 `home.applyData()`，`HomeView.kt:184` 再重算度量并重绘。

由此可以确定悬浮层的三种挂载层级。第一，`HomeView` 内自绘，面板与图标共用一块画布与一套触摸分派。第二，`HomeScreen` 返回 `FrameLayout` 包装器，`HomeView` 与面板视图并列，包装器尺寸与内容区一致。第三，`WindowManager` 浮层，需要 `SYSTEM_ALERT_WINDOW` 权限，会引入权限申请与窗口裁切问题，与当前全屏自绘结构不匹配。

翻页不消失这一要求把可选范围收窄。`HomeView` 在整段桌面生命周期内不重建，自绘面板天然跨页存在；包装器方案同样存在，但需要新增层与布局参数同步，复杂度高于自绘。`WindowManager` 方案能跨 Activity 存在，代价是权限与焦点管理，本设计不推荐。

### 四项设置的取值与写入

`feature/settings/src/main/java/com/shuiyu/octoberrain/feature/settings/DesktopPrefsScreen.kt:58` 的 `build()` 组装「首页布局」「网格」「图标与标签」「交互」「内容与数据」以及负一屏声明表。四项设置位于 `DesktopPrefsScreen.kt:114` 的 `buildGrid()` 内，控件为 `SettingsUi.section().stepper()`：

| 设置项 | 配置字段 | 取值序列 | 默认值 | 行号 |
| --- | --- | --- | --- | --- |
| 每页列数 | `desktop.columns` | 0、3、4、5、6、7，0 表示自动 | 0 | `DesktopPrefsScreen.kt:119` 至 `:125` |
| 每页行数 | `desktop.rows` | 0、2、3、4、5、6，0 表示自动 | 0 | `DesktopPrefsScreen.kt:126` 至 `:132` |
| 列间距 | `desktop.columnSpacingDp` | 0、4、8、12、16、20、24 dp，0 表示自动 | 0 | `DesktopPrefsScreen.kt:140` 至 `:146` |
| 行间距 | `desktop.rowSpacingDp` | 同列间距 | 0 | `DesktopPrefsScreen.kt:147` 至 `:153` |
| 图标尺寸 | `desktop.iconSizeDp` | 0、40、48、56、64、72、80 dp，0 表示自动 | 0 | `DesktopPrefsScreen.kt:154` 至 `:160` |
| 首页应用行数 | `desktop.firstPageRows` | 0、2、3、4，0 表示自动 | 0 | `DesktopPrefsScreen.kt:97` 至 `:103` |
| 每页应用上限 | `desktop.pageLimit` | 由 `PAGE_LIMIT_VALUES` 给出，默认 15 | 15 | `DesktopPrefsScreen.kt:104` 至 `:110` |

取值常量集中在 `DesktopPrefsScreen.kt:550` 至 `DesktopPrefsScreen.kt:570`。字段默认值见 `core/common/src/main/java/com/shuiyu/octoberrain/core/common/Models.kt` 的 `DesktopConfig`，其中 `columns`、`rows`、`columnSpacingDp`、`rowSpacingDp`、`iconSizeDp`、`firstPageRows` 均为 0 表示自动，`pageLimit` 默认 15。

写入流程为 `SettingsUi.edit` → `AppServices.config.edit` → `JsonConfigStore.edit`。连续步进由 `DesktopPrefsScreen.kt:323` 的 `scheduleDesktop()` 合并，`STEP_DEBOUNCE_MS` 为 160 毫秒（`DesktopPrefsScreen.kt:540`），`onPause()` 与 `onStop()` 时冲刷待写值。`DesktopPrefsScreen.kt:76` 的提示文案写「自动档位按屏幕尺寸反算，布局改动在返回桌面后生效」，这是对设置页返回流程的描述；配置流本身是即时推送，桌面若在栈中，`applyData()` 会立即重算度量，界面在下次可见时已呈现新布局。

恢复入口在 `DesktopPrefsScreen.kt:295` 的「恢复本页默认」，`confirmResetLayout()` 位于 `DesktopPrefsScreen.kt:452`，把 `columns`、`rows`、`columnSpacingDp`、`rowSpacingDp`、`iconSizeDp`、`firstPageRows` 全部写回 0，保留隐藏应用与排序。

### 网格计算与改动效果

`DesktopMetrics.compute()` 的关键分支：

- 列数：`DesktopMetrics.kt:331` 至 `:338`，配置大于 0 时取值，否则由 `DeviceRuntime.profile.gridColumns(tokens.cellMinWidth)` 反算，再夹在 1 到 8。
- 单元格宽度：`DesktopMetrics.kt:341`，`(right - left - columnGap * (columns - 1)) / columns`。
- 列间距：`DesktopMetrics.kt:234` 至 `:239`，配置大于 0 时按 `tokens.dp()` 换算，否则取 `tokens.gap`。
- 行间距基准：`DesktopMetrics.kt:240` 至 `:245`。
- 图标尺寸：`DesktopMetrics.kt:342` 至 `:354`，显式值时夹在单元格宽度的 0.55 到 0.8 之间，否则取单元格宽度的 0.68 并与令牌上限比较。
- 单元格高度：`DesktopMetrics.kt:362` 至 `:364`，`iconSize + labelHeight + rowBaseGap`，行数按可用高度反算并夹在 1 到 12。
- 首页组件槽：`DesktopMetrics.kt:371` 至 `:388`，`resolveWidgetBlock()` 位于 `DesktopMetrics.kt:464`，组件槽吸收剩余高度。
- 余量分配：`fitRows()` 把剩余像素均分到行间，结果写入 `firstRowGap` 与 `fullRowGap`。

改动是否即时可见：`HomeView.applyData()` 末尾调用 `recomputeMetrics()` 与 `invalidate()`，`publishLayout()` 同步分页快照。配置每写入一次，桌面就重算并重绘一次，无需重建 View 或重启页面。

### 设备与令牌实测

S4 设备为 480×800 像素，状态栏高 46 像素，内容区 480×754。页边距 `tokens.pageGutter` 实测 27 像素，`tokens.gap` 为 11 像素，`tokens.pageTop` 与 `tokens.pageBottom` 各 19 像素，`tokens.stroke` 为 2 像素。按当前配置计算：`left = 30`、`right = 450`、列数为 4、`cellWidth ≈ 96`、`iconSize ≈ 65`、`labelHeight = 30`、`cellHeight = 106`、组件槽高 231 像素、`widgetGap = 28` 像素、首页应用区从 278 像素开始；常规页行数为 6。

## 三、候选项

四个候选项共用同一组控件语义：每一项由「名称」「当前值」「左减」「右加」组成，取值序列与设置页一致，0 显示为「自动」。差异在位置、尺寸、对网格的影响与手势处理。

### 方案一 底部常驻工具带

位置与尺寸：贴在内容区下缘，左右留 `pageGutter`，高度由标题行、四个参数行、底部操作行与内边距组成。按实测令牌计算：标题行 23 像素加 11 像素间距、四个参数行各 58 像素、操作行 58 像素、上下内边距各 11 像素，合计约 346 像素，占据 754 像素内容区的 46%。

对网格的影响：工具带占据底部固定区域，网格可用高度减少 346 像素。首页行数由 4 降为 2，常规页行数由 6 降为 3 左右，需要给 `DesktopMetrics.compute()` 增加「保留底部高度」参数，或把 `bottom` 减去工具带高度后再计算。这会改变 `HomeView.kt:319` 的调用点与 `publishLayout()` 的分页快照，`DesktopMetricsTest` 的既有断言需要同步调整。

控件形态：四行步进器，加底部「完成」与「恢复自动」两个按钮。数值范围与默认值同设置页。即时预览：每次步进写入配置后整页重排，工具带本身不动。撤销与恢复：进入整理模式时记录四项初始值，「恢复自动」写 0，「撤销」写回快照。翻页保活：工具带属于 `HomeView` 自绘区域，翻页只改 `currentPage`，工具带保持。退出状态：面板随整理模式退出消失，不写入新字段。误触防护：工具带矩形内先于图标命中，命中后不进入拖动；工具带以下的内容区不再响应空白退出。墨水屏代价：每次步进引起整页重排，局部刷新区域覆盖应用区，刷新面积约 420×500 像素；打开与关闭整理模式各引发一次大面积重绘。实现位置：`HomeView` 内约 200 至 240 行，`DesktopMetrics` 增加参数与注释约 20 行，测试约 40 行。可测试部分：保留高度参与的行数计算、工具带矩形与行矩形、数值序列的上下限与环绕、快照恢复。

### 方案二 悬浮方形面板

位置与尺寸：面板左边界取 `m.left`，宽度取 `m.right - m.left`，即 420 像素，等于四列网格宽度。顶部从提示条下沿再留一个间距开始，即 `y = m.top + bannerHeight + tokens.gap ≈ 49`。高度按单列四行计算：标题 23 加间距 11、四行各 58、底部操作行 58、上下内边距各 11，合计约 346 像素，底面约 395 像素，略高于组件槽下沿 250 像素，覆盖首页第一行图标的上半部分。

控件形态：四行参数，每行左侧名称与当前值，右侧两个方形按钮；底部一行放「完成」与「恢复自动」；标题行右端加「撤销」。翻页控制放在底部操作行左侧，用「上一页」「页码」「下一页」三个命中区，避免依赖横向滑动。数值范围与默认值同设置页。即时预览：写入配置后由配置流触发 `applyData()`，重算度量并重绘；面板位置依赖 `m.left`、`m.right`、`m.top`，重算后同步刷新。撤销与恢复：单级快照加恢复自动。翻页保活：面板由 `HomeView` 绘制，翻页后仍在原位，页码文本随 `currentPage` 更新。退出状态：面板随整理模式消失，位置若需记忆则另存界面偏好。误触防护：`handleDown()` 首先判断面板矩形，命中即消费本次手势并记录按钮候选，不设置 `dragIndex`；面板外点击按现有规则退出整理模式。墨水屏代价：步进只改面板自身与重排后的应用区，面板矩形内的局部刷新面积约 420×346 像素；翻页刷新整页。实现位置：`HomeView` 约 200 至 240 行；新增纯计算文件 `EditPanelLayout.kt` 约 140 至 170 行；不改 `DesktopMetrics`。可测试部分：面板矩形随度量与提示条变化、行矩形与按钮命中、数值序列、页码边界、快照恢复。

变体：若面板高度需要控制在组件槽 231 像素内，可把四行参数改为两列两行，每格 204 像素宽，高度约 230 像素，正好落在组件槽内，不覆盖图标。代价是每行左右按钮的触控宽度变小。

可拖动与位置记忆：长按标题行进入拖动，松手后把面板左上角换算为相对内容区的归一化坐标，写入界面偏好文件；下次进入整理模式恢复，越界时夹取到内容区内。拖动期间不写配置，仅重绘面板。

### 方案三 顶部下拉条

位置与尺寸：从提示条下沿展开，宽度取内容区宽度，高度按标题行加两行参数加操作行计算。四个参数横向排布时每项仅 96 像素宽，放不下名称、数值与两个按钮，因此改为两行两列：标题 23 加间距 11、两行各 66、操作行 58、内边距 22，合计约 246 像素，覆盖组件槽下沿以下 50 像素左右。展开后顶部区域固定，翻页保持。

控件形态：每格左侧名称，中间数值，右侧上下或左右两个按钮，按钮触控高度 44 至 58 像素。收起方式为点击标题行右侧「收起」或再次下滑。即时预览、撤销、恢复、翻页保活与方案二相同。误触防护：只在提示条区域的下滑手势触发展开，展开后条内消费触摸。墨水屏代价：展开与收起各一次面板区域重绘，参数步进引起应用区重排。实现位置：`HomeView` 约 180 行，手势仲裁新增约 40 行。可测试部分：展开高度与两列分格、滑动判定与阈值、参数序列。

手势冲突：宫格形态下向下滑动当前用于开关托盘，`HomeView.kt:944` 至 `HomeView.kt:946` 调用 `toggleTrayBySwipe()`；列表形态下纵向滑动用于翻页，`HomeView.kt:942` 至 `HomeView.kt:943`。顶部下拉需要额外的起点判定与优先级规则，冲突面大于方案二。

### 方案四 全屏布局调整页加整理模式入口

位置与尺寸：整理模式提示条右侧增加「布局」入口，跳转到全屏页面，页面复用设置页的分组与步进器，顶部保留一块桌面预览。预览复用 `HomeView` 的 `previewMode`（`HomeView.kt:86`）与 `setPreviewPage()`（`HomeView.kt:332`），按当前配置绘制缩小版面；也可在页面内嵌第二块 `HomeView` 实例。

控件形态：设置页同款分区与步进器，四项设置加「恢复自动」。数值范围与默认值同设置页。即时预览：写入后预览视图收到同一配置流并重绘。撤销与恢复：页面级快照，返回时若未保存则丢弃，或每次写入即生效并提供恢复自动。翻页保活：页面独立，整理模式已退出，不存在面板跨页问题。退出状态：返回整理模式后重新进入，或返回桌面。误触防护：常规设置页交互，无特殊冲突。墨水屏代价：每次步进全屏刷新，代价最高。实现位置：新增屏幕约 250 至 300 行，新增路由与入口约 30 行，预览联动约 120 行。可测试部分：页面构建、预览页码与缩放、写入与恢复。

该方案与需求中「在小组件的位置出现」不符，且翻页期间不可见，适合作为完整布局编辑的补充入口，不适合作为主方案。

## 四、方案对比

| 维度 | 底部常驻工具带 | 悬浮方形面板 | 顶部下拉条 | 全屏调整页 |
| --- | --- | --- | --- | --- |
| 位置 | 内容区下缘 | 组件槽上方，覆盖第一行图标 | 提示条下方 | 独立页面 |
| 面板高度 | 约 346 像素 | 约 346 像素，紧凑变体 230 像素 | 约 246 像素 | 全屏 |
| 对网格几何的影响 | 需减少可用高度，行数下降 | 无，网格不变 | 无 | 无 |
| 改动范围 | `DesktopMetrics` 参数、度量测试、`HomeView` | `HomeView` 与新增纯计算文件 | `HomeView` 与手势仲裁 | 新页面、路由与预览 |
| 翻页保活 | 天然保持 | 天然保持 | 天然保持 | 不适用 |
| 翻页手段 | 现有滑动被编辑模式屏蔽，需放开 | 面板内按钮或面板外滑动 | 面板内按钮 | 页面内预览翻页 |
| 手势冲突 | 与空白退出、页码点 | 与图标拖动、空白退出 | 与托盘下滑、列表纵向翻页 | 无 |
| 误触防护 | 工具带命中优先 | 面板矩形命中优先 | 展开手势需仲裁 | 常规页面 |
| 墨水屏刷新 | 每次步进整页重排 | 面板区域加重排区 | 面板区域加重排区 | 全屏重绘 |
| 实现估算 | 约 300 行 | 约 350 至 410 行 | 约 300 至 340 行 | 约 400 行 |
| 与需求贴合 | 中 | 高 | 中 | 低 |

## 五、推荐方案

推荐方案二，悬浮方形面板，采用单列四行参数与底部操作行，面板顶部从提示条下沿开始，宽度与四列网格一致。理由如下。

组合方式与需求最接近。面板出现在组件槽位置，与「在小组件的位置出现」一致；调整期间网格列行与图标仍在下方可见，调整结果与画面变化一一对应。

对既有几何无侵入。`DesktopMetrics` 不需要新增参数，`DesktopMetricsTest` 的 18 例断言不受影响，`publishLayout()` 快照与美化模式预览保持一致。底部工具带与顶部下拉条都要改变可用高度或增加手势优先级，回归面明显更大。

翻页保活无需额外容器。`HomeView` 在桌面生命周期内不重建，面板作为自绘层跨页存在；包装器方案需要同步尺寸与命中分派，`WindowManager` 方案需要悬浮窗权限，均增加不必要的复杂度。

刷新代价可控。面板矩形固定，参数步进只改面板文本与网格重排后的区域；翻页刷新整页，与当前翻页行为一致。

为降低遮挡与误触，建议在实现时同时采用三条细则。其一，面板打开时，被面板覆盖的槽位不参与拖动落点，`slotAt()` 结果若落在面板矩形内则忽略该次落点。其二，面板内点击不触发「点击空白退出整理模式」。其三，参数写入沿用设置页的 160 毫秒合并策略，避免连续步进触发多次整页重排与多次写盘。

若 Doow 认为遮挡第一行图标不可接受，改用紧凑变体：四行参数改为两列两行，面板高度压到 230 像素，完整落在 231 像素的组件槽内，行与列的按钮仍保持 96 像素宽与 58 像素高。

## 六、手势与交互冲突

横向翻页在整理模式下被关闭。`HomeView.kt:910` 至 `HomeView.kt:920` 在 `editing` 为真时只处理拖动并提前返回，`HomeView.kt:974` 至 `HomeView.kt:987` 的抬起分支只处理菜单与退出。面板内放置页码按钮可以绕开这一限制，不需要修改既有手势分派。

点击空白退出与面板点击冲突。`HomeView.kt:977` 在未命中入口时调用 `exitEditMode()`，面板区域必须在此之前命中并消费手势，否则每次调整都会退出整理模式。

图标拖动落点可能落在面板下方。`HomeView.kt:915` 用地 `slotAt()` 计算目标槽位，面板覆盖的槽位在视觉上不可见。实现时需要在 `moveEntry()` 前判断目标槽位矩形是否与面板矩形相交，相交则忽略本次落点。

长按进入整理模式的计时从图标按下开始。`HomeView.kt:111` 的 `enterEditRunnable` 只在命中入口后投递，面板内按下不会命中入口，因此不会误触发；实现时仍需在 `handleDown()` 最前判断面板矩形，避免面板内长按触发单条菜单。

顶部提示条与面板起点冲突。提示条高度为 `tokens.textCaption + tokens.gap * 2`，实测约 38 像素，面板起点取 49 像素可避开。

音量键在整理模式下被消费。`HomeView.kt:264` 至 `HomeView.kt:271` 在编辑状态下返回 `true` 且不翻页，面板的翻页按钮与音量键互不影响。

设置页与面板可能同时写入。设置页使用 160 毫秒合并，面板若立即写入，连续步进会产生多次配置流发射与多次重排。建议面板采用同样的合并间隔，并在退出整理模式时冲刷。

页码点在悬浮方案下不受影响。`HomeView.kt:774` 至 `HomeView.kt:806` 的指示点位于底部留白内，与面板矩形不交叠；底部工具带方案则会压住指示点。

文本缩放影响面板高度。`tokens.textScale` 会改变标题与数值字号，面板高度与行高应当由令牌推导，不使用固定像素。

## 七、待定项

1. 面板位置采用固定组件槽，还是允许长按标题拖动并记忆位置。固定实现约 300 行，可拖动与记忆再加约 60 行。
2. 「间距」一项是列间距与行间距联动写入，还是拆成两个参数行。联动与设置页的独立两项不一致，拆分会让参数行变为五行。
3. 调整档位是否沿用设置页序列：列 3 至 7、行 2 至 6、间距 0 与 4 至 24 dp、尺寸 0 与 40 至 80 dp。面板内是否需要在序列中提供「自动」以外的快速回退。
4. 是否保留单级「撤销」与「恢复自动」两个入口，或只保留「恢复自动」。
5. 面板遮挡第一行图标是否可接受；若不可接受，采用 230 像素紧凑变体。
6. 组件槽为空，即 `desktop.widgetId` 为空时，面板是否仍然出现在顶部。
7. 552×768 与 824×1648 两档是否改用两列两行布局。
8. 面板位置记忆写入配置流的 `DesktopConfig` 新字段，还是写入独立的界面偏好文件。前者会改变配置结构，后者不进入备份。

## 八、可测试部分与实现估算

纯计算部分集中在新增文件 `feature/home/src/main/java/com/shuiyu/octoberrain/feature/home/EditPanelLayout.kt`，建议包含：面板矩形计算，输入为内容区宽高、度量边界、提示条高度与文本令牌；四个参数行的矩形与左右按钮命中；底部操作行内页码三个命中区；当前位置对应的序列下标与显示文本；拖动后的归一化坐标与越界夹取。该文件可写 12 至 16 例单元测试，不依赖 Android 类。

交互与写入部分留在 `HomeView`：字段、绘制、命中分派、快照、合并写入。此部分依赖 `MotionEvent` 与配置服务，按现有测试约定不新增 `androidTest`，以手工与真机截图核对。

实现估算汇总：纯计算文件 140 至 170 行；`HomeView` 增加 200 至 240 行，其中绘制约 110 行、命中与手势约 70 行、快照与写入约 50 行；测试 150 至 200 行。合计约 500 至 600 行，与当前 `HomeView` 1366 行的体量相比，建议把绘制与命中计算分离，避免单文件继续增大。


