# OctoberLauncher feature 层代码复核：home / widgets / tray

复核对象：只读源码核对，debug 构建已由他人验证通过，本报告不涉及构建与运行。
工作目录：`D:\DSHStation\OctoberLauncher`（Windows，非 git 仓库）。

## 一、路径约定

为便于阅读，正文中的位置以模块根为基准书写，例如：

- `feature\home\HomeView.kt:358`
  指 `feature\home\src\main\java\com\shuiyu\octoberrain\feature\home\HomeView.kt` 第 358 行。
- `core\render\BitmapPipe.kt:24`
  指 `core\render\src\main\java\com\shuiyu\octoberrain\core\render\BitmapPipe.kt` 第 24 行。
- `core\widget-api\WidgetApi.kt:116`
  指 `core\widget-api\src\main\java\com\shuiyu\octoberrain\core\widget\WidgetApi.kt` 第 116 行。

## 二、复核范围

| 模块 | 文件 | 行数 |
| --- | --- | --- |
| feature\home | HomeView.kt | 1219 |
| feature\home | DesktopMetrics.kt | 510 |
| feature\home | NegativeScreen.kt | 282 |
| feature\home | HomeScreen.kt | 156 |
| feature\home | DesktopEntries.kt | 75 |
| feature\home | HomeDesktopLayoutProvider.kt | 91 |
| feature\home | HomeDesktopPreviewProvider.kt | 56 |
| feature\home | IconStore.kt | 112 |
| feature\widgets | InkWidgetView.kt | 276 |
| feature\widgets | ClockWidget.kt | 267 |
| feature\widgets | ClockFaces.kt | 298 |
| feature\widgets | WidgetCatalog.kt | 117 |
| feature\widgets | WidgetPickerScreen.kt | 138 |
| feature\tray | TrayPanelView.kt | 823 |
| feature\tray | StatusBarView.kt | 223 |
| feature\tray | TrayControllerImpl.kt | 113 |
| core\render | BitmapPipe.kt / Ditherer.kt / InkCanvas.kt / RefreshScheduler.kt | 64 / 101 / 64 / 118 |
| core\widget-api | WidgetApi.kt | 约 170 |

同时按需查阅 `core\device`（DeviceProfile、DeviceProfileDetector、DeviceRuntime）、`core\design`（DesignRuntime、InkTokens、InkWidgets）、`core\common\Contracts.kt` 中被复核代码实际依赖的接口。

## 三、发现清单

严重度按「对墨水屏设备上可感知的行为影响」排序，同一级别内按影响面排序。

### 1. 位图统一管线与灰度抖动两处核心能力均无调用方

- 位置：`core\render\BitmapPipe.kt:13`、`core\render\Ditherer.kt:12`、`feature\widgets\ImageWidget.kt:138-149`、`feature\widgets\CoversWidget.kt:141-152`
- 级别：高

问题：设计文档规定的渲染层组件 `BitmapPipe` 与 `Ditherer` 在全仓库检索中只命中定义处，`feature` 层没有任何调用点。组件各自复制了一份两遍解码逻辑，并改用 `ARGB_8888`。灰度抖动与二值化在任何绘制位置都没有接入，彩色应用图标与彩色封面按原样绘制到灰阶面板。

依据：

- 检索 `Ditherer|BitmapPipe` 的全部匹配为 `core\render\Ditherer.kt:12`、`core\render\BitmapPipe.kt:13` 两处定义，无调用。
- `ImageWidget.kt:138-149` 与 `CoversWidget.kt:141-152` 均自行实现 `inJustDecodeBounds` 第一遍取尺寸、第二遍设 `inSampleSize` 解码，并在第 147 行与第 150 行指定 `Bitmap.Config.ARGB_8888`。
- `BitmapPipe.sampleSize`（第 45-58 行）在按目标尺寸降采样之外还有一层像素总量兜底：`MAX_PIXELS = 4_000_000`（第 16 行），并在第 54-56 行继续翻倍采样。组件内的实现只有尺寸降采样，缺少这层兜底。
- `Ditherer.threshold` 的注释（第 80-84 行）写明「把位图转换为纯黑白二值图，用于小尺寸图标」，与实际调用情况相反。

影响：单张位图常驻内存由 `RGB_565` 的两字节每像素变为四字节每像素；封面类大图若为长条构图，可能绕过尺寸判断，解码后的像素量不受约束；灰阶面板上的彩色图案只能由系统做灰度映射，细节与层次无法控制。

建议：组件解码统一走 `BitmapPipe.decodeFile`，图标缓存写入前按 `Ditherer.threshold` 或 `dither` 处理，或在 `IconStore` 取图返回处统一处理一次，避免每个绘制点各自判断。

### 2. 桌面预览视图没有释放入口，位图与协程随预览次数累积

- 位置：`feature\home\HomeDesktopPreviewProvider.kt:36-54`、`core\common\Contracts.kt:539-546`、`feature\home\HomeView.kt:257-264`
- 级别：高

问题：每次调用 `createPreviewView` 都新建一个 `HomeView`，其中包含独立的 `CoroutineScope`（第 56 行）、`Handler`（第 58 行）、`IconStore`（第 57 行，最多缓存 48 张位图）以及按配置创建的组件实例（第 356-369 行）。`DesktopPreviewProvider` 接口（`Contracts.kt:539-546`）只有 `createPreviewView` 一个方法，没有销毁或释放方法，`HomeView.release()`（第 257-264 行）在预览调用位置不存在。

依据：

- `HomeDesktopPreviewProvider.kt:36-42` 创建实例后只调用 `applyData`、`measure`、`layout`、`setPreviewPage`，返回视图交调用方缩放显示。
- `HomeView.kt:299-305` 的 `setPreviewPage` 调用 `widget?.onRefresh(false)`，说明预览路径确实会持有组件实例，组件自身的 `reloadJob` 与内部协程作用域随视图一起存活。
- `HomeView.release()` 第 258-263 行依次清理 `Handler` 消息（`removeCallbacksAndMessages(null)`）、组件 `onDestroy`、图标缓存与协程作用域，是唯一完整的释放入口。

影响：美化模式每展示一页即遗留一套图标缓存与作用域；翻页预览时（若每次重建）累积速度与页数成正比。组件在预览中加载数据会产生不必要的 IO 与重绘请求。

建议：为 `DesktopPreviewProvider` 增加 `releasePreviewView(view: View)` 或让预览视图在 `onDetachedFromWindow` 中自行调用 `release()`；同时让 `ensureWidget` 在 `previewMode` 下跳过组件创建，预览只绘制组件槽的占位。

### 3. 组件跨分钟整屏失效与整屏局部刷新请求

- 位置：`feature\home\HomeScreen.kt:47`、`feature\home\HomeView.kt:218-227`、`feature\home\HomeView.kt:371-374`
- 级别：中

问题：`MinuteTicker` 每个整分调用 `refreshCurrentWidget(true)`，后者执行 `onRefresh(force)`、`invalidate()`（整屏失效）与 `requestPartial()`（局部刷新请求）。同时 `HomeScreen.kt:50-54` 的配置收集在每次配置发射时再调用一次 `refreshCurrentWidget(true)`，`HomeScreen.kt:83` 与 `HomeScreen.kt:88` 在 `onResume` 中又各调用一次。

依据：

- `HomeScreen.kt:47`：`minuteTicker = MinuteTicker { home.refreshCurrentWidget(true) }`。
- `HomeView.kt:218-227`：非空组件时执行 `runCatching { current.onRefresh(force) }`、`invalidate()`、`requestPartial()`；组件为空时也执行 `invalidate()`。
- `HomeView.kt:371-374` 的 `requestWidgetDraw` 忽略 `widget` 参数，注释写明「桌面为单屏自绘视图，直接整屏失效即可」。

影响：钟面类组件本身只占首页上方一条区域，却触发整屏重绘与整屏局部刷新。墨水屏的局部刷新时长与脏区面积正相关，整屏局部刷新会引起肉眼可见的闪烁与残影累积；每分钟一次意味着待机时每小时六十次整屏刷新。

建议：`requestWidgetDraw` 按组件矩形计算失效区域并 `invalidate(left, top, right, bottom)`，`requestPartial` 传递脏区；分钟刷新只对声明按分钟变化的组件生效，由组件自身决定刷新范围。

### 4. 组件协议与设计文档不一致，分类与刷新策略缺少声明位置

- 位置：`core\widget-api\WidgetApi.kt:116-123`、`feature\widgets\WidgetCatalog.kt:91`、`feature\widgets\WidgetsModule.kt:16`
- 级别：中

问题：`docs\03-target-architecture.md` 规定的 `WidgetProvider{id,category,refreshPolicy,requiredCapabilities,createView}` 在实现中只剩四个成员：`id`、`label`、`description`、`create`。分类、刷新策略、能力声明在接口上均不存在。

依据：

- `WidgetApi.kt:116-123`：`interface WidgetProvider { val id; val label; val description; fun create(context): WidgetView }`。
- `WidgetCatalog.kt:91`：`val providers: List<WidgetProvider> = listOf(...)`，以固定列表声明全部组件。
- `WidgetsModule.kt:16`：`WidgetCatalog.providers.forEach { WidgetRegistry.register(it) }`。

新增一个组件需要改动的位置：新建一个继承 `InkWidgetView` 的实现文件；在 `WidgetCatalog.kt:91` 的列表中加入一行。选择器由注册表驱动（`WidgetApi.kt:139` 的 `all()`），不修改选择器代码。这一部分解耦程度尚可，但「按刷新策略调度」缺少声明依据——`RefreshScheduler` 的间隔只能由组件内部自行决定，宿主无法统一编排。

建议：在 `WidgetProvider` 上补充 `category` 与 `refreshPolicy` 字段，由 `WidgetCatalog` 声明，`RefreshScheduler` 据此计算间隔；`requiredCapabilities` 可与 `DeviceProfile` 的能力字段配合做创建前的可用性判断。

### 5. 组件创建失败后不重试，永久停留在占位提示

- 位置：`feature\home\HomeView.kt:356-369`
- 级别：中

问题：`ensureWidget` 先把 `widgetId` 置为目标标识（第 361 行），再尝试 `WidgetRegistry.create`。创建返回空时 `widget` 保持为空，而第 358 行的 `if (id == widgetId) return` 使后续同样配置的回调立即返回，不再重试。

依据：

```kotlin
private fun ensureWidget(cfg: LauncherConfig) {
    val id = cfg.desktop.widgetId
    if (id == widgetId) return
    widget?.onDestroy()
    widget = null
    widgetId = id
    if (id.isBlank()) return
    val created = runCatching { WidgetRegistry.create(id, context) }.getOrNull()
    if (created != null) { ... }
}
```

影响：组件模块的安装顺序晚于桌面视图创建时（模块由反射安装，`WidgetsModule.install` 的调用时序不由桌面保证），首次创建落空；此后配置不变，组件区域永久显示「未安装组件」占位，直到用户手动切换组件或配置写入使标识变化。`HomeDesktopPreviewProvider` 中新建的预览视图不受此影响（每次新实例重新走一遍），真实桌面受影响。

建议：把「是否已成功创建」与「目标标识」分开记录，创建失败时保留空实现并允许下一次配置回调重试；或在模块安装完成后主动广播一次组件可用事件，桌面收到后重置 `widgetId` 再走创建。

### 6. 长按进入整理模式后，同一次手势内无法拖动条目

- 位置：`feature\home\HomeView.kt:784`、`feature\home\HomeView.kt:974-981`、`feature\home\HomeView.kt:107-113`
- 级别：中

问题：按住条目超过 `editHoldMs()` 后 `enterEditRunnable` 置 `editHoldTriggered = true` 并调用 `enterEditMode(index)`，后者把 `dragIndex` 设为该条目、`dragMoved` 设为 false，意图明显是允许继续拖动。但 `handleMove` 的整理分支条件是 `if (dragIndex >= 0 && !editHoldTriggered)`，该手势内 `editHoldTriggered` 恒为真，拖动分支被关闭。

依据：

- `HomeView.kt:107-113`：`editHoldTriggered = true` 后调用 `enterEditMode(index)`。
- `HomeView.kt:974-981`：`editing = true; dragIndex = index; pressedIndex = index; dragMoved = false`。
- `HomeView.kt:783-791`：`if (editing) { if (dragIndex >= 0 && !editHoldTriggered) { ... moveEntry ... } return }`。
- `HomeView.kt:842-855`：抬手时 `!editHoldTriggered` 与 `dragMoved` 两个分支都不成立，只复位标志并重绘。

影响：长按进入整理模式后，手指继续移动不会触发排序，必须松手再按下才能拖动。整理模式的核心操作因此多出一次抬手动作，在墨水屏上每次多余的重绘都有残影代价。

建议：进入整理模式后不再用 `editHoldTriggered` 关闭拖动分支，改为以 `dragMoved` 区分「尚未移动」与「正在移动」；`editHoldTriggered` 只用于抬手时区分「长按进入」与「短按弹菜单」。

### 7. 标签字号计算读取 DisplayMetrics 与 fontScale

- 位置：`feature\home\HomeView.kt:575-590`
- 级别：中

问题：`labelTextSize` 通过 `resources.displayMetrics.density` 与 `resources.configuration.fontScale` 计算标签字号，与设计文档「feature 层禁止直接读取 DisplayMetrics」的约定冲突。`DesktopMetrics` 自身经 `DeviceRuntime.profile` 取值，未违反该约定。

依据：

- `HomeView.kt:579-580`：`val density = resources.displayMetrics.density`、`val fontScale = resources.configuration.fontScale`。
- 全 `feature` 目录检索 `displayMetrics|DisplayMetrics|resources.configuration` 仅三处命中：`HomeView.kt:579`、`HomeView.kt:580`、`feature\settings\BeautifyScreen.kt:1061`（后者属 settings 模块，不在本次范围，仅作旁证）。

影响：字号在两个墨水屏目标设备上的换算口径与设计令牌（`InkTokens` 的文本字号与 `DesignRuntime.dp`）分属两套体系，行高与基线在不同设备上可能与令牌给定值偏离；后续接入缩放设置时两处需同步修改。

建议：字号统一从 `tokens` 取值，按 `DeviceProfile` 的 `textScale` 或 `InkTokens` 的自适应规则换算，`labelTextSize` 只做宽度约束下的收缩（可与 `InkDraw.fitSize` 合并）。

### 8. DesktopMetrics 的比值常量以 480 宽为基准

- 位置：`feature\home\DesktopMetrics.kt:161-191`
- 级别：低

问题：版面比值以 480 像素宽为分母定义：`GRID_SIDE_RATIO = 30f / 480f`（第 164 行）、`ICON_TOP_RATIO = 28f / 480f`（第 167 行），行数上限 `MAX_GRID_ROWS = 12`（第 161 行）、`MAX_LIST_ROWS = 10`（第 182 行）为固定整数。设计文档声明「不沿用上游尺寸常量与 480×800 画布」。比例形式本身可随宽度缩放，但基准值仍绑定 480 宽屏幕的实测数值。

依据：`DesktopMetrics.kt:163-167` 两处注释写明「取自图墨 480 宽屏幕的 30px」「28px」，与 `docs\03-target-architecture.md` 中不沿用 480×800 画布的表述存在解释空间。

影响：552×768 设备上左右边距按比例放大到约 34.5 像素，24 像素的图标上边距放大到约 32 像素；与 480 宽设备相比，间距的相对比例一致，绝对留白更多。是否仍符合设计意图，需设计侧确认（待确认）。

建议：若设备清单固定为两种分辨率，改为按 `DeviceProfile.widthPx` 的档位表给出实际像素值，或在设计文档中明确比值基准为 480 宽并说明缩放规则。

### 9. 宫格触摸命中不校验右边界与行间隙

- 位置：`feature\home\DesktopMetrics.kt:137-156`
- 级别：低

问题：宫格分支只检查 `x < left`（第 146 行）与 `columnOffset < 0f`（第 148 行），列下标随后被 `coerceIn(0, columns - 1)`（第 152 行）夹到最右列。触点落在内容区右侧空白时仍命中最右列；行间隙与列间隙的触点归入相邻单元格（第 149 行注释说明为有意设计）。

依据：

```kotlin
if (x < left) return -1
val columnOffset = x - left - contentOffsetX
if (columnOffset < 0f) return -1
val column = (columnOffset / (cellWidth + columnGap).coerceAtLeast(1)).toInt().coerceIn(0, columns - 1)
```

影响：宽屏上 `contentOffsetX` 按内容居中计算，右侧余量与左侧边距同量级，最多约 30 至 35 像素的空白区域可触发最右列条目的启动。触控热区略大于视觉区域，在 480 宽的小屏上一般不易察觉，在 552 宽设备上余量增加。

建议：补充 `x > right`（或 `columnOffset >= columns * (cellWidth + columnGap) - columnGap`）的边界判断，使热区与绘制矩形一致；间隙归入相邻列的规则可保留。

### 10. 负一屏在模块晚注册时无法补建

- 位置：`feature\home\NegativeScreen.kt:157-160`、`feature\home\NegativeScreen.kt:112-121`
- 级别：低

问题：`onResume` 在注册表模块数量变化时调用 `applyConfig`，但 `applyConfig` 开头以 `if (bound && ids == boundIds) return` 提前返回。配置中的模块标识列表不变时，补建分支永远不执行，与类注释（第 37 行）和 `onResume` 注释（第 114 行）声明的意图相反。

依据：

- `NegativeScreen.kt:115-116`：`if (boundAvailable != expectedAvailable()) { applyConfig(AppServices.config.state.value) }`。
- `NegativeScreen.kt:159-160`：`val ids = cfg.desktop.negativePage.modules`、`if (bound && ids == boundIds) return`。
- `NegativeScreen.kt:186`：`boundAvailable = available` 只在真正重建后更新。

影响：模块注册晚于负一屏创建时，页面停留在「已启用的模块尚未注册」空状态，直到配置中模块列表发生变化。

建议：把「需要重建」的判断条件扩展为「标识列表变化，或可用模块数量变化」，即 `if (bound && ids == boundIds && boundAvailable == expectedAvailable()) return`。

### 11. 托盘侧滑关闭在事件分发过程中同步移除面板

- 位置：`feature\tray\TrayPanelView.kt:60`、`feature\tray\TrayPanelView.kt:670-693`
- 级别：低

问题：`DismissibleScrollView` 在 `dispatchTouchEvent` 内判定上滑超阈值后回调构造参数传入的 `controller.close()`（第 60 行），关闭动作发生在同一事件的派发过程中，随后仍继续调用 `super.dispatchTouchEvent(event)`，把抬起事件派发给正在被移除的视图树。对比 `NegativeScreen.kt:271-280` 的返回手势，采用 `post { }` 把移除动作推迟到分发结束后执行。

依据：

- `TrayPanelView.kt:60`：`scroll = DismissibleScrollView(context, tokens.dpF(48f)) { controller.close() }`。
- `TrayPanelView.kt:670`：`override fun dispatchTouchEvent(event: MotionEvent): Boolean`，判定与回调在方法体内完成。
- `NegativeScreen.kt:275-279`：`post { if (isAttachedToWindow) { runCatching { AppServices.router.back() } } }`，注释写明「避免在分发过程中移除当前页面引起触摸状态错乱」。

影响：同一次触摸中视图被移除，剩余事件的派发目标处于分离状态；常见表现为关闭动画或按压态残留、偶发下一次点击丢失。两处对同类问题的处理方式不一致，后续维护难以判断哪一种是约定。

建议：托盘关闭改为 `post { controller.close() }`，或在 `dispatchTouchEvent` 中先记录待处理动作，`super` 返回后再执行。

### 12. 文本截断与字号收缩逻辑在 home 与 widgets 各有一套

- 位置：`feature\home\HomeView.kt:707-717`、`feature\home\HomeView.kt:575-590`、`feature\widgets\InkWidgetView.kt:217-240`
- 级别：低

问题：`HomeView.ellipsize` 与 `InkDraw.ellipsize` 实现同样的逐字回退加省略号；`HomeView.labelTextSize` 与 `InkDraw.fitSize` 实现同样的「按宽度上限逐步缩小字号」。两套实现分别位于两个模块，边界条件处理不同。

依据：

- `HomeView.kt:707-717`：按 `measureText` 逐字回退，`end <= 0` 时返回空串。
- `InkWidgetView.kt:217-228`：同上逻辑，`maxWidth <= 0f` 时提前返回空串。
- `HomeView.kt:575-590` 以密度与字体缩放计算字号并做收敛；`InkWidgetView.kt:231-240` 以 4% 步长递减到最小值。

影响：同一段长标签在桌面与组件内的截断结果可能不一致（例如宽度恰好等于上限时的保留字数）；后续调整省略号或回退步长需修改两处。

建议：把两处收敛到 `InkDraw`（或新的 `core\design` 文本工具），`HomeView` 直接复用。

## 四、值得肯定之处

1. 组件生命周期收拢在基类，释放动作确实存在。`InkWidgetView`（`feature\widgets\InkWidgetView.kt:29-176`）统一处理加载、加载中与失败提示、画布裁剪；`onDestroy`（第 167-172 行）先置 `released`、取消 `reloadJob`、取消协程作用域，再回调子类 `onWidgetDestroy`。宿主侧 `HomeView.ensureWidget`（第 359 行）在替换组件时调用旧实例 `onDestroy`，`HomeView.release()`（第 259 行）在页面销毁时再调一次，`WidgetView.release` 的语义在真实桌面路径上有对应实现。

2. 组件绘制异常被隔离。`HomeView.drawWidget`（第 450-461 行）以 `runCatching` 包裹组件 `draw`，失败时降级为 `WidgetPlaceholder.draw`，单个组件抛异常不会中断整屏绘制。

3. 命中区域与绘制区域同源。`DesktopMetrics.cellRect`（第 117-129 行）与 `slotAt`（第 137-156 行）使用同一组 `cellWidth`、`cellHeight`、`columnGap`、`rowGap`、`appAreaTop`，没有出现绘制一套、命中另一套的偏移问题；`rowGapOf` 与 `rowOffsetOf` 也按首页与普通页分别给出。

4. 预览复用真实绘制代码。`HomeDesktopPreviewProvider`（第 36-54 行）以 `previewMode = true` 重建 `HomeView` 并手动 `measure`/`layout`，没有另写一套桌面绘制实现；`previewMode` 在 `onTouchEvent`（第 723-725 行）直接返回 false 屏蔽触摸，在 `recomputeMetrics`（第 283-290 行）跳过布局快照写入，避免预览污染美化模式读取的快照。

5. 图标缓存设了上限与最近最少使用淘汰。`IconStore`（第 16-19 行）以 `accessOrder = true` 的 `LinkedHashMap` 配合 `removeEldestEntry`，上限 `MAX_ENTRIES = 48`（第 110 行），并以 `generation` 计数使在途请求在图标覆盖变化后失效。

6. 负一屏返回手势的时序处理给出了正确示例。`NegativeScreen.SwipeBackFrame`（第 271-280 行）把页面移除推迟到事件分发结束，注释说明了原因；根容器整屏不透明（第 76-79 行）避免透出下层桌面。这一处理方式可以直接作为托盘关闭动作的参照。

## 五、待确认事项

1. `DesktopMetrics` 的 480 基准比值在 552×768 设备上的留白比例是否符合设计预期，需设计侧确认（见发现 8）。
2. `WidgetPickerScreen` 的组件列表数据来源经注册表（`WidgetRegistry.all()`）取得，本次未逐行核对选择器代码，新增组件是否完全无需修改选择器，待确认。
3. 图标位图的灰度处理现状：`IconStore` 直接缓存并绘制原始位图，其上游取图位置（`AppServices.icons` 相关实现，属 core 模块）是否已做灰阶转换，本次未核对，待确认。
4. `ClockFaces`（298 行）内含多个钟面的坐标计算，本次只核对了与 `ClockWidget` 的接口衔接，各钟面在不同组件高度下的文本基线是否越界，待确认。
5. `feature\settings` 不在本次范围，`BeautifyScreen.kt:1061` 的 `displayMetrics` 读取仅作旁证，是否构成同类问题需由 settings 复核方判定。

## 六、测试覆盖评估

`feature\home\src\test\java\com\shuiyu\octoberrain\feature\home\DesktopMetricsTest.kt`（259 行）的覆盖范围是纯计算方法：单元格矩形几何、组件槽矩形、分页数量与页数上限的交互、列数与列表形态组合。类注释说明 `DesktopMetrics.compute` 依赖 `DesignRuntime` 与 `DeviceRuntime`，无法在纯 JVM 测试中初始化，因此以构造参数直接建立版面后验证。

未覆盖的部分包括：`slotAt` 的边界命中（左右边缘、间隙、超宽余量）、`refreshCurrentWidget` 与 `requestPartial` 的调用次数、`HomeView` 的手势状态机（长按进入整理、拖动重排、抬手的四个分支、取消事件）、`ensureWidget` 的创建失败与重试、`NegativeScreen` 的模块补建、组件协议的注册与实例化。`feature\widgets` 与 `feature\tray` 下不存在 `src\test` 目录。

建议优先补充三类测试：`slotAt` 与 `cellRect` 的一致性测试（对每个槽位取矩形内一点反查槽位）；`HomeView` 手势状态机的事件序列测试（可把 `handleDown`/`handleMove`/`handleUp` 的入参抽象为坐标与时间，脱离 MotionEvent 依赖）；`WidgetCatalog` 的注册完整性测试（标识唯一、均可创建）。
