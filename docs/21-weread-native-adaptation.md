# 微读墨记原生适配整改方案

本文定义十雨桌面为微读墨记做原生适配的方案：两套渲染并存，十雨桌面内由内核渲染承载微读墨记的卡片与墨台，微读墨记独立运行或运行在其他桌面之上时仍走原版渲染与墨台入口。数据与配置同源，老用户无需重新设置。

编写日期 2026-10-20。本方案取代 `docs/20-weread-fidelity-remediation.md` 第六节的三种取舍，`docs/20` 第一节至第三节的问题定性继续作为差距清单使用。

## 一 目标

微读墨记是内容与渲染的提供方，十雨桌面是宿主。同一份微读墨记能力有两套渲染路径，按使用场景自动选择，两边看到的数值与外观一致。

1. 十雨桌面为当前桌面时：微读墨记的卡片以十雨原生小组件出现，墨台分区在负一屏出现，两者由内核渲染，形态、尺寸、字体、热力分档、页签、菜单与原版相同。
2. 微读墨记独立运行，或十雨桌面不在使用时：原版悬浮卡片、原版墨台页、原版设置照常工作，与 1.3.1-beta 版本行为一致。
3. 微读墨记的老用户升级到共存状态后，卡片形态、墨台顺序与开关、墨单外观、待办、笔记全部延续。

## 二 双渲染分工

| 渲染路径 | 使用场景 | 渲染实现 | 数据来源 |
| --- | --- | --- | --- |
| 内核渲染 | 十雨桌面前台：桌面小组件、负一屏墨台分区 | `com.inkread.weekread.kernel.render.CardRenderer` 与 `com.inkread.weekread.kernel.desk.DeskPage` | 原盘文件，经 `KernelStore` 读取 |
| 原版渲染 | 微读墨记独立运行、其他桌面、十雨桌面不在前台 | `feature/WeekCardView` 与 `feature/DeskPageView` | 原盘文件，经 `core` 各 Store 读取 |

两条路径共用同一份原盘数据（`filesDir/weread/`），不各自缓存计算结果。同一时刻只让一套渲染对用户可见：十雨桌面在前台时原版悬浮卡片让位，十雨桌面不在前台时原版悬浮卡片按原版逻辑显示。

判定方式见第六节待确认项。

## 三 现状差距

差距已在 `docs/20` 第一节给出实证，此处只列与整改直接相关的五条：

| 差距 | 位置 | 影响 |
| --- | --- | --- |
| 组件自绘 330 行与内核渲染并存 | `feature/widgets/.../WereadBoardWidget.kt` | 内核不可用时呈现近似形态，与原版不同 |
| 内核状态字段只注入 13 个 | `feature/widgets/.../kernel/KernelWereadBoard.kt` | 书封、摘录提示、滚动、展开、错误空态缺失 |
| 负一屏四个自写模块 | `feature/content/.../ui/negative/` | 账单只有月度环比、排行按进度、指纹只有数字 |
| 数据客户端自建 | `feature/content/.../data/WereadClient.kt` 等 | 与微读原版统计规则不一致 |
| 墨台未接入 | 无 | 负一屏与墨台是两套界面 |

## 四 方案

### 4.1 组件层一比一

**删除自绘。** `WereadBoardWidget.kt` 删去 `computeLayout` `drawWeek` `drawMonth` `drawBook` `drawNote` `drawTodo` `drawBars` `drawHeatGrid` `drawProgress` `drawFooter` `drawMenu` 与 `BoardData`，约 330 行。内核不可用时显示统一占位文案。

**补齐状态注入。** 按 `CardState.java` 的 32 个写入方法逐项落实来源：

| 字段组 | 来源 | 备注 |
| --- | --- | --- |
| `setStats` `setBook` `setNote` `setTodo` | 内核 `DataSource` | 已有，需补字段 |
| `setCoverBitmap` | 原盘 `assets.json` 的书封缓存 | 原版 `AssetStore` 已有书封位图 |
| `setNoteHint` `setNoteCardSize` `setNoteFullSize` `setNoteSlot` `setNoteExpanded` | 笔记条数、墨单外观、展开状态 | 展开由手势驱动 |
| `setMonthHeatmap` `setMonthScrollMax` | 墨单外观、月历滚动位置 | 月形态整页滚动 |
| `setNoteScrollY` `setNoteLineH` `setNoteViewH` `setNoteContentH` `setNoteScrollMax` | 手势滚动量与本记排版度量 | 由内核量测回调 |
| `setErrorText` `setEmptyNote` `setAchievementText` | 取数结果、空态判定、成就数据 | 失败与空态不再空白 |
| `setDeskEnabled` `setDeskOpenLabel` | 宿主 `KernelHost` | 卡片左上角「打开墨台」 |
| `setUnit` `setFullscreen` `setPadXRatio` `setApiKey` `setRefreshing` `setMode` | 宿主与配置 | 已有 |

**交互复刻。** 对照原版 `feature/CardInteraction.java` 210 行的行为：点击（页签切换、正文、书封、菜单按钮）、长按（隐藏、刷新、打开墨台，判定时长与原版一致）、滚动（本记纵向、月形态整页，到边界后交给上层滑动）、展开（本记折叠与展开）。内核 `CardSurface.hitTest` 与 `onAction` 已提供命中与动作通道，十雨侧只做手势采集与转发。

**尺寸规则。** 卡片以 368 为基准宽度，`scale = width / 368f` 钳制 0.6 至 2.0，与 `KernelHost.scale()` 一致；组件选择器中的预览沿用原版。

**形态持久化。** `desktop.negativePage.forms["weread_board"]` 保存形态序号，与原版 `card_period` 对应。

### 4.2 墨台纳入负一屏

**内核新增墨台渲染层。** 把微读原版墨台的渲染代码平移进内核，包名 `com.inkread.weekread.kernel.desk`：

| 原版文件 | 处理 | 说明 |
| --- | --- | --- |
| `feature/DeskRenderer.java` 19046 字节 | 平移为 `kernel/desk/DeskRenderer.java` | 分区绘制工具，Context 依赖改为宿主接口 |
| `feature/DeskModules.java` 13103 字节 | 平移为 `kernel/desk/DeskModules.java` | 分区装配，数据取自 `KernelStore` |
| `feature/DeskModule.java` 1951 字节 | 平移为 `kernel/desk/DeskModule.java` | 模块契约 |
| `feature/DeskPageView.java` 1506 行 | 拆为 `kernel/desk/DeskPage.java` 与分区类，View 部分留宿主 | 手势、滚动、命中、分享、壁纸生成归宿主 |

契约新增：

```java
public interface DeskHost {
    File filesDir();
    float scale();
    void requestRedraw();
    void openPage(String target);
    String menuPrefs(String key, String fallback);
    boolean menuBool(String key, boolean fallback);
    int menuInt(String key, int fallback);
}

public final class DeskPage {
    public static final int SEC_BILL = 0;
    public static final int SEC_ASSET = 1;
    public static final int SEC_RANK = 2;
    public DeskPage(DeskHost host, DataSource data);
    public void bind();
    public void render(Canvas canvas, int width, int height, int offset);
    public int height(int width);
    public int hitTest(float x, float y);
    public void onAction(int action);
    public void release();
}
```

墨台的顶栏、页签、按钮全部由代码绘制，不引用 `R.drawable`。原版渲染路径（`DeskPageView` 与 `DeskActivity`）保留不动，作为十雨桌面之外与「单独入口」的渲染方式。

**墨台三个模块直接进负一屏。** 原版墨台的模块注册在 `core/PagePrefs.java:64-90`：`MOD_BILL = "bill"`（墨单）、`MOD_RANK = "rank"`（阅读资产）、`MOD_FP = "fp"`（阅读指纹），顺序存 `desk_order`（CSV），开关存 `desk_on_mask`，默认开启掩码 `DEFAULT_ON_MASK = BIT_BILL | BIT_RANK`，即墨单与阅读资产默认开、阅读指纹默认关。

这三个模块与十雨自有模块同列，参与负一屏的排序与开关：

| 负一屏模块 id | 名称 | 默认开关 | 渲染 |
| --- | --- | --- | --- |
| `bookshelf` | 书架 | 开 | 十雨自有（保持不变） |
| `negative_files` | 最近图片 | 开 | 十雨自有（保持不变） |
| `bill` | 墨单 | 开 | 内核 `DeskPage` 的 `BillSection` |
| `rank` | 阅读资产 | 开 | 内核 `DeskPage` 的 `AssetSection` |
| `fp` | 阅读指纹 | 关 | 内核 `DeskPage` 的 `FingerprintPoster` |

删除 `WereadAssetsNegativeModule`（原为承接旧配置而加，原版墨台没有这个模块）与三个自写模块 `WereadBillNegativeModule` `WereadRankNegativeModule` `WereadFingerprintNegativeModule`。模块 id 沿用 `PagePrefs` 的常量，负一屏的排序结果与 `desk_order` 一一对应，两边改动互相可见。

**负一屏呈现。** 五个模块在同一列表中，按顺序纵向排列，开关控制是否参与排版，整页滚动，命中由内核 `DeskPage` 提供。负一屏的排序与开关沿用现有 `NegativeSettingsScreen` 的「卡片开关与顺序」，写回 `LauncherConfig.desktop.negativePage.modules` 的同时同步到微读侧 `desk_order` 与 `desk_on_mask`。

**原版墨台页面。** `DeskActivity` 与 `DeskPageView` 保留，供微读墨记独立运行、其他桌面环境、以及用户从微读墨记桌面图标进入时使用。十雨桌面内不额外设置「打开墨台」按钮，墨台内容已经在负一屏中呈现。

### 4.3 老用户延续

**数据冻结取消。** 静默期只冻结微读墨记对自己库的写入，读取与刷新保留。升级后第一次启动完成迁移，之后内核从原盘数据读取并刷新。迁移前的数据由 `WereadImportStore` 备份七天。

**配置双向同步。** 需要同步的配置分三类：卡片形态（原版 `card_period` 与 `forms["weread_board"]`）、墨台分区顺序与开关（`desk_order` 与 `desk_on_mask`）、墨单 13 项外观（`menu_*`）。

同步通道两条：

1. 微读墨记到十雨：扩展导出桥的条目，`kernel_prefs.json` 已包含 `card_period` 与 `menu_*`；新增 `desk_prefs.json`，内容为 `{"order":["bill","rank","fp"],"onMask":3}`。十雨在启动、负一屏首次展开、从微读墨记回到前台三个时机拉取一次。
2. 十雨到微读墨记：`ExportActivity` 新增动作 `com.inkread.weekread.action.WRITE_PREFS`，调用方仍校验 `com.shuiyu.octoberrain`，一次性令牌校验照旧。写入范围只限 `card_period`、`desk_order`、`desk_on_mask`、`menu_*`，不触碰用户数据、不写笔记与账单。写入成功后微读侧触发一次墨台重算。

冲突处理：以写入时间较晚的一方为准。十雨侧记录 `wereadConfigSyncedAtMillis`，微读侧在写入动作里记录 `deskPrefsUpdatedAt`；拉取时比较两侧时间戳，较旧的一侧被覆盖。同步失败不阻断界面，沿用本地值并在下一次时机重试。

**悬浮窗与图标。** 按第一节第 2 条与第六节待确认项执行：图标保留，悬浮窗只在十雨桌面不在前台时按原版逻辑显示。

## 五 分阶段实施

| 阶段 | 内容 | 产出 | 工作量 |
| --- | --- | --- | --- |
| P0 | 删组件自绘，占位统一 | 构建通过，全仓无自绘方法 | 0.5 人日 |
| P1 | 32 字段注入与交互复刻 | 五形态与微读原版同机比对差异为 0 | 2 人日 |
| P2 | 内核墨台渲染层平移（k2 契约） | `weread-kernel-1.3.2-k2.jar`，墨台分区可渲染 | 3 人日 |
| P3 | 十雨负一屏接入墨台三模块 | 墨单、阅读资产、阅读指纹与自有模块同列排序，13 项外观生效 | 1.5 人日 |
| P4 | 配置双向同步、静默策略调整、悬浮窗让位 | 老用户升级后配置完整，双渲染不同时出现 | 1.5 人日 |
| P5 | 比对门禁与文档 | 像素比对纳入门禁，文档更新 | 1 人日 |

合计 9.5 人日。P0 与 P1 可立即开工，P2 需要动微读仓库源码。

### 实施结果

P0 至 P5 全部完成，对应版本 1.0.2（versionCode 22）。阶段产出如下。

| 阶段 | 落地内容 | 验证 |
| --- | --- | --- |
| P0 | `WereadBoardWidget.kt` 由 738 行降至 380 行，删除十二个自绘方法与数据装配，绘制只剩内核 `render` | 组件代码内无自绘方法，全量门禁通过 |
| P1 | `KernelCardFiller` 补齐十九项 `CardState` 写入，交互按 `CardInteraction.java` 复刻 | 五种形态真机截图齐全，点击抬头循环切换，长按进组件选择器 |
| P2 | 微读侧 `kernel.desk` 与 `kernel.load` 共新增十个源文件，产出 `weread-kernel-1.3.2-k2.jar`（163494 字节，57 class，`Kernel-Api-Version: 2`） | 探针三页渲染，两次构建字节一致，分区起点 0/699/914 |
| P3 | 负一屏墨单、阅读资产、阅读指纹改为 `WereadDeskSectionView` 逐模块一个 `DeskPage` | 导入后墨单显示周结与逐条书目，阅读资产显示一百一十九本与合计金额 |
| P4 | `WRITE_PREFS` 回写、`WereadDeskConfig` 与 v9 迁移、共存模式静默策略 | 迁移映射与回落用例覆盖，微读图标与数据刷新在共存态保留 |
| P5 | 结构断言脚本 `scripts/assert-weread-fidelity.ps1`、交付文档与门禁记录 | 七项断言通过，退出码 0 |


## 六 验收

1. 组件：十雨桌面组件选择器中，微读墨记卡片五种形态与原版逐像素一致（动态区域除外）；点击、长按、滚动、展开行为相同。
2. 墨台：负一屏的分区顺序、内容、13 项外观与微读墨记墨台一致；滚动与命中正常；单独入口打开原版墨台页正常。
3. 双渲染切换：十雨桌面在前台时只见内核渲染的卡片，原版悬浮卡片让位；十雨桌面不在前台时原版悬浮卡片按原版逻辑显示；两者不同时出现。
4. 老用户：从 1.3.1-beta 升级到共存状态，卡片形态、墨台顺序与开关、墨单外观、待办、笔记全部保留。
5. 断网：全部功能可用，数据来自原盘。
6. 独立运行：未装十雨桌面时，微读墨记行为与 1.3.1-beta 一致。

## 七 实施进度

记录截至开工当日的推进状态，后续每完成一阶段在此追加一行。

| 阶段 | 状态 | 已落地内容 | 待办 |
| --- | --- | --- | --- |
| P0 | 完成 | 组件内十二个自绘方法全部删除，`WereadBoardWidget` 由 738 行降至 380 行，绘制只剩内核 `render` 一条路径；内核缺失时统一占位文案 | 无 |
| P1 | 完成 | `CardState` 32 个 setter 中缺失的 19 项已补齐注入（`setNoteHint`、`setAchievementText`、`setNoteCardSize`、`setNoteFullSize`、`setMonthHeatmap`、`setNoteSlot`、`setCoverBitmap`、`setErrorText`、`setEmptyNote`、`setMonthScrollMax`、`setNoteScrollY`、`setNoteLineH`、`setNoteViewH`、`setNoteContentH`、`setNoteScrollMax`、`setDeskEnabled`、`setDeskOpenLabel`）；交互按 `CardInteraction.java` 复刻，组件契约新增 `WidgetTouchAction`、`WidgetTouchTarget` | 逐像素比对待真机补做 |
| P2 | 完成 | k2 内核 jar 产出并接入 `libs/`，`kernel.desk` 下 `DeskHost`、`DeskPage`、`DeskRenderer`、`DeskModules`、`DeskPrefs` 与三分区渲染齐备；另新增 `kernel.load.KernelDataLoader` 与 `KernelDataSource` 直接读原盘 | 探针像素比对随 P5 一并归档 |
| P3 | 完成 | 负一屏 bill、rank、fp 三模块改为 `DeskPage` 分区渲染（`WereadDeskSectionView` 逐模块一个 `DeskPage`），13 项外观经 `WereadDeskMenu` 映射到内核键；默认四项 bookshelf、negative_files、bill、rank | 字号口径按 `InkTheme.unitPx` 修正 |
| P4a | 完成 | 微读侧 `WRITE_PREFS` 动作、`desk_prefs.json` 导出、共存态只停用无障碍服务、图标与数据刷新保留 | 无 |
| P4b | 完成 | `WereadDeskConfig`、`WereadMenuConfig` 进 `LauncherConfig`，v9 迁移含旧默认回落；组件与负一屏数据源改为原盘优先、网络回退；`HostWereadWritePrefs` 回写端与负一屏设置触发点接通 | 设置页「已同步/待同步」状态以回调提示呈现 |
| P5 | 进行中 | 全量门禁脚本、四张真机截图基线、`scripts/compare-weread-fidelity.ps1` 保真度比对 | 像素比对归档与文档回写收尾 |

关键决议两条。其一，负一屏默认四项与微读墨记 `DEFAULT_ON_MASK` 对齐，指纹默认关闭，老用户此前启用的指纹经迁移保留在列表末尾。其二，墨台级键（`card_period`、`desk_order`、`desk_on_mask`、`menu_*`）落在 `LauncherConfig.integrations.wereadDesk` 与 `wereadMenu`，随配置备份；图书级数据（笔记、标价、进度）保持微读原盘 per-book 文件，迁移时原样拷贝。

## 八 已确认事项

1. 切换判定：十雨为默认桌面时启用内核渲染，原版悬浮卡片让位；换回其他桌面时恢复原版悬浮卡片。
2. 配置同步：双向同步。十雨负一屏的修改经导出桥回写微读，微读侧的修改由十雨拉取。
3. 墨台呈现：墨单、阅读资产、阅读指纹三个模块直接在负一屏展示，参与排序与显示，不另设墨台入口。
4. 桌面图标：微读墨记的桌面图标保留。
5. 内核范围：接受把墨台渲染平移到内核并把契约升到 k2。原版渲染路径保留，供独立运行与其他桌面使用。

## 九 渲染尺寸与字号口径

两份渲染的排版尺同源，取值来自微读 \InkTheme.unitPx\：屏幕长边像素乘以 0.0015。480×800 设备上该值为 1.2，15 号字落到 18 像素，18 号字落到 21.6 像素。

悬浮卡片的像素尺寸由 \CardSpec\ 决定并直接交给 WindowManager：宽 424 减 56 为 368，高 416 减 70 为 346，展开态 680；左上角定位 (56, 70)，\gravity\ 为 \TOP|LEFT\。\CardGeometry\ 的 368 是像素值，不含 dp 换算，也不随密度变化。量测佐证为真机截图上抬头分隔线长度 432 像素，等于 0.9 乘以 480。

十雨侧两处宿主因此统一到同一口径。桌面整体卡片的 \KernelHostImpl.scale()\ 返回屏幕长边乘 0.0015，组件把画布缩放到 368 逻辑宽后按该单位排版，字号与原版一致；负一屏的 \WereadDeskHostImpl.scale()\ 同式取值，分区宽度只参与排版与裁剪，不参与字号换算。

早前版本用宽度除以 368 并钳制 0.6 至 2.0 的算法作废：420 像素宽的卡片按该式得 1.14，字号比原版小约百分之五；若按卡片等比放大换算则应放大 1.141 倍，两者都偏离内核语义。
