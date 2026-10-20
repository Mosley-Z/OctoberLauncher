# 微读墨记与十雨内置页逐页对照

对照条件：同一台 S4（480×800）与 P4（552×768），两侧填入同一 API Key（`wrk-V1vh…9QAA`），同一时间窗口 2026-W43。

## 一 阅读页（1.0.9 已对齐）

微读截图 `_analysis/verify/20261020-compare/s4-cur.png`，十雨改造前 `s4-ten-reading.png`，改造后 `_analysis/verify/20261020-parity/step-301-reading-new.png`，并排图 `step-310-side-by-side.png`。

微读结构来自 `app/src/main/res/layout/activity_main.xml` 的 `mp_reader`：

```
SegTabView tab_main        主导航：本周▽ / 设置 / 实验室 / 待办
View sep_top
PeriodPickerView picker    周期行：◀ 2026年10月19日 - 10月25日（本周）▶
View sep_mid
WeekCardView card          内核卡片
InsightPageView insight    洞察页（默认隐藏）
View sep_card
TextView tv_remote_notice
LinearLayout
    Button btn_refresh     整宽刷新按钮
```

改造前的四处差异与改造结果：

| 项 | 微读 | 十雨 1.0.8 | 十雨 1.0.9 |
| --- | --- | --- | --- |
| 主导航首项 | 本周▽ | 阅读 | 本周▽，随形态变化（本月▽/本书▽） |
| 周期行 | 左箭头 + 日期区间 +（本周） | 月历下拉 + 左箭头 + `2026-W43` + 右箭头 | 左箭头 + 日期区间 +（本周） + 右箭头 |
| 子页签 | 无 | 本周/本月/本书/洞察 四段 | 已删除，形态切换交给 NavDropView |
| 底部刷新 | 整宽按钮 | 无 | 整宽按钮，40dp 高，行内 12/8dp |
| 洞察页 | InsightPageView 同层切换 | 独立子页签 | 下拉选「足迹」切 InsightPageView |

## 二 复用实现

微读 UI 层源码复制进 `feature/weread-app/src/main/java/com/inkread/weekread/`，保留原包名，24 个 Java 文件约 1.1 万行，其中 `feature/WeekCardView.java` 867 行、`feature/NoteExport.java` 372 行、`ui/InkTheme.java` 308 行、`core/NoteStore.java` 1835 行、`core/StatsStore.java` 331 行。

两处源码改动：`feature/CardLayout.java` 的 `R.string` 引用换成本地字符串，`feature/WeekCardView.java` 的 `desk_open_btn` 换成字面量「打开墨台」。其余按原样。

`WeekCardView` 的五处内部读盘改走新增的 `wereadapp/WereadMicroBridge.kt`，统一转 `AppServices.config` 与 `WereadAppData`；复制的 `StatsStore`、`NoteStore`、`CardPrefs` 只作编译依赖，运行时不读其 SharedPreferences。

`detekt` 与 `ktlint` 对 `**/com/inkread/weekread/**` 排除，Kotlin 胶水代码照常受检。

## 三 墨台

微读侧实机截图取不到：`onOpenDesk()` 走 `CardA11yService.showDesk()`，无障碍服务在设备上 `settings put secure` 后 `Bound services` 仍为空，系统未绑定；`DeskActivity` 又是 exported=false。

基准改用内核渲染图 `D:\WorkBuddyStation\WereadMoji\weread-widget\docs\snapshots\kernel-1.3.2-k5\` 的 `probe-bill.png`、`probe-asset.png`、`probe-fp.png`、`probe-insight.png`（480×800，与微读 `feature/DeskRenderer.java` 同源），辅以 `docs/measure/measure.md` 的量测值。

对照项六条：顶栏高度与三段文字位置、分区抬头墨竖线宽度与偏移、抬头基线比例 0.28 与 0.73、左右内边距比例、空态文案居中方式、分区之间分隔线粗细与留白。

差异明细写在 `_analysis/verify/20261020-parity/desk-diff.md`，含量测脚本 `_tools/measure-desk.mjs` 与 `_tools/measure-cols.mjs`。首轮发现五处实质差异：内核分区出现内核原生抬头与自建抬头重复、阅读指纹未进运行时装配默认集合、顶栏三段未按全宽定位且缺底部 1px 线、行分隔线为全宽纯黑、自建分区缺 3px 墨竖线与固定高度空态。

1.0.10 修复结果：重复抬头改为「自建抬头 + 内核内容」单层；`DEFAULT_MODULES` 运行时追加 `fp`（v9/v10 迁移默认集合不变）；顶栏改 `FrameLayout` 三段定位，`barH = 18号×3×unit`，标题按全宽居中，补 1px `#D8D8D8` 底线；行分隔线改 `x=24..455`、1px `#D8D8D8`；自建分区抬头补 3px 墨竖线（`x=15..17`、高 20px、标题左缘 25），空态改 108px 固定高居中；另修一处装配时序，首帧后才构建导致书架与最近图片只显示空抬头，`rebuildSections` 末尾补一次恢复回调。

残差两项：设置簇与返回簇宽度受字体度量影响与基准差 6~10px；十雨保留书架与最近图片两个自建分区，微读无对应分区。

## 四 设置页

微读截图 `_analysis/verify/p4-1.0.7/orig-settings-init.png`、`orig-settings-init2.png`、`orig-settings-init3.png`、`orig-settings-custom.png`、`orig-custom-2.png`；十雨截图 `_analysis/verify/20261020-1.0.8/p4-settings-init.png`、`p4-settings-custom.png`。

1.0.8 已改为「初始化」「自定义」两页签，段落顺序、按钮宽度、勾选与单选形态与微读一致。剩余差异：微读自定义页的形态池、月历样式、切换动画、卡片尺寸档四项在实测截图中没有入口。

## 五 待办页

微读实机截图 `_analysis/verify/20261020-parity/micro-todo-01.png`（空态）、`micro-todo-add.png`（新增对话框）、`micro-todo-edit.png`（编辑对话框）；十雨 `step-407-todo-empty.png`、`step-402-todo-list.png`、`step-405-todo-edit.png`。

列表、子页签「待办列表 / 已完成」、空态文案「还没有待办，点下面「添加待办」记一条」、底部两等宽按钮与微读逐字一致。对话框已按微读补齐为「标题 + 输入行 + 未设日期（选日期 / 清除）+ 未设时间（选时间 / 清除）+ 底部删除取消保存」，并删除原有多余提示行；按钮宽度、行 top/bottom 与微读实机截图逐项对齐到 ≤1px（量测见本节末尾）。

像素对照（P4 552×768，1dp = 1.3125px）另记录：页签高度 44dp 对 46dp；时间文字 13sp 对 12sp；排序箭头 22sp 对 17sp；时间选择器已改为与微读同规则的自绘步进（长按 450ms 后每 120ms 连击），对话框载体微读为系统 `AlertDialog`、十雨为自定义描边对话框，几何按像素量测对齐。

## 六 周期行与选择器

周期行两侧是同一份 `PeriodPickerView.java`（196 行、SHA-256 相同），三角形位置、标签基线与越界判定一致；挂载高度两边同为 44dp，按设备密度换算整数像素；十雨此前多出一条 divider，1.0.11 已删除。

选记与选书：微读 `NotePickView` 贴底 0.70、`BookPickView` 贴底 0.62，均带上半屏蒙层、关闭区、左侧选中黑竖条、自绘拖动滚动（SLOP 12px），选记带 54dp 搜索框，选书无搜索框且首行为「自动（最近在读）」。十雨原为一个全屏对话框，缺蒙层、关闭区与竖条。

1.0.11 已拆成两套半屏：新建 `WereadPickSheet.kt` 作为基类（占屏比、标题行高、行高、内边距、搜索行开关参数化，遮罩与分隔线 #D8D8D8，点空白关闭，右上关闭区，选中行 4dp 黑竖条，拖动阈值 12px），`WereadNotePickSheet.kt` 取 0.70 与 44dp 行高并带 54dp 搜索行，`WereadBookPickSheet.kt` 取 0.62 与 46dp 行高且无搜索行；`WereadReadingPage.kt` 把本记「选书」与本书「选书」分别接到两个弹层，选中书目写 `files/weread/book.json` 后重建卡片。

周期行：挂载高度由 `tokens.topBarHeight`（46dp）改为 44dp 常量，删除下方多余 `InkWidgets.divider`，只保留自绘上下两条 `#D8` 线；挂载值改为按 44dp 换算后的整数像素（`dpRound`：`TypedValue.applyDimension(...).roundToInt()`），S4 密度 219 得 60px、P4 密度 210 得 58px。

两机实测文本基线（灰阶扫描，`_tools/measure-lines.mjs`）：

| 项 | S4 480×800 | P4 552×768 |
| --- | --- | --- |
| 导航分隔线 | y=109..110 | y=104..106 |
| 周期行主标签文字带 | y=131..151，基线约 151 | y=120..136，基线约 136 |
| 副标签（本周）浅灰带 | y=169..172 | y=147 |
| 周期行下边线 | y=173 | y=163 |
| 周期行可见高度 | 约 62px（含上下线） | 约 57px（含上下线） |

截图：`_analysis/verify/20261020-parity/step-44x-period-row-s4.png`、`step-44x-period-row-p4.png`。

截图：`_analysis/verify/20261020-pick2/` 的 `p4-pick2-11-period-row.png`、`p4-pick2-13-note-sheet.png`、`p4-pick2-14-scrim-close.png`、`p4-pick2-04-book-sheet.png`；本轮补 `step-44x-period-row-s4.png`、`step-44x-period-row-p4.png`、`step-44x-note-sheet-s4.png`、`step-44x-note-sheet-p4.png`。

## 七 实验室

微读 `page_lab` 三段页签「翻页 / 锁屏密码 / 续航优化」，十雨原为单栏只有门禁说明。差异表在 `_analysis/verify/20261020-parity/lab-parity.md`。

1.0.10 补齐：`WereadLabPage.kt` 改为外壳加三段页签，新增 `WereadLabFlipPage.kt`（连接方式三项灰显标注由微读墨记维护、状态胶囊、角色三选一、折叠说明、页尾无障碍入口）、`WereadLabLockPage.kt`（启用、四位密码 8 字节盐加 SHA-256、背景路径）、`WereadLabPowerPage.kt`（授权命令、三项系统开关、电池优化与 Shizuku 入口）。截图 `p4-lab2-00-flip.png`、`p4-lab2-02-lock.png`、`p4-lab2-04-power.png`。

## 八 本记导出

1.0.11 改为复用微读 `feature/NoteExport.java` 372 行原实现，四个样式 getter 经 `WereadMicroBridge` 读 `kernel_prefs` 的 `paper` 与 `export_tpl`、`nsize`、`note_sign`、`note_date`，与设置页「自定义 → 本记导出图」同键联动；渲染在后台线程，完成后主线程提示与分享，图片存 `Pictures/微读墨记/`。

S4 实测六变体：手账加中字 480×702、素白 480×680、卡纸 480×702、手账加小字 480×496、手账加大字 480×994、署名与日期关闭 480×702。导出宽度恒为 `WeekCardView.EXPORT_W` 的 480，与设备宽度无关，P4 同为 480 宽，两侧留白属与微读一致的行为。导出页脚固定灰字「微读墨记·本记」，不随署名与日期开关变化。

截图：`_analysis/verify/20261020-parity/` 的 `step-42x-export-0-hand-zhong.png` 至 `-5-no-sign-date.png`，对照记录 `export-diff.md`。

## 九 洞察页

入口与微读一致：阅读页主导航首项下拉末项，同层切换，不新开页。截图 `_analysis/verify/20261021-insight/` 的 `p4-insight-00-empty.png` 至 `04-profile.png` 与 `s4-insight-01-top.png` 至 `04-profile.png`，量测脚本 `measure_insight.py`，完整记录 `_analysis/team/tenrain-negative-insight-parity.md`。

三组结论均为一致。年度与累计：分区标题、十二柱、1/4/7/10 月锚点、`readStat` 单行、无纵轴刻度、无月度网格；累计汇总行与陪伴行同源，两版都没有「连续天数」。排行：序号列 26×unit、书名超宽省略、时长右对齐、「单日阅读最久」黑底白字角标、无展开收起仅内滚、视口为视图高的 0.42 倍。画像与空态：判定句、依据行、兴趣雷达条与百分比、偏好作者前六、偏好时段 24 柱与锚点、免责声明、四分区空态文案均同源。

两处曾被列为差异，经源码核实不成立，已撤回：一是下拉末项文案，微读 `res/values/strings.xml` 的 `nav_insight` 在 2026-10-10 已由「洞察」改名为「足迹」，`NavDropView.java:52` 的 `labels` 末项同为「足迹」，十雨一致；二是底部整宽刷新按钮，微读 `activity_main.xml` 的 `btn_refresh` 与 `insight` 视图同层同在，微读洞察页同样常驻，十雨一致。S4 累计陪伴行缺「想法 N 字」属 `noteChars=0` 的数据差异，不是版式差异。

## 十 仍存差异

时间选择器：已改为自绘步进弹窗，步长 ±1、边界按 24/60 循环、长按 450ms 后每 120ms 连发、数字可直接输入，与微读 `dialog_time_pick.xml` 同规则；残差为弹窗整体位置比微读实机截图高约 50px（两机分辨率不同，按 dp 对齐后比例一致）。

待办对话框：按钮宽窄与行距已按微读实机截图像素量测收敛，误差 ≤1px；保存按钮按截图取加粗描边而非实心黑底。

选记与选书弹层：搜索行提示由画布绘制、聚焦后隐藏，与微读一致；微读 `NotePickView`/`BookPickView` 均无放大镜图标（P4 实机与源码双重确认），故不列为差异；两侧遮罩均为纯色 `#D8D8D8`，无渐隐动画，此前「遮罩纯色无渐隐」条目删除。

周期行：挂载高度已按 44dp 换算整数像素（S4 60px、P4 58px），实测行区与理论值差 1~2px，来源于自绘上下 1px 线的统计口径。

本机其余未对齐项：设置页自定义分组的形态池、月历样式、切换动画、卡片尺寸档四项在微读实机截图中无入口，未纳入。
