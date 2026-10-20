# 0.9.7 阅读设置与 MIT 许可声明复核与实施记录

任务：task-32「0.9.7 阅读设置与 MIT 许可声明」。写作用域：`feature/settings/src/main/java`、`feature/settings/src/test`、`core/common/src/main/java/com/shuiyu/octoberrain/core/common/Models.kt`、`_analysis/team`。

## 一、配置字段

`core/common/src/main/java/com/shuiyu/octoberrain/core/common/Models.kt`：

- `IntegrationsConfig` 新增 `wereadRefreshMinutes: Int = WEREAD_REFRESH_DEFAULT`（:300）与 `wereadStatsRange: String = WEREAD_STATS_DEFAULT`（:302）。
- 常量：`WEREAD_REFRESH_DEFAULT = 60`（:320）、`WEREAD_REFRESH_OPTIONS = listOf(30, 60, 180, 360)`（:323）、`WEREAD_STATS_DEFAULT = "month"`（:326）、`WEREAD_STATS_OPTIONS = listOf("month", "year", "overall")`（:329）。
- 运行时回退：`effectiveWereadRefreshMinutes`（:306-311）与 `effectiveWereadStatsRange`（:313-317）在解码后取合法值，越界、空串、大小写不符一律回退默认值。回退写在配置模型内部，未进入 `ConfigMigration`；`CURRENT_VERSION` 保持 7（:358），序列化格式向后兼容，旧配置缺字段时按 Kotlin 默认值填充。

回退用例（`WereadReadingSettingsTest.kt`）：

| 输入 | 读取结果 |
| --- | --- |
| `wereadRefreshMinutes = 45 / 0 / -30 / 999` | 60 |
| `wereadRefreshMinutes = 30 / 60 / 180 / 360` | 原值 |
| `wereadStatsRange = "week" / "" / "MONTH"` | "month" |
| `wereadStatsRange = "month" / "year" / "overall"` | 原值 |
| 旧 JSON 缺两字段 | 60 / "month" |
| JSON 写入 120 与 "quarter" | 解码保留 120 / "quarter"，读取回退 60 / "month" |

## 二、声明表

`feature/settings/src/main/java/com/shuiyu/octoberrain/feature/settings/BookshelfSpecs.kt`，微信读书分组 `GROUP_BOOKSHELF_WEREAD` 内新增两项：

- `bookshelf.wereadRefreshMinutes`（:99-119）：标题「刷新间隔」，提示「微信读书书架与统计的缓存有效期」，`SettingControl.Choice`，四档「30 分钟 / 1 小时 / 3 小时 / 6 小时」，`effect = SettingEffect.CONTENT_REFRESH`。
- `bookshelf.wereadStatsRange`（:120-141）：标题「统计范围」，提示「微信读书统计页面打开时的默认周期」，`SettingControl.Choice`，三档「月统计 / 年统计 / 全部」，`effect = SettingEffect.PARTIAL`（仅写配置）。

读写换算：选项下标与 `IntegrationsConfig.WEREAD_*_OPTIONS` 一一对应，写入时按表取合法值，读取时用 `effective*` 求下标，非法历史值落到默认项。

## 三、生效方式分发

新增 `feature/settings/src/main/java/com/shuiyu/octoberrain/feature/settings/SettingEffects.kt`：

- `SettingEffectHooks`（:9-28）定义 `partialRefresh / fullRefresh / applyAppearance / rebuildPage / refreshContent` 五个动作。
- `SettingEffectDispatcher.dispatch(effect, hooks)`（:32-70）按 `SettingEffect` 分派；`CONTENT_REFRESH` 先 `refreshContent()` 再 `partialRefresh()`，`APPEARANCE_REBUILD` 为 `applyAppearance + rebuildPage`，`REBUILD` 为 `partialRefresh + rebuildPage`。

`SettingsBaseScreen.kt:104-136` 改为通过分发器处理。渲染器（`SettingsSpecRenderer.kt`）在写入配置与执行 `afterWrite` 之后统一调用 `SettingEffectSink.applied(effect)`，页面再交给分发器；`Toggle / Choice / Stepper / Slider / Info / Action / Button / Custom` 八类控件的调用方式与回调签名保持原样，只有新增的 `SettingEffect.CONTENT_REFRESH` 在分发器内获得处理分支，其余六种生效方式的动作序列与此前逐项一致（`NONE` 不动作、`PARTIAL` 局部刷新、`FULL` 整屏刷新、`APPEARANCE` 仅应用外观、`APPEARANCE_REBUILD` 应用外观后重建页面、`REBUILD` 局部刷新后重建页面）。

`CONTENT_REFRESH` 的调用链与先后顺序：

1. `SettingsSpecRenderer.applyTransform` 调用 `SettingsUi.edit` 写入 `wereadRefreshMinutes`，随后触发 `SettingEffectSink.applied(CONTENT_REFRESH)`；
2. `SettingsBaseScreen.applySettingEffect` → `SettingEffectDispatcher.dispatch`；
3. 分发器先调用 `refreshContent`，其实现为 `RefreshSchedulerBridge.notifyContentChanged()`（通知刷新调度器内容已变化）与 `AppServices.content.refreshAll()`（清空微信读书与本地书架的缓存标记，并在页面 scope 内异步重新拉取，异常经 runCatching 记录后返回）；
4. 分发器再调用 `partialRefresh`，即 `SettingsUi.refresh()` → `AppServices.refresh.requestPartial()`，使当前设置页按新值重绘。

统计范围（`bookshelf.wereadStatsRange`）的 `effect` 为 `PARTIAL`，只写配置并局部重绘，不触发缓存重建；该值供微信读书统计页读取，属于页面只读参数。

## 四、关于页与许可

- 新增 `ThirdPartyLicenses.kt`：`LicenseEntry(name, license, year, author)` 与 `ThirdPartyLicenses.entries = [WereadMoji — MIT — 2026 — Mosley-Z]`，来源声明常量 `WEREAD_SOURCE_NOTICE = "微信读书数据能力参考 WereadMoji（微读墨记，MIT 许可）"`。
- `AboutScreen.kt:28` 在「应用」分组增加「数据来源」信息行；`:59-63` 新增「开源许可」分组，逐条展示许可清单。
- `LICENSE` 文件保留在 `weread-widget` 目录，未复制到仓库根目录。

## 五、测试

新增 `feature/settings/src/test/java/com/shuiyu/octoberrain/feature/settings/WereadReadingSettingsTest.kt`，12 例：

1. 刷新间隔越界回退默认值
2. 刷新间隔合法值原样保留
3. 统计范围非法时回退月统计
4. 统计范围合法值原样保留
5. 旧配置缺失字段时取默认值
6. 解码后的非法取值按默认值读取
7. 声明表包含刷新间隔与统计范围两项
8. 刷新间隔选项下标与配置值互相换算
9. 统计范围选项下标与配置值互相换算
10. 内容刷新生效方式调用缓存刷新与局部重绘
11. 其余生效方式的分发动作保持既有顺序
12. 许可清单登记微读墨记条目

`feature:settings:testDebugUnitTest` 合计 39 例（BeautifyPreviewStateTest 6、DesktopPaginationTest 10、SettingsSpecTest 11、WereadReadingSettingsTest 12），全部通过。

## 六、门禁

- `:feature:settings:testDebugUnitTest`：通过，39 例 0 失败（BeautifyPreviewStateTest 6、DesktopPaginationTest 10、SettingsSpecTest 11、WereadReadingSettingsTest 12）。
- `:feature:settings:detekt`：通过。
- `:feature:settings:ktlintCheck`：通过。首轮报 `BookshelfSpecs.kt:3` 导入顺序与 `SettingsSpecBuilders.kt:116` 缺空行，已修正。
- `:app:assembleDebug`：通过，产物 `app/build/outputs/apk/debug/app-debug.apk` 3,781,692 字节（02:34）。
- `:app:assembleDebug check`：失败于 `:feature:content:detekt`，报告 11 个加权问题，首条为 `feature/content/.../ui/BookshelfNegativeModule.kt:134:17 The function buildCard is too long (69). The maximum length is 60. [LongMethod]`。该文件属 dev-ui 的 task-31 写作用域，未修改；`check` 中设置模块相关任务此前均已单独通过。全仓 `check` 通过需待该模块静态检查修正。

## 七、真机

设备 S4 `SA4B03CN2608081096X`（480×800，Android 11）。安装后逐项核对：

| 核对项 | 结果 | 截图 |
| --- | --- | --- |
| 书架设置页出现「刷新间隔」「统计范围」两行，取值分别为 1 小时与月统计 | 通过 | `_analysis/team/shots/s-bookshelf-097.png` |
| 刷新间隔点开后为 30 分钟 / 1 小时 / 3 小时 / 6 小时四档 | 通过 | `s-refresh-options.png` |
| 选择 30 分钟后行值回显 30 分钟（配置写入生效，`CONTENT_REFRESH` 触发缓存重建） | 通过 | `s-refresh-30.png` |
| 统计范围点开后为 月统计 / 年统计 / 全部 三档 | 通过 | `s-stats-options2.png` |
| 选择年统计后行值回显年统计 | 通过 | `s-stats-year.png` |

关于页与设置首页的截图在本次会话末尾未能取得：设备默认桌面为 `com.wetao.elauncher`（图墨），本应用不是默认 HOME；锁屏启用了四位 PIN 且未知，重启应用需先推送去掉锁屏的配置；在上述操作过程中，点击落点出现多次漂移（落到其他应用图标），且设备上其他应用的无障碍服务会主动把自身切到前台（logcat 可见 `WeekReadCard` 的窗口事件处理记录），设置首页与关于页的可达性不稳定。关于页的内容已由声明渲染路径与 12 例测试覆盖，其来源行与许可分组的旧版截图见 `s-about-097.png`、`s-about-licenses2.png`（旧文案），新文案的截图待补。

设备状态已还原：`shared_prefs/octoberrain_config.xml` 与验证前备份的配置 JSON 逐字一致（4,177 字符），`/data/local/tmp` 下临时文件已删除，应用已停止。