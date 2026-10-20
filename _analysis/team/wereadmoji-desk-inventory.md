# 微读墨记「墨台」功能全集清点与并入负一屏评估

- 清点日期：2026-10-20
- 被清点工程：`D:\WorkBuddyStation\WereadMoji`，主工程 `weread-widget`，分支 `beta`，版本 `1.3.0-beta`（vc 46）
- 代码根：`weread-widget/app/src/main/java/com/inkread/weekread`
- 本轮只读，未修改被清点工程的任何文件，未执行 git 提交。
- 依据材料：`docs/FEATURES/desk.md`（863 行，事实以代码为准，文档只作功能意图参考）、`core/` 与 `feature/`、`shell/` 下 17 个类，以及 `core/AssetStore.java`、`core/BillMoney.java`、`core/ProgressLog.java`、`core/MenuPrefs.java`。

---

## 一、墨台是什么（现状）

墨台是微读墨记在墨水屏设备上的全屏页面，由桌面卡片的「打开墨台」按钮呼出。`TASK-088` 起承载形态从无障碍覆盖层改为独立 Activity（`shell/DeskActivity.java:49`），入口按钮本身仍由无障碍服务的透明窗承担（`a11y/OverlayController` + `CardA11yService.showDesk()`）。

页面为单个自绘 `View`（`feature/DeskPageView.java`，1506 行，继承 `View`，重写 `onDraw`、`onTouchEvent`），三态共用一套顶栏与背景：

| 态 | 常量 | 内容 |
|---|---|---|
| 列表 | `MODE_LIST = 0`（`DeskPageView.java:80`） | 按模块顺序排列的分区，可上下滚动 |
| 设置 | `MODE_SETTINGS = 1`（`:81`） | 自绘设置面板 `DeskSettings`，右上角齿轮进入 |
| 壁纸预览 | `MODE_WALLPAPER = 2`（`:82`） | 墨单 / 阅读资产 / 阅读指纹三种海报，保存与分享 |

顶栏左侧自绘齿轮（进设置）、中部标题「墨台」与「更新于 HH:mm」、右侧「返回 ›」；吸顶裁剪在 `DeskRenderer.draw()` 内用 `clipRect` 实现（文档 §「顶栏吸顶裁剪」）。背景恒纯白，背景图与白纱两项已在 2026-10-11 删除（`PagePrefs.java:424-438`）。

---

## 二、模块系统

注册表 `feature/DeskModules.java`，全集 3 个模块（`ALL`，`:55-58`）；元信息（id、位、默认开关、显示名）在 `core/PagePrefs.java`。

| id | 位 | 显示名 | 默认 | 分区实现 |
|---|---|---|---|---|
| `bill` | 1 | 墨单 | 开 | `BillSection`（`DeskModules.java:105-118`） |
| `rank` | 2 | 阅读资产 | 开 | `AssetSection`（`:122-135`） |
| `fp` | 8 | 阅读指纹 | **关** | `FingerprintPoster`（`:139-148`） |

- 位定义与全集：`PagePrefs.java:73-82`；默认开启掩码 `DEFAULT_ON_MASK = BIT_BILL | BIT_RANK`（`:90`，不含阅读指纹）。
- 顺序表 `MODULE_ORDER = { bill, rank, fp }`（`:70-71`），顺序即渲染顺序；关闭的模块不渲染、不占高（`DeskPageView.buildSections()`，`:305`）。
- 持久化：`desk_order`（CSV 有序全表）、`desk_on_mask`（位掩码）、`desk_enabled`（总开关），键名在 `PagePrefs.java:93-95`，prefs 文件 `cfg`（`:100-102`）。
- 读出净化：`moduleBit()` 对未知 id 返回 0（`:107-112`）、`isKnownModule()`（`:115-117`）；未知位与未知 id 一律丢弃，旧配置零迁移。位 4/16/32/64 因模块下线永不复用（`:75-77`）。
- 开关入口：墨台内右上角齿轮 → 设置 → 「内容模块」两行（`DeskSettings.java:152-250` 布局，`activate()` 处理切换与上/下移，`:545-584`）。总开关 `desk_enabled` 的唯一界面入口在 APP 设置 → 自定义（`cb_desk_enabled`，文档 §「第七批 W1」），墨台内不再提供。
- 关闭后的表现：模块不装配、不留空行；总开关关闭则列表为空态，但设置入口仍可用（`DeskPageView.buildSections` 判 `PagePrefs.isDeskEnabled`）。
- 新增模块成本：写一个 `Section` + 注册到 `ALL` + 在 `PagePrefs` 加 id/位/名，容器、渲染器、设置面板不动（`DeskModule.java:5-21` 契约注释）。

---

## 三、墨单（bill）

实现：`feature/BillSection.java`（1636 行），数据来自 `core/Bill.java`（229 行）、`core/BillStore.java`（124 行）、`core/BillScheduler.java`（330 行）、`core/BillMoney.java`（329 行）、`core/ProgressLog.java`（103 行）、`core/MenuPrefs.java`（486 行）、`core/MenuPrice.java`（71 行）。

### 3.1 用户可见功能点

| 功能 | 行为 | 入口 | 数据来源 | 持久化键 | 代码位置 |
|---|---|---|---|---|---|
| 墨单标题 | 分区标题「墨单」，页眉区显示单号与墨单字样 | 列表内分区 | `PagePrefs.moduleName(MOD_BILL)` | — | `BillSection.java:188`、`drawHeadArea:729` |
| 周/月切换 | 周期条 `[周\|月]`，点左半为周、右半为月 | 点击周期条 | `BillStore.startsOf(mode)` | `menu_period_mode` | `hitMode:305-311`、`switchMode:275` |
| 上/下一期 | `‹` `›` 翻期，到边界置灰 | 点击箭头 | 同上 | `menu_period_start` | `hitArrow:314-323`、`shiftPeriod:250` |
| 单号与票据头 | 左=单号（如 `2026-W40`）、右=墨单 | 自动 | `Bill.start` / mode | 无 | `drawPeriodBar:736` |
| 页签 | 摘录菜单 / 读书菜单两格，Canvas 自绘 | 点击页签 | `MenuPrefs.view()` | `menu_view` | `hitTab:450-454`、`drawTabs:870` |
| 摘录菜单 | 每行：序号书名 / 作者 ¥价 / 原文；表头「品类｜主厨｜原价｜收入」 | 默认视图 | `BillStore` + `NoteStore` 缓存 | 无 | `rows():934`、`draw():691` |
| 读书菜单 | 不显示价格，主体为书名/作者/备注，可带时长与进度 | 切页签 | 同上 | 无 | 同上 |
| 收入算法下拉 | 收入算法：增额（默认）/ 累计 / 关闭，自绘下拉 | 点击下拉 | `MenuPrefs.paidAlgo()` | `menu_paid_algo` | `pickAlgo:497-506`、`drawAlgoDrop:509-549` |
| 收入列 | 书价 × 进度；增额档为期末减期初，累计档为期末累计 | 自动 | `BillMoney.paidFen` | 无 | `BillMoney.java` |
| 合计行 | 单行：`合计 阅读 H 小时，≈读回 ¥X，折合 ¥Y/小时`；累计档为 `合计 本期阅读 H 小时，累计读回 ¥X` | 自动 | 逐行累加 | 无 | `billTotalLine:1245`、`billTotalNote:1287` |
| 无价/免费兜底 | 无价书原价与收入 `—` 且不计合计；免费书原价「免费」、收入 `¥0` 且计入 | 自动 | 价格链解析结果 | 无 | `rows():934`（价格三态） |
| 长按录价 | 长按无真实市场价的书目行，弹自绘数字键盘；确定落盘、清空为删除、取消不落盘 | 长按行 | `MenuPrice` | 独立文件 `menu_prices`，键=bookId，值=分 | `openPad:569`、`padPress:649`、`MenuPrice.java:26/40/50` |
| 提示小字 | 表头下一行「长按书目行可录入实际书价」，全部行有价后消失 | 自动 | 本地价格链 | 无 | `rows():934`、文档 §「价格与实付」 |
| 生成壁纸 | 顶栏右侧纯文字「生成壁纸」，进入海报预览；海报保存到相册、分享、关闭 | 点击文字 | 当前账单 | 无 | `hitWallpaper:297`、`enterWallpaper:448`、`saveWallpaper:634`、`shareWallpaper:683` |
| 海报内容 | 摘录版 4 列表头 + 逐本价格 + 三行合计 + 满宽条码；读书版一行合计、无条码 | 自动 | `BillWallpaper.render` | 无 | `BillWallpaper.java`（788 行，`Poster:81`、`Saved:139`） |
| 空态 | 本期无记录时居中「本期无阅读记录」，不生成 0 值空账 | 自动 | `Bill` 占位 | `menu_empty_action` | `BillSection` 空态分支，文档 §「空数据保护」 |
| 月历 | 已于 2026-10-11 整体下架；页签在两档均为 2 格；旧 `menu_view=2` 回落摘录菜单且不回写 | — | — | `menu_view` | 文档 §「月历已下架」；`MenuPrefs.view()` 回落 |
| 整单备注 | 海报尾部可输出备注/条码/两者 | 设置项 | `MenuPrefs` | `menu_footer`、`menu_footer_note` | `BillWallpaper.java`、`MenuPrefs.java:82-93` |
| 手动备注 | 按 bookId 存一条整单备注 | 设置页（未接界面） | 手输 | 独立文件 `menu_notes` | 文档 §「设置项清单」 |

### 3.2 数据生成与刷新

- 生成时机：周账在每周第一天生成上一周，月账在每月第一天生成上一月；实现为幂等补齐 `BillScheduler.ensureBills`，在无障碍服务创建与 `MainActivity.onStart` 各调用一次，不使用定时闹钟（`docs/FEATURES/desk.md` 中「鲁棒实现 = 幂等补齐」段）。
- 存储：prefs 文件 `bills`，键 `bill_<mode>_<periodStart>`，每个 mode 保留最近 12 期（`BillStore`）。
- 当期月：不生成、不显示进行中月账；已存在的当期账每 60 秒节流刷新一次（`BillScheduler.CUR_REFRESH_GAP_MS`）。进度 ≥99 或已读完按 100% 参与收入计算（`BillMoney.progressForBilling`）。
- 状态：历史期改用「估算 ⇄ 精确」统一模型，估算金额前缀 `≈`；估算只覆盖进过 Top10 的书。
- 渲染纪律：`BillSection.refresh()` 只读 `BillStore` 与 `MenuPrefs`，不发网络请求（`BillSection.java:194-201`）。

### 3.3 内部状态（不算用户配置，但会落盘）

- `menu_view`：0 摘录菜单 / 1 读书菜单 / 旧 2 回落。
- `menu_footer_note`：整单备注文本。
- `menu_period_mode`、`menu_period_start`：记住「看哪一期」，不触发生成。
- `menu_paid_algo`：收入算法。

---

## 四、阅读资产（rank）

实现：`feature/AssetSection.java`（1045 行），共享构建器 `AssetSection.snapshot(Context)`（`:625-718`）；壁纸 `feature/AssetPoster.java`（379 行）复用同一快照，禁止另写一套算法。

### 4.1 用户可见功能点

| 功能 | 行为 | 入口 | 数据来源 | 持久化键 | 代码位置 |
|---|---|---|---|---|---|
| 排序三键 | 「时长 / 原价 / 收入」三枚文字键，点击切换；原价未知排最后，收入未知排最后 | 顶部三键 | `AssetStore` + 本地价格链 | 实例内保存，不落盘 | `SORT_*:70-74`、`sorter:883`、`sortRects:965` |
| 折叠/展开 | 前 10 本 + 「展开 ▽」；展开后「收起 △」 | 点击展开行 | 同上 | 不落盘 | `COLLAPSED_N:85`、`toggleOpen:370`、`toggleRect` |
| 摘录皮肤 | 每本显示价格两列与一条摘录；书名区与价格区按 x 分列 | 默认 | `NoteStore` 本地划线 | 无 | `hitRow:394-416`、`excerptOf:724` |
| 读书皮肤 | 不画价格列，整行都是书名区；单击/长按语义不同 | 随 `menu_view` 切换 | 同上 | `menu_view` | `hitRow` 注释 |
| 合计行 | 主串 `合计 N 本 · 时长`，右列原价合计与收入合计 | 自动 | 同一快照 | 无 | `totalRow:862`、`totalMain:869` |
| 合计小字 | `已计价 N 本 · N 本原价未知 · N 本进度未取到` | 自动 | 同一快照 | 无 | `totalNote:872-874` |
| 只统计书架 | 开关开：仅保留 `BookStore.shelfIds` 内的书；清单未知时不过滤；顺序不变 | 墨台设置面板 | `BookStore.shelfIds` | `asset_shelf_only`（默认关） | `snapshot:634`、`PagePrefs.java:274-283`、`RankFilter.java:56-71` |
| 只统计读完 | 默认开：进度未知且未读完的书隐藏；进度 ≥99 或已读完保留 | 墨台设置面板 | `BillMoney.progressForBilling` | `asset_finished_only`（默认开） | `snapshot:651-652`、`PagePrefs.java:289-304` |
| 长按书名移出统计 | 长按书名区把该书移出列表与合计，幂等；提示书名 | 长按书名区 | `PagePrefs.assetHiddenIds` | `asset_hidden_ids`（集合） | `hideBook:435-440`、`PagePrefs.java:315-349` |
| 恢复单本/全部 | 设置面板列出已移出的书，逐本恢复与全部恢复 | 墨台设置面板 | 同上 | 同上 | `DeskSettings.activate`、`PagePrefs` |
| 长按价格录价 | 长按价格区（仅摘录皮肤）弹同一套数字键盘 | 长按价格区 | `MenuPrice` | `menu_prices` | `openPad:449-461`、`padPress:466` |
| 生成资产海报 | 顶栏右侧「生成壁纸」，海报与屏幕列表同数据、同排序、同合计 | 点击文字 | `AssetPoster` + 同一快照 | 无 | `wallLabel:996`、`AssetPoster.java:56` |
| 空态 | 无数据时居中「暂无数据」 | 自动 | — | — | `EMPTY:111` |

### 4.2 口径细节（并入后必须保持）

- 进表门槛：单本累计阅读 ≤600 秒不入表（`MIN_SEC:87`，即 10 分钟）。
- 三条过滤（读完 / 只在架 / 已移出）为 AND 叠加，作用于同一份 `load` 结果，列表、合计、海报一起生效（`snapshot:645-652` 注释）。
- 价格链优先级：手填价（`MenuPrice`）→ 本地笔记价格索引（`NoteStore.priceFenOf`）→ 账单落盘市场价（`Bill.Item.marketPriceFen`）→ 统计落盘原价（`PeriodStats.Longest` 经 `BillMoney.fenOf`）→ 仍无则未知；未知不是免费（`snapshot:659-699`）。
- 收入：`(原价分 × progressForBilling + 50) / 100`，四舍五入到分；进度未知为 `—` 且不计合计；明确免费为 `¥0` 且计入（`snapshot:690-699`、`:702-716`）。
- 合计口径为全部条目，与折叠状态无关（`totalMain` 注释）。

---

## 五、阅读指纹（fp）

实现：`feature/FingerprintPoster.java`（913 行），几何数据 `res/raw/fp_paths.txt`（485 个顶点、总弧长 8,965.1667 px）。

| 功能 | 行为 | 入口 | 数据来源 | 持久化键 | 代码位置 |
|---|---|---|---|---|---|
| 四档字号 | 16 / 17 / 18 / 19 px，默认 18；非法值回落 18 | 指纹分区头部 | `PagePrefs.fpFontPx` | `fp_font_px` | `PagePrefs.java:357-383`、`FingerprintPoster.java:75-84` |
| 选书 | 标准对话框 + 搜索 + 多选，上限 50 本，选完重建指纹 | 指纹分区头部 | `NoteStore` 本地划线 | `fp_books`（集合） | `DeskPageView.pickFpBooks:1029-1165`、`PagePrefs.java:391-422` |
| 指纹绘制 | 485 顶点折线按字号与字宽落字，缺字形跳过；墨量用 256 级 LUT | 自动 | APK 内置几何 + 划线文本 | 无 | `FingerprintPoster.java:127-139` |
| 生成壁纸 | 顶栏「生成壁纸」，画布 480×800，前缀 `fp_` | 指纹头部 | 当前指纹位图 | 无 | `DeskPageView.enterFpWallpaper:567`、`BillWallpaper.PREFIX_FP:150` |
| 空态提示 | 「在上方选书以生成指纹」「选中的书还没有划线」「生成中…」 | 自动 | — | — | `FingerprintPoster.java:106-108` |
| 同步触发 | 选书后 `kickFpSync` 补拉划线（渲染期零网络） | 选书确认后 | `NoteSync` 既有异步路径 | 无 | `DeskPageView.kickFpSync:1173-1193` |

依赖说明：选书用标准 `Dialog` + `EditText`，仅在墨台改为 Activity 后成立（旧的不可聚焦覆盖窗拿不到输入法），见 `docs/FEATURES/desk.md` §「载体关系」。

---

## 六、设置项全集

### 6.1 墨台自身设置面板（`feature/DeskSettings.java`，585 行）

面板只有两组（`layout():152-250`）：

| 分组 | 行 | 控件 | 行为 |
|---|---|---|---|
| 内容模块 | 3 行模块名 | 复选 + `↑`/`↓` | 切换开关注入 `desk_on_mask`，上下移动写 `desk_order`；顶部置灰、底部置灰 |
| 阅读资产 | 只统计书架 | 复选 | 写 `asset_shelf_only` |
| 阅读资产 | 只统计读完 | 复选 | 写 `asset_finished_only` |
| 阅读资产 | 已移出的书 | 逐本恢复 + 全部恢复 | 写 `asset_hidden_ids` |

设置面板没有「启用墨台」行（已移到 APP 设置 → 自定义），也没有背景分组（已整组删除）。行种类常量 `K_SEP/K_HEAD/K_MOD/K_MOD_BT/K_ASSET_*` 在 `:90-101`，动作执行在 `activate():545-584`，Toast 出口 `consumeToast()` 恒返回 null（`:538-540`）。

### 6.2 落盘键清单

| 文件 | 键 | 类型 | 默认 | 作用 |
|---|---|---|---|---|
| `cfg` | `desk_enabled` | bool | true | 墨台总开关 |
| `cfg` | `desk_order` | CSV | `bill,rank,fp` | 模块顺序 |
| `cfg` | `desk_on_mask` | int | 3（bill\|rank） | 模块开关位 |
| `cfg` | `asset_shelf_only` | bool | false | 资产只统计书架 |
| `cfg` | `asset_finished_only` | bool | true | 资产只统计读完 |
| `cfg` | `asset_hidden_ids` | StringSet | 空 | 已移出统计的书 |
| `cfg` | `fp_font_px` | int | 18 | 指纹字号 |
| `cfg` | `fp_books` | StringSet | 空 | 指纹选书 |
| `cfg` | `menu_title` / `menu_unit` / `menu_top_n` / `menu_min_sec` / `menu_drop_off_shelf` / `menu_note_src` / `menu_excerpt_mode` / `menu_show_author` / `menu_show_duration` / `menu_show_progress` / `menu_footer` / `menu_blocks_mask` / `menu_font_title_serif` / `menu_font_body_mono` / `menu_fs_title` / `menu_fs_body` / `menu_fs_serial` / `menu_empty_action` | 混合 | 见 `MenuPrefs.java:36-104` | 墨单 13 项配置 |
| `cfg` | `menu_view` / `menu_footer_note` / `menu_period_mode` / `menu_period_start` / `menu_paid_algo` | 混合 | — | 墨单视图与周期状态 |
| `bills` | `bill_<mode>_<periodStart>` | JSON | — | 每期账单 |
| `menu_prices` | `bookId` | int（分） | — | 手填书价 |
| `menu_notes` | `bookId` | String | — | 每本书手动备注 |
| 已废弃 | `desk_bg_path` / `desk_bg_veil` | — | — | 背景图与白纱，仅保留不读 |

### 6.3 设置入口现状

- 墨台模块开关、资产过滤、指纹字号：墨台内右上角设置面板。
- 墨台总开关：APP 设置 → 自定义（`cb_desk_enabled`）。
- 墨单 13 项配置：**没有用户可达的设置入口**，历史上只经调试钩子 `wb_menu` 驱动（`docs/FEATURES/desk.md` §「遗留缺口」）。并入十雨时必须补齐。

---

## 七、交互、几何与绘制约束

- 触控统一在 `DeskPageView.onTouchEvent`（`:1266-1503`）：滚动手势按分区命中分流；列表态点击优先级为 顶栏设置 → 墨单头部（生成壁纸 / 周月 / 箭头）→ 账单页签 → 返回；设置态点击行走 `hitItem` + `activate`；壁纸态三个按钮。
- 长按阈值 550 ms（`:223`）；横向返回阈值 14 px（`:75`）与左滑比例 0.12（`:77`）。
- 分区内滚：阅读资产内滚 `inner`（`AssetSection:300-333`），墨单行在分区内排布。
- 几何：字号以 `SZ_* × unit` 表示，`unit = h × 0.0015`（文档 §「适配」）；海报固定 480×800（`BillWallpaper.java:59-60`、`FingerprintPoster.java:62-63`）。
- 绘制纪律：图标一律 Path/圆弧自绘，不用字符字形；纯黑白加三档灰；分区抬头有 3 px 左竖线（`DeskRenderer.drawHeadBar`）。
- 海报保存：MediaStore `Pictures/微读墨记`，兜底 `files/wallbill`；文件名前缀 `modan_` / `yuezi_` / `fp_`（`BillWallpaper.java:146-150`）；分享走 `ACTION_SEND` + `FLAG_GRANT_READ_URI_PERMISSION` + `createChooser`，并带 `FLAG_ACTIVITY_NEW_TASK`。

---

## 八、依赖无障碍服务与设备角色的部分

| 功能 | 依赖 | 位置 |
|---|---|---|
| 卡片上的「打开墨台」入口 | 无障碍服务透明窗 + 卡片绘制链 | `a11y/OverlayController`、`a11y/CardA11yService.showDesk()` |
| 墨台让位与恢复 | `noteOwnUiForeground(true/false)`、`endDesk(epoch)` 全在无障碍服务内 | `DeskActivity.java:137`、`:147`、`:214`、`:234` |
| 离开判定 | `onUserLeaveHint` 主判据 + `onStop` 兜底；灭屏与分享在途不算离开 | `DeskActivity.java:162-216` |
| 手机角色 | 墨台按设计只在墨屏端呈现；手机角色进入 `MainActivity` 会被显式拦截 | `shell/MainActivity.java:387-393`（文档引用） |
| 指纹选书输入框 | 需要可聚焦窗口与输入法（Activity 形态） | `DeskPageView.pickFpBooks:1029` |

并入十雨负一屏后，以上无障碍依赖应当整体去掉：负一屏是应用内页面，入口由桌面配置提供，不存在覆盖窗让位问题。

---

## 九、并入负一屏的三档归类

### 9.1 必须保留（直接决定产品价值与口径正确性）

1. 墨单两菜单与周期条（`BillSection` 头部与页签）——数据口径复杂，重做成本高，且是用户主要使用场景。
2. 收入算法三档与合计口径（`BillMoney` + `BillSection.billTotalLine`）——涉及进度快照、估算前缀、无价/免费三态，任何简化都会改变数字。
3. 手填书价（`MenuPrice`，独立 prefs、键=bookId、优先级最高、重同步不覆盖）——自导入书与网文没有官方价格，手填价是唯一真相。
4. 阅读资产的三键排序、折叠 10 本、双皮肤、合计三列、三条过滤（读完/在架/已移出）、长按移出与恢复、长按录价——D9–D19 口径已逐条上机验证。
5. 阅读指纹的四档字号、选书上限 50、485 顶点几何与 LUT 墨量——几何数据与算法是离线工程产物，重做代价最大。
6. 三套海报的保存与分享（前缀 `modan_`/`yuezi_`/`fp_`，MediaStore 落盘 + 系统分享）。
7. 模块系统本身：id、位、顺序、开关、未知值净化、位不复用。
8. 渲染期零网络纪律（`DeskModule.build` 只读本地缓存）。

### 9.2 可以简化

1. 墨台总开关 `desk_enabled`：负一屏已有启用开关与模块列表，总开关与其语义重叠，可合并。
2. 壁纸预览三态（`MODE_WALLPAPER`）可并入十雨既有的图片查看与分享能力，只保留「生成海报 → 预览 → 保存/分享」的数据链。
3. 自绘顶栏（齿轮 + 假粗文字 + 返回）可换用十雨 `InkPage` 顶栏。
4. 自绘设置面板 `DeskSettings` 可改为 `SettingsSpec` 声明表 + 现有渲染器，减少一套手写命中几何；开关入口保留在负一屏右上角。
5. 页签与文字按钮的 Canvas 自绘可换用 `InkWidgets` 既有控件，视觉按十雨设计令牌统一。
6. 分区内滚（`AssetSection.inner`）可交给负一屏统一滚动容器，减少一套命中偏移。

### 9.3 需要重做

1. 承载形态：独立 Activity + 覆盖窗让位 + 离开判定 → 十雨负一屏 `Screen`/`View`，删除 `DeskActivity`、`CardA11yService.showDesk/endDesk` 相关调用。
2. 坐标系：480×800 比例化 `unit` 与固定海报尺寸 → 十雨负一屏的宽度自适应与 `FileMetrics`/`InkPage` 几何。
3. 触控与命中：单 `onTouchEvent` 分区命中 → 十雨现有手势与部件命中；长按菜单改用 `InkDialogs.choice` 一类统一入口。
4. 设置项：`PagePrefs`/`MenuPrefs` 的键迁移为 `LauncherConfig` 的负一屏字段，并进 `SettingsSpec` 声明表；墨单 13 项此前没有界面，需新建声明组。
5. 数据迁移：读取旧工程 `cfg`、`bills`、`menu_prices`、`menu_notes` 与资产筛选键，转换为十雨配置；`desk_order`/`desk_on_mask` 转换为负一屏模块顺序与开关；缺失或非法值按现有净化规则回退。
6. 入口与默认值：三个模块默认开启（与微读现状不同：阅读指纹默认关），书架与最近图片作为可选、可排序模块，开关入口放在负一屏右上角。

---

## 十、迁移映射建议（供后续实施参考）

| 微读侧 | 十雨负一屏 |
|---|---|
| `desk_on_mask` 位 1/2/8 | 模块开关数组，顺序由 `desk_order` 给出 |
| `desk_enabled` | 负一屏启用开关（已有） |
| `menu_*` 13 项 | 新增「墨单」设置分组，逐项进 `SettingsSpec` |
| `menu_prices` / `menu_notes` | 独立偏好文件原样迁移，键仍用 bookId |
| `bills` 各期账 | 保留原结构迁移，避免重算历史 |
| `asset_shelf_only` / `asset_finished_only` / `asset_hidden_ids` | 「阅读资产」设置分组 |
| `fp_font_px` / `fp_books` | 「阅读指纹」设置分组 |
| 海报前缀 `modan_` / `yuezi_` / `fp_` | 保持文件名不变，便于用户已有图片继续留存 |

---

## 十一、待确认项

1. 三个模块「默认开启」与现状的差异：微读当前只有墨单与阅读资产默认开，阅读指纹默认关（`PagePrefs.java:90`）。并入后若要求三个全开，需要明确指纹在无选书时的空态是否可接受。
2. 十雨负一屏现有模块（书架、最近图片）与墨台三个模块的排序默认值尚未定义，需产品侧给出默认顺序。
3. 墨单 13 项配置在微读没有用户可达入口，其默认值是否等于用户期望值，缺少使用证据。
4. 历史账单是否需要在迁移后继续显示「估算 ⇄ 精确」的 `≈` 语义，需产品确认。
5. 阅读指纹的几何数据文件（`res/raw/fp_paths.txt`，9,394 字节）与渲染算法属离线工程产物，迁移时按文件与算法整体复制，不做改写。
6. 海报固定 480×800 是否随十雨负一屏宽度变化而重新排版，需设计确认。
