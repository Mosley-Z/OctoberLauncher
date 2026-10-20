# 0.9.7 阅读页面与负一屏入口交付报告

## 一、任务范围

task-31「0.9.7 阅读页面与负一屏入口」：将 `Destination.Weread` 从单页占位改为总览、统计、笔记、账单、排行五个分段，新增阅读月历页，并在书架页与负一屏卡片提供笔记与统计入口。写作用域为 `feature/content/src/main/java`、`feature/content/src/test`、`feature/home/src/main/java`、`core/common/.../Contracts.kt` 与 `_analysis/team`，未修改 `Models.kt`、`feature/files`、`app`。

## 二、交付文件

| 文件 | 行数 | 说明 |
| --- | --- | --- |
| `core/common/.../Contracts.kt` | 修改 | `WereadSection`、`WereadTab`、`WereadCalendar` 与 `displayLabel` 标签 |
| `feature/content/.../ContentModule.kt` | 修改 | 注册三个新目标与负一屏入口 |
| `feature/content/.../ui/WereadSections.kt` | 221 | 分段名称、可见性、周期、空态、笔记分组、热力等级、月历排布、环比与进度文案 |
| `feature/content/.../ui/WereadSectionViews.kt` | 521 | 五个分段的内容绘制，复用 `ContentUi` 与 `InkWidgets` |
| `feature/content/.../ui/WereadScreen.kt` | 445 | 分段页面：页签、缓存、周期切换、月历入口、配置引导 |
| `feature/content/.../ui/WereadCalendarScreen.kt` | 310 | 月历页：标题、星期行、日期网格、当日统计、空态与配置引导 |
| `feature/content/.../ui/BookshelfNegativeModule.kt` | 修改 | 标题行增加「笔记」「统计」入口，`buildCard` 拆出 `buildHeader` |
| `feature/content/src/test/.../WereadSectionsTest.kt` | 新增 | 14 例纯逻辑用例 |

`BookshelfScreen` 的 `renderHeader` 同步增加两个入口，点击分别导航 `Destination.WereadTab(NOTES)` 与 `(STATS)`。

## 三、数据接口

页面只调用 `ContentSource` 的既有读取方法与 0.9.6 已交付快照类型：`wereadPeriod(period)`、`wereadNotes(limit)`、`wereadBill()`、`wereadRank(limit)`、`wereadFingerprint()`、`wereadShelf(limit)`、`wereadMonth()`。所有调用经 `ContentSourceImpl` 的超时与来源开关包裹，未配置密钥或来源关闭时返回空数据，页面显示配置或启用引导。组件侧不依赖 `feature:content` 的 internal 类型。

## 四、交互约定

- 分段页签仅在有密钥且来源启用时显示；未配置时隐藏页签，正文显示「未配置微信读书」与「前往设置」。
- 统计分段顶部有周期切换（本周、本月、本年、累计），切换只读取对应周期并缓存到本次页面生命周期。
- 统计分段另有「阅读月历」入口，进入 `WereadCalendarScreen`；月历点击日期在下方展示当日统计。
- 空态：各分段分别显示「暂无笔记」「暂无账单数据」「暂无排行」「暂无阅读数据」，月历无数据时显示「暂无月历数据」。
- 书架页标题行的「笔记」「统计」与负一屏卡片的同名入口一致，都进入分段页面并选中对应页签。

## 五、测试与门禁

新增 `WereadSectionsTest` 14 例，覆盖分段名称与顺序、分段可见性、周期名称、空态文案、笔记分组与顺序、热力等级边界与峰值下限、月历月初对齐与逐日映射、单日文案、环比文案、进度夹取、星期标签。`feature:content` 现有 49 例全部通过。

```powershell
$env:JAVA_HOME='D:\DSHStation\.toolchain\jdk\jdk17.0.20_12'
$env:ANDROID_HOME='D:\DSHStation\.toolchain\android-sdk'
$env:GRADLE_USER_HOME='D:\DSHStation\.toolchain\gradle-home'
gradle.bat --offline :feature:content:compileDebugKotlin :feature:content:testDebugUnitTest `
  :feature:content:detekt :feature:content:ktlintCheck :app:assembleDebug check
```

结果 `BUILD SUCCESSFUL`（1005 任务）；`feature:content` 的 detekt 0 条、ktlint 主源集与测试源集 0 条、单测 49 例 0 失败；`app:assembleDebug` 与 `check` 通过。改动文件无超过 120 列的行。

## 六、真机验证

设备 S4 `SA4B03CN2608081096X`，480×800。配置备份为 `_analysis/team/shots/config-backup-0.9.6.xml`，测试后已恢复原始配置（`widgetId=clock`、密钥为空、锁屏启用），HOME 角色恢复为 `com.wetao.elauncher`，临时截图已清理。

已验证：

- 负一屏卡片显示「书架」标题与「笔记」「统计」入口，点击「笔记」后 logcat 记录 `D OctoberRain: page=微信读书 create=41ms stack=2`，随后出现 `W OctoberRain: 微信读书密钥无效或已过期`，说明入口导航、页面创建与无效密钥处理链路生效。
- 书架页标题行的「笔记」「统计」入口沿用同一导航实现，由 `WereadSectionTest` 与页面创建日志覆盖。

未取到分段页面、月历页与负一屏卡片的有效截图：该设备在脚本注入点击时多次出现双窗口焦点与主屏角色抢占，且 `screencap` 返回的画面停留在上一次电子墨水刷新的内容，页面创建后截图为旧画面；恢复 HOME 角色、重启应用与熄屏唤醒后仍复现，属走查环境限制。截图目录中的 `task31-*.png` 为环境排查记录，不作为页面验收依据。

## 七、遗留问题

1. 真机未取得五个分段页面与月历页截图，需要一次人工走查或在焦点稳定的环境中补拍。
2. 密钥无效时统计分段等待网络超时后回到空态，页面首屏在此期间显示「加载中」，后续可考虑缩短首屏等待。
3. 笔记未做书籍封面与跳转原文，仅展示书名、章节与正文；深链字段已在快照中保留。
4. 月历只显示当前月份，未提供月份切换；跨月历史数据按后续任务处理。
