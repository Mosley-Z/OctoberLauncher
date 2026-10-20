# 待办页与周期选择器逐页对照（P4 552×768）

设备 P4 552×768（串号 P4L202610130006，密度 210，1dp = 1.3125px）。微读墨记源码取自 D:\WorkBuddyStation\WereadMoji\weread-widget，十雨源码取自本仓 feature/weread-app。
截图目录 _analysis/verify/20261020-parity/，测量脚本 measure.py 同目录。

## 一、待办页

| 项目 | 微读墨记 | 十雨桌面（feature/weread-app WereadTodoPage.kt） |
| --- | --- | --- |
| 页签 | SegTabView「待办列表 / 已完成」，activity_todo.xml 固定 44dp | SegTabView 同文案，高度 tokens.topBarHeight（P4 为 46dp = 60px） |
| 清单区 | ScrollView 权重 1；listBox padding 左右 16dp、上 10dp、下 10dp | ScrollView 权重 1；listBox padding 16 / 10 / 10（dp 常量同值） |
| 行结构 | item_todo.xml：CheckBox wrap + 内容列 weight1 marginLeft 4dp；行 padding 上下 8dp | 同结构：CheckBox + 内容列 weight1 marginStart 4dp；行 padding 上下 8dp |
| 正文 | 17sp 黑色，已完成加删除线 | tokens.textBody（17sp 档），已完成加删除线并改 INK_3 |
| 时间文字 | 13sp #888，marginTop 2dp，无日期时间则 GONE | tokens.textCaption（12sp）INK_3，whenLabel 为空不加视图 |
| 勾选框 | 系统 CheckBox，勾选后切组并 refresh | 系统 CheckBox，勾选后 host.toggle 再 render |
| 排序态 | 点「排序」切换；排序态每行右侧 ↑ ↓ 各 48dp、22sp，普通态隐藏；排序态点行不触发编辑 | 同：点「排序」切换，箭头 48dp、tokens.textHeading（17sp），普通态点行进编辑 |
| 空态 | TextView 15sp #888，padding 上下 8dp，位于列表与按钮之间 | tokens.textHeading + INK_3，padding 上下 8dp，同一位置 |
| 空态文案 | 待办：还没有待办，点下面「添加待办」记一条；已完成：还没有已完成的事项 | 文案一致 |
| 底部按钮行 | 横向两等宽按钮「添加 / 排序」，padding 左右 16dp、上 8dp、下 12dp、间距 6dp | 同左，按钮高 tokens.buttonHeight，文案「＋ 添加待办 / 排序（完成排序）」 |
| 新增/编辑对话框 | AlertDialog + dialog_todo_edit.xml：标题 添加待办/编辑待办，EditText 内容、日期行、时间行、按钮 保存/取消，编辑态多一个 删除 | 自定义描边 Dialog，顶部对齐 137dp，左右外边距 8dp、内边距 12dp；标题为 heading，EditText hint「要做什么？」，日期行、时间行（状态文字 + 选日期/选时间 + 清除），按钮 删除/取消/保存 |
| 日期选择 | 系统 DatePickerDialog | 系统 DatePickerDialog（一致） |
| 时间选择 | 自绘黑白步进弹窗，－/＋ 点一次 ±1，长按 450ms 后每 120ms 连续 ±1 | 系统 TimePickerDialog（24 小时制）；无步进按钮与长按加速 |
| 文案格式 | whenLabel 由 TodoItem 生成 | 未设日期 / yyyy-MM-dd、未设时间 / HH:mm |
| 数据同步 | 每次增改删勾选排序后 CardA11yService.sync() | AppServices.wereadTodos 读写后 render() |

依据：微读 activity_todo.xml、item_todo.xml、dialog_todo_edit.xml、TodoPageController.java（371 行，:245 pickDate、:261 自绘时间弹窗、:320-334 长按 450/120ms 步进）；十雨 WereadTodoPage.kt（591 行，:521-590 常量、:446 pickDate、:474 pickTime）。

## 二、周期选择器

| 项目 | 微读墨记 | 十雨桌面 |
| --- | --- | --- |
| 组件 | PeriodPickerView.java（196 行） | 同一份文件，SHA-256 均为 542C4F1A…602EF，行数与内容一致 |
| 挂载 | activity_main.xml:53 固定 44dp（P4 折算 57.75px） | WereadReadingPage.kt:186 LinearLayout(MATCH_PARENT, tokens.topBarHeight)，P4 为 46dp（60px），实测行高 60px |
| 周模式箭头 | 左右各一个三角，间距按 BTN_W_RATIO=0.115×宽 | 同公式，实测左三角外接框 (27,123)-(36,137)、右三角 (515,123)-(524,137)，即 10×15px，中心距边 31.7px |
| 月模式箭头 | 左右各两级三角（双箭头） | 同代码，未改 |
| 标签 | 17×unit 加粗居中，基线 h×0.52；副标签 13×unit 灰，h×0.87「（本周）→（本月）」 | 同；实测主标签外接框 (153,120)-(398,138)，副标签 (259,145)-(293,159) |
| 边界禁用 | 前向越界时改用 LIGHT #A8A8A8 且不响应 | 同；步进到上周后右三角变黑、副标签消失（p4-picker-04-prev-week.png） |
| 分隔线 | 组件内自绘上、下两条 #D8 细线 | 组件内同样自绘，另在下方追加 InkWidgets.divider 一条（InkColors.INK_4）；实测浅线位于 y=104-106 与 165-166 |
| 弹出态 | 无弹层；周期行常驻，形态切换靠 NavDropView 下拉 | 同样无弹层；NavDropView 五项（本周/本月/本书/本记/足迹）覆盖卡片区，实测弹层上下边界 y=106 与 y=341，五行总高 235px（约 36dp/行） |
| 触摸区 | 左右各 0.115×宽，月模式再各加一段；只看 x 不看 y | 同代码，未改 |

结论：周期选择器为原文件直用，绘制与命中几何完全一致；差异只有挂载高度（44dp vs 46dp）与十雨多一条分隔线。

## 三、两类选择器（选记 / 选书）

| 项目 | 微读 NotePickView（选记） | 微读 BookPickView（选书） | 十雨 showBookPicker |
| --- | --- | --- | --- |
| 载体 | 贴底半屏 bottom sheet，SHEET_RATIO=0.70 | 贴底半屏，SHEET_RATIO=0.62 | 全屏 Dialog（Theme_DeviceDefault_Light_NoActionBar），窗口 MATCH_PARENT×MATCH_PARENT |
| 关闭 | 上半屏 SCRIM #D8 点击关闭 + 标题栏「关闭」触摸区（宽−padX−56dp 起） | 同款 SCRIM 与关闭区 | 底部「取消」按钮；点外部不关闭 |
| 背景 | 白底，行分隔 #D8 | 白底，行分隔 #D8 | InkColors.PAPER + 每行 InkWidgets.row（右侧作者 + CHEVRON） |
| 标题 | 44dp 行 | 44dp 行 | InkWidgets.title，实测文字外接框 (20,99)-(117,122) |
| 搜索 | 有：54dp 行、EditText 真控件、hint「搜索书名」16sp、内边距 10dp | 无（候选 3~4 条） | 有：hint「输入书名或作者过滤」，下划线实测 y=186-187 |
| 行高 | 44dp | 46dp | tokens.listRowHeight（46dp），实测行距 62px≈47dp |
| 内边距 | padX 16dp | padX 16dp | 四边 tokens.pageGutter（P4 为 20dp） |
| 选中标记 | 列表区左侧黑竖条 | 同 | 无，选中即关闭对话框 |
| 滚动 | 自绘拖动 + SLOP=12px，列表区手势滚动 | 同 | 系统 ScrollView |
| 数据 | NoteStore.BookRef（书名/条数/就绪），首行「全部书籍」 | BookStore.bookCandidates，首行「自动（最近在读）」 | DeskPage.shelf() 书目，首行「全部书 / 关注所有书的划线」，最多 80 行并提示「仅显示前 80 本」 |
| 复用 | 独立 View，不与其他页共用 | 独立 View | 本记与本书共用同一个 showBookPicker() |

实测截图：p4-picker-08-book-pick.png（十雨选书弹层：标题、搜索下划线、八行候选、取消按钮上边线 y=695-696）。

## 四、差异清单（按影响面）

1. 选记与选书在十雨合并为一个全屏对话框，微读是两套半屏弹层（选记带搜索、选书不带搜索）；十雨两者都带搜索，且点外部不关闭、无 SCRIM 与左竖条。
2. 十雨待办的时间选择使用系统 TimePickerDialog，微读为自绘 ±1 步进弹窗并支持长按 450ms 后每 120ms 连续步进；电子墨水屏上系统轮盘的刷新与可用性差别明显。
3. 待办对话框载体不同：十雨为自定义描边对话框（顶部 137dp），微读为系统 AlertDialog；内容结构一致。
4. 字号差异：行内时间文字十雨 12sp、微读 13sp；排序箭头十雨 17sp、微读 22sp；页签高度十雨 46dp、微读 44dp。
5. 周期选择器为同一文件，几何一致；差异仅挂载高度 46dp 与多一条分隔线。
6. 周期选择器与形态下拉均无弹层式设计，微读与十雨同构；「弹出态」实为 NavDropView 下拉（实测 36dp/行、五行总高 235px）。

## 五、截图

- p4-63-reading.png：十雨阅读页与周期行（本周）
- p4-picker-04-prev-week.png：左箭头步进到上一周，右箭头转黑
- p4-picker-05-dropdown.png：形态下拉弹出态（本周/本月/本书/本记/足迹）
- p4-todo-10-new.png：待办页空态、两段页签与底部两按钮
- p4-todo-11-edit.png：新增待办对话框（该包构建于 23:09，尚无日期/时间行）
- p4-todo-12-input.png：输入态与键盘
- p4-picker-07-book.png、p4-picker-08-book-pick.png：本书形态与选书弹层
- p4-picker-06-note.png：本记形态空态（无「换一条」入口）

## 六、已知偏差与限制

- 十雨 WereadTodoPage.kt 在 23:21 有改动，晚于设备上安装的 23:09 构建，待办对话框截图不含最新的日期/时间行；其余对照按当前源码。
- 设备为电子墨水屏，输入事件与刷新存在数秒延迟，个别截图存在过渡帧。
- 微读 NotePickView 未在 P4 上运行（该机未装微读 App），其几何取自源码常量。

## 七、选记与选书半屏改造（2026-10-20 23:45 后）

改动文件位于 feature/weread-app/src/main/java/com/shuiyu/octoberrain/feature/wereadapp/ui/：

- 新建 WereadPickSheet.kt（半屏弹层基类，约 505 行）：贴底底板、上半屏遮罩、关闭区、行高与内边距参数化，拖动滚动与点按命中；遮罩色 #D8D8D8，分隔线 #D8D8D8，关闭文案「关闭」。
- 新建 WereadNotePickSheet.kt：底板占屏高 0.70、标题行 44dp、搜索行 54dp（提示「搜索书名」）、行高 44dp、内边距 16dp、标题「选择书籍」，首行「全部书籍 / 关注所有书的划线」。
- 新建 WereadBookPickSheet.kt：底板占屏高 0.62、标题行 44dp、行高 46dp、内边距 16dp、标题「选择「本书」」、首行「自动（最近在读）」，无搜索行。
- WereadReadingPage.kt：周期行改用 44dp 常量挂载并删除多余分隔线；本记「选书」接选记弹层、本书「选书」接选书弹层；新增 writeCurrentBook() 写 files/weread/book.json（bookId、title、author、coverUrl、readingSec、progress、finished、finishTime）。

验证：:feature:weread-app 的 compileDebugKotlin、ktlintCheck、detekt、testDebugUnitTest 与 :app:assembleDebug 全部 BUILD SUCCESSFUL。

真机截图（_analysis/verify/20261020-pick2/）：

- p4-pick2-11-period-row.png：周期行高 44dp，无多余分隔线
- p4-pick2-13-note-sheet.png：选记半屏（遮罩、标题、搜索行、左侧选中竖条）
- p4-pick2-14-scrim-close.png：点遮罩关闭后回到本记卡片
- p4-pick2-04-book-sheet.png：选书半屏（首行自动、无搜索行）

已知偏差：搜索行提示文字由画布绘制并在 EditText 获得焦点后隐藏；半屏底板无圆角，与微读一致。

核对修正（Lead 复核）：微读 `NotePickView.java:55` 与 `BookPickView.java:49` 的遮罩为常量 `SCRIM = 0xFFD8D8D8` 单色 `drawRect` 填充，无渐变与 alpha 动画，十雨 `WereadPickSheet.drawScrim` 同为纯色，两侧一致；微读选记搜索行无放大镜图标（仅 `setHint` 与 `setBackground(null)`，全仓无 `setCompoundDrawables` 或图标绘制），十雨同样不画图标。以上两点从差异清单删除。

设备数据已复原：删除注入的 files/weread/nt_index.json 与 nt_CB_4nI0jQ0kvCZI6qC6okCPUDvt.json，book.json 保持原内容。
