# 墨台逐页对照：微读内核基准 vs 十雨负一屏（2026-10-20）

> 2026-10-20 22:55 复测：六项差异与重复抬头已修，见文末「修复后复测」。

## 基准与素材

- 基准：`D:\WorkBuddyStation\WereadMoji\weread-widget\docs\snapshots\kernel-1.3.2-k5\probe-bill.png`（480×800，k5 内核整页渲染，等同微读墨台）。
- 十雨实测：`step-324-negative.png`（S4 负一屏顶部）、`step-325-negative-scroll1.png`、`step-326-negative-scroll2.png`（滚动）。
- 对比图：`step-327-desk-side-by-side.png`（左基准右十雨整页）、`step-328-desk-top-crop.png`（顶栏裁剪）、`step-329-desk-duplicate-header.png`（重复抬头证据）。
- 量测工具：`_tools/measure-desk.mjs`（灰度行/文本带量测）、`_tools/measure-cols.mjs`（文本块列聚类）。

## 六项固定对照

### 1. 顶栏高度与三段文字位置

| 项 | 基准 | 十雨实测 | 差异 |
| --- | --- | --- | --- |
| 顶栏高度 | 64.8px（18号×3×unit，unit=1.2；顶部分隔线 y=64） | 63px（DeskSectionStyle.headerHeightPx=max(17号×2.2, listRowHeight)） | -1.8px |
| 文字带 | y=22..41，中心 31.5（≈barH/2） | y=67..87（含状态栏 45px），中心 77=45+32 | 垂直居中一致 |
| 设置键 | x=24..78 | x=25..74 | 右端 -4px（十雨用「⚙ 设置」字符，齿轮宽度与基准自绘齿轮不同） |
| 墨台标题 | 中心 x=239.5（屏幕中心） | 中心 x=192 | 偏左 48px（十雨把标题放在权重 1 的剩余空间居中，未按全屏居中） |
| 更新于 | x=326..404 | x=313..402 | 左端 -13px、右端 -2px（十雨字号略大） |
| 返回 | x=416..454 | x=416..454 | 完全一致 |
| 顶栏底部分隔线 | 1px 浅灰 #DFDFDF，y=64 | 无 | 缺线 |

### 2. 分区抬头左侧墨竖线宽度与偏移

- 基准：墨竖线 x=15..17（3px，=HEAD_BAR_W_UNITS 2.5×unit 1.2），纵跨 y=77..97（21px）；标题左缘 x=25。
- 十雨：内核分区（墨单/阅读资产/阅读指纹）由内核绘制，与基准同源已对齐；自建分区（书架、最近图片）无墨竖线，标题左缘 x=25。
- 差异：自建分区缺 3px 墨竖线（x=15..17）。

### 3. 分区抬头基线比例 0.28 / 0.73

- 基准：墨竖线 y 自抬头行 0.28 到 0.73（实测 y=77..97）。
- 十雨：内核分区同源；自建分区抬头行高 63px、标题垂直居中（标题 y=129..150，行 108..171，上留 21px 下留 21px）。
- 差异：自建分区无该比例基线，与基准竖线起止点不一致。

### 4. 左右内边距比例

- 基准：左右 pad = w×PAD_X_RATIO(0.05)=24px；顶栏设置左缘 24、返回右缘 454；分区标题左缘 25。
- 十雨：顶栏与分区均用 `DeskSectionStyle.paddingXPx(宽)`=24px；设置左缘 25、返回右缘 454、分区标题左缘 25。
- 差异：一致（±1px 字形 bearing）。

### 5. 空态文案居中方式

- 基准：内核分区空态高 `emptyHeight = SZ_EMPTY(15号)×6×unit = 108px`，文案在块内水平与垂直居中。
- 十雨：内核分区空态与基准同源已对齐；自建分区（书架、最近图片）空态为 `InkWidgets.caption` 文本，水平居中、垂直由布局 wrap 决定，无固定空态高度。
- 差异：自建分区空态未按 108px 空态高居中（微读无此两分区，属十雨自建差异）。

### 6. 分区之间的分隔线粗细与留白

- 基准：1px 浅灰 #D8D8D8（实测灰度 225），横向 x=24..456（左右各留 24px）。
- 十雨：`DeskSectionStyle.buildDivider` 为 1px 纯黑 #000000、全宽 0..480（实测 y=184 整行 480px 全黑）；顶栏下方也没有基准的 1px 浅灰线。
- 差异：颜色（黑 vs 浅灰）、宽度（全宽 vs 内缩 24px）。

## 六项之外的主要差异

1. **内核分区重复抬头**（`step-329-desk-duplicate-header.png`）：`DeskKernelSection` 在 `WereadDeskSectionView` 之上又加了一条原生抬头，导致「墨单」「阅读资产」各出现两行标题（原生「墨单 / 壁纸」+ 内核「| 墨单 / 摘录菜单 / 读书菜单 / 生成壁纸」）。微读只有内核一行。
2. **模块集合不同**：十雨负一屏模块为 `bookshelf, negative_files, bill, rank, fp`（书架、最近图片为十雨自建，排在微读三模块之前）；微读为 `bill, rank, fp`（fp 默认关）。
3. **阅读指纹分区在 S4 未渲染**：配置含 `fp`，但 `DEFAULT_NEGATIVE_MODULES`（`core/common/.../Models.kt:86`）只有 `bookshelf, negative_files, bill, rank`，`effectiveOrder`（`DeskContainerView.kt:317`）把 `fp` 过滤掉，所以十雨负一屏永远不显示阅读指纹分区。与微读「fp 可选开启」不一致。
4. **边框/线语言**：基准顶栏与分区之间全部用 1px 浅灰线；十雨自建分区之间用 1px 黑线，顶栏无分隔线。

## 建议改法（均在 `feature/content` 写域内，待 lead 安排）

- `DeskContainerView.buildTopBar`：标题改为按全屏宽度居中（`isSingleLine` 绝对居中或外层 FrameLayout）；补 1px 浅灰底部分隔线；字号按基准调（标题 18号、设置/返回 14号、更新于 12号并以 unit 换算）。
- `DeskContainerView.buildSection`：内核分区不再套 `DeskSectionStyle.buildHeader`，直接使用内核绘制的抬头（`DeskKernelSection` 去掉原生 header）；自建分区保留原生抬头时统一为「3px 墨竖线 + 17号衬线标题 + 右侧动作」。
- `DeskSectionStyle.buildDivider`：改为 1px 浅灰、左右各内缩 24px。
- `effectiveOrder`：把 `fp` 加入 `DEFAULT_NEGATIVE_MODULES` 或单独允许，使阅读指纹分区可显示。
- 自建分区空态：按内核 108px 空态高度居中。

## 修复后复测（2026-10-20 22:55，S4）

- 改动文件：`feature/content/.../negative/DeskContainerView.kt`（顶栏改 FrameLayout 三段定位 + 底线、barH 用 `topBarHeightPx`、`DEFAULT_MODULES` 加入 fp、内核分区去掉原生抬头、装配后补一次分区恢复回调）、`DeskSectionStyle.kt`（顶栏/抬头/分隔线/空态按内核基准重写，新增 `@Suppress("TooManyFunctions")`）、`DeskKernelSection.kt`（去掉原生抬头）、`DeskSettingsView.kt`（设置页抬头 `showBar = false`）。
- 门禁：`:feature:content:ktlintCheck`、`:feature:content:detekt`、`:feature:content:testDebugUnitTest`、`:app:assembleDebug` 全部通过。
- 证据：`step-331-desk-new.png`（负一屏整页）、`step-332-desk-new-scroll1.png`（阅读指纹分区）、`step-333-desk-top-crop.png`（顶栏）、`step-334-desk-section-crop.png`（书架/最近图片/墨单抬头）、`step-335-desk-settings.png`（墨台设置页无墨竖线）、`step-337-desk-final.png`、`step-338-desk-final-scroll.png`（分区数据与阅读指纹）。

| 项 | 基准 | 复测实测 | 结论 |
| --- | --- | --- | --- |
| 1 顶栏高度与三段文字位置 | barH 64.8px；设置 24..78、墨台中心 239.5、更新于 326..404、返回 416..454；底 1px 浅灰线 y=64 | barH 65px（内容区 y=45..110，文字带 y=68..87 居中）；设置 [24,69][75,89]、墨台中心 240、更新于 325..405、返回 415..456；底线 y=110 全宽 #D8D8D8 | 已修（±2px 为字形 bearing） |
| 2 墨竖线宽度与偏移 | x=15..17（3px）、高 21px、标题左缘 25 | x=15..17、高 20px、标题左缘 25 | 已修 |
| 3 抬头基线比例 0.28/0.73 | 墨竖线纵跨抬头行 28%..73% | 抬头行 45px，竖线偏移 13..32px（27.8%..71.1%，取整误差 1px） | 已修 |
| 4 左右内边距比例 | 24px（宽 480×0.05） | 设置左缘 24、标题左缘 25、分隔线 x=24..455 | 已修 |
| 5 空态文案居中方式 | 内核空态高 108px（15号×6×unit）水平垂直居中 | 自建分区空态改为 108px 固定高、居中（`buildEmpty`）；S4 书架/最近图片已有数据，空态待断网或清空数据复核 | 已修，待空数据复核 |
| 6 分隔线粗细与留白 | 1px 浅灰 #D8D8D8、x=24..456 | 1px、灰度 216、x=24..455 | 已修 |

六项之外：

- 内核分区重复抬头：已修，墨单/阅读资产/阅读指纹各一行内核抬头（`step-334`、`step-338`）。
- `fp` 被过滤：已修，`DEFAULT_MODULES = DEFAULT_NEGATIVE_MODULES + "fp"`，S4 阅读指纹分区可渲染并可换字号。
- 自建分区（书架、最近图片）按内核风格补墨竖线与 17 号衬线标题；设置页抬头不画竖线（`showBar=false`）。
- 残差：设置簇与返回簇的文字宽度受字体度量影响，与基准相差 6~10px；十雨保留书架/最近图片两个自建分区（微读无），属产品差异，未删。
