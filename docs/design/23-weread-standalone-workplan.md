# 微读能力独立承载与负一屏墨台交互细化方案

本文把 `docs/design/22-weread-standalone-plan.md` 的五个阶段拆到文件与接口一级，并把负一屏墨单、阅读资产、阅读指纹三个模块的交互补齐，做到在负一屏内直接操作，不进入详情页。

## 一 现状核查

内核 k2 已有的动作能力比十雨侧用到的多，逐项列出实测结果。

`kernel/desk/DeskActions.java` 定义动作编号：`ALGO_BASE = 100` 三档收入算法，`ASSET_SORT_BASE = 110` 三种排序，`ASSET_TOGGLE = 120` 展开收起，`FP_FONT_BASE = 130` 四档字号，`FP_PICK = 140` 选书，`WALLPAPER = 150` 生成壁纸，`BILL_WEEK = 160` 与 `BILL_MONTH = 161` 周期切换，`BILL_PREV = 162` 与 `BILL_NEXT = 163` 前后翻页，`BILL_TAB = 164` 摘录菜单与读书菜单切换。

`kernel/desk/DeskPage.java` 的 `hitTest` 已对三个分区逐项判命中并返回上述编号，`onAction` 已把动作落到分区状态并调 `host.requestRedraw()`。分区内部的命中方法也已具备，账单分区有 `hitWallpaper`、`hitArrow`、`hitTab`、`hitAlgo`、`hitPriceRow`，资产分区有 `hitRow`、`hitSort`、`hitToggle`、`hitWall`，指纹分区有 `hitFont`、`hitPick`、`hitWall`。

十雨侧 `feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/ui/negative/WereadDeskSectionView.kt` 的 `handleTap` 已调用 `hitTest` 与 `onAction`，但存在五处缺口。

坐标映射错误。`handleTap` 用的是 `current.hitTest(x, y + headerHeight)`，而视图渲染时用的是 `render(canvas, width, height, sectionTop)`，两者偏移基准不同，点击位置与命中区域错开一个分区间距。正确换算是 `hitTest(x, y + sectionTop)`。

动作未按分区过滤。`hitTest` 遍历整页分区，账单模块的点击可能命中资产分区的排序按钮，结果改到别的模块状态。

状态不持久化。`onAction` 改的是 `DeskPage` 内存状态，负一屏重进后周期、排序、展开、字号、算法全部回到默认值。

壁纸与选书没有落点。动作 `WALLPAPER` 与 `FP_PICK` 交给 `host.openPage` 之后，十雨侧 `WereadDeskHostImpl.openPage` 需要自行实现，目前是空实现或跳转。

三个模块各自构造一个 `DeskPage`，每个都读全量原盘数据，内存与首帧耗时是单页的三倍。

## 二 负一屏交互目标

目标是把墨台页能做的操作搬到负一屏，逐模块列出。

| 模块 | 交互 | 触发位置 | 反馈 | 持久化键 |
| --- | --- | --- | --- | --- |
| 墨单 | 周与月切换 | 周期选择行 | 立即重绘对应周期 | `kernel_prefs.json` 的 `bill_period` |
| 墨单 | 上一期与下一期 | 周期行两侧箭头 | 立即重绘 | 同上 |
| 墨单 | 摘录菜单与读书菜单 | 分区页签 | 立即重绘 | `menu_view` |
| 墨单 | 收入算法三档 | 收入算法行 | 金额列重算 | `menu_paid_algo` |
| 墨单 | 长按书目行录入实际书价 | 书目行 | 弹输入框，写入后金额重算 | `menu_prices.json` |
| 墨单 | 生成壁纸 | 分区右上按钮 | 负一屏内预览层，可保存到相册 | 不持久化 |
| 阅读资产 | 三种排序 | 排序栏 | 列表重排 | `asset_sort` |
| 阅读资产 | 展开与收起 | 列表末尾 | 行数变化 | `asset_expanded` |
| 阅读资产 | 点书目行查看摘录 | 书目行 | 负一屏内浮层显示该书摘录与想法 | 不持久化 |
| 阅读资产 | 生成壁纸 | 排序栏右侧 | 同墨单 | 不持久化 |
| 阅读指纹 | 字号四档 | 字号档位条 | 立即重绘 | `fp_font_px` |
| 阅读指纹 | 选书 | 指纹图 | 负一屏内浮层选择书目 | `fp_books` |
| 阅读指纹 | 生成壁纸 | 分区右上按钮 | 同墨单 | 不持久化 |

三项交互需要负一屏内浮层，分别是实际书价输入、书目摘录、指纹选书。浮层用十雨现有的对话框与底部弹层实现，不新建页面，也不跳转。

## 三 负一屏改造项

按文件列出改造内容与顺序。

第一项，`WereadDeskSectionView.kt` 的坐标换算改为 `sectionTop` 基准，并在 `hitTest` 返回后按本模块允许的动作集合过滤，账单模块只接受 `BILL_*`、`ALGO_BASE` 至 `ALGO_BASE + 2`、`WALLPAPER`，资产模块只接受 `ASSET_*`、`WALLPAPER`，指纹模块只接受 `FP_*`、`WALLPAPER`。

第二项，同一负一屏内的三个模块共用一个 `DeskPage`。新增 `WereadDeskPageHolder` 对象，按根目录缓存单例，引用计数为零时 `release()`。三个 `WereadDeskSectionView` 从该对象取页，避免重复读盘。

第三项，状态持久化。新增 `WereadDeskState` 读写 `kernel_prefs.json` 的 `bill_period`、`asset_sort`、`asset_expanded`、`fp_font_px`、`menu_view`、`menu_paid_algo`，页面 `bind()` 完成后把值注入分区，`onAction` 之后写回。键名与微读原版偏好一致，保证与微读墨记共用同一份偏好文件时互不冲突。

第四项，`WereadDeskHostImpl.openPage` 的三个入口改为负一屏内处理。`wallpaper` 打开 `WereadWallpaperSheet`，`fingerprint` 打开 `WereadBookPickerSheet`，`back` 与 `settings` 维持现有跳转。同时新增 `requestPriceEntry(bookId)` 回调供长按书价使用。

第五项，新增三个浮层组件，放在 `feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/ui/negative/`。`WereadPriceSheet` 复用设置页的输入框样式，写入 `menu_prices.json` 后调 `requestRedraw`。`WereadNoteSheet` 读该书的 `nt_<id>.json` 与 `ni_<id>.json`，分摘录与想法两栏。`WereadBookPickerSheet` 列出书架书目支持多选，写入 `fp_books`。

第六项，`WereadWallpaperSheet` 生成壁纸。实现方式是把 `DeskPage` 对应分区渲染到指定尺寸的 `Bitmap`，加页眉页脚后由用户选择保存到相册，复用十雨已有的图片保存能力。

第七项，交互提示。长按书目行一百二十毫秒后触发书价输入，与微读原版 `DeskPageView` 的长按阈值一致，阈值常量集中在 `WereadDeskSectionView` 的伴生对象。

## 四 独立承载细化

五个阶段拆到类与方法一级。

### S1 取数层

新建模块目录 `core/weread-sync/src/main/java/com/shuiyu/octoberrain/core/weread/api/`，文件与职责如下。

`WereadHttp.kt` 负责连接与鉴权，方法 `fun get(path: String, query: Map<String, String>): Result<String>`，内部读 `LauncherConfig.integrations.wereadApiKey`，超时八秒，失败重试一次。

`WereadApi.kt` 平移微读 `net/WereadApi.java`，保留九个入口并按 Kotlin 重写为挂起函数：`fetchWeekly(key)`、`fetchDetail(key, mode, baseTime)`、`fetchShelf(key)`、`fetchShelfIds(context, key)`、`fetchProgress(key, bookId)`、`fetchChapters(key, bookId)`、`fetchBestBookmarks(key, bookId)`、`fetchBook(key, bookId)`、`fetchDetailBlocking(key, mode, baseTime)`。去掉对 `Context` 的依赖，需要设备标识的入参改为构造注入 `deviceId: String`。

`NoteSync.kt` 平移微读 `net/NoteSync.java`，方法 `syncAll(key): SyncResult`、`syncBook(key, bookId): SyncResult`、`fetchAllNotebooks(key): List<Notebook>`。

`WereadParsing.kt` 承接 JSON 解析，字段名与微读原版一致，缺字段返回空集合。

验收：三个类各有一组单测，用离线 JSON 样例覆盖正常、缺字段、字段类型不符三种输入。

### S2 存储与调度

新建目录 `core/weread-sync/src/main/java/com/shuiyu/octoberrain/core/weread/store/`。

`WereadStore.kt` 提供根目录与文件定位，方法 `root(): File`、`fileOf(name: String): File`、`readJson(name: String): String?`、`writeJson(name: String, body: String)`，写入走临时文件加原子替换。

`ShelfStore.kt` 写 `shelf.json`、`shelf_ids.json`、`book.json`，方法 `saveShelf(books)`、`saveIds(ids)`、`saveCurrent(book)`、`load()`。

`StatsStore.kt` 写 `stats.json` 与 `kernel_prefs.json` 的 `stats_overall`、`stats_annual_<year>`、`d_<mode>:<base>`、`t_<mode>:<base>`。

`BillStore.kt` 写 `bills.json` 与 `kernel_prefs.json` 的 `bill_weekly_*`、`bill_monthly_*`，并提供 `savePrice(bookId, fen)` 写 `menu_prices.json`。

`AssetStore.kt` 写 `assets.json` 与 `kernel_prefs.json` 的 `asset_books`。

`NoteStore.kt` 写 `nt_index.json`、`nt_<id>.json`、`ni_<id>.json`。

`WereadScheduler.kt` 承接刷新排期，方法 `schedule()`、`cancel()`、`refreshNow(force: Boolean)`，周期取 `effectiveWereadRefreshMinutes`，执行体按「本周 → 本月 → 书架 → 账单 → 资产 → 笔记」顺序调用 S1，失败按三十分钟、一小时、两小时退避，连续三次失败后暂停并置标志。

验收：存储类单测覆盖写入、覆盖写、损坏文件读取；调度类用假时钟验证退避与暂停。

### S3 仓库统一

新建 `core/weread-sync/src/main/java/com/shuiyu/octoberrain/core/weread/WereadRepository.kt`，方法 `shelf(limit)`、`bill()`、`rank(limit)`、`fingerprint()`、`notes(limit)`、`todos()`、`assets()`、`currentBook()`。

读取顺序为原盘文件、内存缓存、网络。原盘命中时返回并记录时间戳；未命中时调 S1 拉取、写盘后返回；网络失败返回空并保留上次缓存。

`feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/ContentSourceImpl.kt` 的微读方法改为委托 `WereadRepository`，删除 `KernelWereadReader` 与 `networkBill` 一类分支。`KernelDataSourceImpl` 同样改为先经仓库再回退内核读取。

验收：仓库单测覆盖三条读取来源的优先级与失败回退。

### S4 页面与入口

`Destination` 已有 `Book`、`Note`、`Todo`、`NegativeSettings`。本阶段补三个页面的数据绑定：`BookScreen` 显示书名、作者、封面、阅读时长、进度与摘录条数；`NoteScreen` 分摘录与想法两栏并支持按书筛选；`TodoScreen` 显示条目、完成状态与编号。

组件「打开桌面」按钮与负一屏各分区「设置」入口改跳十雨自身页面，不再调起微读墨记。新增 `Destination.WereadDesk`，承载墨台三分区的只读全屏视图，供需要整页浏览时使用。

验收：不装微读墨记时四个入口均可打开且有内容。

### S5 迁移与验收

设置页新增两个入口，「从微读墨记导入」保留现有导出通道，仅在检测到微读墨记且版本不低于 48 时显示；「重新同步」调用 `WereadScheduler.refreshNow(true)`，显示进度与结果。

门禁脚本 `scripts/verify-1.0.x.ps1` 增加无微读场景：卸载微读墨记后执行一遍完整验收，断言安装包内不含 `com.inkread` 字符串、五个负一屏卡片与五种组件形态均有内容。

文档补 `docs/23` 与 `docs/17` 的对应章节。

## 五 排期

| 阶段 | 内容 | 人日 | 依赖 |
| --- | --- | --- | --- |
| N1 | 负一屏坐标、动作过滤、单页共享 | 2 | 无 |
| N2 | 状态持久化与三个浮层 | 3 | N1 |
| N3 | 壁纸生成与长按书价 | 2 | N1 |
| S1 | 取数层平移 | 3 | 无 |
| S2 | 存储与调度平移 | 4 | S1 |
| S3 | 仓库统一与取数替换 | 3 | S2 |
| S4 | 页面绑定与入口改跳 | 3 | S3 |
| S5 | 迁移降级与门禁补充 | 3 | S4 |

N 系列与 S 系列可以并行，合计二十三人日。建议顺序为 N1、S1、N2、S2、N3、S3、S4、S5。

## 六 需要确认的取舍

第一项，壁纸生成的产出。原版是生成 PNG 后分享或保存，保存路径在系统相册。十雨可以只保存到相册并提示，也可以加预览层二次确认。后者交互更完整，前者工作量少一半。

第二项，阅读资产点书目看摘录的呈现。负一屏内浮层可以做成半屏弹层或全屏覆盖。半屏弹层保留上下文的可见性，全屏覆盖更接近墨台页的整页浏览。

第三项，长按录入实际书价是否本期实现。原版这一项用于修正估算收入，属于精度增强，与主流程无关。

第四项，TOMO 桌面场景的边界。微读墨记在 TOMO 桌面运行时通过无障碍悬浮卡片提供功能，十雨作为桌面自身拥有组件与负一屏，两者载体不同。需要确认目标是把十雨桌面内的能力做到与微读墨记在 TOMO 上等价，还是要求十雨在非默认桌面时也能悬浮显示卡片。
