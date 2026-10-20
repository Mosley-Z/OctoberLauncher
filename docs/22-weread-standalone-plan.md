# 十雨桌面独立承载微读墨记能力的方案

本文回答一个问题：用户不安装微读墨记，十雨桌面能否独立提供微读墨记的全部功能，以及需要吸收 V1.3.1Beta 的哪些代码、如何划分职责、分几阶段完成。

## 一 目标与判定标准

不安装微读墨记 APK 时，下列能力在十雨桌面内全部可用，是本次目标的判定标准。

桌面整体卡片五种形态完整渲染，分别是本周、本月、本书、本记、待办。负一屏五个卡片完整可用，分别是书架、最近图片、墨单、阅读资产、阅读指纹。书籍页、本记页、待办页提供条目的完整浏览。密钥在十雨设置页填写，书封与统计由十雨自行获取与缓存。断网时显示上次同步的内容。

## 二 现状依赖盘点

当前十雨有六处依赖微读墨记的存在，逐项列出。

迁移导入依赖微读的 `ExportActivity` 导出加密包，十雨 `HostWereadImport` 调起后读取。静默共存依赖微读的 `SilentService` 判定十雨版本号后自我收敛。配置回写依赖微读的 `WritePrefsHandler` 把十雨设置写回微读偏好。墨台入口依赖微读 `DeskActivity` 承接点击。原版悬浮卡片依赖微读的无障碍服务，共存状态下已被停用。数据刷新依赖微读进程内的 `BillScheduler` 与 `AssetRefresher`。

结论：墨台渲染已由内核 jar 解决，剩余五项属于「数据从哪来、状态写到哪去」，可以整体搬到十雨。

## 三 可吸收的资产

微读 V1.3.1Beta 源码规模实测如下，单位为文件数与体积。

| 包 | 文件数 | 体积 | 处置 |
| --- | --- | --- | --- |
| `net` | 4 | 69 KB | 整体吸收，`WereadApi` 与 `NoteSync` 是网络能力核心 |
| `core` | 32 | 430 KB | 挑取存储与调度，界面相关部分丢弃 |
| `kernel` | 42 | 591 KB | 已平移为 k2 jar，继续复用 |
| `feature` | 22 | 603 KB | 已由 k2 与十雨组件覆盖，仅取排版常量 |
| `a11y` | 11 | 269 KB | 不吸收，属于悬浮窗与无障碍交互 |
| `shell` | 10 | 331 KB | 不吸收，属于微读自身界面 |
| `ui` | 13 | 169 KB | 只取 `InkTheme` 的排版单位与配色常量 |
| `silent` | 15 | 56 KB | 不再需要，独立后无共存判定 |
| `bridge` | 10 | 103 KB | 保留导出通道，供已装微读的用户一次性迁移 |
| `remote`、`update` | 16 | 179 KB | 不吸收 |

可直接复用的具体类，按作用列出。

`net/WereadApi.java` 28 KB，提供 `fetchWeekly`、`fetchDetail`、`fetchShelf`、`fetchShelfIds`、`fetchProgress`、`fetchChapters`、`fetchBestBookmarks`、`fetchBook`、`fetchDetailBlocking` 九个入口。`net/NoteSync.java` 22 KB，提供 `fetchAllNotebooks` 与划线、想法的增量同步。`core/StatsStore.java` 16 KB 持周月统计，`core/BillStore.java` 10 KB 与 `core/BillScheduler.java` 55 KB 持账单抓取与估算，`core/AssetStore.java` 9 KB 与 `core/AssetRefresher.java` 15 KB 持资产，`core/BookStore.java` 17 KB 持书架。`core/MenuPrefs.java` 23 KB 持墨单十三项外观，`core/PagePrefs.java` 22 KB 持墨台模块顺序与开关。

## 四 目标架构

十雨内新增一层 `core/weread-sync`，承接取数与存储，渲染仍交给内核 jar，两者以文件为界。`core/weread-sync` 只做三件事：调 API、写原盘文件、调度刷新。内核只读原盘文件，不感知网络。

模块划分如下。

`core/weread-sync/api` 从微读 `net` 平移，包名改为 `com.shuiyu.octoberrain.core.weread.api`，去掉对 `Context` 与 `SharedPreferences` 的依赖，改为构造函数注入根目录。`core/weread-sync/store` 从 `core` 平移 `StatsStore`、`BillStore`、`AssetStore`、`BookStore` 四个存储类，落位沿用原盘名，即 `shelf.json`、`shelf_ids.json`、`book.json`、`bills.json`、`assets.json`、`stats.json`、`nt_*.json`、`ni_*.json`、`kernel_prefs.json`。`core/weread-sync/sync` 平移 `BillScheduler` 与 `AssetRefresher` 的调度骨架，改由十雨的 `WorkManager` 或前台服务驱动，刷新周期沿用配置项 `wereadRefreshMinutes`。

十雨侧界面复用现有实现，把数据来源从 `ContentSourceImpl` 的网络分支与迁移导入的文件读出，统一改为 `WereadRepository`。仓库的读取顺序是原盘文件、内存缓存、网络，三者命中其一即返回，网络结果异步写盘。

## 五 数据获取与存储

网络请求全部由 `WereadApi` 承担，密钥取自 `LauncherConfig.integrations.wereadApiKey`。请求失败按 `wereadRefreshMinutes` 退避重试，连续三次失败后暂停自动刷新，转由用户手动刷新。

存储沿用微读原盘格式，理由有三条。其一，内核 `KernelDataLoader` 已按该格式实现解析，改动成本为零。其二，已通过导出通道迁移过的用户数据可以直接复用，不需要二次转换。其三，两种来源可以共存，用户从「装了微读」过渡到「只装十雨」时数据不中断。

写盘采用临时文件加原子替换，写失败保留上一版本，避免半截文件导致内核读空。

## 六 渲染归属

组件与负一屏继续由内核 k2 渲染，十雨负责提供 `KernelHost` 与数据文件。内核不新增网络能力，保持宿主无关，这一点在 k2 契约中已经成立。

墨台三个模块已在负一屏以分区形式呈现，独立后不需要再跳转微读 `DeskActivity`。已有「打开桌面」按钮的深链接改为打开十雨自身的墨台页，页面先只读渲染三个分区，后续再补排序和开关的即时生效。

## 七 分阶段计划

| 阶段 | 内容 | 交付 | 人日 |
| --- | --- | --- | --- |
| S1 | 平移 `WereadApi` 与 `NoteSync` 到 `core/weread-sync/api`，构造函数注入根目录，去掉 Context 依赖 | 可独立调用的取数层与单测 | 3 |
| S2 | 平移四个存储类与两个调度器，落位对齐原盘文件名 | 刷新调度可用，断网可读缓存 | 4 |
| S3 | 新增 `WereadRepository`，替换 `ContentSourceImpl` 的网络分支与迁移读出 | 组件与负一屏读同一数据源 | 3 |
| S4 | 桌面「打开桌面」与负一屏「详情」改跳十雨页面，新增墨台独立页与书籍页、笔记页、待办页的数据绑定 | 无微读 APK 时入口全部可用 | 3 |
| S5 | 迁移通道降级为可选，设置页提供「从微读墨记导入」与「重新同步」两个入口，补验收与文档 | 候选包 | 3 |

合计十六人日，不含真机回归与视觉比对。

## 八 兼容与迁移

已装微读墨记的用户，首次升级到独立版时弹一次性询问，选择「导入现有数据」则走现有导出通道，选择「直接联网同步」则跳过导入。两种方式都完成后，静默共存逻辑保留但不再必要，微读墨记可以卸载，卸载后十雨不受影响。

纯十雨用户，在设置页填写密钥即可，不需要安装任何其他应用。

数据冲突处理，导入与联网同步以时间戳较晚的一方为准，与现有 `desk_prefs` 的裁决规则一致。

## 九 不确定项

微读 API 的稳定性由腾讯侧决定，接口变更时取数层需要跟随调整，这是外部依赖，无法通过设计消除。可以缓解的方式是把解析层与请求层分开，接口字段变化只改解析。

`NoteSync` 依赖微读账号的笔记结构，字段较多，平移后需要真机联调确认摘录与想法的对应关系。

书封地址来自 API 返回的 `coverUrl`，长期可用性同样受外部影响，本地缓存需要有失效重取。

## 十 验收标准

不装微读墨记时，五个负一屏卡片与五种组件形态均有真实内容。断网时显示上次同步内容。密钥错误时提示明确。导入通道在已装微读时可以一次性迁移，迁移后数据与直接同步结果一致。门禁脚本增加「无微读墨记」场景，安装包中不包含微读任何组件。

## 十一 与当前交付的关系

本轮交付的 1.0.2 已把渲染与墨台模块收入十雨，数据仍以迁移导入与联网回退两种方式并存。本方案是在此基础上补齐取数层的独立能力，不动内核接口，不改组件与负一屏的渲染代码，因此可以与后续版本并行推进。
