# 0.9.2 设置项遗留清理

任务编号 task-15，实施成员 review-settings。写作用域 `feature/settings/src/main/java`、`feature/settings/src/test`，期间未运行 `ktlintFormat`，未改动 `feature/settings` 以外的文件。

## 一、声明表拆分

`SettingsSpec.kt` 由 985 行拆为 7 个文件，保留 `SettingsCatalog` 单一入口。

| 文件 | 行数 | 内容 |
| --- | --- | --- |
| SettingsSpec.kt | 219 | 19 个分组标识常量、五组接线、`specs` / `specsOf` / `specOf` / `groups`、类型声明、`RefreshSchedulerBridge` |
| AppearanceSpecs.kt | 260 | `AppearanceSpecs`：外观 5 组、17 条设置项与档位常量 |
| TraySpecs.kt | 202 | `TraySpecs`：托盘 3 组、15 条设置项与档位常量 |
| LockSpecs.kt | 84 | `LockSpecs`：锁屏 2 组、5 条设置项 |
| SystemSpecs.kt | 45 | `SystemSpecs`：系统 4 组、14 条跳转条目 |
| BookshelfSpecs.kt | 116 | `BookshelfSpecs`：书架 5 组、9 条设置项与行数档位 |
| SettingsSpecBuilders.kt | 138 | 文件级构造辅助：`systemAction`、`toggleAppearance`、`choiceTray`、`sliderTray`、`stepperIntegrations`、`choiceControl`、`optionList` 与两个字体显示名函数 |

入口保持稳定（`SettingsSpec.kt:77`、`:96-98`、`:101`、`:104`、`:107`）：

```kotlin
val appearanceGroups: List<SettingsGroupSpec> = AppearanceSpecs.groups
val specs: List<SettingSpec>
    get() = AppearanceSpecs.specs + TraySpecs.specs + LockSpecs.specs +
        SystemSpecs.specs + BookshelfSpecs.specs
```

`SettingsSpecRenderer.kt:47`、五个设置页与 `SettingsSpecTest` 的调用方式不变。构造辅助由对象私有成员改为文件级 `internal` 函数，页面声明文件按函数名调用，声明体本身逐字未改。

## 二、分组与条目对照

| 页面 | 分组标识 | 条目数 | 条目标识 |
| --- | --- | --- | --- |
| 外观 | appearance.wallpaper、appearance.theme、appearance.fonts、appearance.text、appearance.display | 17 | appearance.wallpaper.pick、.overlay、.clear、.dim、appearance.theme.open、appearance.iconPack.open、appearance.iconShape、appearance.iconContrast、appearance.font.label、appearance.font.clock、appearance.text.scale、appearance.text.reset、appearance.labels、appearance.highContrast、appearance.twelveHour、appearance.invertIsland、appearance.statusBar |
| 托盘 | tray.general、tray.status、tray.refresh | 15 | tray.enabled、tray.notificationLimit、tray.mediaEnabled、tray.brightnessCold、tray.brightnessWarm、tray.volumeKeyFlip、tray.overlayEnabled、tray.showWifi、tray.showBluetooth、tray.showBatteryPercent、tray.refreshInterval、tray.refreshOnExit、tray.refreshDelay、tray.refreshSummary、tray.refreshNow |
| 锁屏 | lock.security、lock.behavior | 5 | lock.enabled、lock.pinStatus、lock.pinEdit、lock.pinClear、lock.autoLock |
| 系统 | system.connection、system.display、system.device、system.system | 14 | system.wifi、system.bluetooth、system.mobile、system.display.page、system.sound、system.displaySize、system.battery、system.storage、system.apps、system.permissions、system.date、system.locale、system.developer、system.update |
| 书架 | bookshelf.source、bookshelf.weread、bookshelf.rows、bookshelf.local、bookshelf.entry | 9 | bookshelf.localEnabled、bookshelf.wereadEnabled、bookshelf.wereadKey、bookshelf.wereadKeyClear、bookshelf.wereadRefresh、bookshelf.wereadCacheNote、bookshelf.rows.count、bookshelf.booksDir、bookshelf.open |

合计 19 组、60 条，与拆分前一致。条目顺序、分组标题、分组说明、组尾 `notes` 均按原文件逐字迁移，`specs` 仍按外观、托盘、锁屏、系统、书架的页面顺序拼接，因此渲染结果与拆分前相同。

## 三、标识改名

| 旧标识 | 新标识 | 引用位置 |
| --- | --- | --- |
| system.display | system.display.page | SystemSpecs.kt:30、SettingsSystemScreen.kt:72 |
| bookshelf.rows | bookshelf.rows.count | BookshelfSpecs.kt:85、SettingsSpecTest.kt:111 |

分组标识 `system.display` 与 `bookshelf.rows` 保持不变，与设置项标识不再重名。

持久化结构未受影响：`SettingSpec.id` 只用于声明表检索、动作表键与自定义行键，不写入 `LauncherConfig`，也不参与 `JsonConfigStore` 的序列化与 `ConfigMigration` 的版本迁移。核对方式为全仓库检索 `system.display` 与 `bookshelf.rows`，改动后仅剩分组常量、分组说明与渲染页面的分组引用。

## 四、图标面板压缩

`BeautifyIconPanel.kt` 由 599 行降至 402 行，方法职责与调用关系不变。

| 文件 | 行数 | 迁出内容 |
| --- | --- | --- |
| BeautifyAppRow.kt | 88 | `buildAppRow`（`:20`）、`loadAppIcon`（`:76`） |
| BeautifyArrowViews.kt | 135 | `ArrowKind`（`:12`）、`ArrowButtonView`（`:20`）、`PageArrowView`（`:95`） |
| BeautifyIconPanel.kt | 402 | `buildAppColumn`（`:37`）、`moveSelection`（`:162`）、`swapInPage`（`:229`）、`moveToEdge`（`:272`）、`saveOrder`（`:310`）、`applyImage`（`:336`）、`refreshRowIcon`（`:375`）、`overrides`（`:400`）等 |

为跨文件调用，`selectRow`（`BeautifyIconPanel.kt:300`）与上述迁出的函数由 `private` 改为 `internal`，同模块内可见性变化，行为不变。画布绘制代码随视图类整体迁移，`InkGlyphs`、`Canvas`、`Path` 三个导入从图标面板移除。

## 五、验证

环境与 task-14 相同：`JAVA_HOME=D:\DSHStation\.toolchain\jdk\jdk17.0.20_12`、`ANDROID_HOME=D:\DSHStation\.toolchain\android-sdk`、`GRADLE_USER_HOME=D:\DSHStation\.toolchain\gradle-home`。

```
gradle --offline :feature:settings:testDebugUnitTest :app:assembleDebug detekt
BUILD SUCCESSFUL   EXIT=0
```

- 测试：27 例全部通过，`BeautifyPreviewStateTest` 6、`DesktopPaginationTest` 10、`SettingsSpecTest` 11，失败与错误均为 0。
- 打包：`app/build/outputs/apk/debug/app-debug.apk`，4,054,832 字节，2026-10-19 23:27:53。
- detekt：设置模块 14 条提示（拆分前 15 条）。`SettingsSpec.kt` 的 `LargeClass` 与函数数提示消失；五个声明文件的文件名与顶层声明不一致提示在文件改名后消失；剩余为 `BeautifyIconPanel.kt` 的 `buildAppColumn` 108 行、`moveSelection` 63 行与 10 处 `return`、`swapInPage`/`moveToEdge`/`applyImage`/`refreshRowIcon` 的 `return` 数、文件函数数 12，`BeautifyImagePanel.kt:37` 74 行与 `:29` 文件名不一致，`BeautifyThemePanel.kt` 函数数 13，`SettingsBaseScreen.kt:23` 函数数 11，`SettingsSpecBuilders.kt` 函数数 13，`BeautifyPreviewPanel.kt:41` 3 处 `return`。`ignoreFailures=true`，未阻断构建。
- ktlint：`ktlintCheck` 通过，报告仍有格式提示，与仓库既有基线一致，按要求未执行 `ktlintFormat`。

真机（S4，`SA4B03CN2608081096X`，480×800）：安装新包后依次进入四页并截图，页面结构、分组标题、行控件与拆分前一致。

| 页面 | 截图 |
| --- | --- |
| 设置首页（十雨标识、十张卡片） | `_analysis/team/shots/f-settings-home.png` |
| 美化模式（左栏排序、右栏图片网格、底部箭头与四个动作） | `_analysis/team/shots/f-beautify.png` |
| 系统设置（连接、显示与声音、设备三组可见，含改名后的「显示」条目） | `_analysis/team/shots/f-system.png` |
| 书架设置（书籍来源、微信读书、书架行数，含改名后的每页行数条目） | `_analysis/team/shots/f-bookshelf.png` |

真机进入方式：桌面长按空白区域打开菜单，选择「设置」；美化模式由同一菜单进入。

## 六、遗留与待确认

1. `SettingsSpecBuilders.kt` 138 行、13 个文件级函数，触发 `TooManyFunctions`；这些函数是各页面共用的构造辅助，合并会牺牲可读性，建议在静态检查基线中保留，或在 task-16 统一处理。
2. `BeautifyIconPanel.kt` 402 行满足任务上限，`buildAppColumn` 108 行与 `moveSelection` 63 行仍触发 `LongMethod`，控制流重构不在本次范围。
3. `BeautifyImagePanel.kt:29` 的文件名与唯一顶层声明 `ImageCell` 不一致，属既有问题，改名会牵动 `BeautifyIconPanel` 的引用，本次未处理。
4. 真机截图由重命名前的同源构建拍摄，重命名只改文件名与可见性，字节码行为一致；如需以最终包复拍，可再次安装重复上述四步。
5. `bookshelf.rows.count` 与 `system.display.page` 已在动作表与测试中同步，后续若继续调整标识命名，需同时检索 `SettingsSystemScreen.kt` 的 `SYSTEM_TARGETS` 键与 `SettingsSpecTest` 的字符串断言。
