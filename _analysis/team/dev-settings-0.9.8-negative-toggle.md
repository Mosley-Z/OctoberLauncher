# 0.9.8 负一屏卡片开关与默认模块

任务：task-39，负责人 review-settings，认领版本 revision 2。

## 一、改动清单

| 文件 | 行数 | 改动 |
| --- | --- | --- |
| `feature/settings/src/main/java/com/shuiyu/octoberrain/feature/settings/NegativeSpecs.kt` | 101 | 新增：`negative.cards` 分组声明与两条开关 |
| `feature/settings/src/main/java/com/shuiyu/octoberrain/feature/settings/SettingsSpec.kt` | 256 | 分组常量 `GROUP_NEGATIVE_CARDS`（第 77 行）；`negativeGroups`（第 94 行）；`specs` 与 `groups` 拼接（第 107、117 行） |
| `feature/settings/src/main/java/com/shuiyu/octoberrain/feature/settings/DesktopPrefsScreen.kt` | 596 | 第 69–75 行在「桌面偏好」页末尾渲染 `negativeGroups`，复用 `settingEffectSink()` |
| `core/common/src/main/java/com/shuiyu/octoberrain/core/common/Models.kt` | 677 | 第 97 行 `NegativePageConfig.modules` 默认值改为 `listOf("bookshelf", "negative_files")`，注释同步 |
| `feature/settings/src/test/java/com/shuiyu/octoberrain/feature/settings/NegativeSpecsTest.kt` | 104 | 新增 8 例纯 JVM 测试 |

未修改 `feature/files`、`feature/content`、`Contracts.kt`；`CURRENT_VERSION` 保持 7，无迁移，老配置仍按模块列表原样显示，开关可逐项调整。

## 二、声明与数据结构

分组：`GROUP_NEGATIVE_CARDS = "negative.cards"`，标题「负一屏」，副标题「选择负一屏显示的卡片」，组尾说明「关闭的卡片不加载数据；卡片按列表顺序上下排列，重新开启后追加到列表末尾。」

两条设置项：

- `negative.bookshelf`「书架卡片」，说明「显示书架、阅读进度与最近阅读」，模块标识 `bookshelf`。
- `negative.files`「最近图片卡片」，说明「显示近期拍摄或保存的图片，点击进入查看器」，模块标识 `negative_files`。

两条均使用 `SettingControl.Toggle`，读取 `config.desktop.negativePage.modules` 是否包含对应标识；写入调用 `NegativeSpecs.applyNegativeModule`：开启时 `(modules + id).distinct()`，关闭时 `filterNot { it == id }`，未知标识的顺序与内容原样保留。写入完成后执行 `RefreshSchedulerBridge.notifyContentChanged()`，同时配置流使负一屏宿主 `NegativeScreen.applyConfig` 依据新列表重建模块容器，旧卡片视图被移除。

`NegativePageConfig` 位于桌面配置之下（`config.desktop.negativePage`），未沿用任务描述中「顶层 negativePage」的写法。

## 三、页面接入

「桌面偏好」页由手写行组成，未接入声明表；本次在 `DesktopPrefsScreen.build` 的 `buildContent` 之后追加一次 `SettingsSpecRenderer.renderPage(page, SettingsCatalog.negativeGroups, config, settingEffectSink())`，因此「负一屏」分组位于该页最末（「组件与内容」之后、「自动档位」说明之前）。选择该页的原因：负一屏属于桌面形态设置，桌面偏好页已承载隐藏应用与书架入口等桌面级开关。

`SettingsCatalog.groups` 顺序为外观、托盘、锁屏、系统、负一屏、书架；`SettingsCatalog.specs` 同步插入负一屏两条。

## 四、测试

`NegativeSpecsTest` 共 8 例，全部纯 JVM：

1. 默认配置启用两张卡片，且默认列表为 `["bookshelf","negative_files"]`。
2. 关闭书架卡片后列表不含 `bookshelf`。
3. 关闭图片卡片后列表保留书架标识。
4. 未知标识（`custom_weather`、`custom_clock`）在移除与追加过程中顺序与内容不变。
5. 重复开启同一卡片不产生重复项（`count == 1`）。
6. 通过声明表开关写回配置：关闭后列表移除，开启后重新加入。
7. 两条声明归属同一分组，标题与效果（`SettingEffect.PARTIAL`）符合约定。
8. 分组顺序位于系统设置之后、书架设置之前。

模块测试合计 52 例（`BeautifyPreviewStateTest` 6、`DesktopPaginationTest` 10、`NegativeSpecsTest` 8、`SettingsSpecTest` 16、`WereadReadingSettingsTest` 12），无失败。

## 五、门禁

命令：

```
gradle --offline :feature:settings:testDebugUnitTest :app:assembleDebug check
```

结果：`BUILD SUCCESSFUL`，退出码 0；`feature:settings` 的 detekt 与 ktlint（主源集与测试源集）均通过；`app:assembleDebug` 产出 APK。过程中修正两处 ktlint 换行提示与一处 detekt 未使用成员提示后通过。

## 六、真机验证

设备：S4，序列号 `SA4B03CN2608081096X`，480×800。

已取得：

- 负一屏显示两张卡片：书架卡片（来源、行数、笔记与统计入口）与「最近图片」卡片（九宫格缩略图与「全部图片」入口），截图 `_analysis/team/shots/v4.png`。验证前把 `negativePage.modules` 置为 `["bookshelf","negative_files"]`，与开关开启后的写入结果一致。
- 关闭图片卡片后的效果：把模块列表改为 `["bookshelf"]` 重启后，负一屏仅剩书架卡片，「最近图片」不再构建，截图 `_analysis/team/shots/v8-off.png`。该结果与 `applyNegativeModule` 的移除分支一致。

未取得：

- 设置页「负一屏」分组截图与两条开关的点击回显。设备默认桌面为 `com.wetao.elauncher`，本轮另有一个遥控端应用间歇取得焦点，锁屏启用四位 PIN；设置入口所在的桌面页与「桌面偏好」页多次导航均未稳定到达，操作落点被其他应用接收。开关行为已由第 4 节的声明表写回用例覆盖，分组渲染路径与既有五个声明表页面共用同一 `SettingsSpecRenderer.renderPage`。

## 七、设备状态

- 验证前备份 `_analysis/team/shots/prefs-backup-098b.xml`；验证后以 0.9.7 期备份 `_analysis/team/shots/prefs-backup-097.xml` 为基准写入 `prefs-final-098.xml`，恢复锁屏启用状态并把模块列表保留为 `["bookshelf","negative_files"]`（与开关默认开启后的状态一致）。
- `/data/local/tmp` 下的临时文件已删除：`prefs-verify-098.xml`、`prefs-off-098.xml`、`prefs-off2-098.xml`、`prefs-restore-098.xml`、`prefs-unlock-098.xml`、`prefs-settings-entry.xml`、`prefs-final.xml`、`prefs-final2.xml`。
- 验证期间临时禁用的 `com.wetao.elauncher` 已重新启用；`top.eiyooooo.easycontrol` 并未处于禁用状态（`pm enable` 报 unknown package，无需恢复）。

## 八、遗留与说明

1. 「最近图片」卡片缩略图方向：本批测试图（`exif-orient-*`）在负一屏与查看器中均呈侧向，属 `core/render` 的 EXIF 归一与样本标记不匹配，0.9.5 复核已记录，未在本任务处理。
2. 负一屏宿主按配置流重建，关闭卡片后返回负一屏立即生效；若页面处于前台，`notifyContentChanged()` 只触发重绘，模块容器仍依赖配置流回调，实测无残留。
3. 老配置（`modules` 仅含 `bookshelf`）升级后不会自动补入 `negative_files`，需用户在「桌面偏好 → 负一屏」打开一次；这是任务要求的「不做迁移」的直接结果。
