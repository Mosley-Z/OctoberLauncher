# 微读能力独立承载与负一屏重构执行计划

本文承接 `docs/22-weread-standalone-plan.md`，把「不安装微读墨记也能在十雨桌面内完成全部微读功能」与「墨台三模块进负一屏并可直接交互」两项工作拆到可施工的粒度。范围以用户 2026-10-20 的四项裁定为准。

## 一 已确认范围

| 项 | 裁定 | 边界说明 |
| --- | --- | --- |
| 目标场景 | 仅十雨桌面内等价 | 不实现跨桌面悬浮卡片，微读墨记在 TOMO 等第三方桌面上的悬浮形态不在本期范围 |
| 壁纸产出 | 先预览再保存 | 与微读墨记一致，预览层提供保存、分享、关闭、返回四个动作 |
| 阅读资产交互 | 完全按微读墨记原版 | 不新增自设计的摘录浮层；短按只做排序与展开，长按价格区录价，长按书名移出统计 |
| 实际书价录入 | 本期实现 | 长按价格行开录价键盘，写入 `filesDir/weread/menu_prices.json` |

## 二 能力缺口实测

内核 k2 的 `DeskPage` 已覆盖全部点按交互，缺口集中在长按、录价键盘、壁纸预览、选书与内滚五处。

已具备（`javap` 与 `DeskPage.java:198-298` 实证）：

| 能力 | 动作码 | 处理位置 |
| --- | --- | --- |
| 周月切换、周期前后 | `BILL_WEEK`、`BILL_MONTH`、`BILL_PREV`、`BILL_NEXT` | `billSection.switchMode`、`shiftPeriod` |
| 菜单页签切换 | `BILL_TAB` | `billSection.setView` |
| 收入算法三档 | `ALGO_BASE + 0..2` | `billSection.pickAlgo` |
| 资产排序三键 | `ASSET_SORT_BASE + 0..2` | `assetSection.setSort` |
| 资产展开收起 | `ASSET_TOGGLE` | `assetSection.toggleOpen` |
| 指纹字号四档 | `FP_FONT_BASE + 0..3` | `data.setFpFontPx` |
| 生成壁纸入口 | `WALLPAPER` | `host.openPage("wallpaper")` |
| 选书入口 | `FP_PICK` | `host.openPage("fingerprint")` |

缺口（需内核 k3 补齐）：

| 缺口 | 原版实现 | k2 现状 |
| --- | --- | --- |
| 长按判定 550 毫秒与三类长按 | `DeskPageView.java:222-259` 的 `longPressRun` 与 `LP_BILL_PRICE`、`LP_ASSET_PRICE`、`LP_ASSET_TITLE` | 无对应公开方法 |
| 录价数字键盘 | `billSection.openPad(idx)`、`assetSection.openPad(bookId)`、`padPress`、`hitPad`、`closePad` | 全部为包内可见，宿主无法调用 |
| 资产内滚 | `DeskPageView.java:1314-1329` 按下判定区内滚后 `scrollBy` | `DeskPage` 无 `scrollBy` |
| 壁纸预览与保存 | `MODE_WALLPAPER` 四动作、`saveWallpaper`、`shareWallpaper` | 只回调 `openPage("wallpaper")`，渲染未入内核 |
| 选书候选与写入 | `DeskPageView.java:1017-1050` 的标准 `Dialog` 选书 | 只回调 `openPage("fingerprint")` |

## 三 负一屏交互映射

负一屏每个模块是一个 `WereadDeskSectionView`，内部持一个 `DeskPage` 并按分区裁剪渲染。交互按下列映射转发，全部在负一屏内完成，不跳页。

| 触摸 | 负一屏动作 | 内核调用 |
| --- | --- | --- |
| 按下 550 毫秒未移动 | 长按 | `longActionAt(x, y)` 取动作码与索引，`onAction(code)` |
| 抬手且未长按、未滚动 | 点按 | `hitTest(x, y)` 取动作码，`onAction(code)` |
| 竖直拖动 | 列表滚动或资产内滚 | 列表交给负一屏逐页滚动容器；资产内滚调 `scrollBy(dy)` |
| 键盘开启时的点按 | 键位 | `hitPad(x, y)` 取键码，`padPress(key)` |
| 壁纸动作码 | 打开预览层 | `renderWallpaper(target, width, height)` 取位图 |
| 选书动作码 | 弹选书对话框 | `fingerprintCandidates()` 取书目，选定后 `setFingerprintBook(bookId)` |

壁纸预览层是全屏覆盖 `Dialog`，内含位图、保存、分享、关闭四个控件，保存写入相册 `Pictures/TenRain` 并提示路径，分享走系统分享选择器。选书对话框用系统 `Dialog` 加 `EditText` 过滤与书目复选框，与原版形态一致。

## 四 内核 k3 契约

在 k2 基础上新增下列公开方法，包名与版本号同步升为 `1.3.2-k3`，`Kernel-Api-Version: 3`。

```java
public final class DeskPage {
    // 长按：直接执行三类长按，返回动作码供宿主判断是否需要提示
    public int longActionAt(float x, float y);
    public String pendingTitle();   // 刚被移出统计的书名，无移出时为 null

    // 录价键盘（padOpen 为真时 height(width) 已包含键盘高度）
    public boolean padOpen();
    public int hitPad(float x, float y);
    public void padPress(int key);
    public void closePad();

    // 内滚
    public boolean scrollBy(float dy);
    public boolean canScroll();

    // 壁纸：走海报渲染，返回指定尺寸的位图，section 为 SEC_BILL、SEC_ASSET、SEC_FP
    public android.graphics.Bitmap renderWallpaper(int section, int width, int height);

    // 选书
    public java.util.List<String[]> fingerprintCandidates();  // [bookId, title, author]
    public void setFingerprintBook(String bookId);
}
```

长按动作码沿用 `DeskActions` 命名空间，新增 `LONG_BILL_PRICE = 200`、`LONG_ASSET_PRICE = 201`、`LONG_ASSET_TITLE = 202`。宿主侧只做两件事：动作码非 `ACTION_NONE` 时刷新缓存与布局；等于 `LONG_ASSET_TITLE` 时用 `pendingTitle()` 拼一行屏上提示。

壁纸渲染不复用墨台页的 `DeskRenderer`，改平移原版三条海报渲染路径（墨单票据框、资产海报、指纹折线图），按传入宽度为基准等比缩放字号与间距，内容超高按原版截断规则处理并记「余 n 本未列出」。

## 五 任务分解

实施进度（2026-10-20）：

| 阶段 | 状态 | 已落地内容 |
| --- | --- | --- |
| W1 内核 k3 | 完成 | `libs/weread-kernel-1.3.2-k3.jar`，180950 字节，SHA-256 `80aa5dce5cc5a331433253956dacde3314b7597211aae928fc6578371463ca65`，`Kernel-Api-Version: 3`；`DeskPage` 二十一项方法、`DeskActions` 三个长按动作码；长按、键盘、内滚、壁纸、选书五段逻辑已进 `kernel/desk` |
| W2 触摸与键盘 | 完成 | `DeskTouchDispatcher.kt` 状态机已落盘并真机验证：点按命中编码正确并触发动作、550 毫秒长按直接执行、长按后分区高度重算且列表由折叠展开到 119 本、内滚交由负一屏外层滚动时收到 CANCEL；价格键盘与长按提示条随数据补齐后一并验证 |
| W3 壁纸与选书 | 完成 | `WereadWallpaperDialog.kt` 预览 1080×1920 海报，「保存到相册」写 `Pictures/TenRain/tenrain-desk-<时间戳>.png` 并通知媒体扫描（真机产出 191553 字节），「分享」写缓存走系统分享；`FingerprintBookDialog.kt` 提供搜索与书目列表，选择后写 `fp_books` 并重绘指纹 |
| W4 布局收口 | 完成 | 三模块的「详情」入口改为「壁纸、选书、设置」；`WereadDeskSectionView` 用位图缓存渲染结果，`height(width)` 与空判定移到后台线程，导入数据后的滑动不再触发输入超时 |
| W5 独立承载 | 完成 | 模块 `core:weread-sync`：`WereadGateway.kt`（网关与鉴权）、`WereadStore.kt`（按原盘格式写盘）、`WereadSyncService.kt` 与 `app/HostWereadSync.kt`（装配与触发）；`WereadSyncBridge.bill` 调内核 `BillSheet.buildBillSheet` 生成票据；统计时长经 `longestByBook` 合并进书架与「本书」；划线与笔记由 `/user/notebooks` 加 `/book/bookmarklist` 合并落盘；配置项 `integrations.wereadSource` 三态与 v9 到 v10 迁移已落地；设置页新增数据来源三选与立即同步 |
| W6 兼容验收 | 部分完成 | `scripts/verify-weread-compat.ps1`（378 行）已通过语法校验与 `-DryRun`，四组组合清单可打印；真机执行待最终候选包 |

### W1 内核 k3 交互 API

目标：把 `DeskPageView` 的长按、键盘、内滚、壁纸、选书五段逻辑平移进 `kernel/desk`，对外暴露第四节的方法。

改动文件：`app/src/main/java/com/inkread/weekread/kernel/desk/DeskPage.java`、`DeskActions.java`、`BillSection.java`、`AssetSection.java`、`FingerprintSection.java`、`WallpaperRenderer.java`（新建）、`tools/kernel/build_kernel_jar.ps1`、`docs/kernel-integration.md`。

做法：长按三类判定按 `DeskPageView.java:1290-1312` 平移，命中索引沿用 `hitPriceRow`、`hitRow`；键盘复用 `PricePad` 平移到 `kernel/desk/PricePad.java`；壁纸渲染按 `WallpaperRenderer` 与 `DeskRenderer` 的分区绘制合成到独立位图；选书返回 `DeskData` 中 `fpBooks` 与书架并集。

验收：`tools/kernel/selftest` 新增长按、键盘、内滚、壁纸四组用例；两次构建字节一致；探针工程渲染出壁纸位图并保存到 `docs/snapshots/kernel-1.3.2-k3/`；人日 3。

### W2 负一屏触摸与键盘

目标：`WereadDeskSectionView` 支持点按、长按、内滚与键盘转发，交互全在负一屏内。

改动文件：`feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/ui/negative/WereadDeskSectionView.kt`、`WereadDeskHostImpl.kt`、新建 `DeskTouchDispatcher.kt`。

做法：`onTouchEvent` 记录按下时刻与位移，`Handler.postDelayed` 550 毫秒触发长按，抬手时按是否长按分派；键盘开启时点按优先走 `hitPad`；内滚与负一屏外层滚动用 `requestDisallowInterceptTouchEvent` 区分。

验收：负一屏内完成周月切换、周期前后、页签、算法三档、排序三键、展开收起、字号四档；长按价格行在弹出的键盘上录价后行金额即时变化，且重启后保持；人日 2。

### W3 壁纸与选书

目标：生成壁纸走预览再保存；选书用对话框选择指纹书目。

改动文件：新建 `feature/content/.../ui/negative/WereadWallpaperDialog.kt`、`FingerprintBookDialog.kt`，改 `WereadDeskSectionView.kt`。

做法：壁纸对话框调用 `renderWallpaper` 取 1080×1920 位图并展示，保存写入 `MediaStore` 的 `Pictures/TenRain`；选书对话框读 `fingerprintCandidates()`，选中后 `setFingerprintBook` 并 `invalidateCache`。

验收：墨单、阅读资产、阅读指纹三处生成壁纸均预览成功并保存到相册；选书后指纹分区按新书重绘；人日 1.5。

### W4 负一屏模块布局收口

目标：三模块在负一屏内按墨台分区尺寸渲染，模块标题与墨台一致，去除详情入口。

改动文件：`BillNegativeModule.kt`、`RankNegativeModule.kt`、`FingerprintNegativeModule.kt`、`NegativeScreen.kt`。

做法：模块标题沿用墨单、阅读资产、阅读指纹；「详情」按钮改为生成壁纸与设置入口；分区高度按内核 `height(width)` 自适应，默认折叠高度与墨台一致。

验收：负一屏自上而下为书架、最近图片、墨单、阅读资产、阅读指纹；每个模块内部可滚动到全部内容；人日 1。

### W5 独立承载数据层

目标：无微读墨记时，十雨用已有网关客户端取数，并把结果落盘为微读原盘格式，内核与负一屏统一读盘。

现状核实：十雨 `feature/content/src/main/java/com/shuiyu/octoberrain/feature/content/data/WereadClient.kt` 已有 680 行网关客户端，覆盖 `/shelf/sync`、`/readdata/detail`、`/user/notebooks`、封面与进度，覆盖度与微读 `net/WereadApi.java` 相当。W5 的重心不在重建客户端，而在写盘适配与同步调度。

改动文件：新建 `core/weread-sync` 模块（`WereadSnapshotWriter.kt`、`WereadSyncScheduler.kt`、`WereadStore.kt`），改 `feature/content/.../ContentSourceImpl.kt` 与 `KernelWereadReader.kt`。

做法：`WereadSnapshotWriter` 把 `WereadClient` 的书架、统计、笔记、进度结果写成与微读原盘同构的 `shelf.json`、`shelf_ids.json`、`book.json`、`stats.json`、`bills.json`、`assets.json`、`nt_index.json` 与 `kernel_prefs.json`；`WereadSyncScheduler` 按 `wereadRefreshMinutes` 触发，首次全量并显示进度，失败按指数退避；取数成功后把 `integrations.wereadSource` 置为 `local`；`ContentSourceImpl` 改为「先落盘、再读盘」，网络只作为落盘来源。

验收：卸载微读墨记后新装十雨，录入密钥即可显示书架、本周与本月时长、墨单条目、阅读资产与笔记，书封全部下载完成；人日 6。

实施记录（2026-10-20）：新建模块 `core/weread-sync`，含 `WereadStore`（原计划名 `WereadSnapshotWriter`，按单文件职责收敛为一个写入器）、`WereadGateway`（网关调用，含超时与错误分类）、`WereadSyncService`（编排与状态记录）、`WereadSyncProvider`（提供者接口）与十项单元测试。数据目录固定 `filesDir/weread`，与迁移导入共用同一份原盘结构，内核与内容层因此不区分数据来源。

调度未单独建 `WereadSyncScheduler`，改由两处驱动：`OctoberRainApp.syncWereadIfNeeded` 在启动时按数据缺失或超期触发一次，`ContentSourceImpl.refreshAll` 在内容刷新时写盘；设置页新增「数据来源」「立即同步数据」两项，动作经 `AppServices.wereadSync` 调用。`IntegrationsConfig.wereadSource` 三态保持 `imported`、`synced`、`local`，默认 `imported`。

### W6 独立承载验收与兼容

目标：两条路径都可用且互不冲突。

做法：装微读墨记时走导入与回写双向同步（原有链路），未装时走 W5 自行取数；配置项 `integrations.wereadSource` 记录 `imported`、`synced`、`local` 三态；补偿测试覆盖装与不装、导入与未导入四种组合。

验收：四种组合下组件五形态与负一屏三模块均正常；人日 2。

## 六 排期与依赖

W1 完成后 W2 与 W3 才能开工；W4 依赖 W2；W5 与 W1 至 W4 并行，互不改同一文件；W6 收尾。

合计十五点五至十七人日。按现有分工：W1 归 areal，W2 至 W4 归 tenrain-negative，W5 归 areal 与 Lead 分担，W6 归 Lead。

## 七 未决项

壁纸渲染已定稿为独立海报渲染，不复用 `DeskRenderer`，W1 开工后确认。选书对话框是否需要搜索输入框，按原版保留搜索行。`integrations.wereadSource` 的默认值取 `imported`，与原版共存路径一致。首次使用范围按完整首用：录入密钥后拉取书架、时长、账单、笔记、资产与待办，并下载全部书封。
