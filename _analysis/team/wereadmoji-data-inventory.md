# 微读墨记本地数据清点与迁移映射

只读清点，供十雨桌面内置版无感迁移使用。清点对象为 `D:\WorkBuddyStation\WereadMoji\weread-widget\app\src\main\java\com\inkread\weekread`、`docs/FEATURES`，以及 S4 真机上的已安装包 `com.inkread.weekread`。除本文件外未改动任何文件。

## 一、范围与方法

- 源码：`core`、`ui`、`net`、`settings` 目录下与持久化相关的类，逐文件检索 `getSharedPreferences`、`filesDir`、`getExternalFilesDir`、`JSONObject` 写入点。
- 文档：`docs/FEATURES` 共 11 份，重点 `stats-data.md`、`note-pick.md`、`month-rank.md`、`insight.md`、`navigation.md`。
- 真机：`adb shell run-as com.inkread.weekread` 导出 `shared_prefs` 与 `files` 清单，统计文件数量与体积，抽样读取 JSON 结构；仅统计，不记录密钥值。
- 体积单位为字节（B）；`du -ck` 输出按 KB 计。

## 二、SharedPreferences 全量清单

### 2.1 文件级清单

| 文件名 | 用途 | 设备体积 | 主要写入方 | 主要读取方 | 可否重建 |
| --- | --- | --- | --- | --- | --- |
| `cfg` | 主配置与统计缓存 | 110,946B | CardPrefs、PagePrefs、LockPrefs、MenuPrefs、StatsStore、TodoStore、UpdateChecker | 同左，另含 MainActivity、各卡片 | 部分可重建，密钥与用户偏好不可重建 |
| `notes` | 本记抽取状态、选书、导出偏好 | 3,571B | NoteStore、NoteExport | NoteStore、NoteExport、卡片渲染 | 抽取历史不可重建；导出偏好可重建 |
| `achv` | 成就卡配置 | 193B | AchievementPrefs | AchievementPrefs、成就卡 | 用户配置不可重建 |
| `bills` | 账单历史 | 38,565B | BillStore | BillStore、账单卡 | 可由接口按周期重拉，但历史周期数据仍以快照为准 |
| `menu_notes` | 每本书的墨单页脚注 | 65B（当前为空） | MenuPrefs 写入方法 | MenuPrefs 读取 | 用户文字，不可重建 |
| `menu_prices` | 每本书的墨单标价 | 113B | MenuPrice | MenuPrice | 用户输入，不可重建 |
| `menu_progress` | 墨单阅读进度快照 | 1,413B | ProgressLog | ProgressLog、墨单 | 由阅读状态派生，可重建 |
| `asset_books` | 资产书架聚合缓存 | 190,907B | AssetStore | AssetStore、资产卡 | 可由接口逐本重拉，耗时较长 |
| `power_cfg` | 系统省电相关设置的备份值 | 未见独立统计 | PowerSettingsManager | PowerSettingsManager | 设备本地设置，可省略 |
| `walls` | 壁纸池 | 290B | 源码中无写入点 | 源码中无读取点 | 遗留键，可省略 |

源码常量位置：`CardPrefs.java:69`、`PagePrefs.java:101`、`LockPrefs.java:48`、`MenuPrefs.java:33`、`StatsStore.java:26`、`TodoStore.java:24`、`AchievementPrefs.java:24`、`BillStore.java:22`、`MenuPrice.java:26`、`ProgressLog.java:41`、`AssetStore.java:64`、`UpdateChecker.java:104`、`FeatureGate.java:57`、`PowerSettingsManager.java:200`、`NoteStore.java:72`、`NoteExport.java:78`。

### 2.2 `cfg` 键清单

配置类键分为卡片显示、桌面页、锁屏、墨单、统计缓存、待办、更新七组。默认值取自 `getXxx` 的第二参数。

卡片与显示（`CardPrefs.java:73-729`）：

| 键 | 类型 | 默认值 | 语义 | 读取方 | 可否重建 |
| --- | --- | --- | --- | --- | --- |
| `card_enabled` | bool | true | 桌面卡总开关 | 桌面渲染、App 卡片 | 用户偏好，建议迁移 |
| `single_page_mode` | bool | false | 单页模式 | 桌面页 | 建议迁移 |
| `bind_targets` | int | `BIND_WEEK|BIND_BOOK`（位组合） | 卡片可绑定的统计对象 | 卡片设置 | 建议迁移 |
| `month_style` | int | `HEATMAP` | 月卡形态（热力或数字） | 月卡 | 建议迁移 |
| `note_card_size` | int | 17 | 本记字号档 | 本记卡 | 建议迁移 |
| `update_channel` | string | `stable` | 更新通道 | 更新检查 | 可省略 |
| `upd_cloud_fallback` | bool | true | 更新通道回退 | 更新检查 | 可省略 |
| `remote_role` | int | 0（关） | 遥控角色 | 遥控服务 | 建议迁移 |
| `remote_idle_timeout` | int | 300 | 遥控空闲断开秒数 | 遥控服务 | 建议迁移 |
| `remote_shake_enabled` | bool | false | 遥控摇一摇翻页 | 遥控服务 | 建议迁移 |
| `remote_shake_sens` | int | 1 | 遥控摇动灵敏度 | 遥控服务 | 建议迁移 |
| `remote_shake_lr_rev` | bool | false | 遥控左右反向 | 遥控服务 | 建议迁移 |
| `remote_shake_ud_rev` | bool | false | 遥控上下反向 | 遥控服务 | 建议迁移 |
| `shake_local_enabled` | bool | false | 本机摇一摇翻页 | 摇动接收 | 建议迁移 |
| `shake_local_mode` | int | 0 | 本机摇动模式 | 摇动接收 | 建议迁移 |
| `shake_ldir_left` / `_right` / `_up` / `_down` | int | 上一条、下一条、上一条、下一条 | 摇动四方向动作 | 摇动接收 | 建议迁移 |
| `shake_local_dir_*` | bool | 旧版方向开关 | 旧版键，供兼容读取 | 摇动接收 | 仅兼容读取 |
| `shake_pdir_*` | int | 旧版方向动作 | 旧版键，供兼容读取 | 摇动接收 | 仅兼容读取 |
| `phone_dark_mode` | bool | false | 手机端深色模式 | App 界面 | 可省略 |
| `bt_control_enabled` | bool | false | 蓝牙控制器总开关 | 蓝牙按键服务 | 建议迁移 |
| `bt_volkey_enabled` | bool | true | 音量键翻页 | 蓝牙按键服务 | 建议迁移 |
| `bt_flip_swap` | bool | false | 翻页键互换 | 蓝牙按键服务 | 建议迁移 |
| `bt_flip_vertical` | bool | false | 竖向翻页 | 蓝牙按键服务 | 建议迁移 |
| `bt_flip_repeat_ms` | int | 220 | 长按重复间隔 | 蓝牙按键服务 | 建议迁移 |
| `bt_type_interval_ms` | int | 30 | 连续输入间隔 | 蓝牙按键服务 | 建议迁移 |
| `card_pool_mask` | int | 31 | 卡片池启用位 | 卡片调度 | 建议迁移 |
| `switch_mode` | int | 0 | 卡片轮换方式 | 卡片调度 | 建议迁移 |
| `rank_shelf_only` | bool | false | 排行仅书架内 | 排行卡 | 建议迁移 |
| `book_pick` | string | 空 | 当前选书 id | 排行、本书卡 | 建议迁移 |
| `install_role` | int | 0 | 安装来源角色 | 首次引导 | 可省略 |

设备 `cfg` 中另有 `shake_axis_lr_enabled`、`desk_bg_path`、`desk_bg_veil` 等键，源码清点未覆盖写入位置，迁移时按未知键保留或忽略，见第九节。

桌面页（`PagePrefs.java:93-95`、`:274-283`、`:289-304`）：

| 键 | 类型 | 默认值 | 语义 | 可否重建 |
| --- | --- | --- | --- | --- |
| `desk_enabled` | bool | true | 桌面页启用 | 用户偏好，建议迁移 |
| `desk_order` | string | null | 模块顺序 CSV（bill、rank、todo、profile、note、annual 等） | 用户布局，建议迁移 |
| `desk_on_mask` | int | 账单位、排行位 | 模块显示位 | 用户布局，建议迁移 |
| `asset_shelf_only` | bool | false | 资产仅书架 | 建议迁移 |
| `asset_hidden_ids` | string set | 空 | 资产隐藏书目 | 建议迁移 |
| `asset_finished_only` | bool | true | 资产仅显示已读完 | 建议迁移 |
| `fp_font_px` | int | 18 | 书架字体像素 | 建议迁移 |
| `fp_books` | string set | 空 | 书架固定书目 | 建议迁移 |

锁屏（`LockPrefs.java:38-42`、`:100-101`、`:141`）：

| 键 | 类型 | 默认值 | 语义 | 可否重建 |
| --- | --- | --- | --- | --- |
| `lock_enabled` | bool | false | 锁屏开关 | 用户安全设置，必须迁移 |
| `lock_pin_salt` | string | 首次设置时随机生成 | PIN 盐值 | 必须迁移，缺失则旧 PIN 失效 |
| `lock_pin_hash` | string | 空 | `sha256(salt+pin)` | 必须迁移 |
| `lock_bg_path` | string | 空，渲染时回退 `Pictures/背景.jpg` | 锁屏背景路径 | 建议迁移 |

墨单（`MenuPrefs.java:96-127`、`:400-441`）：

| 键 | 类型 | 默认值 | 语义 | 可否重建 |
| --- | --- | --- | --- | --- |
| `menu_title` | string | `墨单` | 页眉标题 | 用户偏好，建议迁移 |
| `menu_unit` | string | `h` | 时长单位 | 建议迁移 |
| `menu_top_n` | int | 5 | 榜单条数 | 建议迁移 |
| `menu_min_sec` | int | 0 | 入选最短阅读秒数 | 建议迁移 |
| `menu_drop_off_shelf` | bool | true | 过滤已下架书 | 建议迁移 |
| `menu_note_src` | string | `auto_first` | 笔记来源 | 建议迁移 |
| `menu_excerpt_mode` | string | `mine` | 摘录模式 | 建议迁移 |
| `menu_show_author` | bool | true | 显示作者 | 建议迁移 |
| `menu_show_duration` | bool | true | 显示时长 | 建议迁移 |
| `menu_show_progress` | bool | false | 显示进度 | 建议迁移 |
| `menu_footer` | string | `barcode_note` | 页脚类型 | 建议迁移 |
| `menu_blocks_mask` | int | 全部块 | 组件块显示位 | 建议迁移 |
| `menu_font_title_serif` | bool | true | 标题衬线体 | 建议迁移 |
| `menu_font_body_mono` | bool | true | 正文等宽体 | 建议迁移 |
| `menu_fs_title` / `_body` / `_serial` | int | `FS_MID` | 三处字号档 | 建议迁移 |
| `menu_empty_action` | string | `placeholder` | 空态处理 | 建议迁移 |
| `menu_paid_algo` | string | `period` | 付费统计方式 | 建议迁移 |
| `menu_period_mode` | string | 空 | 统计周期 | 建议迁移 |
| `menu_period_start` | long | 0 | 周期起点秒 | 建议迁移 |
| `menu_view` | int | `VIEW_EXCERPT` | 视图形态 | 建议迁移 |

统计缓存（`StatsStore.java:26-33`、`:196-202`、`:251-262`）：

| 键 | 类型 | 默认值 | 语义 | 读取方 | 可否重建 |
| --- | --- | --- | --- | --- | --- |
| `api_key` | string | 空 | 微信读书接口密钥 | `StatsStore.getKey`、`WereadApi` | 必须迁移 |
| `card_period` | string | `weekly` | 桌面卡统计周期 | `loadCard` | 建议迁移 |
| `cache_order` | string | 空 JSON 数组 | 周/月缓存 LRU 顺序 | `prune` | 可省略 |
| `d_<mode>:<start>` | string | 无 | 周期统计原始回包 JSON | 统计页、月卡 | 可重建，建议迁移离线快照 |
| `t_<mode>:<start>` | long | 0 | 上键的抓取时间戳 | 缓存新鲜度判断 | 随数据迁移 |
| `stats_annual_<年>` | string | 无 | 年度统计回包 | 洞察页年度分区 | 可重建，建议迁移 |
| `stats_annual_t_<年>` | long | 0 | 年度抓取时间 | 同上 | 随数据迁移 |
| `stats_overall` | string | 无 | 累计统计回包 | 洞察页累计分区 | 可重建，建议迁移 |
| `stats_overall_t` | long | 0 | 累计抓取时间 | 同上 | 随数据迁移 |

周/月缓存容量 `CACHE_MAX=6`，超出按 `cache_order` 淘汰。

待办（`TodoStore.java:24-63`）：

| 键 | 类型 | 默认值 | 语义 | 可否重建 |
| --- | --- | --- | --- | --- |
| `todo_list` | string | 空 JSON 数组 | 待办条目 JSON 数组 | 用户内容，必须迁移 |
| `todo_next_id` | long | 1 | 下一条目编号 | 必须迁移，避免编号重复 |

更新与调试（`UpdateChecker.java:104-110`、`FeatureGate.java:57`）：

| 键 | 类型 | 默认值 | 语义 | 可否重建 |
| --- | --- | --- | --- | --- |
| `upd_last_check` | long | 0 | 上次检查更新时间 | 可省略 |
| `upd_remote_code` | int | 0 | 远端版本号 | 可省略 |
| `upd_remote_name` | string | 空 | 远端版本名 | 可省略 |
| `upd_remote_notes` | string | 空 | 远端更新说明 | 可省略 |
| `upd_remote_channel` | string | 空 | 远端通道 | 可省略 |
| `dbg_feature_gate` | int | -1 | 开发分支特性门 | 可省略，仅调试构建 |

省电：`power_cfg` 以 `cap_<键名>` 保存系统设置备份，键名随系统设置项变化，迁移省略。

### 2.3 `notes` 键清单

`NoteStore.java:101-122` 定义基础键；三类抽取槽共用同一套键并加后缀 `_i`（只看想法）、`_p`（选书档），桌面使用默认槽。

| 键 | 类型 | 默认值 | 语义 | 可否重建 |
| --- | --- | --- | --- | --- |
| `ideas_only` | bool | false | 旧版只看想法开关，读取时兼容映射到槽 1 | 建议迁移 |
| `batch_pos` | int | -1 | 当前批次游标 | 可省略，重新抽取即可 |
| `taken` | int | 0 | 已抽取条数 | 建议迁移，保持「今日已签」语义 |
| `day` | string | 空 | 抽取日期 | 建议迁移 |
| `cur_book` | string | 空 | 当前书 id | 建议迁移 |
| `cur_key` | string | 空 | 当前条目键 | 建议迁移 |
| `hist` | string | 空 JSON 数组 | 最近抽取历史，上限 64 条 `{b,k}` | 建议迁移 |
| `pick_book` | string | 空 | 选书档书目 | 建议迁移 |
| `batch_pos_i` / `_p` 等后缀键 | 同上 | 同上 | 想法档、选书档独立状态 | 按档迁移 |

导出偏好（`NoteExport.java:60-114`、`:247-250`）：

| 键 | 类型 | 默认值 | 语义 | 可否重建 |
| --- | --- | --- | --- | --- |
| `paper` | int | 1 | 导出纸张样式 | 建议迁移 |
| `sizeTier` | int | 1 | 导出字号档 | 建议迁移 |
| `showSign` | bool | true | 导出显示署名 | 建议迁移 |
| `showDate` | bool | true | 导出显示日期 | 建议迁移 |

### 2.4 `achv` 键清单

`AchievementPrefs.java:37-48`：

| 键 | 类型 | 默认值 | 语义 | 可否重建 |
| --- | --- | --- | --- | --- |
| `enabled` | bool | false | 成就卡开关 | 用户配置，必须迁移 |
| `week_kind` | int | 0 | 周档类型（0 关、1 全勤、2 狂读、3 自定义） | 必须迁移 |
| `week_min` | int | 0 | 周档阈值分钟 | 必须迁移 |
| `month_kind` | int | 0 | 月档类型 | 必须迁移 |
| `month_min` | int | 0 | 月档阈值分钟 | 必须迁移 |

### 2.5 `bills` 键清单

`BillStore.java:22-25`、`:38-51`、`:101-138`：

| 键 | 类型 | 默认值 | 语义 | 可否重建 |
| --- | --- | --- | --- | --- |
| `bill_<mode>_<periodStart>` | string | 无 | 单期账单 JSON | 可接口重拉，历史期以快照为准 |
| `K_YEAR_BACKFILLED` | bool | false | 年度账单补齐标记 | 可省略 |

保留策略：周账单 12 期，月账单 13 期。账单 JSON 字段：`mode`、`periodStart`、`generatedAt`、`placeholder`、`totalSec`、`dayCount`、`daySec[31]`、`items[]`；条目含 `bookId`、`title`、`author`、`readTimeSec`、`mineText`、`mineCount`、`hotText`、`progressPct`、`marketPriceFen`、`progressStartPct`、`progressEndPct`、`progressEndKnown`、`progressEndEst`、`progressStartEst`。

### 2.6 `menu_notes`、`menu_prices`、`menu_progress` 键清单

| 文件 | 键 | 类型 | 默认值 | 写入方 | 读取方 | 可否重建 |
| --- | --- | --- | --- | --- | --- | --- |
| `menu_notes` | 书 id | string | 空 | `MenuPrefs` 写入方法（`:400` 起） | `MenuPrefs` 读取 | 用户文字，必须迁移 |
| `menu_prices` | 书 id | int（分） | -1 | `MenuPrice.java:43-54` | `MenuPrice` | 用户输入，必须迁移 |
| `menu_progress` | 书 id | string，格式 `periodStart:pct[:g1]` | 空 | `ProgressLog.java:48-62` | `ProgressLog`、墨单 | 阅读状态派生，可重建 |

### 2.7 `asset_books` 键清单

`AssetStore.java:64-67`：

| 键 | 类型 | 默认值 | 语义 | 可否重建 |
| --- | --- | --- | --- | --- |
| `books` | string | 空 JSON 数组 | 资产书架条目数组 | 可逐本重拉，耗时较长；建议迁移 |
| `refreshed_at` | long | 0 | 上次刷新时间 | 可省略 |

条目 JSON 字段：`id`、`title`、`author`、`sec`、`pct`、`fin`、`at`（类注释与 `toJson` 定义）。

## 三、filesDir、cacheDir 与外部目录 JSON 清单

| 路径 | 结构与字段 | 设备体积 | 写入与更新时机 | 失效与重建 | 迁移建议 |
| --- | --- | --- | --- | --- | --- |
| `book.json` | 单对象：`bookId`、`title`、`author`、`deepLink`、`coverUrl`、`progress`、`readingSec`、`chapterUid`、`chapterIdx`、`chapterTitle`、`readUpdateTime`、`fetchedAt`、`finishTime`、`finished` | 518B | `BookStore.save`，打开本书或刷新进度时 | 可重拉进度接口重建 | 可省略 |
| `shelf.json` | `{savedAt, books:[{bookId,title,author,deepLink,cover,readUpdateTime}]}`，按最近阅读排序，最多 200 本 | 66,618B | `BookStore.saveShelf`（`:114-118`） | 书架接口重拉；TTL 6 小时，手动刷新最短 10 分钟 | 可省略，保留价值低 |
| `shelf_ids.json` | `{savedAt, ids:[bookId...]}`，全量书架 id | 15,981B | `BookStore` 书架刷新时（`:196-212`） | 随书架重拉 | 可省略 |
| `ch_<bookId>.json` | `{bookId,synckey,chapterUpdateTime,chapters:[{chapterUid,chapterIdx,updateTime,title,wordCount,price,isMPChapter,paid,level}]}` | 27 个共 276KB | `BookStore.saveChapters`（`:330-337`） | 章节目录接口重拉；书籍更新后失效 | 可省略 |
| `nt_index.json` | `{savedAt,v:3,books:[{bookId,noteCount,reviewCount,title,author,cover,sort,charCount,ideaChars,centPrice,priceY,origY,free,progress}]}`，索引版本 `v=3` | 76,814B | `NoteStore` 索引刷新，`/user/notebooks` 返回 count=300 | 接口重拉；版本变化时按新版本覆盖 | 可迁移，索引重建需多次请求 |
| `nt_<bookId>.json` | `{synckey,fetchedAt,marks:[{bookId,bookmarkId,createTime,chapterUid,colorStyle,type,markText,range,...}]}` | 220 个共 2,316KB | 划线增量同步，按 `synckey` 合并 | TTL 2 小时；`synckey` 与索引不一致时重拉 | 可迁移，纯网络重建成本高 |
| `ni_<bookId>.json` | `{fetchedAt,ideas:[{reviewId,content,abstract,range,chapterUid,chapterIdx,chapterName,createTime,star}]}` | 55 个共 240KB | 想法增量同步 | TTL 6 小时 | 可迁移 |
| `nt_batch.json`、`nt_batch_i.json`、`nt_batch_p.json` | `{savedAt,items:[{b:bookId,k:bookmarkId}]}` | 1,892B、1,816B、36B | 本地抽取批次写入 | 可本地重建，无网络 | 可省略 |
| `files/walls/` | 空目录 | 0B | 无源码写入点 | 遗留 | 可省略 |
| `cacheDir` | 未发现业务 JSON 或图片缓存 | 0B | 无 | 无 | 可省略 |
| 外部 `files/covers/` | `<bookId>.jpg` 封面 | 4 个共 276KB | 封面拉取后缓存 | 可重新下载 | 可省略 |
| 外部 `files/export/` | `benji_*.png` 本记导出图 | 按需生成 | 导出时写入 | 用户文件，非状态 | 可选迁移 |
| 外部 `files/card_debug.log` | 调试日志 | 17,853B | 调试构建写入 | 可删除 | 可省略 |

`filesDir` 合计约 2,944KB。文件写入没有统一版本号，只有 `nt_index.json` 内含 `v=3`。

## 四、微信读书接口密钥

- 存储位置：`cfg` 的 `api_key` 键，字符串，默认空。读取入口为 `StatsStore.getKey(Context)`（`StatsStore.java` 密钥段），写入入口为 `StatsStore.setKey`（`commit` 同步写）。
- 注入路径：调试构建可在启动时从 Intent 读取 `api_key` 额外字段写入，代码位于 `MainActivity.java:553-563`，外层以 `isDebuggableBuild()`（`MainActivity.java:861`，检查 `ApplicationInfo.FLAG_DEBUGGABLE`）与 `FeatureGate` 开关限制。正式构建没有该入口。
- 使用路径：`net/WereadApi.java:493` 将密钥拼为 `Authorization: Bearer <key>`；所有统计、账单、书架、笔记请求共用。
- 缺失或失效：`StatsStore.getKey` 为空时，`BillScheduler.java:189` 直接跳过；`AssetRefresher` 返回 `no_api_key`；界面显示未配置或密钥无效提示。密钥失效由接口 401 触发缓存降级与提示。
- 更换密钥时会清理个人数据：书架快照与全量 id、书籍进度、章节目录（`BookStore.clear`，`:350-359`）、统计缓存（`StatsStore.clearCache`）、账单（`BillStore.clear`）。迁移时必须同时迁移密钥与数据，否则会出现「新账号看到旧缓存」。
- 设备现状：`api_key` 存在，前缀 `wrk-`，长度 28；具体值不记录。

## 五、数据引用关系

- `bookId` 是主键，贯穿 `shelf.json`、`shelf_ids.json`、`book.json`、`ch_<id>.json`、`nt_index.json`、`nt_<id>.json`、`ni_<id>.json`、`asset_books`、`bills.items[].bookId`、`menu_notes`、`menu_prices`、`menu_progress`、`notes.cur_book`、`nt_batch.items[].b`。
- 划线键 `bookmarkId` 形如 `<bookId>_<chapterUid>_<range>`，部分为 `CB_` 前缀的合成 id；`nt_batch.items[].k` 引用该键，`hist[].k` 同。迁移后若只搬批次而不搬 `nt_<id>.json`，抽取会解析为空，因此批次与对应划线文件必须同批迁移。
- 想法条目的 `reviewId` 形如 `<bookId>_<随机串>`，只在 `ni_<id>.json` 内使用，不跨文件引用。
- 账单期键由 `mode + periodStart` 组成，条目引用书籍但不引用划线。
- 成就按 `week_kind/month_kind` 与阈值计算，不落书 id。
- 墨单进度键为书 id，值为周期起点、百分比与可选生成标记；与 `book.json.progress` 语义不同，后者是接口返回的整书进度。
- 风险：接口下架或删除的书会在多个文件留下孤儿条目；迁移时应允许悬空引用，读取端按空处理，不做级联删除。

## 六、迁移优先级

必须迁移（用户创作或不可重建）：

- `cfg.api_key`。
- `cfg.lock_enabled`、`lock_pin_salt`、`lock_pin_hash`、`lock_bg_path`；缺盐值会使旧 PIN 无法验证。
- `menu_notes` 全部书 id 键（页脚注为手写内容）与 `menu_prices` 全部书 id 键（手写标价）。
- `cfg.todo_list`、`todo_next_id`。
- `achv.enabled`、`week_kind`、`week_min`、`month_kind`、`month_min`。
- `notes` 中 `taken`、`day`、`hist`、`cur_book`、`cur_key`、`pick_book` 及三个槽位的批次状态；保留「今日不重复」与选书档。

可迁移（可重建但有价值）：

- `cfg` 中卡片、桌面页、墨单、遥控、摇动、蓝牙、更新通道等用户偏好；重建即恢复默认，但与用户预期不符。
- `bills` 十二期周账单与十三期月账单；接口可重拉，历史期可能已不可得。
- `asset_books`；逐本重拉成本高。
- `stats` 周期缓存、年度与累计段；重拉耗流量且离线不可用。
- `nt_index.json`、`nt_<id>.json`、`ni_<id>.json`；离线阅读与抽取依赖。
- `notes` 导出偏好 `paper`、`sizeTier`、`showSign`、`showDate`。
- `menu_progress` 进度快照。
- `nt_batch*.json` 当前批次，迁移成本低。

可省略（可重建或设备相关）：

- `shelf.json`、`shelf_ids.json`、`book.json`、`ch_<id>.json`。
- `covers/` 封面图片。
- `card_debug.log`、`power_cfg`、`upd_*`、`dbg_feature_gate`、`cache_order`。
- `walls.xml` 遗留键与空 `files/walls/` 目录。
- `install_role` 等一次性引导状态。

## 七、迁移通道建议

采用「整包 JSON + 文件清单」的两段式导入，避免逐项网络重建。

1. 导出包结构：`manifest.json` 与 `payload/`。`manifest.json` 记录 `schemaVersion`（首版 1）、`sourcePackage`、`sourceVersionCode/Name`、`generatedAt`、`prefs`（文件到键值对象的映射）、`files`（路径、字节数、sha256）、`totals`（各文件条目数）。
2. 大小控制：整包预计 3MB 至 5MB（不含封面与日志）；超过 20MB 时按书目分片，`nt_<id>.json` 按 `bookId` 排序分片，每片 500 本。
3. 校验：导入前校验整包 sha256、逐文件 sha256、JSON 可解析、`nt_index.books` 条目数与 `nt_<id>.json` 文件数一致；不通过则中止并保留原数据。导入写入临时目录 `filesDir/weread-import/`，校验通过后再原子替换 `filesDir/weread/`。
4. 版本兼容：`manifest.schemaVersion` 只接受小于等于当前版本；`nt_index.v` 保留原值，读取端按 3 处理。旧键名映射：`notes.ideas_only=true` 映射到槽 1，`shake_local_dir_*` 与 `shake_pdir_*` 映射到新的 `shake_ldir_*`，`menu_period_mode` 与 `menu_period_start` 保留原值。未知键写入 `legacy` 命名空间，不丢弃。
5. 冲突策略：以导出包的 `generatedAt` 与当前数据时间取新，不静默覆盖；迁移完成写入 `migration` 标记，重复导入直接跳过。
6. 回滚：替换前把现有 `filesDir/weread/` 重命名为 `weread-backup-<时间>`，保留 7 天；导入失败恢复原名。
7. 逐项导入仅用于小数据补迁（`menu_notes`、`menu_prices`、`todo_list`），由设置页提供入口；大批量数据一律整包。

## 八、与十雨桌面 LauncherConfig 的边界

`LauncherConfig` 为单键 JSON，启动时整体反序列化，适合小体积、跨模块共享、需要随配置备份的设置；不适合高频写入或数 MB 级内容。

放入 `LauncherConfig`：

- `integrations.wereadApiKey`：密钥唯一来源，避免与旧 `cfg.api_key` 双份漂移；迁移后旧键可删除。
- 与桌面渲染直接相关的开关：卡片池、卡片周期、月卡形态、桌面页模块顺序与显示位、墨单标题与字号等；这些设置由桌面与设置页共享。
- `wereadMigration` 元数据：`schemaVersion`、`importedAt`、`sourceVersion`、`sourcePackage`、`checksum`。

放入独立文件（建议 `filesDir/weread/`）：

- `index.json`、`marks/<bookId>.json`、`ideas/<bookId>.json`、`chapters/<bookId>.json`、`shelf.json`、`shelf_ids.json`、`book.json`。
- `bills.json`、`assets.json`、`stats/<key>.json`。
- `hexport-prefs.json`（`paper`、`sizeTier`、`showSign`、`showDate`）。
- `menu_notes.json`、`menu_prices.json`、`menu_progress.json` 若后续仍按书 id 频繁更新，保留独立文件；若改为低频，可并入 `LauncherConfig` 之外的 `weread-prefs.json`。

理由：`nt_index` 约 77KB、划线合计 2.3MB、资产缓存 191KB，放进单键 JSON 会使每次配置保存都重写整包，墨水屏设备的写放大与启动解析成本都不可接受；独立文件可按书增量更新，损坏影响面也更小。

边界规则：用户可见且跨模块读取的设置进 `LauncherConfig`；以书 id 为键、按需增删的集合进独立文件；仅缓存与调试数据不入迁移包。

## 九、风险与待确认

- 密钥明文存放于 `cfg` 与迁移包；迁移包应加密或至少限制在应用私有目录，导入后删除临时包。
- 正式构建无密钥注入入口；旧设备的 `cfg.api_key` 是唯一来源，迁移失败需要用户重新填写。
- 文档与源码存在分歧：`docs/FEATURES/note-pick.md` 写明桌面入口固定槽 0，源码注释标注 R1 起桌面跟随选书；迁移时以源码行为为准，避免抽取状态错位。
- `cfg` 内统计缓存可能继续增长（设备已达 110KB），迁移包需设单文件上限；超过部分只保留最近六个周期。
- 划线规模在极端用户上可能远超样本（220 本 2.3MB）；按 1 万本估算约 100MB，整包方案需要分片与体积上限。
- 部分设备键（`shake_axis_lr_enabled`、`desk_bg_path`、`desk_bg_veil`）源码清点未定位写入位置，迁移按未知键原样保留。
- `menu_progress` 值为字符串三元组，周期起点按本地时区计算，跨时区迁移需要重算显示日期。

## 十、核对记录

- 源码检索命令：`Select-String -Pattern 'getSharedPreferences\\("(cfg|notes|achv|bills|menu_notes|menu_prices|menu_progress|asset_books|power_cfg|walls)"'`，覆盖 `core`、`ui`、`net`、`settings`。
- 真机命令：`adb shell run-as com.inkread.weekread ls -l shared_prefs`、`cat shared_prefs/*.xml`、`du -sk files`、`ls files/nt_*.json | wc -l`、抽样 `cat` 各 JSON 首部。
- 体积与条目数取自 2026-10-20 的 S4 设备快照；`nt` 220 个、`ni` 55 个、`ch` 27 个、封面 4 个、`filesDir` 2,944KB。
- 未验证项：正式构建升级后的密钥保留行为、超大划线库的导入耗时、分片导入的中断恢复。
