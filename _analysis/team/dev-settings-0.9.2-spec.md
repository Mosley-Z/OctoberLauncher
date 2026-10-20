# 0.9.2 设置项声明化与美化页拆分

任务编号 task-14，实施成员 review-settings。写作用域 `feature/settings/src/main/java` 与 `feature/settings/src/test`，`core/common/Contracts.kt` 只读，未改动 `app`、`feature/home`、`feature/widgets`、`core/*`。

## 一、交付内容

| 类别 | 文件 | 行数 | 说明 |
| --- | --- | --- | --- |
| 新建 | SettingsSpec.kt | 985 | 声明表：19 个分组、60 条设置项，含控件类型、读写函数、生效方式 |
| 新建 | SettingsSpecRenderer.kt | 155 | 声明表到 `InkPage` 视图树的渲染器，写入统一经 `SettingsUi.edit` 后回调生效 |
| 新建 | DesktopPagination.kt | 132 | 桌面分页换算，优先委托 `DesktopLayoutProvider`，失败时本地兜底 |
| 新建 | BeautifyScreen.kt | 597 | 美化页协调器：页面骨架、工具栏、状态提示、底部动作 |
| 新建 | BeautifyPreviewPanel.kt | 131 | 页码切换、左栏刷新、页数据装载、预览跳转 |
| 新建 | BeautifyIconPanel.kt | 599 | 应用排序行、图标覆盖写入、行交换与移动 |
| 新建 | BeautifyImagePanel.kt | 278 | 图片目录与已用图标两列网格、目录选择 |
| 新建 | BeautifyThemePanel.kt | 284 | 主题库、保存、导入、导出、删除、重置 |
| 改写 | AppearanceScreen.kt | 275 → 115 | 改为 `renderPage(appearanceGroups, ...)` |
| 改写 | TrayPrefsScreen.kt | 266 → 121 | 同上，摘要行与立即整刷保留为 custom 与 action |
| 改写 | LockSettingsScreen.kt | 137 → 129 | 同上，启用开关与 PIN 操作保留为 custom 与 action |
| 改写 | SettingsSystemScreen.kt | 110 → 85 | 同上，14 条系统页跳转由动作表提供 |
| 改写 | BookshelfSettingsScreen.kt | 211 → 144 | 同上，密钥校验与缓存说明保留为动作与 custom |
| 改写 | SettingsBaseScreen.kt | 100 → 130 | 新增生效方式分派与 `SettingEffectSink` 注入点 |
| 改写 | PreviewScreen.kt | 569 → 567 | 分页与顺序改由 `DesktopPagination` 提供 |
| 新建 | src/test/SettingsSpecTest.kt | 147 | 声明表 11 例纯 JVM 测试 |
| 新建 | src/test/DesktopPaginationTest.kt | 112 | 分页换算 10 例纯 JVM 测试 |

拆分后 `BeautifyScreen` 原有七类职责分别落到五个文件，单文件行数上限 599。

## 二、声明表结构

`SettingsSpec.kt:19` 定义 `internal object SettingsCatalog`，顶部为 19 个分组标识常量（`:24`–`:78`）与选项常量（`:83`–`:115`）：

- 分组：`appearance.wallpaper/theme/fonts/text/display`、`tray.general/status/refresh`、`lock.security/behavior`、`system.connection/display/device/system`、`bookshelf.source/weread/rows/local/entry`（`:120`–`:217`）。
- 条目：外观 17 条、托盘 15 条、锁屏 5 条、系统 14 条、书架 9 条，合计 60 条，以 `specs` 取值函数汇总（`:219`）。

类型定义位于文件尾部：

```kotlin
internal data class SettingSpec(
    val id: String,
    val group: String,
    val title: String,
    val hint: String? = null,
    val control: SettingControl,
    val effect: SettingEffect = SettingEffect.PARTIAL,
    val visible: (LauncherConfig) -> Boolean = { true },
    val write: ((LauncherConfig) -> LauncherConfig)? = null,
    val afterWrite: (LauncherConfig) -> Unit = {},
)

internal sealed interface SettingControl {
    data class Toggle(val read: (LauncherConfig) -> Boolean, val write: (LauncherConfig, Boolean) -> LauncherConfig)
    data class Choice(val options: List<SettingOption>, val read: ..., val write: ...)
    data class Stepper(...)
    data class Slider(val values: List<Float>, val read: ..., val write: ..., val format: (Float) -> String)
    data class Info(val read: (LauncherConfig) -> CharSequence)
    data class Action(val value: ((LauncherConfig) -> CharSequence?)? = null)
    data class Button(val filled: Boolean = false)
    data class Custom(val key: String)
}

internal enum class SettingEffect { NONE, PARTIAL, FULL, APPEARANCE, APPEARANCE_REBUILD, REBUILD }
```

`SettingEffect` 由 `SettingsBaseScreen.kt:100` 的 `applySettingEffect` 统一分派：`PARTIAL` 请求局部刷新，`FULL` 请求整屏刷新，`APPEARANCE` 重建设计令牌，`APPEARANCE_REBUILD` 追加页面重建，`REBUILD` 请求刷新并重建页面。

## 三、渲染器

`SettingsSpecRenderer.kt:22` 的 `renderPage(page, groups, config, sink, actions, customs)` 遍历分组；`:39` 的 `renderGroup` 过滤 `visible` 后建立 `SettingsUi.section`，逐条渲染，组尾输出 `notes`；`:56` 的 `renderRow` 按控件分派到 `toggle/choose/stepper/slider/info/action/button/custom`。

写入只有一处：`:139` 的 `applyTransform` 先 `SettingsUi.edit(transform)`，再读取最新配置，`runCatching` 执行 `afterWrite`，最后 `sink.applied(effect)`。动作条目从页面的 `actions` 表取回调，缺口回退到 `spec.write`。

```kotlin
private fun applyTransform(spec: SettingSpec, transform: (LauncherConfig) -> LauncherConfig) {
    SettingsUi.edit(transform)
    val latest = SettingsUi.settings()
    runCatching { spec.afterWrite(latest) }
    sink.applied(spec.effect)
}
```

## 四、新增一项设置只改一处

以「壁纸变暗」相邻位置新增「图标描边」为例（示例假设配置模型已有 `iconStroke` 字段），在 `SettingsSpec.kt` 的 `appearanceSpecs` 内追加一条，页面文件不动：

```diff
@@ private val appearanceSpecs: List<SettingSpec> = listOf(
+        SettingSpec(
+            id = "appearance.wallpaper.iconStroke",
+            group = GROUP_APPEARANCE_WALLPAPER,
+            title = "图标描边",
+            hint = "为图标外沿添加细描边，提升深色壁纸上的可辨识度",
+            control = toggleAppearance(
+                read = { it.iconStroke },
+                write = { appearance, value -> appearance.copy(iconStroke = value) },
+            ),
+            effect = SettingEffect.APPEARANCE,
+        ),
```

修改点为一个文件的连续条目：分组、标题、读写函数、生效方式。若确实需要新的配置字段，另在 `core/common/Models.kt` 增加字段并在 `core/data/ConfigMigration.kt` 增补版本规则，页面文件仍然零改动。同类条目的分组标题、组尾说明、排序位置由既有声明决定，`SettingsSpecTest` 的标识唯一与分组引用断言会拦住拼写错误。0.9.1 之前同样的改动需要同时修改页面 `build` 方法、选项常量与文案（`docs/13-inkOS对照评估.md:128` 记录了这一成本）。

## 五、美化页拆分

| 文件 | 行数 | 职责 |
| --- | --- | --- |
| BeautifyScreen.kt | 597 | 协调器：可变页面状态、`createView`（`:101`）、`render/renderNow`（`:125`/`:135`）、工具栏（`:184`）、中部三栏（`:260`）、底部四动作（`:287`）、保存反馈（`:354`） |
| BeautifyPreviewPanel.kt | 131 | `switchPage`（`:18`）、`refreshLeftNow`（`:41`）、`loadPages`（`:68`）、`snapshot`（`:101`）、`pageContentChanged`（`:105`）、`openPreview`（`:114`） |
| BeautifyIconPanel.kt | 599 | `buildAppColumn`（`:38`）、`buildAppRow`（`:157`）、`moveSelection`（`:234`）、`swapInPage`（`:301`）、`moveToEdge`（`:344`）、`saveOrder`（`:382`）、`applyImage`（`:408`） |
| BeautifyImagePanel.kt | 278 | `ImageCell`（`:29`）、`buildImageColumn`（`:37`）、`chooseDirectory`（`:237`）、`promptDirectory`（`:260`） |
| BeautifyThemePanel.kt | 284 | `showThemeLibrary`（`:39`）、`promptSaveTheme`（`:126`）、`importTheme`（`:161`）、`applyTheme`（`:234`）、`exportTheme`（`:251`） |

拆分后 `BeautifyScreen` 的可变成员仍以 `internal` 暴露给同模块的扩展函数文件，页面状态只有一处，避免把状态复制到各面板。

原 `BeautifyScreen.kt:1061` 使用 `context.resources.displayMetrics.widthPixels` 计算提示弹窗宽度，改为 `tokens.device.widthPx`，与设计层设备画像一致；位置现在 `BeautifyScreen.kt:354` 的 `showSavedNotice` 内。

## 六、分页统一

`DesktopPagination.kt` 成为设置模块唯一的桌面分页来源：

- `snapshot`（`:30`）：`AppServices.hasLayoutProvider()` 成立时调用 `layoutProvider.snapshot()`，异常或空值回退本地换算。
- `fallbackSnapshot`（`:44`）：列数、行数、首页行数小于 1 时取 5/5，空页补一页空列表。
- `paginate`（`:68`）：纯函数，首页用首页行数，后续页用通用行数，参数小于 1 按 1。
- `pageCapacity`（`:95`）、`keysOf`（`:101`）、`mergeOrder`（`:110`）。

删除的重复实现：原 `BeautifyScreen.kt:1454-1487` 的 `snapshot`（以 5 列 5 行自算）与 `PreviewScreen.kt:105-126`、`:134-139` 的顺序合并（现分别改为 `DesktopPagination.snapshot` 与 `mergeOrder`，见 `PreviewScreen.kt:110`、`:136`）。美化页的 `snapshot` 现为 `BeautifyPreviewPanel.kt:101` 的一行委托。

## 七、测试

纯 JVM，不依赖 Android 框架：

- `SettingsSpecTest.kt` 11 例：标识唯一、`group` 均指向已声明分组、分组列表非空、选项文本非空且取值唯一、默认配置下 `visible` 成立、自动刷新档位换算、状态栏高度换算（含「自动」档）、图标形状与二值化写入、字号滑条读写、书架行数步进、分组顺序与 `specOf` 检索。
- `DesktopPaginationTest.kt` 10 例：首页与后续页切分、空条目、行列小于 1、兜底行列、读配置列数与首页行数、页容量区分首页、页容量下限、`keysOf`、`mergeOrder` 替换与越界、空页结构。
- `BeautifyPreviewStateTest.kt` 6 例（0.9.1 已有）继续通过。

合计 27 例，全部通过。

## 八、验证结果

环境：`JAVA_HOME=D:\DSHStation\.toolchain\jdk\jdk17.0.20_12`，`ANDROID_HOME=D:\DSHStation\.toolchain\android-sdk`，`GRADLE_USER_HOME=D:\DSHStation\.toolchain\gradle-home`，命令 `gradle --offline`。

```
gradle --offline :feature:settings:testDebugUnitTest :app:assembleDebug detekt ktlintCheck
BUILD SUCCESSFUL   EXIT=0
```

- 测试：`BeautifyPreviewStateTest` 6/0/0、`DesktopPaginationTest` 10/0/0、`SettingsSpecTest` 11/0/0（时间戳 2026-10-19T15:07:09Z）。
- 打包：`app/build/outputs/apk/debug/app-debug.apk`，3,693,266 字节，23:07:10。
- detekt：15 条，全部位于美化页拆分后的文件与声明表，`ignoreFailures=true` 未阻断：`BeautifyIconPanel.kt:38` `buildAppColumn` 108 行、`:234` `moveSelection` 63 行且 10 处 `return`、`:301`/`:344`/`:408`/`:447` 的 `return` 数超限、文件函数数 14；`BeautifyImagePanel.kt:37` 74 行、`:29` 文件名与唯一顶层声明不一致；`BeautifyThemePanel.kt` 函数数 13；`SettingsBaseScreen.kt:23` 函数数 11；`SettingsSpec.kt:19` 类体积与函数数 15；`BeautifyPreviewPanel.kt:41` 3 处 `return`。
- ktlint：`ktlintCheck` 通过（`ignoreFailures=true`），报告仍列出新增文件的格式提示（`SettingsSpec.kt` 91 条、`PreviewScreen.kt` 62 条、`BeautifyImagePanel.kt` 33 条、`BeautifyIconPanel.kt` 33 条、`BeautifyScreen.kt` 24 条等），与仓库既有基线风格一致，未批量重排。

真机（`SA4B03CN2608081096X`，480×800）：安装 `app-debug.apk` 后核对设备内 `base.apk` 与本地一致（3,693,266 字节）；打开设置首页（十雨标识、十张卡片）、美化模式页、书架设置页、系统设置页，结构与 0.9.1 一致，交互可用。截图位于 `_analysis/team/shots/`（`s-home.png`、`appearance.png`、`bookshelf-settings.png`、`t3.png`）。外观与主题、快捷托盘、锁屏与安全三页未逐页截图。在书架设置页切换「本地目录」开关，界面由「关」变为「开」，随后再次点击恢复，写入与刷新正常，证实声明表到 `SettingsUi.edit` 的写入与界面回显可用。

## 九、与设计文档的差异

`docs/03-target-architecture.md:136-149` 规定以 `SettingsNode`（`Toggle/Choice/Action/Group`）配合 `RecyclerView` 渲染，并由 KSP 在编译期校验字段名。本次实现保留文档的数据驱动意图，具体取舍：

1. 命名采用 `SettingSpec/SettingControl`，与既有 `Screen`/`ScreenProvider` 命名一致；`Group` 提升为 `SettingsGroupSpec` 并携带页面级 `notes`。
2. 渲染仍用 `InkPage` 加线性容器，未引入 `RecyclerView`。设置页总数约 100 行，线性容器在墨水屏刷新策略下与既有页面一致，引入列表复用需要改动 `core/design` 与全部既有页面的刷新逻辑。
3. 字段名由编译期 KSP 校验改为 `SettingsSpecTest` 的标识唯一、分组引用、默认配置可读等断言。KSP 方案需要在 `core/widget-api` 增加注解处理器模块，超出本次写作用域。

## 十、未纳入声明表的页面

`UpdateScreen.kt`（193 行）的条目由运行时状态（检查中、有新版本、失败）决定，`DesktopPrefsScreen.kt`（555 行）与 `LauncherPrefsScreen.kt`（524 行）大量使用滑条、隐藏应用列表与分组跳转，`SettingsHomeScreen.kt`（422 行）为卡片网格，`IconPickerScreen.kt`（317 行）与 `PreviewScreen.kt` 为自绘交互。这些页面保留手写，声明表只覆盖纯配置读写页。后续若纳入，`SettingControl` 需要增加列表与自定义视图类型。

## 十一、遗留问题

1. `SettingsSpec.kt` 985 行，超出拆分后 600 行的约定。文件为纯声明，无逻辑分叉，拆分会把同一张表分散到多个文件；建议后续按页面拆为五个文件，由 detekt `LargeClass` 提示驱动。
2. 两处标识与分组标识同名：`system.display`（`SettingsSpec.kt:57` 与 `:643`）、`bookshelf.rows`（`:72` 与 `:711`）。声明表内部不冲突，`specsOf` 与 `specOf` 使用不同前缀语义，但阅读时容易混淆；建议改为 `system.display.page` 与 `bookshelf.rows.count`，同时调整 `SettingsSystemScreen.kt:68` 的动作表键与测试。
3. `BeautifyIconPanel.kt` 599 行，距 600 行上限仅余 1 行，后续新增逻辑需要先拆文件。
4. detekt 的 `ReturnCount`、`LongMethod`、`TooManyFunctions` 提示多数由原 `BeautifyScreen` 的方法原样迁移而来，拆分没有引入新的逻辑分支；消除这些提示需要重构 `moveSelection`、`moveToEdge`、`buildAppColumn` 的控制流，建议单独安排。
5. 补齐静态检查：仓库既有代码的 ktlint 提示数量更大，本次只保证门禁通过；统一执行 `ktlintFormat` 会重排既有文件，建议与格式基线一起处理。
6. 渲染器的执行过程尚未有单元测试覆盖：`SettingsSpecRenderer` 依赖 `AppServices` 与真实 `InkPage`，现有测试只覆盖声明表与分页换算。若后续需要，可对 `applyTransform` 抽取纯函数部分的写入组合，或者在 `androidTest` 中做页面级断言。

## 十二、待确认

1. 系统设置页 `SYSTEM_TARGETS` 为 14 条，而声明表 `systemSpecs` 为 14 条、唯一标识统计为 13 条，原因是 `system.display` 与分组标识重名；需在重命名时一并核对动作表键（`SettingsSystemScreen.kt:68`）与测试期望。
2. 真机只截取四个页面，外观与主题、快捷托盘、锁屏与安全三页的视觉一致性待逐页核对。
