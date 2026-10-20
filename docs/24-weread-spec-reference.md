# 微读墨记功能与交互规格（十雨桌面内置参考）

## 一、文档定位

本文写给十雨桌面工程团队，用于在十雨桌面内以原生桌面小组件形态重建微读墨记的两块界面：桌面卡片与墨台，并覆盖 App 内各功能页，使用户从微读墨记切换到十雨桌面时，桌面观感与操作手感保持不变。

三份材料的分工如下。

| 材料 | 位置 | 内容 |
|---|---|---|
| 工程合同 | `weread-widget/docs/kernel-integration.md` | 内核产物与打包方式、KernelHost 约定、KernelStore 读写、kernel_prefs.json 键、DataSource 来源、接入步骤、验收 |
| 两侧对接 | 十雨仓库 `docs/18-handoff-v1.0.1-and-weread-v1.3.2.md` | 版本对应关系、交付流程、接口联调记录 |
| 本文 | 本文件 | 桌面卡片五形态、墨台四页面与三模块、App 内各页、点击交互逐项、版面常数、代码参考位置 |

建议阅读顺序：先读工程合同第二、三、七节建立数据概念，再读本文第三至五节确认界面形态，最后按本文第九节逐项自检。

本文的代码参考位置均相对 `weread-widget/app/src/main/java/com/inkread/weekread`。内核包 `kernel` 是提供给十雨的实现，`feature`、`a11y`、`shell`、`ui` 是微读原版宿主实现，可作为交互写法的参照。

## 二、系统分层

界面背后分三层，职责边界明确。

第一层是内核 `com.inkread.weekread.kernel`。纯 Java 与 android.graphics，不持有 Context，不发网络请求，不读写宿主存储。产物为四个 jar（k1 渲染核心、k2 卡片、k3 墨台交互与壁纸、k4 数据生成层），版本 1.3.2。

第二层是桥接 `com.inkread.weekread.bridge`。负责把内核需要的缓存数据从微读墨记进程导出给十雨，以及把十雨内修改的偏好写回微读墨记。容器格式 WRMK，AES-GCM 加密，三个动作 EXPORT、PROBE、WRITE_PREFS。

第三层是宿主。微读原版宿主是 a11y 悬浮窗（桌面卡片）与 Activity（墨台、App 内页）；十雨宿主是桌面小组件与十雨自己的 View 体系。

数据流：

```
微信读书接口
  → 微读墨记缓存文件（files/ 下的 shelf.json、book.json、nt_*.json、achievement.json 等）
  → 桥接 EXPORT 导出为 kernel_prefs.json 与四份 menu_*.json
  → 十雨 KernelStore 读取
  → 内核 DataSource 装配快照
  → 内核渲染器绘制到宿主画布
```

反向修改（勾选待办、录价、改字号、隐藏书）由十雨写 WORKSPACE 内的偏好，再通过 WRITE_PREFS 回写微读墨记，下次 EXPORT 后两侧一致。

关键边界：内核只承担排版、绘制与命中判定。形态切换、菜单、刷新时机、窗口管理、持久化全部由宿主承担。十雨若只调用内核渲染而不实现宿主侧交互，卡片将呈现为不可操作的一张图。

## 三、术语与单位

| 术语 | 定义 |
|---|---|
| 号 | 排版单位。1 号等于屏幕长边乘 0.0015，在 S4（480×800）上是 1.2 像素 |
| 卡片档 | 桌面小组件尺寸，368×346 像素，展开态 368×680 |
| 全屏档 | App 内整页尺寸，480×800 |
| 形态 | 卡片内容类型，取值为 weekly、monthly、book、note、todo |
| 卡片池 | 允许轮换的形态集合，由偏好掩码决定 |
| 墨台 | 桌面卡片呼出的全屏操作面板，含墨单、阅读资产、阅读指纹三个模块 |
| 原盘 | 微读墨记 `files/` 目录下的缓存文件，是内核唯一的数据来源 |

字号一律以号给出，渲染时乘宿主给出的 unit。坐标一律为像素，原点在卡片左上角。

## 四、桌面卡片

### 4.1 形态总表

| 形态 | 内部取值 | 卡片档主要内容 | 全屏档补充 | 数据来源 |
|---|---|---|---|---|
| 本周 | weekly | 本周总时长、7 根柱状图、星期标签、两行统计 | 同一版面纵向铺开 | 周期缓存 achievement.json 与 bill 汇总 |
| 本月 | monthly | 本月总时长、日历网格（打卡或热力图）、摘要行 | 排名区、整页滚动 | 周期缓存 |
| 本书 | book | 封面、书名、作者、章节、时长与进度条 | 同版面放大 | book.json 与 shelf.json |
| 本记 | note | 划线正文、署名、章节与日期、换一条、上一条 | 筛选、选书、导出 | nt_*.json |
| 待办 | todo | 未完成条目清单与勾选框 | 同版面 | 本地待办文件 |

卡片抬头结构固定，从左到右为：形态切换热区（88×34，含箭头与「本周」等标签）、「更新于 HH:MM」、「打开墨台」按钮（76×24，贴右端）。四类周期形态与本书形态都有「更新于」，待办形态没有时间戳，该位置留空。

### 4.2 共通版面常数

卡片几何全部定义在 `kernel/CardGeometry.java`，微读原版的 `core/CardSpec.java` 只是转发壳。

| 项 | 取值 | 说明 |
|---|---|---|
| 画布 | 480×800 | 几何坐标恒按该画布定义 |
| 卡片框 | 左 56、右 424、上 70、下 416 | 即 368×346 |
| 展开态底边 | 750 | 本记展开时窗口高度改为 680 |
| 抬头行高 | 34 | 卡片上沿到分隔线 |
| 分隔线 | 卡片框下 2 像素 | 一条横线，卡片档与全屏档位置不同 |
| 内边距 | 横向为画布宽乘 padXRatio | 桌面卡片 padXRatio 为 0，App 内为 0.05 |
| 展开按钮 | 行末，宽度随文字 | 仅卡片档本记 |

### 4.3 本周形态

版面自上而下为抬头行、主数字、柱状图、底部统计行、成就行。

主数字为本周总时长，字号 38 号（全屏档 46 号），居中绘制。宽度不足时逐级缩小，下限为 38 号的 0.55 倍。

柱状图为 7 根竖柱，每根柱顶标数值（16 号），下方一行星期标签「一 二 三 四 五 六 日」（16 号）。柱高按当日时长占本周最大值的比例，空白日画最短柱。图表纵向范围占卡片高度的 0.40 至 0.78。

底部统计行为「阅读 1/7 天 · 日均 17分 · 较上周 ↓39%」，17 号，加粗。宽度不足时自动缩小，下限 12 号。

成就行可选，格式为「本周距离完美阅读周还有 9小时24分」，12 至 17 号居中小字。成就行出现时，主数字与图表让出一段高度：给定量为卡片高度乘 0.095 乘 0.428。

空态与错误态：当前周期没有阅读记录时画空柱与「暂无记录」；历史周期没有记录时改画一行说明文字；接口失败时画「同步失败」并可重试。

### 4.4 本月形态

版面为抬头行、主数字、日历网格、摘要行。全屏档在摘要行下方追加排名区。

日历网格为 7 列 6 行，格宽 20、间距 4、行距 4、网格总宽 164。两种呈现方式由偏好决定：

打卡网格：已读日期画实心黑方块并叠白勾，空白日期画细描边空框，未来日期留白。

热力图：按当日时长分五档黑度，阈值 0、30、60、180 分钟，格内叠网点纹理。纹理格距 2 像素，格边 1 像素。

摘要行为本月阅读天数与累计时长。排名区标题「本月各书籍阅读排名」，每行 26.67 高，行内为序号（宽 34）、书名、时长，右侧留 12 像素间距。

### 4.5 本书形态

左封右文。卡片档封面宽 108 像素（90 号），全屏档 144 像素（120 号），封面与右区间距 14 号。

右区自上而下为书名（15 号，全屏 21 号）、作者（12 号，全屏 15 号）、章节名（13 号，全屏 17 号）、时长与进度百分比（17 号，全屏 22 号）、进度条（高 13 号，全屏 18 号）。

封面未取到时画描边占位框。右下角有「打开」按钮，点击后跳到微信读书该书的阅读页。左下角有「选书」按钮，点击后弹出候选列表。

### 4.6 本记形态

版面为抬头行、大引号、正文、署名、末行、按钮行。

正文前的大引号为 30 号（全屏 40 号）。正文共分两路字号：卡片档取偏好 `note_card_size`，范围 12 至 24 号，默认 17 号；全屏档取导出档位，小、中、大三档为 15、17、19 号。

正文行高为字号乘 1.55。行数范围为 3 至 16 行，超出部分在卡片档以省略号截断并提供展开按钮，在全屏档以滚动消化。

署名第一行为书名（14 号，全屏 17 号），第二行为作者（12 号，全屏 15 号）。末行为章节与日期（11.5 号，全屏 14 号）。右下角「换一条」，左下角「上一条」，位置与本书形态的「打开」「选书」共用同一按钮位。

全屏档另有三个控件：左下「筛选」格（在「全部」与「只看想法」之间切换）、「选书」格（弹半屏书目列表）、「导出」按钮（导出为长图）。

进度提示为「第 N / 共 M 条」，M 取当前槽位的池子规模。卡片档的槽位跟随选书结果，选中某本书后进度按该书的条目数计算。

空态分两种，必须区分：接口失败时显示「同步失败」并提供重试入口；确实没有内容时显示说明文案，例如「这本书还没有划线」。两者文案不同，不能混用一句「暂无内容」。

### 4.7 待办形态

版面无主数字与图形，直接列未完成条目。每条为复选框加一行文字（17.6 号），任务时间以 13.2 号灰字排在右侧。条目数超过一屏时截图显示前若干条。

空态为「暂无待办」。待办数据为本地清单，没有「更新于」。

### 4.8 卡片交互总表

微读原版桌面卡片的窗口分两层：卡片主体窗口标记为不可触摸，手势穿透给桌面；按钮与可点区域各开一个透明小窗叠在对应位置。下表为完整可点区域。

| 区域 | 几何 | 动作 | 实现位置 |
|---|---|---|---|
| 「打开墨台」按钮 | 贴卡片右端，76×24 | 唤出墨台 | `a11y/OverlayController.java` deskView |
| 形态切换热区 | 卡片左端 88×34 | 短按切到卡片池内下一形态；长按弹隐藏菜单 | `a11y/OverlayController.java` titleView |
| 「更新于 HH:MM」 | 形态切换热区右侧，128×34 | 手动刷新数据 | 同文件 hitView |
| 右下按钮 | 卡片右下 | 本记形态为「换一条」；本书形态为「打开」并跳微信读书 | 同文件 openView |
| 左下按钮 | 卡片左下 | 本记形态为「上一条」；本书形态为「选书」候选 | 同文件 prevView |
| 行末「展开▽」 | 随正文落点 | 展开或收起本记正文 | 同文件 expandView，矩形由绘制回写 |
| 待办复选框 | 每条左侧 | 勾选该条 | 同文件 todoViews，一屏最多五六个 |
| 本记正文 | 正文区 | 纵向拖动逐行滚动 | `feature/CardInteraction.java` noteTouch |

文本交互细节：本记正文滚动阈值 12 像素，位移超过阈值即锁定为滚动，松手不判点击；滚动量吸附到整行，关闭惯性；本月全屏档整页滚动不吸附，同样关闭惯性。

三张菜单互斥，同一时刻只显示一张，8 秒无操作自动关闭。

隐藏菜单由抬头长按弹出，四行为「隐藏 15 秒（测试）」「隐藏 1 分钟」「隐藏 5 分钟」「取消」，对应隐藏时长 15 秒、60 秒、300 秒与取消。隐藏期间卡片不显示，到时自动恢复。

选卡下拉由抬头短按在列表模式下弹出，选项为卡片池内各形态的简称。

本书候选菜单由左下按钮在本书形态下弹出，第一行恒为「自动（最近在读）」，其后为书架候选，当前档在行首画黑色竖条标记。

### 4.9 卡片状态与刷新时机

卡片形态存在偏好 `card_period`，取值 weekly、monthly、book、note、todo。卡片池掩码存在 `card_pool_mask`，决定短按抬头可轮换的形态集合。

「更新于」的时间来源分三路：周与月取周期缓存的 fetchedAt，本书取 book.json 的 fetchedAt，本记取该条内容所属书的落盘时刻。落盘时刻为零时不画「更新于」，并不以渲染时刻顶替。

刷新时机有四种：App 静默刷新（前台服务定时）、桌面卡片唤起时的补刷、点击「更新于」的手动刷新、用户切换形态后的重绘。渲染函数本身不发请求、不读偏好，全部数据由宿主编排后注入。

### 4.10 卡片内核接口

十雨侧使用 `kernel/CardState.java` 作为状态容器，构造时传入实现 `CardSurface` 的绘制目标，随后：

状态写入用 setMode、setStats、setBook、setNote、setTodo、setError、setPlaceholder、setAchievementText、setMonthHeatmap、setNoteCardSize、setNoteSlot、setCoverBitmap。

绘制调用 `kernel/render/CardRenderer.java` 的 draw，状态容器实现 `CardView` 接口向渲染器提供读数。

命中判定调 `CardState.hitTest(x, y)`，返回 `kernel/HitRegions.java` 的编号，判定顺序为：「打开墨台」、本月滚动、本记的筛选、展开、上一条、打开、选书、导出、正文滚动，本书的打开与选书，待办的逐条复选框，最后是打开。

动作回调调 `CardState.onAction(int)`，当前实现把打开、选书、上一条、筛选、打开墨台五项翻译为宿主页面跳转：待办形态跳 todo，本书跳 book，本记跳 note，其余跳 bill，打开墨台跳 desk。

内核未提供的交互，需要十雨宿自己实现：形态轮换、三张菜单、手动刷新、隐藏时长、滚动惯性策略、窗口管理。参照实现为 `feature/CardInteraction.java`（触摸分流与滚动）与 `a11y/OverlayController.java`（透明小窗与菜单）。

### 4.11 卡片代码索引

| 关注点 | 文件 |
|---|---|
| 几何常数 | `kernel/CardGeometry.java` |
| 绘制主体 | `kernel/render/CardRenderer.java` |
| 排版与文本工具 | `kernel/render/CardLayout.java` |
| 状态容器与命中 | `kernel/CardState.java` |
| 渲染器状态读取接口 | `kernel/CardView.java` |
| 命中编号 | `kernel/HitRegions.java` |
| 参考宿主实现 | `feature/WeekCardView.java`、`feature/CardInteraction.java`、`a11y/OverlayController.java` |
| 偏好键 | `core/CardPrefs.java` |
| 卡片几何转发壳 | `core/CardSpec.java` |

## 五、墨台

### 5.1 定位与入口

墨台是桌面卡片呼出的全屏操作面板，承载墨单、阅读资产、阅读指纹三个模块，并提供设置面板与壁纸预览。

打开路径唯一：卡片抬头右端「打开墨台」按钮。微读原版中该按钮由独立透明小窗承接，点击后由无障碍服务启动 `shell/DeskActivity`；十雨侧对应为宿主的窗口调度。

墨台为全屏、纯白底、无状态栏。顶栏常驻，高为 3 个单位（约 54 像素）。

### 5.2 页面状态

墨台有三个页面状态，状态之间互斥。

列表态：默认状态，展示已启用模块。顶栏左侧为「齿轮 设置」，中间为衬线标题「墨台」，右侧为「更新于 HH:MM」与「返回 ›」。返回 › 关闭墨台，回到桌面。

设置态：由「齿轮 设置」进入。顶栏标题换成设置面板标题，左侧设置键置空，右侧保留「返回 ›」。面板内容为模块开关与排序、阅读资产的三项过滤、阅读指纹的选书入口与已移出书目恢复。

壁纸预览态：由各模块抬头右端的「生成壁纸」进入。顶栏标题换成壁纸标题，右侧保留「返回 ›」。预览区下方有三个按钮：保存、分享、关闭。

状态切换规则：

| 当前状态 | 动作 | 目标状态 |
|---|---|---|
| 列表 | 点「齿轮 设置」 | 设置 |
| 列表 | 点「返回 ›」或向左滑动 | 关闭墨台 |
| 设置 | 点「返回 ›」或横向滑动 | 列表 |
| 壁纸预览 | 点「保存」 | 保存到相册，留在预览 |
| 壁纸预览 | 点「分享」 | 调用系统分享，留在预览 |
| 壁纸预览 | 点「关闭」或「返回 ›」 | 返回进入前的状态 |

左右的退出手势阈值：横向位移超过 14 像素与屏宽 0.12 两者中的较大值，判定为退出。向左滑动在列表态关闭墨台，在设置态返回列表。

### 5.3 模块注册与顺序

模块有三个，注册信息在内核 `kernel/desk/DeskPrefs.java`。

| 模块 | 标识 | 位掩码 | 默认启用 |
|---|---|---|---|
| 墨单 | bill | 1 | 是 |
| 阅读资产 | rank | 2 | 是 |
| 阅读指纹 | fp | 8 | 否 |

默认掩码为 3，即墨单与阅读资产启用。模块顺序由偏好 `desk_order` 决定，默认顺序为墨单、阅读资产、阅读指纹。分区装配在 `kernel/desk/DeskPage.java` 的 rebuildSections，宿主通过 `DeskHost.deskModuleMask` 与 `DeskHost.deskOrder` 提供取值。

代码位置对应关系：微读原版的模块注册表为 `core/DeskModules.java`，装配入口为 `feature/DeskPageView.java` 的 buildSections，偏好读写为 `core/PagePrefs.java`。

### 5.4 墨单模块

墨单是记账式阅读清单，把一段周期内的阅读时长折算成书价与收入。数据来源为账单缓存与书目缓存，渲染期间不发请求。

版式自上而下为：抬头行（标题、页签、生成壁纸）、周期条（‹ 2026-W40 › 与周月切换）、票据头（周期号与「墨单」）、周期摘要（周结 · 09.28–10.04 · 5 本 · 16h53m）、价格表、整单备注、合计行、条码、落款。

价格表为四列：序号、品类、原价、收入。收入列关闭时回落为三列。列宽常量 62 个单位，收入列贴右，原价列在其左侧。

两个视图共用一份数据与一套尺，由抬头页签切换：

摘录菜单：以书摘为主体，显示价格与收入，计算合计，不显示进度行。

读书菜单：以书名、作者、备注为主体，不显示价格，不计算合计。

收入算法有三档，由偏好 `menu_paid_algo` 决定：增额、累计、关闭。行内收入由 `BillMath` 计算，合计行按档位改写文案。累计档首段为「合计 本期阅读 X 小时」，非累计且时长大于零时追加「折合 ¥Y/小时」。

价格有三态：有价格时显示金额，明确免费时显示「免费」并将收入计为零，价格未知时显示破折号且不计入合计。价格链为手填价、笔记价格、账单市场价、统计落盘价，全部失配时按未知处理。

长按价格行可录入实际书价。限制条件：只在摘录菜单、且该行市场价为零时开放。录入键盘为数字键盘，键码包括小数点、删除、确定、取消。空输入点确定等于清除该书的录入价。

页签切换规则：点击页签条，在当前可见的两个视图之间循环切换，并写入偏好。

### 5.5 阅读资产模块

阅读资产把书架逐本的阅读时长、进度折算为原价与收入，按条目列表展示。模块标识沿用 `rank`，与旧版读书排行共用同一个位，内容已替换。

版式复用墨单的行渲染：主串为序号加书名，右端两列收入与原价，第二行小字为作者、时长、进度，摘录皮肤再追加第三行摘录。

收入公式：收入（分）等于原价（分）乘计费进度再除以一百，半进位。计费进度唯一入口为 `kernel/BillMath.java` 的 progressForBilling。进度未取到时该条收入显示破折号且不计入合计。

过滤规则有三项：

书架过滤，偏好键 `asset_shelf_only`，缺省关闭。开启后只统计书架内的书。书架快照缺失时不过滤。

读完过滤，偏好键 `asset_finished_only`，缺省开启。开启后只统计已读完的书。

移出清单，偏好键 `asset_hidden_ids`。被移出的书不出现在表中，可在设置面板逐个恢复或全部恢复。

时长门槛：累计阅读不足 600 秒的书不进表。

列表默认折叠显示 10 本，尾部有「展开 ›」与「收起」的切换行，并显示「还有 N 本」。展开状态为瞬时状态，不写入偏好。

抬头右端有三个排序键与「生成壁纸」按钮。排序键为时长、原价、收入，默认按时长降序，未知价格与未知收入排在末位。

### 5.6 阅读指纹模块

阅读指纹把阅读书目与划线文字排布在一组折线上，形成文字指纹图。默认不启用。

几何数据来自 `res/raw/fp_paths.txt`，共 22 条折线、485 个顶点，坐标保留三位小数。文件只存几何，字位与容量在运行时按公式现算。

运行时规则：

字步长等于字号乘字距系数。每段折线的容量等于折线长度除以步长取整。字位沿折线均布，两端各留半个字。字向取该点切线角，窗口正负 9 像素，结果归入负 90 度到正 90 度。净空过滤：字心到其它折线的最小距离小于净空值时丢弃该字位。缺字留白，不替换字形。文字基线按居中修正。墨量按字形覆盖度做平方根提升，用 256 项查找表实现。

四档字号为 16、17、18、19 像素，偏好键 `fp_font_px`，缺省 18。四档字距为 1.00、1.00、0.95、1.00，四档净空为 8.0、8.5、14.4、9.5 像素。18 像素档因净空过滤丢弃两个字位，其余三档不丢。

抬头右端为字号四档选择与「生成壁纸」。选书入口最多保存 50 本书，候选列表屏内最多列 300 条。

### 5.7 设置面板

设置面板分为三段。

第一段为模块开关与排序。每个模块一行，含勾选框与上下移动键。勾选写入掩码，上下移动改写顺序。顺序决定列表态的分区上下次序。

第二段为阅读资产的三项过滤：只统计书架、只统计读完、已移出书目的恢复入口。

第三段为阅读指纹的选书与字号。选中书目后指纹模块只使用这些书。

### 5.8 壁纸预览

三个模块都能生成壁纸。壁纸为纯黑白墨水屏版式，分辨率与当前屏幕一致。

预览态下方三键：保存写入相册目录，分享调用系统分享面板，关闭返回进入前的状态。壁纸渲染在 `kernel/desk/WallpaperRenderer.java` 与各模块的 Poster 类中，微读原版对应 `feature/BillWallpaper.java`、`feature/AssetPoster.java`、`core/FingerprintPoster.java`。

### 5.9 交互细节常数

| 项 | 取值 |
|---|---|
| 左滑退出阈值 | 14 像素与屏宽 0.12 的较大值 |
| 长按判定 | 550 毫秒，手指移动即撤销 |
| 菜单自动关闭 | 8 秒 |
| 价格键盘键码 | 小数点 10、删除 11、确定 12、取消 13 |
| 列表折叠 | 10 本 |
| 资产时长门槛 | 600 秒 |
| 指纹字号档 | 16、17、18、19 像素 |

### 5.10 墨台内核接口

十雨侧使用 `kernel/desk/DeskPage.java`。构造时传入实现 `DeskHost` 的宿主对象，可选传入数据根目录。

宿主需要实现的方法：deskModuleMask（模块掩码）、deskOrder（模块顺序）、menuPrefs（读字符串偏好）、menuBool、menuInt、fpFontPx（指纹字号）、styleColor（颜色令牌）、filesDir（数据目录）、scale（长度缩放系数，缺省 1.2）、requestRedraw、openPage（打开功能页）。

绘制调用 render(canvas, width, height, offset)，高度查询调用 height(width)，命中查询调用 hitTest(x, y)。

命中后返回动作编号，编号定义在 `kernel/desk/DeskActions.java`：收入算法 100 起三档、资产排序 110 起三档、资产展开 120、指纹字号 130 起四档、指纹选书 140、生成壁纸 150、账单本周 160、本月 161、上一期 162、下一期 163、摘录与读书视图切换 164、长按墨单价格 200、长按资产价格 201、长按资产书名 202。

宿主在拿到编号后按需改写偏好、切换状态或打开页面，再调 requestRedraw 请求重绘。长按的延迟判定与菜单显示由宿主实现，内核只给出长按命中的编号。

### 5.11 墨台代码索引

| 关注点 | 文件 |
|---|---|
| 页面状态与装配 | `kernel/desk/DeskPage.java` |
| 宿主能力接口 | `kernel/desk/DeskHost.java` |
| 偏好键与文案 | `kernel/desk/DeskPrefs.java` |
| 动作编号 | `kernel/desk/DeskActions.java` |
| 视觉常数与颜色令牌 | `kernel/desk/DeskStyle.java` |
| 顶栏与分区抬头 | `kernel/desk/DeskRenderer.java` |
| 墨单 | `kernel/desk/BillSection.java` |
| 阅读资产 | `kernel/desk/AssetSection.java` |
| 阅读指纹 | `kernel/desk/FingerprintSection.java` |
| 价格键盘 | `kernel/desk/PricePad.java` |
| 壁纸渲染 | `kernel/desk/WallpaperRenderer.java` |
| 参考宿主实现 | `feature/DeskPageView.java`、`feature/DeskSettings.java`、`shell/DeskActivity.java` |
| 原版偏好读写 | `core/PagePrefs.java`、`core/MenuPrefs.java` |
| 原版数据装配 | `feature/DeskData.java` |

## 六、APP 内页面

### 6.1 结构总览

客户端为单 Activity 多页签结构。主页面由四段页签控制：本周、本月、本书、洞察。页签左侧另有一个五档下拉，用于切换阅读页的五种形态：本周、本月、本书、本记、洞察。

页面清单与文件对应：

| 页面 | 入口 | 文件 |
|---|---|---|
| 阅读页（周期卡） | 桌面图标，LauncherAlias 指向 | `shell/MainActivity.java` |
| 设置页（内嵌表单） | 页签第 2 段 | `shell/SettingsPageController.java` |
| 实验室页 | 页签第 3 段 | `shell/LabPageController.java` |
| 待办页 | 页签第 4 段 | `shell/TodoPageController.java` |
| 本记页 | 下拉索引 3 | 卡片渲染，`feature/WeekCardView.java` |
| 洞察页 | 下拉索引 4 | `feature/InsightPageView.java` 与 `feature/InsightRenderer.java` |
| 独立设置 Activity | 遥控台齿轮键 | `shell/SettingsActivity.java` |
| 遥控台 | 安装角色为手机时自动进入 | `shell/ConsoleActivity.java` |
| 墨台 | 卡片抬头按钮 | `shell/DeskActivity.java` |
| 帮助页 | 设置页帮助行 | `shell/HelpActivity.java` |
| 待办独立页 | 清单声明 | `shell/TodoActivity.java` |
| 导出页 | 外部调用 | `bridge/ExportActivity.java` |

### 6.2 阅读页

版式为顶部三段控件加一张整卡：

顶部为四段页签，即周、月、书、洞察。页签左侧是周期选择器，形如「‹ 2026-W40 ›」，周档左右移动一周，月档左右移动一个月，长按跳到上一年或下一年。页签下方是整卡容器，卡片由 `WeekCardView` 绘制。

五种形态的版面在第四章已给出。本页承担三种输入：

点击卡片主体。本周与本月档在统计热区外点击卡片时，按书中记录打开微信读书对应位置，链接形如 weread://reading?bId=；若当档没有可打开的书，则不响应。

点击抬头按钮。打开墨台，与桌面卡片上的按钮行为一致。

点按刷新。右上角刷新按钮重新拉取统计数据并重绘。

周期选择器的边界：最早到数据起始期，最晚到当前期，越界时箭头置为不可用状态。

安装角色为手机时，进入阅读页即跳转遥控台并结束本页。

### 6.3 设置页

设置页为表单，分三组。

第一组为显示。纸色三档、正文字号三档、是否显示署名与日期、榜单是否只取书架书目。

第二组为卡片。卡片总开关、五种形态的启用池、月历样式、切换动画样式、卡片尺寸档位。修改后写偏好并请求重绘桌面卡片。

第三组为成就与联动。成就开关与目标档位、保存提示、测连、帮助、无障碍设置入口、重开卡片、更新渠道、云端回退开关、安装角色。

设置页的所有项都通过偏好写入生效，没有独立的保存按钮，唯一的显式保存动作是保存提示行。

### 6.4 实验室与待办

实验室页承载试验性开关，默认隐藏，需在设置页连续点击版本号展开。内容为实验性功能入口，具体项在 `LabPageController` 内装配。

待办页为清单式增删改查，数据落在偏好键 `todo_list`，条目含标题、时间、完成标记。操作包括新增、修改、删除、切换完成、上移下移。

### 6.5 本记页与选书浮层

本记页每次显示一条划线，卡片内为摘录、想法、作者与出处、页码进度、上一条与下一条按钮、换一条按钮，以及导出当前划线。

选书浮层由「换一条」或卡片抬头打开，为全屏浮层，含筛选输入框、书目列表、确认与取消。选中后写入当前书，并立即重绘本记页。

本记的取数与序号依赖笔记索引，数据来源与第四章的限制说明一致。

### 6.6 洞察页

洞察页为自绘滚动视图，分若干节：年度概览、阅读画像、书架构成、时段分布。每节有标题与数据块，排版由 `InsightRenderer` 负责。

滚动为整页自绘滚动，无惯性；触摸位移小于阈值时吞掉事件，避免误触。

### 6.7 独立 Activity 与锁屏

独立设置 Activity 为遥控台进入的设置页，顶部分两段页签，支持深色主题。内容与内嵌设置页部分重叠，是早期形态。

遥控台为手机端遥控墨水屏设备的界面，含翻页、按键、键盘、晃动、连接五页，底部为导航条。依赖无障碍服务与蓝牙 HID 保活服务。

锁屏软锁为无障碍覆盖窗，全屏覆盖加 3 乘 4 数字键盘，用于设备锁屏后的解锁。Home 与返回可绕过。

### 6.8 升级

升级流程为检查版本、下载安装包、调用系统安装器。检查渠道有两档：稳定与测试。云端回退开关用于下载失败时切换备用地址。

### 6.9 数据层

数据层全部为本地缓存，网络只用于刷新，渲染期只读缓存。

| 类 | 职责 | 存储位置 |
|---|---|---|
| `core/StatsStore.java` | 周期、书、本记统计与年度累计 | 偏好 `cfg`，键前缀 d_ 与 t_ |
| `core/BillStore.java` | 每期账单 | 偏好 `bills` |
| `core/BillScheduler.java` | 账单生成编排 | 写 `BillStore` 与 `ProgressLog` |
| `core/BillEstimator.java` | 账单估算 | `BillStore` 字段 |
| `core/AssetStore.java` | 书架书价与资产缓存 | 偏好 `asset_books` |
| `core/AssetRefresher.java` | 资产增量刷新，带有效期与请求上限 | `AssetStore` 与 `BookStore` |
| `core/NoteStore.java` | 划线、想法、索引、抽取批次 | 文件 `nt_index.json`、`nt_<bookId>.json`、`ni_<bookId>.json`、`nt_batch*.json` |
| `net/NoteSync.java` | 索引、想法、划线三段同步 | 写 `NoteStore` |
| `core/BookStore.java` | 书架与书目 | 文件 `shelf.json`、`book.json`、`shelf_ids.json` |
| `core/TodoStore.java` | 待办清单 | 偏好 `todo_list` |
| `core/ProgressLog.java` | 期界进度快照 | 偏好 `menu_progress` |
| `core/MenuPrice.java` | 菜单书价 | 偏好 `menu_prices` |
| `core/CoverStore.java` | 封面图 | 外部私有目录 covers |
| `core/PowerSettingsManager.java` | 安全设置写入命令与开关 | 偏好 `power_cfg` |
| `silent/SilentService.java` | 静默进程定期清点本地数据 | 由本地数据刷新器决定 |

刷新时机集中在五处：主页面进入与恢复、周期切换、卡片刷新按钮、无障碍服务的卡片重建、账单生成编排的定时点。

### 6.10 偏好键

偏好集中在 `core` 包，按键分组如下。

| 类 | 分组名 | 主要键 |
|---|---|---|
| `CardPrefs.java` | `cfg` | card_enabled、single_page_mode、bind_targets、month_style、note_card_size、update_channel、upd_cloud_fallback、remote_role、remote_idle_timeout、phone_dark_mode、install_role、remote_shake_enabled、remote_shake_sens、bt_control_enabled、bt_volkey_enabled、bt_flip_swap、bt_flip_vertical、bt_flip_repeat_ms、bt_type_interval_ms、card_pool_mask、switch_mode、rank_shelf_only、book_pick |
| `PagePrefs.java` | `cfg` | desk_enabled、desk_order、desk_on_mask、asset_shelf_only、asset_finished_only、asset_hidden_ids、fp_font_px、fp_books |
| `MenuPrefs.java` | `cfg` 与 `menu_notes` | menu_title、menu_unit、menu_top_n、menu_min_sec、menu_drop_off_shelf、menu_note_src、menu_excerpt_mode、menu_show_author、menu_show_duration、menu_show_progress、menu_footer、menu_blocks_mask、menu_font_title_serif、menu_font_body_mono、menu_fs_*、menu_empty_action、menu_footer_note、menu_view、menu_paid_algo、menu_period_mode、menu_period_start，以及逐书批注键 |
| `AchievementPrefs.java` | `achv` | enabled、week_kind、week_min、month_kind、month_min、targetSec |
| `LockPrefs.java` | `cfg` | lock_enabled、lock_pin_salt、lock_pin_hash、lock_bg_path |

### 6.11 清单声明

与内置相关的声明项：

| 声明 | 作用 |
|---|---|
| 两个无障碍服务，卡片服务与遥控服务 | 卡片、墨台、锁屏、账单生成时机依赖第一个 |
| 前台服务，HID 保活 | 遥控台使用 |
| 静默进程服务与开机接收器 | 数据清点与开机拉起 |
| 导出 Activity，对外可调用 | 跨应用数据导出 |
| 安装包提供者 | 升级安装 |
| 查询声明，含桌面宿主包名与 HOME | 桌面宿主导航与 Launcher 探测 |

桌面宿主包名为 `com.shuiyu.octoberrain`。微读原版在清单中声明了对该包的查询与开机接收器条件，用于与桌面共存时识别宿主。

### 6.12 内置建议

十雨侧已有桌面宿主，自绘卡片与墨台可以直接复用内核，没有障碍。建议的复用边界如下。

可直接复用：`kernel/render` 全量渲染与几何、`kernel/desk` 全量墨台、`kernel/BillMath` 与 `kernel/PeriodCalendar` 等纯计算、`kernel/load` 数据装载、`kernel/KernelStore` 偏好读写。

需要自建：桌面容器的窗口调度与让位、点击热区的落点、页面之间的跳转、通知与锁屏、升级。

可以裁剪：遥控台、HID 保活、摇一摇、静默进程这一组与桌面内置无关，属于设备远程控制功能，建议不移植。

## 七、内部机制与共存建议

### 7.1 桌面共存机制

微读原版以无障碍覆盖窗的形式存在于桌面之上，与桌面宿主之间存在三处协作点。

第一处为宿主识别。清单中声明对宿主包名的查询，并在开机接收器中监听宿主的安装、替换、移除事件，用于改变自身形态。

第二处为让位。宿主的应用抽屉或编辑态出现时，卡片让位隐藏；宿主回到桌面时，卡片重新出现。判定依据为窗口焦点与自家界面的可见性。

第三处为拉起。卡片抬头按钮由透明小窗承接点击，点击后打开墨台页。

十雨内置后这三处协作点都不再需要：卡片由宿主自己绘制，焦点判定与让位逻辑由宿主负责，墨台作为宿主页面打开。

### 7.2 刷新与数据时效

卡片的刷新时机有四处：亮屏、宿主回到桌面、点击刷新按钮、周期边界。刷新为异步，期间卡片显示刷新标记。

数据链路为网络请求改写本地缓存，缓存变化触发重绘。渲染期不发起请求，这一点在卡片与墨台都已保证。

### 7.3 与旧版本共存

微读原版与内置版本可以同时存在。共存策略在 `silent/CoexistPolicy.java` 中：检测到桌面宿主包名时，微读原版进入静默形态，停止绘制卡片与墨台，只保留数据服务。

十雨内置后应关闭微读原版的桌面形态，或直接引导用户卸载，避免两份卡片叠加。

## 八、代码索引

| 关注点 | 文件 |
|---|---|
| 应用入口与页面装配 | `shell/MainActivity.java` |
| 设置表单 | `shell/SettingsPageController.java` |
| 遥控台 | `shell/ConsoleActivity.java` |
| 墨台 Activity | `shell/DeskActivity.java` |
| 卡片渲染 | `feature/CardRenderer.java`、`feature/CardLayout.java` |
| 卡片状态容器 | `kernel/CardState.java`、`feature/WeekCardView.java` |
| 卡片交互 | `feature/CardInteraction.java` |
| 卡片窗口与三个菜单 | `a11y/OverlayController.java`、`ui/CardMenuView.java` |
| 无障碍服务 | `a11y/CardA11yService.java` |
| 墨台视图 | `feature/DeskPageView.java`、`feature/DeskRenderer.java` |
| 墨单 | `feature/BillSection.java`、`feature/AssetPoster.java`、`feature/BillWallpaper.java` |
| 阅读资产 | `feature/AssetSection.java` |
| 阅读指纹 | `feature/FingerprintSection.java`、`core/FingerprintPoster.java` |
| 价格键盘 | `feature/PricePad.java` |
| 本记 | `feature/NotePickView.java`、`feature/NoteRenderer.java` |
| 洞察 | `feature/InsightPageView.java`、`feature/InsightRenderer.java` |
| 数据层 | `core/StatsStore.java`、`core/BillStore.java`、`core/AssetStore.java`、`core/NoteStore.java`、`core/BookStore.java` |
| 跨应用导出 | `bridge/BridgeContract.java`、`bridge/ExportActivity.java` |
| 共存策略 | `silent/CoexistPolicy.java` |

## 九、待补事项

本版覆盖页面结构、交互规则、几何常数与数据层。以下内容需要单独补充。

动画与过渡：页面切换与卡片形态切换的过渡效果说明。

字体与墨水屏调优：灰度映射、抗锯齿开关、刷新率与局部刷新策略。

多屏适配：当前版本以 480 乘 800 为基准，其他分辨率下的缩放与重排规则需要另行评估。

逐项文案：界面文案的完整清单，含空态、异常态与提示语。
